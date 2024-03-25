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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.XtsType;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfoFactory;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportParser;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ModuleConfigurationHelper;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationMetadata;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Device;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class SessionRequestHandlerUtilTest {

  private static final String XTS_ROOT_DIR_PATH = "/path/to/xts_root_dir";
  private static final String ANDROID_XTS_ZIP_PATH = "/path/to/android_xts.zip";

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private LocalFileUtil localFileUtil;
  @Bind @Mock private ModuleConfigurationHelper moduleConfigurationHelper;
  @Bind @Mock private ConfigurationUtil configurationUtil;
  @Bind @Mock private CompatibilityReportMerger compatibilityReportMerger;
  @Bind @Mock private CompatibilityReportParser compatibilityReportParser;
  @Bind @Mock private CompatibilityReportCreator reportCreator;
  @Bind @Mock private CertificationSuiteInfoFactory certificationSuiteInfoFactory;
  @Mock private TestSuiteHelper testSuiteHelper;

  @Inject private SessionRequestHandlerUtil sessionRequestHandlerUtil;

  @Before
  public void setUp() {
    // Sets flags.
    ImmutableMap<String, String> flagMap = ImmutableMap.of("enable_ats_mode", "true");
    Flags.parse(
        flagMap.entrySet().stream()
            .map(e -> String.format("--%s=%s", e.getKey(), e.getValue()))
            .collect(toImmutableList())
            .toArray(new String[0]));

    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @After
  public void tearDown() {
    Flags.resetToDefault();
  }

  @Test
  public void createXtsTradefedTestJobConfig_calculateTimeout() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobConfig(
            SessionRequestHandlerUtil.SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType(XtsType.CTS)
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setJobTimeout(Duration.ofSeconds(3000))
                .setStartTimeout(Duration.ofSeconds(1000))
                .build(),
            ImmutableList.of());

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getJobTimeoutSec()).isEqualTo(3000L);
    assertThat(jobConfigOpt.get().getTestTimeoutSec()).isEqualTo(3000L);
    assertThat(jobConfigOpt.get().getStartTimeoutSec()).isEqualTo(1000L);
  }

  @Test
  public void createXtsTradefedTestJobConfig_pickOneDevice() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobConfig(
            SessionRequestHandlerUtil.SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType(XtsType.CTS)
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .build(),
            ImmutableList.of());

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(SubDeviceSpec.newBuilder().setType("AndroidRealDevice").build());

    // Asserts the driver
    assertThat(jobConfigOpt.get().getDriver().getName()).isEqualTo("XtsTradefedTest");
    String driverParams = jobConfigOpt.get().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParams, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap)
        .containsExactly(
            "xts_type", "CTS", "xts_root_dir", XTS_ROOT_DIR_PATH, "xts_test_plan", "cts");
  }

  @Test
  public void createXtsTradefedTestJobConfig_addAndroidXtsZipPathIfAvailable() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobConfig(
            SessionRequestHandlerUtil.SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType(XtsType.CTS)
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setAndroidXtsZip(ANDROID_XTS_ZIP_PATH)
                .build(),
            ImmutableList.of());

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(SubDeviceSpec.newBuilder().setType("AndroidRealDevice").build());

    // Asserts the driver
    assertThat(jobConfigOpt.get().getDriver().getName()).isEqualTo("XtsTradefedTest");
    String driverParams = jobConfigOpt.get().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParams, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap)
        .containsExactly(
            "xts_type", "CTS", "android_xts_zip", ANDROID_XTS_ZIP_PATH, "xts_test_plan", "cts");
  }

  @Test
  public void createXtsTradefedTestJobConfig_shardCount2_pick2Devices() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobConfig(
            SessionRequestHandlerUtil.SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType(XtsType.CTS)
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardCount(2)
                .build(),
            ImmutableList.of());

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder().setType("AndroidRealDevice").build(),
            SubDeviceSpec.newBuilder().setType("AndroidRealDevice").build());
  }

  @Test
  public void createXtsTradefedTestJobConfig_shardCount3_only2OnlineDevices_pick2Devices()
      throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobConfig(
            SessionRequestHandlerUtil.SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType(XtsType.CTS)
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardCount(3)
                .build(),
            ImmutableList.of());

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder().setType("AndroidRealDevice").build(),
            SubDeviceSpec.newBuilder().setType("AndroidRealDevice").build());
  }

  @Test
  public void createXtsTradefedTestJobConfig_noOnlineDevices_noJobConfig() throws Exception {
    when(deviceQuerier.queryDevice(any())).thenReturn(DeviceQueryResult.getDefaultInstance());

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobConfig(
            SessionRequestHandlerUtil.SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType(XtsType.CTS)
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .build(),
            ImmutableList.of());

    assertThat(jobConfigOpt).isEmpty();
  }

  @Test
  public void createXtsTradefedTestJobConfig_withGivenSerial() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobConfig(
            SessionRequestHandlerUtil.SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType(XtsType.CTS)
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setDeviceSerials(ImmutableList.of("device_id_1"))
                .setModuleNames(ImmutableList.of("module1"))
                .setShardCount(2)
                .setExtraArgs(ImmutableList.of("--logcat-on-failure"))
                .build(),
            ImmutableList.of("module1"));

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidRealDevice")
                .setDimensions(StringMap.newBuilder().putContent("serial", "device_id_1"))
                .build());

    // Asserts the driver
    assertThat(jobConfigOpt.get().getDriver().getName()).isEqualTo("XtsTradefedTest");
    String driverParams = jobConfigOpt.get().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParams, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap)
        .containsExactly(
            "xts_type",
            "CTS",
            "xts_root_dir",
            XTS_ROOT_DIR_PATH,
            "xts_test_plan",
            "cts",
            "run_command_args",
            "-m module1 --shard-count 2 --logcat-on-failure");
  }

  @Test
  public void createXtsTradefedTestJobConfig_someGivenSerialsNotExist_pickExistingDevicesOnly()
      throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_3").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobConfig(
            SessionRequestHandlerUtil.SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType(XtsType.CTS)
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setDeviceSerials(
                    ImmutableList.of("device_id_1", "not_exist_device", "device_id_3"))
                .build(),
            ImmutableList.of());

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidRealDevice")
                .setDimensions(StringMap.newBuilder().putContent("serial", "device_id_1"))
                .build(),
            SubDeviceSpec.newBuilder()
                .setType("AndroidRealDevice")
                .setDimensions(StringMap.newBuilder().putContent("serial", "device_id_3"))
                .build());
  }

  @Test
  public void createXtsTradefedTestJobConfig_allGivenSerialsNotExist_noJobConfig()
      throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_3").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobConfig(
            SessionRequestHandlerUtil.SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType(XtsType.CTS)
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setDeviceSerials(ImmutableList.of("device_id_4", "device_id_5"))
                .build(),
            ImmutableList.of());

    assertThat(jobConfigOpt).isEmpty();
  }

  @Test
  public void createXtsNonTradefedJobs() throws Exception {
    Configuration config1 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config2 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    when(localFileUtil.isDirExist(XTS_ROOT_DIR_PATH)).thenReturn(true);
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1, "/path/to/config2", config2));
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests())
        .thenReturn(ImmutableMap.of("module1", config1, "module2", config2));

    SessionRequestHandlerUtil.SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestHandlerUtil.SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType(XtsType.CTS)
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo);

    assertThat(jobInfos).hasSize(2);
    verify(moduleConfigurationHelper, times(2)).updateJobInfo(any(), any(), any());
  }

  @Test
  public void createXtsNonTradefedJobs_noMatchedNonTradefedModules() throws Exception {
    Configuration config1 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config2 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    when(localFileUtil.isDirExist(XTS_ROOT_DIR_PATH)).thenReturn(true);
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1, "/path/to/config2", config2));
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests())
        .thenReturn(ImmutableMap.of("module1", config1, "module2", config2));

    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestHandlerUtil.addNonTradefedModuleInfo(
                SessionRequestHandlerUtil.SessionRequestInfo.builder()
                    .setTestPlan("cts")
                    .setXtsType(XtsType.CTS)
                    .setXtsRootDir(XTS_ROOT_DIR_PATH)
                    .setModuleNames(ImmutableList.of("TfModule1"))
                    .build()));

    assertThat(jobInfos).isEmpty();
  }

  @Test
  public void createXtsNonTradefedJobs_partialMatchedNonTradefedModules() throws Exception {
    Configuration config1 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config2 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    when(localFileUtil.isDirExist(XTS_ROOT_DIR_PATH)).thenReturn(true);
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1, "/path/to/config2", config2));
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests())
        .thenReturn(ImmutableMap.of("module1", config1, "module2", config2));

    SessionRequestHandlerUtil.SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestHandlerUtil.SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType(XtsType.CTS)
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setModuleNames(ImmutableList.of("TfModule1", "module2"))
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo);

    assertThat(jobInfos).hasSize(1);
  }

  @Test
  public void getTestResultFromTest_success() throws Exception {
    TestInfo testInfo = Mockito.mock(TestInfo.class);
    Properties properties = Mockito.mock(Properties.class);
    when(properties.get(
            com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test
                .LAB_TEST_GEN_FILE_DIR))
        .thenReturn("/test_gen_file_dir");
    when(properties.has(
            com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test
                .LAB_TEST_GEN_FILE_DIR))
        .thenReturn(true);
    when(testInfo.properties()).thenReturn(properties);
    Path resultFilePath = Path.of("/test_gen_file_dir/test_file.txt");
    when(localFileUtil.listFilePaths(eq(Path.of("/test_gen_file_dir")), eq(true), any()))
        .thenReturn(ImmutableList.of(resultFilePath));
    Optional<Result> result = Optional.of(Result.getDefaultInstance());
    when(compatibilityReportParser.parse(resultFilePath)).thenReturn(result);

    assertThat(sessionRequestHandlerUtil.getTestResultFromTest(testInfo)).isEqualTo(result);
  }

  @Test
  public void getTestResultFromTest_parserFailed() throws Exception {
    TestInfo testInfo = Mockito.mock(TestInfo.class);
    Properties properties = Mockito.mock(Properties.class);
    when(properties.get(
            com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test
                .LAB_TEST_GEN_FILE_DIR))
        .thenReturn("/test_gen_file_dir");
    when(properties.has(
            com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test
                .LAB_TEST_GEN_FILE_DIR))
        .thenReturn(true);
    when(testInfo.properties()).thenReturn(properties);
    Path resultFilePath = Path.of("/test_gen_file_dir/test_file.txt");
    when(localFileUtil.listFilePaths(eq(Path.of("/test_gen_file_dir")), eq(true), any()))
        .thenReturn(ImmutableList.of(resultFilePath));
    when(compatibilityReportParser.parse(resultFilePath)).thenReturn(Optional.empty());

    assertThat(sessionRequestHandlerUtil.getTestResultFromTest(testInfo)).isEmpty();
  }

  @Test
  public void getTestResultFromTest_noTestGenFileDir() throws Exception {
    TestInfo testInfo = Mockito.mock(TestInfo.class);
    Properties properties = Mockito.mock(Properties.class);

    when(properties.has(
            com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test
                .LAB_TEST_GEN_FILE_DIR))
        .thenReturn(false);
    when(testInfo.properties()).thenReturn(properties);
    assertThat(sessionRequestHandlerUtil.getTestResultFromTest(testInfo)).isEmpty();
  }
}
