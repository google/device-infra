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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.androiddesktop.device.CrosCipdUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupResult;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Decorator for CrossOver provisioning on Android Desktop devices.
 *
 * <p>This decorator strictly handles the OS transition from ChromeOS to Android OS. It checks if
 * the DUT is already accessible over ADB (running Android OS); if so, provisioning is skipped
 * regardless of the Android build currently installed, and aligning the device to a specific build
 * is deferred to downstream flashing decorators. Otherwise, it resolves DUT stable OS and firmware
 * image targets (via dt-converter and labservice), provisions the device from ChromeOS to Android
 * OS using foil-provision, and reconnects ADB for subsequent test drivers.
 *
 * <p>Note: This decorator is not supported for Maui cables, as devices using Maui cables are
 * expected to be directly plugged in.
 */
@DecoratorAnnotation(help = "CrossOver provisioning decorator for Android Desktop.")
public class CrossOverAndroidDesktopProvisionDecorator extends CrosBaseDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String FOIL_PROVISION_PACKAGE = CrosDecoratorSpec.FOIL_PROVISION_PACKAGE;
  public static final String FOIL_PROVISION_CIPD_PATH = CrosDecoratorSpec.FOIL_PROVISION_CIPD_PATH;
  public static final String FOIL_PROVISION_CIPD_TAG = CrosDecoratorSpec.FOIL_PROVISION_CIPD_TAG;
  public static final String DT_CONVERTER_PACKAGE = CrosDecoratorSpec.DT_CONVERTER_PACKAGE;
  public static final String DT_CONVERTER_CIPD_PATH = CrosDecoratorSpec.DT_CONVERTER_CIPD_PATH;
  public static final String DT_CONVERTER_CIPD_TAG = CrosDecoratorSpec.DT_CONVERTER_CIPD_TAG;
  public static final String DEFAULT_CIPD_TAG = CrosDecoratorSpec.DEFAULT_CIPD_TAG;
  public static final String BUILD_ID = CrosDecoratorSpec.BUILD_ID;
  public static final String BUILD_TARGET = CrosDecoratorSpec.BUILD_TARGET;
  public static final String USE_SIGNED_IMAGE = CrosDecoratorSpec.USE_SIGNED_IMAGE;
  public static final String USE_TEST_RAMDISK = CrosDecoratorSpec.USE_TEST_RAMDISK;
  public static final String SKIP_STABLE_VERSION = CrosDecoratorSpec.SKIP_STABLE_VERSION;
  public static final String TEST_ARG_NEEDS_PROVISION_REPAIR = "needs_provision_repair";
  public static final Duration DEFAULT_FOIL_PROVISION_TIMEOUT =
      CrosDecoratorSpec.DEFAULT_FOIL_PROVISION_TIMEOUT;

  /**
   * Test property recording the Android build ID ({@code ro.build.version.incremental}) detected on
   * the DUT when CrossOver provisioning is skipped because the device is already ADB accessible.
   */
  public static final String TEST_PROPERTY_CURRENT_BUILD_ID = "crossover_current_build_id";

  /**
   * Default TCP/IP ADB port suffix for network-connected DUTs.
   *
   * <p>Note: This decorator is not supported for Maui cables, as devices using Maui cables are
   * expected to be directly plugged in.
   */
  private static final String DEFAULT_ADB_PORT = ":5555";

  /**
   * Timeout for the pre-provision {@code adb connect} check. A DUT booted into ChromeOS may drop
   * SYN packets on port 5555 instead of replying with RST, so a short timeout lets the check fail
   * fast and proceed to provisioning.
   */
  @VisibleForTesting static final Duration ADB_CONNECT_CHECK_TIMEOUT = Duration.ofSeconds(15);

  private static final Duration DT_CONVERTER_TIMEOUT = Duration.ofMinutes(2);

  /** Data holder for resolved build ID and build target. */
  static class BuildInfo {
    private final String buildId;
    private final String buildTarget;

    /**
     * Constructs a {@link BuildInfo} instance.
     *
     * @param buildId the Android build ID (e.g. "12345678"), or {@code null} if unresolved
     * @param buildTarget the Android build target (e.g. "brya-trunk_staging-userdebug"), or {@code
     *     null} if unresolved
     */
    BuildInfo(@Nullable String buildId, @Nullable String buildTarget) {
      this.buildId = buildId;
      this.buildTarget = buildTarget;
    }

    /** Returns the resolved build ID, or {@code null} if not resolved. */
    @Nullable
    String buildId() {
      return buildId;
    }

    /** Returns the resolved build target, or {@code null} if not resolved. */
    @Nullable
    String buildTarget() {
      return buildTarget;
    }
  }

  private final CommandExecutor commandExecutor;
  private final LocalFileUtil fileUtil;
  private final AndroidAdbUtil androidAdbUtil;

  private String resolvedFoilProvisionPath = FOIL_PROVISION_CIPD_PATH;
  private String resolvedDtConverterPath = DT_CONVERTER_CIPD_PATH;
  private Path foilProvisionDownloadedDir;
  private Path dtConverterDownloadedDir;

  /**
   * Constructs the decorator via Guice dependency injection.
   *
   * @param driver the decorated driver
   * @param testInfo the current test context
   * @param commandExecutor executor used to run shell commands
   */
  @Inject
  CrossOverAndroidDesktopProvisionDecorator(
      Driver driver, TestInfo testInfo, CommandExecutor commandExecutor) {
    this(driver, testInfo, commandExecutor, new LocalFileUtil(), new AndroidAdbUtil());
  }

  /**
   * Constructs the decorator with a default {@link CommandExecutor}.
   *
   * @param driver the decorated driver
   * @param testInfo the current test context
   */
  CrossOverAndroidDesktopProvisionDecorator(Driver driver, TestInfo testInfo) {
    this(driver, testInfo, new CommandExecutor());
  }

  /**
   * Testing constructor with injected command executor and local file utility.
   *
   * @param driver the decorated driver
   * @param testInfo the current test context
   * @param commandExecutor executor used to run shell commands
   * @param fileUtil local file utility
   */
  @VisibleForTesting
  CrossOverAndroidDesktopProvisionDecorator(
      Driver driver, TestInfo testInfo, CommandExecutor commandExecutor, LocalFileUtil fileUtil) {
    this(driver, testInfo, commandExecutor, fileUtil, new AndroidAdbUtil());
  }

  /**
   * Testing constructor with all injected dependencies including {@link AndroidAdbUtil}.
   *
   * @param driver the decorated driver
   * @param testInfo the current test context
   * @param commandExecutor executor used to run shell commands
   * @param fileUtil local file utility
   * @param androidAdbUtil ADB utility for device connection and property checks
   */
  @VisibleForTesting
  CrossOverAndroidDesktopProvisionDecorator(
      Driver driver,
      TestInfo testInfo,
      CommandExecutor commandExecutor,
      LocalFileUtil fileUtil,
      AndroidAdbUtil androidAdbUtil) {
    super(driver, testInfo);
    this.commandExecutor = commandExecutor;
    this.fileUtil = fileUtil;
    this.androidAdbUtil = androidAdbUtil;
  }

  @Override
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    String dutName = deviceName(deviceId());
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("CrossOverAndroidDesktopProvisionDecorator is running on device: %s", dutName);

    if (isDeviceAdbAccessible(testInfo, dutName)) {
      return SetupResult.continueDecorated();
    }

    BuildInfo buildInfo = resolveBuildParameters(testInfo, dutName);
    String buildId = buildInfo.buildId();
    String buildTarget = buildInfo.buildTarget();

    if (Strings.isNullOrEmpty(buildId) || Strings.isNullOrEmpty(buildTarget)) {
      throw new MobileHarnessException(
          BasicErrorId.NON_MH_EXCEPTION,
          String.format(
              "Missing build_id or build_target for device %s (build_id=%s, build_target=%s)",
              dutName, buildId, buildTarget));
    }

    resolveFoilProvisionPath(testInfo);
    CrosCipdUtil.printVersion(commandExecutor, resolvedFoilProvisionPath, testInfo);

    Command provisionCommand = generateProvisionCommand(testInfo, dutName, buildId, buildTarget);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Executing CrossOver provisioning command: %s", provisionCommand);

    try {
      CommandResult result;
      try {
        result = commandExecutor.exec(provisionCommand);
      } catch (MobileHarnessException e) {
        String msg = Strings.nullToEmpty(e.getMessage());
        if (msg.contains("OS_BOOT_FAILURE")
            || msg.contains("STATUS_OS_BOOT_FAILURE")
            || msg.contains("target OS failed to boot")) {
          throw new MobileHarnessException(
              e.getErrorId(),
              String.format("Target OS failed to boot on device %s: %s", dutName, msg),
              e);
        }
        if (msg.contains("OS_UNREACHABLE")
            || msg.contains("STATUS_OS_UNREACHABLE")
            || msg.contains("target OS unreachable")
            || msg.contains("STATUS_POST_PROVISION_SETUP_FAILED")
            || msg.contains("STATUS_DUT_UNREACHABLE_POST_PROVISION")) {
          throw new MobileHarnessException(
              e.getErrorId(),
              String.format("Target OS unreachable on device %s: %s", dutName, msg),
              e);
        }
        throw new MobileHarnessException(
            AndroidErrorId.CROSSOVER_ANDROID_DESKTOP_PROVISION_DECORATOR_PROVISION_ERROR,
            String.format("foil-provision failed for device %s: %s", dutName, e.getMessage()),
            e);
      }
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "CrossOver provisioning finished successfully for %s:\n%s", dutName, result.stdout());

      connectAdbAfterProvision(testInfo, dutName);
    } catch (MobileHarnessException | InterruptedException e) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "CrossOver provisioning or post-provision ADB connection failed for %s (%s); setting"
                  + " %s to true",
              dutName, e.getMessage(), TEST_ARG_NEEDS_PROVISION_REPAIR);
      testInfo.properties().add(TEST_ARG_NEEDS_PROVISION_REPAIR, "true");
      throw e;
    }

    return SetupResult.continueDecorated();
  }

  /**
   * Checks whether the device is already accessible over ADB (indicating it is booted into Android
   * OS and does not require CrossOver provisioning from ChromeOS).
   *
   * <p>This check only verifies the OS transition state. Any stale or offline ADB transport left in
   * the host ADB server is disconnected first on a best-effort basis to prevent false-positive
   * "already connected" states. If the device is responsive over ADB, its current Android build ID
   * is recorded in {@link #TEST_PROPERTY_CURRENT_BUILD_ID} and provisioning is skipped regardless
   * of any requested {@code build_id}; aligning the build is deferred to downstream flashing
   * decorators. If the check fails, the ADB transport is again disconnected quietly before
   * proceeding to CrossOver provisioning.
   *
   * <p>Note: Connects to {@code dutName + ":5555"} over TCP/IP ADB with a short timeout ({@link
   * #ADB_CONNECT_CHECK_TIMEOUT}) to fail fast when the DUT is booted into ChromeOS. This decorator
   * is not supported for Maui cables, as devices using Maui cables are expected to be directly
   * plugged in.
   *
   * @param testInfo the current test context
   * @param dutName the DUT hostname
   * @return {@code true} if the device is responsive over ADB, {@code false} otherwise
   */
  @VisibleForTesting
  boolean isDeviceAdbAccessible(TestInfo testInfo, String dutName) throws InterruptedException {
    String connectionTarget = dutName + DEFAULT_ADB_PORT;
    disconnectAdbQuietly(testInfo, connectionTarget);
    try {
      androidAdbUtil.connect(connectionTarget, ADB_CONNECT_CHECK_TIMEOUT);
      String currentBuildId =
          androidAdbUtil.getProperty(connectionTarget, AndroidProperty.INCREMENTAL_BUILD);
      if (!Strings.isNullOrEmpty(currentBuildId)) {
        testInfo.properties().add(TEST_PROPERTY_CURRENT_BUILD_ID, currentBuildId);
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Device %s is already ADB accessible (running Android build %s); skipping CrossOver"
                    + " provisioning.",
                dutName, currentBuildId);
        return true;
      }
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "ADB connected to %s but device did not return a valid Android build ID; proceeding"
                  + " with CrossOver provisioning.",
              connectionTarget);
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "ADB check on %s failed (%s); device is not running Android.",
              connectionTarget, e.getMessage());
    }
    disconnectAdbQuietly(testInfo, connectionTarget);
    return false;
  }

  /**
   * Reconnects to the device over ADB after {@code foil-provision ate} completes, since {@code
   * foil-provision} disconnects its local ADB session during teardown.
   *
   * <p>Any existing transport for the target is disconnected first so that {@code adb connect}
   * re-establishes a fresh TCP session instead of reporting "already connected" for an offline or
   * half-open entry. The session is then verified by reading {@code ro.build.version.incremental}.
   *
   * <p>No post-provision sleep is needed before reconnecting because {@code foil-provision ate}'s
   * internal validation step blocks until the device has finished booting into Android OS,
   * confirmed ADB responsiveness, set stay-awake settings, and verified the installed build ID.
   * {@code foil-provision} only disconnects its host ADB session during teardown, so the DUT is
   * already fully booted and ready for reconnect.
   *
   * <p>Note: Connects to {@code dutName + ":5555"} over TCP/IP ADB. This decorator is not supported
   * for Maui cables, as devices using Maui cables are expected to be directly plugged in.
   */
  private void connectAdbAfterProvision(TestInfo testInfo, String dutName)
      throws MobileHarnessException, InterruptedException {
    String connectionTarget = dutName + DEFAULT_ADB_PORT;
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Connecting to device %s via ADB after CrossOver provisioning.", connectionTarget);
    disconnectAdbQuietly(testInfo, connectionTarget);
    String currentBuildId;
    try {
      androidAdbUtil.connect(connectionTarget);
      currentBuildId =
          androidAdbUtil.getProperty(connectionTarget, AndroidProperty.INCREMENTAL_BUILD);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId
              .CROSSOVER_ANDROID_DESKTOP_PROVISION_DECORATOR_POST_PROVISION_CONNECT_ADB_ERROR,
          String.format(
              "Failed to connect to %s via ADB after CrossOver provisioning: %s",
              connectionTarget, e.getMessage()),
          e);
    }
    if (Strings.isNullOrEmpty(currentBuildId)) {
      throw new MobileHarnessException(
          AndroidErrorId
              .CROSSOVER_ANDROID_DESKTOP_PROVISION_DECORATOR_POST_PROVISION_DEVICE_NOT_RESPONSIVE,
          String.format(
              "Failed to connect to %s via ADB after CrossOver provisioning: device did not return"
                  + " a valid Android build ID",
              connectionTarget));
    }
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Successfully connected to device %s via ADB (running Android build %s).",
            connectionTarget, currentBuildId);
  }

  /**
   * Disconnects {@code connectionTarget} from the host ADB server on a best-effort basis, ignoring
   * failures. Used to clear stale or offline transports before reconnecting.
   */
  private void disconnectAdbQuietly(TestInfo testInfo, String connectionTarget)
      throws InterruptedException {
    try {
      androidAdbUtil.disconnect(connectionTarget);
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Ignoring ADB disconnect failure for %s: %s", connectionTarget, e.getMessage());
    }
  }

  /**
   * Resolves build_id and build_target parameters. If either is missing, queries stable versions
   * via dt-converter unless skip_stable_version is enabled.
   */
  private BuildInfo resolveBuildParameters(TestInfo testInfo, String dutName)
      throws MobileHarnessException, InterruptedException {
    String buildId = getParam(testInfo, BUILD_ID);
    String buildTarget = getParam(testInfo, BUILD_TARGET);

    if (!Strings.isNullOrEmpty(buildId) && !Strings.isNullOrEmpty(buildTarget)) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Using provided build parameters: build_id=%s, build_target=%s",
              buildId, buildTarget);
      return new BuildInfo(buildId, buildTarget);
    }

    String skipStableVersion = getParam(testInfo, SKIP_STABLE_VERSION, "false");
    if (Ascii.equalsIgnoreCase("true", skipStableVersion)) {
      throw new MobileHarnessException(
          BasicErrorId.NON_MH_EXCEPTION,
          String.format(
              "Missing build_id or build_target for device %s (build_id=%s, build_target=%s) and"
                  + " skip_stable_version is true",
              dutName, buildId, buildTarget));
    }

    resolveDtConverterPath(testInfo);
    CrosCipdUtil.printVersion(commandExecutor, resolvedDtConverterPath, testInfo);

    List<String> args = new ArrayList<>();
    args.add(resolvedDtConverterPath);
    args.add("stable-version");
    args.add("-unit");
    args.add(dutName);
    args.add("-labservice");
    args.add(getInventoryServiceAddress());
    args.add("-device-type");
    args.add("androidos");
    args.add("-json");

    Command cmd = Command.of(args).timeout(DT_CONVERTER_TIMEOUT);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Querying stable version for %s via: %s", dutName, String.join(" ", args));

    CommandResult result;
    try {
      result = commandExecutor.exec(cmd);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          e.getErrorId(),
          String.format(
              "Failed to query stable version for %s via dt-converter: %s",
              dutName, e.getMessage()),
          e);
    }
    String stdout = result.stdout();
    testInfo.log().atInfo().alsoTo(logger).log("dt-converter stable-version output:\n%s", stdout);

    return parseAndApplyStableVersion(stdout, testInfo, buildId, buildTarget);
  }

  /** Parses JSON output from dt-converter stable-version and updates missing test properties. */
  @VisibleForTesting
  BuildInfo parseAndApplyStableVersion(
      String output,
      TestInfo testInfo,
      @Nullable String currentBuildId,
      @Nullable String currentBuildTarget) {
    String resolvedBuildId = currentBuildId;
    String resolvedBuildTarget = currentBuildTarget;

    if (!Strings.isNullOrEmpty(output)) {
      try {
        JsonObject root = JsonParser.parseString(output).getAsJsonObject();
        if (root.has("android") && root.get("android").isJsonObject()) {
          JsonObject androidObj = root.getAsJsonObject("android");
          if (androidObj.has("build_id") && !androidObj.get("build_id").isJsonNull()) {
            String id = androidObj.get("build_id").getAsString().trim();
            if (!id.isEmpty() && Strings.isNullOrEmpty(resolvedBuildId)) {
              resolvedBuildId = id;
              testInfo.properties().add("crossover_build_id", id);
              testInfo
                  .log()
                  .atInfo()
                  .alsoTo(logger)
                  .log("Populated build_id from stable-version: %s", id);
            }
          }
          if (androidObj.has("build_target") && !androidObj.get("build_target").isJsonNull()) {
            String target = androidObj.get("build_target").getAsString().trim();
            if (!target.isEmpty() && Strings.isNullOrEmpty(resolvedBuildTarget)) {
              resolvedBuildTarget = target;
              testInfo.properties().add("crossover_build_target", target);
              testInfo
                  .log()
                  .atInfo()
                  .alsoTo(logger)
                  .log("Populated build_target from stable-version: %s", target);
            }
          }
        }
      } catch (RuntimeException e) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .withCause(e)
            .log("Failed to parse JSON output from dt-converter stable-version:\n%s", output);
      }
    }
    return new BuildInfo(resolvedBuildId, resolvedBuildTarget);
  }

  /** Resolves the foil-provision binary path. */
  @VisibleForTesting
  void resolveFoilProvisionPath(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    String cipdTag = getParam(testInfo, FOIL_PROVISION_CIPD_TAG, DEFAULT_CIPD_TAG);
    if (!Strings.isNullOrEmpty(cipdTag)) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Pulling foil-provision CIPD package with tag/version: %s", cipdTag);
      if (foilProvisionDownloadedDir != null) {
        CrosCipdUtil.cleanupTempDir(fileUtil, foilProvisionDownloadedDir, testInfo);
        foilProvisionDownloadedDir = null;
      }
      String binaryName = "foil-provision";
      Path downloaded =
          CrosCipdUtil.downloadPackage(
              commandExecutor,
              fileUtil,
              FOIL_PROVISION_PACKAGE,
              cipdTag,
              /* destDir= */ null,
              binaryName,
              testInfo,
              CrosCipdUtil.DEFAULT_CIPD_TIMEOUT);
      resolvedFoilProvisionPath = downloaded.toAbsolutePath().toString();
      foilProvisionDownloadedDir = CrosCipdUtil.getPackageRootDir(downloaded, binaryName);
      return;
    }

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "No CIPD tag specified for foil-provision; using pre-installed binary at %s",
            FOIL_PROVISION_CIPD_PATH);
    resolvedFoilProvisionPath = FOIL_PROVISION_CIPD_PATH;
  }

  /** Resolves the dt-converter binary path. */
  @VisibleForTesting
  void resolveDtConverterPath(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    String cipdTag = getParam(testInfo, DT_CONVERTER_CIPD_TAG, DEFAULT_CIPD_TAG);
    if (!Strings.isNullOrEmpty(cipdTag)) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Pulling dt-converter CIPD package with tag/version: %s", cipdTag);
      if (dtConverterDownloadedDir != null) {
        CrosCipdUtil.cleanupTempDir(fileUtil, dtConverterDownloadedDir, testInfo);
        dtConverterDownloadedDir = null;
      }
      String binaryName = "dt-converter";
      Path downloaded =
          CrosCipdUtil.downloadPackage(
              commandExecutor,
              fileUtil,
              DT_CONVERTER_PACKAGE,
              cipdTag,
              /* destDir= */ null,
              binaryName,
              testInfo,
              CrosCipdUtil.DEFAULT_CIPD_TIMEOUT);
      resolvedDtConverterPath = downloaded.toAbsolutePath().toString();
      dtConverterDownloadedDir = CrosCipdUtil.getPackageRootDir(downloaded, binaryName);
      return;
    }

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "No CIPD tag specified for dt-converter; using pre-installed binary at %s",
            DT_CONVERTER_CIPD_PATH);
    resolvedDtConverterPath = DT_CONVERTER_CIPD_PATH;
  }

  /** Constructs the foil-provision ate command. */
  @VisibleForTesting
  Command generateProvisionCommand(
      TestInfo testInfo, String dutName, String buildId, String buildTarget)
      throws MobileHarnessException {
    List<String> args = new ArrayList<>();
    args.add(resolvedFoilProvisionPath);
    args.add("ate");
    args.add("-dut-name");
    args.add(dutName);
    args.add("-build-id");
    args.add(buildId);
    args.add("-build-target");
    args.add(buildTarget);
    args.add("-labservice-address");
    args.add(getInventoryServiceAddress());
    args.add("-log-path");
    args.add(testInfo.getGenFileDir());

    if (Ascii.equalsIgnoreCase("true", getParam(testInfo, USE_SIGNED_IMAGE, "false"))) {
      args.add("-use-signed-image");
    }
    if (Ascii.equalsIgnoreCase("true", getParam(testInfo, USE_TEST_RAMDISK, "false"))) {
      args.add("-use-test-ramdisk");
    }

    Command cmd = Command.of(args).timeout(DEFAULT_FOIL_PROVISION_TIMEOUT);
    if (testInfo.locator() != null && !Strings.isNullOrEmpty(testInfo.locator().getId())) {
      cmd = cmd.extraEnv("ATE_TASK_ID", testInfo.locator().getId());
    }
    return cmd;
  }

  @Override
  protected void cleanUp(TeardownContext context) {
    TestInfo testInfo = context == null ? getTest() : context.testInfo();
    if (foilProvisionDownloadedDir != null) {
      CrosCipdUtil.cleanupTempDir(fileUtil, foilProvisionDownloadedDir, testInfo);
      foilProvisionDownloadedDir = null;
    }
    if (dtConverterDownloadedDir != null) {
      CrosCipdUtil.cleanupTempDir(fileUtil, dtConverterDownloadedDir, testInfo);
      dtConverterDownloadedDir = null;
    }
  }
}
