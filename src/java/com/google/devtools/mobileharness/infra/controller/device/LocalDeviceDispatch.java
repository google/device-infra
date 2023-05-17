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

import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResults;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.Dispatcher;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResult;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResult.DispatchType;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.model.DispatchResults;
import com.google.devtools.mobileharness.api.devicemanager.util.ClassUtil;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCacheManager;
import com.google.devtools.mobileharness.infra.controller.device.external.ExternalDeviceManager;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalDeviceDownEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalDeviceUpEvent;
import com.google.wireless.qa.mobileharness.shared.controller.stat.DeviceStat;
import com.google.wireless.qa.mobileharness.shared.controller.stat.LabStat;
import com.google.wireless.qa.mobileharness.shared.controller.stat.StatManager;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

/**
 * Manages devices, including the device dispatchers, and the management of the {@link
 * LocalDeviceRunner}s of the devices.
 */
public class LocalDeviceDispatch {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ImmutableList<Class<? extends Dispatcher>> dispatcherClasses;

  private final List<Dispatcher> dispatchers;

  /** Device {ID, {LocalDeviceRunner, Future}} of the devices detected and managed by this class. */
  private final ConcurrentMap<String, Entry<LocalDeviceRunner, Future<?>>> devices =
      new ConcurrentHashMap<>();

  /** The {@link DispatchResults} of the previous round of detection. */
  private volatile DispatchResults previousResults = new DispatchResults();

  /** Thread pool for all the device runner threads. */
  private final ExecutorService threadPool;

  /** EventBus for {@code LocalDeviceEvent}s. */
  private final EventBus globalInternalBus;

  /** The device manager who knows the devices of old detectors. */
  private final LocalDeviceManager deviceManager;

  /** Statistic data for all the devices. */
  private final LabStat labStat;

  /** The manager of devices {controlId, uuid}. */
  private final DeviceIdManager deviceIdManager;

  /** {Dispatcher, Device} pair generating by reflecting. */
  private final Map<Class<? extends Dispatcher>, Class<? extends Device>> dispatcherToDevice;

  private final ExternalDeviceManager externalDeviceManager;

  public LocalDeviceDispatch(
      List<Class<? extends Dispatcher>> dispatcherClasses,
      LocalDeviceManager deviceManager,
      ExecutorService threadPool,
      EventBus globalInternalBus,
      ExternalDeviceManager externalDeviceManager) {
    this.dispatcherClasses = ImmutableList.copyOf(dispatcherClasses);
    this.deviceManager = deviceManager;
    this.labStat = StatManager.getInstance().getOrCreateLabStat(LabLocator.LOCALHOST.ip());
    this.threadPool = threadPool;
    this.globalInternalBus = globalInternalBus;
    this.deviceIdManager = DeviceIdManager.getInstance();
    this.dispatchers = new ArrayList<>();
    this.dispatcherToDevice = new HashMap<>();
    this.externalDeviceManager = externalDeviceManager;
  }

  @VisibleForTesting
  LocalDeviceDispatch(
      List<Dispatcher> dispatchers,
      Map<Class<? extends Dispatcher>, Class<? extends Device>> dispatcherToDevice,
      LocalDeviceManager deviceManager,
      ExecutorService threadPool,
      EventBus globalInternalBus,
      ExternalDeviceManager externalDeviceManager) {
    this.dispatcherClasses = ImmutableList.of();
    this.deviceManager = deviceManager;
    this.labStat = StatManager.getInstance().getOrCreateLabStat(LabLocator.LOCALHOST.ip());
    this.threadPool = threadPool;
    this.globalInternalBus = globalInternalBus;
    this.deviceIdManager = DeviceIdManager.getInstance();
    this.dispatchers = dispatchers;
    this.dispatcherToDevice = dispatcherToDevice;
    this.externalDeviceManager = externalDeviceManager;
  }

  @SuppressWarnings("FloggerLogWithCause")
  public void initialize() {
    for (Class<? extends Dispatcher> dispatcherClass : dispatcherClasses) {
      String dispatcherName = dispatcherClass.getSimpleName();

      // Creates Dispatcher instance.
      Dispatcher dispatcher;
      try {
        Constructor<? extends Dispatcher> dispatcherConstructor = dispatcherClass.getConstructor();
        dispatcher = dispatcherConstructor.newInstance();
      } catch (ReflectiveOperationException e) {
        logger.atWarning().withCause(e).log(
            "Failed to generate the classifier instance of classifier type %s", dispatcherName);
        continue;
      }

      // Loads Device class.
      Class<? extends Device> deviceClass;
      try {
        deviceClass = ClassUtil.getDeviceClass(dispatcherClass);
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Device class [%s] of dispatcher [%s] not found (not in runtime_deps of the jar)",
            ClassUtil.getDeviceClassSimpleNameOfDispatcher(dispatcherClass), dispatcherName);
        continue;
      }

      // Checks Dispatcher.precondition().
      Optional<String> dispatcherDisabledReason = dispatcher.precondition();
      if (dispatcherDisabledReason.isPresent()) {
        logger.atWarning().log(
            "Dispatcher [%s] is disabled because [%s]", dispatcherName, dispatcherDisabledReason);
        continue;
      }

      this.dispatcherToDevice.put(dispatcherClass, deviceClass);
      this.dispatchers.add(dispatcher);
    }
  }

  /**
   * Checks the current active devices, updates the device runners. Always keeps one {@link
   * LocalDeviceRunner} thread for one active device.
   *
   * @return whether the device set are changed
   */
  @SuppressWarnings("Interruption")
  @CanIgnoreReturnValue
  public boolean dispatchDevices(DetectionResults detectionResults) throws InterruptedException {
    boolean isChanged = false;

    // Dispatches the devices. Merges the previous round of dispatch result to tolerate a short time
    // of disconnection.
    DispatchResults realtimeDispatchResults = realtimeDispatch(detectionResults);

    // The merged ids which occur in both the current real time dispatch and the previous real time
    // dispatch.
    DispatchResults mergedResults = previousResults;
    previousResults = realtimeDispatchResults;
    mergedResults.mergeFrom(realtimeDispatchResults);
    Set<String> newIds = mergedResults.getDeviceControlIds(DispatchType.LIVE);
    for (Entry<String, Entry<LocalDeviceRunner, Future<?>>> device : devices.entrySet()) {
      String id = device.getKey();
      LocalDeviceRunner runner = device.getValue().getKey();
      Future<?> future = device.getValue().getValue();
      // Will kill the runner, or wait for its termination if:
      // - ID of the runner doesn't show up in the merged ids,
      // - or the runner is not alive,
      // - or the runner thread is cancelled
      String deadReason = null;
      DispatchResult dispatchResult = mergedResults.get(id);
      // Will mark the device as undetected if:
      // - dispatch result of the device is null,
      // - dispatch type of the device is not LIVE or CACHE,
      // - dispatch device type of the device does not match the current device runner and the
      // current device runner is not in cache.
      if (dispatchResult == null
          || !(dispatchResult.dispatchType().equals(DispatchType.LIVE)
              || dispatchResult.dispatchType().equals(DispatchType.CACHE))
          || (!runner.getDevice().getClass().equals(dispatchResult.deviceType())
              && !DeviceCacheManager.getInstance()
                  .getCachedDevices(runner.getDevice().getClass().getSimpleName())
                  .contains(id))) {
        deadReason = "not detected";
      } else if (!runner.isAlive()) {
        deadReason = "not alive";
      } else if (future.isCancelled()) {
        deadReason = "cancelled";
      }
      if (deadReason != null) {
        isChanged = true;
        // Avoids to kill the device thread if the device is tearing down. b/30105986
        if (!future.isCancelled() && !runner.isTearingDown()) {
          if (future.cancel(true)) {
            logger.atInfo().log("Cancel runner thread of dead device %s(%s)", id, deadReason);
          } else {
            logger.atInfo().log(
                "Failed to cancel runner thread of dead device %s(%s)", id, deadReason);
          }
          if (dispatchResult != null
              && dispatchResult.dispatchType().equals(DispatchType.SUB_DEVICE)) {
            // If the device is be recognized as a sub-device, device manager will not manage it
            // anymore, immediately clean up the device info to avoid duplication.
            // Otherwise, it will remove in device runner with a delay.
            DeviceInfoManager.getInstance().remove(id);
          }
        }
        runner.cancel();
        if (runner.isStopped() && future.isDone()) {
          devices.remove(id);
          logger.atInfo().log("Runner thread of dead device %s(%s) stopped", id, deadReason);
          logger.atInfo().log("Post LocalDeviceDownEvent");
          globalInternalBus.post(
              new LocalDeviceDownEvent(
                  id,
                  runner.getDevice().getDeviceUuid(),
                  runner.getDevice().getClass().getSimpleName()));
        } else {
          logger.atInfo().log("Stopping runner thread of dead device %s(%s)", id, deadReason);
        }
      }

      // Increases the device status counter.
      runner.updateStatusStat();

      // If the newIds contains the current runner, removes it from the newIds, so we will not
      // create new runner for it.
      newIds.remove(id);
    }

    // Constructs new {@code LocalDeviceRunner} instance for new device.
    for (String newId : newIds) {
      // Removes the device IDs that are detectable by old detectors, to avoid
      // generating multiple test runner threads for the same device ID.
      LocalDeviceRunner deviceRunner = deviceManager.getLocalDeviceRunner(newId);
      if (deviceRunner != null) {
        logger.atInfo().log(
            "Ignored new device ID %s because there is a %s device runner for it",
            newId, deviceRunner.getDevice().getClass().getSimpleName());
        continue;
      }

      isChanged = true;
      logger.atInfo().log("New device %s", newId);
      LocalDeviceRunner runner;
      try {
        DeviceStat deviceStat = labStat.getOrCreateDeviceStat(newId);
        DispatchResult dispatchResult = mergedResults.get(newId);
        runner =
            new LocalDeviceLifecycleAndTestRunner(
                dispatchResult.deviceId(),
                dispatchResult.deviceType(),
                globalInternalBus,
                deviceStat,
                externalDeviceManager);
        Future<?> future = threadPool.submit(() -> runner.run());
        devices.put(newId, Map.entry(runner, future));
        logger.atInfo().log("Post LocalDeviceUpEvent");
        globalInternalBus.post(
            new LocalDeviceUpEvent(
                newId,
                dispatchResult.deviceId().uuid(),
                dispatchResult.deviceType().getSimpleName()));
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log("Fail to create the device runner for %s", newId);
      }
    }

    if (isChanged) {
      String dispatchResultLogStr =
          mergedResults.getAll().stream()
              .map(
                  dispatchResult ->
                      String.format(
                          "%s-%s-%s",
                          dispatchResult.deviceId().controlId(),
                          dispatchResult.deviceType().getSimpleName(),
                          dispatchResult.dispatchType().name()))
              .collect(joining(", "));
      logger.atInfo().log(
          "Current runner count: %d, new round dispatch result: %s",
          LocalDeviceRunner.getRunnerCount(), dispatchResultLogStr);
    }

    return isChanged;
  }

  /** Checks whether the device type is supported. */
  boolean isDeviceTypeSupported(Class<? extends Device> deviceType) {
    try {
      Class<? extends Dispatcher> dispatcherClass = ClassUtil.getDispatcherClass(deviceType);
      return dispatchers.stream()
          .anyMatch(dispatcher -> dispatcher.getClass().equals(dispatcherClass));
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to find dispatcher class for device type %s: %s",
          deviceType.getSimpleName(), e.getMessage());
      return false;
    }
  }

  /** Checks whether the device is alive. Will double-check with a real-time detection. */
  boolean isDeviceAlive(String deviceUuid, DetectionResults detectionResults)
      throws InterruptedException {
    if (deviceIdManager.containsUuid(deviceUuid)) {
      String deviceControlId = deviceIdManager.getDeviceIdFromUuid(deviceUuid).get().controlId();
      Entry<LocalDeviceRunner, Future<?>> device = devices.get(deviceControlId);
      if (device != null) {
        // Found the device.
        LocalDeviceRunner runner = device.getKey();
        if (runner.isAlive()) {
          DispatchResult dispatchResult = realtimeDispatch(detectionResults).get(deviceControlId);
          if (dispatchResult != null
              && (dispatchResult.dispatchType().equals(DispatchType.LIVE)
                  || dispatchResult.dispatchType().equals(DispatchType.CACHE))
              && runner.getDevice().getClass().equals(dispatchResult.deviceType())) {
            return true;
          } else {
            logger.atInfo().log(
                "Real-time detection shows device %s is disconnected", deviceControlId);
          }
        }
      }
    }
    return false;
  }

  /**
   * Gets the device runner according to its device control id or uuid.
   *
   * @param deviceControlIdOrUuid ID of the device
   * @return the runner, or null if not found
   */
  @Nullable
  public LocalDeviceRunner getDeviceRunner(String deviceControlIdOrUuid) {
    String deviceControlId = deviceControlIdOrUuid;
    if (deviceIdManager.containsUuid(deviceControlIdOrUuid)) {
      deviceControlId =
          deviceIdManager.getDeviceIdFromUuid(deviceControlIdOrUuid).get().controlId();
    }
    Entry<LocalDeviceRunner, Future<?>> device = devices.get(deviceControlId);
    if (device != null) {
      return device.getKey();
    }
    return null;
  }

  /**
   * Gets all the device runners.
   *
   * @param detectionResults whether to do a real-time detection to make sure the devices of the
   *     runners are detectable
   */
  List<LocalDeviceRunner> getDeviceRunners(@Nullable DetectionResults detectionResults)
      throws InterruptedException {
    List<LocalDeviceRunner> runners = new ArrayList<>();
    Set<String> realtimeIds =
        detectionResults != null
            ? realtimeDispatch(detectionResults)
                .getDeviceControlIds(DispatchType.LIVE, DispatchType.CACHE)
            : null;
    for (Entry<LocalDeviceRunner, Future<?>> device : devices.values()) {
      LocalDeviceRunner runner = device.getKey();
      if (realtimeIds != null && !realtimeIds.contains(runner.getDevice().getDeviceControlId())) {
        continue;
      }
      runners.add(runner);
    }
    return runners;
  }

  /**
   * Real-time dispatch the devices.
   *
   * @return the dispatch results are actually detectable.
   */
  private DispatchResults realtimeDispatch(DetectionResults detectionResults)
      throws InterruptedException {
    DispatchResults dispatchResults = new DispatchResults();
    for (Dispatcher dispatcher : dispatchers) {
      try {
        Map<String, DispatchType> realtimeIds =
            dispatcher.dispatchDevices(detectionResults, dispatchResults);
        realtimeIds.forEach(
            (deviceControlId, dispatchType) -> {
              // Removes the control id from detection results.
              detectionResults.remove(deviceControlId);
              if (dispatchType.equals(DispatchType.SUB_DEVICE)) {
                // If the device dispatch as sub device by this dispatcher, force update the
                // dispatch type since it has been  marked as other types by other dispatchers.
                dispatchResults.forceUpdateDispatchType(deviceControlId, DispatchType.SUB_DEVICE);
              } else {
                try {
                  Optional<DeviceId> deviceId =
                      deviceIdManager.getDeviceIdFromControlId(deviceControlId);
                  if (deviceId.isEmpty()) {
                    // Abnormal status, the device is not created from device manager, will ignore.
                    if (dispatchType.equals(DispatchType.CACHE)) {
                      return;
                    }
                    deviceId = Optional.of(dispatcher.generateDeviceId(deviceControlId));
                  }
                  // Override the id map every time, since disconnected device and other device may
                  // share the same uuid.
                  deviceIdManager.add(deviceId.get());
                  dispatchResults.addIfNotExist(
                      DispatchResult.of(
                          deviceId.get(),
                          dispatchType,
                          dispatcherToDevice.get(dispatcher.getClass())));
                } catch (MobileHarnessException e) {
                  logger.atWarning().withCause(e).log(
                      "Failed to get uuid for device: %s", deviceControlId);
                }
              }
            });
      } catch (MobileHarnessException e) {
        logger.atWarning().log("Failed to dispatch devices: %s", e.getMessage());
      }
    }
    return dispatchResults;
  }
}
