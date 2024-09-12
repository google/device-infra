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
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.common.plan.TestPlanParser;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfoFactory;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportParser;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.SessionGenDir;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.SessionTempDir;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.common.util.MoblyTestLoader;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ModuleConfigurationHelper;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationMetadata;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Device;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
import java.io.File;
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
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class SessionRequestHandlerUtilTest {

  private static final String XTS_ROOT_DIR_PATH = "/path/to/xts_root_dir";

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
  @Bind @SessionGenDir private Path sessionGenDir;
  @Bind @SessionTempDir private Path sessionTempDir;
  @Bind @Mock private SessionInfo sessionInfo;
  @Bind @Mock private MoblyTestLoader moblyTestLoader;

  @Inject private SessionRequestHandlerUtil sessionRequestHandlerUtil;

  private TestPlanParser.TestPlanFilter testPlanFilter;

  @Before
  public void setUp() throws Exception {
    setFlags(/* enableAtsMode= */ true, /* useTfRetry= */ false);

    sessionGenDir = folder.newFolder("session_gen_dir").toPath();
    sessionTempDir = folder.newFolder("session_temp_dir").toPath();

    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    testPlanFilter = TestPlanParser.TestPlanFilter.create(ImmutableSet.of(), ImmutableSet.of());

    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());
  }

  private void setFlags(boolean enableAtsMode, boolean useTfRetry) {
    ImmutableMap<String, String> flagMap =
        ImmutableMap.of(
            "enable_ats_mode",
            String.valueOf(enableAtsMode),
            "use_tf_retry",
            String.valueOf(useTfRetry));
    Flags.parse(
        flagMap.entrySet().stream()
            .map(e -> String.format("--%s=%s", e.getKey(), e.getValue()))
            .collect(toImmutableList())
            .toArray(new String[0]));
  }

  @After
  public void tearDown() {
    Flags.resetToDefault();
  }

  private SessionRequestInfo.Builder defaultSessionRequestInfoBuilder() {
    return SessionRequestInfo.builder()
        .setTestPlan("cts")
        .setCommandLineArgs("cts")
        .setXtsType("cts")
        .setXtsRootDir(XTS_ROOT_DIR_PATH);
  }

  private Configuration.Builder defaultConfigurationBuilder() {
    return Configuration.newBuilder()
        .addDevices(Device.newBuilder().setName("AndroidDevice"))
        .setTest(
            com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto
                .Test.newBuilder()
                .setClazz("Driver"));
  }

  @Test
  public void initializeJobConfig_calculateTimeout() throws Exception {
    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setJobTimeout(Duration.ofSeconds(3000L))
                .setStartTimeout(Duration.ofSeconds(1000L))
                .build(),
            ImmutableMap.of());

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getJobTimeoutSec()).isEqualTo(3000L);
    assertThat(jobConfigOpt.get().getTestTimeoutSec()).isEqualTo(2940L);
    assertThat(jobConfigOpt.get().getStartTimeoutSec()).isEqualTo(1000L);
  }

  @Test
  public void initializeJobConfig_pickOneDevice() throws Exception {
    ImmutableMap<String, String> driverParams =
        ImmutableMap.of(
            "xts_type", "cts", "xts_root_dir", XTS_ROOT_DIR_PATH, "xts_test_plan", "cts");
    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder().build(), driverParams);

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidDevice")
                .setDimensions(
                    StringMap.newBuilder().putContent("id", "regex:(device_id_1|device_id_2)"))
                .build());

    assertThat(jobConfigOpt.get().getDriver().getName()).isEqualTo("XtsTradefedTest");
    String driverParamsStr = jobConfigOpt.get().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParamsStr, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap).containsExactlyEntriesIn(driverParams);
  }

  @Test
  public void initializeJobConfig_atsServerNoDeviceRequirement_pickOneDevice() throws Exception {
    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder().setIsAtsServerRequest(true).build(),
            ImmutableMap.of());

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidDevice")
                .setDimensions(
                    StringMap.newBuilder().putContent("id", "regex:(device_id_1|device_id_2)"))
                .build());
  }

  @Test
  public void initializeJobConfig_atsServerSpecifyOneDevice_createJobWithThatDevice()
      throws Exception {
    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setIsAtsServerRequest(true)
                .setDeviceSerials(ImmutableList.of("device_id_1"))
                .build(),
            ImmutableMap.of());

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidDevice")
                .setDimensions(StringMap.newBuilder().putContent("uuid", "device_id_1"))
                .build());
  }

  @Test
  public void initializeJobConfig_atsServerSpecifyNotAvailableDevice_createJobWithThatDevice()
      throws Exception {
    when(deviceQuerier.queryDevice(any())).thenReturn(DeviceQueryResult.getDefaultInstance());
    initializeJobConfig_atsServerSpecifyOneDevice_createJobWithThatDevice();
  }

  @Test
  public void initializeJobConfig_pickOneRealDevice() throws Exception {
    ImmutableMap<String, String> driverParams =
        ImmutableMap.of(
            "xts_type", "cts", "xts_root_dir", XTS_ROOT_DIR_PATH, "xts_test_plan", "cts");

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setDeviceType(SessionRequestHandlerUtil.ANDROID_REAL_DEVICE_TYPE)
                .build(),
            driverParams);

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidRealDevice")
                .setDimensions(
                    StringMap.newBuilder().putContent("id", "regex:(device_id_1|device_id_2)"))
                .build());

    assertThat(jobConfigOpt.get().getDriver().getName()).isEqualTo("XtsTradefedTest");
    String driverParamsStr = jobConfigOpt.get().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParamsStr, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap).containsExactlyEntriesIn(driverParams);
  }

  @Test
  public void initializeJobConfig_excludeDevice() throws Exception {
    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setExcludeDeviceSerials(ImmutableList.of("device_id_1"))
                .build(),
            ImmutableMap.of());

    // TODO: why this is the same?
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidDevice")
                .setDimensions(StringMap.newBuilder().putContent("id", "regex:(device_id_2)"))
                .build());
  }

  @Test
  public void initializeJobConfig_multiDevice_pick2Devices() throws Exception {
    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder().setTestPlan("cts-multidevice").build(),
            ImmutableMap.of());

    SubDeviceSpec subDeviceSpec =
        SubDeviceSpec.newBuilder()
            .setType("AndroidDevice")
            .setDimensions(
                StringMap.newBuilder().putContent("id", "regex:(device_id_1|device_id_2)"))
            .build();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpec, subDeviceSpec);
  }

  @Test
  public void initializeJobConfig_multiDevice_pick2Emulators() throws Exception {
    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setTestPlan("cts-multidevice")
                .setDeviceType(SessionRequestHandlerUtil.ANDROID_LOCAL_EMULATOR_TYPE)
                .build(),
            ImmutableMap.of());

    SubDeviceSpec subDeviceSpec =
        SubDeviceSpec.newBuilder()
            .setType("AndroidLocalEmulator")
            .setDimensions(
                StringMap.newBuilder().putContent("id", "regex:(device_id_1|device_id_2)"))
            .build();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpec, subDeviceSpec);
  }

  @Test
  public void initializeJobConfig_multiDevice_noEnoughDevices() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder().setTestPlan("cts-multi-device").build(),
            ImmutableMap.of());

    assertThat(jobConfigOpt).isEmpty();
  }

  @Test
  public void initializeJobConfig_shardCount2_pick2Devices() throws Exception {
    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setCommandLineArgs("cts --shard-count 2")
                .setShardCount(2)
                .build(),
            ImmutableMap.of());

    SubDeviceSpec subDeviceSpec =
        SubDeviceSpec.newBuilder()
            .setType("AndroidDevice")
            .setDimensions(
                StringMap.newBuilder().putContent("id", "regex:(device_id_1|device_id_2)"))
            .build();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpec, subDeviceSpec);
  }

  @Test
  public void initializeJobConfig_shardCount3_only2OnlineDevices_pick2Devices() throws Exception {
    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setCommandLineArgs("cts --shard-count 3")
                .setShardCount(3)
                .build(),
            ImmutableMap.of());

    SubDeviceSpec subDeviceSpec =
        SubDeviceSpec.newBuilder()
            .setType("AndroidDevice")
            .setDimensions(
                StringMap.newBuilder().putContent("id", "regex:(device_id_1|device_id_2)"))
            .build();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpec, subDeviceSpec);
  }

  @Test
  public void initializeJobConfig_noOnlineDevices_noJobConfig() throws Exception {
    when(deviceQuerier.queryDevice(any())).thenReturn(DeviceQueryResult.getDefaultInstance());

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder().build(), ImmutableMap.of());

    assertThat(jobConfigOpt).isEmpty();
  }

  @Test
  public void initializeJobConfig_withGivenSerial() throws Exception {
    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setEnvVars(ImmutableMap.of("env_key1", "env_value1"))
                .setDeviceSerials(ImmutableList.of("device_id_1"))
                .setModuleNames(ImmutableList.of("module1"))
                .setShardCount(2)
                .setExtraArgs(ImmutableList.of("--logcat-on-failure"))
                .build(),
            ImmutableMap.of());

    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidDevice")
                .setDimensions(StringMap.newBuilder().putContent("id", "device_id_1"))
                .build());
  }

  @Test
  public void initializeJobConfig_someGivenSerialsNotExist_pickExistingDevicesOnly()
      throws Exception {
    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setDeviceSerials(
                    ImmutableList.of("device_id_1", "not_exist_device", "device_id_2"))
                .build(),
            ImmutableMap.of());

    SubDeviceSpec.Builder subDeviceSpecBuilder =
        SubDeviceSpec.newBuilder().setType("AndroidDevice");
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            subDeviceSpecBuilder
                .setDimensions(StringMap.newBuilder().putContent("id", "device_id_1"))
                .build(),
            subDeviceSpecBuilder
                .setDimensions(StringMap.newBuilder().putContent("id", "device_id_2"))
                .build());
  }

  @Test
  public void initializeJobConfig_allGivenSerialsNotExist_noJobConfig() throws Exception {
    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setDeviceSerials(ImmutableList.of("device_id_4", "device_id_5"))
                .build(),
            ImmutableMap.of());

    assertThat(jobConfigOpt).isEmpty();
  }

  @Test
  public void getFilteredTradefedModules_testFilters() throws Exception {
    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(false))
            .build();
    Configuration config2 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(false))
            .build();

    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);
    when(configurationUtil.getConfigsFromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1, "/path/to/config2", config2));

    assertThat(
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setIncludeFilters(ImmutableList.of("module3 TestClass#TestCase"))
                    .build()))
        .isEmpty();
    assertThat(
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setExcludeFilters(ImmutableList.of("module1", "module2"))
                    .build()))
        .isEmpty();
    assertThat(
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setIncludeFilters(ImmutableList.of("module1"))
                    .setExcludeFilters(ImmutableList.of("module2"))
                    .build()))
        .isPresent();

    assertThat(
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setIncludeFilters(ImmutableList.of("arm64-v8a module1 TestClass#TestCase"))
                    .build()))
        .isPresent();

    assertThat(
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setExcludeFilters(ImmutableList.of("module1", "arm64-v8a module2"))
                    .build()))
        .isPresent();

    assertThat(
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setIncludeFilters(
                        ImmutableList.of("arm64-v8a module1[instant] TestClass#TestCase"))
                    .build()))
        .isPresent();
  }

  @Test
  public void createXtsNonTradefedJobs() throws Exception {
    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .build();
    Configuration config2 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .build();
    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1, "/path/to/config2", config2));
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("abilist").setValue("arm64-v8a")))
                .build());
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests(any()))
        .thenReturn(ImmutableMap.of("module1", config1, "module2", config2));

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder().build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(2);
    verify(moduleConfigurationHelper, times(2)).updateJobInfo(any(), any(), any(), any());
  }

  @Test
  public void canCreateNonTradefedJobs_noMatchedNonTradefedModules() throws Exception {
    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .build();
    Configuration config2 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .build();
    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1, "/path/to/config2", config2));
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("abilist").setValue("arm64-v8a")))
                .build());
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests(any()))
        .thenReturn(ImmutableMap.of("module1", config1, "module2", config2));

    assertThat(
            sessionRequestHandlerUtil.canCreateNonTradefedJobs(
                sessionRequestHandlerUtil.addNonTradefedModuleInfo(
                    defaultSessionRequestInfoBuilder()
                        .setModuleNames(ImmutableList.of("TfModule1"))
                        .build())))
        .isFalse();
  }

  @Test
  public void createXtsNonTradefedJobs_partialMatchedNonTradefedModules() throws Exception {
    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .build();
    Configuration config2 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .build();
    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1, "/path/to/config2", config2));
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("abilist").setValue("arm64-v8a")))
                .build());
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests(any()))
        .thenReturn(ImmutableMap.of("module1", config1, "module2", config2));

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setModuleNames(ImmutableList.of("TfModule1", "module2"))
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(1);
  }

  @Test
  public void createXtsNonTradefedJobs_testFilters() throws Exception {
    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .build();
    Configuration config2 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .build();
    Configuration config3 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module3").setIsConfigV2(true))
            .build();
    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(
            ImmutableMap.of(
                "/path/to/config1",
                config1,
                "/path/to/config2",
                config2,
                "/path/to/config3",
                config3));
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("abilist").setValue("arm64-v8a")))
                .build());
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests(any()))
        .thenReturn(ImmutableMap.of("module1", config1, "module2", config2, "module3", config3));
    when(moblyTestLoader.getTestNamesInModule(Path.of("/path/to/config3"), config3))
        .thenReturn(ImmutableList.of("test1", "test2", "test3"));
    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setIncludeFilters(
                    ImmutableList.of(
                        "module1 test_class1#test1",
                        "module1 test_class2#test2",
                        "module1 test_class2#test3",
                        "module2",
                        "module3"))
                .setExcludeFilters(ImmutableList.of("module3 test_class1#test1"))
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(3);
    assertThat(jobInfos.get(0).params().get("test_case_selector")).isEqualTo("test1 test2 test3");
    assertThat(jobInfos.get(1).params().get("test_case_selector")).isNull();
    assertThat(jobInfos.get(2).params().get("test_case_selector")).isEqualTo("test2 test3");
  }

  @Test
  public void createXtsNonTradefedJobs_withGivenTest() throws Exception {
    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .build();
    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1));
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("abilist").setValue("arm64-v8a")))
                .build());
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests(any())).thenReturn(ImmutableMap.of("module1", config1));

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setModuleNames(ImmutableList.of("module1"))
                .setTestName("testclass#test1")
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, null, ImmutableMap.of());

    SessionRequestInfo sessionRequestInfoWithInvalidTestName =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setModuleNames(ImmutableList.of("module1"))
                .setTestName("test1")
                .build());
    ImmutableList<JobInfo> jobInfosWithInvalidTestName =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfoWithInvalidTestName, testPlanFilter, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(1);
    assertThat(jobInfos.get(0).params().get("test_case_selector")).isEqualTo("test1");
    assertThat(jobInfosWithInvalidTestName).isEmpty();
  }

  @Test
  public void createXtsNonTradefedJobs_retryAtsServerSession_nonTfFailedTests() throws Exception {
    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .build();
    Configuration config2 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .build();
    Configuration config3 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module3").setIsConfigV2(true))
            .build();
    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(
            ImmutableMap.of(
                "/path/to/config1",
                config1,
                "/path/to/config2",
                config2,
                "/path/to/config3",
                config3));
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("abilist").setValue("arm64-v8a")))
                .build());
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests(any()))
        .thenReturn(
            ImmutableMap.of(
                "arm64-v8a module1",
                config1,
                "arm64-v8a module2",
                config2,
                "arm64-v8a module3",
                config3));
    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.addNonTfIncludeFilter("arm64-v8a module1 android.test.Foo#test1");
    subPlan.addNonTfIncludeFilter("arm64-v8a module1 android.test.Foo#test2");
    subPlan.addNonTfIncludeFilter("arm64-v8a module2"); // retry entire module
    doCallRealMethod()
        .when(certificationSuiteInfoFactory)
        .generateSuiteInfoMap(any(), any(), any());

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setTestPlan("retry")
                .setCommandLineArgs("retry")
                .setRetrySessionId("previous_session_id")
                .setRetryResultDir("/retry/result/dir")
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, subPlan, ImmutableMap.of());

    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).params().get("test_case_selector")).isEqualTo("test1 test2");
    assertThat(jobInfos.get(0).params().get("xts_suite_info")).contains("suite_plan=cts");
    assertThat(jobInfos.get(1).params().get("test_case_selector")).isNull();
    assertThat(jobInfos.get(1).params().get("xts_suite_info")).contains("suite_plan=cts");
  }

  @Test
  public void createXtsNonTradefedJobs_retry_nonTfFailedTests() throws Exception {
    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .build();
    Configuration config2 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .build();
    Configuration config3 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module3").setIsConfigV2(true))
            .build();
    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(
            ImmutableMap.of(
                "/path/to/config1",
                config1,
                "/path/to/config2",
                config2,
                "/path/to/config3",
                config3));
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("abilist").setValue("arm64-v8a")))
                .build());
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests(any()))
        .thenReturn(
            ImmutableMap.of(
                "arm64-v8a module1",
                config1,
                "arm64-v8a module2",
                config2,
                "arm64-v8a module3",
                config3));
    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.addNonTfIncludeFilter("arm64-v8a module1 android.test.Foo#test1");
    subPlan.addNonTfIncludeFilter("arm64-v8a module1 android.test.Foo#test2");
    subPlan.addNonTfIncludeFilter("arm64-v8a module2"); // retry entire module
    doCallRealMethod()
        .when(certificationSuiteInfoFactory)
        .generateSuiteInfoMap(any(), any(), any());

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setTestPlan("retry")
                .setCommandLineArgs("retry")
                .setRetrySessionIndex(0)
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, subPlan, ImmutableMap.of());

    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).params().get("test_case_selector")).isEqualTo("test1 test2");
    assertThat(jobInfos.get(0).params().get("xts_suite_info")).contains("suite_plan=cts");
    assertThat(jobInfos.get(1).params().get("test_case_selector")).isNull();
    assertThat(jobInfos.get(1).params().get("xts_suite_info")).contains("suite_plan=cts");
  }

  @Test
  public void createXtsNonTradefedJobs_subPlanCmd() throws Exception {
    File xtsRootDir = folder.newFolder("xts_root_dir");
    SubPlan subPlan = new SubPlan();
    subPlan.addNonTfIncludeFilter("arm64-v8a HelloWorldTest");
    subPlan.addNonTfIncludeFilter("arm64-v8a HelloWorldTest[instant]");
    subPlan.addNonTfIncludeFilter("armeabi-v7a HelloWorldTest");

    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder()
                    .setXtsModule("HelloWorldTest")
                    .setIsConfigV2(true))
            .build();
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1));
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("abilist").setValue("arm64-v8a")))
                .build());
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests(any()))
        .thenReturn(
            ImmutableMap.of(
                "arm64-v8a HelloWorldTest", config1, "arm64-v8a HelloWorldTest[instant]", config1));
    doCallRealMethod().when(localFileUtil).isFileExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).isDirExist(any(Path.class));
    when(localFileUtil.readFile(any(Path.class))).thenReturn("");

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setCommandLineArgs("cts --subplan subplan1")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setSubPlanName("subplan1")
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, subPlan, ImmutableMap.of());

    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).locator().getName()).endsWith("HelloWorldTest");
    assertThat(jobInfos.get(1).locator().getName()).endsWith("HelloWorldTest[instant]");
  }

  @Test
  public void createXtsNonTradefedJobs_subPlanCmdWithExcludeFilter() throws Exception {
    File xtsRootDir = folder.newFolder("xts_root_dir");
    SubPlan subPlan = new SubPlan();
    subPlan.addNonTfIncludeFilter("arm64-v8a HelloWorldTest");
    subPlan.addNonTfIncludeFilter("arm64-v8a HelloWorldTest[instant]");
    subPlan.addNonTfIncludeFilter("armeabi-v7a HelloWorldTest");
    subPlan.addNonTfExcludeFilter("arm64-v8a HelloWorldTest");

    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder()
                    .setXtsModule("HelloWorldTest")
                    .setIsConfigV2(true))
            .build();
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1));
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("abilist").setValue("arm64-v8a")))
                .build());
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests(any()))
        .thenReturn(
            ImmutableMap.of(
                "arm64-v8a HelloWorldTest", config1, "arm64-v8a HelloWorldTest[instant]", config1));
    doCallRealMethod().when(localFileUtil).isFileExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).isDirExist(any(Path.class));
    when(localFileUtil.readFile(any(Path.class))).thenReturn("");

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setCommandLineArgs("cts --subplan subplan3")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setSubPlanName("subplan3")
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, subPlan, ImmutableMap.of());

    assertThat(jobInfos).hasSize(1);

    // The exclude filter in subplan excluded "arm64-v8a HelloWorldTest".
    assertThat(jobInfos.get(0).locator().getName()).endsWith("HelloWorldTest[instant]");
  }

  @Test
  public void createXtsNonTradefedJobs_subPlanFilterMergeWithCommandFilter() throws Exception {
    SubPlan subPlan = new SubPlan();
    subPlan.addNonTfExcludeFilter("module1 test_class1#test1");
    subPlan.addNonTfIncludeFilter("module1");
    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .build();
    Configuration config2 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .build();
    Configuration config3 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module3").setIsConfigV2(true))
            .build();
    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(
            ImmutableMap.of(
                "/path/to/config1",
                config1,
                "/path/to/config2",
                config2,
                "/path/to/config3",
                config3));
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("abilist").setValue("arm64-v8a")))
                .build());
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests(any()))
        .thenReturn(ImmutableMap.of("module1", config1, "module2", config2, "module3", config3));
    when(moblyTestLoader.getTestNamesInModule(Path.of("/path/to/config1"), config1))
        .thenReturn(ImmutableList.of("test1", "test2", "test3"));
    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setExcludeFilters(ImmutableList.of("module1 test_class2#test2"))
                .setSubPlanName("subplan3")
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, subPlan, ImmutableMap.of());

    assertThat(jobInfos).hasSize(1);
    assertThat(jobInfos.get(0).params().get("test_case_selector")).isEqualTo("test3");
  }
}
