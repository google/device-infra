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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionHandlerHelper;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil.TradefedJobInfo;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.plan.TestPlanParser;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.ShardingMode;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.DeviceActionConfigObject;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestEnvironment;
import com.google.devtools.mobileharness.infra.ats.server.util.AtsServerSessionUtil;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper.DeviceInfo;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryArgs;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryGenerator;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
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
public final class ServerJobCreatorTest {

  private static final String TEST_DATA_PREFIX =
      "javatests/com/google/devtools/mobileharness/infra/ats/common/jobcreator/testdata/subplans/";

  private static final String SUBPLAN1_XML =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "subplan1.xml");

  private static final String SUBPLAN2_XML =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "subplan2.xml");

  private static final String SUBPLAN3_XML =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "subplan3.xml");
  private static final String ANDROID_XTS_ZIP_PATH = "ats-file-server::/path/to/android_xts.zip";

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Bind @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  @Bind @Mock private AtsServerSessionUtil atsServerSessionUtil;
  @Bind @Mock private LocalFileUtil localFileUtil;
  @Bind @Mock private TestPlanParser testPlanParser;
  @Bind @Mock private PreviousResultLoader previousResultLoader;
  @Bind @Mock private RetryGenerator retryGenerator;
  @Bind @Mock private ModuleShardingArgsGenerator moduleShardingArgsGenerator;

  private LocalFileUtil realLocalFileUtil;
  private String publicDir;
  private String xtsRootDir;

  @Inject private ServerJobCreator jobCreator;

  @Before
  public void setUp() throws Exception {
    publicDir = tmpFolder.newFolder("public_dir").getAbsolutePath();
    flags.setAllFlags(
        ImmutableMap.of(
            "enable_ats_mode",
            "true",
            "use_tf_retry",
            "false",
            "ats_storage_path",
            tmpFolder.getRoot().getAbsolutePath()));
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    realLocalFileUtil = new LocalFileUtil();
    when(sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(any()))
        .thenReturn(
            ImmutableList.of(
                SubDeviceSpec.getDefaultInstance(), SubDeviceSpec.getDefaultInstance()));
    xtsRootDir = publicDir + "/session_session_id/file";
    realLocalFileUtil.prepareParentDir(xtsRootDir);
  }

  @Test
  public void createXtsTradefedTestJob_missingTestEnvironment_throwException() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir)
            .setAndroidXtsZip(ANDROID_XTS_ZIP_PATH)
            .setModuleNames(ImmutableList.of("mock_module"))
            .build();
    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());
    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () ->
                jobCreator.createXtsTradefedTestJobInfo(
                    sessionRequestInfo, ImmutableList.of("mock_module")));
    assertThat(thrown.getErrorId())
        .isEqualTo(InfraErrorId.ATS_SERVER_MISSING_TEST_ENVIRONMENT_ERROR);
  }

  @Test
  public void createXtsTradefedTestJob_moduleSharding() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir)
            .setAtsServerTestEnvironment(TestEnvironment.getDefaultInstance())
            .setAndroidXtsZip(ANDROID_XTS_ZIP_PATH)
            .setShardingMode(ShardingMode.MODULE)
            .build();

    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());
    when(moduleShardingArgsGenerator.generateShardingArgs(eq(sessionRequestInfo), any()))
        .thenReturn(ImmutableSet.of("arg1", "arg2", "arg3"));

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(3);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void createXtsTradefedTestJob() throws Exception {
    String xtsRootDir = publicDir + "/session_session_id/file";
    realLocalFileUtil.prepareParentDir(xtsRootDir);
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setAtsServerTestEnvironment(TestEnvironment.getDefaultInstance())
            .setXtsRootDir(xtsRootDir)
            .setAndroidXtsZip(ANDROID_XTS_ZIP_PATH)
            .setRemoteRunnerFilePathPrefix("ats-file-server::")
            .setModuleNames(ImmutableList.of("mock_module"))
            .setDeviceInfo(
                Optional.of(
                    DeviceInfo.builder()
                        .setDeviceId("mock_device_id")
                        .setSupportedAbiList("arm64-v8a,armeabi-v7a")
                        .build()))
            .build();
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
            "-m mock_module",
            "xts_type",
            "cts",
            "android_xts_zip",
            ANDROID_XTS_ZIP_PATH,
            "xts_test_plan",
            "cts",
            "xts_test_plan_file",
            "ats-file-server::/public_dir/session_session_id/command.xml");
    assertThat(tradefedJobInfoList.get(0).extraJobProperties())
        .containsExactly(
            Job.XTS_TEST_PLAN,
            "cts",
            Job.FILTERED_TRADEFED_MODULES,
            "mock_module",
            Job.DEVICE_SUPPORTED_ABI_LIST,
            "arm64-v8a,armeabi-v7a");
    String commandXmlContent =
        realLocalFileUtil.readFile(Path.of(publicDir, "session_session_id/command.xml"));
    assertThat(commandXmlContent).contains("TF_DEVICE_0");
    assertThat(commandXmlContent).contains("TF_DEVICE_1");
    assertThat(countOccurrences(commandXmlContent, "TF_DEVICE_")).isEqualTo(2);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void createXtsTradefedTestJob_hasTestPlanFileAndLocalMode() throws Exception {
    String xtsRootDir = publicDir + "/session_session_id/file";
    realLocalFileUtil.prepareParentDir(xtsRootDir);
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setAtsServerTestEnvironment(TestEnvironment.getDefaultInstance())
            .setXtsRootDir(xtsRootDir)
            .setAndroidXtsZip(ANDROID_XTS_ZIP_PATH)
            .setModuleNames(ImmutableList.of("mock_module"))
            .build();
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
            "-m mock_module",
            "xts_type",
            "cts",
            "android_xts_zip",
            ANDROID_XTS_ZIP_PATH,
            "xts_test_plan",
            "cts",
            "xts_test_plan_file",
            publicDir + "/session_session_id/command.xml");
    assertThat(tradefedJobInfoList.get(0).extraJobProperties())
        .containsExactly(
            Job.XTS_TEST_PLAN,
            "cts",
            Job.FILTERED_TRADEFED_MODULES,
            "mock_module",
            Job.DEVICE_SUPPORTED_ABI_LIST,
            "");
    String commandXmlContent =
        realLocalFileUtil.readFile(Path.of(publicDir, "session_session_id/command.xml"));
    assertThat(commandXmlContent).contains("TF_DEVICE_0");
    assertThat(commandXmlContent).contains("TF_DEVICE_1");
    assertThat(countOccurrences(commandXmlContent, "TF_DEVICE_")).isEqualTo(2);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void createXtsTradefedTestJob_enableDefaultLogsTrue() throws Exception {
    String xtsRootDir = publicDir + "/session_session_id/file";
    realLocalFileUtil.prepareParentDir(xtsRootDir);
    TestEnvironment testEnvironment = TestEnvironment.getDefaultInstance();
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setAtsServerTestEnvironment(testEnvironment)
            .setXtsRootDir(xtsRootDir)
            .setAndroidXtsZip(ANDROID_XTS_ZIP_PATH)
            .setModuleNames(ImmutableList.of("mock_module"))
            .setEnableDefaultLogs(true)
            .build();
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
        .containsEntry("run_command_args", "-m mock_module --enable-default-logs true");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void createTradefedJobInfo_retryAndSetRemoteFilePrefix_addPrefixToSubplan()
      throws Exception {
    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.setPreviousSessionDeviceBuildFingerprint("[fake device build fingerprint]");
    subPlan.addIncludeFilter("armeabi-v7a ModuleA android.test.Foo#test1");
    File xtsZipPath = folder.newFile("xts_zip.zip");
    File retryResultDir = folder.newFolder("retry_result_dir");
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("retry")
            .setCommandLineArgs("retry --retry 0")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir)
            .setAndroidXtsZip(xtsZipPath.getAbsolutePath())
            .setAtsServerTestEnvironment(TestEnvironment.getDefaultInstance())
            .setRemoteRunnerFilePathPrefix("ats-file-server::")
            .setRetrySessionId("0")
            .setRetryResultDir(retryResultDir.getAbsolutePath())
            .build();
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
            "android_xts_zip",
            xtsZipPath.getAbsolutePath(),
            "xts_test_plan",
            "retry",
            "prev_session_xts_test_plan",
            "cts",
            "xts_test_plan_file",
            "ats-file-server::/public_dir/session_session_id/command.xml");
    assertThat(driverParamsMap.get("subplan_xml")).startsWith("ats-file-server::");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void createXtsTradefedTestJobInfo_retrySubplanWithFilters() throws Exception {
    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.setPreviousSessionDeviceBuildFingerprint("[fake device build fingerprint]");
    subPlan.addIncludeFilter("armeabi-v7a ModuleA android.test.Foo#test1");
    subPlan.addExcludeFilter("armeabi-v7a ModuleB android.test.Foo#test1");
    File xtsZipPath = folder.newFile("xts_zip.zip");
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("retry")
            .setCommandLineArgs("retry --retry 0")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir)
            .setAtsServerTestEnvironment(TestEnvironment.getDefaultInstance())
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
            "android_xts_zip",
            xtsZipPath.getAbsolutePath(),
            "xts_test_plan",
            "retry",
            "prev_session_xts_test_plan",
            "cts",
            "xts_test_plan_file",
            publicDir + "/session_session_id/command.xml");
    assertThat(driverParamsMap.get("subplan_xml"))
        .startsWith(Path.of(xtsRootDir).getParent().toString());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void createXtsTradefedTestJobInfo_addSubPlanXmlPathForRetry() throws Exception {
    SubPlan subPlan = new SubPlan();
    subPlan.setPreviousSessionXtsTestPlan("cts");
    subPlan.setPreviousSessionDeviceBuildFingerprint("[fake device build fingerprint]");
    subPlan.addIncludeFilter("armeabi-v7a ModuleA android.test.Foo#test1");
    File xtsZipPath = folder.newFile("xts_zip.zip");
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("retry")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir)
            .setAndroidXtsZip(xtsZipPath.getAbsolutePath())
            .setAtsServerTestEnvironment(TestEnvironment.getDefaultInstance())
            .setRetrySessionId("previous_session_id")
            .setRetryResultDir("/retry/result/dir")
            .build();
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

    verify(retryGenerator).generateRetrySubPlan(retryArgsCaptor.capture());
    RetryArgs retryArgs = retryArgsCaptor.getValue();
    assertThat(retryArgs.previousSessionId()).hasValue("previous_session_id");
    assertThat(retryArgs.resultsDir().toString()).isEqualTo("/retry/result/dir");
    assertThat(retryArgs.previousSessionIndex().isEmpty()).isTrue();

    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture(), any(), any());
    Map<String, String> driverParamsMap = driverParamsCaptor.getValue();
    assertThat(driverParamsMap).hasSize(6);
    assertThat(driverParamsMap)
        .containsAtLeast(
            "xts_type",
            "cts",
            "android_xts_zip",
            xtsZipPath.getAbsolutePath(),
            "xts_test_plan",
            "retry",
            "prev_session_xts_test_plan",
            "cts",
            "xts_test_plan_file",
            publicDir + "/session_session_id/command.xml");
    assertThat(driverParamsMap.get("subplan_xml"))
        .startsWith(Path.of(xtsRootDir).getParent().toString());
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
            .setAtsServerTestEnvironment(TestEnvironment.getDefaultInstance())
            .setAndroidXtsZip(xtsZipPath.getAbsolutePath())
            .setSubPlanName("subplan1")
            .setModuleNames(ImmutableList.of("CtsAccelerationTestCases", "OtherTestModule"))
            .setDeviceInfo(
                Optional.of(
                    DeviceInfo.builder()
                        .setDeviceId("mock_device_id")
                        .setSupportedAbiList("arm64-v8a,armeabi-v7a")
                        .build()))
            .build();
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    doCallRealMethod().when(localFileUtil).isFileExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).removeFileOrDir(any(Path.class));
    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("CtsAccelerationTestCases", "OtherTestModule"));

    assertThat(tradefedJobInfoList).hasSize(1);
    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture(), any(), any());
    Map<String, String> driverParamsMap = driverParamsCaptor.getValue();
    assertThat(driverParamsMap)
        .containsAtLeast(
            "run_command_args",
            "-m CtsAccelerationTestCases -m OtherTestModule",
            "xts_type",
            "cts",
            "android_xts_zip",
            xtsZipPath.getAbsolutePath(),
            "xts_test_plan",
            "cts");
    assertThat(driverParamsMap.get("subplan_xml"))
        .containsMatch(subPlansDir.resolve("subplan1_tf_auto_gen_\\d+\\.xml").toString());
    assertThat(tradefedJobInfoList.get(0).extraJobProperties())
        .containsExactly(
            Job.XTS_TEST_PLAN,
            "cts",
            // CtsAccelerationTestCases is in the subplan1.xml:
            Job.FILTERED_TRADEFED_MODULES,
            "CtsAccelerationTestCases",
            Job.DEVICE_SUPPORTED_ABI_LIST,
            "arm64-v8a,armeabi-v7a");
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
            .setAtsServerTestEnvironment(TestEnvironment.getDefaultInstance())
            .setAndroidXtsZip(xtsZipPath.getAbsolutePath())
            .setSubPlanName("subplan2")
            .setModuleNames(ImmutableList.of("mock_module"))
            .build();
    ArgumentCaptor<Map<String, String>> driverParamsCaptor = ArgumentCaptor.forClass(Map.class);

    doCallRealMethod().when(localFileUtil).isFileExist(any(Path.class));
    doCallRealMethod().when(localFileUtil).removeFileOrDir(any(Path.class));
    when(sessionRequestHandlerUtil.initializeJobConfig(eq(sessionRequestInfo), any(), any(), any()))
        .thenReturn(JobConfig.getDefaultInstance());

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        jobCreator.createXtsTradefedTestJobInfo(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(tradefedJobInfoList).hasSize(1);

    verify(sessionRequestHandlerUtil)
        .initializeJobConfig(eq(sessionRequestInfo), driverParamsCaptor.capture(), any(), any());
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
        SessionRequestInfo.builder()
            .setTestPlan("retry")
            .setCommandLineArgs("retry")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir)
            .setRetrySessionId("previous_session_id")
            .setRetryResultDir("/retry/result/dir")
            .build();

    when(previousResultLoader.getPrevSessionTestReportProperties(any(Path.class)))
        .thenReturn(Optional.empty());
    when(localFileUtil.isDirExist(Path.of(xtsRootDir))).thenReturn(true);
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(new SubPlan());
    doCallRealMethod()
        .when(sessionRequestHandlerUtil)
        .canCreateNonTradefedJobs(eq(sessionRequestInfo));

    assertThrows(
        MobileHarnessException.class,
        () -> jobCreator.createXtsNonTradefedJobs(sessionRequestInfo));

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

    ImmutableList<JobInfo> unused = jobCreator.createXtsNonTradefedJobs(sessionRequestInfo);

    verify(sessionRequestHandlerUtil)
        .createXtsNonTradefedJobs(eq(sessionRequestInfo), subPlanCaptor.capture(), any());

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

    ImmutableList<JobInfo> unused = jobCreator.createXtsNonTradefedJobs(sessionRequestInfo);

    verify(sessionRequestHandlerUtil)
        .createXtsNonTradefedJobs(eq(sessionRequestInfo), subPlanCaptor.capture(), any());

    assertThat(subPlanCaptor.getValue().getAllIncludeFilters())
        .isEqualTo(subPlan.getAllIncludeFilters());
    assertThat(subPlanCaptor.getValue().getAllExcludeFilters())
        .isEqualTo(subPlan.getAllExcludeFilters());
  }

  @Test
  public void createXtsNonTradefedJobs_noNonTfModulesAndTestsFoundInSubPlan_skipped()
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

    assertThrows(
        MobileHarnessException.class,
        () -> jobCreator.createXtsNonTradefedJobs(sessionRequestInfo));

    verify(sessionRequestHandlerUtil, never()).createXtsNonTradefedJobs(any(), any(), any());
  }

  @Test
  public void reviseRequestInfoForDynamicJob_withoutTestEnvironment_doNothing() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir)
            .build();
    assertThat(jobCreator.reviseRequestInfoForDynamicJob(sessionRequestInfo))
        .isEqualTo(sessionRequestInfo);
  }

  @Test
  public void createXtsTradefedTestJob_dynamicDownloadEnabled_createsStaticJobFirst()
      throws Exception {
    String xtsRootDir = publicDir + "/session_session_id/file";
    realLocalFileUtil.prepareParentDir(xtsRootDir);
    DeviceActionConfigObject resultReporter =
        DeviceActionConfigObject.newBuilder()
            .setType(DeviceActionConfigObject.DeviceActionConfigObjectType.RESULT_REPORTER)
            .build();
    TestEnvironment testEnvironment =
        TestEnvironment.newBuilder()
            .addDeviceActionConfigObjects(
                DeviceActionConfigObject.newBuilder()
                    .setType(DeviceActionConfigObject.DeviceActionConfigObjectType.TARGET_PREPARER)
                    .build())
            .addDeviceActionConfigObjects(resultReporter)
            .build();
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setAtsServerTestEnvironment(testEnvironment)
            .setXtsRootDir(xtsRootDir)
            .setAndroidXtsZip(ANDROID_XTS_ZIP_PATH)
            .setIsXtsDynamicDownloadEnabled(true)
            .build();
    when(sessionRequestHandlerUtil.getStaticMctsModules())
        .thenReturn(ImmutableSet.of("mcts-module"));
    when(sessionRequestHandlerUtil.initializeJobConfig(any(), any(), any(), any()))
        .thenReturn(JobConfig.newBuilder().setName("job_name").build());
    ArgumentCaptor<SessionRequestInfo> sessionRequestInfoCaptor =
        ArgumentCaptor.forClass(SessionRequestInfo.class);
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(
            sessionRequestInfoCaptor.capture(), any()))
        .thenAnswer(
            invocation -> {
              JobInfo jobInfo = mock(JobInfo.class);
              when(jobInfo.properties()).thenReturn(new Properties(new Timing()));
              return jobInfo;
            });
    when(sessionRequestHandlerUtil.getFilteredTradefedModules(any()))
        .thenReturn(ImmutableList.of());

    ImmutableList<JobInfo> jobInfos = jobCreator.createXtsTradefedTestJob(sessionRequestInfo);

    assertThat(jobInfos).hasSize(2);
    assertThat(jobInfos.get(0).properties().get(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME))
        .isEqualTo(XtsConstants.STATIC_XTS_JOB_NAME);
    assertThat(jobInfos.get(1).properties().get(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME))
        .isEqualTo(XtsConstants.DYNAMIC_MCTS_JOB_NAME);
    List<SessionRequestInfo> capturedSrIs = sessionRequestInfoCaptor.getAllValues();
    assertThat(
            capturedSrIs.get(0).atsServerTestEnvironment().get().getDeviceActionConfigObjectsList())
        .hasSize(2);
    assertThat(
            capturedSrIs.get(1).atsServerTestEnvironment().get().getDeviceActionConfigObjectsList())
        .containsExactly(resultReporter);
  }

  @Test
  public void createXtsTradefedTestJob_dynamicDownloadDisabled_createsOneJob() throws Exception {
    String xtsRootDir = publicDir + "/session_session_id/file";
    realLocalFileUtil.prepareParentDir(xtsRootDir);
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setAtsServerTestEnvironment(TestEnvironment.getDefaultInstance())
            .setXtsRootDir(xtsRootDir)
            .setAndroidXtsZip(ANDROID_XTS_ZIP_PATH)
            .setIsXtsDynamicDownloadEnabled(false) // disabled
            .build();
    when(sessionRequestHandlerUtil.getStaticMctsModules())
        .thenReturn(ImmutableSet.of("mcts-module"));
    when(sessionRequestHandlerUtil.initializeJobConfig(any(), any(), any(), any()))
        .thenReturn(JobConfig.newBuilder().setName("job_name").build());
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any(), any()))
        .thenAnswer(
            invocation -> {
              JobInfo jobInfo = mock(JobInfo.class);
              when(jobInfo.properties()).thenReturn(new Properties(new Timing()));
              return jobInfo;
            });
    when(sessionRequestHandlerUtil.getFilteredTradefedModules(any()))
        .thenReturn(ImmutableList.of());

    ImmutableList<JobInfo> jobInfos = jobCreator.createXtsTradefedTestJob(sessionRequestInfo);

    assertThat(jobInfos).hasSize(1);
    verify(sessionRequestHandlerUtil).createXtsTradefedTestJob(eq(sessionRequestInfo), any());
  }

  @Test
  public void reviseRequestInfoForDynamicJob_withTestEnvironment_filterDeviceActions()
      throws Exception {
    DeviceActionConfigObject resultReporter =
        DeviceActionConfigObject.newBuilder()
            .setType(DeviceActionConfigObject.DeviceActionConfigObjectType.RESULT_REPORTER)
            .build();
    TestEnvironment testEnvironment =
        TestEnvironment.newBuilder()
            .addDeviceActionConfigObjects(
                DeviceActionConfigObject.newBuilder()
                    .setType(DeviceActionConfigObject.DeviceActionConfigObjectType.TARGET_PREPARER)
                    .build())
            .addDeviceActionConfigObjects(resultReporter)
            .build();
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir)
            .setAtsServerTestEnvironment(testEnvironment)
            .build();

    SessionRequestInfo updatedSessionRequestInfo =
        jobCreator.reviseRequestInfoForDynamicJob(sessionRequestInfo);
    assertThat(updatedSessionRequestInfo.atsServerTestEnvironment()).isPresent();
    assertThat(
            updatedSessionRequestInfo
                .atsServerTestEnvironment()
                .get()
                .getDeviceActionConfigObjectsList())
        .containsExactly(resultReporter);
  }

  private int countOccurrences(String text, String pattern) {
    int count = 0;
    int index = text.indexOf(pattern);
    while (index != -1) {
      count++;
      index = text.indexOf(pattern, index + 1);
    }
    return count;
  }
}
