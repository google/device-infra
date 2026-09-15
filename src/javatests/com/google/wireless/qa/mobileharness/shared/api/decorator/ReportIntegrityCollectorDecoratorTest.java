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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidRemoteProvisioningUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.ReportIntegrityCollectorDecoratorSpec;
import java.time.Duration;
import java.util.Base64;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ReportIntegrityCollectorDecoratorTest {

  private static final String DEVICE_ID = "device_id";
  private static final String APK_NAME = "ReportIntegrityInfo.apk";
  private static final String PACKAGE_NAME = "com.android.compatibility.common.reportintegrityinfo";
  private static final String XTS_TEST_DIR = "/path/to/xts";
  private static final String APK_PATH = "/path/to/xts/ReportIntegrityInfo.apk";
  private static final String RUNNER_NAME = "androidx.test.runner.AndroidJUnitRunner";
  private static final String SRC_DIR = "/sdcard/device-info-files/";
  private static final String TMP_FILE_DIR = "/tmp_file_dir";
  private static final String HOST_DEST_DIR = "/tmp_file_dir/report_integrity_device_info_files";
  private static final String DEVICE_INFO_FILE =
      "/tmp_file_dir/report_integrity_device_info_files/KeystoreAttestationDeviceInfo.deviceinfo.json";

  private static final String DEVICE_INFO_JSON =
      "{"
          + "\"keymint_key_attestation\": {\"root_of_trust\": {\"verified_boot_hash\":"
          + " \"hash_keymint\"}},"
          + "\"strong_box_key_attestation\": {\"root_of_trust\": {\"verified_boot_hash\":"
          + " \"hash_strongbox\"}},"
          + "\"no_root_of_trust\": {\"other_field\": \"other_value\"},"
          + "\"not_an_object\": \"value\""
          + "}";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Driver decoratedDriver;
  @Mock private Device device;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private AndroidAdbUtil androidAdbUtil;
  @Mock private AndroidRemoteProvisioningUtil androidRemoteProvisioningUtil;
  @Mock private ApkInstaller apkInstaller;
  @Mock private AndroidFileUtil androidFileUtil;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private AndroidSystemSettingUtil androidSystemSettingUtil;
  @Mock private AndroidInstrumentationUtil androidInstrumentationUtil;
  @Mock private CountDownTimer countDownTimer;

  private Properties testProperties;
  private ReportIntegrityCollectorDecoratorSpec.Builder specBuilder;
  private ReportIntegrityCollectorDecorator decorator;

  @Before
  public void setUp() throws Exception {
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    testProperties = new Properties(new Timing());
    when(testInfo.properties()).thenReturn(testProperties);
    when(testInfo.log()).thenReturn(new Log(new Timing()));
    when(testInfo.timer()).thenReturn(countDownTimer);
    when(countDownTimer.remainingTimeJava()).thenReturn(Duration.ofMinutes(5));
    when(testInfo.getTmpFileDir()).thenReturn(TMP_FILE_DIR);

    // By default the decorator is not configured with a device info APK, so it only collects the
    // VBMeta digest and the CSR values.
    specBuilder = ReportIntegrityCollectorDecoratorSpec.newBuilder();
    when(jobInfo.combinedSpec(
            ArgumentMatchers.<SpecConfigable<ReportIntegrityCollectorDecoratorSpec>>any(),
            eq(DEVICE_ID)))
        .thenAnswer(invocation -> specBuilder.build());

    when(androidAdbUtil.getProperty(DEVICE_ID, ImmutableList.of("ro.boot.vbmeta.digest")))
        .thenReturn("vbmeta_digest_value");

    decorator =
        new ReportIntegrityCollectorDecorator(
            decoratedDriver,
            testInfo,
            androidAdbUtil,
            androidRemoteProvisioningUtil,
            apkInstaller,
            androidFileUtil,
            localFileUtil,
            androidSystemSettingUtil,
            androidInstrumentationUtil);
  }

  /** Configures the spec with the device info APK and mocks a fully successful collection. */
  private void configureApkCollection() throws Exception {
    specBuilder
        .setApk(APK_NAME)
        .setPackageName(PACKAGE_NAME)
        .setXtsTestDir(XTS_TEST_DIR)
        .setSrcDir(SRC_DIR);

    when(localFileUtil.isDirExist(XTS_TEST_DIR)).thenReturn(true);
    when(localFileUtil.isFileExist(APK_PATH)).thenReturn(true);
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(35);
    when(androidInstrumentationUtil.getTestRunnerClassName(
            eq(testInfo), eq(DEVICE_ID), eq(PACKAGE_NAME), eq(APK_PATH), anyBoolean()))
        .thenReturn(RUNNER_NAME);
    when(androidFileUtil.isFileOrDirExisted(DEVICE_ID, SRC_DIR)).thenReturn(true);
    when(androidFileUtil.pull(DEVICE_ID, SRC_DIR, HOST_DEST_DIR)).thenReturn("pulled files");
    when(localFileUtil.listFilePaths(HOST_DEST_DIR, /* recursively= */ true))
        .thenReturn(ImmutableList.of(DEVICE_INFO_FILE));
  }

  @Test
  public void setUp_collectsVbmetaAndCsrs() throws Exception {
    when(androidRemoteProvisioningUtil.getInstanceNameToCsr(DEVICE_ID))
        .thenReturn(
            ImmutableMap.of(
                "instance1", "csr1_bytes".getBytes(UTF_8),
                "instance2", "csr2_bytes".getBytes(UTF_8)));

    decorator.setUp(SetupContext.create(testInfo));

    assertThat(testProperties.get("cts:build_vb_meta_digest")).isEqualTo("vbmeta_digest_value");
    assertThat(testProperties.get("cts:csr_instance1"))
        .isEqualTo(Base64.getEncoder().encodeToString("csr1_bytes".getBytes(UTF_8)));
    assertThat(testProperties.get("cts:csr_instance2"))
        .isEqualTo(Base64.getEncoder().encodeToString("csr2_bytes".getBytes(UTF_8)));
  }

  @Test
  public void setUp_collectVbmetaThrows_failsSetup() throws Exception {
    when(androidAdbUtil.getProperty(DEVICE_ID, ImmutableList.of("ro.boot.vbmeta.digest")))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_UTIL_GET_DEVICE_PROPERTY_ERROR,
                "Failed to get property"));

    assertThrows(
        MobileHarnessException.class, () -> decorator.setUp(SetupContext.create(testInfo)));
  }

  @Test
  public void setUp_collectCsrThrows_doesNotFailSetup() throws Exception {
    when(androidRemoteProvisioningUtil.getInstanceNameToCsr(DEVICE_ID))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_DECODE_ERROR,
                "Failed to get CSR"));

    decorator.setUp(SetupContext.create(testInfo));

    assertThat(testProperties.get("cts:build_vb_meta_digest")).isEqualTo("vbmeta_digest_value");
    assertThat(testProperties.get("cts:csr_instance1")).isNull();
  }

  @Test
  public void setUp_noApkConfigured_skipsDeviceInfoApk() throws Exception {
    decorator.setUp(SetupContext.create(testInfo));

    verify(apkInstaller, never()).installApk(any(), any(), any());
    verify(androidInstrumentationUtil, never())
        .instrument(any(), anyInt(), any(AndroidInstrumentationSetting.class), any());
  }

  @Test
  public void setUp_collectsVerifiedBootHashes() throws Exception {
    configureApkCollection();
    when(localFileUtil.readFile(DEVICE_INFO_FILE)).thenReturn(DEVICE_INFO_JSON);

    decorator.setUp(SetupContext.create(testInfo));

    assertThat(testProperties.get("cts:keymint_key_attestation.root_of_trust.verified_boot_hash"))
        .isEqualTo("hash_keymint");
    assertThat(
            testProperties.get("cts:strong_box_key_attestation.root_of_trust.verified_boot_hash"))
        .isEqualTo("hash_strongbox");
    assertThat(testProperties.get("cts:no_root_of_trust.root_of_trust.verified_boot_hash"))
        .isNull();
    // The VBMeta digest is still collected.
    assertThat(testProperties.get("cts:build_vb_meta_digest")).isEqualTo("vbmeta_digest_value");

    ArgumentCaptor<ApkInstallArgs> installArgsCaptor =
        ArgumentCaptor.forClass(ApkInstallArgs.class);
    verify(apkInstaller).installApk(eq(device), installArgsCaptor.capture(), any());
    assertThat(installArgsCaptor.getValue().apkPaths()).containsExactly(APK_PATH);

    verify(androidInstrumentationUtil)
        .instrument(
            eq(DEVICE_ID),
            eq(35),
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
            any(Duration.class));

    verify(localFileUtil).prepareDir(HOST_DEST_DIR);
    verify(androidFileUtil).pull(DEVICE_ID, SRC_DIR, HOST_DEST_DIR);
    // Once before the installation and once after the collection, since this is a setup only
    // decorator without a teardown phase.
    verify(apkInstaller, times(2)).uninstallApk(eq(device), eq(PACKAGE_NAME), anyBoolean(), any());
  }

  @Test
  public void setUp_apkNotFound_doesNotFailSetup() throws Exception {
    configureApkCollection();
    when(localFileUtil.isDirExist(XTS_TEST_DIR)).thenReturn(false);

    decorator.setUp(SetupContext.create(testInfo));

    verify(apkInstaller, never()).installApk(any(), any(), any());
    assertThat(testProperties.get("cts:build_vb_meta_digest")).isEqualTo("vbmeta_digest_value");
  }

  @Test
  public void setUp_instrumentationFails_doesNotFailSetupAndUninstallsApk() throws Exception {
    configureApkCollection();
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID), eq(35), any(AndroidInstrumentationSetting.class), any(Duration.class)))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_ERROR, "instrument error"));

    decorator.setUp(SetupContext.create(testInfo));

    assertThat(testProperties.get("cts:keymint_key_attestation.root_of_trust.verified_boot_hash"))
        .isNull();
    assertThat(testProperties.get("cts:build_vb_meta_digest")).isEqualTo("vbmeta_digest_value");
    verify(apkInstaller, times(2)).uninstallApk(eq(device), eq(PACKAGE_NAME), anyBoolean(), any());
  }

  @Test
  public void setUp_malformedDeviceInfoFile_doesNotFailSetup() throws Exception {
    configureApkCollection();
    when(localFileUtil.readFile(DEVICE_INFO_FILE)).thenReturn("not a json object");

    decorator.setUp(SetupContext.create(testInfo));

    assertThat(testProperties.get("cts:keymint_key_attestation.root_of_trust.verified_boot_hash"))
        .isNull();
    assertThat(testProperties.get("cts:build_vb_meta_digest")).isEqualTo("vbmeta_digest_value");
  }

  @Test
  public void setUp_deviceInfoDirMissing_doesNotFailSetup() throws Exception {
    configureApkCollection();
    when(androidFileUtil.isFileOrDirExisted(DEVICE_ID, SRC_DIR)).thenReturn(false);

    decorator.setUp(SetupContext.create(testInfo));

    assertThat(testProperties.get("cts:keymint_key_attestation.root_of_trust.verified_boot_hash"))
        .isNull();
    verify(androidFileUtil, never()).pull(any(), any(), any());
  }
}
