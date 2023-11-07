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

import static com.google.devtools.deviceaction.common.utils.Conditions.checkState;
import static com.google.devtools.deviceaction.common.utils.Verify.verify;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.utils.ResourceHelper;
import com.google.devtools.deviceaction.common.utils.TimeoutMonitor;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager.Lease;
import com.google.devtools.mobileharness.shared.util.quota.proto.Quota.QuotaKey;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/** An {@link Operation} to flash the device with image zip files. */
public class ImageZipFlasher implements Operation {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String ANDROID_SERIAL_ENV = "ANDROID_SERIAL";

  private static final Duration QUOTA_WAIT_TIMEOUT = Duration.ofMinutes(10);

  private final Duration qutaWaitTimeout;

  private final AndroidPhone device;

  private final LocalFileUtil fileUtil;

  private final ResourceHelper resourceHelper;

  private final QuotaManager quotaManager;

  private final CommandExecutor executor;

  public ImageZipFlasher(
      AndroidPhone device,
      LocalFileUtil fileUtil,
      ResourceHelper resourceHelper,
      QuotaManager quotaManager,
      CommandExecutor executor) {
    this(device, fileUtil, resourceHelper, quotaManager, executor, QUOTA_WAIT_TIMEOUT);
  }

  @VisibleForTesting
  ImageZipFlasher(
      AndroidPhone device,
      LocalFileUtil fileUtil,
      ResourceHelper resourceHelper,
      QuotaManager quotaManager,
      CommandExecutor executor,
      Duration quotaWaitTimeout) {
    this.device = device;
    this.fileUtil = fileUtil;
    this.resourceHelper = resourceHelper;
    this.quotaManager = quotaManager;
    this.executor = executor;
    this.qutaWaitTimeout = quotaWaitTimeout;
  }

  /** Flashes the device with zipped image. */
  public void flashDevice(File imageInZip, String flashingScriptName, Duration timeout)
      throws DeviceActionException, InterruptedException {
    Path unzipDir = unzipImage(imageInZip);
    Path flashingScript = unzipDir.resolve(flashingScriptName);
    checkState(
        flashingScript.toFile().exists(),
        ErrorType.CUSTOMER_ISSUE,
        "Missing flashing script in zip image.");
    try (TimeoutMonitor timeoutMonitor =
            TimeoutMonitor.createAndStart(timeout.plus(qutaWaitTimeout));
        Lease ignored = quotaManager.acquire(QuotaKey.FASTBOOT_FLASH_DEVICE, 1)) {
      Duration waitForQuota = timeoutMonitor.getElapsedSinceLastCheck();
      verify(
          waitForQuota.compareTo(qutaWaitTimeout) <= 0,
          "Get quota after %d sec, not enough time to flash the device.",
          waitForQuota.toSeconds());
      Command command =
          Command.of("bash", "-x", flashingScript.toString())
              .extraEnv(ANDROID_SERIAL_ENV, device.getUuid())
              .workDir(unzipDir)
              .timeout(timeout);
      logger.atInfo().log(
          "Start flashing %s with command %s", device.getUuid(), command.getCommand());
      try {
        CommandResult result = executor.exec(command);
        verify(result.exitCode() == 0, "Failed to flash the device: %s.", result.stdout());
      } catch (CommandException e) {
        throw new DeviceActionException(e, "Failed to execute the flash script %s", command);
      }
      logger.atInfo().log(
          "Cost %s to execute the flash script", timeoutMonitor.getElapsedSinceLastCheckSafely());
    }
    recoverDevice();
  }

  private void recoverDevice() throws DeviceActionException, InterruptedException {
    device.waitUntilReady();
  }

  private Path unzipImage(File imageInZip) throws DeviceActionException, InterruptedException {
    String targetDirPath;
    try {
      targetDirPath = fileUtil.createTempDir(resourceHelper.getTmpFileDir().toString());
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to create tmp dir for image zip.");
    }

    try {
      fileUtil.unzipFile(imageInZip.getAbsolutePath(), targetDirPath);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to unzip image zip.");
    }
    return Paths.get(targetDirPath);
  }
}
