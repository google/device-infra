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

package com.google.devtools.mobileharness.infra.ats.console.util.verifier;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.StatusFilter;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.user.AndroidUserUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.linecallback.ScanSignalOutputCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.gson.Gson;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;

/** Helper class to broadcast results to CTS-V * */
public class VerifierResultHelper {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String BROADCAST_TEST_RESULT_PASS = "PASS";
  private static final String BROADCAST_TEST_RESULT_FAIL = "FAIL";
  private static final String FAIL_RESULT = "fail";

  private static final int MAX_DETAIL_LENGTH = 100;

  private static final ImmutableSet<String> REPORT_LOGS =
      ImmutableSet.of(
          "config", "device-info-files", "report-log-files", "screenshots", "vintf-files");

  @VisibleForTesting static final String CTS_VERIFIER_APK = "CtsVerifier.apk";
  @VisibleForTesting static final String VERIFIER_PACKAGE = "com.android.cts.verifier";
  @VisibleForTesting static final String MAIN_ACTIVITY = ".CtsVerifierActivity";
  @VisibleForTesting static final String HOST_TESTS_ACTIVITY = ".HostTestsActivity";

  @VisibleForTesting
  static final ImmutableList<String> CTS_VERIFIER_SETTING_SHELL_COMMANDS =
      ImmutableList.of(
          "appops set com.android.cts.verifier android:read_device_identifiers allow",
          "appops set com.android.cts.verifier MANAGE_EXTERNAL_STORAGE 0",
          "am compat enable ALLOW_TEST_API_ACCESS com.android.cts.verifier",
          "appops set com.android.cts.verifier TURN_SCREEN_ON 0");

  @VisibleForTesting
  static final String BROADCAST_COMMAND =
      "am broadcast -a com.android.cts.verifier.ACTION_HOST_TEST_RESULT --es"
          + " com.android.cts.verifier.extra.HOST_TEST_RESULT ";

  private final Adb adb;
  private final AndroidPackageManagerUtil androidPackageManagerUtil;
  private final AndroidProcessUtil androidProcessUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final AndroidUserUtil androidUserUtil;
  private final CommandExecutor commandExecutor;
  private final LocalFileUtil localFileUtil;

  @Inject
  VerifierResultHelper(
      Adb adb,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidProcessUtil androidProcessUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidUserUtil androidUserUtil,
      CommandExecutor commandExecutor,
      LocalFileUtil localFileUtil) {
    this.adb = adb;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.androidProcessUtil = androidProcessUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.androidUserUtil = androidUserUtil;
    this.commandExecutor = commandExecutor;
    this.localFileUtil = localFileUtil;
  }

  public void pushResults(
      Collection<String> serials, Result result, Path xtsRootDir, Path resultDir)
      throws InterruptedException {
    prepareCtsVerifier(serials, xtsRootDir);
    startHostTestsActivity(serials);
    broadcastResults(serials, result);
    pushReportLogFiles(serials, resultDir);
  }

  private void prepareCtsVerifier(Collection<String> serials, Path xtsRootDir)
      throws InterruptedException {
    File verifierApk = xtsRootDir.resolve(CTS_VERIFIER_APK).toFile();
    for (String serial : serials) {
      try {
        if (verifierApk.exists()) {
          installApkIfVersionNameMismatch(serial, verifierApk.getPath());
        } else {
          logger.atWarning().log(
              "Cannot find CTS Verifier APK at %s. Results cannot be pushed to %s if the Verifier"
                  + " APP has not been installed.",
              verifierApk.getPath(), serial);
        }
      } catch (MobileHarnessException e) {
        logger.atInfo().withCause(e).log("Unable to install CtsVerifier on %s", serial);
      }
      setUpCtsVerifier(serial);
    }
  }

  private void startHostTestsActivity(Collection<String> serials) throws InterruptedException {
    for (String serial : serials) {
      try {
        String currentTime = adb.runShellWithRetry(serial, "date '+%Y-%m-%d %H:%M:%S.%3N'").trim();
        androidProcessUtil.startApplication(
            serial, VERIFIER_PACKAGE, MAIN_ACTIVITY, /* extras= */ null, /* clearTop= */ true);
        androidProcessUtil.startApplication(serial, VERIFIER_PACKAGE, HOST_TESTS_ACTIVITY);
        // Wait for the HostTestsActivity to be ready.
        var unused =
            adb.run(
                serial,
                new String[] {
                  "logcat", "-T", currentTime, HOST_TESTS_ACTIVITY.substring(1) + ":I", "*:S"
                },
                Duration.ofSeconds(60),
                new ScanSignalOutputCallback(
                    "Registered broadcast receivers", /* stopOnSignal= */ true));
      } catch (MobileHarnessException e) {
        logger.atInfo().withCause(e).log("Unable to start HostTestsActivity on %s", serial);
      }
    }
  }

  private void broadcastResults(Collection<String> serials, Result result)
      throws InterruptedException {
    Gson gson = new Gson();
    for (Module module : result.getModuleInfoList()) {
      ImmutableMap.Builder<String, VerifierResult> testCases = new ImmutableMap.Builder<>();
      for (TestCase testCase : module.getTestCaseList()) {
        ImmutableMap.Builder<String, VerifierResult> tests = new ImmutableMap.Builder<>();
        for (Test test : testCase.getTestList()) {
          if (test.getResult().isEmpty()) {
            continue;
          }
          tests.put(
              test.getName(),
              VerifierResult.of(
                  getTestResult(test),
                  test.hasFailure() ? getShortDetails(test.getFailure().getMsg()) : "",
                  ImmutableMap.of()));
        }
        testCases.put(testCase.getName(), VerifierResult.of("", "", tests.buildOrThrow()));
      }
      ImmutableMap<String, VerifierResult> modules =
          ImmutableMap.of(
              module.getName(),
              VerifierResult.of(
                  getModuleResult(module),
                  module.hasReason() ? getShortDetails(module.getReason().getMsg()) : "",
                  testCases.buildOrThrow()));
      broadcastResult(serials, gson.toJson(modules));
    }
  }

  private void pushReportLogFiles(Collection<String> serials, Path resultDir)
      throws InterruptedException {
    ImmutableList.Builder<Path> reportLogFilesBuilder = ImmutableList.builder();
    try {
      List<Path> reportLogs =
          localFileUtil.listFilesOrDirs(
              resultDir.toAbsolutePath(),
              (file) -> REPORT_LOGS.contains(file.getFileName().toString()));
      for (Path reportLog : reportLogs) {
        if (reportLog.toFile().isFile()) {
          reportLogFilesBuilder.add(reportLog);
          continue;
        }
        // Only push single-level nested files
        reportLogFilesBuilder.addAll(
            localFileUtil.listFilePaths(reportLog, /* recursively= */ false));
      }
    } catch (MobileHarnessException e) {
      logger.atInfo().withCause(e).log("Fail to get report logs in %s", resultDir);
    }
    ImmutableList<Path> reportLogFiles = reportLogFilesBuilder.build();
    logger.atInfo().log("Collect report log files: %s", reportLogFiles);
    for (String serial : serials) {
      pushFiles(serial, resultDir, reportLogFiles);
    }
  }

  /**
   * Installs the CTS Verifier APK if the version name (e.g. 16_r1) is different from the one on the
   * device or the APK is not installed.
   */
  private void installApkIfVersionNameMismatch(String serial, String apkPath)
      throws InterruptedException, MobileHarnessException {
    String originalVersionName = "not installed";
    int sdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(serial);
    try {
      // Check if the apk is installed for current user. The installed apk may be not available to
      // current user and need re-installation.
      if (!androidPackageManagerUtil
          .listPackages(
              UtilArgs.builder()
                  .setSdkVersion(sdkVersion)
                  .setSerial(serial)
                  .setUserId(Integer.toString(getUserId(serial)))
                  .build(),
              StatusFilter.NONE,
              VERIFIER_PACKAGE)
          .isEmpty()) {
        // Check the version name for the installed apk
        originalVersionName = androidPackageManagerUtil.getAppVersionName(serial, VERIFIER_PACKAGE);
      }
    } catch (MobileHarnessException e) {
      logger.atInfo().withCause(e).log("Unable to find CTS Verifier on device %s.", serial);
    }
    String newVersionName = androidPackageManagerUtil.getApkVersionName(apkPath);
    if (!Objects.equals(newVersionName, originalVersionName)) {
      logger.atInfo().log(
          "Install CTS Verifier %s on %s. [Previous version: %s]",
          newVersionName, serial, originalVersionName);
      // Allow access to non-SDK interfaces.
      var unused = adb.runShellWithRetry(serial, "settings put global hidden_api_policy 1");
      androidPackageManagerUtil.installApk(serial, sdkVersion, apkPath);
    }
  }

  /**
   * Sets up the CTS Verifier app. There is no guarantee that all the permissions are granted
   * successfully. https://source.android.com/docs/compatibility/cts/verifier#setup
   */
  private void setUpCtsVerifier(String serial) throws InterruptedException {
    for (String command : CTS_VERIFIER_SETTING_SHELL_COMMANDS) {
      try {
        var unused = adb.runShellWithRetry(serial, command);
      } catch (MobileHarnessException e) {
        logger.atInfo().withCause(e).log(
            "Unable to give permissions to CTS Verifier on device %s. ADB shell command [%s]",
            serial, command);
      }
    }
  }

  private String getShortDetails(String details) {
    if (details.length() <= MAX_DETAIL_LENGTH) {
      return details;
    }
    return details.substring(0, MAX_DETAIL_LENGTH) + "...";
  }

  private String getModuleResult(Module module) {
    return module.getDone() && module.getFailedTests() == 0
        ? BROADCAST_TEST_RESULT_PASS
        : BROADCAST_TEST_RESULT_FAIL;
  }

  private String getTestResult(Test test) {
    // IGNORE and ASSUMPTION_FAILURE are regarded as pass
    return test.getResult().equals(FAIL_RESULT)
        ? BROADCAST_TEST_RESULT_FAIL
        : BROADCAST_TEST_RESULT_PASS;
  }

  private void broadcastResult(Collection<String> serials, String msg) throws InterruptedException {
    logger.atInfo().log("Broadcast result size: %d", msg.length());
    for (String serial : serials) {
      try {
        var unused = adb.runShell(serial, BROADCAST_COMMAND + String.format("'%s'", msg));
      } catch (MobileHarnessException e) {
        logger.atInfo().withCause(e).log("Unable to broadcast results to %s", serial);
      }
    }
  }

  private void pushFiles(String serial, Path basePath, List<Path> files)
      throws InterruptedException {
    int user = getUserId(serial);
    for (Path path : files) {
      try {
        String shellCommand =
            String.format(
                "%s -s %s shell content write --user %d --uri"
                    + " content://com.android.cts.verifier.testresultsprovider/logs/%s < %s",
                adb.getAdbPath(), serial, user, basePath.relativize(path), path);
        // Use bash to handle the file redirection.
        var unused = commandExecutor.run(Command.of("bash", "-c", shellCommand));
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to push report log %s to device %s", path, serial);
      }
    }
  }

  private int getUserId(String serial) throws InterruptedException {
    int user = 0;
    try {
      int sdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(serial);
      user = androidUserUtil.getCurrentUser(serial, sdkVersion);
    } catch (MobileHarnessException e) {
      logger.atInfo().withCause(e).log(
          "Unable to get user on device %s. Use the default user 0", serial);
    }
    return user;
  }

  /** Represents a CTS-V result. */
  @AutoValue
  public abstract static class VerifierResult {
    public abstract String result();

    public abstract String details();

    public abstract ImmutableMap<String, VerifierResult> subtests();

    public static VerifierResult of(
        String result, String details, ImmutableMap<String, VerifierResult> subtests) {
      return new AutoValue_VerifierResultHelper_VerifierResult(result, details, subtests);
    }
  }
}
