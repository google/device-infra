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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.common.SessionHandlerHelper;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil.TradefedJobInfo;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.ShardConstants;
import com.google.devtools.mobileharness.infra.ats.common.plan.TestPlanParser;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.ShardingMode;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryArgs;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryGenerator;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
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
public final class ServerJobCreatorTest {

  private static final String TEST_DATA_PREFIX =
      "javatests/com/google/devtools/mobileharness/infra/ats/common/jobcreator/testdata/subplans/";

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

  @Bind @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  @Bind @Mock private LocalFileUtil localFileUtil;
  @Bind @Mock private TestPlanParser testPlanParser;
  @Bind @Mock private PreviousResultLoader previousResultLoader;
  @Bind @Mock private RetryGenerator retryGenerator;

  private LocalFileUtil realLocalFileUtil;
  private TestPlanParser.TestPlanFilter testPlanFilter;

  @Inject private ServerJobCreator jobCreator;

  @Before
  public void setUp() throws Exception {
    setFlags(/* enableAtsMode= */ true, /* useTfRetry= */ false);

    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    testPlanFilter = TestPlanParser.TestPlanFilter.create(ImmutableSet.of(), ImmutableSet.of());
    realLocalFileUtil = new LocalFileUtil();
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
            .setAndroidXtsZip(ANDROID_XTS_ZIP_PATH)
            .build();
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
        .thenReturn(Optional.of(JobConfig.getDefaultInstance()));

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
            "android_xts_zip",
            ANDROID_XTS_ZIP_PATH,
            "xts_test_plan",
            "cts");
    assertThat(tradefedJobInfoList.get(0).extraJobProperties()).isEmpty();
  }

  @Test
  public void createXtsTradefedTestJob_moduleSharding() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setAndroidXtsZip(ANDROID_XTS_ZIP_PATH)
            .setShardingMode(ShardingMode.MODULE)
            .build();

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
        .thenReturn(Optional.of(JobConfig.getDefaultInstance()));

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.copyOf(ShardConstants.SHARED_MODULES));

    assertThat(tradefedJobInfoList)
        .hasSize(ShardConstants.SHARED_MODULES.size() * ShardConstants.MAX_MODULE_SHARDS);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void createXtsTradefedTestJob_hasTestPlanFile() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setAndroidXtsZip(ANDROID_XTS_ZIP_PATH)
            .setTestPlanFile("ats-file-server::/path/to/test_plan")
            .build();
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
        .thenReturn(Optional.of(JobConfig.getDefaultInstance()));

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
            "android_xts_zip",
            ANDROID_XTS_ZIP_PATH,
            "xts_test_plan",
            "cts",
            "xts_test_plan_file",
            "ats-file-server::/path/to/test_plan");
    assertThat(tradefedJobInfoList.get(0).extraJobProperties()).isEmpty();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void createTradefedJobInfo_retryAndSetRemoteFilePrefix_addPrefixToSubplan()
      throws Exception {
    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.addIncludeFilter("armeabi-v7a ModuleA android.test.Foo#test1");
    File xtsRootDir = folder.newFolder("xts_root_dir");
    File xtsZipPath = folder.newFile("xts_zip.zip");
    File retryResultDir = folder.newFolder("retry_result_dir");
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("retry")
            .setCommandLineArgs("retry --retry 0")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .setAndroidXtsZip(xtsZipPath.getAbsolutePath())
            .setRemoteRunnerFilePathPrefix("ats-file-server::")
            .setRetrySessionId("0")
            .setRetryResultDir(retryResultDir.getAbsolutePath())
            .build();
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
        .thenReturn(Optional.of(JobConfig.getDefaultInstance()));
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
            "android_xts_zip",
            xtsZipPath.getAbsolutePath(),
            "xts_test_plan",
            "retry",
            "prev_session_xts_test_plan",
            "cts");
    assertThat(driverParamsMap.get("subplan_xml")).startsWith("ats-file-server::");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void createXtsTradefedTestJobInfo_retrySubplanWithFilters() throws Exception {
    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.addIncludeFilter("armeabi-v7a ModuleA android.test.Foo#test1");
    subPlan.addExcludeFilter("armeabi-v7a ModuleB android.test.Foo#test1");
    File xtsRootDir = folder.newFolder("xts_root_dir");
    File xtsZipPath = folder.newFile("xts_zip.zip");
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("retry")
            .setCommandLineArgs("retry --retry 0")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .setAndroidXtsZip(xtsZipPath.getAbsolutePath())
            .setRetrySessionId("previous_session_id")
            .setRetryResultDir("/retry/result/dir")
            .setIncludeFilters(ImmutableList.of("armeabi-v7a ModuleA android.test.Foo#test1"))
            .setExcludeFilters(ImmutableList.of("armeabi-v7a ModuleB android.test.Foo#test1"))
            .build();
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<RetryArgs> retryArgsCaptor = ArgumentCaptor.forClass(RetryArgs.class);

    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);
    doCallRealMethod().when(localFileUtil).prepareDir(any(Path.class));
    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
        .thenReturn(Optional.of(JobConfig.getDefaultInstance()));

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
            "android_xts_zip",
            xtsZipPath.getAbsolutePath(),
            "xts_test_plan",
            "retry",
            "prev_session_xts_test_plan",
            "cts");
    assertThat(driverParamsMap.get("subplan_xml")).startsWith(xtsRootDir.getParent());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void createXtsTradefedTestJobInfo_addSubPlanXmlPathForRetry() throws Exception {
    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.addIncludeFilter("armeabi-v7a ModuleA android.test.Foo#test1");
    File xtsRootDir = folder.newFolder("xts_root_dir");
    File xtsZipPath = folder.newFile("xts_zip.zip");
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("retry")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .setAndroidXtsZip(xtsZipPath.getAbsolutePath())
            .setRetrySessionId("previous_session_id")
            .setRetryResultDir("/retry/result/dir")
            .build();
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<RetryArgs> retryArgsCaptor = ArgumentCaptor.forClass(RetryArgs.class);

    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);
    doCallRealMethod().when(localFileUtil).prepareDir(any(Path.class));
    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
        .thenReturn(Optional.of(JobConfig.getDefaultInstance()));

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(1);

    verify(retryGenerator).generateRetrySubPlan(retryArgsCaptor.capture());
    RetryArgs retryArgs = retryArgsCaptor.getValue();
    assertThat(retryArgs.previousSessionId()).hasValue("previous_session_id");
    assertThat(retryArgs.resultsDir().toString()).isEqualTo("/retry/result/dir");
    assertThat(retryArgs.previousSessionIndex().isEmpty()).isTrue();

    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture());
    Map<String, String> driverParamsMap = driverParamsCaptor.getValue();
    assertThat(driverParamsMap).hasSize(5);
    assertThat(driverParamsMap)
        .containsAtLeast(
            "xts_type",
            "cts",
            "android_xts_zip",
            xtsZipPath.getAbsolutePath(),
            "xts_test_plan",
            "retry",
            "prev_session_xts_test_plan",
            "cts");
    assertThat(driverParamsMap.get("subplan_xml")).startsWith(xtsRootDir.getParent());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void createXtsTradefedTestJobInfo_addSubPlanXmlPathForSubPlanCommand() throws Exception {
    File xtsRootDir = folder.newFolder("xts_root_dir");
    File xtsZipPath = folder.newFile("xts_zip.zip");
    Path subPlansDir = xtsRootDir.toPath().resolve("android-cts/subplans");
    realLocalFileUtil.prepareDir(subPlansDir);
    realLocalFileUtil.copyFileOrDir(SUBPLAN1_XML, subPlansDir.toAbsolutePath().toString());
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts --subplan subplan1")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .setAndroidXtsZip(xtsZipPath.getAbsolutePath())
            .setSubPlanName("subplan1")
            .build();
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    doCallRealMethod().when(localFileUtil).isFileExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).removeFileOrDir(any(Path.class));
    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
        .thenReturn(Optional.of(JobConfig.getDefaultInstance()));

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture());
    Map<String, String> driverParamsMap = driverParamsCaptor.getValue();
    assertThat(driverParamsMap)
        .containsAtLeast(
            "run_command_args",
            "-m mock_module",
            "xts_type",
            "cts",
            "android_xts_zip",
            xtsZipPath.getAbsolutePath(),
            "xts_test_plan",
            "cts");
    assertThat(driverParamsMap.get("subplan_xml"))
        .isEqualTo(subPlansDir.resolve("subplan1_tf_auto_gen.xml").toString());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void
      createXtsTradefedTestJobInfo_addSubPlanXmlPathForSubPlanCommand_useOriginalSubPlanXml()
          throws Exception {
    File xtsRootDir = folder.newFolder("xts_root_dir");
    File xtsZipPath = folder.newFile("xts_zip.zip");
    Path subPlansDir = xtsRootDir.toPath().resolve("android-cts/subplans");
    realLocalFileUtil.prepareDir(subPlansDir);
    realLocalFileUtil.copyFileOrDir(SUBPLAN2_XML, subPlansDir.toAbsolutePath().toString());
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts --subplan subplan2")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .setAndroidXtsZip(xtsZipPath.getAbsolutePath())
            .setSubPlanName("subplan2")
            .build();
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    doCallRealMethod().when(localFileUtil).isFileExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).removeFileOrDir(any(Path.class));
    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any()))
        .thenReturn(Optional.of(JobConfig.getDefaultInstance()));

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(1);

    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture());
    Map<String, String> driverParamsMap = driverParamsCaptor.getValue();
    assertThat(driverParamsMap)
        .containsAtLeast(
            "run_command_args",
            "-m mock_module",
            "xts_type",
            "cts",
            "android_xts_zip",
            xtsZipPath.getAbsolutePath(),
            "xts_test_plan",
            "cts");
    assertThat(driverParamsMap.get("subplan_xml"))
        .isEqualTo(subPlansDir.resolve("subplan2.xml").toString());
  }

  @Test
  public void createXtsNonTradefedJobs_retryAtsServerSession_nonTfFailedTests() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        // sessionRequestHandlerUtil.addNonTradefedModuleInfo(
        SessionRequestInfo.builder()
            .setTestPlan("retry")
            .setCommandLineArgs("retry")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setRetrySessionId("previous_session_id")
            .setRetryResultDir("/retry/result/dir")
            .build();

    when(previousResultLoader.getPrevSessionTestReportProperties(any(Path.class)))
        .thenReturn(Optional.empty());
    when(localFileUtil.isDirExist(Path.of(XTS_ROOT_DIR_PATH))).thenReturn(true);
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(new SubPlan());
    doCallRealMethod()
        .when(sessionRequestHandlerUtil)
        .canCreateNonTradefedJobs(eq(sessionRequestInfo));

    ImmutableList<JobInfo> unused =
        jobCreator.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    ArgumentCaptor<RetryArgs> retryArgsCaptor = ArgumentCaptor.forClass(RetryArgs.class);
    verify(retryGenerator).generateRetrySubPlan(retryArgsCaptor.capture());
    RetryArgs retryArgs = retryArgsCaptor.getValue();
    assertThat(retryArgs.previousSessionId()).hasValue("previous_session_id");
    assertThat(retryArgs.resultsDir().toString()).isEqualTo("/retry/result/dir");
    assertThat(retryArgs.previousSessionIndex().isEmpty()).isTrue();
  }

  @Test
  public void createXtsNonTradefedJobs_subPlanCmd() throws Exception {
    File xtsRootDir = folder.newFolder("xts_root_dir");
    Path subPlansDir = xtsRootDir.toPath().resolve("android-cts/subplans");
    realLocalFileUtil.prepareDir(subPlansDir);
    realLocalFileUtil.copyFileOrDir(SUBPLAN1_XML, subPlansDir.toAbsolutePath().toString());
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts --subplan subplan1")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .setSubPlanName("subplan1")
            .build();
    ArgumentCaptor<SubPlan> subPlanCaptor = ArgumentCaptor.forClass(SubPlan.class);
    SubPlan subPlan = SessionHandlerHelper.loadSubPlan(Path.of(SUBPLAN1_XML).toFile());

    doCallRealMethod().when(localFileUtil).isFileExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).isDirExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).readFile(any(Path.class));
    doCallRealMethod()
        .when(sessionRequestHandlerUtil)
        .canCreateNonTradefedJobs(eq(sessionRequestInfo));

    ImmutableList<JobInfo> unused =
        jobCreator.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    verify(sessionRequestHandlerUtil)
        .createXtsNonTradefedJobs(eq(sessionRequestInfo), any(), subPlanCaptor.capture(), any());

    assertThat(subPlanCaptor.getValue().getAllIncludeFilters())
        .isEqualTo(subPlan.getAllIncludeFilters());
    assertThat(subPlanCaptor.getValue().getAllExcludeFilters())
        .isEqualTo(subPlan.getAllExcludeFilters());
  }

  @Test
  public void createXtsNonTradefedJobs_subPlanCmdWithExcludeFilter() throws Exception {
    File xtsRootDir = folder.newFolder("xts_root_dir");
    Path subPlansDir = xtsRootDir.toPath().resolve("android-cts/subplans");
    realLocalFileUtil.prepareDir(subPlansDir);
    realLocalFileUtil.copyFileOrDir(SUBPLAN3_XML, subPlansDir.toAbsolutePath().toString());
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts --subplan subplan3")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .setSubPlanName("subplan3")
            .build();
    ArgumentCaptor<SubPlan> subPlanCaptor = ArgumentCaptor.forClass(SubPlan.class);
    SubPlan subPlan = SessionHandlerHelper.loadSubPlan(Path.of(SUBPLAN3_XML).toFile());

    doCallRealMethod().when(localFileUtil).isFileExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).isDirExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).readFile(any(Path.class));
    doCallRealMethod()
        .when(sessionRequestHandlerUtil)
        .canCreateNonTradefedJobs(eq(sessionRequestInfo));

    ImmutableList<JobInfo> unused =
        jobCreator.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    verify(sessionRequestHandlerUtil)
        .createXtsNonTradefedJobs(eq(sessionRequestInfo), any(), subPlanCaptor.capture(), any());

    assertThat(subPlanCaptor.getValue().getAllIncludeFilters())
        .isEqualTo(subPlan.getAllIncludeFilters());
    assertThat(subPlanCaptor.getValue().getAllExcludeFilters())
        .isEqualTo(subPlan.getAllExcludeFilters());
  }

  @Test
  public void createXtsNonTradefedJobs_noNonTfModulesAndTestsFoundinSubPlan_skipped()
      throws Exception {
    File xtsRootDir = folder.newFolder("xts_root_dir");
    Path subPlansDir = xtsRootDir.toPath().resolve("android-cts/subplans");
    realLocalFileUtil.prepareDir(subPlansDir);
    realLocalFileUtil.copyFileOrDir(SUBPLAN2_XML, subPlansDir.toAbsolutePath().toString());
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts --subplan subplan2")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .setSubPlanName("subplan2")
            .build();

    doCallRealMethod().when(localFileUtil).isFileExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).isDirExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).readFile(any(Path.class));
    doCallRealMethod()
        .when(sessionRequestHandlerUtil)
        .canCreateNonTradefedJobs(eq(sessionRequestInfo));

    ImmutableList<JobInfo> unused =
        jobCreator.createXtsNonTradefedJobs(sessionRequestInfo, testPlanFilter);

    verify(sessionRequestHandlerUtil, times(0))
        .createXtsNonTradefedJobs(any(), any(), any(), any());
  }
}
