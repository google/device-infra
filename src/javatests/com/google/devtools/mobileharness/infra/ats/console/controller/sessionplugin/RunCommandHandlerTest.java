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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionHandlerHelper;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.SessionResultHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.jobcreator.XtsJobCreator;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.DeviceType;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommandState;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger.ParseResult;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportParser;
import com.google.devtools.mobileharness.infra.ats.console.util.verifier.VerifierResultHelper;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.SessionGenDir;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.SessionTempDir;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
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

  private static final String SESSION_PROPERTY_NAME_TIMESTAMP_DIR_NAME = "timestamp_dir_name";

  // Refer to:
  // com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants.RESULT_ZIP_FILENAME_PATTERN
  private static final String TIMESTAMP_DIR_NAME = "2023.06.13_06.27.28.789_1234";

  // For tradefed job
  private static final Path JOB_1_GEN_DIR =
      Path.of(
          RunfilesUtil.getRunfilesLocation(
              "javatests/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin/testdata/runcommand/resultprocessing/job-1_gen/"));
  // For non-tradefed job
  private static final Path JOB_2_GEN_DIR =
      Path.of(
          RunfilesUtil.getRunfilesLocation(
              "javatests/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin/testdata/runcommand/resultprocessing/job-2_gen/"));

  private final LocalFileUtil localFileUtil = new LocalFileUtil();

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private CompatibilityReportMerger compatibilityReportMerger;
  @Bind @Mock private CompatibilityReportParser compatibilityReportParser;
  @Bind @Mock private CompatibilityReportCreator reportCreator;
  @Bind @SessionGenDir private Path sessionGenDir;
  @Bind @SessionTempDir private Path sessionTempDir;
  @Bind @Mock private SessionInfo sessionInfo;
  @Bind @Mock private XtsJobCreator xtsJobCreator;
  @Bind @Mock private PreviousResultLoader previousResultLoader;
  @Bind @Mock private VerifierResultHelper verifierResultHelper;
  @Bind @Mock private Sleeper sleeper;

  private RunCommandHandler runCommandHandler;
  @Inject private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  private SessionResultHandlerUtil sessionResultHandlerUtil;

  @Before
  public void setUp() throws Exception {
    sessionGenDir = folder.newFolder("session_gen_dir").toPath();
    sessionTempDir = folder.newFolder("session_temp_dir").toPath();

    // Sets flags.
    File tmpDir = folder.newFolder("lab_server_tmp_dir");
    flags.setAllFlags(ImmutableMap.of("tmp_dir_root", tmpDir.getAbsolutePath()));

    Injector injector =
        Guice.createInjector(
            BoundFieldModule.of(this),
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Clock.class).toInstance(Clock.systemUTC());
              }
            });
    injector.injectMembers(this);

    // Disable sessionResultHandlerUtil.cleanUpJobGenDirs(...), or we will get "Permission denied"
    // when removing testdata files.
    sessionResultHandlerUtil = spy(injector.getInstance(SessionResultHandlerUtil.class));
    doNothing().when(sessionResultHandlerUtil).cleanUpJobGenDirs(any());

    when(sessionInfo.getSessionProperty(SESSION_PROPERTY_NAME_TIMESTAMP_DIR_NAME))
        .thenReturn(Optional.of(TIMESTAMP_DIR_NAME));

    when(compatibilityReportMerger.mergeResultBundles(anyList(), anyBoolean()))
        .thenReturn(ParseResult.of(Optional.empty(), Optional.empty(), ImmutableSet.of()));

    runCommandHandler =
        new RunCommandHandler(
            localFileUtil,
            sessionRequestHandlerUtil,
            sessionResultHandlerUtil,
            sessionInfo,
            xtsJobCreator,
            verifierResultHelper);
  }

  @Test
  public void generateSessionRequestInfo_success() throws Exception {
    RunCommand command =
        RunCommand.newBuilder()
            .setTestPlan("test")
            .setXtsRootDir("xts_root_dir")
            .setXtsType("cts")
            .setInitialState(RunCommandState.newBuilder().setCommandLineArgs("run cts xxx"))
            .addAllDeviceSerial(ImmutableList.of("device_1", "device_2"))
            .addAllModuleName(ImmutableList.of("module_1", "module_2"))
            .setHtmlInZip(true)
            .addAllIncludeFilter(ImmutableList.of("filter_1", "filter_2"))
            .addAllExcludeFilter(ImmutableList.of("exclude_filter_1", "exclude_filter_2"))
            .addAllModuleArg(ImmutableList.of("module:arg_1:value_1", "module:arg_2:value_2"))
            .addAllExtraArg(ImmutableList.of("arg1", "arg2"))
            .setTestName("test_name")
            .setShardCount(10)
            .setPythonPkgIndexUrl("python_pkg_index_url")
            .setRetrySessionIndex(1)
            .setRetryType("FAILED")
            .setSubPlanName("sub_plan_name")
            .setDeviceType(DeviceType.EMULATOR)
            .setMaxBatteryLevel(99)
            .setMinBatteryLevel(1)
            .setMaxBatteryTemperature(30)
            .setMinSdkLevel(32)
            .setMaxSdkLevel(35)
            .build();

    SessionRequestInfo sessionRequestInfo = runCommandHandler.generateSessionRequestInfo(command);

    assertThat(sessionRequestInfo.testPlan()).isEqualTo("test");
    assertThat(sessionRequestInfo.xtsRootDir()).isEqualTo("xts_root_dir");
    assertThat(sessionRequestInfo.xtsType()).isEqualTo("cts");
    assertThat(sessionRequestInfo.enableModuleParameter()).isTrue();
    assertThat(sessionRequestInfo.enableModuleOptionalParameter()).isFalse();
    assertThat(sessionRequestInfo.commandLineArgs()).isEqualTo("run cts xxx");
    assertThat(sessionRequestInfo.deviceSerials()).containsExactly("device_1", "device_2");
    assertThat(sessionRequestInfo.moduleNames()).containsExactly("module_1", "module_2");
    assertThat(sessionRequestInfo.htmlInZip()).isTrue();
    assertThat(sessionRequestInfo.includeFilters()).containsExactly("filter_1", "filter_2");
    assertThat(sessionRequestInfo.excludeFilters())
        .containsExactly("exclude_filter_1", "exclude_filter_2");
    assertThat(sessionRequestInfo.moduleArgs())
        .containsExactly("module:arg_1:value_1", "module:arg_2:value_2");
    assertThat(sessionRequestInfo.extraArgs()).containsExactly("arg1", "arg2");
    assertThat(sessionRequestInfo.testName()).hasValue("test_name");
    assertThat(sessionRequestInfo.shardCount()).hasValue(10);
    assertThat(sessionRequestInfo.pythonPkgIndexUrl()).hasValue("python_pkg_index_url");
    assertThat(sessionRequestInfo.retrySessionIndex()).hasValue(1);
    assertThat(sessionRequestInfo.retryType()).hasValue(RetryType.FAILED);
    assertThat(sessionRequestInfo.subPlanName()).hasValue("sub_plan_name");
    assertThat(sessionRequestInfo.deviceType())
        .hasValue(SessionRequestHandlerUtil.ANDROID_LOCAL_EMULATOR_TYPE);
    assertThat(sessionRequestInfo.maxBatteryLevel()).hasValue(99);
    assertThat(sessionRequestInfo.minBatteryLevel()).hasValue(1);
    assertThat(sessionRequestInfo.maxBatteryTemperature()).hasValue(30);
    assertThat(sessionRequestInfo.minSdkLevel()).hasValue(32);
    assertThat(sessionRequestInfo.maxSdkLevel()).hasValue(35);
  }

  @Test
  public void generateSessionRequestInfo_strictIncludeFilter() throws Exception {
    RunCommand command =
        RunCommand.newBuilder()
            .setXtsType("cts")
            .setXtsRootDir("xts_root_dir")
            .addAllStrictIncludeFilter(ImmutableList.of("strict_1", "strict_2"))
            .build();

    SessionRequestInfo sessionRequestInfo = runCommandHandler.generateSessionRequestInfo(command);

    assertThat(sessionRequestInfo.strictIncludeFilters()).containsExactly("strict_1", "strict_2");
  }

  private static ImmutableList<JobInfo> createJobInfos() throws MobileHarnessException {
    JobInfo tradefedJobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                JobType.newBuilder()
                    .setDevice("AndroidRealDevice")
                    .setDriver("TradefedTest")
                    .build())
            .setSetting(
                JobSetting.newBuilder()
                    .setGenFileDir(JOB_1_GEN_DIR.toAbsolutePath().toString())
                    .build())
            .setTiming(new Timing())
            .build();
    tradefedJobInfo.properties().add(Job.IS_XTS_TF_JOB, "true");
    tradefedJobInfo.tests().add("1", "test_name");
    tradefedJobInfo
        .tests()
        .getOnly()
        .properties()
        .add(XtsConstants.TRADEFED_INVOCATION_DIR_NAME, "inv_123456");

    JobInfo nonTradefedJobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id_non_tf", "job_name_non_tf"))
            .setType(
                JobType.newBuilder()
                    .setDevice("AndroidRealDevice")
                    .setDriver("MoblyAospTest")
                    .build())
            .setSetting(
                JobSetting.newBuilder()
                    .setGenFileDir(JOB_2_GEN_DIR.toAbsolutePath().toString())
                    .build())
            .setTiming(new Timing())
            .build();
    nonTradefedJobInfo.properties().add(Job.IS_XTS_NON_TF_JOB, "true");
    nonTradefedJobInfo
        .properties()
        .add(SessionHandlerHelper.XTS_MODULE_NAME_PROP, "mobly_test_module_name");
    nonTradefedJobInfo.tests().add("2", "test_name");

    return ImmutableList.of(tradefedJobInfo, nonTradefedJobInfo);
  }

  private ImmutableList<String> listFilePathsForNames(Path dir) throws MobileHarnessException {
    return localFileUtil.listFilePaths(dir, /* recursively= */ false).stream()
        .map(f -> f.getFileName().toString())
        .collect(toImmutableList());
  }

  @Test
  public void handleResultProcessing_copyResultsAndLogsIntoXtsRootDir() throws Exception {
    File xtsRootDir = folder.newFolder(XTS_ROOT_DIR_NAME);
    // run cts
    RunCommand command =
        RunCommand.newBuilder()
            .setXtsType("cts")
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .build();

    when(sessionInfo.getAllJobs()).thenReturn(createJobInfos());
    when(sessionResultHandlerUtil.isSessionCompleted(anyList())).thenReturn(true);

    runCommandHandler.initialize(command);
    runCommandHandler.handleResultProcessing(command, RunCommandState.getDefaultInstance());

    Path resultDir =
        xtsRootDir.toPath().resolve(String.format("android-cts/results/%s", TIMESTAMP_DIR_NAME));
    Path logDir =
        xtsRootDir.toPath().resolve(String.format("android-cts/logs/%s", TIMESTAMP_DIR_NAME));

    assertThat(resultDir.toFile().isDirectory()).isTrue();

    // Lists tradefed test result/log files
    assertThat(listFilePathsForNames(resultDir)).contains("invocation_summary.txt");
    assertThat(listFilePathsForNames(logDir.resolve("inv_123456")))
        .containsExactly("host_adb_log.txt");
    assertThat(listFilePathsForNames(logDir.resolve("inv_123456/TradefedTest_test_1")))
        .containsExactly("xts_tf_output.log", "command_history.txt");

    // Lists non-tradefed test result/log files
    assertThat(
            listFilePathsForNames(
                resultDir.resolve("non-tradefed_results/mobly_test_module_name_test_2")))
        .containsExactly(
            "ats_module_run_result.textproto",
            "device_build_fingerprint.txt",
            "mobly_run_build_attributes.textproto",
            "mobly_run_result_attributes.textproto",
            "test_summary.yaml");
    assertThat(
            listFilePathsForNames(
                logDir.resolve("non-tradefed_logs/mobly_test_module_name_test_2")))
        .containsExactly(
            "ats_module_run_result.textproto",
            "command_history.txt",
            "mobly_command_output.log",
            "device_build_fingerprint.txt",
            "mobly_run_build_attributes.textproto",
            "mobly_run_result_attributes.textproto");
    assertThat(
            listFilePathsForNames(
                logDir.resolve("non-tradefed_logs/mobly_test_module_name_test_2/raw_mobly_logs")))
        .containsExactly("test_summary.yaml");
  }

  @Test
  public void handleResultProcessing_retrySession() throws Exception {
    File xtsRootDir = folder.newFolder(XTS_ROOT_DIR_NAME);
    // run retry --retry 0
    RunCommand command =
        RunCommand.newBuilder()
            .setXtsType("cts")
            .setTestPlan("retry")
            .setRetrySessionIndex(0)
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .build();

    when(sessionInfo.getAllJobs()).thenReturn(createJobInfos());
    when(sessionResultHandlerUtil.isSessionCompleted(anyList())).thenReturn(true);

    Result previousResult = Result.getDefaultInstance();
    when(previousResultLoader.getPrevSessionResultDir(any(), anyInt(), any()))
        .thenReturn(Path.of("/path/to/prev_result_dir"));
    when(previousResultLoader.loadPreviousResult(any(), anyInt(), any()))
        .thenReturn(previousResult);

    runCommandHandler.initialize(command);
    runCommandHandler.handleResultProcessing(command, RunCommandState.getDefaultInstance());

    Path resultDir =
        xtsRootDir.toPath().resolve(String.format("android-cts/results/%s", TIMESTAMP_DIR_NAME));

    assertThat(resultDir.toFile().isDirectory()).isTrue();
  }
}
