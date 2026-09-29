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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.util.ErrorIdComparator;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.systemsetting.SystemSettingManager;
import com.google.devtools.mobileharness.platform.android.nativebin.NativeBinArgs;
import com.google.devtools.mobileharness.platform.android.nativebin.NativeBinUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.android.AndroidBuildSpecKey;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.TestAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidNativeBinSpec;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import java.io.File;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

/** Driver for running Android native binaries on Android real devices/emulators. */
@DriverAnnotation(
    help =
        "For running Android native binaries implemented with Android NDK"
            + "(https://developer.android.com/ndk/), such as using C and C++. ")
@TestAnnotation(
    required = false,
    help =
        "You can leave this empty and Mobile Harness will retrieve all the binary name from "
            + "the binary files. Or you can specify the binary name you want to run.")
public class AndroidNativeBin extends BaseDriver implements AndroidNativeBinSpec {

  /** Logger for this device. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * {@code AndroidSystemStateUtil} for performing Android system state related operations on
   * Android device.
   */
  protected final AndroidSystemStateUtil systemStateUtil;

  /** {@code AndroidFileUtil} for managing file on Android device. */
  protected final AndroidFileUtil androidFileUtil;

  /** {@code LocalFileUtil} for saving output file into local host. */
  protected final LocalFileUtil fileUtil;

  private final NativeBinUtil nativeBinUtil;

  private final SystemSettingManager systemSettingManager;

  /** Default file name to save binary output. */
  private static final String DEFAULT_OUTPUT_NAME = "android_native_bin.log";

  /** Test property for cpu affinity. */
  private static final String PROPERTY_CPU_AFFINITY = "android_native_bin_cpu_affinity";

  /** Reserved time for TestManager to process test result. */
  private static final Duration RESERVED_TIME_FOR_RESULT_PROCESSING = Duration.ofSeconds(20);

  /** The following exceptions for running the binary are considered as test failures. */
  private static final ImmutableSet<ErrorId> ERROR_IDS_OF_TEST_FAILURES =
      ImmutableSet.of(
          AndroidErrorId.NATIVE_BIN_UTIL_RUN_NATIVE_BIN_FAILURE,
          AndroidErrorId.NATIVE_BIN_UTIL_RUN_NATIVE_BIN_NULL_COMMAND_RESULT_ERROR,
          AndroidErrorId.NATIVE_BIN_UTIL_RUN_NATIVE_BIN_NON_ZERO_EXIT_CODE_FROM_ECHO,
          AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR,
          AndroidErrorId.ANDROID_ADB_SYNC_CMD_START_ERROR);

  @Inject
  AndroidNativeBin(
      Device device,
      TestInfo testInfo,
      AndroidSystemStateUtil systemStateUtil,
      AndroidFileUtil androidFileUtil,
      LocalFileUtil fileUtil,
      NativeBinUtil nativeBinUtil,
      SystemSettingManager systemSettingManager) {
    super(device, testInfo);
    this.systemStateUtil = systemStateUtil;
    this.androidFileUtil = androidFileUtil;
    this.fileUtil = fileUtil;
    this.nativeBinUtil = nativeBinUtil;
    this.systemSettingManager = systemSettingManager;
  }

  @Override
  public void run(final TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    JobInfo jobInfo = testInfo.jobInfo();
    Params params = jobInfo.params();
    String options = getOptions(params);
    String testUser = params.get(PARAM_TEST_USER);
    String testPath = testInfo.locator().getName();
    String runDir = params.get(PARAM_RUN_DIR, DEFAULT_RUN_DIR);
    Optional<Integer> sdkVersion = getDeviceSdkVersion();

    // Save test binary to device.
    prepareTestBinary(testInfo, runDir);

    String cpuAffinity = null;
    // taskset is only available in device with API > 22.
    if (sdkVersion.orElse(0) > AndroidVersion.LOLLIPOP.getEndSdkVersion()) {
      cpuAffinity = params.get(PARAM_CPU_AFFINITY);
    }

    String runEnvironment = params.getOptional(PARAM_RUN_ENVIRONMENT).orElse("");
    if (jobInfo.properties().has(PropertyName.Job.SESSION_ID)) {
      runEnvironment += " SPONGE_ID=" + jobInfo.properties().get(PropertyName.Job.SESSION_ID);
    }

    // Update testPath and runDir accordingly if Zip file is provided
    ImmutableSet<String> binZipPaths = jobInfo.files().get(TAG_BIN_ZIP);
    if (!binZipPaths.isEmpty()) {
      ImmutableSet<String> binPaths = jobInfo.files().get(TAG_BIN);
      testPath = getUnzipFilePath(binPaths, testInfo.locator().getName());
      runDir = PathUtil.join(runDir, DEFAULT_UNZIP_DIR_NAME);
    }

    // Runs the binary.
    Duration actualTimeout = getActualTimeout(testInfo);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Start running binary [%s] as %s on CPU %s at \"%s\" with environment (%s) and "
                + "options [%s] in %s",
            testPath,
            Strings.isNullOrEmpty(testUser) ? "root" : testUser,
            Strings.isNullOrEmpty(cpuAffinity) ? "all" : cpuAffinity,
            runDir,
            runEnvironment,
            options,
            actualTimeout);
    CommandResult commandResult = null;
    // TODO: Split and save stdout/stderr to different file.
    try {
      NativeBinArgs.Builder nativeBinArgsBuilder =
          NativeBinArgs.builder()
              .setRunDirectory(runDir)
              .setBinary(testPath)
              .setCommandTimeout(actualTimeout)
              .setStdoutLineCallback(
                  LineCallback.does(line -> testInfo.log().atInfo().log("%s", line)));
      if (testUser != null) {
        nativeBinArgsBuilder.setRunAs(testUser);
      }
      if (runEnvironment != null) {
        nativeBinArgsBuilder.setRunEnvironment(runEnvironment);
      }
      if (options != null) {
        nativeBinArgsBuilder.setOptions(options);
      }
      if (cpuAffinity != null) {
        nativeBinArgsBuilder.setCpuAffinity(cpuAffinity);
      }
      // Calls '{@code echo $?}' after shell command to get shell command exit code in command
      // output. For device sdk <= 23, adb shell doesn't return exit code of program (b/137390305).
      boolean echoCommandExitCode =
          sdkVersion.isPresent()
              && sdkVersion.get() <= AndroidVersion.MARSHMALLOW.getStartSdkVersion();
      nativeBinArgsBuilder.setEchoCommandExitCode(echoCommandExitCode);

      commandResult = nativeBinUtil.runNativeBin(deviceId, nativeBinArgsBuilder.build());
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Finish running binary. Exit code: %d", commandResult.exitCode());
      saveTestResult(
          testInfo, commandResult, Strings.isNullOrEmpty(cpuAffinity) ? "all" : cpuAffinity);
    } catch (MobileHarnessException e) {
      if (ErrorIdComparator.equal(
          AndroidErrorId.NATIVE_BIN_UTIL_RUN_NATIVE_BIN_TIMEOUT, e.getErrorId())) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Binary [%s] timed out after %s.", testInfo.locator().getName(), actualTimeout);
        testInfo.resultWithCause().setNonPassing(TestResult.TIMEOUT, e);
      } else if (ERROR_IDS_OF_TEST_FAILURES.stream()
          .anyMatch(errorId -> ErrorIdComparator.equal(errorId, e.getErrorId()))) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Finish running test with failure: %s", e.getMessage());
        testInfo.resultWithCause().setNonPassing(TestResult.FAIL, e);
      } else {
        throw e;
      }
    }
  }

  /**
   * Returns the actual timeout for the Android native binary execution.
   *
   * @throws MobileHarnessException if the remaining test timeout is negative or zero
   */
  private Duration getActualTimeout(TestInfo testInfo) throws MobileHarnessException {
    Duration binTimeout =
        Duration.ofSeconds(testInfo.jobInfo().params().getInt(PARAM_ANDROID_BIN_TIMEOUT_SEC, 0));
    Duration remainingTestTimeout =
        testInfo.timer().remainingTimeJava().minus(RESERVED_TIME_FOR_RESULT_PROCESSING);
    if (remainingTestTimeout.isNegative() || remainingTestTimeout.isZero()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_NATIVE_BIN_REMAINING_TEST_TIMEOUT_TOO_SHORT,
          String.format(
              "Remaining test_timeout_sec=%s is negative or zero, not enough time to run the"
                  + " android native binary.",
              remainingTestTimeout));
    }

    if (binTimeout.isZero()) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "%s is not set, using the remaining test_timeout_sec: %s as the timeout for the"
                  + " Android native binary execution",
              PARAM_ANDROID_BIN_TIMEOUT_SEC, remainingTestTimeout);
      return remainingTestTimeout;
    } else if (binTimeout.compareTo(remainingTestTimeout) > 0) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log(
              "%s time %s > the remaining test_timeout_sec: %s. Using the remaining"
                  + " test_timeout_sec as the timeout for the Android native binary execution.",
              PARAM_ANDROID_BIN_TIMEOUT_SEC, binTimeout, remainingTestTimeout);
      return remainingTestTimeout;
    } else {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "%s is set to %s, which is less than the remaining test_timeout_sec: %s. Using"
                  + " %s as the timeout for the Android native binary execution.",
              PARAM_ANDROID_BIN_TIMEOUT_SEC, binTimeout, remainingTestTimeout, binTimeout);
      return binTimeout;
    }
  }

  @VisibleForTesting
  String getUnzipFilePath(Set<String> binFilePaths, String testName)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Looking for matching binary files to run %s", testName);
    for (String binFilePath : binFilePaths) {
      if (binFilePath.endsWith(testName)) {
        // Resolved file paths containing unzip_file have the following pattern:
        // ".../unzip_file=path/to/bin/path/to/bin"
        // We can use this pattern to extract the target "path/to/bin"
        String unzipFileKey = AndroidBuildSpecKey.UNZIP_FILE.name().toLowerCase(Locale.ROOT) + "=";
        Iterable<String> fields = Splitter.on(unzipFileKey).split(binFilePath);
        if (Iterables.size(fields) != 2) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_NATIVE_BIN_ANDROID_BUILD_ERROR,
              String.format(
                  "Unexpected android-build path [%s], should contain exactly one instance of [%s]",
                  binFilePath, unzipFileKey));
        }
        String unzipFile = Iterables.get(fields, 1);
        int middleSeparatorIndex = unzipFile.length() / 2;
        // +1 to skip the separator
        String testPath = unzipFile.substring(middleSeparatorIndex + 1);
        return testPath;
      } else {
        logger.atInfo().log("Skipping %s as it does not end in %s", binFilePath, testName);
      }
    }

    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_NATIVE_BIN_TEST_NOT_FOUND_IN_BIN,
        String.format(
            "Test[%s] listed by AndroidNativeBinLister but not found in BIN file(s): %s",
            testName, binFilePaths));
  }

  private void saveTestResult(TestInfo testInfo, CommandResult commandResult, String cpuAffinity) {
    String log = commandResult.stdoutWithoutTrailingLineTerminator();
    logger.atInfo().log("Saving test output:\n%s", log);
    try {
      fileUtil.writeToFile(
          PathUtil.join(testInfo.getGenFileDir(), DEFAULT_OUTPUT_NAME),
          commandResult.stdoutWithoutTrailingLineTerminator());
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("Failed to save AndroidNativeBin output to file with exception %s", e.getMessage());
    }

    testInfo.properties().add(PROPERTY_CPU_AFFINITY, cpuAffinity);
    String passSignal = testInfo.jobInfo().params().get(PARAM_PASS_SIGNAL);
    if (commandResult.exitCode() != 0) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.FAIL,
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_NATIVE_BIN_EXIT_CODE_ERROR,
                  "AndroidNativeBin returns non-zero exit code: " + commandResult.exitCode()));
    } else if (Strings.isNullOrEmpty(passSignal) || log.contains(passSignal)) {
      testInfo.resultWithCause().setPass();
    } else {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.FAIL,
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_NATIVE_BIN_SIGNAL_NOT_FOUND,
                  String.format("Failed to find signal [%s] in AndroidNativeBin log", passSignal)));
    }
  }

  private void prepareTestBinary(TestInfo testInfo, String runDir)
      throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    JobInfo jobInfo = testInfo.jobInfo();
    // sdkVersion affects log only.
    int sdkVersion = getDeviceSdkVersion().orElse(0);

    // Remount device if runDir contains /system or /vendor since these directories are read-only.
    // Device is required to be rooted before running "adb remount".
    if ((runDir.contains("/system") || runDir.contains("/vendor"))
        && systemStateUtil.becomeRoot(deviceId)) {
      systemStateUtil.waitUntilReady(deviceId);
      androidFileUtil.remount(deviceId);
    }

    // Make sure runDir exists before pushing the bin files there.
    if (!androidFileUtil.isFileOrDirExisted(deviceId, runDir)) {
      androidFileUtil.makeDirectory(deviceId, runDir);
    }

    // Pushes the bin files to the device.
    ImmutableSet<String> binFilePaths = jobInfo.files().get(TAG_BIN);
    ImmutableSet<String> binZipPaths = jobInfo.files().get(TAG_BIN_ZIP);

    if (!binZipPaths.isEmpty()) {
      String unzipDir = PathUtil.join(testInfo.getTmpFileDir(), DEFAULT_UNZIP_DIR_NAME);
      String devUnzipDir = PathUtil.join(runDir, DEFAULT_UNZIP_DIR_NAME);
      if (!androidFileUtil.isFileOrDirExisted(deviceId, devUnzipDir)) {
        for (String binZipPath : binZipPaths) {
          String binZipName = new File(binZipPath).getName();
          fileUtil.unzipFile(binZipPath, unzipDir);

          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Push unzipped contents of %s to device path %s: %s",
                  binZipName, runDir, androidFileUtil.push(deviceId, sdkVersion, unzipDir, runDir));
        }
      }
    } else {
      for (String binFilePath : binFilePaths) {
        String binFileName = new File(binFilePath).getName();
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Push %s to device path %s: %s",
                binFileName,
                runDir,
                androidFileUtil.push(deviceId, sdkVersion, binFilePath, runDir));
      }
    }
  }

  String getOptions(final Params params) {
    return params.get(PARAM_OPTIONS);
  }

  Optional<Integer> getDeviceSdkVersion() throws InterruptedException {
    Integer sdkVersion = null;
    try {
      sdkVersion = systemSettingManager.getDeviceSdkVersion(getDevice());
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to get sdk version for device %s", getDevice().getDeviceId());
    }
    return Optional.ofNullable(sdkVersion);
  }
}
