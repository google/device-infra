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

package com.google.devtools.mobileharness.platform.android.xts.plugin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Plugin for checking device status and unlocking device if necessary. */
@Plugin(type = PluginType.LAB)
public final class AtsDeviceRecoveryPlugin {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // The passwords are based on
  // google3/third_party/java_src/tradefederation_core/tools/tradefederation/core/res/src/config/checker/baseline_config.json
  private static final ImmutableList<String> UNLOCK_PASSWORDS =
      ImmutableList.of("0000", "1234", "12345", "private");
  private final Adb adb;
  private final AndroidSystemStateUtil androidSystemStateUtil;

  public AtsDeviceRecoveryPlugin() {
    this(new Adb(), new AndroidSystemStateUtil());
  }

  @VisibleForTesting
  AtsDeviceRecoveryPlugin(Adb adb, AndroidSystemStateUtil androidSystemStateUtil) {
    this.adb = adb;
    this.androidSystemStateUtil = androidSystemStateUtil;
  }

  @Subscribe
  public void onTestStarting(LocalTestStartingEvent event) throws InterruptedException {
    recoverDevices(event.getLocalDevices());
  }

  @Subscribe
  public void onTestEnding(LocalTestEndingEvent event) throws InterruptedException {
    recoverDevices(event.getLocalDevices());
  }

  private void recoverDevices(Map<String, Device> devices) throws InterruptedException {
    if (devices.isEmpty()) {
      return;
    }
    // Give the devices under test total of ats_device_recovery_timeout time to be ready and
    // perform recovery steps.
    Instant endTime = Instant.now().plus(Flags.instance().atsDeviceRecoveryTimeout.getNonNull());
    for (Device device : devices.values()) {
      if (device.getDeviceTypes().contains("AndroidRealDevice")) {
        recoverDevice(device.getDeviceId(), endTime);
      }
    }
  }

  private void recoverDevice(String deviceId, Instant endTime) throws InterruptedException {

    // The time until `endTime` is used as a timeout to wait for the device to be ready.
    Duration remainingTimeout = Duration.between(Instant.now(), endTime);
    if (remainingTimeout.isNegative() || remainingTimeout.isZero()) {
      logger.atWarning().log(
          "Skipping waiting for device %s to be ready as end time has passed.", deviceId);
    } else {
      try {
        logger.atInfo().log("Waiting for device %s to be online", deviceId);
        androidSystemStateUtil.waitUntilReady(deviceId, remainingTimeout);
        logger.atInfo().log("Device %s is online", deviceId);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Device %s did not come online within %d seconds",
            deviceId, remainingTimeout.toSeconds());
        // Continue with other recovery steps even if waitUntilReady failed.
      }
    }

    boolean isOnline = false;
    try {
      isOnline = androidSystemStateUtil.isOnline(deviceId);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to check if device %s is online", deviceId);
      return;
    }

    if (!isOnline) {
      logger.atInfo().log("Device %s is not online", deviceId);
      return;
    }

    if (!isDeviceUnlocked(deviceId)) {
      logger.atInfo().log("Device %s is locked, unlocking", deviceId);
      boolean unlocked = false;
      for (String password : UNLOCK_PASSWORDS) {
        if (unlockDevice(deviceId, password)) {
          unlocked = isDeviceUnlocked(deviceId);
          break;
        }
      }

      if (unlocked) {
        logger.atInfo().log("Device %s unlocked and verified", deviceId);
      } else {
        logger.atWarning().log("Failed to unlock device %s", deviceId);
      }
    } else {
      logger.atInfo().log("Device %s is already unlocked", deviceId);
    }
  }

  private boolean isDeviceUnlocked(String deviceId) throws InterruptedException {
    try {
      String output = adb.runShell(deviceId, "locksettings get-disabled");
      return output.contains("true");
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to check device %s lock status", deviceId);
      return false;
    }
  }

  private boolean unlockDevice(String deviceId, String password) throws InterruptedException {
    try {
      String command = String.format("locksettings clear --old %s", password);
      String output = adb.runShell(deviceId, command);
      if (output.contains("Lock credential cleared")) {
        return true;
      } else {
        logger.atWarning().log(
            "Failed to unlock device %s with password %s, output: %s", deviceId, password, output);
        return false;
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to unlock device %s with password %s", deviceId, password);
      return false;
    }
  }
}
