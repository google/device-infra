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

package com.google.devtools.mobileharness.platform.android.systemstate;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Enums.FastbootProperty;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Fastboot;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.RebootMode;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.time.Duration;

/**
 * Utility class for managing Generic System Image (GSI) and Dynamic System Update (DSU) state on
 * Android devices.
 */
public class GsiTool {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DSU_RUNNING_PROPERTY = "ro.gsid.image_running";
  private static final String GSI_TOOL_DISABLE_CMD = "gsi_tool disable";
  private static final String GSI_TOOL_WIPE_CMD = "gsi_tool wipe";
  private static final Duration DEVICE_READY_TIMEOUT = Duration.ofMinutes(5);
  private static final Duration FASTBOOT_GSI_WIPE_TIMEOUT = Duration.ofMinutes(2);

  private final AndroidAdbInternalUtil androidAdbInternalUtil;
  private final AndroidAdbUtil androidAdbUtil;
  private final Adb adb;
  private final AndroidSystemStateUtil systemStateUtil;
  private final Fastboot fastboot;

  public GsiTool(
      AndroidAdbInternalUtil androidAdbInternalUtil,
      AndroidAdbUtil androidAdbUtil,
      Adb adb,
      AndroidSystemStateUtil systemStateUtil,
      Fastboot fastboot) {
    this.androidAdbInternalUtil = androidAdbInternalUtil;
    this.androidAdbUtil = androidAdbUtil;
    this.adb = adb;
    this.systemStateUtil = systemStateUtil;
    this.fastboot = fastboot;
  }

  /**
   * Disables and wipes any active Dynamic System Update (DSU) guest image on the device and reboots
   * back to the stock system image.
   *
   * <p>When a device is booted into a DSU GSI image ({@code ro.gsid.image_running=1}), {@code cmd
   * testharness enable} fails with {@code SecurityException: Wiping data is not allowed while in
   * DSU mode}. If exiting the DSU image causes the device to boot into recovery mode (e.g. due to a
   * {@code /data/misc} fscrypt policy mismatch), the recovery fallback reboots into userspace
   * {@code fastbootd} and runs {@code fastboot gsi wipe} and {@code fastboot -w}, which erases
   * {@code userdata}. For devices not in DSU mode, this method is a fast no-op after reading {@code
   * ro.gsid.image_running}.
   */
  public void disableDsuIfRunning(String deviceId, boolean isOverTcpDevice)
      throws MobileHarnessException, InterruptedException {
    String dsuRunning =
        androidAdbUtil.getProperty(deviceId, ImmutableList.of(DSU_RUNNING_PROPERTY)).trim();
    if (!dsuRunning.equals("1") && !Ascii.equalsIgnoreCase(dsuRunning, "true")) {
      return;
    }
    logger.atInfo().log(
        "Device %s is running in DSU mode (%s=%s). Disabling DSU and rebooting to stock image"
            + " before factory reset.",
        deviceId, DSU_RUNNING_PROPERTY, dsuRunning);
    try {
      String unused = adb.runShellWithRetry(deviceId, GSI_TOOL_DISABLE_CMD);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to disable DSU via gsi_tool on device %s; continuing with wipe/reboot.",
          deviceId);
    }
    try {
      String unused = adb.runShellWithRetry(deviceId, GSI_TOOL_WIPE_CMD);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to wipe DSU via gsi_tool on device %s; continuing with reboot.", deviceId);
    }
    try {
      rebootAndWaitForReady(deviceId, isOverTcpDevice, DEVICE_READY_TIMEOUT);
    } catch (MobileHarnessException e) {
      // When exiting a DSU GSI image back to the stock image, AOSP init may fail with
      // `reboot_into_recovery({"--reason=set_policy_failed:/data/misc"})` if the fscrypt
      // encryption policy on /data/misc mismatches the stock image (see b/574335790#comment21).
      // If the device lands in recovery mode, recover it via userspace fastbootd (`fastboot gsi
      // wipe` + `fastboot -w`).
      if (!isOverTcpDevice
          && Flags.enableFastbootInAndroidRealDevice.getNonNull()
          && androidAdbInternalUtil
              .getDeviceSerialsByState(DeviceState.RECOVERY)
              .contains(deviceId)) {
        logger.atWarning().withCause(e).log(
            "Device %s entered recovery mode after exiting DSU; recovering via fastbootd wipe.",
            deviceId);
        recoverDsuDeviceFromRecoveryViaFastboot(deviceId, DEVICE_READY_TIMEOUT);
      } else {
        throw e;
      }
    }
  }

  private void recoverDsuDeviceFromRecoveryViaFastboot(String deviceId, Duration deviceReadyTimeout)
      throws MobileHarnessException, InterruptedException {
    systemStateUtil.reboot(deviceId, RebootMode.FASTBOOT);
    try {
      String isUserspace =
          Ascii.toLowerCase(fastboot.getVar(deviceId, FastbootProperty.IS_USERSPACE).trim());
      if (!isUserspace.contains("yes")) {
        logger.atWarning().log(
            "Device %s did not enter userspace fastbootd (is-userspace=%s); skipping DSU"
                + " fastboot wipe.",
            deviceId, isUserspace);
      } else {
        try {
          String unused =
              fastboot.runWithRetry(
                  deviceId,
                  new String[] {"gsi", "wipe"},
                  FASTBOOT_GSI_WIPE_TIMEOUT,
                  /* flashSemaphore= */ false);
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log(
              "Failed to run 'fastboot gsi wipe' on device %s; continuing with fastboot wipe.",
              deviceId);
        }
        String unused = fastboot.wipe(deviceId, /* retryTask= */ null);
      }
    } finally {
      try {
        fastboot.reboot(deviceId);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to reboot device %s from fastboot mode.", deviceId);
      }
    }
    waitForDeviceReady(deviceId, /* isOverTcpDevice= */ false, deviceReadyTimeout);
  }

  private void rebootAndWaitForReady(
      String deviceId, boolean isOverTcpDevice, Duration deviceReadyTimeout)
      throws MobileHarnessException, InterruptedException {
    systemStateUtil.reboot(deviceId);
    if (isOverTcpDevice) {
      systemStateUtil.waitForOverTcpDeviceConnection(deviceId, deviceReadyTimeout);
    }
    waitForDeviceReady(deviceId, isOverTcpDevice, deviceReadyTimeout);
  }

  private void waitForDeviceReady(
      String deviceId, boolean isOverTcpDevice, Duration deviceReadyTimeout)
      throws MobileHarnessException, InterruptedException {
    if (!isOverTcpDevice) {
      systemStateUtil.waitForState(deviceId, DeviceConnectionState.DEVICE, deviceReadyTimeout);
    }
    systemStateUtil.waitUntilReady(deviceId, deviceReadyTimeout);
  }
}
