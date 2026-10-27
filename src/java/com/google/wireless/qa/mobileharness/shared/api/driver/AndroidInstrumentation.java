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

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.event.util.AppInstallEventUtil;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.instrumentation.PrepareTestArgsParams;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.systemsetting.SystemSettingManager;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.StepAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.TestAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.AndroidInstrumentationOptionsPreparer.PreparedInstrumentationOptions;
import com.google.wireless.qa.mobileharness.shared.api.driver.AndroidInstrumentationRunner.ExecutionParams;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidInstrumentationDriverSpec;
import com.google.wireless.qa.mobileharness.shared.api.spec.EntryDelimiterSpec;
import com.google.wireless.qa.mobileharness.shared.api.step.android.InstallApkStep;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidInstrumentationSpec;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
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

  /** {@code AndroidPackageManagerUtil} for package operations on Android devices. */
  protected final AndroidPackageManagerUtil androidPackageManagerUtil;

  /** {@code Aapt} for AAPT operations. */
  protected final Aapt aapt;

  /** {@code ApkInstaller} for common apk installation. */
  protected final ApkInstaller apkInstaller;

  private final SystemSettingManager systemSettingManager;
  private final TestMessageUtil testMessageUtil;
  private final AndroidInstrumentationUtil androidInstrumentationUtil;
  private final AndroidInstrumentationOptionsPreparer optionsPreparer;
  private final AndroidInstrumentationRunner runner;

  @Inject
  AndroidInstrumentation(
      Device device,
      TestInfo testInfo,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      Aapt aapt,
      ApkInstaller apkInstaller,
      SystemSettingManager systemSettingManager,
      TestMessageUtil testMessageUtil,
      AndroidInstrumentationUtil androidInstrumentationUtil,
      InstallApkStep installApkStep,
      AndroidInstrumentationOptionsPreparer optionsPreparer,
      AndroidInstrumentationRunner runner) {
    super(device, testInfo);
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.aapt = aapt;
    this.apkInstaller = apkInstaller;
    this.systemSettingManager = systemSettingManager;
    this.testMessageUtil = testMessageUtil;
    this.androidInstrumentationUtil = androidInstrumentationUtil;
    this.installApkStep = installApkStep;
    this.optionsPreparer = optionsPreparer;
    this.runner = runner;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    Device device = getDevice();
    String deviceId = device.getDeviceId();
    JobInfo job = testInfo.jobInfo();

    ImmutableSet<String> jobBuildApks = getBuildApks(testInfo);
    String testApk = getTestApk(testInfo);
    int deviceSdkVersion = systemSettingManager.getDeviceSdkVersion(device);

    List<String> buildPackageNames;
    String testPackageName;
    Stopwatch installStopwatch = Stopwatch.createStarted();
    try {
      buildPackageNames =
          installApkStep.installBuildApks(device, testInfo, jobBuildApks, /* spec= */ null);
      testPackageName = installTestApks(testInfo, deviceSdkVersion, testApk);
    } catch (MobileHarnessException e) {
      if (installApkStep.isInstallFailure(e, testInfo)) {
        return;
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_INSTALL_APK_ERROR, e.getMessage(), e);
    }
    recordDurationProperties(
        testInfo,
        installStopwatch.stop().elapsed(),
        PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_INSTALL_APK_TIME_MS,
        PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_INSTALL_APK_TIME_SEC);

    String runnerName =
        androidInstrumentationUtil.getTestRunnerClassName(
            testInfo, deviceId, testPackageName, testApk, /* analyzeApk= */ false);
    String externalStoragePath =
        androidInstrumentationUtil
            .cleanTestStorageOnDevice(testInfo, deviceId, deviceSdkVersion)
            .orElse(null);
    androidInstrumentationUtil.pushTestData(
        testInfo,
        deviceId,
        job.inputFiles().get(AndroidInstrumentationDriverSpec.TAG_TEST_DATA),
        externalStoragePath);
    prepareTestArgs(testInfo, externalStoragePath);

    testInfo.properties().add(AndroidInstrumentationDriverSpec.PROPERTY_PACKAGE, testPackageName);
    testInfo.properties().add(AndroidInstrumentationDriverSpec.PROPERTY_RUNNER, runnerName);

    PreparedInstrumentationOptions preparedOptions =
        optionsPreparer.prepare(testInfo, deviceId, deviceSdkVersion, externalStoragePath);

    boolean showRawResults = androidInstrumentationUtil.showRawResultsIfNeeded(testInfo);

    Stopwatch executionStopwatch = Stopwatch.createStarted();
    runner.run(
        ExecutionParams.builder()
            .setTestInfo(testInfo)
            .setDeviceId(deviceId)
            .setDeviceSdkVersion(deviceSdkVersion)
            .setTestPackageName(testPackageName)
            .setRunnerName(runnerName)
            .setBuildPackageNames(buildPackageNames)
            .setExternalStoragePath(externalStoragePath)
            .setPreparedOptions(preparedOptions)
            .setShowRawResults(showRawResults)
            .build());
    recordDurationProperties(
        testInfo,
        executionStopwatch.stop().elapsed(),
        PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_EXECUTION_TIME_MS,
        PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_EXECUTION_TIME_SEC);
  }

  protected ImmutableSet<String> getBuildApks(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    return ImmutableSet.copyOf(androidInstrumentationUtil.getBuildApks(testInfo));
  }

  protected String getTestApk(TestInfo testInfo) {
    return Iterables.getOnlyElement(
        testInfo.jobInfo().inputFiles().get(AndroidInstrumentationDriverSpec.TAG_TEST_APK));
  }

  /**
   * Installs the test apk, basic_services.apk, and test_services.apk, returning the package name of
   * the test apk.
   */
  private String installTestApks(TestInfo testInfo, int deviceSdkVersion, String testApk)
      throws MobileHarnessException, InterruptedException {
    String testPackageName = aapt.getApkPackageName(testApk);

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
      androidInstrumentationUtil.enableLegacyStorageForApk(
          getDevice().getDeviceId(), testPackageName);
    }

    if (testInfo.jobInfo().params().isTrue(AndroidInstrumentationDriverSpec.PARAM_MOCK_LOCATION)) {
      androidInstrumentationUtil.setMockLocationEnabled(
          getDevice().getDeviceId(), testPackageName, deviceSdkVersion);
    }

    return testPackageName;
  }

  private void postAppInstallStartEvent(TestInfo testInfo, String packageName) {
    postAppInstallEvent(
        testInfo,
        () -> AppInstallEventUtil.createStartMessage(getDevice().getDimensions(), packageName));
  }

  private void postAppInstallFinishEvent(TestInfo testInfo, String packageName) {
    postAppInstallEvent(
        testInfo,
        () -> AppInstallEventUtil.createFinishMessage(getDevice().getDimensions(), packageName));
  }

  private void postAppInstallEvent(
      TestInfo testInfo, Supplier<Map<String, String>> messageSupplier) {
    if (!testInfo
        .jobInfo()
        .params()
        .getBool(AndroidInstrumentationDriverSpec.PARAM_BROADCAST_INSTALL_MESSAGE, false)) {
      return;
    }
    try {
      testMessageUtil.sendMessageToTest(testInfo.locator().getId(), messageSupplier.get());
    } catch (MobileHarnessException e) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_INSTRUMENTATION_CREATE_MESSAGE_ERROR, e.getMessage()),
              logger);
    }
  }

  private void prepareTestArgs(TestInfo testInfo, @Nullable String externalStoragePath)
      throws MobileHarnessException, InterruptedException {
    androidInstrumentationUtil.prepareTestArgs(
        PrepareTestArgsParams.builder()
            .setSerial(getDevice().getDeviceId())
            .setTestArgs(androidInstrumentationUtil.getTestArgs(testInfo))
            .setDeviceExternalStoragePath(externalStoragePath)
            .setHostTmpFileDir(testInfo.getTmpFileDir())
            .setLog(testInfo.log())
            .setWarnings(testInfo.warnings())
            .setSkipClearMediaProviderForMultiUserCase(
                testInfo
                    .jobInfo()
                    .params()
                    .getBool(
                        AndroidInstrumentationDriverSpec
                            .PARAM_SKIP_CLEAR_MEDIA_PROVIDER_FOR_MULTI_USER_CASE,
                        false))
            .build());
  }

  private static void recordDurationProperties(
      TestInfo testInfo,
      Duration elapsed,
      PropertyName.Test.AndroidInstrumentation msProperty,
      PropertyName.Test.AndroidInstrumentation secProperty) {
    testInfo.properties().add(msProperty, Long.toString(elapsed.toMillis()));
    testInfo.properties().add(secProperty, Long.toString(elapsed.toSeconds()));
  }
}
