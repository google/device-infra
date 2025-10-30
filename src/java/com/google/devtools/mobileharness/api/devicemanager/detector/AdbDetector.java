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

package com.google.devtools.mobileharness.api.devicemanager.detector;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCacheManager;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.platform.android.overtcp.OverTcpDeviceCache;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidRealDeviceProxyManager;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.testbed.TestbedUtil;
import com.google.devtools.mobileharness.platform.testbed.config.GlobalTestbedLoader;
import com.google.devtools.mobileharness.platform.testbed.config.TestbedConfig;
import com.google.devtools.mobileharness.platform.testbed.config.TestbedLoader;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil.KillSignal;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidRealDevice;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Detector for adb. */
public class AdbDetector implements Detector {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * The max rounds of detection when adb detect command failed with "could not install
   * *smartsocket* listener: Address already in use". If reached, will kill all adb server processes
   * to try to recover. Since adb detects devices with 2 seconds interval, so here set it 450 which
   * is around 15 mins.
   */
  @VisibleForTesting static final int MAX_ADB_ADDRESS_IN_USE_ERROR_ROUNDS = 450;

  private static final String ADB_ADDRESS_IN_USE_ERROR =
      "could not install *smartsocket* listener: Address already in use";

  /** The rounds of detection when adb detects no devices. */
  private static final AtomicInteger noDeviceDetectionRounds = new AtomicInteger(0);

  /**
   * The rounds of detection when adb detect command failed with "could not install *smartsocket*
   * listener: Address already in use".
   */
  private static final AtomicInteger adbAddressInUseErrorRounds = new AtomicInteger(0);

  private final AndroidAdbInternalUtil adbInternalUtil;
  private final OverTcpDeviceCache cache;
  private final TestbedLoader testbedLoader;
  private final TestbedUtil testbedUtil;
  private final ApiConfig apiConfig;
  private final SystemUtil systemUtil;

  public AdbDetector() {
    this(
        new AndroidAdbInternalUtil(),
        ApiConfig.getInstance(),
        GlobalTestbedLoader.getInstance(),
        new TestbedUtil(),
        new SystemUtil());
  }

  @VisibleForTesting
  AdbDetector(
      AndroidAdbInternalUtil adbInternalUtil,
      ApiConfig apiConfig,
      TestbedLoader testbedLoader,
      TestbedUtil testbedUtil,
      SystemUtil systemUtil) {
    this.adbInternalUtil = adbInternalUtil;
    this.apiConfig = apiConfig;
    this.testbedLoader = testbedLoader;
    this.testbedUtil = testbedUtil;
    this.cache = new OverTcpDeviceCache(adbInternalUtil, logger);
    this.systemUtil = systemUtil;
  }

  @Override
  public boolean precondition() throws InterruptedException {

    Optional<String> adbUnsupportedReason = adbInternalUtil.checkAdbSupport();
    if (adbUnsupportedReason.isEmpty()) {
      try {
        logger.atInfo().log("Ensure adb server is alive.");
        ImmutableList<String> unused = adbInternalUtil.listDevices(/* timeout= */ null);
      } catch (MobileHarnessException e) {
        logger.atInfo().withCause(e).log("Failed to check adb.");
      }
      return true;
    } else {
      logger.atInfo().log(
          "%s is disabled because ADB is not supported, reason=[%s]",
          getClass().getSimpleName(), adbUnsupportedReason.get());
      return false;
    }
  }

  /**
   * Detects the adb devices.
   *
   * @return Lists of {@link DetectionResult} of the current active adb devices.
   * @throws MobileHarnessException if fails to detect devices
   * @throws InterruptedException if the current thread or its sub-thread is {@linkplain
   *     Thread#interrupt() interrupted} by another thread
   */
  @Override
  public List<DetectionResult> detectDevices() throws MobileHarnessException, InterruptedException {
    try {
      Map<String, DeviceState> ids = adbInternalUtil.getDeviceSerialsAsMap();
      int maxNoDeviceDetectionRounds = Flags.instance().adbMaxNoDeviceDetectionRounds.getNonNull();
      if (needCheckAdbProcess(maxNoDeviceDetectionRounds)) {
        if (ids.isEmpty() && getCachedDevices().isEmpty()) {
          if (noDeviceDetectionRounds.getAndIncrement() == maxNoDeviceDetectionRounds) {
            // Kills the ADB server and let it restart automatically.
            logger.atInfo().log(
                "Adb detects no devices for %s rounds. "
                    + "In case adb is not working, trying to recover the adb by starting it...",
                maxNoDeviceDetectionRounds);
            adbInternalUtil.killAdbServer();
            noDeviceDetectionRounds.set(0);
          }
        } else {
          noDeviceDetectionRounds.set(0);
        }
        // When it successfully gets device serials with command "adb devices" without any command
        // error, reset adbAddressInUseErrorRounds.
        adbAddressInUseErrorRounds.set(0);
      }
      if (needRedetectDevice(ids)) {
        // The adb detection result have been changed, use the realtime result.
        ids = adbInternalUtil.getDeviceSerialsAsMap();
      }
      return ids.entrySet().stream()
          .filter(entry -> needKeepDevice(entry.getKey()))
          .map(entry -> DetectionResult.of(entry.getKey(), DetectionType.ADB, entry.getValue()))
          .collect(toImmutableList());
    } catch (MobileHarnessException e) {
      killAllAdbIfNeeded(e);
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DM_DETECTOR_ADB_ERROR, "AdbDetector failed to detect devices", e);
    }
  }

  /** Gets the cached device id from DeviceCache. */
  private ImmutableSet<String> getCachedDevices() {
    DeviceCacheManager deviceCacheManager = DeviceCacheManager.getInstance();
    return Stream.of("AndroidRealDevice", "AndroidLocalEmulator")
        .flatMap(type -> deviceCacheManager.getCachedDevices(type).stream())
        .collect(toImmutableSet());
  }

  /** Returns whether the detector need to check adb process. */
  private boolean needCheckAdbProcess(int maxNoDeviceDetectionRounds) {
    return maxNoDeviceDetectionRounds > 0 && !DeviceUtil.inSharedLab();
  }

  /**
   * Returns whether the detector need to redetect the devices based on the detected id; e.g. need
   * to redetect when the overtcp configuration is updated.
   */
  private boolean needRedetectDevice(Map<String, DeviceState> ids) throws InterruptedException {
    return doesOverTcpDeviceChange(ids);
  }

  /** Returns whether the device id need to be kept. */
  private boolean needKeepDevice(String id) {
    return !AndroidRealDeviceProxyManager.isRealDeviceProxy(id);
  }

  /** Connects/Disconnects the devices which needs to be connected/disconnected. */
  private boolean doesOverTcpDeviceChange(Map<String, DeviceState> ids)
      throws InterruptedException {
    Set<String> connectedId =
        ids.entrySet().stream()
            .filter(entry -> entry.getValue().equals(DeviceState.DEVICE))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    ImmutableSet<String> cachedDevices = getCachedDevices();
    connectedId.addAll(cachedDevices);

    // Reads over tcp device id from api config.
    Set<String> newOverTcpIds = new HashSet<>(apiConfig.getOverTcpDeviceControlIds());
    // Reads over tcp device id from testbed config.
    try {
      // The function will read file directly and AdbDetector should not do that, will migrate to
      // read memory data instead of disk data after storing testbed config  in config server.
      Collection<TestbedConfig> testbedConfigs = testbedLoader.getTestbedConfigs().values();
      newOverTcpIds.addAll(
          testbedUtil.getAllIdsOfType(testbedConfigs, AndroidRealDevice.class).stream()
              .filter(DeviceUtil::isOverTcpDevice)
              .collect(toImmutableList()));
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to read over tcp device from testbed configs");
    }

    return !cache
        .update(newOverTcpIds, new HashSet<>(connectedId), cachedDevices)
        .equals(connectedId);
  }

  private void killAllAdbIfNeeded(MobileHarnessException e) {
    if (DeviceUtil.inSharedLab()) {
      logger.atInfo().log("The devices are not managed by MH, skip killing adb processes.");
      return;
    }
    Optional<CommandFailureException> commandFailureException =
        getCommandFailureExceptionInCauseChain(e);
    if (commandFailureException.isEmpty()) {
      logger.atInfo().log(
          "Not found CommandFailureException in exception chain, skip killing adb processes.");
      return;
    }
    if (commandFailureException.get().getMessage().contains(ADB_ADDRESS_IN_USE_ERROR)) {
      if (adbAddressInUseErrorRounds.incrementAndGet() == MAX_ADB_ADDRESS_IN_USE_ERROR_ROUNDS) {
        logger.atInfo().log(
            "Adb consecutively failed to detect for %d rounds due to error [%s]. The adb server may"
                + " not work well which is likely impacting the all tests in the host, trying to"
                + " kill all adb processes to recover.",
            MAX_ADB_ADDRESS_IN_USE_ERROR_ROUNDS, ADB_ADDRESS_IN_USE_ERROR);
        killAllAdbProcesses();
        adbAddressInUseErrorRounds.set(0);
      }
    } else {
      // When find an error not related with ADB_ADDRESS_IN_USE_ERROR in the middle, reset
      adbAddressInUseErrorRounds.set(0);
    }
  }

  private static Optional<CommandFailureException> getCommandFailureExceptionInCauseChain(
      MobileHarnessException mhe) {
    Throwable throwable = mhe;
    while (throwable != null) {
      if (throwable instanceof CommandFailureException) {
        return Optional.of((CommandFailureException) throwable);
      }
      throwable = throwable.getCause();
    }
    return Optional.empty();
  }

  private void killAllAdbProcesses() {
    logger.atInfo().log("Trying to kill all adb related processes.");
    try {
      if (systemUtil.killAllProcesses("adb", KillSignal.SIGKILL)) {
        logger.atInfo().log("Successfully killed all adb related processes.");
      } else {
        logger.atWarning().log(
            "Failed to kill adb process with killall command as no process exists.");
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to kill all adb processes: %s", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.atWarning().withCause(e).log("Interrupted when killall adb process");
    }
  }
}
