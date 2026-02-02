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

package com.google.devtools.mobileharness.infra.ats.common.jobcreator;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil.TradefedJobInfo;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.plan.TestPlanParser;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.ShardingMode;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper.DeviceInfo;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader.TradefedResultFilesBundle;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryArgs;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryGenerator;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.api.spec.TradefedTestSpec;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
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
public final class ConsoleJobCreatorTest {

  private static final String XTS_ROOT_DIR_PATH = "/path/to/xts_root_dir";

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  @Bind @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  @Bind @Mock private LocalFileUtil localFileUtil;
  @Bind @Mock private TestPlanParser testPlanParser;
  @Bind @Mock private PreviousResultLoader previousResultLoader;
  @Bind @Mock private RetryGenerator retryGenerator;
  @Bind @Mock private ModuleShardingArgsGenerator moduleShardingArgsGenerator;

  @Inject private ConsoleJobCreator jobCreator;

  @Before
  public void setUp() throws Exception {
    flags.setAllFlags(ImmutableMap.of("enable_ats_mode", "true", "use_tf_retry", "false"));

    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(any()))
        .thenReturn(
            ImmutableList.of(
                SubDeviceSpec.getDefaultInstance(), SubDeviceSpec.getDefaultInstance()));
  }

  @Test
  public void createXtsTradefedTestJob() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setModuleNames(ImmutableList.of("mock_module"))
            .setDeviceInfo(
                Optional.of(
                    DeviceInfo.builder()
                        .setDeviceId("mock_device_id")
                        .setSupportedAbiList("arm64-v8a,armeabi-v7a")
                        .build()))
            .setEnableDefaultLogs(false)
            .build();
    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture(), any(), any());
    assertThat(driverParamsCaptor.getValue())
        .containsExactly(
            "run_command_args",
            "-m mock_module --enable-default-logs false",
            "xts_type",
            "cts",
            "xts_root_dir",
            XTS_ROOT_DIR_PATH,
            "xts_test_plan",
            "cts");
    assertThat(tradefedJobInfoList.get(0).extraJobProperties())
        .containsExactly(
            Job.XTS_TEST_PLAN,
            "cts",
            Job.FILTERED_TRADEFED_MODULES,
            "mock_module",
            Job.DEVICE_SUPPORTED_ABI_LIST,
            "arm64-v8a,armeabi-v7a");
  }

  @Test
  public void createXtsTradefedTestJob_withSubPlan() throws Exception {
    File xtsRootDir = folder.newFolder("xts_root_dir");
    File subplansDir = folder.newFolder("xts_root_dir", "android-cts", "subplans");
    Path subPlanPath = new File(subplansDir, "sub_plan_name.xml").toPath();
    Files.writeString(
        subPlanPath,
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <subPlan>
            <Entry include="armeabi-v7a MockModule2 TestName"/>
        </subPlan>
        """);

    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .setSubPlanName("sub_plan_name")
            .setDeviceInfo(
                Optional.of(
                    DeviceInfo.builder()
                        .setDeviceId("mock_device_id")
                        .setSupportedAbiList("arm64-v8a,armeabi-v7a")
                        .build()))
            .setEnableDefaultLogs(false)
            .build();
    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("MockModule1", "MockModule2"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture(), any(), any());
    Map<String, String> driverParams = driverParamsCaptor.getValue();
    assertThat(driverParams).containsEntry("xts_type", "cts");
    assertThat(driverParams).containsEntry("xts_root_dir", xtsRootDir.getAbsolutePath());
    assertThat(driverParams).containsEntry("xts_test_plan", "cts");
    assertThat(driverParams).containsEntry("run_command_args", "--enable-default-logs false");
    assertThat(driverParams.get("subplan_xml")).endsWith("sub_plan_name.xml");
    assertThat(tradefedJobInfoList.get(0).extraJobProperties())
        .containsExactly(
            Job.XTS_TEST_PLAN,
            "cts",
            Job.FILTERED_TRADEFED_MODULES,
            "MockModule2",
            Job.DEVICE_SUPPORTED_ABI_LIST,
            "arm64-v8a,armeabi-v7a");
  }

  @Test
  public void createXtsTradefedTestJob_withModuleSharding() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setShardingMode(ShardingMode.MODULE)
            .build();

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());
    when(moduleShardingArgsGenerator.generateShardingArgs(eq(sessionRequestInfo), any()))
        .thenReturn(ImmutableSet.of("arg1", "arg2"));

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(2);
  }

  @Test
  public void createXtsTradefedTestJob_withTfModules() throws Exception {
    SessionRequestInfo sessionRequestInfo =
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
            .setEnableDefaultLogs(false)
            .build();
    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(sessionRequestInfo, ImmutableList.of("module1"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture(), any(), any());
    assertThat(driverParamsCaptor.getValue())
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
            "-m module1 --shard-count 2 --enable-default-logs false --logcat-on-failure");
    assertThat(tradefedJobInfoList.get(0).extraJobProperties())
        .containsExactly(
            Job.XTS_TEST_PLAN,
            "cts",
            Job.FILTERED_TRADEFED_MODULES,
            "module1",
            Job.DEVICE_SUPPORTED_ABI_LIST,
            "");
  }

  @Test
  public void createXtsTradefedTestJob_strictIncludeFilters() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setModuleNames(ImmutableList.of("mock_module"))
            .setStrictIncludeFilters(ImmutableList.of("strict_filter_1", "strict_filter_2"))
            .setEnableDefaultLogs(false)
            .build();
    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, /* tfModules= */ ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture(), any(), any());
    assertThat(driverParamsCaptor.getValue())
        .containsEntry(
            "run_command_args",
            "-m mock_module --strict-include-filter \"strict_filter_1\" --strict-include-filter"
                + " \"strict_filter_2\" --enable-default-logs false");
  }

  @Test
  public void createXtsTradefedTestJob_strictIncludeFilters_ignoreOtherFilters() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setModuleNames(ImmutableList.of("mock_module"))
            .setStrictIncludeFilters(ImmutableList.of("strict_filter"))
            .setIncludeFilters(ImmutableList.of("include_filter"))
            .setExcludeFilters(ImmutableList.of("exclude_filter"))
            .setEnableDefaultLogs(false)
            .build();
    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture(), any(), any());
    assertThat(driverParamsCaptor.getValue())
        .containsEntry(
            "run_command_args",
            "-m mock_module --strict-include-filter \"strict_filter\" --enable-default-logs false");
  }

  @Test
  public void createXtsTradefedTestJob_withGivenTest() throws Exception {
    SessionRequestInfo sessionRequestInfo =
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
            .setEnableDefaultLogs(false)
            .build();
    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(sessionRequestInfo, ImmutableList.of("module1"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture(), any(), any());
    assertThat(driverParamsCaptor.getValue())
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
            "-m module1 -t \"test1\" --shard-count 2 --enable-default-logs false"
                + " --logcat-on-failure");
    assertThat(tradefedJobInfoList.get(0).extraJobProperties())
        .containsExactly(
            Job.XTS_TEST_PLAN,
            "cts",
            Job.FILTERED_TRADEFED_MODULES,
            "module1",
            Job.DEVICE_SUPPORTED_ABI_LIST,
            "");
  }

  @Test
  public void createXtsTradefedTestJob_enableDefaultLogsTrue() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setModuleNames(ImmutableList.of("module1"))
            .setEnableDefaultLogs(true)
            .build();
    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(sessionRequestInfo, ImmutableList.of("module1"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture(), any(), any());
    assertThat(driverParamsCaptor.getValue())
        .containsEntry("run_command_args", "-m module1 --enable-default-logs true");
  }

  @Test
  public void createXtsTradefedTestJob_addSubPlanXmlPathForRetry() throws Exception {
    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.setPreviousSessionDeviceBuildFingerprint("[fake device build fingerprint]");
    subPlan.addIncludeFilter("armeabi-v7a ModuleA android.test.Foo#test1");
    File xtsRootDir = folder.newFolder("xts_root_dir");
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("retry")
            .setCommandLineArgs("retry --retry 0")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .setRetrySessionIndex(0)
            .setEnableDefaultLogs(false)
            .build();
    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);
    doCallRealMethod().when(localFileUtil).prepareDir(any(Path.class));

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture(), any(), any());

    Map<String, String> driverParamsMap = driverParamsCaptor.getValue();
    assertThat(driverParamsMap).hasSize(6);
    assertThat(driverParamsMap)
        .containsAtLeast(
            "xts_type",
            "cts",
            "run_command_args",
            "--enable-default-logs false",
            "xts_root_dir",
            xtsRootDir.getAbsolutePath(),
            "xts_test_plan",
            "retry",
            "prev_session_xts_test_plan",
            "cts");
    assertThat(driverParamsMap.get("subplan_xml")).startsWith(xtsRootDir.getAbsolutePath());
  }

  @Test
  public void createXtsTradefedTestJobInfo_retrySubplanWithFilters() throws Exception {
    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.setPreviousSessionDeviceBuildFingerprint("[fake device build fingerprint]");
    subPlan.addIncludeFilter("armeabi-v7a ModuleA android.test.Foo#test1");
    subPlan.addExcludeFilter("armeabi-v7a ModuleB android.test.Foo#test1");
    File xtsRootDir = folder.newFolder("xts_root_dir");
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("retry")
            .setCommandLineArgs("retry --retry 0")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .setRetrySessionIndex(0)
            .setIncludeFilters(ImmutableList.of("armeabi-v7a ModuleA android.test.Foo#test1"))
            .setExcludeFilters(ImmutableList.of("armeabi-v7a ModuleB android.test.Foo#test1"))
            .setEnableDefaultLogs(false)
            .build();
    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<RetryArgs> retryArgsCaptor = ArgumentCaptor.forClass(RetryArgs.class);

    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);
    doCallRealMethod().when(localFileUtil).prepareDir(any(Path.class));
    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(1);
    // Verify generator got correct input.
    verify(retryGenerator).generateRetrySubPlan(retryArgsCaptor.capture());
    assertThat(retryArgsCaptor.getValue().passedInIncludeFilters()).hasSize(1);
    assertThat(retryArgsCaptor.getValue().passedInIncludeFilters().iterator().next().filterString())
        .isEqualTo("armeabi-v7a ModuleA android.test.Foo#test1");
    assertThat(retryArgsCaptor.getValue().passedInExcludeFilters()).hasSize(1);
    assertThat(retryArgsCaptor.getValue().passedInExcludeFilters().iterator().next().filterString())
        .isEqualTo("armeabi-v7a ModuleB android.test.Foo#test1");

    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture(), any(), any());
    Map<String, String> driverParamsMap = driverParamsCaptor.getValue();
    assertThat(driverParamsMap).hasSize(6);
    assertThat(driverParamsMap)
        .containsAtLeast(
            "xts_type",
            "cts",
            "run_command_args",
            "--enable-default-logs false",
            "xts_root_dir",
            xtsRootDir.getAbsolutePath(),
            "xts_test_plan",
            "retry",
            "prev_session_xts_test_plan",
            "cts");
    assertThat(driverParamsMap.get("subplan_xml")).startsWith(xtsRootDir.getAbsolutePath());
  }

  @Test
  public void createXtsNonTradefedJobs_retry_noNonTfFailedTestsFound_throwException()
      throws Exception {
    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);
    doCallRealMethod()
        .when(sessionRequestHandlerUtil)
        .canCreateNonTradefedJobs(any(SessionRequestInfo.class));
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(new SubPlan());
    when(previousResultLoader.getPrevSessionTestReportProperties(any(Path.class), anyInt(), any()))
        .thenReturn(Optional.empty());

    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("retry")
            .setCommandLineArgs("retry")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setRetrySessionIndex(0)
            .build();
    assertThrows(
        MobileHarnessException.class,
        () -> jobCreator.createXtsNonTradefedJobs(sessionRequestInfo));
  }

  @Test
  public void createXtsTradefedTestJob_tfRetryWithModules() throws Exception {
    flags.setAllFlags(ImmutableMap.of("enable_ats_mode", "true", "use_tf_retry", "true"));
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("retry")
            .setCommandLineArgs("retry --retry 0")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setRetrySessionIndex(0)
            .setModuleNames(ImmutableList.of("mock_module[instant]"))
            .setEnableDefaultLogs(false)
            .build();
    when(previousResultLoader.getPrevSessionTestReportProperties(any(Path.class), anyInt(), any()))
        .thenReturn(Optional.empty());
    Path testResultPath = folder.newFile("test_result.xml").toPath();
    Path testRecordPath = folder.newFile("test_record.pb").toPath();
    when(previousResultLoader.getPrevSessionResultFilesBundle(any(Path.class), anyInt(), any()))
        .thenReturn(
            Optional.of(
                TradefedResultFilesBundle.of(testResultPath, ImmutableList.of(testRecordPath))));
    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);
    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<ImmutableMultimap<String, String>> jobFilesCaptor =
        ArgumentCaptor.forClass(ImmutableMultimap.class);

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(
            eq(sessionRequestInfo), driverParamsCaptor.capture(), any(), jobFilesCaptor.capture());
    assertThat(driverParamsCaptor.getValue())
        .containsExactly(
            "run_command_args",
            "-m mock_module[instant] --enable-default-logs false",
            "xts_type",
            "cts",
            "xts_root_dir",
            XTS_ROOT_DIR_PATH,
            "xts_test_plan",
            "retry",
            "prev_session_test_result_xml",
            testResultPath.toString());
    assertThat(jobFilesCaptor.getValue())
        .containsExactly(
            TradefedTestSpec.TAG_PREV_SESSION_TEST_RECORD_PB_FILES, testRecordPath.toString());
    assertThat(tradefedJobInfoList.get(0).extraJobProperties())
        .containsEntry(Job.IS_RUN_RETRY, "true");
  }
}
