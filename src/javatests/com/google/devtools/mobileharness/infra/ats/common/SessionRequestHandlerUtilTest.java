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
import static com.google.devtools.mobileharness.platform.android.xts.constant.NonTradefedReportGeneratorConstants.PARAM_XTS_SUITE_INFO;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Job.AllocationExitStrategy;
import com.google.devtools.mobileharness.infra.ats.common.plan.TestPlanParser;
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
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Option;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.wireless.qa.mobileharness.shared.api.spec.MoblyAospTestSpec;
import com.google.wireless.qa.mobileharness.shared.api.spec.MoblyTestSpec;
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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(TestParameterInjector.class)
public final class SessionRequestHandlerUtilTest {

  private static final String XTS_ROOT_DIR_PATH = "/path/to/xts_root_dir";
  private static final String TRADEFED_TEST_DRIVER_NAME = "TradefedTest";

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
  @Bind @Mock private TestPlanParser testPlanParser;
  @Bind @Mock private Sleeper sleeper;

  @Inject private SessionRequestHandlerUtil sessionRequestHandlerUtil;

  private final TestPlanFilter testPlanFilter =
      TestPlanFilter.create(
          ImmutableSet.of(),
          ImmutableSet.of(),
          ImmutableMultimap.of(),
          ImmutableMultimap.of(),
          ImmutableSet.of(
              "com.android.compatibility.common.tradefed.testtype.suite.CompatibilityTestSuite"));

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
    when(testPlanParser.parseFilters(any(), anyString(), anyString())).thenReturn(testPlanFilter);
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
        .addDevices(Device.newBuilder().setName("AndroidDevice2"))
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
  public void createXtsNonTradefedJobs_enableTokenSharding_setsSimCardTypeDimension(
      @TestParameter boolean enableTokenSharding) throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    ConfigurationDescriptor configDescriptor =
        ConfigurationDescriptor.newBuilder()
            .putMetadata(
                "token",
                ConfigurationDescriptorMetadata.newBuilder()
                    .setKey("token")
                    .addValue("SIM_CARD")
                    .build())
            .build();
    Configuration configWithToken =
        defaultConfigurationBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .setConfigDescriptor(configDescriptor)
            .build();

    when(testSuiteHelper.loadTests(any()))
        .thenReturn(ImmutableMap.of("arm64-v8a module2", configWithToken));
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config2", configWithToken));

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setEnableTokenSharding(enableTokenSharding)
                .setModuleNames(ImmutableList.of("module2"))
                .build());

    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(1);
    if (enableTokenSharding) {
      assertThat(jobInfos.get(0).subDeviceSpecs().getAllSubDevices().get(0).dimensions().getAll())
          .containsEntry("sim_card_type", "SIM_CARD");
    } else {
      assertThat(jobInfos.get(0).subDeviceSpecs().getAllSubDevices().get(0).dimensions().getAll())
          .doesNotContainKey("sim_card_type");
    }
  }

  @Test
  public void initializeJobConfig_calculateTimeout() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setJobTimeout(Duration.ofSeconds(3000L))
            .setStartTimeout(Duration.ofSeconds(1000L))
            .build();
    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            sessionRequestInfo, ImmutableMap.of(), subDeviceSpecs, ImmutableMultimap.of());

    assertThat(jobConfig.getJobTimeoutSec()).isEqualTo(3000L);
    assertThat(jobConfig.getTestTimeoutSec()).isEqualTo(2940L);
    assertThat(jobConfig.getStartTimeoutSec()).isEqualTo(1000L);
  }

  @Test
  public void initializeJobConfig_pickOneDevice() throws Exception {
    ImmutableMap<String, String> driverParams =
        ImmutableMap.of(
            "xts_type", "cts", "xts_root_dir", XTS_ROOT_DIR_PATH, "xts_test_plan", "cts");
    SessionRequestInfo sessionRequestInfo = defaultSessionRequestInfoBuilder().build();
    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            sessionRequestInfo, driverParams, subDeviceSpecs, ImmutableMultimap.of());

    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpecWithDimension("id", "regex:(device_id_1|device_id_2)"));

    assertThat(jobConfig.getDriver().getName()).isEqualTo(TRADEFED_TEST_DRIVER_NAME);
    String driverParamsStr = jobConfig.getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParamsStr, new TypeToken<>() {});
    assertThat(driverParamsMap).containsExactlyEntriesIn(driverParams);
  }

  @Test
  public void initializeJobConfig_atsServerNoDeviceRequirement_pickOneDevice() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder().setIsAtsServerRequest(true).build();
    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            sessionRequestInfo, ImmutableMap.of(), subDeviceSpecs, ImmutableMultimap.of());
    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpecWithDimension("id", "regex:(device_id_1|device_id_2)"));
  }

  @Test
  public void initializeJobConfig_atsServerSpecifyOneDevice_createJobWithThatDevice()
      throws Exception {
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setIsAtsServerRequest(true)
            .setDeviceSerials(ImmutableList.of("device_id_1"))
            .build();
    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            sessionRequestInfo, ImmutableMap.of(), subDeviceSpecs, ImmutableMultimap.of());
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
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setIsAtsServerRequest(true)
            .setAllowPartialDeviceMatch(true)
            .setDeviceSerials(ImmutableList.of("device_id_1", "device_id_2"))
            .build();
    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            sessionRequestInfo, ImmutableMap.of(), subDeviceSpecs, ImmutableMultimap.of());
    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpecWithDimension("uuid", "device_id_1"));

    initializeJobConfig_atsServerSpecifyOneDevice_createJobWithThatDevice();
  }

  @Test
  public void
      getSubDeviceSpecListForTradefed_atsServerRequestWithDeviceSerials_retryAndSucceedsOnThirdAttempt()
          throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(DeviceQueryResult.getDefaultInstance()) // First call, no devices
        .thenReturn(DeviceQueryResult.getDefaultInstance()) // Second call, no devices
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addDimension(
                            Dimension.newBuilder().setName("uuid").setValue("device_id_1"))
                        .addType("AndroidOnlineDevice"))
                .build()); // Third call, one device

    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setIsAtsServerRequest(true)
            .setDeviceSerials(ImmutableList.of("device_id_1"))
            .build();

    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);

    assertThat(subDeviceSpecs).hasSize(1);
    assertThat(subDeviceSpecs.get(0).getDimensions().getContentMap())
        .containsEntry("uuid", "device_id_1");
    verify(sleeper, times(2)).sleep(Duration.ofSeconds(30));
  }

  @Test
  public void
      getSubDeviceSpecListForTradefed_atsServerRequestWithDeviceSerials_retryUntilRequestedIsFound()
          throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(DeviceQueryResult.getDefaultInstance()) // First call, no devices
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_2")
                        .addDimension(
                            Dimension.newBuilder().setName("uuid").setValue("device_id_2"))
                        .addType("AndroidOnlineDevice"))
                .build()) // Second call, one device that is not the one requested
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
                            Dimension.newBuilder().setName("uuid").setValue("device_id_2"))
                        .addType("AndroidOnlineDevice"))
                .build()); // Third call, All devices are found

    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setIsAtsServerRequest(true)
            .setDeviceSerials(ImmutableList.of("device_id_1"))
            .build();

    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);

    assertThat(subDeviceSpecs).hasSize(1);
    assertThat(subDeviceSpecs.get(0).getDimensions().getContentMap())
        .containsEntry("uuid", "device_id_1");
    verify(sleeper, times(2)).sleep(Duration.ofSeconds(30));
  }

  @Test
  public void getSubDeviceSpecListForTradefed_atsServerRequestWithDeviceSerials_retryAndFail()
      throws Exception {
    when(deviceQuerier.queryDevice(any())).thenReturn(DeviceQueryResult.getDefaultInstance());

    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setIsAtsServerRequest(true)
            .setDeviceSerials(ImmutableList.of("device_id_1"))
            .build();

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo));

    assertThat(exception).hasMessageThat().contains("No available device is found.");
    verify(sleeper, times(39)).sleep(Duration.ofSeconds(30));
  }

  @Test
  public void
      getSubDeviceSpecListForTradefed_atsServerRequestWithPartialDeviceMatch_allowPartialDeviceMatch_filterNotAvailableDevices()
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

    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setIsAtsServerRequest(true)
            .setAllowPartialDeviceMatch(true)
            .setDeviceSerials(ImmutableList.of("device_id_1", "device_id_2"))
            .build();

    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);

    assertThat(subDeviceSpecs).hasSize(1);
    assertThat(subDeviceSpecs.get(0).getDimensions().getContentMap())
        .containsEntry("uuid", "device_id_1");
  }

  @Test
  public void
      getSubDeviceSpecListForTradefed_atsServerRequestWithPartialDeviceMatch_notAllowPartialDeviceMatch_notFilterNotAvailableDevices()
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

    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setIsAtsServerRequest(true)
            .setAllowPartialDeviceMatch(false)
            .setDeviceSerials(ImmutableList.of("device_id_1", "device_id_2"))
            .build();

    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);

    assertThat(subDeviceSpecs).hasSize(2);
    assertThat(subDeviceSpecs.get(0).getDimensions().getContentMap())
        .containsEntry("uuid", "device_id_1");
    assertThat(subDeviceSpecs.get(1).getDimensions().getContentMap())
        .containsEntry("uuid", "device_id_2");
  }

  @Test
  public void initializeJobConfig_pickOneRealDevice() throws Exception {
    ImmutableMap<String, String> driverParams =
        ImmutableMap.of(
            "xts_type", "cts", "xts_root_dir", XTS_ROOT_DIR_PATH, "xts_test_plan", "cts");
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setDeviceType(SessionRequestHandlerUtil.ANDROID_REAL_DEVICE_TYPE)
            .build();
    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            sessionRequestInfo, driverParams, subDeviceSpecs, ImmutableMultimap.of());

    // subDeviceSpecWithDimension uses "AndroidDevice" type, and cannot be used here.
    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidRealDevice")
                .setDimensions(
                    StringMap.newBuilder().putContent("id", "regex:(device_id_1|device_id_2)"))
                .build());

    assertThat(jobConfig.getDriver().getName()).isEqualTo(TRADEFED_TEST_DRIVER_NAME);
    String driverParamsStr = jobConfig.getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParamsStr, new TypeToken<>() {});
    assertThat(driverParamsMap).containsExactlyEntriesIn(driverParams);
  }

  @Test
  public void initializeJobConfig_excludeDevice() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setExcludeDeviceSerials(ImmutableList.of("device_id_1"))
            .build();
    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            sessionRequestInfo, ImmutableMap.of(), subDeviceSpecs, ImmutableMultimap.of());

    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpecWithDimension("id", "regex:(device_id_2)"));
  }

  @Test
  public void initializeJobConfig_multiDevice_pick2Devices() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder().setTestPlan("cts-multidevice").build();
    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            sessionRequestInfo, ImmutableMap.of(), subDeviceSpecs, ImmutableMultimap.of());
    SubDeviceSpec subDeviceSpec =
        subDeviceSpecWithDimension("id", "regex:(device_id_1|device_id_2)");
    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpec, subDeviceSpec);
  }

  @Test
  public void initializeJobConfig_multiDevice_pick2Emulators() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setTestPlan("cts-multidevice")
            .setDeviceType(SessionRequestHandlerUtil.ANDROID_LOCAL_EMULATOR_TYPE)
            .build();
    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            sessionRequestInfo, ImmutableMap.of(), subDeviceSpecs, ImmutableMultimap.of());

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
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder().setTestPlan("cts-multi-device").build();
    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);
    assertThrows(
        MobileHarnessException.class,
        () -> {
          sessionRequestHandlerUtil.initializeJobConfig(
              sessionRequestInfo, ImmutableMap.of(), subDeviceSpecs, ImmutableMultimap.of());
        });
  }

  @Test
  public void initializeJobConfig_shardCount2_pick2Devices() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setCommandLineArgs("cts --shard-count 2")
            .setShardCount(2)
            .build();
    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            sessionRequestInfo, ImmutableMap.of(), subDeviceSpecs, ImmutableMultimap.of());

    SubDeviceSpec subDeviceSpec =
        subDeviceSpecWithDimension("id", "regex:(device_id_1|device_id_2)");
    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpec, subDeviceSpec);
  }

  @Test
  public void initializeJobConfig_shardCount3_only2OnlineDevices_pick2Devices() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setCommandLineArgs("cts --shard-count 3")
            .setShardCount(3)
            .build();
    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            sessionRequestInfo, ImmutableMap.of(), subDeviceSpecs, ImmutableMultimap.of());

    SubDeviceSpec subDeviceSpec =
        subDeviceSpecWithDimension("id", "regex:(device_id_1|device_id_2)");
    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpec, subDeviceSpec);
  }

  @Test
  public void initializeJobConfig_noOnlineDevices_noJobConfig() throws Exception {
    when(deviceQuerier.queryDevice(any())).thenReturn(DeviceQueryResult.getDefaultInstance());
    SessionRequestInfo sessionRequestInfo = defaultSessionRequestInfoBuilder().build();
    assertThrows(
        MobileHarnessException.class,
        () -> sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo));
  }

  @Test
  public void initializeJobConfig_withGivenSerial() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setEnvVars(ImmutableMap.of("env_key1", "env_value1"))
            .setDeviceSerials(ImmutableList.of("device_id_1"))
            .setModuleNames(ImmutableList.of("module1"))
            .setShardCount(2)
            .setExtraArgs(ImmutableList.of("--logcat-on-failure"))
            .build();
    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            sessionRequestInfo, ImmutableMap.of(), subDeviceSpecs, ImmutableMultimap.of());

    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(subDeviceSpecWithDimension("id", "device_id_1"));
  }

  @Test
  public void initializeJobConfig_someGivenSerialsNotExist_pickExistingDevicesOnly()
      throws Exception {
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setDeviceSerials(ImmutableList.of("device_id_1", "not_exist_device", "device_id_2"))
            .build();
    ImmutableList<SubDeviceSpec> subDeviceSpecs =
        sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo);
    JobConfig jobConfig =
        sessionRequestHandlerUtil.initializeJobConfig(
            sessionRequestInfo, ImmutableMap.of(), subDeviceSpecs, ImmutableMultimap.of());

    assertThat(jobConfig.getDevice().getSubDeviceSpecList())
        .containsExactly(
            subDeviceSpecWithDimension("id", "device_id_1"),
            subDeviceSpecWithDimension("id", "device_id_2"));
  }

  @Test
  public void getSubDeviceSpecListForTradefed_allGivenSerialsNotExist_noJobConfig()
      throws Exception {
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setDeviceSerials(ImmutableList.of("device_id_4", "device_id_5"))
            .build();
    assertThrows(
        MobileHarnessException.class,
        () -> sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(sessionRequestInfo));
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
            sessionRequestInfo, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(2);
    verify(moduleConfigurationHelper, times(2)).updateJobInfo(any(), any(), any(), any());
    assertThat(jobInfos.get(0).setting().getAllocationExitStrategy())
        .isEqualTo(AllocationExitStrategy.FAIL_FAST_NO_MATCH);
    assertThat(jobInfos.get(1).setting().getAllocationExitStrategy())
        .isEqualTo(AllocationExitStrategy.FAIL_FAST_NO_MATCH);
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
            sessionRequestInfo, null, ImmutableMap.of());

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
            sessionRequestInfo, null, ImmutableMap.of());

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
            sessionRequestInfo, null, ImmutableMap.of());

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
            sessionRequestInfo, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(3);
    assertThat(jobInfos.get(0).params().get(MoblyTestSpec.TEST_SELECTOR_KEY)).isNull();
    assertThat(jobInfos.get(1).params().get(MoblyTestSpec.TEST_SELECTOR_KEY).split(" "))
        .asList()
        .containsExactly("test_class1.test1", "test_class2.test2", "test_class2.test3");
    assertThat(jobInfos.get(2).params().get(MoblyTestSpec.TEST_SELECTOR_KEY))
        .isEqualTo("test_class2.test3");
  }

  @Test
  public void createXtsNonTradefedJobs_configMissingDriverName_throwsException() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    Configuration config1 =
        Configuration.newBuilder()
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.getDefaultInstance())
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
            defaultSessionRequestInfoBuilder().build());

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
                            sessionRequestInfo, null, ImmutableMap.of()))
                .getErrorId())
        .isEqualTo(InfraErrorId.XTS_MODULE_CONFIG_MISSING_DRIVER_NAME);
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
            sessionRequestInfo, null, ImmutableMap.of());
    assertThat(jobInfos).hasSize(1);
    assertThat(jobInfos.get(0).params().get(MoblyTestSpec.TEST_SELECTOR_KEY).split(" "))
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
            sessionRequestInfo, null, ImmutableMap.of());
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
            sessionRequestInfo, null, ImmutableMap.of());
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
            sessionRequestInfo, null, ImmutableMap.of());
    assertThat(jobInfos).hasSize(1);
    assertThat(jobInfos.get(0).params().get(MoblyTestSpec.TEST_SELECTOR_KEY))
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
            sessionRequestInfo, null, ImmutableMap.of());
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
            sessionRequestInfo, subPlan, ImmutableMap.of());
    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).params().get(MoblyTestSpec.TEST_SELECTOR_KEY).split(" "))
        .asList()
        .containsExactly("FooTest.test1", "FooTest.test2");
    assertThat(jobInfos.get(0).params().get(PARAM_XTS_SUITE_INFO)).contains("suite_plan=cts");
    assertThat(jobInfos.get(1).params().get(MoblyTestSpec.TEST_SELECTOR_KEY)).isNull();
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
            sessionRequestInfo, subPlan, ImmutableMap.of());
    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).params().get(MoblyTestSpec.TEST_SELECTOR_KEY).split(" "))
        .asList()
        .containsExactly("FooTest.test1", "FooTest.test2");
    assertThat(jobInfos.get(0).params().get(PARAM_XTS_SUITE_INFO)).contains("suite_plan=cts");
    assertThat(jobInfos.get(1).params().get(MoblyTestSpec.TEST_SELECTOR_KEY)).isNull();
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
            sessionRequestInfo, subPlan, ImmutableMap.of());

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
            sessionRequestInfo, subPlan, ImmutableMap.of());

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
            sessionRequestInfo, subPlan, ImmutableMap.of());

    assertThat(jobInfos).hasSize(1);
    assertThat(jobInfos.get(0).params().get(MoblyTestSpec.TEST_SELECTOR_KEY))
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
            sessionRequestInfo, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).params().get("option")).isEqualTo("value");
    assertThat(jobInfos.get(1).params().get("option")).isNull();
    assertThat(jobInfos.get(0).files().get("config")).containsExactly(file1, file2);
    assertThat(jobInfos.get(1).files().get("config")).isEmpty();
  }

  @Test
  public void createXtsNonTradefedJobs_withTestPathOption_ignoreExcludeFilters() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    Configuration config1 =
        defaultConfigurationBuilder()
            .addOptions(Option.newBuilder().setName("test_path").setValue("path/to/test"))
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .build();
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1));
    when(testSuiteHelper.loadTests(any())).thenReturn(ImmutableMap.of("module1", config1));

    // With include filters for one test class and one test case.
    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setIncludeFilters(
                    ImmutableList.of("module1 test_class1", "module1 test_class2#test2"))
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(1);
    assertThat(jobInfos.get(0).params().get(MoblyTestSpec.TEST_SELECTOR_KEY).split(" "))
        .asList()
        .containsExactly("test_class1", "test_class2.test2");

    // With exclude filters, they should be ignored, result should be same.
    sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setIncludeFilters(
                    ImmutableList.of("module1 test_class1", "module1 test_class2#test2"))
                .setExcludeFilters(ImmutableList.of("module1 test_class1"))
                .build());
    jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(1);
    assertThat(jobInfos.get(0).params().get(MoblyTestSpec.TEST_SELECTOR_KEY).split(" "))
        .asList()
        .containsExactly("test_class1", "test_class2.test2");

    // Without include filters, MoblyTestSpec.TEST_SELECTOR_KEY should not be set.
    sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setExcludeFilters(ImmutableList.of("module1 test_class1"))
                .build());
    jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, null, ImmutableMap.of());
    assertThat(jobInfos).hasSize(1);
    assertThat(jobInfos.get(0).params().get(MoblyTestSpec.TEST_SELECTOR_KEY)).isNull();
  }

  @Test
  public void createXtsNonTradefedJobs_noAvailableDevices() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder().build());
    when(deviceQuerier.queryDevice(any())).thenReturn(DeviceQueryResult.getDefaultInstance());

    assertThrows(
        MobileHarnessException.class,
        () ->
            sessionRequestHandlerUtil.createXtsNonTradefedJobs(
                sessionRequestInfo, null, ImmutableMap.of()));
  }

  @Test
  public void createXtsNonTradefedJobs_retry_loadTestPlanFilters() throws Exception {
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
            ImmutableSetMultimap.of(
                "FooTest",
                "FooTest.test1",
                "FooTest",
                "FooTest.test2",
                "FooTest",
                "FooTest.test3"));
    when(testPlanParser.parseFilters(any(), any(), eq("cts")))
        .thenReturn(
            TestPlanFilter.create(
                ImmutableSet.of(),
                ImmutableSet.of("module1 FooTest#test1", "module2 FooTest#test2"),
                ImmutableMultimap.of(),
                ImmutableMultimap.of(),
                ImmutableSet.of(
                    "com.android.compatibility.common.tradefed.testtype.suite.CompatibilityTestSuite")));

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setTestPlan("retry")
                .setCommandLineArgs("retry")
                .setRetrySessionIndex(0)
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, subPlan, ImmutableMap.of());
    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).params().get(MoblyTestSpec.TEST_SELECTOR_KEY).split(" "))
        .asList()
        .containsExactly("FooTest.test2");
    assertThat(jobInfos.get(0).params().get(PARAM_XTS_SUITE_INFO)).contains("suite_plan=cts");
    assertThat(jobInfos.get(1).params().get(MoblyTestSpec.TEST_SELECTOR_KEY).split(" "))
        .asList()
        .containsExactly("FooTest.test1", "FooTest.test3");
    assertThat(jobInfos.get(1).params().get(PARAM_XTS_SUITE_INFO)).contains("suite_plan=cts");
  }

  @Test
  public void createXtsNonTradefedJobs_withVenvPath() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setModuleArgs(ImmutableList.of("module1:venv_path:/path/to/custom/venv"))
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).params().getOptional(MoblyAospTestSpec.PARAM_VENV_PATH))
        .hasValue(Path.of("/path/to/custom/venv").toString()); // module arg takes precedence
    assertThat(jobInfos.get(1).params().getOptional(MoblyAospTestSpec.PARAM_VENV_PATH))
        .hasValue(Path.of("/path/to/venv").toString()); // default venv path
  }

  @Test
  public void createXtsNonTradefedJobs_atsServerRequest_noVenvPath() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder().setIsAtsServerRequest(true).build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, null, ImmutableMap.of());

    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).params().getOptional(MoblyAospTestSpec.PARAM_VENV_PATH)).isEmpty();
    assertThat(jobInfos.get(1).params().getOptional(MoblyAospTestSpec.PARAM_VENV_PATH)).isEmpty();
  }

  @Test
  public void filterModuleByConfigMetadata_success() throws Exception {
    ImmutableListMultimap<String, String> moduleMetadata =
        ImmutableListMultimap.of(
            "key1", "value1", "key1", "value2", "key2", "value3", "key2", "value4");

    // Include filter partially matched.
    assertThat(
            SessionRequestHandlerUtil.filterModuleByConfigMetadata(
                moduleMetadata,
                ImmutableMultimap.of("key1", "value1", "key1", "value2"),
                ImmutableMultimap.of("key2", "value5")))
        .isTrue();
    // Include filter fully match.
    assertThat(
            SessionRequestHandlerUtil.filterModuleByConfigMetadata(
                moduleMetadata,
                ImmutableMultimap.of(
                    "key1", "value1", "key1", "value2", "key2", "value3", "key2", "value4"),
                ImmutableMultimap.of("key1", "value5", "key2", "value5")))
        .isTrue();
    // Include filter unmatched.
    assertThat(
            SessionRequestHandlerUtil.filterModuleByConfigMetadata(
                moduleMetadata,
                ImmutableMultimap.of("key1", "value1", "key1", "value2", "key1", "value3"),
                ImmutableMultimap.of()))
        .isFalse();
    // Exclude filter partially matched.
    assertThat(
            SessionRequestHandlerUtil.filterModuleByConfigMetadata(
                moduleMetadata,
                ImmutableMultimap.of("key1", "value1", "key1", "value2"),
                ImmutableMultimap.of("key2", "value3")))
        .isFalse();
    // Exclude filter fully match.
    assertThat(
            SessionRequestHandlerUtil.filterModuleByConfigMetadata(
                moduleMetadata,
                ImmutableMultimap.of("key1", "value1", "key1", "value2"),
                ImmutableMultimap.of("key2", "value3", "key2", "value4")))
        .isFalse();
    // Exclude filter unmatched.
    assertThat(
            SessionRequestHandlerUtil.filterModuleByConfigMetadata(
                moduleMetadata, ImmutableMultimap.of(), ImmutableMultimap.of("key2", "value5")))
        .isTrue();
  }

  @Test
  public void excludeNonTfModuleByRunner_simpleClassName() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setExcludeRunners(ImmutableSet.of("Driver"))
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, null, ImmutableMap.of());

    assertThat(jobInfos).isEmpty();
  }

  @Test
  public void excludeNonTfModuleByRunner_fullClassName() throws Exception {
    setUpForCreateXtsNonTradefedJobs();
    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder()
                .setExcludeRunners(ImmutableSet.of("com.google.Driver"))
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, null, ImmutableMap.of());

    assertThat(jobInfos).isEmpty();
  }

  @Test
  public void excludeNonTfModuleByTestPlan() throws Exception {
    when(testPlanParser.parseFilters(any(), any(), eq("cts")))
        .thenReturn(
            TestPlanFilter.create(
                ImmutableSet.of(),
                ImmutableSet.of("module1 FooTest#test1", "module2 FooTest#test2"),
                ImmutableMultimap.of(),
                ImmutableMultimap.of(),
                ImmutableSet.of()));
    setUpForCreateXtsNonTradefedJobs();
    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            defaultSessionRequestInfoBuilder().build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfo, null, ImmutableMap.of());

    assertThat(jobInfos).isEmpty();
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

  @Test
  public void getHostIp_success() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addDimension(
                            Dimension.newBuilder().setName("host_ip").setValue("192.168.1.1")))
                .build());

    assertThat(sessionRequestHandlerUtil.getHostIp("device_id_1")).isEqualTo("192.168.1.1");
  }

  @Test
  public void getHostIp_deviceNotFound() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_2")
                        .addDimension(
                            Dimension.newBuilder().setName("host_ip").setValue("192.168.1.1")))
                .build());

    assertThat(sessionRequestHandlerUtil.getHostIp("device_id_1")).isEmpty();
  }

  @Test
  public void getHostIp_hostIpNotFound() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(DeviceInfo.newBuilder().setId("device_id_1"))
                .build());

    assertThat(sessionRequestHandlerUtil.getHostIp("device_id_1")).isEmpty();
  }

  @Test
  public void getSimCardTypeDimensionValue_noTokenKey_returnsEmpty() {
    ImmutableListMultimap<String, String> moduleMetadata =
        ImmutableListMultimap.of("other_key", "value");
    assertThat(SessionRequestHandlerUtil.getSimCardTypeDimensionValue(moduleMetadata)).isEmpty();
  }

  @Test
  public void getSimCardTypeDimensionValue_tokenKeyWithNoMatchingValue_returnsEmpty() {
    ImmutableListMultimap<String, String> moduleMetadata =
        ImmutableListMultimap.of("token", "NOT_A_SIM_TYPE");
    assertThat(SessionRequestHandlerUtil.getSimCardTypeDimensionValue(moduleMetadata)).isEmpty();
  }

  @Test
  public void getSimCardTypeDimensionValue_tokenKeyWithMatchingValue_returnsValue() {
    ImmutableListMultimap<String, String> moduleMetadata =
        ImmutableListMultimap.of("token", "SIM_CARD", "token", "OTHER_VALUE");
    assertThat(SessionRequestHandlerUtil.getSimCardTypeDimensionValue(moduleMetadata))
        .hasValue("SIM_CARD");
  }

  @Test
  public void getSimCardTypeDimensionValue_tokenKeyWithMultipleMatchingValues_returnsFirst() {
    ImmutableListMultimap<String, String> moduleMetadata =
        ImmutableListMultimap.of("token", "UICC_SIM_CARD", "token", "SIM_CARD");
    assertThat(SessionRequestHandlerUtil.getSimCardTypeDimensionValue(moduleMetadata))
        .hasValue("UICC_SIM_CARD");
  }

  @Test
  public void getDeviceInfo_atsServerRequest_waitForDevices() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder()
            .setIsAtsServerRequest(true)
            .setDeviceSerials(ImmutableList.of("device_id_1"))
            .build();

    when(deviceQuerier.queryDevice(any()))
        .thenReturn(DeviceQueryResult.getDefaultInstance()) // First call, no devices
        .thenReturn(DeviceQueryResult.getDefaultInstance()) // Second call, no devices
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addDimension(
                            Dimension.newBuilder().setName("uuid").setValue("device_id_1"))
                        .addType("AndroidOnlineDevice"))
                .build()); // Third call, device found

    var unused = sessionRequestHandlerUtil.getDeviceInfo(sessionRequestInfo);

    verify(sleeper, times(2)).sleep(Duration.ofSeconds(30));
    // Verify that deviceQuerier.queryDevice(any()) is called 4 times: 3 times in
    // waitForRequestedDevicesToBeReady and 1 time in getDeviceInfoFromMaster.
    verify(deviceQuerier, times(4)).queryDevice(any());
  }
}
