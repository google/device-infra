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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.instrumentation.parser.AmInstrumentationParser;
import com.google.devtools.mobileharness.platform.android.instrumentation.parser.AmInstrumentationResultBuilder;
import com.google.devtools.mobileharness.platform.android.instrumentation.parser.TestTimeTrackerKt;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestResult;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestStatus;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.StepSkippableLifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.ApkPreconditionCheckDecoratorSpec;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Inject;

/** Decorator to run apk precondition checks before test. */
@DecoratorAnnotation(help = "Decorator to run apk precondition checks before test.")
public class ApkPreconditionCheckDecorator extends StepSkippableLifecycleDecorator
    implements SpecConfigable<ApkPreconditionCheckDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LocalFileUtil localFileUtil;
  private final ApkInstaller apkInstaller;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final AndroidInstrumentationUtil androidInstrumentationUtil;

  @Inject
  ApkPreconditionCheckDecorator(
      Driver decorated,
      TestInfo testInfo,
      LocalFileUtil localFileUtil,
      ApkInstaller apkInstaller,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidInstrumentationUtil androidInstrumentationUtil)
      throws MobileHarnessException {
    super(decorated, testInfo);
    this.localFileUtil = localFileUtil;
    this.apkInstaller = apkInstaller;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.androidInstrumentationUtil = androidInstrumentationUtil;
  }

  @Override
  protected void skippableSetUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();
    ApkPreconditionCheckDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this, deviceId);

    String apk = spec.getApk();
    String packageName = spec.getPackageName();
    String xtsTestDir = spec.getXtsTestDir();

    // Validate parameters.
    if (apk.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_APK_PRECONDITION_CHECK_DECORATOR_INVALID_PARAMETER,
          "apk is missing from the spec.");
    }
    if (packageName.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_APK_PRECONDITION_CHECK_DECORATOR_INVALID_PARAMETER,
          "package_name is missing from the spec.");
    }
    if (xtsTestDir.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_APK_PRECONDITION_CHECK_DECORATOR_INVALID_PARAMETER,
          "xts_test_dir is missing from the spec.");
    }

    String apkPath = locateApk(xtsTestDir, apk);
    testInfo.log().atInfo().alsoTo(logger).log("Located APK: %s", apkPath);

    // Uninstall existing package if present.
    testInfo.log().atInfo().alsoTo(logger).log("Uninstalling package if present: %s", packageName);
    apkInstaller.uninstallApk(getDevice(), packageName, /* logFailures= */ false, testInfo.log());

    // Install the APK.
    testInfo.log().atInfo().alsoTo(logger).log("Installing APK: %s", apkPath);
    apkInstaller.installApk(
        getDevice(), ApkInstallArgs.builder().addApkPaths(apkPath).build(), testInfo.log());

    runPreconditionTests(testInfo, deviceId, packageName, apkPath);
  }

  private String locateApk(String xtsTestDir, String apk) throws MobileHarnessException {
    if (!localFileUtil.isDirExist(xtsTestDir)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_APK_PRECONDITION_CHECK_DECORATOR_APK_NOT_FOUND,
          "xts_test_dir does not exist: " + xtsTestDir);
    }

    File directApkFile = new File(xtsTestDir, apk);
    if (localFileUtil.isFileExist(directApkFile.getAbsolutePath())) {
      return directApkFile.getAbsolutePath();
    }

    List<String> files = localFileUtil.listFilePaths(xtsTestDir, /* recursively= */ true);
    Optional<String> locatedPath =
        files.stream().filter(f -> f.endsWith(File.separator + apk) || f.equals(apk)).findFirst();

    if (locatedPath.isPresent()) {
      return locatedPath.get();
    }

    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_APK_PRECONDITION_CHECK_DECORATOR_APK_NOT_FOUND,
        String.format("Could not find APK '%s' in '%s'", apk, xtsTestDir));
  }

  private void runPreconditionTests(
      TestInfo testInfo, String deviceId, String packageName, String apkPath)
      throws MobileHarnessException, InterruptedException {
    int deviceSdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(deviceId);

    String runnerName =
        androidInstrumentationUtil.getTestRunnerClassName(
            testInfo, deviceId, packageName, apkPath, /* analyzeApk= */ false);

    TestSuiteResult.Builder testSuiteResultBuilder = TestSuiteResult.newBuilder();
    AmInstrumentationParser amInstrumentationParser =
        new AmInstrumentationParser(
            ImmutableSet.of(new AmInstrumentationResultBuilder(testSuiteResultBuilder)),
            () -> TestTimeTrackerKt.TestTimeTracker(Instant::now));

    Supplier<LineCallback> lineCallbackFactory =
        () -> {
          testSuiteResultBuilder.clear();
          return new LineCallback() {
            @Override
            public LineCallback.Response onLine(String line) {
              amInstrumentationParser.parse(line);
              return LineCallback.Response.empty();
            }
          };
        };

    Duration timeout = testInfo.timer().remainingTimeJava();
    AndroidInstrumentationSetting setting =
        AndroidInstrumentationSetting.create(
            packageName,
            runnerName,
            /* className= */ null,
            /* otherOptions= */ null,
            /* async= */ false,
            /* showRawResults= */ true,
            /* prefixAndroidTest= */ false,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ false,
            /* enableCoverage= */ false);

    testInfo.log().atInfo().alsoTo(logger).log("Running precondition instrumentation...");
    androidInstrumentationUtil.instrument(
        deviceId, deviceSdkVersion, setting, timeout, lineCallbackFactory);
    amInstrumentationParser.done();

    if (amInstrumentationParser.getInstrumentationError() != null) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_APK_PRECONDITION_CHECK_DECORATOR_TEST_FAILURE,
          "Instrumentation failed with error: "
              + amInstrumentationParser.getInstrumentationError());
    }

    TestSuiteResult testSuiteResult = testSuiteResultBuilder.build();

    // Check individual failures first to get details.
    ImmutableList<TestResult> failedTests =
        testSuiteResult.getTestResultList().stream()
            .filter(
                r ->
                    r.getTestStatus() == TestStatus.FAILED || r.getTestStatus() == TestStatus.ERROR)
            .collect(toImmutableList());

    if (!failedTests.isEmpty()) {
      String failureDetails =
          failedTests.stream()
              .map(
                  r ->
                      String.format(
                          "%s%s.%s: %s",
                          r.getTestCase().getTestPackage().isEmpty()
                              ? ""
                              : r.getTestCase().getTestPackage() + ".",
                          r.getTestCase().getTestClass(),
                          r.getTestCase().getTestMethod(),
                          r.hasError() ? r.getError().getErrorMessage() : "unknown"))
              .collect(joining("\n"));
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_APK_PRECONDITION_CHECK_DECORATOR_TEST_FAILURE,
          "Precondition check tests failed:\n" + failureDetails);
    }

    // If no individual failures but suite status is bad, throw generic suite failure.
    if (testSuiteResult.getTestStatus() == TestStatus.FAILED
        || testSuiteResult.getTestStatus() == TestStatus.ERROR) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_APK_PRECONDITION_CHECK_DECORATOR_TEST_FAILURE,
          "Precondition check failed. Test suite status: " + testSuiteResult.getTestStatus());
    }

    testInfo.log().atInfo().alsoTo(logger).log("Precondition check tests passed.");
  }
}
