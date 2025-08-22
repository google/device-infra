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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import static java.lang.String.format;

import com.google.common.base.Ascii;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.event.util.AppInstallEventUtil;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.systemsetting.SystemSettingManager;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.android.parser.AndroidInstrumentationParser;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.StepAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.TestAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidInstrumentationDriverSpec;
import com.google.wireless.qa.mobileharness.shared.api.spec.EntryDelimiterSpec;
import com.google.wireless.qa.mobileharness.shared.api.step.android.InstallApkStep;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidInstrumentationSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Driver for running instrumentation tests on Android real devices/emulators. */
@DriverAnnotation(
    help =
        "For running instrumentation tests on Android real devices/emulators, including Espresso, "
            + "instrumentation-based UiAutomator, etc. For shell-based UiAutomator, use "
            + "AndroidUIAutomator driver instead.")
@TestAnnotation(
    required = false,
    help =
        "You can leave this empty and Mobile Harness will retrieve all the test "
            + "methods(except the suppress ones) from the test apk. Or you can specify the test "
            + "class or method names you want to run. Or 'all'/'small'/'medium'/'large' to run "
            + "all/small/medium/large tests in the test apk.")
public class AndroidInstrumentation extends BaseDriver
    implements AndroidInstrumentationDriverSpec,
        EntryDelimiterSpec,
        SpecConfigable<AndroidInstrumentationSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @StepAnnotation private final InstallApkStep installApkStep;

  /** An entry in the sequence option maps, to skip the run of the option entry. */
  private static final ImmutableMap<String, String> SKIPPED_OPTION_MAP = ImmutableMap.of();

  /** {@code AndroidInstrumentationUtil} fir instrument test on device. */
  private final AndroidInstrumentationUtil androidInstrumentationUtil;

  /** {@code AndroidPackageManagerUtil} for package operations on Android devices. */
  protected final AndroidPackageManagerUtil androidPackageManagerUtil;

  /** Android SDK ADB shell command line tool executor. */
  private final Adb adb;

  /** {@code Aapt} for AAPT operations. */
  protected final Aapt aapt;

  /** {@code ApkInstaller} for common apk installation. */
  protected final ApkInstaller apkInstaller;

  private final SystemSettingManager systemSettingManager;

  /** Util for host side file operations. */
  private final LocalFileUtil fileUtil;

  /** For broadcasting messages when starting/finishing install an app. */
  private final TestMessageUtil testMessageUtil;

  @Inject
  AndroidInstrumentation(
      Device device,
      TestInfo testInfo,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      Adb adb,
      Aapt aapt,
      ApkInstaller apkInstaller,
      SystemSettingManager systemSettingManager,
      LocalFileUtil fileUtil,
      TestMessageUtil testMessageUtil,
      AndroidInstrumentationUtil androidInstrumentationUtil,
      InstallApkStep installApkStep) {
    super(device, testInfo);
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.adb = adb;
    this.aapt = aapt;
    this.apkInstaller = apkInstaller;
    this.systemSettingManager = systemSettingManager;
    this.fileUtil = fileUtil;
    this.testMessageUtil = testMessageUtil;
    this.androidInstrumentationUtil = androidInstrumentationUtil;
    this.installApkStep = installApkStep;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    Stopwatch stopwatch = Stopwatch.createUnstarted();
    Device device = getDevice();
    String deviceId = device.getDeviceId();
    JobInfo job = testInfo.jobInfo();
    List<String> buildPackageNames;
    String testPackageName;

    ImmutableSet<String> jobBuildApks = getBuildApks(testInfo);
    String testApk = getTestApk(testInfo);

    int deviceSdkVersion = systemSettingManager.getDeviceSdkVersion(device);

    // Install apks.
    stopwatch.start();
    try {
      buildPackageNames =
          installApkStep.installBuildApks(
              device, testInfo, jobBuildApks, /* installSuccessHandler= */ null, /* spec= */ null);
      testPackageName = installTestApks(testInfo, deviceSdkVersion, testApk);
    } catch (MobileHarnessException e) {
      if (installApkStep.isInstallFailure(e, testInfo)) {
        return;
      } else {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_INSTRUMENTATION_INSTALL_APK_ERROR, e.getMessage(), e);
      }
    }
    stopwatch.stop();
    long installApkTimeMs = stopwatch.elapsed().toMillis();
    testInfo
        .properties()
        .add(
            PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_INSTALL_APK_TIME_MS,
            Long.toString(installApkTimeMs));
    testInfo
        .properties()
        .add(
            PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_INSTALL_APK_TIME_SEC,
            Long.toString(installApkTimeMs / 1000));
    stopwatch.reset();

    // Gets the runner name.
    String runnerName =
        androidInstrumentationUtil.getTestRunnerClassName(
            testInfo, deviceId, testPackageName, testApk, /* analyzeApk= */ false);

    // Gets the option maps.
    List<Map<String, String>> optionMaps = getOptionMaps(job);

    // Cleans $EXTERNAL_STORAGE/googletest/ dir.
    Optional<String> externalStoragePath =
        androidInstrumentationUtil.cleanTestStorageOnDevice(testInfo, deviceId, deviceSdkVersion);

    // Pushed test data.
    androidInstrumentationUtil.pushTestData(
        testInfo,
        deviceId,
        job.files().get(AndroidInstrumentationDriverSpec.TAG_TEST_DATA),
        externalStoragePath.orElse(null));

    // Prepares the test arg.
    prepareTestArgs(testInfo, externalStoragePath.orElse(null));

    // Finalizes the test target.
    String testTarget = getTestTarget(testInfo, optionMaps);

    // Sets the test properties.
    testInfo.properties().add(AndroidInstrumentationDriverSpec.PROPERTY_PACKAGE, testPackageName);
    testInfo.properties().add(AndroidInstrumentationDriverSpec.PROPERTY_RUNNER, runnerName);

    if (optionMaps.size() == 1
        && job.params().get(AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_0") == null) {
      testInfo
          .properties()
          .add(
              AndroidInstrumentationDriverSpec.PROPERTY_OPTIONS,
              StrUtil.DEFAULT_MAP_JOINER.join(optionMaps.get(0)));
    } else {
      testInfo
          .properties()
          .add(
              AndroidInstrumentationDriverSpec.PROPERTY_ITER_COUNT,
              String.valueOf(optionMaps.size()));
      int idx = 0;
      for (Map<String, String> optionMap : optionMaps) {
        testInfo
            .properties()
            .add(
                AndroidInstrumentationDriverSpec.PROPERTY_OPTIONS + "_" + idx++,
                optionMap == SKIPPED_OPTION_MAP
                    ? AndroidInstrumentationDriverSpec.SKIPPED_OPTION_STR
                    : StrUtil.DEFAULT_MAP_JOINER.join(optionMap));
      }
    }

    Long instrumentTimeoutMs = null;
    if (job.params().get(AndroidInstrumentationDriverSpec.PARAM_INSTRUMENT_TIMEOUT_SEC) != null) {
      instrumentTimeoutMs =
          Duration.ofSeconds(
                  Integer.parseInt(
                      job.params()
                          .get(AndroidInstrumentationDriverSpec.PARAM_INSTRUMENT_TIMEOUT_SEC)))
              .toMillis();
    }
    // Prints raw test results preparing to parse.
    boolean showRawResults = androidInstrumentationUtil.showRawResultsIfNeeded(testInfo);

    boolean noIsolatedStorage =
        job.params()
            .isTrue(
                AndroidInstrumentationDriverSpec
                    .PARAM_DISABLE_ISOLATED_STORAGE_FOR_INSTRUMENTATION);

    stopwatch.start();
    if (job.params().isTrue(AndroidInstrumentationDriverSpec.PARAM_ASYNC)) {
      // When async is enable, only one single run with only one option map is allowed.
      Map<String, String> optionMap = Iterables.getOnlyElement(optionMaps);
      asyncRun(
          testInfo,
          optionMap,
          testTarget,
          testPackageName,
          runnerName,
          deviceSdkVersion,
          instrumentTimeoutMs,
          showRawResults,
          noIsolatedStorage);
    } else {
      try {
        syncRun(
            testInfo,
            optionMaps,
            testTarget,
            testPackageName,
            runnerName,
            deviceSdkVersion,
            instrumentTimeoutMs,
            buildPackageNames,
            showRawResults,
            noIsolatedStorage);
      } finally {
        androidInstrumentationUtil.pullInstrumentationFilesFromDevice(
            deviceId, testInfo, externalStoragePath.orElse(null));
      }
    }
    stopwatch.stop();
    long executeTimeMs = stopwatch.elapsed().toMillis();
    testInfo
        .properties()
        .add(
            PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_EXECUTION_TIME_MS,
            Long.toString(executeTimeMs));
    testInfo
        .properties()
        .add(
            PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_EXECUTION_TIME_SEC,
            Long.toString(executeTimeMs / 1000));
    stopwatch.reset();
  }

  /** Async run the instrumentation. */
  private void asyncRun(
      TestInfo testInfo,
      Map<String, String> optionMap,
      @Nullable String testTarget,
      String testPackageName,
      String runnerName,
      @Nullable Integer deviceSdkVersion,
      @Nullable Long instrumentTimeoutMs,
      boolean showRawResults,
      boolean noIsolatedStorage)
      throws MobileHarnessException, InterruptedException {
    JobInfo job = testInfo.jobInfo();
    // When async is enable, only one option map is allowed.
    long testTimeoutMs = testInfo.timer().remainingTimeJava().toMillis();
    if (instrumentTimeoutMs != null) {
      testTimeoutMs = Math.min(testTimeoutMs, instrumentTimeoutMs);
    }
    String deviceId = getDevice().getDeviceId();
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "%nAsync run instrumentation: %n"
                + " - test target: %s%n - timeout: %d ms%n - options: %s",
            testTarget, testTimeoutMs, StrUtil.DEFAULT_MAP_JOINER.join(optionMap));
    if (deviceSdkVersion == null) {
      try {
        deviceSdkVersion = systemSettingManager.getDeviceSdkVersion(getDevice());
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_INSTRUMENTATION_GET_SDK_VERSION_ERROR,
            "Failed to get device SDK version info",
            e);
      }
    }
    if (deviceSdkVersion < 21) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_INCOMPATIBLE_DEVICE,
          "Device sdk version is " + deviceSdkVersion + " (<21) which cannot support async run.");
    }
    try {
      androidInstrumentationUtil.instrument(
          deviceId,
          deviceSdkVersion,
          AndroidInstrumentationSetting.create(
              testPackageName,
              runnerName,
              testTarget,
              optionMap,
              /* async= */ true,
              showRawResults,
              job.params()
                  .getBool(AndroidInstrumentationDriverSpec.PARAM_PREFIX_ANDROID_TEST, false),
              noIsolatedStorage,
              /* useTestStorageService= */ true,
              job.params().getBool(AndroidInstrumentationDriverSpec.PARAM_ENABLE_COVERAGE, false)),
          Duration.ofMillis(testTimeoutMs));
    } catch (MobileHarnessException e) {
      if (e.getErrorId() == AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_TIMEOUT) {
        testInfo.resultWithCause().setNonPassing(TestResult.TIMEOUT, e);
      }
      throw e;
    }
    testInfo.log().atInfo().alsoTo(logger).log("Instrumentation started\n");
    // Async run is usually used in battery tests. No way to adjust the test result at this moment.
    // Leaves it to the latter logic of the decorators/plugins.
  }

  /** Sync run the instrumentation. */
  @SuppressWarnings("ReferenceEquality")
  private void syncRun(
      TestInfo testInfo,
      List<Map<String, String>> optionMaps,
      @Nullable String testTarget,
      String testPackageName,
      String runnerName,
      @Nullable Integer deviceSdkVersion,
      @Nullable Long instrumentTimeoutMs,
      List<String> buildPackageNames,
      boolean showRawResults,
      boolean noIsolatedStorage)
      throws MobileHarnessException, InterruptedException {
    JobInfo job = testInfo.jobInfo();
    Properties properties = testInfo.properties();
    String deviceId = getDevice().getDeviceId();

    boolean hasError = false;
    boolean hasFail = false;
    boolean hasPass = false;
    MobileHarnessException errorResultCause = null;
    MobileHarnessException failResultCause = null;

    properties.add(PropertyName.Test.ANDROID_INST_TEST_METHOD_REPEAT_TIMES, String.valueOf(1));
    boolean hasSequentialOptionMaps =
        optionMaps.size() > 1
            || job.params().get(AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_0") != null;

    boolean iterClearBuild =
        optionMaps.size() > 1
            && !buildPackageNames.isEmpty()
            && job.params().isTrue(AndroidInstrumentationDriverSpec.PARAM_ITER_CLEAR_BUILD);
    boolean iterClearTest =
        optionMaps.size() > 1
            && job.params().isTrue(AndroidInstrumentationDriverSpec.PARAM_ITER_CLEAR_TEST);

    StringBuilder logFilenames = new StringBuilder();

    for (int optionMapIdx = 0; optionMapIdx < optionMaps.size(); optionMapIdx++) {
      Map<String, String> optionMap = optionMaps.get(optionMapIdx);
      if (optionMap == SKIPPED_OPTION_MAP) {
        // Skips this run.
        properties.add(
            AndroidInstrumentationDriverSpec.PROPERTY_RESULT + "_" + optionMapIdx,
            AndroidInstrumentationDriverSpec.SKIPPED_OPTION_STR);
        continue;
      }

      // Run adb shell commands before running instrument.
      if (hasSequentialOptionMaps) {
        String beforeIterShellCmd =
            job.params()
                .get(
                    AndroidInstrumentationDriverSpec.PARAM_ITER_ADB_SHELL_BEFORE_INSTRUMENTATION
                        + "_"
                        + optionMapIdx);
        if (beforeIterShellCmd != null) {
          try {
            String beforeTestOutput =
                adb.runShell(
                    deviceId, beforeIterShellCmd, /* timeout= */ null, /* lineCallback= */ null);
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log("Run [adb shell %s]:%n%s%n", beforeIterShellCmd, beforeTestOutput);
          } catch (MobileHarnessException e) {
            throw new MobileHarnessException(
                AndroidErrorId.ANDROID_INSTRUMENTATION_ADB_COMMAND_ERROR,
                format("Failed to execute ADB command before test: %s", beforeIterShellCmd),
                e);
          }
        }
      }

      // Actually run the instrument.
      long testTimeoutMs = testInfo.timer().remainingTimeJava().toMillis();
      if (instrumentTimeoutMs != null) {
        testTimeoutMs = Math.min(testTimeoutMs, instrumentTimeoutMs);
      }
      String message =
          String.format(
              "%nStart instrumentation: %n"
                  + " - test target: %s%n - timeout: %d ms%n - options: %s",
              testTarget, testTimeoutMs, StrUtil.DEFAULT_MAP_JOINER.join(optionMap));
      testInfo.log().atInfo().alsoTo(logger).log("%s", message);
      String output;
      MobileHarnessException mhException = null;
      try {
        output =
            androidInstrumentationUtil.instrument(
                deviceId,
                deviceSdkVersion,
                AndroidInstrumentationSetting.create(
                    testPackageName,
                    runnerName,
                    testTarget,
                    optionMap,
                    /* async= */ false,
                    showRawResults,
                    job.params()
                        .getBool(AndroidInstrumentationDriverSpec.PARAM_PREFIX_ANDROID_TEST, false),
                    noIsolatedStorage,
                    /* useTestStorageService= */ true,
                    job.params()
                        .getBool(AndroidInstrumentationDriverSpec.PARAM_ENABLE_COVERAGE, false)),
                Duration.ofMillis(testTimeoutMs));
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .withCause(e)
            .log("MobileHarnessException captured during instrumentation test");
        mhException = e;
        output = e.getMessage();
      }

      testInfo.log().atInfo().alsoTo(logger).log("Finish instrumentation: %s", output);
      // Also log to file.
      String instrumentLogFileName =
          "instrument" + (hasSequentialOptionMaps ? "_" + optionMapIdx : "") + ".log";
      try {
        fileUtil.writeToFile(
            PathUtil.join(testInfo.getGenFileDir(), instrumentLogFileName), output);
        logFilenames.append(instrumentLogFileName).append(";");
      } catch (MobileHarnessException e) {
        testInfo
            .warnings()
            .addAndLog(
                new MobileHarnessException(
                    AndroidErrorId.ANDROID_INSTRUMENTATION_WRITE_FILE_ERROR, e.getMessage()),
                logger);
      }

      // Checks the result.
      boolean useAndroidInstrumentationParser =
          job.params()
              .getBool(
                  AndroidInstrumentationDriverSpec.PARAM_USE_ANDROIDINSTRUMENTATION_PARSER, true);
      TestResult result;
      StringBuilder errorMsg = new StringBuilder();

      if (useAndroidInstrumentationParser) {
        AndroidInstrumentationParser parser = new AndroidInstrumentationParser();
        result =
            parser.parseOutput(
                output,
                errorMsg,
                mhException,
                job.params().getBool(PARAM_FAIL_IF_NO_TESTS_RAN, false));
      } else {
        String filePathOnDevice =
            job.params().get(AndroidInstrumentationDriverSpec.PARAM_GTEST_XML_FILE_ON_DEVICE);
        if (filePathOnDevice == null) {
          // TODO: Move this check to AndroidInstrumentationJobValidator class.
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_INSTRUMENTATION_GTEST_XML_FILE_ON_DEVICE_NOT_SET,
              "Param gtest_xml_file_on_device is not set."
                  + " This is required when using gtest XML parser.");
        }
        AndroidInstrumentationUtil.GtestResult gtestResult =
            androidInstrumentationUtil.processGtestResult(
                deviceId, testInfo, filePathOnDevice, mhException);
        result = gtestResult.testResult();
        errorMsg.append(gtestResult.errorMessage());
      }

      switch (result) {
        case PASS:
          hasPass = true;
          break;
        case FAIL:
          hasFail = true;
          failResultCause =
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_FAILED,
                  "Instrumentation failures: "
                      + errorMsg
                      + "\nThis usually indicates validation errors of the test app, or "
                      + "bugs of the app under test. You should be able to reproduce it with "
                      + "\"adb shell am instrument ...\" with your local devices, without "
                      + "Mobile Harness.",
                  mhException);
          break;
        case ERROR:
          hasError = true;
          errorResultCause =
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_ERROR,
                  "Instrumentation start/finish unexpectedly: " + errorMsg,
                  mhException);
          break;
        case TIMEOUT:
          MobileHarnessException timeoutCause =
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_TIMEOUT,
                  String.format(
                      "Instrumentation timeout [please try to increase test_timeout_sec"
                          + ", or instrument_timeout_sec if it has been set.]:%n%s",
                      errorMsg),
                  mhException);
          testInfo.resultWithCause().setNonPassing(TestResult.TIMEOUT, timeoutCause);
          throw timeoutCause;
        default:
          MobileHarnessException errorCause =
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_RESULT_NOT_FOUND,
                  "Unexpected Test Result Found",
                  mhException);
          testInfo.resultWithCause().setNonPassing(TestResult.ERROR, errorCause);
          throw errorCause;
      }

      // Extra adb shell commands or clean up after instrument.
      if (hasSequentialOptionMaps) {
        String afterTestShellCmd =
            job.params()
                .get(
                    AndroidInstrumentationDriverSpec.PARAM_ITER_ADB_SHELL_AFTER_INSTRUMENTATION
                        + "_"
                        + optionMapIdx);
        if (afterTestShellCmd != null) {
          try {
            String afterTestOutput =
                adb.runShell(
                    deviceId, afterTestShellCmd, /* timeout= */ null, /* lineCallback= */ null);
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log("Run [adb shell %s]:%n%s%n", afterTestShellCmd, afterTestOutput);
          } catch (MobileHarnessException e) {
            // Only log the failure after the main test finished.
            testInfo
                .warnings()
                .addAndLog(
                    new MobileHarnessException(
                        AndroidErrorId.ANDROID_INSTRUMENTATION_ADB_COMMAND_ERROR,
                        String.format(
                            "Failed to execute ADB command after test: %s", afterTestShellCmd),
                        e),
                    logger);
          }
        }

        // Set result property.
        if (result != null) {
          properties.add(
              AndroidInstrumentationDriverSpec.PROPERTY_RESULT + "_" + optionMapIdx, result.name());
        }
        try {
          // Cleanup.
          if (iterClearBuild) {
            for (String buildPackageName : buildPackageNames) {
              androidPackageManagerUtil.clearPackage(deviceId, buildPackageName);
              testInfo
                  .log()
                  .atInfo()
                  .alsoTo(logger)
                  .log("%nClear build package %s", buildPackageName);
            }
          }
          if (iterClearTest) {
            androidPackageManagerUtil.clearPackage(deviceId, testPackageName);
            testInfo.log().atInfo().alsoTo(logger).log("%nClear test package %s", testPackageName);
          }
        } catch (MobileHarnessException e) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_INSTRUMENTATION_CLEAR_PACKAGE_ERROR,
              "Failed to clear package with exception raised",
              e);
        }
      }
    }

    if (logFilenames.length() > 0) {
      // Remove the trailing ";"
      logFilenames.deleteCharAt(logFilenames.length() - 1);
    }
    properties.add(
        PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_LOG_FILENAMES,
        logFilenames.toString());

    // Uses the worse case as the final result.
    if (hasError) {
      testInfo.resultWithCause().setNonPassing(TestResult.ERROR, errorResultCause);
    } else if (hasFail) {
      testInfo.resultWithCause().setNonPassing(TestResult.FAIL, failResultCause);
    } else if (hasPass) {
      testInfo.resultWithCause().setPass();
    }
  }

  /**
   * Installs the test apk, basic_services.apk and test_services.apk.
   *
   * @return the package name of the test apk
   */
  private String installTestApks(TestInfo testInfo, int deviceSdkVersion, String testApk)
      throws MobileHarnessException, InterruptedException {
    // Gets the package name from the test apk.
    String testPackageName = aapt.getApkPackageName(testApk);

    // Installs APKs.
    postAppInstallStartEvent(testInfo, testPackageName);
    apkInstaller.installApkIfNotExist(
        getDevice(),
        ApkInstallArgs.builder()
            .setApkPath(testApk)
            .setGrantPermissions(true)
            .setSkipDowngrade(false)
            .build(),
        testInfo.log());
    postAppInstallFinishEvent(testInfo, testPackageName);

    androidInstrumentationUtil.prepareServicesApks(
        testInfo,
        getDevice(),
        deviceSdkVersion,
        this::postAppInstallStartEvent,
        this::postAppInstallFinishEvent);

    if (testInfo
        .jobInfo()
        .params()
        .getBool(AndroidInstrumentationDriverSpec.PARAM_DISABLE_ISOLATED_STORAGE_FOR_APK, true)) {
      // Disable isolated-storage for test apk.
      androidInstrumentationUtil.enableLegacyStorageForApk(
          getDevice().getDeviceId(), testPackageName);
    }

    return testPackageName;
  }

  /**
   * Finalizes the option maps. The result contains at least one option map, even the map is empty.
   */
  private List<Map<String, String>> getOptionMaps(JobInfo jobInfo) {
    // Finalizes the option maps.
    List<Map<String, String>> optionMaps = new ArrayList<>();
    String paramOption = jobInfo.params().get(AndroidInstrumentationDriverSpec.PARAM_OPTIONS);
    boolean commaEncoded = false;
    if (paramOption != null
        && paramOption.contains(AndroidInstrumentationDriverSpec.COMMA_ESCAPED)) {
      commaEncoded = true;
      paramOption =
          paramOption.replace(
              AndroidInstrumentationDriverSpec.COMMA_ESCAPED,
              AndroidInstrumentationDriverSpec.COMMA_ENCODED);
    }
    Map<String, String> singletonOptionMap = new HashMap<>(StrUtil.toMap(paramOption, true));
    if (!singletonOptionMap.isEmpty()) {
      optionMaps.add(singletonOptionMap);
    } else {
      for (int i = 0; ; i++) {
        String paramKey = AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_" + i;
        String paramValue = jobInfo.params().get(paramKey);
        if (paramValue == null) {
          break;
        } else if (paramValue.equals(AndroidInstrumentationDriverSpec.SKIPPED_OPTION_STR)) {
          optionMaps.add(SKIPPED_OPTION_MAP);
          continue;
        }
        if (paramValue.contains(AndroidInstrumentationDriverSpec.COMMA_ESCAPED)) {
          commaEncoded = true;
          paramValue =
              paramValue.replace(
                  AndroidInstrumentationDriverSpec.COMMA_ESCAPED,
                  AndroidInstrumentationDriverSpec.COMMA_ENCODED);
        }
        Map<String, String> sequentialOptionMap = new HashMap<>(StrUtil.toMap(paramValue, true));
        if (sequentialOptionMap.isEmpty()) {
          break;
        }
        optionMaps.add(sequentialOptionMap);
      }
      if (optionMaps.isEmpty()) {
        // No options specified, adds an empty option map.
        optionMaps.add(singletonOptionMap);
      }
    }

    if (commaEncoded) {
      for (Map<String, String> optionMap : optionMaps) {
        for (Map.Entry<String, String> entry : optionMap.entrySet()) {
          entry.setValue(
              entry.getValue().replace(AndroidInstrumentationDriverSpec.COMMA_ENCODED, ","));
        }
      }
    }
    return optionMaps;
  }

  /** Get test target from either test name or user input. */
  private String getTestTarget(TestInfo testInfo, List<Map<String, String>> optionMaps) {
    String testName = testInfo.locator().getName();
    String testTarget;
    if (Ascii.equalsIgnoreCase(testName, AndroidInstrumentationDriverSpec.TEST_NAME_ALL)) {
      testTarget = null;
      testInfo.log().atInfo().alsoTo(logger).log("Running all tests");
    } else if (Ascii.equalsIgnoreCase(testName, AndroidInstrumentationDriverSpec.TEST_NAME_SMALL)
        || Ascii.equalsIgnoreCase(testName, AndroidInstrumentationDriverSpec.TEST_NAME_MEDIUM)
        || Ascii.equalsIgnoreCase(testName, AndroidInstrumentationDriverSpec.TEST_NAME_LARGE)) {
      testTarget = null;
      String size = Ascii.toLowerCase(testName);
      for (Map<String, String> optionMap : optionMaps) {
        optionMap.put("size", size);
      }
      testInfo.log().atInfo().alsoTo(logger).log("Running all %s tests", size);
    } else {
      testTarget = testName;
    }
    return testTarget;
  }

  private void postAppInstallFinishEvent(TestInfo testInfo, String packageName) {
    if (!testInfo
        .jobInfo()
        .params()
        .getBool(AndroidInstrumentationDriverSpec.PARAM_BROADCAST_INSTALL_MESSAGE, false)) {
      return;
    }
    try {
      testMessageUtil.sendMessageToTest(
          testInfo.locator().getId(),
          AppInstallEventUtil.createFinishMessage(getDevice().getDimensions(), packageName));
    } catch (MobileHarnessException e) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_INSTRUMENTATION_CREATE_MESSAGE_ERROR, e.getMessage()),
              logger);
    }
  }

  private void postAppInstallStartEvent(TestInfo testInfo, String packageName) {
    if (!testInfo
        .jobInfo()
        .params()
        .getBool(AndroidInstrumentationDriverSpec.PARAM_BROADCAST_INSTALL_MESSAGE, false)) {
      return;
    }
    try {
      testMessageUtil.sendMessageToTest(
          testInfo.locator().getId(),
          AppInstallEventUtil.createStartMessage(getDevice().getDimensions(), packageName));
    } catch (MobileHarnessException e) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_INSTRUMENTATION_CREATE_MESSAGE_ERROR, e.getMessage()),
              logger);
    }
  }

  /** Cleans/creates test args files. */
  private void prepareTestArgs(TestInfo testInfo, @Nullable String externalStoragePath)
      throws MobileHarnessException, InterruptedException {
    ImmutableMap<String, String> testArgs = androidInstrumentationUtil.getTestArgs(testInfo);
    androidInstrumentationUtil.prepareTestArgs(
        getDevice().getDeviceId(),
        testArgs,
        externalStoragePath,
        testInfo.getTmpFileDir(),
        testInfo.log(),
        testInfo.warnings(),
        /* forceAdbPush= */ false);
  }

  protected ImmutableSet<String> getBuildApks(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    return ImmutableSet.copyOf(androidInstrumentationUtil.getBuildApks(testInfo));
  }

  protected String getTestApk(TestInfo testInfo) {
    return Iterables.getOnlyElement(
        testInfo.jobInfo().files().get(AndroidInstrumentationDriverSpec.TAG_TEST_APK));
  }
}
