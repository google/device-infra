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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.ApkPreconditionCheckDecoratorSpec;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ApkPreconditionCheckDecoratorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Driver decorated;
  @Mock private Device device;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private ApkInstaller apkInstaller;
  @Mock private AndroidSystemSettingUtil androidSystemSettingUtil;
  @Mock private AndroidInstrumentationUtil androidInstrumentationUtil;
  @Mock private CountDownTimer countDownTimer;
  @Captor private ArgumentCaptor<Supplier<LineCallback>> callbackCaptor;

  private ApkPreconditionCheckDecorator decorator;
  private ApkPreconditionCheckDecoratorSpec.Builder specBuilder;

  private static final String DEVICE_ID = "device_id";
  private static final String APK_NAME = "CtsPreconditions.apk";
  private static final String PACKAGE_NAME = "com.android.preconditions.cts";
  private static final String XTS_TEST_DIR = "/path/to/xts";
  private static final String APK_PATH = "/path/to/xts/CtsPreconditions.apk";
  private static final String RUNNER_NAME = "androidx.test.runner.AndroidJUnitRunner";

  @Before
  public void setUp() throws Exception {
    when(decorated.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(jobInfo.properties()).thenReturn(new Properties(new Timing()));
    when(testInfo.log()).thenReturn(new Log(new Timing()));
    when(testInfo.timer()).thenReturn(countDownTimer);
    when(countDownTimer.remainingTimeJava()).thenReturn(Duration.ofMinutes(5));

    specBuilder =
        ApkPreconditionCheckDecoratorSpec.newBuilder()
            .setApk(APK_NAME)
            .setPackageName(PACKAGE_NAME)
            .setXtsTestDir(XTS_TEST_DIR);

    // We can't easily mock combinedSpec because it's a generic method and mockito struggles
    // with that sometimes, but we can try. If it fails, we might need a real JobInfo or a subclass.
    // Actually, let's try to mock it.
    when(jobInfo.combinedSpec(any(ApkPreconditionCheckDecorator.class), eq(DEVICE_ID)))
        .thenAnswer(invocation -> specBuilder.build());

    decorator =
        new ApkPreconditionCheckDecorator(
            decorated,
            testInfo,
            localFileUtil,
            apkInstaller,
            androidSystemSettingUtil,
            androidInstrumentationUtil);
  }

  @Test
  public void skippableSetUp_success() throws Exception {
    when(localFileUtil.isDirExist(XTS_TEST_DIR)).thenReturn(true);
    when(localFileUtil.isFileExist(APK_PATH)).thenReturn(true);
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(30);
    when(androidInstrumentationUtil.getTestRunnerClassName(
            eq(testInfo), eq(DEVICE_ID), eq(PACKAGE_NAME), eq(APK_PATH), anyBoolean()))
        .thenReturn(RUNNER_NAME);

    // Mock instrumentation to succeed and return some output.
    // We also need to feed the line callback to simulate parser results.
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID),
            eq(30),
            any(AndroidInstrumentationSetting.class),
            any(Duration.class),
            callbackCaptor.capture()))
        .thenAnswer(
            invocation -> {
              Supplier<LineCallback> supplier = callbackCaptor.getValue();
              LineCallback callback = supplier.get();
              // Simulate some instrumentation output that AmInstrumentationParser expects
              // We need to simulate a successful run.
              // AmInstrumentationParser parses lines like:
              // "INSTRUMENTATION_STATUS: class=com.android.preconditions.cts.SomeTest"
              // "INSTRUMENTATION_STATUS: test=testMethod"
              // "INSTRUMENTATION_STATUS_CODE: 1" (start)
              // "INSTRUMENTATION_STATUS: class=com.android.preconditions.cts.SomeTest"
              // "INSTRUMENTATION_STATUS: test=testMethod"
              // "INSTRUMENTATION_STATUS_CODE: 0" (success)
              // "INSTRUMENTATION_RESULT: stream="
              // "INSTRUMENTATION_CODE: -1" (success)
              callback.onLine(
                  "INSTRUMENTATION_STATUS: class=com.android.preconditions.cts.SomeTest");
              callback.onLine("INSTRUMENTATION_STATUS: test=testMethod");
              callback.onLine("INSTRUMENTATION_STATUS_CODE: 1");
              callback.onLine(
                  "INSTRUMENTATION_STATUS: class=com.android.preconditions.cts.SomeTest");
              callback.onLine("INSTRUMENTATION_STATUS: test=testMethod");
              callback.onLine("INSTRUMENTATION_STATUS_CODE: 0");
              callback.onLine("INSTRUMENTATION_CODE: -1");
              return "instrumentation output";
            });

    decorator.skippableSetUp(testInfo);

    verify(apkInstaller).uninstallApk(eq(device), eq(PACKAGE_NAME), eq(false), any());
    ArgumentCaptor<ApkInstallArgs> installArgsCaptor =
        ArgumentCaptor.forClass(ApkInstallArgs.class);
    verify(apkInstaller).installApk(eq(device), installArgsCaptor.capture(), any());
    assertThat(installArgsCaptor.getValue().apkPaths()).containsExactly(APK_PATH);

    verify(androidInstrumentationUtil)
        .instrument(
            eq(DEVICE_ID),
            eq(30),
            eq(
                AndroidInstrumentationSetting.create(
                    PACKAGE_NAME,
                    RUNNER_NAME,
                    /* className= */ null,
                    /* otherOptions= */ null,
                    /* async= */ false,
                    /* showRawResults= */ true,
                    /* prefixAndroidTest= */ false,
                    /* noIsolatedStorage= */ false,
                    /* useTestStorageService= */ false,
                    /* enableCoverage= */ false)),
            any(Duration.class),
            any());
  }

  @Test
  public void skippableSetUp_missingApk_throwsException() {
    specBuilder.clearApk();
    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.skippableSetUp(testInfo));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_APK_PRECONDITION_CHECK_DECORATOR_INVALID_PARAMETER);
  }

  @Test
  public void skippableSetUp_missingPackage_throwsException() {
    specBuilder.clearPackageName();
    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.skippableSetUp(testInfo));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_APK_PRECONDITION_CHECK_DECORATOR_INVALID_PARAMETER);
  }

  @Test
  public void skippableSetUp_missingXtsTestDir_throwsException() {
    specBuilder.clearXtsTestDir();
    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.skippableSetUp(testInfo));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_APK_PRECONDITION_CHECK_DECORATOR_INVALID_PARAMETER);
  }

  @Test
  public void skippableSetUp_apkNotFound_throwsException() throws Exception {
    when(localFileUtil.isDirExist(XTS_TEST_DIR)).thenReturn(true);
    when(localFileUtil.isFileExist(APK_PATH)).thenReturn(false);
    when(localFileUtil.listFilePaths(XTS_TEST_DIR, true)).thenReturn(ImmutableList.of());

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.skippableSetUp(testInfo));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_APK_PRECONDITION_CHECK_DECORATOR_APK_NOT_FOUND);
  }

  @Test
  public void skippableSetUp_apkFoundRecursively_success() throws Exception {
    when(localFileUtil.isDirExist(XTS_TEST_DIR)).thenReturn(true);
    when(localFileUtil.isFileExist(APK_PATH)).thenReturn(false);
    String deepApkPath = XTS_TEST_DIR + "/subdir/" + APK_NAME;
    when(localFileUtil.listFilePaths(XTS_TEST_DIR, true)).thenReturn(ImmutableList.of(deepApkPath));
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(30);
    when(androidInstrumentationUtil.getTestRunnerClassName(
            eq(testInfo), eq(DEVICE_ID), eq(PACKAGE_NAME), eq(deepApkPath), anyBoolean()))
        .thenReturn(RUNNER_NAME);

    // Mock instrumentation success.
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID),
            eq(30),
            any(AndroidInstrumentationSetting.class),
            any(Duration.class),
            callbackCaptor.capture()))
        .thenAnswer(
            invocation -> {
              Supplier<LineCallback> supplier = callbackCaptor.getValue();
              LineCallback callback = supplier.get();
              callback.onLine("INSTRUMENTATION_CODE: -1");
              return "instrumentation output";
            });

    decorator.skippableSetUp(testInfo);

    ArgumentCaptor<ApkInstallArgs> installArgsCaptor =
        ArgumentCaptor.forClass(ApkInstallArgs.class);
    verify(apkInstaller).installApk(eq(device), installArgsCaptor.capture(), any());
    assertThat(installArgsCaptor.getValue().apkPaths()).containsExactly(deepApkPath);
  }

  @Test
  public void skippableSetUp_instrumentationFailure_throwsException() throws Exception {
    when(localFileUtil.isDirExist(XTS_TEST_DIR)).thenReturn(true);
    when(localFileUtil.isFileExist(APK_PATH)).thenReturn(true);
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(30);
    when(androidInstrumentationUtil.getTestRunnerClassName(
            eq(testInfo), eq(DEVICE_ID), eq(PACKAGE_NAME), eq(APK_PATH), anyBoolean()))
        .thenReturn(RUNNER_NAME);

    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID),
            eq(30),
            any(AndroidInstrumentationSetting.class),
            any(Duration.class),
            callbackCaptor.capture()))
        .thenAnswer(
            invocation -> {
              Supplier<LineCallback> supplier = callbackCaptor.getValue();
              LineCallback callback = supplier.get();
              // Simulate a failed test.
              callback.onLine(
                  "INSTRUMENTATION_STATUS: class=com.android.preconditions.cts.SomeTest");
              callback.onLine("INSTRUMENTATION_STATUS: test=testMethod");
              callback.onLine("INSTRUMENTATION_STATUS_CODE: 1");
              callback.onLine(
                  "INSTRUMENTATION_STATUS: class=com.android.preconditions.cts.SomeTest");
              callback.onLine("INSTRUMENTATION_STATUS: test=testMethod");
              callback.onLine(
                  "INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: expected...");
              callback.onLine("INSTRUMENTATION_STATUS_CODE: -2"); // FAILURE
              callback.onLine("INSTRUMENTATION_CODE: -1");
              return "instrumentation output";
            });

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.skippableSetUp(testInfo));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_APK_PRECONDITION_CHECK_DECORATOR_TEST_FAILURE);
    assertThat(exception).hasMessageThat().contains("Precondition check tests failed");
    assertThat(exception)
        .hasMessageThat()
        .contains("com.android.preconditions.cts.SomeTest.testMethod");
  }

  @Test
  public void skippableSetUp_instrumentationCrash_throwsException() throws Exception {
    when(localFileUtil.isDirExist(XTS_TEST_DIR)).thenReturn(true);
    when(localFileUtil.isFileExist(APK_PATH)).thenReturn(true);
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(30);
    when(androidInstrumentationUtil.getTestRunnerClassName(
            eq(testInfo), eq(DEVICE_ID), eq(PACKAGE_NAME), eq(APK_PATH), anyBoolean()))
        .thenReturn(RUNNER_NAME);

    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID),
            eq(30),
            any(AndroidInstrumentationSetting.class),
            any(Duration.class),
            callbackCaptor.capture()))
        .thenAnswer(
            invocation -> {
              Supplier<LineCallback> supplier = callbackCaptor.getValue();
              LineCallback callback = supplier.get();
              // Simulate a crash (e.g. process crashed during run).
              // AmInstrumentationParser detects crash if we don't get INSTRUMENTATION_CODE
              // or if we get some error line.
              callback.onLine("INSTRUMENTATION_RESULT: shortMsg=Process crashed.");
              callback.onLine("INSTRUMENTATION_CODE: 0"); // usually 0 or non-zero for crash
              return "instrumentation output";
            });

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.skippableSetUp(testInfo));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_APK_PRECONDITION_CHECK_DECORATOR_TEST_FAILURE);
    assertThat(exception).hasMessageThat().contains("Instrumentation failed with error");
  }
}
