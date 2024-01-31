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

package com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.google.common.truth.Truth8;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.XtsType;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionDetailHolder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLabel;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class RunCommandHandlerTest {

  private static final String XTS_ROOT_DIR_NAME = "xts_root_dir";
  private static final String TIMESTAMP_DIR_NAME = "2023.06.13_06.27.28";

  private static final LocalFileUtil realLocalFileUtil = new LocalFileUtil();

  // For tradefed job
  private static final Path JOB_1_GEN_DIR =
      Paths.get(
          RunfilesUtil.getRunfilesLocation(
              "javatests/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin/testdata/runcommand/resultprocessing/job-1_gen/"));
  // For non-tradefed job
  private static final Path JOB_2_GEN_DIR =
      Paths.get(
          RunfilesUtil.getRunfilesLocation(
              "javatests/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin/testdata/runcommand/resultprocessing/job-2_gen/"));

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private CompatibilityReportMerger compatibilityReportMerger;
  @Bind @Mock private CompatibilityReportCreator reportCreator;

  @Inject private RunCommandHandler runCommandHandler;
  @Inject private SessionRequestHandlerUtil sessionRequestHandlerUtil;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    sessionRequestHandlerUtil = spy(sessionRequestHandlerUtil);
    runCommandHandler = spy(runCommandHandler);
  }

  @Test
  public void handleResultProcessing_copyResultsAndLogsIntoXtsRootDir() throws Exception {
    runCommandHandler = spy(new RunCommandHandler(new LocalFileUtil(), sessionRequestHandlerUtil));
    doNothing().when(sessionRequestHandlerUtil).cleanUpJobGenDirs(any());
    doReturn(TIMESTAMP_DIR_NAME).when(runCommandHandler).getTimestampDirName();

    File xtsRootDir = folder.newFolder(XTS_ROOT_DIR_NAME);
    RunCommand command =
        RunCommand.newBuilder()
            .setXtsType(XtsType.CTS)
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .build();

    JobInfo tradefedJobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                JobType.newBuilder()
                    .setDevice("AndroidRealDevice")
                    .setDriver("XtsTradefedTest")
                    .build())
            .setSetting(
                JobSetting.newBuilder()
                    .setGenFileDir(JOB_1_GEN_DIR.toAbsolutePath().toString())
                    .build())
            .setTiming(new Timing())
            .build();
    tradefedJobInfo.properties().add(SessionRequestHandlerUtil.XTS_TF_JOB_PROP, "true");
    tradefedJobInfo.tests().add("1", "test_name");

    JobInfo nonTradefedJobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id_non_tf", "job_name_non_tf"))
            .setType(
                JobType.newBuilder()
                    .setDevice("AndroidRealDevice")
                    .setDriver("MoblyAospPackageTest")
                    .build())
            .setSetting(
                JobSetting.newBuilder()
                    .setGenFileDir(JOB_2_GEN_DIR.toAbsolutePath().toString())
                    .build())
            .setTiming(new Timing())
            .build();
    nonTradefedJobInfo.properties().add(SessionRequestHandlerUtil.XTS_NON_TF_JOB_PROP, "true");
    nonTradefedJobInfo
        .properties()
        .add(SessionRequestHandlerUtil.XTS_MODULE_NAME_PROP, "mobly_test_module_name");
    nonTradefedJobInfo.tests().add("2", "test_name");

    SessionInfo sessionInfo =
        new SessionInfo(
            new SessionDetailHolder(SessionDetail.getDefaultInstance()),
            SessionPluginLabel.getDefaultInstance(),
            SessionPluginExecutionConfig.getDefaultInstance());
    sessionInfo.addJob(tradefedJobInfo);
    sessionInfo.addJob(nonTradefedJobInfo);

    runCommandHandler.handleResultProcessing(command, sessionInfo);

    assertThat(
            xtsRootDir
                .toPath()
                .resolve(String.format("android-cts/results/%s", TIMESTAMP_DIR_NAME))
                .toFile()
                .isDirectory())
        .isTrue();

    // Verifies tradefed test
    List<Path> newFilesInTradefedResultsDir =
        realLocalFileUtil.listFilePaths(
            xtsRootDir
                .toPath()
                .resolve(
                    String.format(
                        "android-cts/results/%s/tradefed_results/XtsTradefedTest_test_1",
                        TIMESTAMP_DIR_NAME)),
            /* recursively= */ true);
    Truth8.assertThat(newFilesInTradefedResultsDir.stream().map(f -> f.getFileName().toString()))
        .containsExactly("test_result.xml");

    List<Path> newFilesInTradefedLogsDir =
        realLocalFileUtil.listFilePaths(
            xtsRootDir
                .toPath()
                .resolve(
                    String.format(
                        "android-cts/logs/%s/tradefed_logs/XtsTradefedTest_test_1",
                        TIMESTAMP_DIR_NAME)),
            /* recursively= */ true);
    Truth8.assertThat(newFilesInTradefedLogsDir.stream().map(f -> f.getFileName().toString()))
        .containsExactly("host_adb_log.txt", "xts_tf_output.log", "command_history.txt");

    // Verifies non-tradefed test
    List<Path> newFilesInNonTradefedResultsDir =
        realLocalFileUtil.listFilePaths(
            xtsRootDir
                .toPath()
                .resolve(
                    String.format(
                        "android-cts/results/%s/non-tradefed_results/MoblyAospPackageTest_test_2",
                        TIMESTAMP_DIR_NAME)),
            /* recursively= */ true);
    Truth8.assertThat(newFilesInNonTradefedResultsDir.stream().map(f -> f.getFileName().toString()))
        .containsExactly(
            "device_build_fingerprint.txt",
            "mobly_run_build_attributes.textproto",
            "mobly_run_result_attributes.textproto",
            "test_summary.yaml");

    List<Path> newFilesInNonTradefedLogsDir =
        realLocalFileUtil.listFilePaths(
            xtsRootDir
                .toPath()
                .resolve(
                    String.format(
                        "android-cts/logs/%s/non-tradefed_logs/MoblyAospPackageTest_test_2",
                        TIMESTAMP_DIR_NAME)),
            /* recursively= */ true);
    Truth8.assertThat(newFilesInNonTradefedLogsDir.stream().map(f -> f.getFileName().toString()))
        .containsExactly(
            "command_history.txt",
            "mobly_command_output.log",
            "device_build_fingerprint.txt",
            "mobly_run_build_attributes.textproto",
            "mobly_run_result_attributes.textproto",
            "test_summary.yaml");
  }
}
