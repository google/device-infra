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
  public void preprocessReport_withFailureLevelWarning_success() throws Exception {
    Configuration module1Config =
        Configuration.newBuilder()
            .setMetadata(ConfigurationMetadata.newBuilder().setXtsModule("module1"))
            .setConfigDescriptor(
                ConfigurationDescriptor.newBuilder()
                    .putMetadata(
                        "failure_level",
                        ConfigurationDescriptorMetadata.newBuilder().addValue("WARNING").build()))
            .build();
    Configuration module2Config =
        Configuration.newBuilder()
            .setMetadata(ConfigurationMetadata.newBuilder().setXtsModule("module2"))
            .build();
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("testPlan")
            .setCommandLineArgs("commandLineArgs")
            .setXtsRootDir("xtsRootDir")
            .setXtsType("xtsType")
            .setExpandedModules(ImmutableMap.of("module1", module1Config, "module2", module2Config))
            .build();

    Result originalReport =
        Result.newBuilder()
            .addModuleInfo(
                Module.newBuilder()
                    .setName("module1")
                    .setFailedTests(2)
                    .addTestCase(
                        TestCase.newBuilder()
                            .addTest(
                                com.google.devtools.mobileharness.infra.ats.console.result.proto
                                    .ReportProto.Test.newBuilder()
                                    .setName("test1")
                                    .setResult("pass"))
                            .addTest(
                                com.google.devtools.mobileharness.infra.ats.console.result.proto
                                    .ReportProto.Test.newBuilder()
                                    .setName("test2")
                                    .setResult("fail")))
                    .addTestCase(
                        TestCase.newBuilder()
                            .addTest(
                                com.google.devtools.mobileharness.infra.ats.console.result.proto
                                    .ReportProto.Test.newBuilder()
                                    .setName("test3")
                                    .setResult("fail"))))
            .addModuleInfo(
                Module.newBuilder()
                    .setName("module2")
                    .setFailedTests(1)
                    .addTestCase(
                        TestCase.newBuilder()
                            .addTest(
                                com.google.devtools.mobileharness.infra.ats.console.result.proto
                                    .ReportProto.Test.newBuilder()
                                    .setName("test4")
                                    .setResult("pass"))
                            .addTest(
                                com.google.devtools.mobileharness.infra.ats.console.result.proto
                                    .ReportProto.Test.newBuilder()
                                    .setName("test5")
                                    .setResult("fail"))))
            .setSummary(Summary.newBuilder().setPassed(2).setFailed(3).setWarning(0))
            .build();

    Result userfacingReport =
        sessionResultHandlerUtil.preprocessReport(originalReport, sessionRequestInfo);

    assertThat(userfacingReport.getModuleInfo(0).getName()).isEqualTo("module1");
    assertThat(userfacingReport.getModuleInfo(0).getFailedTests()).isEqualTo(0);
    assertThat(userfacingReport.getModuleInfo(0).getWarningTests()).isEqualTo(2);
    assertThat(userfacingReport.getModuleInfo(0).getTestCase(0).getTest(1).getResult())
        .isEqualTo("warning");
    assertThat(userfacingReport.getModuleInfo(0).getTestCase(1).getTest(0).getResult())
        .isEqualTo("warning");

    assertThat(userfacingReport.getModuleInfo(1).getName()).isEqualTo("module2");
    assertThat(userfacingReport.getModuleInfo(1).getFailedTests()).isEqualTo(1);
    assertThat(userfacingReport.getModuleInfo(1).getWarningTests()).isEqualTo(0);
    assertThat(userfacingReport.getModuleInfo(1).getTestCase(0).getTest(1).getResult())
        .isEqualTo("fail");

    assertThat(userfacingReport.getSummary().getFailed()).isEqualTo(1);
    assertThat(userfacingReport.getSummary().getWarning()).isEqualTo(2);
  }
}
