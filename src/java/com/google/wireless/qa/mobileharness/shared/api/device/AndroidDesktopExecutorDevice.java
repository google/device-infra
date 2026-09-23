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

package com.google.wireless.qa.mobileharness.shared.api.device;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.androiddesktop.device.AndroidDesktopDeviceHelper;
import com.google.devtools.mobileharness.platform.androiddesktop.device.CrosCipdUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/** A placeholder device for Android Desktop executor devices. */
public class AndroidDesktopExecutorDevice extends BaseDevice {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String TEST_ARG_DUT_NAME = "dut_name";
  private static final String TEST_ARG_NEEDS_PROVISION_REPAIR = "needs_provision_repair";
  public static final String PARAM_IGNORE_DEVICE_PRE_RUN_ADB_CONNECT_FAILURE =
      "ignore_device_pre_run_adb_connect_failure";

  /** Accepted spellings of the dt-converter CIPD tag parameter, in lookup order. */
  private static final ImmutableList<String> DT_CONVERTER_CIPD_TAG_KEYS =
      ImmutableList.of(
          CrosDecoratorSpec.DT_CONVERTER_CIPD_TAG,
          CrosDecoratorSpec.DT_CONVERTER_CIPD_TAG.replace('_', '-'));

  private volatile String deviceIdOverride;

  private final AndroidDesktopDeviceHelper androidDesktopDeviceHelper;
  private final AndroidAdbInternalUtil adbInternalUtil;
  private final CommandExecutor commandExecutor;
  private final LocalFileUtil fileUtil;

  public AndroidDesktopExecutorDevice(String deviceId) {
    this(
        deviceId,
        new AndroidDesktopDeviceHelper(),
        new AndroidAdbInternalUtil(),
        new CommandExecutor());
  }

  @VisibleForTesting
  AndroidDesktopExecutorDevice(
      String deviceId,
      AndroidDesktopDeviceHelper androidDesktopDeviceHelper,
      AndroidAdbInternalUtil adbInternalUtil,
      CommandExecutor commandExecutor) {
    super(deviceId);
    this.androidDesktopDeviceHelper = androidDesktopDeviceHelper;
    this.adbInternalUtil = adbInternalUtil;
    this.commandExecutor = commandExecutor;
    this.fileUtil = new LocalFileUtil();
  }

  @Override
  public void prepare() throws MobileHarnessException, InterruptedException {
    super.prepare();
    this.addSupportedDriver("NoOpDriver");
    this.addSupportedDriver("TradefedTest");
    // For Mobly tests.
    this.addSupportedDriver("MoblyAospTest");
    this.addSupportedDriver("MoblyTest");
    // For AndroidInstrumentation tests.
    this.addSupportedDriver("AndroidInstrumentation");
    // Decorators for Mobly tests.
    this.addSupportedDecorator("AndroidAccountDecorator");
    this.addSupportedDecorator("AndroidAdbShellDecorator");
    this.addSupportedDecorator("AndroidDesktopOtaUpdateDecorator");
    this.addSupportedDecorator("AndroidFilePullerDecorator");
    this.addSupportedDecorator("AndroidFilePusherDecorator");
    this.addSupportedDecorator("AndroidInstallAppsDecorator");
    this.addSupportedDecorator("AndroidLogCatDecorator");
    this.addSupportedDecorator("AndroidPerfettoDecorator");
    this.addSupportedDecorator("AndroidSwitchLanguageDecorator");
    this.addSupportedDecorator("CrosLsNexusDecorator");
    this.addSupportedDecorator("CrossOverAndroidDesktopProvisionDecorator");
    this.addSupportedDecorator("CrosDutTopologyDecorator");
    if (getDimension("network_zone").isEmpty()) {
      addDimension("network_zone", "unspecified");
    }
    String executorGroup = Flags.androidDesktopExecutorGroup.getNonNull();
    if (!executorGroup.isEmpty()) {
      updateDimension("network_zone", executorGroup);
    }
  }

  @Override
  public String getDeviceId() {
    String deviceId = deviceIdOverride;
    String id = deviceId != null ? deviceId : super.getDeviceId();
    logger.atInfo().log("getDeviceId: %s", id);
    return id;
  }

  @Override
  public void preRunTest(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    super.preRunTest(testInfo);
    deviceIdOverride = getDeviceIdOverride(testInfo);
    testInfo.log().atInfo().alsoTo(logger).log("deviceIdOverride: %s", deviceIdOverride);
    if (deviceIdOverride != null) {
      String rawDutName = testInfo.jobInfo().params().get(TEST_ARG_DUT_NAME);
      String dutName = extractHostname(rawDutName);
      testInfo.properties().add(TEST_ARG_DUT_NAME, dutName);

      try {
        // TODO: Support multi-duts units in the future.
        String connectedDeviceId = establishDeviceConnection(testInfo, deviceIdOverride, dutName);
        deviceIdOverride = connectedDeviceId;
      } catch (MobileHarnessException e) {
        testInfo.log().atWarning().alsoTo(logger).log("Failed to connect to %s", deviceIdOverride);
        if (testInfo.jobInfo().params().isTrue(PARAM_IGNORE_DEVICE_PRE_RUN_ADB_CONNECT_FAILURE)) {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Failed to connect to %s, but continuing because %s is set.",
                  deviceIdOverride, PARAM_IGNORE_DEVICE_PRE_RUN_ADB_CONNECT_FAILURE);
          return;
        }
        testInfo.log().atWarning().log(
            "Failed to connect to %s. Setting device to needs_repair.", deviceIdOverride);
        try {
          androidDesktopDeviceHelper.updateDeviceDutState(
              dutName,
              "needs_repair",
              /* provision= */ true,
              /* reimage= */ false,
              /* usbkey= */ false,
              /* clearRepairRequests= */ false);
        } catch (MobileHarnessException | InterruptedException ex) {
          testInfo.log().atWarning().log(
              "Failed to update device DUT state to needs_repair: %s", ex.getMessage());
          e.addSuppressed(ex);
        }
        throw e;
      }
    }
  }

  @Override
  public PostTestDeviceOp postRunTest(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    String dutName = testInfo.properties().get("dut_name");
    if (dutName != null) {
      try {
        Map<String, String> dimensions = androidDesktopDeviceHelper.getDeviceDimensions(dutName);
        if (dimensions == null) {
          dimensions = new HashMap<>();
        }
        dimensions.put("dut_name", dutName);
        androidDesktopDeviceHelper.propagateDimensionsToSubLeafTests(testInfo, dimensions);
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atWarning()
            .withCause(e)
            .alsoTo(logger)
            .log("Failed to extract Android Desktop device dimensions for hostname %s", dutName);
      }
    }

    try {
      boolean skipHealthCheck = false;
      if (!skipHealthCheck) {
        if (testInfo.resultWithCause() == null
            || !Objects.equals(testInfo.resultWithCause().get().type(), TestResult.PASS)) {
          if (testInfo.properties().getBoolean(TEST_ARG_NEEDS_PROVISION_REPAIR).orElse(false)) {
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log(
                    "Test failed with repair_force_provision=true. Skipping health check and"
                        + " marking device as needs_repair.");
            androidDesktopDeviceHelper.updateDeviceDutState(
                dutName,
                "needs_repair",
                /* provision= */ true,
                /* reimage= */ false,
                /* usbkey= */ false,
                /* clearRepairRequests= */ false);
          } else {
            testInfo.properties().add("post_run_health_check_triggered", "true");
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log("Test failed. Triggering post-run health check.");
            boolean isHealthy = androidDesktopDeviceHelper.isDeviceHealthy(getDeviceId());
            testInfo.properties().add("post_run_health_check_result", String.valueOf(isHealthy));
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log("Post-run health check finished. Result: %s", isHealthy);
          }
        } else {
          testInfo.log().atInfo().alsoTo(logger).log("Test passed, skipping health check.");
        }
      }
    } finally {
      if (deviceIdOverride != null && deviceIdOverride.contains(":")) {
        try {
          // TODO: Support multi-duts units in the future.
          adbInternalUtil.disconnect(deviceIdOverride);
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .log("Failed to disconnect from %s", deviceIdOverride);
        }
      }
      deviceIdOverride = null;
    }
    return super.postRunTest(testInfo);
  }

  /** Establishes a connection to the device. */
  @VisibleForTesting
  String establishDeviceConnection(TestInfo testInfo, String deviceId, String dutName)
      throws InterruptedException, MobileHarnessException {
    if (isDeviceOnline(deviceId)) {
      testInfo.log().atInfo().alsoTo(logger).log("Device %s is already online.", deviceId);
      return deviceId;
    }
    String ipConnection = deviceId.contains(":") ? deviceId : dutName + ":5555";
    testInfo.log().atInfo().alsoTo(logger).log("Connecting to device %s.", ipConnection);
    adbInternalUtil.connect(ipConnection);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Successfully connected to device %s.", ipConnection);
    return ipConnection;
  }

  /**
   * Gets the device ID override from the test info.
   *
   * <p>If dutName contains ":" it is assumed to be in explicit host:port format and is returned
   * directly. Otherwise, attempts to resolve the device identifier via dt-converter
   * resolve-device-id.
   *
   * <p>The resolved device ID can be:
   *
   * <ul>
   *   <li>A direct USB/Maui cable serial number (e.g., "MAUIV1234567") for USB-attached DUTs.
   *   <li>An IP endpoint with port (e.g., "192.168.1.50:5555" or "host:5555") for TCP/IP ADB.
   * </ul>
   *
   * <p>If resolution fails or returns an empty result, it falls back to appending default port
   * dutName + ":5555".
   */
  @VisibleForTesting
  @Nullable
  String getDeviceIdOverride(TestInfo testInfo) throws InterruptedException {
    String dutName = testInfo.jobInfo().params().get(TEST_ARG_DUT_NAME);
    if (dutName == null) {
      return null;
    }
    if (dutName.contains(":")) {
      return dutName;
    }
    List<String> resolvedDeviceIds = resolveDeviceIds(testInfo, dutName);
    if (!resolvedDeviceIds.isEmpty()) {
      // TODO: Support multi-duts units in the future.
      return resolvedDeviceIds.get(0);
    }
    String fallbackDeviceId = dutName + ":5555";
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Could not resolve device ID for %s; assuming device is network-connected and"
                + " proceeding with %s",
            dutName, fallbackDeviceId);
    return fallbackDeviceId;
  }

  /** Checks if the device is online in adb. */
  @VisibleForTesting
  boolean isDeviceOnline(String deviceId) throws InterruptedException {
    try {
      Set<String> onlineDevices = adbInternalUtil.getDeviceSerialsByState(DeviceState.DEVICE);
      return onlineDevices.contains(deviceId);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to query device state for %s", deviceId);
      return false;
    }
  }

  /**
   * Resolves the device identifier(s) associated with the given DUT name using dt-converter.
   *
   * <p>The resolved device ID can be:
   *
   * <ul>
   *   <li>A direct USB/Maui cable serial number (e.g., "MAUIV1234567") for USB-attached DUTs.
   *   <li>An IP endpoint with port (e.g., "192.168.1.50:5555" or "host:5555") for TCP/IP ADB.
   * </ul>
   *
   * <p>The dt-converter binary is pulled from CIPD at runtime and its temporary directory is always
   * removed before returning.
   */
  @VisibleForTesting
  List<String> resolveDeviceIds(TestInfo testInfo, String dutName) throws InterruptedException {
    String labServiceAddr =
        testInfo
            .jobInfo()
            .params()
            .get(CrosDecoratorSpec.INVENTORY_SERVICE, CrosDecoratorSpec.DEFAULT_INVENTORY_SERVICE);
    Path cipdDownloadedDir = null;
    String resolvedDtConverterPath = CrosDecoratorSpec.DT_CONVERTER_CIPD_PATH;
    try {
      Path downloadedBinary = downloadDtConverter(testInfo);
      if (downloadedBinary != null) {
        resolvedDtConverterPath = downloadedBinary.toAbsolutePath().toString();
        cipdDownloadedDir = CrosCipdUtil.getPackageRootDir(downloadedBinary, "dt-converter");
      }
      CrosCipdUtil.printVersion(commandExecutor, resolvedDtConverterPath, testInfo);

      Command command =
          Command.of(
                  resolvedDtConverterPath,
                  "resolve-device-id",
                  "-labservice",
                  labServiceAddr,
                  "-unit",
                  dutName)
              .timeout(Duration.ofSeconds(15));
      CommandResult result = commandExecutor.exec(command);
      List<String> resolvedDeviceIds =
          Splitter.on('\n')
              .trimResults()
              .omitEmptyStrings()
              .splitToList(result.stdoutWithoutTrailingLineTerminator());
      if (!resolvedDeviceIds.isEmpty()) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Resolved device id(s) for %s: %s", dutName, resolvedDeviceIds);
        return resolvedDeviceIds;
      }
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .withCause(e)
          .alsoTo(logger)
          .log("Failed to resolve device ID(s) for %s", dutName);
    } finally {
      if (cipdDownloadedDir != null) {
        CrosCipdUtil.cleanupTempDir(fileUtil, cipdDownloadedDir, testInfo);
      }
    }
    return ImmutableList.of();
  }

  /**
   * Returns the CIPD tag to pull dt-converter with, accepting both the underscored and hyphenated
   * spelling of the parameter.
   *
   * <p>An absent parameter yields {@link CrosDecoratorSpec#DEFAULT_CIPD_TAG}, while one explicitly
   * set to {@code ""} yields {@code ""}, which is what triggers the pre-installed binary fallback.
   */
  @VisibleForTesting
  static String getDtConverterCipdTag(TestInfo testInfo) {
    Params params = testInfo.jobInfo().params();
    for (String key : DT_CONVERTER_CIPD_TAG_KEYS) {
      if (params.has(key)) {
        return Strings.nullToEmpty(params.get(key)).trim();
      }
    }
    return CrosDecoratorSpec.DEFAULT_CIPD_TAG;
  }

  /**
   * Pulls the dt-converter CIPD package and returns the downloaded binary.
   *
   * <p>The package at {@link CrosDecoratorSpec#DT_CONVERTER_CIPD_TAG} is pulled, defaulting to
   * {@link CrosDecoratorSpec#DEFAULT_CIPD_TAG} ("prod"). An explicitly empty tag skips the download
   * and returns {@code null}, meaning the pre-installed {@link
   * CrosDecoratorSpec#DT_CONVERTER_CIPD_PATH} should be used instead.
   *
   * <p>The caller owns the temporary directory holding the returned binary and must remove it.
   */
  @Nullable
  private Path downloadDtConverter(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    String cipdTag = getDtConverterCipdTag(testInfo);

    if (!Strings.isNullOrEmpty(cipdTag)) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Pulling dt-converter CIPD package with tag/version: %s", cipdTag);
      String binaryName = "dt-converter";
      return CrosCipdUtil.downloadPackage(
          commandExecutor,
          fileUtil,
          CrosDecoratorSpec.DT_CONVERTER_PACKAGE,
          cipdTag,
          /* destDir= */ null,
          binaryName,
          testInfo,
          CrosCipdUtil.DEFAULT_CIPD_TIMEOUT);
    }
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "No CIPD tag/version specified for dt-converter; using pre-installed binary at %s",
            CrosDecoratorSpec.DT_CONVERTER_CIPD_PATH);
    return null;
  }

  /** Extracts the hostname from a dutName which may contain a port. */
  @VisibleForTesting
  @Nullable
  static String extractHostname(@Nullable String dutName) {
    if (dutName == null) {
      return null;
    }
    int closingBracket = dutName.indexOf(']');
    if (dutName.startsWith("[") && closingBracket != -1) {
      return dutName.substring(0, closingBracket + 1);
    }
    int colonIndex = dutName.indexOf(':');
    if (colonIndex != -1) {
      return dutName.substring(0, colonIndex);
    }
    return dutName;
  }
}
