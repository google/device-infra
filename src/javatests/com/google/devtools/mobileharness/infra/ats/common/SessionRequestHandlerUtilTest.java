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
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil.TradefedJobInfo;
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
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryArgs;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
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

  private static final String SUBPLAN3_XML =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "subplan3.xml");

  private static final String XTS_ROOT_DIR_PATH = "/path/to/xts_root_dir";
  private static final String ANDROID_XTS_ZIP_PATH = "ats-file-server::/path/to/android_xts.zip";

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
    setFlags(/* enableAtsMode= */ true);

    sessionGenDir = folder.newFolder("session_gen_dir").toPath();
    sessionTempDir = folder.newFolder("session_temp_dir").toPath();

    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    testPlanFilter = TestPlanLoader.TestPlanFilter.create(ImmutableSet.of(), ImmutableSet.of());
    realLocalFileUtil = new LocalFileUtil();
  }

  private void setFlags(boolean enableAtsMode) {
    ImmutableMap<String, String> flagMap =
        ImmutableMap.of("enable_ats_mode", String.valueOf(enableAtsMode));
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

  @Test
  public void createXtsTradefedTestJobInfo_calculateTimeout() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setJobTimeout(Duration.ofSeconds(3000L))
                .setStartTimeout(Duration.ofSeconds(1000L))
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getJobTimeoutSec()).isEqualTo(3000L);
    assertThat(tradefedJobInfoOpt.get().jobConfig().getTestTimeoutSec()).isEqualTo(2940L);
    assertThat(tradefedJobInfoOpt.get().jobConfig().getStartTimeoutSec()).isEqualTo(1000L);
  }

  @Test
  public void createXtsTradefedTestJobInfo_pickOneDevice() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDevice().getSubDeviceSpecList())
        .containsExactly(SubDeviceSpec.newBuilder().setType("AndroidDevice").build());

    // Asserts the driver
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDriver().getName())
        .isEqualTo("XtsTradefedTest");
    String driverParams = tradefedJobInfoOpt.get().jobConfig().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParams, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap)
        .containsExactly(
            "xts_type", "cts", "xts_root_dir", XTS_ROOT_DIR_PATH, "xts_test_plan", "cts");
  }

  @Test
  public void createXtsTradefedTestJobInfo_multiDevice_pick2Devices() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts-multidevice")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder().setType("AndroidDevice").build(),
            SubDeviceSpec.newBuilder().setType("AndroidDevice").build());
  }

  @Test
  public void createXtsTradefedTestJobInfo_multiDevice_noEnoughDevices() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .build());

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts-multi-device")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isEmpty();
  }

  @Test
  public void createXtsTradefedTestJobInfo_addAndroidXtsZipPathIfAvailable() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .build());

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setAndroidXtsZip(ANDROID_XTS_ZIP_PATH)
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDevice().getSubDeviceSpecList())
        .containsExactly(SubDeviceSpec.newBuilder().setType("AndroidDevice").build());

    // Asserts the driver
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDriver().getName())
        .isEqualTo("XtsTradefedTest");
    String driverParams = tradefedJobInfoOpt.get().jobConfig().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParams, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap)
        .containsExactly(
            "xts_type", "cts", "android_xts_zip", ANDROID_XTS_ZIP_PATH, "xts_test_plan", "cts");
  }

  @Test
  public void createXtsTradefedTestJobInfo_addSubPlanXmlPathForRetry() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .build());
    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.addIncludeFilter("armeabi-v7a ModuleA android.test.Foo#test1");
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);
    doCallRealMethod().when(localFileUtil).prepareDir(any(Path.class));

    File xtsRootDir = folder.newFolder("xts_root_dir");

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("retry")
                .setCommandLineArgs("retry --retry 0")
                .setXtsType("cts")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setRetrySessionIndex(0)
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDevice().getSubDeviceSpecList())
        .containsExactly(SubDeviceSpec.newBuilder().setType("AndroidDevice").build());

    // Asserts the driver
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDriver().getName())
        .isEqualTo("XtsTradefedTest");
    String driverParams = tradefedJobInfoOpt.get().jobConfig().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParams, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap).hasSize(5);
    assertThat(driverParamsMap)
        .containsAtLeast(
            "xts_type",
            "cts",
            "xts_root_dir",
            xtsRootDir.getAbsolutePath(),
            "xts_test_plan",
            "retry",
            "prev_session_xts_test_plan",
            "cts");
    assertThat(driverParamsMap.get("subplan_xml")).startsWith(xtsRootDir.getAbsolutePath());
  }

  @Test
  public void createXtsTradefedTestJobInfo_retrySubplanWithFiltersAtsConsole() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .build());
    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.addIncludeFilter("armeabi-v7a ModuleA android.test.Foo#test1");
    subPlan.addExcludeFilter("armeabi-v7a ModuleB android.test.Foo#test1");
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);
    doCallRealMethod().when(localFileUtil).prepareDir(any(Path.class));

    File xtsRootDir = folder.newFolder("xts_root_dir");

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("retry")
                .setCommandLineArgs("retry --retry 0")
                .setXtsType("cts")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setRetrySessionIndex(0)
                .setIncludeFilters(ImmutableList.of("armeabi-v7a ModuleA android.test.Foo#test1"))
                .setExcludeFilters(ImmutableList.of("armeabi-v7a ModuleB android.test.Foo#test1"))
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDevice().getSubDeviceSpecList())
        .containsExactly(SubDeviceSpec.newBuilder().setType("AndroidDevice").build());

    // Verify generator got correct input.
    ArgumentCaptor<RetryArgs> retryArgsCaptor = ArgumentCaptor.forClass(RetryArgs.class);
    verify(retryGenerator).generateRetrySubPlan(retryArgsCaptor.capture());
    assertThat(retryArgsCaptor.getValue().passedInIncludeFilters()).hasSize(1);
    assertThat(retryArgsCaptor.getValue().passedInIncludeFilters().iterator().next().filterString())
        .isEqualTo("armeabi-v7a ModuleA android.test.Foo#test1");
    assertThat(retryArgsCaptor.getValue().passedInExcludeFilters()).hasSize(1);
    assertThat(retryArgsCaptor.getValue().passedInExcludeFilters().iterator().next().filterString())
        .isEqualTo("armeabi-v7a ModuleB android.test.Foo#test1");

    // Asserts the driver
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDriver().getName())
        .isEqualTo("XtsTradefedTest");
    String driverParams = tradefedJobInfoOpt.get().jobConfig().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParams, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap).hasSize(5);
    assertThat(driverParamsMap)
        .containsAtLeast(
            "xts_type",
            "cts",
            "xts_root_dir",
            xtsRootDir.getAbsolutePath(),
            "xts_test_plan",
            "retry",
            "prev_session_xts_test_plan",
            "cts");
    assertThat(driverParamsMap.get("subplan_xml")).startsWith(xtsRootDir.getAbsolutePath());
  }

  @Test
  public void createXtsTradefedTestJobInfo_retrySubplanWithFiltersAtsServer() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .build());
    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.addIncludeFilter("armeabi-v7a ModuleA android.test.Foo#test1");
    subPlan.addExcludeFilter("armeabi-v7a ModuleB android.test.Foo#test1");
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);
    doCallRealMethod().when(localFileUtil).prepareDir(any(Path.class));

    File xtsRootDir = folder.newFolder("xts_root_dir");

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("retry")
                .setCommandLineArgs("retry --retry 0")
                .setXtsType("cts")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setRetrySessionId("previous_session_id")
                .setRetryResultDir("/retry/result/dir")
                .setIncludeFilters(ImmutableList.of("armeabi-v7a ModuleA android.test.Foo#test1"))
                .setExcludeFilters(ImmutableList.of("armeabi-v7a ModuleB android.test.Foo#test1"))
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDevice().getSubDeviceSpecList())
        .containsExactly(SubDeviceSpec.newBuilder().setType("AndroidDevice").build());

    // Verify generator got correct input.
    ArgumentCaptor<RetryArgs> retryArgsCaptor = ArgumentCaptor.forClass(RetryArgs.class);
    verify(retryGenerator).generateRetrySubPlan(retryArgsCaptor.capture());
    assertThat(retryArgsCaptor.getValue().passedInIncludeFilters()).hasSize(1);
    assertThat(retryArgsCaptor.getValue().passedInIncludeFilters().iterator().next().filterString())
        .isEqualTo("armeabi-v7a ModuleA android.test.Foo#test1");
    assertThat(retryArgsCaptor.getValue().passedInExcludeFilters()).hasSize(1);
    assertThat(retryArgsCaptor.getValue().passedInExcludeFilters().iterator().next().filterString())
        .isEqualTo("armeabi-v7a ModuleB android.test.Foo#test1");

    // Asserts the driver
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDriver().getName())
        .isEqualTo("XtsTradefedTest");
    String driverParams = tradefedJobInfoOpt.get().jobConfig().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParams, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap).hasSize(5);
    assertThat(driverParamsMap)
        .containsAtLeast(
            "xts_type",
            "cts",
            "xts_root_dir",
            xtsRootDir.getAbsolutePath(),
            "xts_test_plan",
            "retry",
            "prev_session_xts_test_plan",
            "cts");
    assertThat(driverParamsMap.get("subplan_xml")).startsWith(xtsRootDir.getParent());
  }

  @Test
  public void createXtsTradefedTestJobInfo_addSubPlanXmlPathForRetryInAtsServer() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .build());
    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.addIncludeFilter("armeabi-v7a ModuleA android.test.Foo#test1");
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);
    doCallRealMethod().when(localFileUtil).prepareDir(any(Path.class));

    File xtsRootDir = folder.newFolder("xts_root_dir");

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("retry")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setRetrySessionId("previous_session_id")
                .setRetryResultDir("/retry/result/dir")
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDevice().getSubDeviceSpecList())
        .containsExactly(SubDeviceSpec.newBuilder().setType("AndroidDevice").build());

    // Asserts the driver
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDriver().getName())
        .isEqualTo("XtsTradefedTest");
    String driverParams = tradefedJobInfoOpt.get().jobConfig().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParams, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap).hasSize(5);
    assertThat(driverParamsMap)
        .containsAtLeast(
            "xts_type",
            "cts",
            "xts_root_dir",
            xtsRootDir.getAbsolutePath(),
            "xts_test_plan",
            "retry",
            "prev_session_xts_test_plan",
            "cts");
    assertThat(driverParamsMap.get("subplan_xml")).startsWith(xtsRootDir.getParent());
    ArgumentCaptor<RetryArgs> retryArgsCaptor = ArgumentCaptor.forClass(RetryArgs.class);
    verify(retryGenerator).generateRetrySubPlan(retryArgsCaptor.capture());
    RetryArgs retryArgs = retryArgsCaptor.getValue();
    assertThat(retryArgs.previousSessionId()).hasValue("previous_session_id");
    assertThat(retryArgs.resultsDir().toString()).isEqualTo("/retry/result/dir");
    assertThat(retryArgs.previousSessionIndex().isEmpty()).isTrue();
  }

  @Test
  public void createXtsTradefedTestJobInfo_addSubPlanXmlPathForSubPlanCommand() throws Exception {
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

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts --subplan subplan1")
                .setXtsType("cts")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setSubPlanName("subplan1")
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDevice().getSubDeviceSpecList())
        .containsExactly(SubDeviceSpec.newBuilder().setType("AndroidDevice").build());

    // Asserts the driver
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDriver().getName())
        .isEqualTo("XtsTradefedTest");
    String driverParams = tradefedJobInfoOpt.get().jobConfig().getDriver().getParam();
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
      createXtsTradefedTestJobInfo_addSubPlanXmlPathForSubPlanCommand_useOriginalSubPlanXml()
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

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts --subplan subplan2")
                .setXtsType("cts")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setSubPlanName("subplan2")
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDevice().getSubDeviceSpecList())
        .containsExactly(SubDeviceSpec.newBuilder().setType("AndroidDevice").build());

    // Asserts the driver
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDriver().getName())
        .isEqualTo("XtsTradefedTest");
    String driverParams = tradefedJobInfoOpt.get().jobConfig().getDriver().getParam();
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
  public void createXtsTradefedTestJobInfo_shardCount2_pick2Devices() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts --shard-count 2")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardCount(2)
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder().setType("AndroidDevice").build(),
            SubDeviceSpec.newBuilder().setType("AndroidDevice").build());
  }

  @Test
  public void createXtsTradefedTestJobInfo_shardCount3_only2OnlineDevices_pick2Devices()
      throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts --shard-count 3")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardCount(3)
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder().setType("AndroidDevice").build(),
            SubDeviceSpec.newBuilder().setType("AndroidDevice").build());
  }

  @Test
  public void createXtsTradefedTestJobInfo_noOnlineDevices_noJobConfig() throws Exception {
    when(deviceQuerier.queryDevice(any())).thenReturn(DeviceQueryResult.getDefaultInstance());

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isEmpty();
  }

  @Test
  public void createXtsTradefedTestJobInfo_withGivenSerial() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setEnvVars(ImmutableMap.of("env_key1", "env_value1"))
                .setDeviceSerials(ImmutableList.of("device_id_1"))
                .setModuleNames(ImmutableList.of("module1"))
                .setShardCount(2)
                .setExtraArgs(ImmutableList.of("--logcat-on-failure"))
                .build(),
            ImmutableList.of("module1"));

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidDevice")
                .setDimensions(StringMap.newBuilder().putContent("id", "device_id_1"))
                .build());

    // Asserts the driver
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDriver().getName())
        .isEqualTo("XtsTradefedTest");
    String driverParams = tradefedJobInfoOpt.get().jobConfig().getDriver().getParam();
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
  public void createXtsTradefedTestJobInfo_someGivenSerialsNotExist_pickExistingDevicesOnly()
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

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setDeviceSerials(
                    ImmutableList.of("device_id_1", "not_exist_device", "device_id_3"))
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidDevice")
                .setDimensions(StringMap.newBuilder().putContent("id", "device_id_1"))
                .build(),
            SubDeviceSpec.newBuilder()
                .setType("AndroidDevice")
                .setDimensions(StringMap.newBuilder().putContent("id", "device_id_3"))
                .build());
  }

  @Test
  public void createXtsTradefedTestJobInfo_allGivenSerialsNotExist_noJobConfig() throws Exception {
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

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setDeviceSerials(ImmutableList.of("device_id_4", "device_id_5"))
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isEmpty();
  }

  @Test
  public void createXtsTradefedTestJobInfo_withGivenTest() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setEnvVars(ImmutableMap.of("env_key1", "env_value1"))
                .setModuleNames(ImmutableList.of("module1"))
                .setTestName("test1")
                .setShardCount(2)
                .setExtraArgs(ImmutableList.of("--logcat-on-failure"))
                .build(),
            ImmutableList.of("module1"));

    assertThat(tradefedJobInfoOpt).isPresent();

    // Asserts the driver
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDriver().getName())
        .isEqualTo("XtsTradefedTest");
    String driverParams = tradefedJobInfoOpt.get().jobConfig().getDriver().getParam();
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
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config2 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(false))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
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
    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);
    when(configurationUtil.getConfigsFromDirs(any()))
        .thenReturn(ImmutableMap.of("/path/to/config1", config1, "/path/to/config2", config2));
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    doReturn(testSuiteHelper)
        .when(sessionRequestHandlerUtil)
        .getTestSuiteHelper(any(), any(), any());
    when(testSuiteHelper.loadTests(any()))
        .thenReturn(ImmutableMap.of("module1", config1, "module2", config2));

    SessionRequestInfo sessionRequestInfoWithIncludeFilters =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setIncludeFilters(ImmutableList.of("module3 TestClass#TestCase"))
            .build();
    Optional<JobInfo> jobInfoOptWithIncludeFilters =
        sessionRequestHandlerUtil.createXtsTradefedTestJob(sessionRequestInfoWithIncludeFilters);

    SessionRequestInfo sessionRequestInfoWithExcludeFilters =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setExcludeFilters(ImmutableList.of("module1", "module2"))
            .build();
    Optional<JobInfo> jobInfoOptWithExcludeFilters =
        sessionRequestHandlerUtil.createXtsTradefedTestJob(sessionRequestInfoWithExcludeFilters);

    SessionRequestInfo sessionRequestInfoWithMixedFilters =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
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

    SessionRequestInfo sessionRequestInfoWithIncludeFiltersAndAbi =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setIncludeFilters(ImmutableList.of("arm64-v8a module1 TestClass#TestCase"))
            .build();
    assertThat(
            sessionRequestHandlerUtil.createXtsTradefedTestJob(
                sessionRequestInfoWithIncludeFiltersAndAbi))
        .isPresent();

    SessionRequestInfo sessionRequestInfoWithExcludeFiltersAndAbi =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setExcludeFilters(ImmutableList.of("module1", "arm64-v8a module2"))
            .build();
    assertThat(
            sessionRequestHandlerUtil.createXtsTradefedTestJob(
                sessionRequestInfoWithExcludeFiltersAndAbi))
        .isPresent();

    SessionRequestInfo sessionRequestInfoWithIncludeFiltersAndAbiAndParam =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setIncludeFilters(ImmutableList.of("arm64-v8a module1[instant] TestClass#TestCase"))
            .build();
    assertThat(
            sessionRequestHandlerUtil.createXtsTradefedTestJob(
                sessionRequestInfoWithIncludeFiltersAndAbiAndParam))
        .isPresent();
  }

  @Test
  public void createXtsTradefedTestJobInfo_hasTestPlanFile() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<TradefedJobInfo> tradefedJobInfoOpt =
        sessionRequestHandlerUtil.createXtsTradefedTestJobInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setTestPlanFile("ats-file-server::/path/to/test_plan")
                .build(),
            ImmutableList.of());

    assertThat(tradefedJobInfoOpt).isPresent();
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDevice().getSubDeviceSpecList())
        .containsExactly(SubDeviceSpec.newBuilder().setType("AndroidDevice").build());

    // Asserts the driver
    assertThat(tradefedJobInfoOpt.get().jobConfig().getDriver().getName())
        .isEqualTo("XtsTradefedTest");
    String driverParams = tradefedJobInfoOpt.get().jobConfig().getDriver().getParam();
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
            "xts_test_plan_file",
            "ats-file-server::/path/to/test_plan");
  }

  @Test
  public void createXtsNonTradefedJobs() throws Exception {
    Configuration config1 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config2 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    assertThat(jobInfos).hasSize(2);
    verify(moduleConfigurationHelper, times(2)).updateJobInfo(any(), any(), any(), any());
  }

  @Test
  public void createXtsNonTradefedJobs_noMatchedNonTradefedModules() throws Exception {
    Configuration config1 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config2 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
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

    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(
            sessionRequestHandlerUtil.addNonTradefedModuleInfo(
                SessionRequestInfo.builder()
                    .setTestPlan("cts")
                    .setCommandLineArgs("cts")
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
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config2 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
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
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config2 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config3 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module3").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
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

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
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
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
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
                .setCommandLineArgs("cts")
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
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config2 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
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
        .thenReturn(ImmutableMap.of("arm64-v8a module1", config1, "arm64-v8a module2", config2));
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(new SubPlan());

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestInfo.builder()
                .setTestPlan("retry")
                .setCommandLineArgs("retry")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setRetrySessionIndex(0)
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    assertThat(jobInfos).isEmpty();
  }

  @Test
  public void createXtsNonTradefedJobs_retryAtsServerSession_nonTfFailedTests() throws Exception {
    Configuration config1 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config2 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config3 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module3").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
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
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);
    doCallRealMethod()
        .when(certificationSuiteInfoFactory)
        .generateSuiteInfoMap(any(), any(), any());

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestInfo.builder()
                .setTestPlan("retry")
                .setCommandLineArgs("retry")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setRetrySessionId("previous_session_id")
                .setRetryResultDir("/retry/result/dir")
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).params().get("test_case_selector")).isEqualTo("test1 test2");
    assertThat(jobInfos.get(0).params().get("xts_suite_info")).contains("suite_plan=cts");
    assertThat(jobInfos.get(1).params().get("test_case_selector")).isNull();
    assertThat(jobInfos.get(1).params().get("xts_suite_info")).contains("suite_plan=cts");

    ArgumentCaptor<RetryArgs> retryArgsCaptor = ArgumentCaptor.forClass(RetryArgs.class);
    verify(retryGenerator).generateRetrySubPlan(retryArgsCaptor.capture());
    RetryArgs retryArgs = retryArgsCaptor.getValue();
    assertThat(retryArgs.previousSessionId()).hasValue("previous_session_id");
    assertThat(retryArgs.resultsDir().toString()).isEqualTo("/retry/result/dir");
    assertThat(retryArgs.previousSessionIndex().isEmpty()).isTrue();
  }

  @Test
  public void createXtsNonTradefedJobs_retry_nonTfFailedTests() throws Exception {
    Configuration config1 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module1").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config2 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module2").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
            .build();
    Configuration config3 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder().setXtsModule("module3").setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
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
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);
    doCallRealMethod()
        .when(certificationSuiteInfoFactory)
        .generateSuiteInfoMap(any(), any(), any());

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestInfo.builder()
                .setTestPlan("retry")
                .setCommandLineArgs("retry")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setRetrySessionIndex(0)
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).params().get("test_case_selector")).isEqualTo("test1 test2");
    assertThat(jobInfos.get(0).params().get("xts_suite_info")).contains("suite_plan=cts");
    assertThat(jobInfos.get(1).params().get("test_case_selector")).isNull();
    assertThat(jobInfos.get(1).params().get("xts_suite_info")).contains("suite_plan=cts");
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
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts --subplan subplan1")
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
  public void createXtsNonTradefedJobs_subPlanCmdWithExcludeFilter() throws Exception {
    File xtsRootDir = folder.newFolder("xts_root_dir");
    Path subPlansDir = xtsRootDir.toPath().resolve("android-cts/subplans");
    realLocalFileUtil.prepareDir(subPlansDir);
    realLocalFileUtil.copyFileOrDir(SUBPLAN3_XML, subPlansDir.toAbsolutePath().toString());

    Configuration config1 =
        Configuration.newBuilder()
            .setMetadata(
                ConfigurationMetadata.newBuilder()
                    .setXtsModule("HelloWorldTest")
                    .setIsConfigV2(true))
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
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
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts --subplan subplan3")
                .setXtsType("cts")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setSubPlanName("subplan3")
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    assertThat(jobInfos).hasSize(1);

    // The exclude filter in subplan excluded "arm64-v8a HelloWorldTest".
    assertThat(jobInfos.get(0).locator().getName()).endsWith("HelloWorldTest[instant]");
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
            .addDevices(Device.newBuilder().setName("AndroidDevice"))
            .setTest(
                com.google.devtools.mobileharness.platform.android.xts.config.proto
                    .ConfigurationProto.Test.newBuilder()
                    .setClazz("Driver"))
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
    doCallRealMethod().when(localFileUtil).isDirExist(any(String.class));

    SessionRequestInfo sessionRequestInfo =
        sessionRequestHandlerUtil.addNonTradefedModuleInfo(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts --subplan subplan2")
                .setXtsType("cts")
                .setXtsRootDir(xtsRootDir.getAbsolutePath())
                .setSubPlanName("subplan2")
                .build());
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    assertThat(jobInfos).isEmpty();
  }
}
