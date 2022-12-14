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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCacheManager;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil.KillSignal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Detector for adb. */
public class BaseAdbDetector implements Detector {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The max rounds of detection when adb detects no devices. If reached, will restart the adb. */
  @VisibleForTesting static final int MAX_NO_DEVICE_DETECTION_ROUNDS = 20;

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

  final AndroidAdbInternalUtil adbInternalUtil;
  final SystemUtil systemUtil;

  public BaseAdbDetector() {
    this(new AndroidAdbInternalUtil(), new SystemUtil());
  }

  BaseAdbDetector(AndroidAdbInternalUtil adbInternalUtil, SystemUtil systemUtil) {
    this.adbInternalUtil = adbInternalUtil;
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
      if (needCheckAdbProcess()) {
        if (ids.isEmpty() && getCachedDevices().isEmpty()) {
          if (noDeviceDetectionRounds.getAndIncrement() == MAX_NO_DEVICE_DETECTION_ROUNDS) {
            // Kills the ADB server and let it restart automatically.
            logger.atInfo().log(
                "Adb detects no devices for %s rounds. "
                    + "In case adb is not working, trying to recover the adb by starting it...",
                MAX_NO_DEVICE_DETECTION_ROUNDS);
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
  Set<String> getCachedDevices() {
    Set<String> cachedRealDevices = new HashSet<>();
    cachedRealDevices.addAll(
        DeviceCacheManager.getInstance().getCachedDevices("AndroidRealDevice"));
    return cachedRealDevices;
  }

  /** Returns whether the detector need to check adb process. */
  boolean needCheckAdbProcess() {
    return true;
  }

  /**
   * Returns whether the detector need to redetect the devices based on the detected id; e.g. need
   * to redetect when the overtcp configuration is updated.
   */
  boolean needRedetectDevice(Map<String, DeviceState> ids) throws InterruptedException {
    return false;
  }

  /** Returns whether the device id need to be kept. */
  boolean needKeepDevice(String id) {
    return true;
  }

  private void killAllAdbIfNeeded(MobileHarnessException e) {
    if (!needCheckAdbProcess()) {
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
