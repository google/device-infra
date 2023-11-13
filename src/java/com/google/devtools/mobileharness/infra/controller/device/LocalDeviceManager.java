/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.infra.controller.device;

import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.devicemanager.detector.Detector;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.Dispatcher;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatusWithTimestamp;
import com.google.devtools.mobileharness.infra.controller.device.external.ExternalDeviceManager;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.annotation.Nullable;

/**
 * Manages all local devices which are physically connected to the host machine. It detects new
 * devices, generates a new {@link LocalDeviceRunner} for each new device, destroy the {@link
 * LocalDeviceRunner}s when devices disconnected.
 */
public class LocalDeviceManager extends BaseDeviceStatusProvider
    implements Runnable, DeviceHelperFactory, LocalDeviceRunnerProvider, DeviceStateChecker {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int DISPATCH_DEVICE_INTERVAL_SEC = 1;

  /**
   * The max consecutive rounds of detection when detector meets exception. If reached, will clear
   * the detection result from cache.
   */
  private static final int MAX_DEVICE_DETECTION_CONSECUTIVE_FAILURE_ROUNDS = 20;

  /** Whether to keep this thread running when runtime exceptions occur. */
  private final boolean keepGoing;

  private final LocalDeviceDispatch localDeviceDispatch;

  private final List<Detector> detectors;

  private final ConcurrentHashMap<String, List<DetectionResult>> detectionResultCache =
      new ConcurrentHashMap<>();

  private final Map<String, Integer> deviceDetectionConsecutiveFailureRounds =
      new ConcurrentHashMap<>();

  private final AtomicBoolean inDrainingMode = new AtomicBoolean(false);

  /**
   * Creates a LocalDeviceManager to detect and manage devices.
   *
   * @param detectors detectors for generating the {@link DetectionResults} for {@link Dispatcher}
   *     (go/mhv5-dm-detector-dispatcher)
   * @param dispatchers dispatchers for dispatching different kinds of devices.
   * @param keepGoing whether to keep this thread running when runtime exceptions occur
   * @param threadPool thread pool for all device runner threads
   * @param globalInternalEventBus to receive device events
   */
  public LocalDeviceManager(
      List<Detector> detectors,
      List<Class<? extends Dispatcher>> dispatchers,
      boolean keepGoing,
      ExecutorService threadPool,
      EventBus globalInternalEventBus,
      ExternalDeviceManager externalDeviceManager) {
    this.detectors = detectors;
    this.localDeviceDispatch =
        new LocalDeviceDispatch(
            dispatchers, this, threadPool, globalInternalEventBus, externalDeviceManager);
    this.keepGoing = keepGoing;
  }

  @VisibleForTesting
  LocalDeviceManager(
      List<Detector> detectors,
      LocalDeviceDispatch localDeviceDispatch,
      boolean keepGoing,
      Cache<String, Boolean> duplicatedUuidCache) {
    super(duplicatedUuidCache);
    this.detectors = detectors;
    this.localDeviceDispatch = localDeviceDispatch;
    this.keepGoing = keepGoing;
  }

  /** Initializes the local device manager. */
  public void initialize() {
    localDeviceDispatch.initialize();
  }

  @Override
  public void run() {
    logger.atInfo().log("Running...");
    // Starts multiple threads for each detector.
    ListeningExecutorService detectorThreadPool =
        MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    detectors.forEach(
        detector -> {
          String detectorName = detector.getClass().getSimpleName();
          logFailure(
              detectorThreadPool.submit(
                  threadRenaming(
                      () -> {
                        Duration detectDeviceInterval =
                            Duration.ofSeconds(
                                Flags.instance().detectDeviceIntervalSec.getNonNull());
                        // Use the detector's detection interval if specified, otherwise, use the
                        // global interval instead.
                        Optional<Duration> detectionIntervalPerDetector =
                            detector.getDetectionInterval();
                        if (detectionIntervalPerDetector.isPresent()) {
                          detectDeviceInterval = detectionIntervalPerDetector.get();
                        }
                        while (!Thread.currentThread().isInterrupted()) {
                          try {
                            Sleeper.defaultSleeper().sleep(detectDeviceInterval);
                            detectionResultCache.put(detectorName, detector.detectDevices());
                            deviceDetectionConsecutiveFailureRounds.put(detectorName, 0);
                          } catch (MobileHarnessException e) {
                            int consecutiveFailureRounds =
                                deviceDetectionConsecutiveFailureRounds.getOrDefault(
                                        detectorName, 0)
                                    + 1;
                            deviceDetectionConsecutiveFailureRounds.put(
                                detectorName, consecutiveFailureRounds);
                            if (consecutiveFailureRounds
                                    % MAX_DEVICE_DETECTION_CONSECUTIVE_FAILURE_ROUNDS
                                == 0) {
                              detectionResultCache.remove(detectorName);
                              logger.atSevere().withCause(e).log(
                                  "The %s failed to detect device for %s consecutive rounds",
                                  detectorName, consecutiveFailureRounds);
                            } else {
                              logger.atSevere().atMostEvery(1, MINUTES).withCause(e).log(
                                  "The %s failed to detect devices", detectorName);
                            }
                          } catch (InterruptedException e) {
                            logger.atInfo().log("Interrupted: %s", e.getMessage());
                            break;
                          } catch (
                              @SuppressWarnings("CatchingUnchecked")
                              RuntimeException e) {
                            // Catches all exception to make sure this detector thread won't
                            // be stopped.
                            // Otherwise, no device can be detected.
                            logger.atSevere().withCause(e).log("FATAL ERROR");
                            if (!keepGoing) {
                              break;
                            }
                          }
                        }
                        logger.atWarning().log(
                            "Current thread for %s is interrupted.", detectorName);
                      },
                      () -> "detector-thread-" + detectorName)),
              Level.SEVERE,
              "Fatal error in detector");
        });
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Sleeper.defaultSleeper().sleep(Duration.ofSeconds(DISPATCH_DEVICE_INTERVAL_SEC));
        DetectionResults detectionResults = getCachedDetectionResults();
        localDeviceDispatch.dispatchDevices(detectionResults);
      } catch (InterruptedException e) {
        logger.atInfo().log("Interrupted: %s", e.getMessage());
        break;
      } catch (RuntimeException e) {
        // Catches all exception to make sure this LocalDeviceManager thread won't be stopped.
        // Otherwise, no device can be detected.
        logger.atSevere().withCause(e).log("FATAL ERROR");
        if (!keepGoing) {
          break;
        }
      }
    }
    logger.atFine().log("Stopped!");
  }

  /** Checks whether the device type is supported. */
  public boolean isDeviceTypeSupported(Class<? extends Device> deviceType) {
    return localDeviceDispatch.isDeviceTypeSupported(deviceType);
  }

  /** Checks whether the device is alive. Will double-check with a real-time detection. */
  public boolean isDeviceAlive(String deviceId) throws InterruptedException {
    return localDeviceDispatch.isDeviceAlive(deviceId, getRealtimeDetectionResult());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Note looking up the device runner without the device type is not safe inside the lab when
   * the device monitor is enabled. See b/23560412. So always try to use {@link
   * #getLocalDeviceRunner(String, String)} instead if possible.
   */
  @Override
  @Nullable
  public LocalDeviceRunner getLocalDeviceRunner(String deviceId) {
    return getLocalDeviceRunner(deviceId, /* deviceType= */ null);
  }

  /**
   * Gets the device runner according to its device ID and device type.
   *
   * @param deviceId ID of the device
   * @param deviceType simple class name of the device
   * @return the runner, or null if not found
   */
  @Nullable
  public LocalDeviceRunner getLocalDeviceRunner(String deviceId, @Nullable String deviceType) {
    LocalDeviceRunner runner = localDeviceDispatch.getDeviceRunner(deviceId);
    if (runner != null) {
      if (deviceType == null || runner.getDevice().getClass().getSimpleName().equals(deviceType)) {
        return runner;
      }
    }
    return null;
  }

  @Override
  public boolean isDirty(String deviceId) throws InterruptedException {
    // If in lameduck mode, the device would be viewed as DIRTY instead of IDLE, which will be used
    // by master.
    return !isDeviceAlive(deviceId);
  }

  @Override
  public Device getDeviceHelper(String deviceId) throws MobileHarnessException {
    LocalDeviceRunner deviceRunner = getLocalDeviceRunner(deviceId);
    if (deviceRunner == null) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_RPC_EXEC_TEST_KICK_OFF_TEST_DEVICE_NOT_FOUND,
          String.format(
              "Device runner [%s] is not found. Maybe the device is disconnected", deviceId));
    }
    return deviceRunner.getDevice();
  }

  @Override
  @Nullable
  public DeviceWithStatusInfo getDeviceAndStatusInfo(String deviceId) {
    return getDeviceAndStatusInfo(deviceId, /* deviceType= */ null);
  }

  @Override
  @Nullable
  public DeviceWithStatusInfo getDeviceAndStatusInfo(String deviceId, @Nullable String deviceType) {
    LocalDeviceRunner deviceRunner = getLocalDeviceRunner(deviceId, deviceType);
    if (deviceRunner == null) {
      return null;
    }
    return DeviceWithStatusInfo.create(
        deviceRunner.getDevice(),
        DeviceStatusInfo.newBuilder()
            .setDeviceStatusWithTimestamp(deviceRunner.getDeviceStatusWithTimestamp())
            .build());
  }

  /**
   * Gets the status of all devices. The devices which are not ready are excluded from the result.
   *
   * @param realtimeDispatch whether to do a real-time dispatch to make sure the devices of the
   *     runners are detectable by the previous detection result.
   * @return {Device, {@link DeviceStatusWithTimestamp}} mapping of all the ready devices
   */
  @Override
  public Map<Device, DeviceStatusInfo> getAllDeviceStatus(boolean realtimeDispatch)
      throws InterruptedException {
    Map<Device, DeviceStatusInfo> deviceStatusMap = new HashMap<>();
    // Uses the cached detection result to do the realtime dispatch.
    DetectionResults detectionResults = realtimeDispatch ? getCachedDetectionResults() : null;
    for (LocalDeviceRunner runner : localDeviceDispatch.getDeviceRunners(detectionResults)) {
      deviceStatusMap.put(
          runner.getDevice(),
          DeviceStatusInfo.newBuilder()
              .setDeviceStatusWithTimestamp(runner.getDeviceStatusWithTimestamp())
              .build());
    }

    // Directly return if not in lameduck/draining mode.
    if (!inDrainingMode.get()) {
      return deviceStatusMap;
    }

    // Override device status to lameduck if it is idle.
    // This will only impact device status report back to Master.
    deviceStatusMap
        .entrySet()
        .forEach(
            entry -> {
              if (entry.getValue().getDeviceStatusWithTimestamp().getStatus()
                  == DeviceStatus.IDLE) {
                entry.setValue(
                    DeviceStatusInfo.newBuilder()
                        .setDeviceStatusWithTimestamp(
                            DeviceStatusWithTimestamp.newBuilder(
                                    entry.getValue().getDeviceStatusWithTimestamp())
                                .setStatus(DeviceStatus.LAMEDUCK)
                                .build())
                        .build());
              }
            });
    return deviceStatusMap;
  }

  private DetectionResults getRealtimeDetectionResult() throws InterruptedException {
    DetectionResults detectionResults = new DetectionResults();
    for (Detector detector : detectors) {
      try {
        detectionResults.add(detector.detectDevices());
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "%s failed to detect devices: %s", detector.getClass().getSimpleName(), e.getMessage());
      }
    }
    return detectionResults;
  }

  private DetectionResults getCachedDetectionResults() {
    DetectionResults detectionResults = new DetectionResults();
    detectionResultCache.values().forEach(detectionResults::add);
    return detectionResults;
  }

  /** Flag to notify LocalDeviceManager in draining mode. */
  public void enableDrainingMode() {
    inDrainingMode.set(true);
  }
}
