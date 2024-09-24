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

import static com.google.common.collect.ImmutableList.toImmutableList;
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
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil.TradefedJobInfo;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName.Job;
import com.google.devtools.mobileharness.infra.ats.common.plan.TestPlanParser;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.ShardingMode;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader.TradefedResultFilesBundle;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryArgs;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryGenerator;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import java.io.File;
import java.nio.file.Path;
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
public final class ConsoleJobCreatorTest {

  private static final String XTS_ROOT_DIR_PATH = "/path/to/xts_root_dir";

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Bind @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  @Bind @Mock private LocalFileUtil localFileUtil;
  @Bind @Mock private TestPlanParser testPlanParser;
  @Bind @Mock private PreviousResultLoader previousResultLoader;
  @Bind @Mock private RetryGenerator retryGenerator;
  @Bind @Mock private ModuleShardingArgsGenerator moduleShardingArgsGenerator;

  private TestPlanParser.TestPlanFilter testPlanFilter;

  @Inject private ConsoleJobCreator jobCreator;

  @Before
  public void setUp() throws Exception {
    setFlags(/* enableAtsMode= */ true, /* useTfRetry= */ false);

    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    testPlanFilter = TestPlanParser.TestPlanFilter.create(ImmutableSet.of(), ImmutableSet.of());
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

  @SuppressWarnings("unchecked")
  @Test
  public void createXtsTradefedTestJob() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .build();
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture());
    assertThat(driverParamsCaptor.getValue())
        .containsExactly(
            "run_command_args",
            "-m mock_module",
            "xts_type",
            "cts",
            "xts_root_dir",
            XTS_ROOT_DIR_PATH,
            "xts_test_plan",
            "cts");
    assertThat(tradefedJobInfoList.get(0).extraJobProperties()).isEmpty();
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

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
        .thenReturn(JobConfig.getDefaultInstance());
    when(moduleShardingArgsGenerator.generateShardingArgs(eq(sessionRequestInfo), any()))
        .thenReturn(ImmutableSet.of("arg1", "arg2"));

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(2);
  }

  @SuppressWarnings("unchecked")
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
            .build();
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(sessionRequestInfo, ImmutableList.of("module1"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture());
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
            "-m module1 --shard-count 2 --logcat-on-failure");
    assertThat(tradefedJobInfoList.get(0).extraJobProperties()).isEmpty();
  }

  @SuppressWarnings("unchecked")
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
            .build();
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(sessionRequestInfo, ImmutableList.of("module1"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture());
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
            "-m module1 -t test1 --shard-count 2 --logcat-on-failure");
    assertThat(tradefedJobInfoList.get(0).extraJobProperties()).isEmpty();
  }

  @SuppressWarnings("unchecked")
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
            .build();
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
        .thenReturn(JobConfig.getDefaultInstance());
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);
    doCallRealMethod().when(localFileUtil).prepareDir(any(Path.class));

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture());

    Map<String, String> driverParamsMap = driverParamsCaptor.getValue();
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

  @SuppressWarnings("unchecked")
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
            .build();
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<RetryArgs> retryArgsCaptor = ArgumentCaptor.forClass(RetryArgs.class);

    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);
    doCallRealMethod().when(localFileUtil).prepareDir(any(Path.class));
    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
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
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture());
    Map<String, String> driverParamsMap = driverParamsCaptor.getValue();
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
  public void createXtsNonTradefedJobs_retry_noNonTfFailedTestsFound_throwException()
      throws Exception {
    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);
    doCallRealMethod()
        .when(sessionRequestHandlerUtil)
        .canCreateNonTradefedJobs(any(SessionRequestInfo.class));
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(new SubPlan());
    when(previousResultLoader.getPrevSessionTestReportProperties(any(Path.class), anyInt()))
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
        () -> jobCreator.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter));
  }

  @Test
  public void createXtsTradefedTestJob_tfRetryWithModules() throws Exception {
    setFlags(/* enableAtsMode= */ true, /* useTfRetry= */ true);
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("retry")
            .setCommandLineArgs("retry --retry 0")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setRetrySessionIndex(0)
            .setModuleNames(ImmutableList.of("mock_module[instant]"))
            .build();
    when(previousResultLoader.getPrevSessionTestReportProperties(any(Path.class), anyInt()))
        .thenReturn(Optional.empty());
    Path testResultPath = folder.newFile("test_result.xml").toPath();
    Path testRecordPath = folder.newFile("test_record.pb").toPath();
    when(previousResultLoader.getPrevSessionResultFilesBundle(any(Path.class), anyInt()))
        .thenReturn(
            Optional.of(
                TradefedResultFilesBundle.of(testResultPath, ImmutableList.of(testRecordPath))));
    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture());
    assertThat(driverParamsCaptor.getValue())
        .containsExactly(
            "run_command_args",
            "-m mock_module[instant]",
            "xts_type",
            "cts",
            "xts_root_dir",
            XTS_ROOT_DIR_PATH,
            "xts_test_plan",
            "retry",
            "prev_session_test_result_xml",
            testResultPath.toString(),
            "prev_session_test_record_files",
            String.format("[\"%s\"]", testRecordPath)); // json format
    assertThat(tradefedJobInfoList.get(0).extraJobProperties())
        .containsExactly(Job.IS_RUN_RETRY, "true");
  }
}
