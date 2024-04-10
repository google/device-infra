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
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfoFactory;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportParser;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.SessionGenDir;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.SessionTempDir;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ModuleConfigurationHelper;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationMetadata;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Device;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryGenerator;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
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
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class SessionRequestHandlerUtilTest {

  private static final String TEST_DATA_PREFIX =
      "javatests/com/google/devtools/mobileharness/infra/ats/common/testdata/subplans/";

  private static final String SUBPLAN1_XML =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "subplan1.xml");

  private static final String SUBPLAN2_XML =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "subplan2.xml");

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
  @Bind @Mock private RetryGenerator retryGenerator;
  @Bind @Mock private CompatibilityReportCreator reportCreator;
  @Bind @Mock private CertificationSuiteInfoFactory certificationSuiteInfoFactory;
  @Mock private TestSuiteHelper testSuiteHelper;
  @Bind @SessionGenDir private Path sessionGenDir;
  @Bind @SessionTempDir private Path sessionTempDir;
  @Bind @Mock private SessionInfo sessionInfo;

  @Inject private SessionRequestHandlerUtil sessionRequestHandlerUtil;

  private LocalFileUtil realLocalFileUtil;
  private TestPlanLoader.TestPlanFilter testPlanFilter;

  @Before
  public void setUp() throws Exception {
    // Sets flags.
    ImmutableMap<String, String> flagMap = ImmutableMap.of("enable_ats_mode", "true");
    Flags.parse(
        flagMap.entrySet().stream()
            .map(e -> String.format("--%s=%s", e.getKey(), e.getValue()))
            .collect(toImmutableList())
            .toArray(new String[0]));

    sessionGenDir = folder.newFolder("session_gen_dir").toPath();
    sessionTempDir = folder.newFolder("session_temp_dir").toPath();

    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    testPlanFilter = TestPlanLoader.TestPlanFilter.create(ImmutableSet.of(), ImmutableSet.of());
    realLocalFileUtil = new LocalFileUtil();
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
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
            "xts_type", "cts", "xts_root_dir", XTS_ROOT_DIR_PATH, "xts_test_plan", "cts");
  }

  @Test
  public void createXtsTradefedTestJobConfig_verifyUseParallelSetup() throws Exception {
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setUseParallelSetup(true)
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
            "xts_type",
            "cts",
            "xts_root_dir",
            XTS_ROOT_DIR_PATH,
            "xts_test_plan",
            "cts",
            "run_command_args",
            "--parallel-setup true --parallel-setup-timeout 0");
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
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
            "xts_type",
            "cts",
            "android_xts_zip",
            "ats-file-server::" + ANDROID_XTS_ZIP_PATH,
            "xts_test_plan",
            "cts");
  }

  @Test
  public void createXtsTradefedTestJobConfig_addSubPlanXmlPathForRetry() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .build());
    SubPlan subPlan = new SubPlan();
    subPlan.addIncludeFilter("armeabi-v7a ModuleA android.test.Foo#test1");
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);
    doCallRealMethod().when(localFileUtil).prepareDir(any(Path.class));

    File xtsRootDir = folder.newFolder("xts_root_dir");

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobConfig(
            SessionRequestInfo.builder()
                .setTestPlan("retry")
                .setXtsType("cts")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setRetrySessionId(0)
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
    assertThat(driverParamsMap).hasSize(4);
    assertThat(driverParamsMap)
        .containsAtLeast(
            "xts_type",
            "cts",
            "xts_root_dir",
            xtsRootDir.getAbsolutePath(),
            "xts_test_plan",
            "retry");
    assertThat(driverParamsMap.get("subplan_xml")).startsWith(xtsRootDir.getAbsolutePath());
  }

  @Test
  public void createXtsTradefedTestJobConfig_addSubPlanXmlPathForSubPlanCommand() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .build());
    doCallRealMethod().when(localFileUtil).isFileExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).removeFileOrDir(any(Path.class));

    File xtsRootDir = folder.newFolder("xts_root_dir");
    Path subPlansDir = xtsRootDir.toPath().resolve("android-cts/subplans");
    realLocalFileUtil.prepareDir(subPlansDir);
    realLocalFileUtil.copyFileOrDir(SUBPLAN1_XML, subPlansDir.toAbsolutePath().toString());

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobConfig(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setSubPlanName("subplan1")
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
    assertThat(driverParamsMap).hasSize(4);
    assertThat(driverParamsMap)
        .containsAtLeast(
            "xts_type",
            "cts",
            "xts_root_dir",
            xtsRootDir.getAbsolutePath(),
            "xts_test_plan",
            "cts");
    assertThat(driverParamsMap.get("subplan_xml"))
        .isEqualTo(subPlansDir.resolve("subplan1_tf_auto_gen.xml").toString());
  }

  @Test
  public void
      createXtsTradefedTestJobConfig_addSubPlanXmlPathForSubPlanCommand_useOriginalSubPlanXml()
          throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .build());
    doCallRealMethod().when(localFileUtil).isFileExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).removeFileOrDir(any(Path.class));

    File xtsRootDir = folder.newFolder("xts_root_dir");
    Path subPlansDir = xtsRootDir.toPath().resolve("android-cts/subplans");
    realLocalFileUtil.prepareDir(subPlansDir);
    realLocalFileUtil.copyFileOrDir(SUBPLAN2_XML, subPlansDir.toAbsolutePath().toString());

    Optional<JobConfig> jobConfigOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobConfig(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setSubPlanName("subplan2")
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
    assertThat(driverParamsMap).hasSize(4);
    assertThat(driverParamsMap)
        .containsAtLeast(
            "xts_type",
            "cts",
            "xts_root_dir",
            xtsRootDir.getAbsolutePath(),
            "xts_test_plan",
            "cts");
    assertThat(driverParamsMap.get("subplan_xml"))
        .isEqualTo(subPlansDir.resolve("subplan2.xml").toString());
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setEnvVars(ImmutableMap.of("env_key1", "env_value1"))
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
            "cts",
            "xts_root_dir",
            XTS_ROOT_DIR_PATH,
            "xts_test_plan",
            "cts",
            "env_vars",
            "{\"env_key1\":\"env_value1\"}",
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setDeviceSerials(ImmutableList.of("device_id_4", "device_id_5"))
                .build(),
            ImmutableList.of());

    assertThat(jobConfigOpt).isEmpty();
  }

  @Test
  public void createXtsTradefedTestJobConfig_withGivenTest() throws Exception {
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setEnvVars(ImmutableMap.of("env_key1", "env_value1"))
                .setModuleNames(ImmutableList.of("module1"))
                .setTestName("test1")
                .setShardCount(2)
                .setExtraArgs(ImmutableList.of("--logcat-on-failure"))
                .build(),
            ImmutableList.of("module1"));

    assertThat(jobConfigOpt).isPresent();

    // Asserts the driver
    assertThat(jobConfigOpt.get().getDriver().getName()).isEqualTo("XtsTradefedTest");
    String driverParams = jobConfigOpt.get().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParams, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap)
        .containsExactly(
            "xts_type",
            "cts",
            "xts_root_dir",
            XTS_ROOT_DIR_PATH,
            "xts_test_plan",
            "cts",
            "env_vars",
            "{\"env_key1\":\"env_value1\"}",
            "run_command_args",
            "-m module1 -t test1 --shard-count 2 --logcat-on-failure");
  }

  @Test
  public void createXtsTradefedTestJob_testFilters() throws Exception {
    Configuration config1 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(false))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config2 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(false))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();

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
    when(localFileUtil.isDirExist(XTS_ROOT_DIR_PATH)).thenReturn(true);
    when(configurationUtil.getConfigsFromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1, "/path/to/config2", config2));
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests())
        .thenReturn(ImmutableMap.of("module1", config1, "module2", config2));

    SessionRequestInfo sessionRequestInfoWithIncludeFilters =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setIncludeFilters(ImmutableList.of("module3 TestClass#TestCase"))
            .build();
    Optional<JobInfo> jobInfoOptWithIncludeFilters =
        sessionRequestHandlerUtil.createXtsTradefedTestJob(sessionRequestInfoWithIncludeFilters);

    SessionRequestInfo sessionRequestInfoWithExcludeFilters =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setExcludeFilters(ImmutableList.of("module1", "module2"))
            .build();
    Optional<JobInfo> jobInfoOptWithExcludeFilters =
        sessionRequestHandlerUtil.createXtsTradefedTestJob(sessionRequestInfoWithExcludeFilters);

    SessionRequestInfo sessionRequestInfoWithMixedFilters =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setIncludeFilters(ImmutableList.of("module1"))
            .setExcludeFilters(ImmutableList.of("module2"))
            .build();
    Optional<JobInfo> jobInfoOptWithMixedFilters =
        sessionRequestHandlerUtil.createXtsTradefedTestJob(sessionRequestInfoWithMixedFilters);

    assertThat(jobInfoOptWithIncludeFilters).isEmpty();
    assertThat(jobInfoOptWithExcludeFilters).isEmpty();
    assertThat(jobInfoOptWithMixedFilters).isPresent();
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

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

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
                SessionRequestInfo.builder()
                    .setTestPlan("cts")
                    .setXtsType("cts")
                    .setXtsRootDir(XTS_ROOT_DIR_PATH)
                    .setModuleNames(ImmutableList.of("TfModule1"))
                    .build()),
            testPlanFilter);

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

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setModuleNames(ImmutableList.of("TfModule1", "module2"))
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    assertThat(jobInfos).hasSize(1);
  }

  @Test
  public void createXtsNonTradefedJobs_testFilters() throws Exception {
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
    Configuration config3 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module3").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    when(localFileUtil.isDirExist(XTS_ROOT_DIR_PATH)).thenReturn(true);
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(
            ImmutableMap.of(
                "/path/to/config1",
                config1,
                "/path/to/config2",
                config2,
                "/path/to/config3",
                config3));
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests())
        .thenReturn(ImmutableMap.of("module1", config1, "module2", config2, "module3", config3));

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setIncludeFilters(
                    ImmutableList.of(
                        "module1 test_class1#test1",
                        "module1 test_class2#test2",
                        "module1 test_class2#test3",
                        "module2",
                        "module3 test_class1#test1"))
                .setExcludeFilters(ImmutableList.of("module3"))
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).params().get("test_case_selector")).isEqualTo("test1 test2 test3");
    assertThat(jobInfos.get(1).params().get("test_case_selector")).isNull();
  }

  @Test
  public void createXtsNonTradefedJobs_withGivenTest() throws Exception {
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
    when(localFileUtil.isDirExist(XTS_ROOT_DIR_PATH)).thenReturn(true);
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1));
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests()).thenReturn(ImmutableMap.of("module1", config1));

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
                .setModuleNames(ImmutableList.of("module1"))
                .setTestName("testclass#test1")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    SessionRequestInfo sessionRequestInfoWithInvalidTestName =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
                .setModuleNames(ImmutableList.of("module1"))
                .setTestName("test1")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .build());
    ImmutableList<JobInfo> jobInfosWithInvalidTestName =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestInfoWithInvalidTestName, testPlanFilter);

    assertThat(jobInfos).hasSize(1);
    assertThat(jobInfos.get(0).params().get("test_case_selector")).isEqualTo("test1");
    assertThat(jobInfosWithInvalidTestName).isEmpty();
  }

  @Test
  public void createXtsNonTradefedJobs_retry_noNonTfFailedTestsFound_skipped() throws Exception {
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
        .thenReturn(ImmutableMap.of("arm64-v8a module1", config1, "arm64-v8a module2", config2));
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(new SubPlan());

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestInfo.builder()
                .setTestPlan("retry")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setRetrySessionId(0)
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    assertThat(jobInfos).isEmpty();
  }

  @Test
  public void createXtsNonTradefedJobs_retry_nonTfFailedTests() throws Exception {
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
    Configuration config3 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module3").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    when(localFileUtil.isDirExist(XTS_ROOT_DIR_PATH)).thenReturn(true);
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(
            ImmutableMap.of(
                "/path/to/config1",
                config1,
                "/path/to/config2",
                config2,
                "/path/to/config3",
                config3));
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests())
        .thenReturn(
            ImmutableMap.of(
                "arm64-v8a module1",
                config1,
                "arm64-v8a module2",
                config2,
                "arm64-v8a module3",
                config3));
    SubPlan subPlan = new SubPlan();
    subPlan.addNonTfIncludeFilter("arm64-v8a module1 android.test.Foo#test1");
    subPlan.addNonTfIncludeFilter("arm64-v8a module1 android.test.Foo#test2");
    subPlan.addNonTfIncludeFilter("arm64-v8a module2"); // retry entire module
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestInfo.builder()
                .setTestPlan("retry")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setRetrySessionId(0)
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).params().get("test_case_selector")).isEqualTo("test1 test2");
    assertThat(jobInfos.get(1).params().get("test_case_selector")).isNull();
  }

  @Test
  public void createXtsNonTradefedJobs_subPlanCmd() throws Exception {
    File xtsRootDir = folder.newFolder("xts_root_dir");
    Path subPlansDir = xtsRootDir.toPath().resolve("android-cts/subplans");
    realLocalFileUtil.prepareDir(subPlansDir);
    realLocalFileUtil.copyFileOrDir(SUBPLAN1_XML, subPlansDir.toAbsolutePath().toString());

    Configuration config1 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder()
                    .setXtsModule("HelloWorldTest")
                    .setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1));
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests())
        .thenReturn(
            ImmutableMap.of(
                "arm64-v8a HelloWorldTest", config1, "arm64-v8a HelloWorldTest[instant]", config1));
    doCallRealMethod().when(localFileUtil).isFileExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).isDirExist(any(String.class));

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setSubPlanName("subplan1")
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).locator().getName()).endsWith("HelloWorldTest");
    assertThat(jobInfos.get(1).locator().getName()).endsWith("HelloWorldTest[instant]");
  }

  @Test
  public void createXtsNonTradefedJobs_noNonTfModulesAndTestsFound_skipped() throws Exception {
    File xtsRootDir = folder.newFolder("xts_root_dir");
    Path subPlansDir = xtsRootDir.toPath().resolve("android-cts/subplans");
    realLocalFileUtil.prepareDir(subPlansDir);
    realLocalFileUtil.copyFileOrDir(SUBPLAN2_XML, subPlansDir.toAbsolutePath().toString());

    Configuration config1 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder()
                    .setXtsModule("HelloWorldTest")
                    .setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1));
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests())
        .thenReturn(
            ImmutableMap.of(
                "arm64-v8a HelloWorldTest", config1, "arm64-v8a HelloWorldTest[instant]", config1));
    doCallRealMethod().when(localFileUtil).isFileExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).isDirExist(any(String.class));

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setXtsType("cts")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setSubPlanName("subplan2")
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    assertThat(jobInfos).isEmpty();
  }

  @Test
  public void getTestResultFromTest_success() throws Exception {
    TestInfo testInfo = Mockito.mock(TestInfo.class);
    Path resultFilePath = Path.of("/data/genfiles/test_id/test_result.xml");
    when(testInfo.locator())
        .thenReturn(new TestLocator("test_id", "test_name", new JobLocator("job_id", "job_name")));
    when(localFileUtil.isDirExist("/data/genfiles/test_id")).thenReturn(true);
    when(localFileUtil.listFilePaths(eq(Path.of("/data/genfiles/test_id")), eq(true), any()))
        .thenReturn(ImmutableList.of(resultFilePath));
    Optional<Result> result = Optional.of(Result.getDefaultInstance());
    when(compatibilityReportParser.parse(resultFilePath)).thenReturn(result);

    assertThat(sessionRequestHandlerUtil.getTestResultFromTest(testInfo)).isEqualTo(result);
  }

  @Test
  public void getTestResultFromTest_parserFailed() throws Exception {
    TestInfo testInfo = Mockito.mock(TestInfo.class);
    Path resultFilePath = Path.of("/data/genfiles/test_id/test_result.xml");
    when(testInfo.locator())
        .thenReturn(new TestLocator("test_id", "test_name", new JobLocator("job_id", "job_name")));
    when(localFileUtil.listFilePaths(eq(Path.of("/data/genfiles/test_id")), eq(true), any()))
        .thenReturn(ImmutableList.of(resultFilePath));
    when(localFileUtil.isDirExist("/data/genfiles/test_id")).thenReturn(true);
    when(compatibilityReportParser.parse(resultFilePath)).thenReturn(Optional.empty());

    assertThat(sessionRequestHandlerUtil.getTestResultFromTest(testInfo)).isEmpty();
  }
}
