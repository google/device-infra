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
import static com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil.MOBLY_TEST_SELECTOR_KEY;
import static com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil.PARAM_XTS_SUITE_INFO;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.plan.TestPlanParser.TestPlanFilter;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfoFactory;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.SessionGenDir;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.SessionTempDir;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.common.util.MoblyTestLoader;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ModuleConfigurationHelper;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationDescriptor;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationDescriptorMetadata;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationMetadata;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Device;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
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
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
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

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder folder = new TemporaryFolder();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private LocalFileUtil localFileUtil;
  @Bind @Mock private ModuleConfigurationHelper moduleConfigurationHelper;
  @Bind @Mock private ConfigurationUtil configurationUtil;
  @Bind @Mock private CertificationSuiteInfoFactory certificationSuiteInfoFactory;
  @Mock private TestSuiteHelper testSuiteHelper;
  @Bind @SessionGenDir private Path sessionGenDir;
  @Bind @SessionTempDir private Path sessionTempDir;
  @Bind @Mock private SessionInfo sessionInfo;
  @Bind @Mock private MoblyTestLoader moblyTestLoader;

  @Inject private SessionRequestHandlerUtil sessionRequestHandlerUtil;

  private final TestPlanFilter testPlanFilter =
      TestPlanFilter.create(
          ImmutableSet.of(), ImmutableSet.of(), ImmutableMultimap.of(), ImmutableMultimap.of());

  @Before
  public void setUp() throws Exception {
    flags.setAllFlags(ImmutableMap.of("enable_ats_mode", "true", "use_tf_retry", "false"));

    sessionGenDir = folder.newFolder("session_gen_dir").toPath();
    sessionTempDir = folder.newFolder("session_temp_dir").toPath();

    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addDimension(
                            Dimension.newBuilder().setName("uuid").setValue("device_id_1"))
                        .addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_2")
                        .addDimension(
                            Dimension.newBuilder().setName("uuid").setValue("device_id_1"))
                        .addType("AndroidOnlineDevice"))
                .build());
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

  /** Returns SubDeviceSpec of "AndroidDevice" type with given dimension name-value pair. */
  private SubDeviceSpec subDeviceSpecWithDimension(String dimensionName, String dimensionValue) {
    return SubDeviceSpec.newBuilder()
        .setType("AndroidDevice")
        .setDimensions(StringMap.newBuilder().putContent(dimensionName, dimensionValue))
        .build();
  }

  @Test
  public void initializeJobConfig_calculateTimeout() throws Exception {
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setJobTimeout(Duration.ofSeconds(3000L))
                .setStartTimeout(Duration.ofSeconds(1000L))
                .build(),
            ImmutableMap.of());

    assertThat(jobConfig.getJobTimeoutSec()).isEqualTo(3000L);
    assertThat(jobConfig.getTestTimeoutSec()).isEqualTo(2940L);
    assertThat(jobConfig.getStartTimeoutSec()).isEqualTo(1000L);
  }

  @Test
  public void initializeJobConfig_pickOneDevice() throws Exception {
    ImmutableMap<String, String> driverParams =
        ImmutableMap.of(
            "xts_type", "cts", "xts_root_dir", XTS_ROOT_DIR_PATH, "xts_test_plan", "cts");
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder().build(), driverParams);

    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpecWithDimension("id", "regex:(device_id_1|device_id_2)"));

    assertThat(jobConfig.getDriver().getName()).isEqualTo("XtsTradefedTest");
    String driverParamsStr = jobConfig.getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParamsStr, new TypeToken<>() {});
    assertThat(driverParamsMap).containsExactlyEntriesIn(driverParams);
  }

  @Test
  public void initializeJobConfig_atsServerNoDeviceRequirement_pickOneDevice() throws Exception {
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder().setIsAtsServerRequest(true).build(),
            ImmutableMap.of());
    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpecWithDimension("id", "regex:(device_id_1|device_id_2)"));
  }

  @Test
  public void initializeJobConfig_atsServerSpecifyOneDevice_createJobWithThatDevice()
      throws Exception {
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setIsAtsServerRequest(true)
                .setDeviceSerials(ImmutableList.of("device_id_1"))
                .build(),
            ImmutableMap.of());
    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpecWithDimension("uuid", "device_id_1"));
  }

  @Test
  public void initializeJobConfig_atsServerSpecifyNotAvailableDevice_createJobWithLeftDevice()
      throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addDimension(
                            Dimension.newBuilder().setName("uuid").setValue("device_id_1"))
                        .addType("AndroidOnlineDevice"))
                .build());
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setIsAtsServerRequest(true)
                .setDeviceSerials(ImmutableList.of("device_id_1", "device_id_2"))
                .build(),
            ImmutableMap.of());
    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpecWithDimension("uuid", "device_id_1"));

    initializeJobConfig_atsServerSpecifyOneDevice_createJobWithThatDevice();
  }

  @Test
  public void initializeJobConfig_pickOneRealDevice() throws Exception {
    ImmutableMap<String, String> driverParams =
        ImmutableMap.of(
            "xts_type", "cts", "xts_root_dir", XTS_ROOT_DIR_PATH, "xts_test_plan", "cts");
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setDeviceType(SessionRequestHandlerUtil.ANDROID_REAL_DEVICE_TYPE)
                .build(),
            driverParams);

    // subDeviceSpecWithDimension uses "AndroidDevice" type, and cannot be used here.
    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidRealDevice")
                .setDimensions(
                    StringMap.newBuilder().putContent("id", "regex:(device_id_1|device_id_2)"))
                .build());

    assertThat(jobConfig.getDriver().getName()).isEqualTo("XtsTradefedTest");
    String driverParamsStr = jobConfig.getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParamsStr, new TypeToken<>() {});
    assertThat(driverParamsMap).containsExactlyEntriesIn(driverParams);
  }

  @Test
  public void initializeJobConfig_excludeDevice() throws Exception {
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setExcludeDeviceSerials(ImmutableList.of("device_id_1"))
                .build(),
            ImmutableMap.of());

    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpecWithDimension("id", "regex:(device_id_2)"));
  }

  @Test
  public void initializeJobConfig_multiDevice_pick2Devices() throws Exception {
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder().setTestPlan("cts-multidevice").build(),
            ImmutableMap.of());

    SubDeviceSpec subDeviceSpec =
        subDeviceSpecWithDimension("id", "regex:(device_id_1|device_id_2)");
    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpec, subDeviceSpec);
  }

  @Test
  public void initializeJobConfig_multiDevice_pick2Emulators() throws Exception {
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setTestPlan("cts-multidevice")
                .setDeviceType(SessionRequestHandlerUtil.ANDROID_LOCAL_EMULATOR_TYPE)
                .build(),
            ImmutableMap.of());

    // subDeviceSpecWithDimension uses "AndroidDevice" type, and cannot be used here.
    SubDeviceSpec subDeviceSpec =
        SubDeviceSpec.newBuilder()
            .setType("AndroidLocalEmulator")
            .setDimensions(
                StringMap.newBuilder().putContent("id", "regex:(device_id_1|device_id_2)"))
            .build();
    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
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

    assertThrows(
        MobileHarnessException.class,
        () ->
            sessionRequestHandlerUtil.initializeJobConfig(
                defaultSessionRequestInfoBuilder().setTestPlan("cts-multi-device").build(),
                ImmutableMap.of()));
  }

  @Test
  public void initializeJobConfig_shardCount2_pick2Devices() throws Exception {
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setCommandLineArgs("cts --shard-count 2")
                .setShardCount(2)
                .build(),
            ImmutableMap.of());

    SubDeviceSpec subDeviceSpec =
        subDeviceSpecWithDimension("id", "regex:(device_id_1|device_id_2)");
    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpec, subDeviceSpec);
  }

  @Test
  public void initializeJobConfig_shardCount3_only2OnlineDevices_pick2Devices() throws Exception {
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setCommandLineArgs("cts --shard-count 3")
                .setShardCount(3)
                .build(),
            ImmutableMap.of());

    SubDeviceSpec subDeviceSpec =
        subDeviceSpecWithDimension("id", "regex:(device_id_1|device_id_2)");
    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpec, subDeviceSpec);
  }

  @Test
  public void initializeJobConfig_noOnlineDevices_noJobConfig() throws Exception {
    when(deviceQuerier.queryDevice(any())).thenReturn(DeviceQueryResult.getDefaultInstance());

    assertThrows(
        MobileHarnessException.class,
        () ->
            sessionRequestHandlerUtil.initializeJobConfig(
                defaultSessionRequestInfoBuilder().build(), ImmutableMap.of()));
  }

  @Test
  public void initializeJobConfig_withGivenSerial() throws Exception {
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setEnvVars(ImmutableMap.of("env_key1", "env_value1"))
                .setDeviceSerials(ImmutableList.of("device_id_1"))
                .setModuleNames(ImmutableList.of("module1"))
                .setShardCount(2)
                .setExtraArgs(ImmutableList.of("--logcat-on-failure"))
                .build(),
            ImmutableMap.of());

    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpecWithDimension("id", "device_id_1"));
  }

  @Test
  public void initializeJobConfig_someGivenSerialsNotExist_pickExistingDevicesOnly()
      throws Exception {
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            defaultSessionRequestInfoBuilder()
                .setDeviceSerials(
                    ImmutableList.of("device_id_1", "not_exist_device", "device_id_2"))
                .build(),
            ImmutableMap.of());

    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(
            subDeviceSpecWithDimension("id", "device_id_1"),
            subDeviceSpecWithDimension("id", "device_id_2"));
  }

  @Test
  public void initializeJobConfig_allGivenSerialsNotExist_noJobConfig() {
    assertThrows(
        MobileHarnessException.class,
        () ->
            sessionRequestHandlerUtil.initializeJobConfig(
                defaultSessionRequestInfoBuilder()
                    .setDeviceSerials(ImmutableList.of("device_id_4", "device_id_5"))
                    .build(),
                ImmutableMap.of()));
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
    when(configurationUtil.getConfigsFromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1, "/path/to/config2", config2));
    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);

    assertThrows(
        MobileHarnessException.class,
        () ->
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setIncludeFilters(ImmutableList.of("module3 TestClass#TestCase"))
                    .build()));
    assertThrows(
        MobileHarnessException.class,
        () ->
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setExcludeFilters(ImmutableList.of("module1", "module2"))
                    .build()));
    assertThat(
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setIncludeFilters(ImmutableList.of("module1"))
                    .setExcludeFilters(ImmutableList.of("module2"))
                    .build()))
        .containsExactly("module1");

    assertThat(
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setIncludeFilters(ImmutableList.of("arm64-v8a module1 TestClass#TestCase"))
                    .build()))
        .containsExactly("module1");

    assertThat(
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setExcludeFilters(ImmutableList.of("module1", "arm64-v8a module2"))
                    .build()))
        .containsExactly("module2");

    assertThat(
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setIncludeFilters(
                        ImmutableList.of("arm64-v8a module1[instant] TestClass#TestCase"))
                    .build()))
        .containsExactly("module1");

    assertThat(
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setModuleNames(ImmutableList.of("module1"))
                    .build()))
        .hasSize(1);

    assertThat(
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setModuleNames(ImmutableList.of("module1", "module2", "module3"))
                    .build()))
        .hasSize(2);

    assertThrows(
        MobileHarnessException.class,
        () ->
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setModuleNames(ImmutableList.of("module3"))
                    .build()));
  }

  @Test
  public void getFilteredTradefedModules_testFilters_tfRetryWithModules() throws Exception {
    flags.setAllFlags(ImmutableMap.of("enable_ats_mode", "true", "use_tf_retry", "true"));
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
    when(configurationUtil.getConfigsFromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1, "/path/to/config2", config2));
    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);

    assertThat(
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setTestPlan("retry")
                    .setRetrySessionIndex(0)
                    .setModuleNames(ImmutableList.of("module1"))
                    .build()))
        .hasSize(1);
    assertThat(
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setTestPlan("retry")
                    .setRetrySessionIndex(0)
                    .setModuleNames(ImmutableList.of("module2[instant]"))
                    .build()))
        .hasSize(1);
    assertThrows(
        MobileHarnessException.class,
        () ->
            sessionRequestHandlerUtil.getFilteredTradefedModules(
                defaultSessionRequestInfoBuilder()
                    .setTestPlan("retry")
                    .setRetrySessionIndex(0)
                    .setModuleNames(ImmutableList.of("module"))
                    .build()));
  }

  /** Common setUp for createXtsNonTradefedJobs... tests */
  private void setUpForCreateXtsNonTradefedJobs() throws Exception {
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
        .thenReturn(ImmutableMap.of("arm64-v8a module1", config1, "arm64-v8a module2", config2));

    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);
    when(localFileUtil.isFileExist(any(Path.class))).thenReturn(true);
    when(localFileUtil.readFile(any(Path.class))).thenReturn("");
  }

  @Test
  public void createXtsNonTradefedJobs() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
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
    setUpForCreateXtsNonTradefedJobs();
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
    setUpForCreateXtsNonTradefedJobs();
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
  public void createXtsNonTradefedJobs_withDeviceSerials() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_3").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_4").addType("AndroidOnlineDevice"))
                .build());
    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setDeviceSerials(ImmutableList.of("device_id_2", "device_id_3"))
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(2);
    assertThat(getJobInfoDeviceDimensionValue(jobInfos.get(0), "id"))
        .hasValue("regex:(device_id_2|device_id_3)");
    assertThat(getJobInfoDeviceDimensionValue(jobInfos.get(1), "id"))
        .hasValue("regex:(device_id_2|device_id_3)");

    sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setExcludeDeviceSerials(ImmutableList.of("device_id_2"))
                .build());
    jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(2);
    assertThat(getJobInfoDeviceDimensionValue(jobInfos.get(0), "id"))
        .hasValue("regex:(device_id_1|device_id_3|device_id_4)");
  }

  private static Optional<String> getJobInfoDeviceDimensionValue(
      JobInfo jobInfo, String dimensionName) {
    return jobInfo
        .subDeviceSpecs()
        .getSubDevice(0)
        .deviceRequirement()
        .dimensions()
        .get(dimensionName);
  }

  /** Special setUp for some createXtsNonTradefedJobs... tests */
  private Configuration[] setUpForCreateXtsNonTradefedJobsCreate3Config() throws Exception {
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
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(
            ImmutableMap.of(
                "/path/to/config1",
                config1,
                "/path/to/config2",
                config2,
                "/path/to/config3",
                config3));

    when(testSuiteHelper.loadTests(any()))
        .thenReturn(
            ImmutableMap.of(
                "arm64-v8a module1",
                config1,
                "arm64-v8a module2",
                config2,
                "arm64-v8a module3",
                config3));

    return new Configuration[] {config1, config2, config3};
  }

  @Test
  public void createXtsNonTradefedJobs_testFilters() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    Configuration[] config = setUpForCreateXtsNonTradefedJobsCreate3Config();
    when(testSuiteHelper.loadTests(any()))
        .thenReturn(
            ImmutableMap.of("module1", config[0], "module2", config[1], "module3", config[2]));
    // Include the whole module
    when(moblyTestLoader.getTestNamesInModule(Path.of("/path/to/config1"), config[0]))
        .thenReturn(ImmutableSetMultimap.of());
    // Include 1 test class (with 1 cases) and 2 test cases, run 3 test cases
    when(moblyTestLoader.getTestNamesInModule(Path.of("/path/to/config2"), config[1]))
        .thenReturn(
            ImmutableSetMultimap.of(
                "test_class1",
                "test_class1.test1",
                "test_class2",
                "test_class2.test2",
                "test_class2",
                "test_class2.test3"));
    // Exclude 1 test class (with 1 cases) and 1 test cases, run 1 test cases
    when(moblyTestLoader.getTestNamesInModule(Path.of("/path/to/config3"), config[2]))
        .thenReturn(
            ImmutableSetMultimap.of(
                "test_class1",
                "test_class1.test1",
                "test_class2",
                "test_class2.test2",
                "test_class2",
                "test_class2.test3"));
    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setIncludeFilters(
                    ImmutableList.of(
                        "module1",
                        "module2 test_class1",
                        "module2 test_class2#test2",
                        "module2 test_class2#test3",
                        "module3"))
                .setExcludeFilters(
                    ImmutableList.of("module3 test_class1", "module3 test_class2#test2"))
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(3);
    assertThat(jobInfos.get(0).params().get(MOBLY_TEST_SELECTOR_KEY)).isNull();
    assertThat(jobInfos.get(1).params().get(MOBLY_TEST_SELECTOR_KEY).split(" "))
        .asList()
        .containsExactly("test_class1.test1", "test_class2.test2", "test_class2.test3");
    assertThat(jobInfos.get(2).params().get(MOBLY_TEST_SELECTOR_KEY))
        .isEqualTo("test_class2.test3");
  }

  @Test
  public void createXtsNonTradefedJobs_testFilters_override() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .build();
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1));
    when(testSuiteHelper.loadTests(any())).thenReturn(ImmutableMap.of("module1", config1));
    when(moblyTestLoader.getTestNamesInModule(any(), any()))
        .thenReturn(
            ImmutableSetMultimap.of(
                "test_class1",
                "test_class1.test1",
                "test_class2",
                "test_class2.test2",
                "test_class2",
                "test_class2.test3"));

    // When include filters for test cases are specified, test class filters are ignored.
    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setIncludeFilters(
                    ImmutableList.of(
                        "module1 test_class1#test1",
                        "module1 test_class2",
                        "module1 test_class2#test3"))
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, null, ImmutableMap.of());
    assertThat(jobInfos).hasSize(1);
    assertThat(jobInfos.get(0).params().get(MOBLY_TEST_SELECTOR_KEY).split(" "))
        .asList()
        .containsExactly("test_class1.test1", "test_class2.test3");

    // When exclude filters for test classes are specified, test case filters are ignored
    sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setExcludeFilters(
                    ImmutableList.of(
                        "module1 test_class1#test1",
                        "module1 test_class2",
                        "module1 test_class2#test3"))
                .build());
    jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, null, ImmutableMap.of());
    assertThat(jobInfos).isEmpty();

    // Exclude filters have higher priority than include filters
    sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setIncludeFilters(
                    ImmutableList.of("module1 test_class1#test1", "module1 test_class2#test2"))
                .setExcludeFilters(
                    ImmutableList.of("module1 test_class1#test1", "module1 test_class2"))
                .build());
    jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, null, ImmutableMap.of());
    assertThat(jobInfos).isEmpty();
  }

  @Test
  public void createXtsNonTradefedJobs_withGivenTest() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .build();
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1));
    when(testSuiteHelper.loadTests(any())).thenReturn(ImmutableMap.of("module1", config1));
    when(moblyTestLoader.getTestNamesInModule(any(), any()))
        .thenReturn(ImmutableSetMultimap.of("test_class1", "test_class1.test1"));

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setModuleNames(ImmutableList.of("module1"))
                .setTestName("test_class1#test1")
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, null, ImmutableMap.of());
    assertThat(jobInfos).hasSize(1);
    assertThat(jobInfos.get(0).params().get(MOBLY_TEST_SELECTOR_KEY))
        .isEqualTo("test_class1.test1");

    // Test invalid class name
    sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setModuleNames(ImmutableList.of("module1"))
                .setTestName("test_class2")
                .build());
    jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, null, ImmutableMap.of());
    assertThat(jobInfos).isEmpty();
  }

  @Test
  public void createXtsNonTradefedJobs_retry_nonTfFailedTests() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    Configuration[] unused = setUpForCreateXtsNonTradefedJobsCreate3Config();

    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.addNonTfIncludeFilter("arm64-v8a module1 FooTest#test1");
    subPlan.addNonTfIncludeFilter("arm64-v8a module1 FooTest#test2");
    subPlan.addNonTfIncludeFilter("arm64-v8a module2"); // retry entire module
    doCallRealMethod()
        .when(certificationSuiteInfoFactory)
        .generateSuiteInfoMap(any(), any(), any());
    doCallRealMethod().when(certificationSuiteInfoFactory).getSuiteVariant(any(), any());
    when(moblyTestLoader.getTestNamesInModule(any(), any()))
        .thenReturn(
            ImmutableSetMultimap.of("FooTest", "FooTest.test1", "FooTest", "FooTest.test2"));

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
    assertThat(jobInfos.get(0).params().get(MOBLY_TEST_SELECTOR_KEY).split(" "))
        .asList()
        .containsExactly("FooTest.test1", "FooTest.test2");
    assertThat(jobInfos.get(0).params().get(PARAM_XTS_SUITE_INFO)).contains("suite_plan=cts");
    assertThat(jobInfos.get(1).params().get(MOBLY_TEST_SELECTOR_KEY)).isNull();
    assertThat(jobInfos.get(1).params().get(PARAM_XTS_SUITE_INFO)).contains("suite_plan=cts");

    // Same result as above
    sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setTestPlan("retry")
                .setCommandLineArgs("retry")
                .setRetrySessionIndex(0)
                .build());
    jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, subPlan, ImmutableMap.of());
    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).params().get(MOBLY_TEST_SELECTOR_KEY).split(" "))
        .asList()
        .containsExactly("FooTest.test1", "FooTest.test2");
    assertThat(jobInfos.get(0).params().get(PARAM_XTS_SUITE_INFO)).contains("suite_plan=cts");
    assertThat(jobInfos.get(1).params().get(MOBLY_TEST_SELECTOR_KEY)).isNull();
    assertThat(jobInfos.get(1).params().get(PARAM_XTS_SUITE_INFO)).contains("suite_plan=cts");
  }

  @Test
  public void createXtsNonTradefedJobs_subPlanCmd() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder()
                    .setXtsModule("HelloWorldTest")
                    .setIsConfigV2(true))
            .build();
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1));
    when(testSuiteHelper.loadTests(any()))
        .thenReturn(
            ImmutableMap.of(
                "arm64-v8a HelloWorldTest", config1, "arm64-v8a HelloWorldTest[instant]", config1));

    SubPlan subPlan = new SubPlan();
    subPlan.addNonTfIncludeFilter("arm64-v8a HelloWorldTest");
    subPlan.addNonTfIncludeFilter("arm64-v8a HelloWorldTest[instant]");
    subPlan.addNonTfIncludeFilter("armeabi-v7a HelloWorldTest");

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setCommandLineArgs("cts --subplan subplan1")
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
    setUpForCreateXtsNonTradefedJobs();
    Configuration config1 =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder()
                    .setXtsModule("HelloWorldTest")
                    .setIsConfigV2(true))
            .build();
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1));
    when(testSuiteHelper.loadTests(any()))
        .thenReturn(
            ImmutableMap.of(
                "arm64-v8a HelloWorldTest", config1, "arm64-v8a HelloWorldTest[instant]", config1));

    SubPlan subPlan = new SubPlan();
    subPlan.addNonTfIncludeFilter("arm64-v8a HelloWorldTest");
    subPlan.addNonTfIncludeFilter("arm64-v8a HelloWorldTest[instant]");
    subPlan.addNonTfIncludeFilter("armeabi-v7a HelloWorldTest");
    subPlan.addNonTfExcludeFilter("arm64-v8a HelloWorldTest");

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setCommandLineArgs("cts --subplan subplan3")
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
    setUpForCreateXtsNonTradefedJobs();
    Configuration[] config = setUpForCreateXtsNonTradefedJobsCreate3Config();
    when(testSuiteHelper.loadTests(any()))
        .thenReturn(
            ImmutableMap.of("module1", config[0], "module2", config[1], "module3", config[2]));
    when(moblyTestLoader.getTestNamesInModule(Path.of("/path/to/config1"), config[0]))
        .thenReturn(
            ImmutableSetMultimap.of(
                "test_class1",
                "test_class1.test1",
                "test_class2",
                "test_class2.test2",
                "test_class3",
                "test_class3.test3"));

    SubPlan subPlan = new SubPlan();
    subPlan.addNonTfExcludeFilter("module1 test_class1#test1");
    subPlan.addNonTfIncludeFilter("module1");

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
    assertThat(jobInfos.get(0).params().get(MOBLY_TEST_SELECTOR_KEY))
        .isEqualTo("test_class3.test3");
  }

  @Test
  public void createXtsNonTradefedJobs_withModuleArgs() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    String file1 = folder.newFile().getAbsolutePath();
    String file2 = folder.newFile().getAbsolutePath();
    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setModuleArgs(
                    ImmutableList.of(
                        "module1:option:value",
                        "module1:config:file:=" + file1,
                        "arm64-v8a module1:config:file:=" + file2))
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, testPlanFilter, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).params().get("option")).isEqualTo("value");
    assertThat(jobInfos.get(1).params().get("option")).isNull();
    assertThat(jobInfos.get(0).files().get("config")).containsExactly(file1, file2);
    assertThat(jobInfos.get(1).files().get("config")).isEmpty();
  }

  @Test
  public void createXtsNonTradefedJobs_noEnoughDevices() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder().build());
    when(deviceQuerier.queryDevice(any())).thenReturn(DeviceQueryResult.getDefaultInstance());

    assertThrows(
        MobileHarnessException.class,
        () ->
            sessionRequestHandlerUtil.createXtsNonTradefedJobs(
                sessionRequestInfo, testPlanFilter, null, ImmutableMap.of()));
  }

  @Test
  public void filterModuleByConfigMetadata_success() throws Exception {
    Configuration config =
        defaultConfigurationBuilder()
            .setConfigDescriptor(
                ConfigurationDescriptor.newBuilder()
                    .putMetadata(
                        "key1",
                        ConfigurationDescriptorMetadata.newBuilder()
                            .setKey("key1")
                            .addValue("value1")
                            .addValue("value2")
                            .build())
                    .putMetadata(
                        "key2",
                        ConfigurationDescriptorMetadata.newBuilder()
                            .setKey("key2")
                            .addValue("value3")
                            .addValue("value4")
                            .build()))
            .build();

    // Include filter partially matched.
    assertThat(
            SessionRequestHandlerUtil.filterModuleByConfigMetadata(
                config,
                ImmutableMultimap.of("key1", "value1", "key1", "value2"),
                ImmutableMultimap.of("key2", "value5")))
        .isTrue();
    // Include filter fully match.
    assertThat(
            SessionRequestHandlerUtil.filterModuleByConfigMetadata(
                config,
                ImmutableMultimap.of(
                    "key1", "value1", "key1", "value2", "key2", "value3", "key2", "value4"),
                ImmutableMultimap.of("key1", "value5", "key2", "value5")))
        .isTrue();
    // Include filter unmatched.
    assertThat(
            SessionRequestHandlerUtil.filterModuleByConfigMetadata(
                config,
                ImmutableMultimap.of("key1", "value1", "key1", "value2", "key1", "value3"),
                ImmutableMultimap.of()))
        .isFalse();
    // Exclude filter partially matched.
    assertThat(
            SessionRequestHandlerUtil.filterModuleByConfigMetadata(
                config,
                ImmutableMultimap.of("key1", "value1", "key1", "value2"),
                ImmutableMultimap.of("key2", "value3")))
        .isFalse();
    // Exclude filter fully match.
    assertThat(
            SessionRequestHandlerUtil.filterModuleByConfigMetadata(
                config,
                ImmutableMultimap.of("key1", "value1", "key1", "value2"),
                ImmutableMultimap.of("key2", "value3", "key2", "value4")))
        .isFalse();
    // Exclude filter unmatched.
    assertThat(
            SessionRequestHandlerUtil.filterModuleByConfigMetadata(
                config, ImmutableMultimap.of(), ImmutableMultimap.of("key2", "value5")))
        .isTrue();
  }

  @Test
  public void needTestHarnessPropertyFalse_expected() {
    assertThat(
            SessionRequestHandlerUtil.needTestHarnessPropertyFalse(
                defaultSessionRequestInfoBuilder().setXtsType("cts").build()))
        .isTrue();
    assertThat(
            SessionRequestHandlerUtil.needTestHarnessPropertyFalse(
                defaultSessionRequestInfoBuilder().setXtsType("cts-on-gts-on-s").build()))
        .isTrue();
    assertThat(
            SessionRequestHandlerUtil.needTestHarnessPropertyFalse(
                defaultSessionRequestInfoBuilder().setXtsType("mcts").build()))
        .isTrue();
    assertThat(
            SessionRequestHandlerUtil.needTestHarnessPropertyFalse(
                defaultSessionRequestInfoBuilder().setXtsType("gts").build()))
        .isFalse();
  }
}
