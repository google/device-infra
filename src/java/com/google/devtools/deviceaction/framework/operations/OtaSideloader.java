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

package com.google.devtools.deviceaction.framework.operations;

import static com.google.devtools.deviceaction.common.utils.Conditions.checkArgument;
import static com.google.devtools.deviceaction.common.utils.Verify.verify;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.utils.TimeoutMonitor;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.RebootMode;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager.Lease;
import com.google.devtools.mobileharness.shared.util.quota.proto.Quota.QuotaKey;
import java.io.File;
import java.time.Duration;

/** An {@link Operation} to sideload ota package. */
public class OtaSideloader implements Operation {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Includes booting to sideload and acquiring quota.
  private static final Duration EXTRA_WAIT_NEEDED = Duration.ofMinutes(15);

  private final Duration extraWaitNeeded;

  private final AndroidPhone device;

  private final QuotaManager quotaManager;

  public OtaSideloader(AndroidPhone device, QuotaManager quotaManager) {
    this(device, quotaManager, EXTRA_WAIT_NEEDED);
  }

  @VisibleForTesting
  OtaSideloader(AndroidPhone device, QuotaManager quotaManager, Duration extraWaitNeeded) {
    this.device = device;
    this.quotaManager = quotaManager;
    this.extraWaitNeeded = extraWaitNeeded;
  }

  /** Sideloads an OTA package. */
  public void sideload(File otaPackage, Duration timeout, boolean useAutoReboot)
      throws DeviceActionException, InterruptedException {
    checkArgument(
        otaPackage.isFile(),
        ErrorType.CUSTOMER_ISSUE,
        "The OTA package file %s is missing or invalid.",
        otaPackage.getAbsolutePath());
    try (TimeoutMonitor timeoutMonitor =
        TimeoutMonitor.createAndStart(timeout.plus(extraWaitNeeded))) {
      try {
        prepareSideload(useAutoReboot);
        logger.atInfo().log(
            "Reboot to sideload on %s within %d sec",
            device.getUuid(), timeoutMonitor.getElapsedSinceLastCheck().toSeconds());
      } catch (DeviceActionException e) {
        device.reboot();
        throw e;
      }
      try (Lease ignored = quotaManager.acquire(QuotaKey.FASTBOOT_FLASH_DEVICE, 1)) {
        logger.atInfo().log(
            "Get quota after %d sec", timeoutMonitor.getElapsedSinceLastCheck().toSeconds());
        verify(
            timeoutMonitor.getRemainingTimeout().compareTo(timeout) > 0,
            "Not enough time to sideload. Abort");

        logger.atInfo().log("Sideloading package %s to %s", otaPackage, device.getUuid());
        device.sideload(otaPackage, timeout, Duration.ofSeconds(2));
        logger.atInfo().log(
            "Sideloaded package %s to %s within %d sec",
            otaPackage,
            device.getUuid(),
            timeoutMonitor.getElapsedSinceLastCheckSafely().toSeconds());
      }
      recoverDevice(useAutoReboot);
    }
  }

  private void prepareSideload(boolean useAutoReboot)
      throws DeviceActionException, InterruptedException {
    logger.atInfo().log("Start sideloading %s", device.getUuid());
    if (useAutoReboot) {
      device.reboot(RebootMode.SIDELOAD_AUTO_REBOOT);
    } else {
      device.reboot(RebootMode.SIDELOAD);
    }
  }

  private void recoverDevice(boolean useAutoReboot)
      throws DeviceActionException, InterruptedException {
    logger.atInfo().log("Wait for device %s recover.", device.getUuid());
    if (useAutoReboot) {
      device.waitUntilReady();
    } else {
      // After sideloading package, device should transition to recovery mode.
      device.waitUntilReady(RebootMode.RECOVERY);
      logger.atInfo().log("Wait for %s reboot to userspace.", device.getUuid());
      device.reboot();
    }
  }
}
