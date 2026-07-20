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
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.util.StepSkippableLifecycleDecoratorUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.DeviceInfoCollectorDecoratorSpec;
import java.io.File;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class DeviceInfoCollectorDecoratorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Driver decorated;
  @Mock private Device device;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private AndroidFileUtil androidFileUtil;
  @Mock private AndroidAdbUtil androidAdbUtil;
  @Mock private ApkInstaller apkInstaller;
  @Mock private AndroidSystemSettingUtil androidSystemSettingUtil;
  @Mock private AndroidInstrumentationUtil androidInstrumentationUtil;
  @Mock private CountDownTimer countDownTimer;
  @Captor private ArgumentCaptor<Supplier<LineCallback>> callbackCaptor;

  private DeviceInfoCollectorDecorator decorator;
  private DeviceInfoCollectorDecoratorSpec.Builder specBuilder;
  private Properties testProperties;

  private static final String DEVICE_ID = "device_id";
  private static final String APK_NAME = "AtsDeviceInfo.apk";
  private static final String PACKAGE_NAME = "com.android.compatibility.common.deviceinfo";
  private static final String XTS_TEST_DIR = "/path/to/xts";
  private static final String APK_PATH = "/path/to/xts/AtsDeviceInfo.apk";
  private static final String RUNNER_NAME = "androidx.test.runner.AndroidJUnitRunner";
  private static final String SRC_DIR = "/sdcard/device-info-files/";
  private static final String DEST_DIR = "device-info-files";
  private static final String HOST_DEST_DIR = "/genfiles/device-info-files";

  @Before
  public void setUp() throws Exception {
    when(decorated.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(jobInfo.properties()).thenReturn(new Properties(new Timing()));
    testProperties = new Properties(new Timing());
    when(testInfo.properties()).thenReturn(testProperties);
    when(testInfo.log()).thenReturn(new Log(new Timing()));
    when(testInfo.timer()).thenReturn(countDownTimer);
    when(countDownTimer.remainingTimeJava()).thenReturn(Duration.ofMinutes(5));
    when(testInfo.getGenFileDir()).thenReturn("/genfiles");

    specBuilder =
        DeviceInfoCollectorDecoratorSpec.newBuilder()
            .setApk(APK_NAME)
            .setPackageName(PACKAGE_NAME)
            .setXtsTestDir(XTS_TEST_DIR)
            .setSrcDir(SRC_DIR)
            .setDestDir(DEST_DIR);

    when(jobInfo.combinedSpec(any(DeviceInfoCollectorDecorator.class), eq(DEVICE_ID)))
        .thenAnswer(invocation -> specBuilder.build());

    decorator =
        new DeviceInfoCollectorDecorator(
            decorated,
            testInfo,
            androidFileUtil,
            localFileUtil,
            androidAdbUtil,
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
    when(androidAdbUtil.getProperty(eq(DEVICE_ID), ArgumentMatchers.<ImmutableList<String>>any()))
        .thenReturn("prop_value");
    when(androidFileUtil.isFileOrDirExisted(DEVICE_ID, SRC_DIR)).thenReturn(true);
    when(androidFileUtil.pull(DEVICE_ID, SRC_DIR, HOST_DEST_DIR)).thenReturn("pulled files");

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

    decorator.skippableSetUp(SetupContext.create(testInfo));

    // Verify properties collected
    assertThat(testProperties.get("cts:build_abi")).isEqualTo("prop_value");
    assertThat(testProperties.get("cts:build_brand")).isEqualTo("prop_value");
    assertThat(testProperties.get("device_info_collected")).isEqualTo("true");

    // Verify APK installation
    verify(apkInstaller).uninstallApk(eq(device), eq(PACKAGE_NAME), eq(false), any());
    ArgumentCaptor<ApkInstallArgs> installArgsCaptor =
        ArgumentCaptor.forClass(ApkInstallArgs.class);
    verify(apkInstaller).installApk(eq(device), installArgsCaptor.capture(), any());
    assertThat(installArgsCaptor.getValue().apkPaths()).containsExactly(APK_PATH);

    // Verify instrumentation
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
                    /* noIsolatedStorage= */ true,
                    /* useTestStorageService= */ false,
                    /* enableCoverage= */ false)),
            any(Duration.class),
            any());

    // Verify file pulling
    verify(localFileUtil).prepareDir(new File(HOST_DEST_DIR).getParent());
    verify(androidFileUtil).pull(DEVICE_ID, SRC_DIR, HOST_DEST_DIR);
  }

  @Test
  public void skippableSetUp_missingApk_throwsException() {
    specBuilder.clearApk();
    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> decorator.skippableSetUp(SetupContext.create(testInfo)));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_DEVICE_INFO_COLLECTOR_DECORATOR_INVALID_PARAMETER);
  }

  @Test
  public void skippableTearDown_success() throws Exception {
    // Set state to simulate installed
    StepSkippableLifecycleDecoratorUtil.setState(
        jobInfo, DEVICE_ID, DeviceInfoCollectorDecorator.class.getName(), "installed", "true");

    decorator.skippableTearDown(TeardownContext.create(testInfo, null, null));

    verify(apkInstaller).uninstallApk(eq(device), eq(PACKAGE_NAME), eq(false), any());

    String state =
        StepSkippableLifecycleDecoratorUtil.getState(
                jobInfo, DEVICE_ID, DeviceInfoCollectorDecorator.class.getName(), "installed")
            .orElse("");
    assertThat(state).isEqualTo("false");
  }
}
