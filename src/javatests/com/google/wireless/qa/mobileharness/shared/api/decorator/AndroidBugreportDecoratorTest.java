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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Findings;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Diagnostic.Finding.Severity;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidBugreportSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidBugreportDecorator}. */
@RunWith(JUnit4.class)
public class AndroidBugreportDecoratorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Driver decoratedDriver;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Device device;
  @Mock private AndroidAdbUtil androidAdbUtil;
  @Mock private AndroidFileUtil androidFileUtil;
  @Mock private AndroidSystemSettingUtil androidSystemSettingUtil;
  @Mock private SystemStateManager systemStateManager;
  @Mock private LocalFileUtil fileUtil;
  @Mock private DeviceCache deviceCache;
  @Mock private Log log;
  @Mock private Log.Api api;
  @Mock private Params params;
  @Mock private Result result;
  @Mock private TestInfo rootTest;

  private static final String DEVICE_ID = "3635AAC1B25C00EC";

  private final Findings findings = new Findings(new Log(new Timing()));

  private AndroidBugreportDecorator androidBugreportDecorator;

  @Before
  public void setUp() throws Exception {
    // For the constructor of AndroidBugreportDecorator, called by BaseDecorator.
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.log()).thenReturn(log);
    when(log.atInfo()).thenReturn(api);
    when(api.alsoTo(any(FluentLogger.class))).thenReturn(api);
    when(testInfo.resultWithCause()).thenReturn(result);
    when(testInfo.findings()).thenReturn(findings);
    when(jobInfo.params()).thenReturn(params);
    when(jobInfo.params().getBool(anyString(), anyBoolean())).thenReturn(false);
    when(testInfo.getRootTest()).thenReturn(rootTest);
    when(rootTest.resultWithCause()).thenReturn(result);
    decoratedDriver.run(testInfo);

    androidBugreportDecorator =
        new AndroidBugreportDecorator(
            decoratedDriver,
            testInfo,
            androidAdbUtil,
            androidFileUtil,
            androidSystemSettingUtil,
            systemStateManager,
            fileUtil,
            deviceCache);
  }

  @Test
  public void dumpBugreportOnTestFailed_sdk23() throws Exception {
    when(result.get())
        .thenReturn(
            ResultTypeWithCause.create(
                TestResult.FAIL,
                new MobileHarnessException(BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_FAIL, "fake")));
    when(systemStateManager.isOnline(DEVICE_ID)).thenReturn(true);
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(23);
    String bugreport = "device bugreport 1";
    when(androidAdbUtil.bugreport(DEVICE_ID, /* bugreportFilePath= */ null)).thenReturn(bugreport);

    String publicTmpDir = "/var/www/tmp";
    when(testInfo.getGenFileDir()).thenReturn(publicTmpDir);

    androidBugreportDecorator.run(testInfo);

    verify(androidAdbUtil).bugreport(DEVICE_ID, /* bugreportFilePath= */ null);
    verify(fileUtil).writeToFile(PathUtil.join(publicTmpDir, "bugreport.txt"), bugreport);
    verify(deviceCache).cache(DEVICE_ID, device.getClass().getSimpleName(), Duration.ofMinutes(15));
    verify(deviceCache).invalidateCache(DEVICE_ID);
  }

  @Test
  public void dumpBugreportOnTestFailed_sdk24() throws Exception {
    when(result.get())
        .thenReturn(
            ResultTypeWithCause.create(
                TestResult.FAIL,
                new MobileHarnessException(BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_FAIL, "fake")));
    when(systemStateManager.isOnline(DEVICE_ID)).thenReturn(true);
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(24);

    String bugreport = "bugreport command output";
    String publicTmpDir = "/var/www/tmp";
    String bugreportFilePath = PathUtil.join(publicTmpDir, "bugreport.zip");
    when(androidAdbUtil.bugreport(DEVICE_ID, bugreportFilePath)).thenReturn(bugreport);
    when(testInfo.getGenFileDir()).thenReturn(publicTmpDir);

    androidBugreportDecorator.run(testInfo);

    verify(androidAdbUtil).bugreport(DEVICE_ID, bugreportFilePath);
    verify(deviceCache).cache(DEVICE_ID, device.getClass().getSimpleName(), Duration.ofMinutes(15));
    verify(deviceCache).invalidateCache(DEVICE_ID);
  }

  @Test
  public void dumpBugreportSkipped_onTestPassed() throws Exception {
    when(result.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, null));
    when(jobInfo.params().getBool("bugreport_on_pass", false)).thenReturn(false);

    androidBugreportDecorator.run(testInfo);

    verify(androidAdbUtil, never()).bugreport(any(), any());
  }

  @Test
  public void dumpBugreportIfRequiredOnTestPassed_sdk23() throws Exception {
    when(result.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, null));
    when(jobInfo.params().getBool("bugreport_on_pass", false)).thenReturn(true);

    when(systemStateManager.isOnline(DEVICE_ID)).thenReturn(true);
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(23);
    String bugreport = "device bugreport 1";
    when(androidAdbUtil.bugreport(DEVICE_ID, /* bugreportFilePath= */ null)).thenReturn(bugreport);

    String publicTmpDir = "/var/www/tmp";
    when(testInfo.getGenFileDir()).thenReturn(publicTmpDir);

    androidBugreportDecorator.run(testInfo);

    verify(androidAdbUtil).bugreport(DEVICE_ID, /* bugreportFilePath= */ null);
    verify(fileUtil).writeToFile(PathUtil.join(publicTmpDir, "bugreport.txt"), bugreport);
    verify(deviceCache).cache(DEVICE_ID, device.getClass().getSimpleName(), Duration.ofMinutes(15));
    verify(deviceCache).invalidateCache(DEVICE_ID);
  }

  @Test
  public void dumpBugreportIfRequiredOnTestPassed_sdk24() throws Exception {
    when(result.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, null));
    when(jobInfo.params().getBool("bugreport_on_pass", false)).thenReturn(true);

    when(systemStateManager.isOnline(DEVICE_ID)).thenReturn(true);
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(24);

    String bugreport = "bugreport command output";
    String publicTmpDir = "/var/www/tmp";
    String bugreportFilePath = PathUtil.join(publicTmpDir, "bugreport.zip");

    when(androidAdbUtil.bugreport(DEVICE_ID, bugreportFilePath)).thenReturn(bugreport);
    when(testInfo.getGenFileDir()).thenReturn(publicTmpDir);

    androidBugreportDecorator.run(testInfo);

    verify(androidAdbUtil).bugreport(DEVICE_ID, bugreportFilePath);
    verify(deviceCache).cache(DEVICE_ID, device.getClass().getSimpleName(), Duration.ofMinutes(15));
    verify(deviceCache).invalidateCache(DEVICE_ID);
  }

  @Test
  public void deviceDisconnectedAfterTest_logErrorAndSkipBugreport() throws Exception {
    when(result.get())
        .thenReturn(
            ResultTypeWithCause.create(
                TestResult.FAIL,
                new MobileHarnessException(BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_FAIL, "fake")));
    when(systemStateManager.isOnline(DEVICE_ID)).thenReturn(false);
    when(testInfo.getGenFileDir()).thenReturn("/var/www/tmp");

    androidBugreportDecorator.run(testInfo);

    verify(androidAdbUtil, never()).bugreport(any(), any());
    assertThat(findings.getAll()).hasSize(1);
    assertThat(
            getOnlyElement(
                    findings.get(AndroidErrorId.ANDROID_BUGREPORT_DECORATOR_DEVICE_NOT_FOUND))
                .getSeverity())
        .isEqualTo(Severity.WARNING);
  }

  @Test
  public void dumpBugreportError_addsWarningFinding() throws Exception {
    when(result.get())
        .thenReturn(
            ResultTypeWithCause.create(
                TestResult.FAIL,
                new MobileHarnessException(BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_FAIL, "fake")));
    when(systemStateManager.isOnline(DEVICE_ID)).thenReturn(true);
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(24);
    String bugreportFilePath = PathUtil.join("/var/www/tmp", "bugreport.zip");
    when(androidAdbUtil.bugreport(DEVICE_ID, bugreportFilePath))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "bugreport error"));
    when(testInfo.getGenFileDir()).thenReturn("/var/www/tmp");

    androidBugreportDecorator.run(testInfo);

    assertThat(findings.getAll()).hasSize(1);
    assertThat(
            getOnlyElement(
                    findings.get(AndroidErrorId.ANDROID_BUGREPORT_DECORATOR_DUMP_BUGREPORT_ERROR))
                .getSeverity())
        .isEqualTo(Severity.WARNING);
  }

  @Test
  public void resetBatteryError_addsWarningFinding() throws Exception {
    when(result.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, null));
    when(systemStateManager.isOnline(DEVICE_ID)).thenReturn(true);
    when(jobInfo.params().getBool(AndroidBugreportSpec.PARAM_NO_AUTO_RESET, false))
        .thenReturn(true);
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "reset battery error"))
        .when(androidSystemSettingUtil)
        .setBatteryStatsNoAutoReset(DEVICE_ID, /* enable= */ false);

    androidBugreportDecorator.run(testInfo);

    assertThat(findings.getAll()).hasSize(1);
    assertThat(
            getOnlyElement(
                    findings.get(AndroidErrorId.ANDROID_BUGREPORT_DECORATOR_RESET_BATTERY_ERROR))
                .getSeverity())
        .isEqualTo(Severity.WARNING);
  }

  @Test
  public void deleteBugreport() throws Exception {
    when(result.get())
        .thenReturn(
            ResultTypeWithCause.create(
                TestResult.FAIL,
                new MobileHarnessException(BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_FAIL, "fake")));
    when(systemStateManager.isOnline(DEVICE_ID)).thenReturn(true);
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(24);
    when(testInfo.getGenFileDir()).thenReturn("/var/www/tmp");

    when(jobInfo.params().getBool("delete_bugreport", true)).thenReturn(true);
    when(androidAdbUtil.bugreportDirectory(DEVICE_ID))
        .thenReturn("/data/user_de/0/com.android.shell/files/bugreports");

    androidBugreportDecorator.run(testInfo);

    verify(androidFileUtil, atLeast(1))
        .removeFiles(DEVICE_ID, "/data/user_de/0/com.android.shell/files/bugreports/*");
  }
}
