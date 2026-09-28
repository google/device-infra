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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Metric;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Summary;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportFormat;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger;
import com.google.devtools.mobileharness.infra.ats.console.result.report.MoblyReportParser.MoblyReportInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationDescriptor;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationDescriptorMetadata;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationMetadata;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommon;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryReportMerger;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfos;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class SessionResultHandlerUtilTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final SetFlags flags = new SetFlags();

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Bind @Mock CompatibilityReportMerger compatibilityReportMerger;
  @Bind @Mock CompatibilityReportCreator reportCreator;
  @Bind @Mock RetryReportMerger retryReportMerger;
  @Bind @Mock PreviousResultLoader previousResultLoader;
  @Bind @Mock SessionInfo sessionInfo;
  @Bind @Spy LocalFileUtil localFileUtil = new LocalFileUtil();

  @Mock private JobInfo jobInfo;
  @Mock private TestInfo testInfo;
  @Mock private TestLocator testLocator;
  @Mock private Properties jobProperties;
  @Mock private Properties testProperties;

  private final JobType jobType =
      JobType.newBuilder().setDevice("AndroidRealDevice").setDriver("TradefedTest").build();
  @Inject private SessionResultHandlerUtil sessionResultHandlerUtil;

  @Before
  public void setup() {
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.locator()).thenReturn(testLocator);
    when(testInfo.properties()).thenReturn(testProperties);
    when(jobInfo.properties()).thenReturn(jobProperties);
    when(jobInfo.type()).thenReturn(jobType);
    when(testLocator.getId()).thenReturn("test_id");
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void copyRetryFiles_success() throws Exception {
    Path oldDir = folder.newFolder("old_dir").toPath();
    Path newDir = folder.newFolder("new_dir").toPath();
    oldDir.resolve("file1.txt").toFile().createNewFile();
    oldDir.resolve("file2.txt").toFile().createNewFile();
    oldDir.resolve("dir1").toFile().mkdir();
    oldDir.resolve("dir1").resolve("file3.txt").toFile().createNewFile();
    oldDir.resolve("dir1").resolve("file4.txt").toFile().createNewFile();
    oldDir.resolve("dir2").toFile().mkdir();
    oldDir.resolve("dir2").resolve("file5.txt").toFile().createNewFile();
    oldDir.resolve("module_reports").toFile().mkdir();

    newDir.resolve("file1.txt").toFile().createNewFile();
    newDir.resolve("dir1").toFile().mkdir();
    newDir.resolve("dir1").resolve("file3.txt").toFile().createNewFile();
    newDir.resolve("dir2").toFile().mkdir();
    newDir.resolve("dir2").resolve("file5.txt").toFile().createNewFile();

    sessionResultHandlerUtil.copyRetryFiles(oldDir.toString(), newDir.toString());
    assertThat(newDir.resolve("file1.txt").toFile().exists()).isTrue();
    assertThat(newDir.resolve("file2.txt").toFile().exists()).isTrue();
    assertThat(newDir.resolve("dir1").toFile().exists()).isTrue();
    assertThat(newDir.resolve("dir1").resolve("file3.txt").toFile().exists()).isTrue();
    assertThat(newDir.resolve("dir1").resolve("file4.txt").toFile().exists()).isTrue();
    assertThat(newDir.resolve("dir2").toFile().exists()).isTrue();
    assertThat(newDir.resolve("dir2").resolve("file5.txt").toFile().exists()).isTrue();
    // The "module_reports" directory is not copied.
    assertThat(newDir.resolve("module_reports").toFile().exists()).isFalse();
  }

  @Test
  public void cleanUpLabGenFileDir_success() throws Exception {
    flags.set("ats_storage_path", "/tmp/ats_storage_path");
    doReturn(true).when(localFileUtil).isDirExist(eq("/tmp/ats_storage_path/genfiles/test_id"));
    // when(localFileUtil.isFileExist(eq("/tmp/ats_storage_path/genfiles/test_id"))).thenReturn(true);
    doNothing().when(localFileUtil).removeFileOrDir(anyString());
    sessionResultHandlerUtil.cleanUpLabGenFileDir(testInfo);
    verify(localFileUtil).removeFileOrDir("/tmp/ats_storage_path/genfiles/test_id");
  }

  @Test
  public void getTradefedInvocationLogDir_withInvocationDirNameProperty() throws Exception {
    when(testProperties.has(XtsConstants.TRADEFED_INVOCATION_DIR_NAME)).thenReturn(true);
    when(testProperties.get(XtsConstants.TRADEFED_INVOCATION_DIR_NAME)).thenReturn("inv_123");
    Path logRootDir = folder.getRoot().toPath();

    Path result = sessionResultHandlerUtil.getTradefedInvocationLogDir(testInfo, logRootDir);

    assertThat(result.toString())
        .isEqualTo(logRootDir.resolve("inv_123/TradefedTest_test_test_id").toString());
  }

  @Test
  public void getTradefedInvocationLogDir_withoutInvocationDirNameProperty() throws Exception {
    when(testProperties.has(XtsConstants.TRADEFED_INVOCATION_DIR_NAME)).thenReturn(false);
    when(jobProperties.getOptional(XtsConstants.XTS_JOB_NAME)).thenReturn(Optional.empty());
    Path logRootDir = folder.getRoot().toPath();

    Path result = sessionResultHandlerUtil.getTradefedInvocationLogDir(testInfo, logRootDir);

    assertThat(result.toString())
        .isEqualTo(logRootDir.resolve("inv_test_id/TradefedTest_test_test_id").toString());
  }

  @Test
  public void getTradefedInvocationLogDir_withXtsJobNameProperty() throws Exception {
    when(testProperties.has(XtsConstants.TRADEFED_INVOCATION_DIR_NAME)).thenReturn(false);
    when(jobProperties.getOptional(XtsConstants.XTS_JOB_NAME)).thenReturn(Optional.of("MCTS"));
    Path logRootDir = folder.getRoot().toPath();

    Path result = sessionResultHandlerUtil.getTradefedInvocationLogDir(testInfo, logRootDir);

    assertThat(result.toString())
        .isEqualTo(logRootDir.resolve("inv_mcts_test_id/TradefedTest_test_test_id").toString());
  }

  @Test
  public void preprocessReport_withFailureLevels() throws Exception {
    Configuration module1Config = createModuleConfigWithFailureLevel("module1", Optional.empty());
    Configuration module2Config =
        createModuleConfigWithFailureLevel("module2", Optional.of("PASSED"));
    Configuration module3Config =
        createModuleConfigWithFailureLevel("module3", Optional.of("WARNING"));
    Configuration module4Config =
        createModuleConfigWithFailureLevel("module4", Optional.of("FAILURE"));
    Configuration module5Config =
        createModuleConfigWithFailureLevel("module5", Optional.of("IGNORED"));
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("testPlan")
                .setCommandLineArgs("commandLineArgs")
                .setXtsRootDir("xtsRootDir")
                .setXtsType("xtsType")
                .putAllExpandedModules(
                    ImmutableMap.of(
                        "module1",
                        module1Config,
                        "module2",
                        module2Config,
                        "module3",
                        module3Config,
                        "module4",
                        module4Config,
                        "module5",
                        module5Config)));

    Module.Builder defaultModuleBuilder =
        Module.newBuilder()
            .setPassed(1)
            .setFailedTests(2)
            .addTestCase(
                TestCase.newBuilder()
                    .addTest(ReportProto.Test.newBuilder().setName("test1").setResult("pass"))
                    .addTest(ReportProto.Test.newBuilder().setName("test2").setResult("fail")))
            .addTestCase(
                TestCase.newBuilder()
                    .addTest(ReportProto.Test.newBuilder().setName("test3").setResult("fail")));
    Result originalReport =
        Result.newBuilder()
            .addModuleInfo(defaultModuleBuilder.setName("module1").build())
            .addModuleInfo(defaultModuleBuilder.setName("module2").build())
            .addModuleInfo(defaultModuleBuilder.setName("module3").build())
            .addModuleInfo(defaultModuleBuilder.setName("module4").build())
            .addModuleInfo(defaultModuleBuilder.setName("module5").build())
            .setSummary(Summary.newBuilder().setPassed(5).setFailed(10).setWarning(0))
            .build();

    Result userfacingReport =
        sessionResultHandlerUtil.preprocessReport(originalReport, sessionRequestInfo);

    assertModuleResultAfterFormat(
        userfacingReport.getModuleInfo(0), "module1", 1, 2, 0, "fail", "fail");
    assertModuleResultAfterFormat(
        userfacingReport.getModuleInfo(1), "module2", 3, 0, 0, "pass", "pass");
    assertModuleResultAfterFormat(
        userfacingReport.getModuleInfo(2), "module3", 1, 0, 2, "warning", "warning");
    assertModuleResultAfterFormat(
        userfacingReport.getModuleInfo(3), "module4", 1, 2, 0, "fail", "fail");
    assertModuleResultAfterFormat(
        userfacingReport.getModuleInfo(4), "module5", 1, 0, 0, "IGNORED", "IGNORED");

    assertThat(userfacingReport.getSummary().getPassed()).isEqualTo(7);
    assertThat(userfacingReport.getSummary().getFailed()).isEqualTo(4);
    assertThat(userfacingReport.getSummary().getWarning()).isEqualTo(2);
  }

  @Test
  public void preprocessReport_withWarningForApproval() throws Exception {
    Configuration module1Config = createModuleConfigWithFailureLevel("module1", Optional.empty());
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("testPlan")
                .setCommandLineArgs("commandLineArgs")
                .setXtsRootDir("xtsRootDir")
                .setXtsType("xtsType")
                .putAllExpandedModules(ImmutableMap.of("module1", module1Config)));

    Module module =
        Module.newBuilder()
            .setName("module1")
            .setPassed(1)
            .setFailedTests(2)
            .setWarningTests(0)
            .addTestCase(
                TestCase.newBuilder()
                    .addTest(ReportProto.Test.newBuilder().setName("test1").setResult("pass"))
                    .addTest(
                        ReportProto.Test.newBuilder()
                            .setName("test2")
                            .setResult("fail")
                            .addMetric(
                                Metric.newBuilder()
                                    .setKey(
                                        CompatibilityReportFormat.WARNING_FOR_APPROVAL_METRIC_KEY)
                                    .build())))
            .addTestCase(
                TestCase.newBuilder()
                    .addTest(ReportProto.Test.newBuilder().setName("test3").setResult("fail")))
            .build();

    Result originalReport =
        Result.newBuilder()
            .addModuleInfo(module)
            .setSummary(Summary.newBuilder().setPassed(1).setFailed(2).setWarning(0))
            .build();

    Result userfacingReport =
        sessionResultHandlerUtil.preprocessReport(originalReport, sessionRequestInfo);

    assertModuleResultAfterFormat(
        userfacingReport.getModuleInfo(0), "module1", 1, 1, 1, "warning", "fail");

    assertThat(userfacingReport.getSummary().getPassed()).isEqualTo(1);
    assertThat(userfacingReport.getSummary().getFailed()).isEqualTo(1);
    assertThat(userfacingReport.getSummary().getWarning()).isEqualTo(1);
  }

  private Configuration createModuleConfigWithFailureLevel(
      String moduleName, Optional<String> failureLevel) {
    Configuration.Builder builder =
        Configuration.newBuilder()
            .setMetadata(ConfigurationMetadata.newBuilder().setXtsModule(moduleName));
    failureLevel.ifPresent(
        level ->
            builder.setConfigDescriptor(
                ConfigurationDescriptor.newBuilder()
                    .putMetadata(
                        "failure_level",
                        ConfigurationDescriptorMetadata.newBuilder().addValue(level).build())));
    return builder.build();
  }

  private void assertModuleResultAfterFormat(
      Module moduleInfo,
      String expectedModuleName,
      int expectedPassed,
      int expectedFailed,
      int expectedWarning,
      String expectedTest2Result,
      String expectedTest3Result) {
    assertThat(moduleInfo.getName()).isEqualTo(expectedModuleName);
    assertThat(moduleInfo.getPassed()).isEqualTo(expectedPassed);
    assertThat(moduleInfo.getFailedTests()).isEqualTo(expectedFailed);
    assertThat(moduleInfo.getWarningTests()).isEqualTo(expectedWarning);
    assertThat(moduleInfo.getTestCase(0).getTest(1).getResult()).isEqualTo(expectedTest2Result);
    assertThat(moduleInfo.getTestCase(1).getTest(0).getResult()).isEqualTo(expectedTest3Result);
  }

  @Test
  public void getExpandedNonTfModuleId_withAbi_withoutParameter() {
    when(jobProperties.getOptional(SessionHandlerHelper.XTS_MODULE_NAME_PROP))
        .thenReturn(Optional.of("module_name"));
    when(jobProperties.get(SessionHandlerHelper.XTS_MODULE_ABI_PROP)).thenReturn("x86_64");
    when(jobProperties.get(SessionHandlerHelper.XTS_MODULE_PARAMETER_PROP)).thenReturn("");

    String result = SessionResultHandlerUtil.getExpandedNonTfModuleId(jobInfo);

    assertThat(result).isEqualTo("x86_64 module_name");
  }

  @Test
  public void getExpandedNonTfModuleId_withoutAbi_withParameter() {
    when(jobProperties.getOptional(SessionHandlerHelper.XTS_MODULE_NAME_PROP))
        .thenReturn(Optional.of("module_name"));
    when(jobProperties.get(SessionHandlerHelper.XTS_MODULE_ABI_PROP)).thenReturn("");
    when(jobProperties.get(SessionHandlerHelper.XTS_MODULE_PARAMETER_PROP)).thenReturn("param1");

    String result = SessionResultHandlerUtil.getExpandedNonTfModuleId(jobInfo);

    assertThat(result).isEqualTo("module_name[param1]");
  }

  @Test
  public void copyTradefedTestLogFiles_skipsRedundantClusterLogs() throws Exception {
    Path genFileDir = folder.newFolder("gen_file_dir").toPath();
    when(testInfo.getGenFileDir()).thenReturn(genFileDir.toString());

    // Create android-<xtsType>-gen-files/logs
    Path genFilesSubDir = genFileDir.resolve("android-cts-gen-files").resolve("logs");
    genFilesSubDir.toFile().mkdirs();
    genFilesSubDir.resolve("latest").toFile().createNewFile();

    // Create a regular timestamped directory under logs
    Path timestampedDir = genFilesSubDir.resolve("2025.01.01_12.00.00");
    timestampedDir.toFile().mkdirs();
    timestampedDir.resolve("inv_1234").toFile().mkdirs();
    timestampedDir.resolve("tradefed.log").toFile().createNewFile();

    // Create redundant ClusterLogSaver items
    Path toolLogsDir = genFileDir.resolve("tool-logs");
    toolLogsDir.toFile().mkdirs();
    toolLogsDir.resolve("stdout.txt").toFile().createNewFile();
    toolLogsDir.resolve("module-configuration_123.xml").toFile().createNewFile();
    toolLogsDir.resolve("subprocess-device_logcat_test_123.txt").toFile().createNewFile();
    toolLogsDir
        .resolve("subprocess-PackageDeviceInfo.deviceinfo.json_123.txt")
        .toFile()
        .createNewFile();

    // Create some other valid non-gen file
    genFileDir.resolve("valid_other_file.txt").toFile().createNewFile();

    Path logRootDir = folder.newFolder("log_root_dir").toPath();

    sessionResultHandlerUtil.copyTradefedTestLogFiles(testInfo, logRootDir, true);

    Path invocationDir = logRootDir.resolve("inv_test_id");
    Path testLogDir = invocationDir.resolve("TradefedTest_test_test_id");

    // Verify tool-logs contents were selectively skipped
    assertThat(testLogDir.resolve("tool-logs").toFile().exists()).isTrue();
    assertThat(testLogDir.resolve("tool-logs").resolve("stdout.txt").toFile().exists()).isTrue();
    assertThat(
            testLogDir
                .resolve("tool-logs")
                .resolve("module-configuration_123.xml")
                .toFile()
                .exists())
        .isFalse();
    assertThat(
            testLogDir
                .resolve("tool-logs")
                .resolve("subprocess-device_logcat_test_123.txt")
                .toFile()
                .exists())
        .isFalse();
    assertThat(
            testLogDir
                .resolve("tool-logs")
                .resolve("subprocess-PackageDeviceInfo.deviceinfo.json_123.txt")
                .toFile()
                .exists())
        .isFalse();

    // Verify valid non-gen file was copied to testLogDir
    assertThat(testLogDir.resolve("valid_other_file.txt").toFile().exists()).isTrue();

    // Verify standard gen-files contents were copied to invocationDir
    assertThat(invocationDir.resolve("tradefed.log").toFile().exists()).isTrue();
  }

  @Test
  public void copyTradefedTestLogFiles_doesNotSkipRedundantClusterLogs() throws Exception {
    Path genFileDir = folder.newFolder("gen_file_dir_2").toPath();
    when(testInfo.getGenFileDir()).thenReturn(genFileDir.toString());

    // Create android-<xtsType>-gen-files/logs
    Path genFilesSubDir = genFileDir.resolve("android-cts-gen-files").resolve("logs");
    genFilesSubDir.toFile().mkdirs();
    Path timestampedDir = genFilesSubDir.resolve("2025.01.01_12.00.00");
    timestampedDir.toFile().mkdirs();
    timestampedDir.resolve("tradefed.log").toFile().createNewFile();

    // Create redundant ClusterLogSaver items
    Path toolLogsDir = genFileDir.resolve("tool-logs");
    toolLogsDir.toFile().mkdirs();
    toolLogsDir.resolve("stdout.txt").toFile().createNewFile();
    toolLogsDir.resolve("module-configuration_123.xml").toFile().createNewFile();

    Path logRootDir = folder.newFolder("log_root_dir_2").toPath();

    sessionResultHandlerUtil.copyTradefedTestLogFiles(testInfo, logRootDir, false);

    Path invocationDir = logRootDir.resolve("inv_test_id");
    Path testLogDir = invocationDir.resolve("TradefedTest_test_test_id");

    // Verify tool-logs contents were NOT skipped
    assertThat(testLogDir.resolve("tool-logs").toFile().exists()).isTrue();
    assertThat(testLogDir.resolve("tool-logs").resolve("stdout.txt").toFile().exists()).isTrue();
    assertThat(
            testLogDir
                .resolve("tool-logs")
                .resolve("module-configuration_123.xml")
                .toFile()
                .exists())
        .isTrue();
    assertThat(invocationDir.resolve("tradefed.log").toFile().exists()).isTrue();
  }

  @Test
  public void handleNonTradefedJobEnd_nonTradefedJobPass_atsModuleRunResultFileWritten()
      throws Exception {
    TestInfos testInfos = mock(TestInfos.class);
    com.google.devtools.mobileharness.api.model.job.out.Result result =
        mock(com.google.devtools.mobileharness.api.model.job.out.Result.class);
    when(jobProperties.getBoolean(Job.IS_XTS_NON_TF_JOB)).thenReturn(Optional.of(true));
    when(jobInfo.tests()).thenReturn(testInfos);
    when(testInfos.getAll()).thenReturn(ImmutableListMultimap.of("test_id", testInfo));
    when(testInfo.resultWithCause()).thenReturn(result);
    when(result.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, /* cause= */ null));
    when(testInfo.getGenFileDir()).thenReturn("/tmp/test_gen_file_dir");

    sessionResultHandlerUtil.handleNonTradefedJobEnd(jobInfo);

    verify(localFileUtil)
        .writeToFile("/tmp/test_gen_file_dir/ats_module_run_result.textproto", "result: PASS\n");
  }

  @Test
  public void handleNonTradefedJobEnd_nonTradefedJobErrorWithCause_atsModuleRunResultFileWritten()
      throws Exception {
    TestInfos testInfos = mock(TestInfos.class);
    com.google.devtools.mobileharness.api.model.job.out.Result result =
        mock(com.google.devtools.mobileharness.api.model.job.out.Result.class);
    when(jobProperties.getBoolean(Job.IS_XTS_NON_TF_JOB)).thenReturn(Optional.of(true));
    when(jobInfo.tests()).thenReturn(testInfos);
    when(testInfos.getAll()).thenReturn(ImmutableListMultimap.of("test_id", testInfo));
    when(testInfo.resultWithCause()).thenReturn(result);
    when(result.get())
        .thenReturn(
            ResultTypeWithCause.create(
                TestResult.ERROR,
                new MobileHarnessException(
                    InfraErrorId.DM_RESERVE_BUSY_DEVICE, "Device is not available.")));
    when(testInfo.getGenFileDir()).thenReturn("/tmp/test_gen_file_dir");

    sessionResultHandlerUtil.handleNonTradefedJobEnd(jobInfo);

    verify(localFileUtil)
        .writeToFile(
            eq("/tmp/test_gen_file_dir/ats_module_run_result.textproto"),
            startsWith(
                "result: ERROR\n"
                    + "cause: \"ERROR[cause=MobileHarnessException: Device is not available."));
  }

  @Test
  public void handleNonTradefedJobEnd_notNonTradefedJob_doesNothing() throws Exception {
    when(jobProperties.getBoolean(Job.IS_XTS_NON_TF_JOB)).thenReturn(Optional.of(false));

    sessionResultHandlerUtil.handleNonTradefedJobEnd(jobInfo);

    verify(localFileUtil, never())
        .writeToFile(eq("/tmp/test_gen_file_dir/ats_module_run_result.textproto"), anyString());
  }

  @Test
  public void copyNonTradefedTestResultFiles_filtersDeviceInfoFiles() throws Exception {
    Path testGenFileDir = folder.newFolder("non_tf_test_gen_files").toPath();
    when(testInfo.getGenFileDir()).thenReturn(testGenFileDir.toString());

    // Create device-info-files containing .deviceinfo.json and .xml files.
    Path deviceInfoDir = testGenFileDir.resolve("device-info-files");
    deviceInfoDir.toFile().mkdirs();
    deviceInfoDir.resolve("PackageDeviceInfo.deviceinfo.json").toFile().createNewFile();
    deviceInfoDir.resolve("VintfDeviceInfo.deviceinfo.json").toFile().createNewFile();
    deviceInfoDir.resolve("device_compatibility_matrix.xml").toFile().createNewFile();
    deviceInfoDir.resolve("device_manifest.xml").toFile().createNewFile();
    deviceInfoDir.resolve("framework_compatibility_matrix.xml").toFile().createNewFile();
    deviceInfoDir.resolve("framework_manifest.xml").toFile().createNewFile();

    // Create report-log-files directory.
    Path reportLogDir = testGenFileDir.resolve("report-log-files");
    reportLogDir.toFile().mkdirs();
    reportLogDir.resolve("report.txt").toFile().createNewFile();

    Path nonTradefedResultDir = folder.newFolder("non_tf_result_dir").toPath();
    Path rootResultDir = folder.newFolder("root_result_dir").toPath();

    sessionResultHandlerUtil.copyNonTradefedTestResultFiles(
        testInfo,
        nonTradefedResultDir,
        rootResultDir,
        "CtsNpuManagerMoblyTestCases",
        /* moduleAbi= */ null,
        /* moduleParameter= */ null);

    // Verify rootResultDir/device-info-files only has *.deviceinfo.json
    Path resultDeviceInfoDir = rootResultDir.resolve("device-info-files");
    assertThat(resultDeviceInfoDir.toFile().exists()).isTrue();
    assertThat(resultDeviceInfoDir.resolve("PackageDeviceInfo.deviceinfo.json").toFile().exists())
        .isTrue();
    assertThat(resultDeviceInfoDir.resolve("VintfDeviceInfo.deviceinfo.json").toFile().exists())
        .isTrue();
    assertThat(resultDeviceInfoDir.resolve("device_compatibility_matrix.xml").toFile().exists())
        .isFalse();
    assertThat(resultDeviceInfoDir.resolve("device_manifest.xml").toFile().exists()).isFalse();
    assertThat(resultDeviceInfoDir.resolve("framework_compatibility_matrix.xml").toFile().exists())
        .isFalse();
    assertThat(resultDeviceInfoDir.resolve("framework_manifest.xml").toFile().exists()).isFalse();

    // Verify other root result dirs (e.g. report-log-files) are copied as-is
    Path resultReportLogDir = rootResultDir.resolve("report-log-files");
    assertThat(resultReportLogDir.toFile().exists()).isTrue();
    assertThat(resultReportLogDir.resolve("report.txt").toFile().exists()).isTrue();
  }

  @Test
  public void copyTradefedTestResultFiles_filtersDeviceInfoFiles() throws Exception {
    Path testGenFileDir = folder.newFolder("tf_test_gen_files").toPath();
    when(testInfo.getGenFileDir()).thenReturn(testGenFileDir.toString());

    // Create device-info-files containing .deviceinfo.json and .xml files in testGenFileDir.
    Path deviceInfoDir = testGenFileDir.resolve("device-info-files");
    deviceInfoDir.toFile().mkdirs();
    deviceInfoDir.resolve("PackageDeviceInfo.deviceinfo.json").toFile().createNewFile();
    deviceInfoDir.resolve("device_compatibility_matrix.xml").toFile().createNewFile();

    Path tmpResultDir = folder.newFolder("tmp_result_dir").toPath();
    Path resultDirInZip = folder.newFolder("result_dir_in_zip").toPath();

    sessionResultHandlerUtil.copyTradefedTestResultFiles(testInfo, tmpResultDir, resultDirInZip);

    Path resultDeviceInfoDir = resultDirInZip.resolve("device-info-files");
    assertThat(resultDeviceInfoDir.toFile().exists()).isTrue();
    assertThat(resultDeviceInfoDir.resolve("PackageDeviceInfo.deviceinfo.json").toFile().exists())
        .isTrue();
    assertThat(resultDeviceInfoDir.resolve("device_compatibility_matrix.xml").toFile().exists())
        .isFalse();
  }

  @Test
  public void processResult_tradefedJobFailedWithoutResultFiles_insertsUnexecutedModules()
      throws Exception {
    flags.set("tmp_dir_root", folder.newFolder("tmp_dir_root_1").toString());
    Path resultDir = folder.newFolder("tf_fail_result_dir").toPath();
    Path logDir = folder.newFolder("tf_fail_log_dir").toPath();

    TestInfos testInfos = mock(TestInfos.class);
    com.google.devtools.mobileharness.api.model.job.out.Result result =
        mock(com.google.devtools.mobileharness.api.model.job.out.Result.class);
    when(jobProperties.getBoolean(Job.IS_XTS_TF_JOB)).thenReturn(Optional.of(true));
    when(jobInfo.tests()).thenReturn(testInfos);
    when(testInfos.getAll()).thenReturn(ImmutableListMultimap.of("test_id", testInfo));
    when(testInfo.resultWithCause()).thenReturn(result);
    when(result.get())
        .thenReturn(
            ResultTypeWithCause.create(
                TestResult.ERROR,
                new MobileHarnessException(
                    InfraErrorId.DM_RESERVE_BUSY_DEVICE, "Device is not available.")));
    when(testProperties.getOptional(
            XtsConstants.TRADEFED_FILTERED_EXPANDED_MODULES_FOR_TEST_PROPERTY_KEY))
        .thenReturn(Optional.of("arm64-v8a CtsModule1,arm64-v8a CtsModule2"));
    when(testInfo.getGenFileDir()).thenReturn(folder.newFolder("test_gen_files_1").toString());
    when(sessionInfo.getSessionId()).thenReturn("session_id");

    sessionResultHandlerUtil.processResult(
        resultDir,
        logDir,
        /* latestResultLink= */ null,
        /* latestLogLink= */ null,
        ImmutableList.of(jobInfo),
        newCtsSessionRequestInfo());

    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<List<Result>> reportListCaptor = ArgumentCaptor.forClass(List.class);
    verify(compatibilityReportMerger)
        .mergeReports(reportListCaptor.capture(), eq(true), anyBoolean());

    List<Result> reportList = reportListCaptor.getValue();
    assertThat(reportList).isNotEmpty();
    ImmutableList<Module> unexecutedModules =
        reportList.stream().flatMap(r -> r.getModuleInfoList().stream()).collect(toImmutableList());
    assertThat(unexecutedModules).hasSize(2);
    assertThat(unexecutedModules.get(0).getName()).isEqualTo("CtsModule1");
    assertThat(unexecutedModules.get(0).getAbi()).isEqualTo("arm64-v8a");
    assertThat(unexecutedModules.get(0).getDone()).isFalse();
    assertThat(unexecutedModules.get(1).getName()).isEqualTo("CtsModule2");
    assertThat(unexecutedModules.get(1).getAbi()).isEqualTo("arm64-v8a");
    assertThat(unexecutedModules.get(1).getDone()).isFalse();
  }

  @Test
  public void processResult_tradefedJobPassedWithoutResultFiles_doesNotInsertUnexecutedModules()
      throws Exception {
    flags.set("tmp_dir_root", folder.newFolder("tmp_dir_root_2").toString());
    Path resultDir = folder.newFolder("tf_pass_result_dir").toPath();
    Path logDir = folder.newFolder("tf_pass_log_dir").toPath();

    TestInfos testInfos = mock(TestInfos.class);
    com.google.devtools.mobileharness.api.model.job.out.Result result =
        mock(com.google.devtools.mobileharness.api.model.job.out.Result.class);
    when(jobProperties.getBoolean(Job.IS_XTS_TF_JOB)).thenReturn(Optional.of(true));
    when(jobInfo.tests()).thenReturn(testInfos);
    when(testInfos.getAll()).thenReturn(ImmutableListMultimap.of("test_id", testInfo));
    when(testInfo.resultWithCause()).thenReturn(result);
    when(result.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, /* cause= */ null));
    when(testProperties.getOptional(
            XtsConstants.TRADEFED_FILTERED_EXPANDED_MODULES_FOR_TEST_PROPERTY_KEY))
        .thenReturn(Optional.of("arm64-v8a CtsModule1,arm64-v8a CtsModule2"));
    when(testInfo.getGenFileDir()).thenReturn(folder.newFolder("test_gen_files_2").toString());
    when(sessionInfo.getSessionId()).thenReturn("session_id");

    sessionResultHandlerUtil.processResult(
        resultDir,
        logDir,
        /* latestResultLink= */ null,
        /* latestLogLink= */ null,
        ImmutableList.of(jobInfo),
        newCtsSessionRequestInfo());

    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<List<Result>> reportListCaptor = ArgumentCaptor.forClass(List.class);
    verify(compatibilityReportMerger)
        .mergeReports(reportListCaptor.capture(), eq(true), anyBoolean());

    List<Result> reportList = reportListCaptor.getValue();
    ImmutableList<Module> unexecutedModules =
        reportList.stream().flatMap(r -> r.getModuleInfoList().stream()).collect(toImmutableList());
    assertThat(unexecutedModules).isEmpty();
  }

  @Test
  public void processResult_onlyPreconditionJob_contributesNothingButCopiesDeviceInfoFiles()
      throws Exception {
    flags.set("tmp_dir_root", folder.newFolder("tmp_dir_root_3").toString());
    Path resultDir = folder.newFolder("setup_only_result_dir").toPath();
    Path logDir = folder.newFolder("setup_only_log_dir").toPath();
    Path testGenFileDir = folder.newFolder("setup_only_test_gen_files").toPath();

    // The SETUP job still produces device-info-files/vintf-files that must reach the result dir.
    Path deviceInfoDir = testGenFileDir.resolve("device-info-files");
    deviceInfoDir.toFile().mkdirs();
    deviceInfoDir.resolve("PackageDeviceInfo.deviceinfo.json").toFile().createNewFile();
    Path vintfDir = testGenFileDir.resolve("vintf-files");
    vintfDir.toFile().mkdirs();
    vintfDir.resolve("device_manifest.xml").toFile().createNewFile();

    setUpNonTradefedJob(
        XtsConstants.SETUP_JOB_NAME, TestResult.PASS, /* cause= */ null, testGenFileDir.toString());
    // Precondition jobs use the job name as their module name.
    when(jobProperties.get(SessionHandlerHelper.XTS_MODULE_NAME_PROP))
        .thenReturn(XtsConstants.SETUP_JOB_NAME);
    // In production a SETUP-only run creates no report at all (mergeReports returns empty for an
    // empty report list), so stub a report here purely to reach createReport and inspect the test
    // report properties it would receive.
    when(compatibilityReportMerger.mergeReports(anyList(), anyBoolean(), anyBoolean()))
        .thenReturn(Optional.of(Result.getDefaultInstance()));

    sessionResultHandlerUtil.processResult(
        resultDir,
        logDir,
        /* latestResultLink= */ null,
        /* latestLogLink= */ null,
        ImmutableList.of(jobInfo),
        newCtsSessionRequestInfo());

    // The synthetic SETUP job must not contribute any report, otherwise an empty test_result.xml
    // with no build/suite metadata is generated when no module matched.
    verify(compatibilityReportMerger, never()).mergeMoblyReports(anyList(), anyBoolean());

    // Nor may it mark the report as having a non-Tradefed module, otherwise a later "run retry"
    // would try to retry modules that never existed.
    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<Map<String, String>> testReportPropertiesCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(reportCreator)
        .createReport(
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            anyString(),
            testReportPropertiesCaptor.capture(),
            any());
    assertThat(testReportPropertiesCaptor.getValue())
        .containsEntry(SuiteCommon.TEST_REPORT_PROPERTY_HAS_NON_TF_MODULE, "false");

    // device-info-files and vintf-files are still copied into the result dir.
    assertThat(
            resultDir
                .resolve("device-info-files")
                .resolve("PackageDeviceInfo.deviceinfo.json")
                .toFile()
                .exists())
        .isTrue();
    assertThat(resultDir.resolve("vintf-files").resolve("device_manifest.xml").toFile().exists())
        .isTrue();
  }

  @Test
  public void processResult_allNonTradefedModulesSkipped_stillAddsSkippedModulesToReport()
      throws Exception {
    flags.set("tmp_dir_root", folder.newFolder("tmp_dir_root_4").toString());
    Path resultDir = folder.newFolder("all_skipped_result_dir").toPath();
    Path logDir = folder.newFolder("all_skipped_log_dir").toPath();
    Path testGenFileDir = folder.newFolder("all_skipped_test_gen_files").toPath();

    setUpNonTradefedJob(
        /* xtsJobName= */ null,
        TestResult.SKIP,
        new MobileHarnessException(
            InfraErrorId.DM_RESERVE_BUSY_DEVICE, "Module is skipped by feature checker."),
        testGenFileDir.toString());
    when(jobProperties.getOptional(SessionHandlerHelper.XTS_MODULE_NAME_PROP))
        .thenReturn(Optional.of("CtsNpuManagerMoblyTestCases"));
    when(jobProperties.get(SessionHandlerHelper.XTS_MODULE_NAME_PROP))
        .thenReturn("CtsNpuManagerMoblyTestCases");
    when(jobProperties.get(SessionHandlerHelper.XTS_MODULE_ABI_PROP)).thenReturn("arm64-v8a");

    sessionResultHandlerUtil.processResult(
        resultDir,
        logDir,
        /* latestResultLink= */ null,
        /* latestLogLink= */ null,
        ImmutableList.of(jobInfo),
        newCtsSessionRequestInfo());

    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<List<Result>> reportListCaptor = ArgumentCaptor.forClass(List.class);
    verify(compatibilityReportMerger)
        .mergeReports(reportListCaptor.capture(), eq(true), anyBoolean());

    ImmutableList<Module> modules =
        reportListCaptor.getValue().stream()
            .flatMap(r -> r.getModuleInfoList().stream())
            .collect(toImmutableList());
    assertThat(modules).hasSize(1);
    assertThat(modules.get(0).getName()).isEqualTo("CtsNpuManagerMoblyTestCases");
    assertThat(modules.get(0).getAbi()).isEqualTo("arm64-v8a");
    assertThat(modules.get(0).getSkipped()).isTrue();

    // The skipped module is a real module, so its parsed report is still merged. That report is
    // what carries the build/suite metadata (suite name, plan, fingerprint, start/end); the
    // skipped-module entries asserted above carry none of it.
    @SuppressWarnings("unchecked") // safe by specification
    ArgumentCaptor<List<MoblyReportInfo>> moblyReportInfosCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(compatibilityReportMerger)
        .mergeMoblyReports(moblyReportInfosCaptor.capture(), anyBoolean());
    assertThat(moblyReportInfosCaptor.getValue()).hasSize(1);
  }

  @Test
  public void isSessionCompleted_onlyPreconditionJobPassed_returnsFalse() throws Exception {
    for (String jobName :
        ImmutableList.of(XtsConstants.SETUP_JOB_NAME, XtsConstants.TEARDOWN_JOB_NAME)) {
      setUpNonTradefedJob(jobName, TestResult.PASS, /* cause= */ null, /* genFileDir= */ null);

      assertThat(sessionResultHandlerUtil.isSessionCompleted(ImmutableList.of(jobInfo))).isFalse();
    }
  }

  @Test
  public void isSessionCompleted_realModuleJobPassed_returnsTrue() throws Exception {
    setUpNonTradefedJob(
        /* xtsJobName= */ null, TestResult.PASS, /* cause= */ null, /* genFileDir= */ null);

    assertThat(sessionResultHandlerUtil.isSessionCompleted(ImmutableList.of(jobInfo))).isTrue();
  }

  /**
   * Stubs {@link #jobInfo} as a non-Tradefed job whose single test ends with {@code testResult}.
   *
   * @param xtsJobName value of the {@code xts_job_name} property, or {@code null} if unset (i.e. a
   *     real module job rather than a SETUP/TEARDOWN precondition job)
   * @param genFileDir the test gen file dir, or {@code null} if the test doesn't need one
   */
  private void setUpNonTradefedJob(
      @Nullable String xtsJobName,
      TestResult testResult,
      @Nullable MobileHarnessException cause,
      @Nullable String genFileDir)
      throws MobileHarnessException {
    TestInfos testInfos = mock(TestInfos.class);
    com.google.devtools.mobileharness.api.model.job.out.Result result =
        mock(com.google.devtools.mobileharness.api.model.job.out.Result.class);
    when(jobProperties.getBoolean(Job.IS_XTS_NON_TF_JOB)).thenReturn(Optional.of(true));
    when(jobProperties.get(XtsConstants.XTS_JOB_NAME)).thenReturn(xtsJobName);
    when(jobInfo.params()).thenReturn(mock(Params.class));
    when(jobInfo.tests()).thenReturn(testInfos);
    when(testInfos.getAll()).thenReturn(ImmutableListMultimap.of("test_id", testInfo));
    when(testInfo.resultWithCause()).thenReturn(result);
    when(jobInfo.resultWithCause()).thenReturn(result);
    when(result.get()).thenReturn(ResultTypeWithCause.create(testResult, cause));
    if (genFileDir != null) {
      when(testInfo.getGenFileDir()).thenReturn(genFileDir);
    }
    when(sessionInfo.getSessionId()).thenReturn("session_id");
  }

  private static SessionRequestInfo newCtsSessionRequestInfo() {
    return SessionRequestInfo.newBuilder()
        .setTestPlan("cts")
        .setCommandLineArgs("cts")
        .setXtsRootDir("xtsRootDir")
        .setXtsType("cts")
        .build();
  }
}
