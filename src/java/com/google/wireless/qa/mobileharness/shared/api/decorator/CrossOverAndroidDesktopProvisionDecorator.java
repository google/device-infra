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
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
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
 * <p>Resolves DUT stable OS and firmware image targets (via dt-converter and labservice) and
 * provisions the device using foil-provision.
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
  public static final Duration DEFAULT_FOIL_PROVISION_TIMEOUT =
      CrosDecoratorSpec.DEFAULT_FOIL_PROVISION_TIMEOUT;
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
    super(driver, testInfo);
    this.commandExecutor = commandExecutor;
    this.fileUtil = new LocalFileUtil();
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
   * Testing constructor with injected dependencies.
   *
   * @param driver the decorated driver
   * @param testInfo the current test context
   * @param commandExecutor executor used to run shell commands
   * @param fileUtil local file utility
   */
  @VisibleForTesting
  CrossOverAndroidDesktopProvisionDecorator(
      Driver driver, TestInfo testInfo, CommandExecutor commandExecutor, LocalFileUtil fileUtil) {
    super(driver, testInfo);
    this.commandExecutor = commandExecutor;
    this.fileUtil = fileUtil;
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

    CommandResult result;
    try {
      result = commandExecutor.exec(provisionCommand);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          e.getErrorId(),
          String.format("foil-provision failed for device %s: %s", dutName, e.getMessage()),
          e);
    }
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("CrossOver provisioning finished successfully for %s:\n%s", dutName, result.stdout());

    return SetupResult.continueDecorated();
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

    return Command.of(args).timeout(DEFAULT_FOIL_PROVISION_TIMEOUT);
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
