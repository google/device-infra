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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import static com.google.common.collect.Comparators.min;
import static com.google.devtools.mobileharness.shared.util.command.LineCallback.does;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupResult;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.SetupOnlyDecorator;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidDesktopOtaUpdateDecoratorSpec;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;

/** Decorator for performing OTA updates on Android Desktop devices. */
@DecoratorAnnotation(
    help =
        "For performing OTA updates on Android Desktop devices. This decorator"
            + " performs a simulated OTA update on the device by pulling the OTA package from AB"
            + " and applying it to the device running the update_device script. This is the"
            + " recommended way to perform OTA updates on Android Desktop devices.")
public class AndroidDesktopOtaUpdateDecorator extends SetupOnlyDecorator
    implements SpecConfigable<AndroidDesktopOtaUpdateDecoratorSpec> {

  private static final String TEST_ARG_NEEDS_PROVISION_REPAIR = "needs_provision_repair";
  private static final String OTA_DOWNGRADE_PROP = "ro.ota.allow_downgrade";
  private static final String IN_ZIP_SCRIPT_PATH =
      String.join(File.separator, "bin", "update_device");
  private static final String UPDATE_SUCCESS_OUTPUT =
      "onPayloadApplicationComplete(ErrorCode::kSuccess (0)";
  private static final Duration APPLY_OTA_PACKAGE_TIMEOUT = Duration.ofMinutes(25);
  private static final Duration WAIT_FOR_DEVICE_TIMEOUT = Duration.ofMinutes(10);
  private static final Duration CACHE_EXPIRATION_TIME = Duration.ofMinutes(15);

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final CommandExecutor cmdExecutor;
  private final AndroidSystemStateUtil androidSystemStateUtil;
  private final AndroidAdbUtil androidAdbUtil;
  private final Adb adb;
  private final SystemUtil systemUtil;
  private final LocalFileUtil localFileUtil;

  private AndroidDesktopOtaUpdateDecoratorSpec spec;

  @Inject
  @VisibleForTesting
  AndroidDesktopOtaUpdateDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      CommandExecutor cmdExecutor,
      AndroidAdbUtil androidAdbUtil,
      AndroidSystemStateUtil androidSystemStateUtil,
      SystemUtil systemUtil,
      Adb adb,
      LocalFileUtil localFileUtil) {
    super(decoratedDriver, testInfo);
    this.cmdExecutor = cmdExecutor;
    this.androidAdbUtil = androidAdbUtil;
    this.androidSystemStateUtil = androidSystemStateUtil;
    this.systemUtil = systemUtil;
    this.adb = adb;
    this.localFileUtil = localFileUtil;
  }

  @VisibleForTesting
  void setSpec(JobInfo jobInfo, String deviceId)
      throws MobileHarnessException, InterruptedException {
    this.spec = jobInfo.combinedSpec(this, deviceId);
  }

  @Override
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    try {
      Device device = getDecorated().getDevice();
      String deviceSerial = device.getDeviceId();
      setSpec(testInfo.jobInfo(), deviceSerial);

      // Get the update_device python script in the job's tmp file dir. Using the job's tmp file dir
      // instead of testInfo.getTmpFileDir() avoids issues with multi-device tests where the
      // directory's path includes the device_id containing a colon (:), such as <IP>:<PORT>,
      // which causes a conflict with how the python launcher detects the colon character as a
      // special character for separating items in env PATH.
      File updateDeviceScript = getUpdateDeviceScript(testInfo.jobInfo().setting().getTmpFileDir());
      File otaPackage = getOtaPackage();

      String buildId = androidAdbUtil.getProperty(deviceSerial, AndroidProperty.INCREMENTAL_BUILD);
      // Avoid applying OTA update if the device is already on the requested build and forcing OTA
      // update is not required.
      if (spec.getForceOtaUpdate() || !otaPackage.getName().contains(buildId)) {
        applyOtaUpdate(testInfo, device, updateDeviceScript, otaPackage);
        rebootAndWaitUntilDeviceIsReady(deviceSerial);
        buildId = androidAdbUtil.getProperty(deviceSerial, AndroidProperty.INCREMENTAL_BUILD);
        testInfo.log().atInfo().alsoTo(logger).log("Device is updated to build ID: %s", buildId);
      } else {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Device is already on the requested build ID: %s", buildId);
      }
    } catch (MobileHarnessException | InterruptedException e) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Provision failed, setting needs_provision_repair to true");
      testInfo.properties().add(TEST_ARG_NEEDS_PROVISION_REPAIR, "true");
      throw e;
    }
    return SetupResult.continueDecorated();
  }

  /** Gets the update_device script from the OTA tools zip file. */
  private File getUpdateDeviceScript(String tmpDir)
      throws MobileHarnessException, InterruptedException {
    // Unzip the OTA tools zip file to extract the update_device script
    logger.atInfo().log("Unzipping OTA tools zip file at %s to %s", spec.getOtaTools(), tmpDir);
    try {
      localFileUtil.unzipFile(spec.getOtaTools(), tmpDir);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DESKTOP_OTA_UPDATE_DECORATOR_OTA_TOOLS_UNZIP_ERROR,
          String.format("Failed to unzip OTA tools zip file at %s.", spec.getOtaTools()),
          e);
    }

    // Check that the update_device script is present and executable
    File updateDeviceScript = new File(tmpDir, IN_ZIP_SCRIPT_PATH);
    if (!updateDeviceScript.exists() || !updateDeviceScript.isFile()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DESKTOP_OTA_UPDATE_DECORATOR_OTA_UPDATE_SCRIPT_FILE_NOT_FOUND,
          String.format(
              "Update device script file at %s does not exist or is not a regular file.",
              updateDeviceScript.getAbsolutePath()));
    }
    if (!updateDeviceScript.setExecutable(true)) {
      throw new MobileHarnessException(
          AndroidErrorId
              .ANDROID_DESKTOP_OTA_UPDATE_DECORATOR_OTA_UPDATE_SCRIPT_NOT_EXECUTABLE_ERROR,
          String.format("Failed to set executable for %s.", updateDeviceScript.getAbsolutePath()));
    }

    return updateDeviceScript;
  }

  /** Gets the OTA package file */
  private File getOtaPackage() throws MobileHarnessException {
    // Check that the OTA package file is present
    File otaPackage = new File(spec.getOtaPackage());
    if (!otaPackage.exists() || !otaPackage.isFile()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DESKTOP_OTA_UPDATE_DECORATOR_OTA_UPDATE_PACKAGE_FILE_NOT_EXISTS,
          String.format(
              "OTA package file at %s does not exist or is not a regular file.",
              spec.getOtaPackage()));
    }

    return otaPackage;
  }

  /** Applies OTA update to the device. */
  private void applyOtaUpdate(
      TestInfo testInfo, Device device, File updateDeviceScript, File otaPackage)
      throws MobileHarnessException, InterruptedException {

    // Set OTA update command timeout to the minimum of the remaining test time and the default
    // timeout.
    Duration cmdTimeout = min(testInfo.timer().remainingTimeJava(), APPLY_OTA_PACKAGE_TIMEOUT);

    // Allow OTA downgrade since it can't be assumed that incoming builds are always newer
    androidAdbUtil.setProperty(device.getDeviceId(), OTA_DOWNGRADE_PROP, "1");

    // cancel any in-progress update and unmap/delete snapshots
    try {
      var unused = adb.runShell(device.getDeviceId(), "update_engine_client --cancel");
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Failed to cancel in-progress update: %s", e.getMessage());
    }
    // in case there are precreated snapshots
    try {
      var unused = adb.runShell(device.getDeviceId(), "snapshotctl unmap-snapshots");
    } catch (MobileHarnessException e) {
      testInfo.log().atInfo().alsoTo(logger).log("Failed to unmap snapshots: %s", e.getMessage());
    }
    try {
      var unused = adb.runShell(device.getDeviceId(), "snapshotctl delete-snapshots");
    } catch (MobileHarnessException e) {
      testInfo.log().atInfo().alsoTo(logger).log("Failed to delete snapshots: %s", e.getMessage());
    }

    // Trigger OTA update
    List<String> updateDeviceScriptArgs =
        new ArrayList<>(Arrays.asList("-s", device.getDeviceId()));
    if (spec.getWipeUserData()) {
      updateDeviceScriptArgs.add("--wipe-user-data");
    }
    updateDeviceScriptArgs.add(otaPackage.getAbsolutePath());
    var envMap = getEnvMap(testInfo);
    testInfo.log().atInfo().alsoTo(logger).log("Extra environment map: %s", envMap.toString());
    Command otaUpdateCommand =
        Command.of(updateDeviceScript.getAbsolutePath(), updateDeviceScriptArgs).extraEnv(envMap);
    try {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Running OTA update on device: %s", otaUpdateCommand.getCommand());
      String cmdOut =
          cmdExecutor.run(
              otaUpdateCommand
                  .timeout(cmdTimeout)
                  .redirectStderr(true)
                  .onStdout(does(logger.atInfo()::log)));

      // Verify OTA update success
      if (!cmdOut.contains(UPDATE_SUCCESS_OUTPUT)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_DESKTOP_OTA_UPDATE_DECORATOR_OTA_UPDATE_ERROR,
            "Failed to apply OTA update to Android Device.");
      }
    } catch (CommandException otaUpdateException) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DESKTOP_OTA_UPDATE_DECORATOR_OTA_UPDATE_ERROR,
          "Failed to apply OTA update to Android Device.",
          otaUpdateException);
    }
  }

  /**
   * Returns an environment map containing the necessary PATH variables for executing commands.
   *
   * <p>This includes the directory of the ADB binary provided by Mobile Harness and the system's
   * existing PATH.
   */
  ImmutableMap<String, String> getEnvMap(TestInfo testInfo) {
    List<String> paths = new ArrayList<>();
    // Use the adb binaries that ship with Mobile Harness
    var adbPath = adb.getAdbPath();
    if (localFileUtil.isFileExist(adbPath)) {
      var adbParent = localFileUtil.getParentDirPath(adbPath);
      if (!Strings.isNullOrEmpty(adbParent)) {
        paths.add(adbParent);
        testInfo.log().atInfo().alsoTo(logger).log("Found ADB parent directory: %s.", adbParent);
      }
    }
    // Add the system PATH to the environment.
    String systemPath = systemUtil.getEnv("PATH");
    if (systemPath != null) {
      paths.add(systemPath);
    }
    String path = Joiner.on(File.pathSeparator).join(paths);
    return ImmutableMap.of("PATH", path);
  }

  /** Reboots device and ensures it is online and ready to respond. */
  private void rebootAndWaitUntilDeviceIsReady(String deviceId)
      throws MobileHarnessException, InterruptedException {
    try {
      // Cache device to avoid device detection issues during reboot.
      DeviceCache.getInstance()
          .cache(deviceId, getDevice().getClass().getSimpleName(), CACHE_EXPIRATION_TIME);
      // Reboot device
      androidSystemStateUtil.reboot(deviceId);
      // Wait for device to be online after reboot
      androidSystemStateUtil.waitForState(
          deviceId, DeviceConnectionState.DEVICE, WAIT_FOR_DEVICE_TIMEOUT);
      androidSystemStateUtil.waitUntilReady(deviceId);

      // Set the device "stay awake" status post-flash to prevent the newly provisioned OS
      // from sleeping, which will breaks downstream tests when ADB connection dropped.
      try {
        var unused = adb.runShell(deviceId, "svc power stayon true");
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to set stay awake status for device %s", deviceId);
      }
    } finally {
      // Invalidate device cache
      DeviceCache.getInstance().invalidateCache(deviceId);
    }
  }
}
