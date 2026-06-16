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

package com.google.devtools.mobileharness.infra.ats.common;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Option;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.TargetPreparer;
import com.google.protobuf.util.JsonFormat;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAdbShellDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidDeviceSettingsDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidFilePullerDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.ApkPreconditionCheckDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.DeviceInfoCollectorDecoratorSpec;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class XtsPreconditionsMapperTest {

  private SubDeviceSpec.Builder subDeviceSpec;
  private JobConfig.Builder jobConfig;
  private SessionRequestInfo sessionRequestInfo;

  @Before
  public void setUp() {
    subDeviceSpec = SubDeviceSpec.newBuilder();
    jobConfig = JobConfig.newBuilder();
    sessionRequestInfo =
        SessionRequestInfo.newBuilder().setXtsRootDir("/path/to/xts").setXtsType("cts").build();
  }

  @Test
  public void mapAndAttachDecorators_emptyPreparers_noChange() throws Exception {
    XtsPreconditionsMapper.mapAndAttachDecorators(
        subDeviceSpec, jobConfig, ImmutableList.of(), sessionRequestInfo);

    assertThat(subDeviceSpec.hasDecorators()).isFalse();
    assertThat(jobConfig.hasParams()).isFalse();
  }

  @Test
  public void mapAndAttachDecorators_settingsPreparer_screenAlwaysOn() throws Exception {
    TargetPreparer preparer =
        TargetPreparer.newBuilder()
            .setClazz("com.android.compatibility.common.tradefed.targetprep.SettingsPreparer")
            .addOptions(Option.newBuilder().setName("device-setting").setValue("screen-always-on"))
            .addOptions(Option.newBuilder().setName("set-value").setValue("true"))
            .build();

    XtsPreconditionsMapper.mapAndAttachDecorators(
        subDeviceSpec, jobConfig, ImmutableList.of(preparer), sessionRequestInfo);

    List<JobConfig.Driver> decorators = subDeviceSpec.getDecorators().getContentList();
    assertThat(decorators).hasSize(1);
    assertThat(decorators.get(0).getName()).isEqualTo("AndroidDeviceSettingsDecorator");

    AndroidDeviceSettingsDecoratorSpec.Builder spec =
        AndroidDeviceSettingsDecoratorSpec.newBuilder();
    JsonFormat.parser().merge(decorators.get(0).getParam(), spec);
    assertThat(spec.getScreenAlwaysOn()).isTrue();
  }

  @Test
  public void mapAndAttachDecorators_settingsPreparer_adbShellFallback() throws Exception {
    TargetPreparer preparer =
        TargetPreparer.newBuilder()
            .setClazz("com.android.compatibility.common.tradefed.targetprep.SettingsPreparer")
            .addOptions(Option.newBuilder().setName("device-setting").setValue("custom_setting"))
            .addOptions(Option.newBuilder().setName("setting-type").setValue("system"))
            .addOptions(Option.newBuilder().setName("set-value").setValue("100"))
            .build();

    XtsPreconditionsMapper.mapAndAttachDecorators(
        subDeviceSpec, jobConfig, ImmutableList.of(preparer), sessionRequestInfo);

    List<JobConfig.Driver> decorators = subDeviceSpec.getDecorators().getContentList();
    assertThat(decorators).hasSize(1);
    assertThat(decorators.get(0).getName()).isEqualTo("AndroidAdbShellDecorator");

    AndroidAdbShellDecoratorSpec.Builder spec = AndroidAdbShellDecoratorSpec.newBuilder();
    JsonFormat.parser().merge(decorators.get(0).getParam(), spec);
    assertThat(spec.getAdbShellBeforeTest()).isEqualTo("settings put system custom_setting 100");
  }

  @Test
  public void mapAndAttachDecorators_apkPreconditionCheck() throws Exception {
    TargetPreparer preparer =
        TargetPreparer.newBuilder()
            .setClazz("com.android.compatibility.common.tradefed.targetprep.ApkPreconditionCheck")
            .addOptions(Option.newBuilder().setName("apk").setValue("CtsPreconditions.apk"))
            .addOptions(
                Option.newBuilder().setName("package").setValue("com.android.preconditions"))
            .build();

    XtsPreconditionsMapper.mapAndAttachDecorators(
        subDeviceSpec, jobConfig, ImmutableList.of(preparer), sessionRequestInfo);

    List<JobConfig.Driver> decorators = subDeviceSpec.getDecorators().getContentList();
    assertThat(decorators).hasSize(1);
    assertThat(decorators.get(0).getName()).isEqualTo("ApkPreconditionCheckDecorator");

    ApkPreconditionCheckDecoratorSpec.Builder spec = ApkPreconditionCheckDecoratorSpec.newBuilder();
    JsonFormat.parser().merge(decorators.get(0).getParam(), spec);
    assertThat(spec.getApk()).isEqualTo("CtsPreconditions.apk");
    assertThat(spec.getPackageName()).isEqualTo("com.android.preconditions");
    assertThat(spec.getXtsTestDir()).isEqualTo("/path/to/xts/android-cts/testcases");
  }

  @Test
  public void mapAndAttachDecorators_wifiCheck_withSsid() throws Exception {
    TargetPreparer preparer =
        TargetPreparer.newBuilder()
            .setClazz("com.android.compatibility.common.tradefed.targetprep.WifiCheck")
            .addOptions(Option.newBuilder().setName("wifi-ssid").setValue("TestSsid"))
            .addOptions(Option.newBuilder().setName("wifi-psk").setValue("TestPsk"))
            .build();

    XtsPreconditionsMapper.mapAndAttachDecorators(
        subDeviceSpec, jobConfig, ImmutableList.of(preparer), sessionRequestInfo);

    List<JobConfig.Driver> decorators = subDeviceSpec.getDecorators().getContentList();
    assertThat(decorators).hasSize(1);
    assertThat(decorators.get(0).getName()).isEqualTo("AndroidSetWifiDecorator");

    assertThat(jobConfig.getParams().getContentMap()).containsEntry("wifi_ssid", "TestSsid");
    assertThat(jobConfig.getParams().getContentMap()).containsEntry("wifi_psk", "TestPsk");
  }

  @Test
  public void mapAndAttachDecorators_wifiCheck_noSsid() throws Exception {
    TargetPreparer preparer =
        TargetPreparer.newBuilder()
            .setClazz("com.android.compatibility.common.tradefed.targetprep.WifiCheck")
            .build();

    XtsPreconditionsMapper.mapAndAttachDecorators(
        subDeviceSpec, jobConfig, ImmutableList.of(preparer), sessionRequestInfo);

    List<JobConfig.Driver> decorators = subDeviceSpec.getDecorators().getContentList();
    assertThat(decorators).hasSize(1);
    assertThat(decorators.get(0).getName()).isEqualTo("AndroidSetWifiDecorator");

    assertThat(jobConfig.getParams().getContentMap()).containsEntry("wifi_ssid_optional", "true");
  }

  @Test
  public void mapAndAttachDecorators_deviceInfoCollector() throws Exception {
    TargetPreparer preparer =
        TargetPreparer.newBuilder()
            .setClazz("com.android.compatibility.common.tradefed.targetprep.DeviceInfoCollector")
            .addOptions(Option.newBuilder().setName("src-dir").setValue("/sdcard/src"))
            .addOptions(Option.newBuilder().setName("dest-dir").setValue("dest"))
            .build();

    XtsPreconditionsMapper.mapAndAttachDecorators(
        subDeviceSpec, jobConfig, ImmutableList.of(preparer), sessionRequestInfo);

    List<JobConfig.Driver> decorators = subDeviceSpec.getDecorators().getContentList();
    assertThat(decorators).hasSize(1);
    assertThat(decorators.get(0).getName()).isEqualTo("DeviceInfoCollectorDecorator");

    DeviceInfoCollectorDecoratorSpec.Builder spec = DeviceInfoCollectorDecoratorSpec.newBuilder();
    JsonFormat.parser().merge(decorators.get(0).getParam(), spec);
    assertThat(spec.getApk()).isEqualTo("CtsDeviceInfo.apk");
    assertThat(spec.getSrcDir()).isEqualTo("/sdcard/src");
    assertThat(spec.getDestDir()).isEqualTo("dest");
    assertThat(spec.getXtsTestDir()).isEqualTo("/path/to/xts/android-cts/testcases");
  }

  @Test
  public void mapAndAttachDecorators_reportIntegrityCollector() throws Exception {
    TargetPreparer preparer =
        TargetPreparer.newBuilder()
            .setClazz(
                "com.android.compatibility.common.tradefed.targetprep.ReportIntegrityCollector")
            .build();

    XtsPreconditionsMapper.mapAndAttachDecorators(
        subDeviceSpec, jobConfig, ImmutableList.of(preparer), sessionRequestInfo);

    List<JobConfig.Driver> decorators = subDeviceSpec.getDecorators().getContentList();
    assertThat(decorators).hasSize(1);
    assertThat(decorators.get(0).getName()).isEqualTo("ReportIntegrityCollectorDecorator");
  }

  @Test
  public void mapAndAttachDecorators_reportLogCollector() throws Exception {
    TargetPreparer preparer =
        TargetPreparer.newBuilder()
            .setClazz("com.android.compatibility.common.tradefed.targetprep.ReportLogCollector")
            .addOptions(
                Option.newBuilder().setName("src-dir").setValue("/sdcard/report-log-files/"))
            .build();

    XtsPreconditionsMapper.mapAndAttachDecorators(
        subDeviceSpec, jobConfig, ImmutableList.of(preparer), sessionRequestInfo);

    List<JobConfig.Driver> decorators = subDeviceSpec.getDecorators().getContentList();
    assertThat(decorators).hasSize(1);
    assertThat(decorators.get(0).getName()).isEqualTo("ReportLogCollectorDecorator");

    assertThat(jobConfig.getParams().getContentMap())
        .containsEntry("report_log_src_dir", "/sdcard/report-log-files/");
    assertThat(jobConfig.getParams().getContentMap())
        .containsEntry("report_log_device_dir", "true");
  }

  @Test
  public void mapAndAttachDecorators_deviceFileCollector() throws Exception {
    TargetPreparer preparer =
        TargetPreparer.newBuilder()
            .setClazz("com.android.compatibility.common.tradefed.targetprep.DeviceFileCollector")
            .addOptions(Option.newBuilder().setName("src-file").setValue("/sdcard/test.txt"))
            .addOptions(Option.newBuilder().setName("dest-file").setValue("test.txt"))
            .addOptions(Option.newBuilder().setName("property").setValue("ro.product.brand=Google"))
            .build();

    XtsPreconditionsMapper.mapAndAttachDecorators(
        subDeviceSpec, jobConfig, ImmutableList.of(preparer), sessionRequestInfo);

    List<JobConfig.Driver> decorators = subDeviceSpec.getDecorators().getContentList();
    assertThat(decorators).hasSize(1);
    assertThat(decorators.get(0).getName()).isEqualTo("AndroidFilePullerDecorator");

    AndroidFilePullerDecoratorSpec.Builder spec = AndroidFilePullerDecoratorSpec.newBuilder();
    JsonFormat.parser().merge(decorators.get(0).getParam(), spec);
    assertThat(spec.getFilePathOnDevice()).isEqualTo("/sdcard/test.txt");
    assertThat(spec.getPulledFileDir()).isEqualTo("test.txt");
    assertThat(spec.getPropertyMap()).containsEntry("ro.product.brand", "Google");
  }

  @Test
  public void mapAndAttachDecorators_dynamicConfigPusher() throws Exception {
    TargetPreparer preparer =
        TargetPreparer.newBuilder()
            .setClazz("com.android.compatibility.common.tradefed.targetprep.DynamicConfigPusher")
            .addOptions(Option.newBuilder().setName("config-filename").setValue("CtsTest"))
            .addOptions(
                Option.newBuilder().setName("dynamic-resource-name").setValue("CtsTestResource"))
            .addOptions(Option.newBuilder().setName("extract-from-resource").setValue("true"))
            .build();

    XtsPreconditionsMapper.mapAndAttachDecorators(
        subDeviceSpec, jobConfig, ImmutableList.of(preparer), sessionRequestInfo);

    List<JobConfig.Driver> decorators = subDeviceSpec.getDecorators().getContentList();
    assertThat(decorators).hasSize(1);
    assertThat(decorators.get(0).getName()).isEqualTo("AndroidAtsDynamicConfigPusherDecorator");

    AndroidAtsDynamicConfigPusherDecoratorSpec.Builder spec =
        AndroidAtsDynamicConfigPusherDecoratorSpec.newBuilder();
    JsonFormat.parser().merge(decorators.get(0).getParam(), spec);
    assertThat(spec.getConfigFilename()).isEqualTo("CtsTest");
    assertThat(spec.getDynamicResourceName()).isEqualTo("CtsTestResource");
    assertThat(spec.getExtractFromResource()).isTrue();
  }

  @Test
  public void mapAndAttachDecorators_runCommand() throws Exception {
    TargetPreparer preparer =
        TargetPreparer.newBuilder()
            .setClazz("com.android.tradefed.targetprep.RunCommandTargetPreparer")
            .addOptions(Option.newBuilder().setName("run-command").setValue("cmd1"))
            .addOptions(Option.newBuilder().setName("run-command").setValue("cmd2,arg"))
            .addOptions(Option.newBuilder().setName("teardown-command").setValue("clean1"))
            .build();

    XtsPreconditionsMapper.mapAndAttachDecorators(
        subDeviceSpec, jobConfig, ImmutableList.of(preparer), sessionRequestInfo);

    List<JobConfig.Driver> decorators = subDeviceSpec.getDecorators().getContentList();
    assertThat(decorators).hasSize(1);
    assertThat(decorators.get(0).getName()).isEqualTo("AndroidAdbShellDecorator");

    AndroidAdbShellDecoratorSpec.Builder spec = AndroidAdbShellDecoratorSpec.newBuilder();
    JsonFormat.parser().merge(decorators.get(0).getParam(), spec);
    assertThat(spec.getAdbShellBeforeTest()).isEqualTo("cmd1,cmd2\\,arg");
    assertThat(spec.getAdbShellAfterTest()).isEqualTo("clean1");
  }

  @Test
  public void mapAndAttachDecorators_stayAwake() throws Exception {
    TargetPreparer preparer =
        TargetPreparer.newBuilder()
            .setClazz("com.android.compatibility.common.tradefed.targetprep.StayAwakePreparer")
            .build();

    XtsPreconditionsMapper.mapAndAttachDecorators(
        subDeviceSpec, jobConfig, ImmutableList.of(preparer), sessionRequestInfo);

    List<JobConfig.Driver> decorators = subDeviceSpec.getDecorators().getContentList();
    assertThat(decorators).hasSize(1);
    assertThat(decorators.get(0).getName()).isEqualTo("AndroidDeviceSettingsDecorator");

    AndroidDeviceSettingsDecoratorSpec.Builder spec =
        AndroidDeviceSettingsDecoratorSpec.newBuilder();
    JsonFormat.parser().merge(decorators.get(0).getParam(), spec);
    assertThat(spec.getScreenAlwaysOn()).isTrue();
  }

  @Test
  public void mapAndAttachDecorators_packageDisabler() throws Exception {
    TargetPreparer preparer =
        TargetPreparer.newBuilder()
            .setClazz("com.android.compatibility.common.tradefed.targetprep.PackageDisabler")
            .addOptions(Option.newBuilder().setName("package").setValue("com.android.app1"))
            .addOptions(Option.newBuilder().setName("package").setValue("com.android.app2"))
            .build();

    XtsPreconditionsMapper.mapAndAttachDecorators(
        subDeviceSpec, jobConfig, ImmutableList.of(preparer), sessionRequestInfo);

    List<JobConfig.Driver> decorators = subDeviceSpec.getDecorators().getContentList();
    assertThat(decorators).hasSize(1);
    assertThat(decorators.get(0).getName()).isEqualTo("AndroidAdbShellDecorator");

    AndroidAdbShellDecoratorSpec.Builder spec = AndroidAdbShellDecoratorSpec.newBuilder();
    JsonFormat.parser().merge(decorators.get(0).getParam(), spec);
    assertThat(spec.getAdbShellBeforeTest())
        .isEqualTo(
            "pm disable-user --user 0 com.android.app1,pm disable-user --user 0 com.android.app2");
  }
}
