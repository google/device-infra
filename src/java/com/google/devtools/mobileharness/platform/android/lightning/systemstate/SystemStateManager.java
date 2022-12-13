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

package com.google.devtools.mobileharness.platform.android.lightning.systemstate;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.platform.android.lightning.shared.SharedLogUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.time.Duration;
import javax.annotation.Nullable;

/**
 * Class for managing Android device system state.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 */
public class SystemStateManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Timeout of probing device/emulator readiness. */
  @VisibleForTesting static final Duration DEVICE_READY_TIMEOUT = Duration.ofMinutes(5);

  @VisibleForTesting static final String STATE_DEVICE_NOT_DETECTED = "DEVICE NOT DETECTED";

  private final AndroidSystemStateUtil systemStateUtil;

  private final AndroidAdbInternalUtil adbInternalUtil;

  private final DeviceCache deviceCache;

  /**
   * Oxygen device is of the format <serverUrl>:<waterfallPort>:<session_id> server url has numbers
   * and "." is at least 8 digits waterfall port is upto 5 digits and session is an alphanuermic
   * string.
   */
  private static final String OXYGEN_DEVICE_REGEX =
      "(^[0-9\\.]{8,}):([0-9]){2,5}:([a-zA-Z0-9-]{6,}$)";

  public SystemStateManager() {
    this(new AndroidSystemStateUtil(), new AndroidAdbInternalUtil(), DeviceCache.getInstance());
  }

  @VisibleForTesting
  SystemStateManager(
      AndroidSystemStateUtil systemStateUtil,
      AndroidAdbInternalUtil adbInternalUtil,
      DeviceCache deviceCache) {
    this.systemStateUtil = systemStateUtil;
    this.adbInternalUtil = adbInternalUtil;
    this.deviceCache = deviceCache;
  }

  /**
   * Roots device if possible.
   *
   * @param device the device being rooted
   * @return {@code true} if device is rooted, otherwise {@code false}
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  @CanIgnoreReturnValue
  public boolean becomeRoot(Device device) throws MobileHarnessException, InterruptedException {
    return becomeRoot(device.getDeviceId(), device.getClass().getSimpleName());
  }

  /**
   * Roots device if possible.
   *
   * @param serial String of the serial number for the device being rooted.
   * @param deviceType String, the type of the device to cache, e.g., "AndroidRealDevice",
   *     "IosRealDevice", etc., in most cases which is the simple class name of a {@code Device}
   *     instance
   * @return {@code true} if device is rooted, otherwise {@code false}
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public boolean becomeRoot(String serial, String deviceType)
      throws MobileHarnessException, InterruptedException {
    Duration waitUntilReadyTimeout = Duration.ofMinutes(5);
    Duration cacheDuration =
        AndroidSystemStateUtil.CHECK_ROOT_TIMEOUT
            .plus(AndroidSystemStateUtil.CHECK_ROOT_INTERVAL)
            .multipliedBy(AndroidSystemStateUtil.CHECK_ROOT_RETRY_TIMES)
            .plus(waitUntilReadyTimeout);
    cacheDevice(serial, deviceType, cacheDuration);
    try {
      boolean rooted = systemStateUtil.becomeRoot(serial);
      systemStateUtil.waitUntilReady(serial, waitUntilReadyTimeout);
      return rooted;
    } finally {
      invalidateCacheDevice(serial);
    }
  }

  /**
   * Checks if real device or emulator is detected by adb as online.
   *
   * <p>It'll log device current state if it's not online.
   *
   * @param serial serial number of device
   * @return {@code true} if device is online, otherwise {@code false}
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public boolean isOnline(String serial) throws MobileHarnessException, InterruptedException {
    return isOnline(serial, /* logDeviceStateIfNotOnline= */ true);
  }

  /**
   * Checks if real device or emulator is detected by adb as online.
   *
   * @param serial serial number of device
   * @param logDeviceStateIfNotOnline log device current state if it's not online.
   * @return {@code true} if device is online, otherwise {@code false}
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public boolean isOnline(String serial, boolean logDeviceStateIfNotOnline)
      throws MobileHarnessException, InterruptedException {
    if (serial.matches(OXYGEN_DEVICE_REGEX)) {
      logger.atInfo().log("Found an oxygen device with device ID %s", serial);
      return systemStateUtil.isOxygenDeviceOnline(serial);
    }
    DeviceState deviceState = adbInternalUtil.getDeviceSerialsAsMap().getOrDefault(serial, null);
    if (DeviceState.DEVICE.equals(deviceState)) {
      return true;
    } else if (logDeviceStateIfNotOnline) {
      logger.atWarning().log(
          "Device %s is not online, current state: %s",
          serial, deviceState == null ? STATE_DEVICE_NOT_DETECTED : deviceState);
    }
    return false;
  }

  /**
   * Reboots the device and waits until device is ready for accepting operations.
   *
   * @param device the device being rebooted
   * @param log log of the currently running test, usually from {@code TestInfo}
   * @param deviceReadyTimeout max wait time for checking the readiness of the device, uses {@link
   *     #DEVICE_READY_TIMEOUT} if it's {@code null}
   * @throws MobileHarnessException if device is not ready after waiting for {@code
   *     deviceReadyTimeout}, or fails to execute commands or timeout
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void reboot(
      Device device, @Nullable LogCollector<?> log, @Nullable Duration deviceReadyTimeout)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    if (!device.canReboot()) {
      SharedLogUtil.logMsg(
          logger, log, "Skip rebooting device %s as it doesn't support reboot", deviceId);
      return;
    }

    deviceReadyTimeout = deviceReadyTimeout == null ? DEVICE_READY_TIMEOUT : deviceReadyTimeout;
    Duration cacheDuration = deviceReadyTimeout.multipliedBy(2);
    SharedLogUtil.logMsg(
        logger, log, "Cache device %s before reboot with %s", deviceId, cacheDuration);
    cacheDevice(device, cacheDuration);
    try {
      device.reboot();
      // `adb wait-for-device` exits with error code when using a proxied device. Therefore, we wait
      // for the device ready by using `systemStateUtil.waitUntilReady` instead.
      if (!DeviceUtil.isOverTcpDevice(deviceId)) {
        systemStateUtil.waitForDevice(deviceId, deviceReadyTimeout);
      }
      systemStateUtil.waitUntilReady(deviceId, deviceReadyTimeout);
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.SYSTEM_STATE_MANAGER_REBOOT_DEVICE_ERROR, e.getMessage(), e);
    } finally {
      invalidateCacheDevice(device);
    }

    becomeRoot(device);
    SharedLogUtil.logMsg(logger, log, "Device %s reboot finished.", deviceId);
  }

  private void cacheDevice(Device device, Duration timeout) {
    cacheDevice(device.getDeviceId(), device.getClass().getSimpleName(), timeout);
  }

  private void cacheDevice(String deviceId, String deviceType, Duration timeout) {
    deviceCache.cache(deviceId, deviceType, timeout);
  }

  private void invalidateCacheDevice(Device device) {
    invalidateCacheDevice(device.getDeviceId());
  }

  private void invalidateCacheDevice(String deviceId) {
    deviceCache.invalidateCache(deviceId);
  }
}
