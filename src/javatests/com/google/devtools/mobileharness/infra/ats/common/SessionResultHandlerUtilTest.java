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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Summary;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationDescriptor;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationDescriptorMetadata;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationMetadata;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryReportMerger;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class SessionResultHandlerUtilTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

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
      JobType.newBuilder().setDevice("AndroidRealDevice").setDriver("XtsTradefedTest").build();
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
    flags.setAllFlags(ImmutableMap.of("ats_storage_path", "/tmp/ats_storage_path"));
    Mockito.doReturn(true)
        .when(localFileUtil)
        .isDirExist(eq("/tmp/ats_storage_path/genfiles/test_id"));
    // when(localFileUtil.isFileExist(eq("/tmp/ats_storage_path/genfiles/test_id"))).thenReturn(true);
    Mockito.doNothing().when(localFileUtil).removeFileOrDir(anyString());
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
        .isEqualTo(logRootDir.resolve("inv_123/XtsTradefedTest_test_test_id").toString());
  }

  @Test
  public void getTradefedInvocationLogDir_withoutInvocationDirNameProperty() throws Exception {
    when(testProperties.has(XtsConstants.TRADEFED_INVOCATION_DIR_NAME)).thenReturn(false);
    when(jobProperties.getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME))
        .thenReturn(Optional.empty());
    Path logRootDir = folder.getRoot().toPath();

    Path result = sessionResultHandlerUtil.getTradefedInvocationLogDir(testInfo, logRootDir);

    assertThat(result.toString())
        .isEqualTo(logRootDir.resolve("inv_test_id/XtsTradefedTest_test_test_id").toString());
  }

  @Test
  public void getTradefedInvocationLogDir_withDynamicDownloadJobNameProperty() throws Exception {
    when(testProperties.has(XtsConstants.TRADEFED_INVOCATION_DIR_NAME)).thenReturn(false);
    when(jobProperties.getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME))
        .thenReturn(Optional.of("MCTS"));
    Path logRootDir = folder.getRoot().toPath();

    Path result = sessionResultHandlerUtil.getTradefedInvocationLogDir(testInfo, logRootDir);

    assertThat(result.toString())
        .isEqualTo(logRootDir.resolve("inv_mcts_test_id/XtsTradefedTest_test_test_id").toString());
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
        SessionRequestInfo.builder()
            .setTestPlan("testPlan")
            .setCommandLineArgs("commandLineArgs")
            .setXtsRootDir("xtsRootDir")
            .setXtsType("xtsType")
            .setExpandedModules(
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
                    module5Config))
            .build();

    Module.Builder defaultModuleBuilder =
        Module.newBuilder()
            .setPassed(1)
            .setFailedTests(2)
            .addTestCase(
                TestCase.newBuilder()
                    .addTest(
                        com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto
                            .Test.newBuilder()
                            .setName("test1")
                            .setResult("pass"))
                    .addTest(
                        com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto
                            .Test.newBuilder()
                            .setName("test2")
                            .setResult("fail")))
            .addTestCase(
                TestCase.newBuilder()
                    .addTest(
                        com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto
                            .Test.newBuilder()
                            .setName("test3")
                            .setResult("fail")));
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
}
