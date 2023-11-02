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

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.TimeLimiter;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** An {@link Operation} to sideload ota package. */
public class OtaSideloader implements Operation {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AndroidPhone device;

  private final QuotaManager quotaManager;

  private final TimeLimiter timeLimiter;

  public OtaSideloader(AndroidPhone device, QuotaManager quotaManager, TimeLimiter timeLimiter) {
    this.device = device;
    this.quotaManager = quotaManager;
    this.timeLimiter = timeLimiter;
  }

  /** Sideloads an OTA package. */
  public void sideload(File otaPackage, Duration timeout, boolean useAutoReboot)
      throws DeviceActionException, InterruptedException {
    checkArgument(
        otaPackage.isFile(),
        ErrorType.CUSTOMER_ISSUE,
        "The OTA package file %s is missing or invalid.",
        otaPackage.getAbsolutePath());
    try {
      timeLimiter.callWithTimeout(
          () -> {
            try (TimeoutMonitor timeoutMonitor = TimeoutMonitor.createAndStart(timeout)) {
              if (useAutoReboot) {
                device.reboot(RebootMode.SIDELOAD_AUTO_REBOOT);
              } else {
                device.reboot(RebootMode.SIDELOAD);
              }

              try (Lease ignored = quotaManager.acquire(QuotaKey.FASTBOOT_FLASH_DEVICE, 1)) {
                device.sideload(
                    otaPackage, timeoutMonitor.getRemainingTimeout(), Duration.ofSeconds(2));
              }

              if (useAutoReboot) {
                device.waitUntilReady(timeoutMonitor.getRemainingTimeout());
              } else {
                // After sideloading package, device should transition to recovery mode.
                device.waitUntilReady(RebootMode.RECOVERY, timeoutMonitor.getRemainingTimeout());
                logger.atInfo().log(
                    "Sideloading completed on %s, rebooting and waiting for boot complete.",
                    device.getUuid());
                // Now reboot to userspace
                device.reboot(timeoutMonitor.getRemainingTimeout());
              }
            }
            return null;
          },
          timeout);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof DeviceActionException) {
        throw (DeviceActionException) e.getCause();
      }
      throw new DeviceActionException(
          "UNKNOWN_EXECUTION_ERROR", ErrorType.UNDETERMINED, "Unknown error.", e);
    } catch (TimeoutException e) {
      throw new DeviceActionException("TIMEOUT", ErrorType.UNDETERMINED, "TIMEOUT!", e);
    }
  }
}
