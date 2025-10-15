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

package com.google.devtools.mobileharness.infra.ats.server.sessionplugin;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toProtoDuration;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.SessionResultHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName.Job;
import com.google.devtools.mobileharness.infra.ats.common.XtsTypeLoader;
import com.google.devtools.mobileharness.infra.ats.common.jobcreator.XtsJobCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.ErrorReason;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.NewMultiCommandRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail.RequestState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestContext;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestEnvironment;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResource;
import com.google.devtools.mobileharness.infra.ats.server.sessionplugin.NewMultiCommandRequestHandler.CreateJobsResult;
import com.google.devtools.mobileharness.infra.ats.server.sessionplugin.NewMultiCommandRequestHandler.HandleResultProcessingResult;
import com.google.devtools.mobileharness.infra.ats.server.util.AtsServerSessionUtil;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfo;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfo.TradefedInvocation;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil.XtsTradefedRuntimeInfoFileDetail;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfos;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class NewMultiCommandRequestHandlerTest {
  private static final String ANDROID_XTS_ZIP = "file:///path/to/xts/zip/file.zip";
  private static final String DEFAULT_COMMAND_LINE =
      "cts-plan --module module1 --test test1 --logcat-on-failure --shard-count 2"
          + " --parallel-setup true --parallel-setup-timeout 0";
  private static final String DEVICE_ID_1 = "device_id_1";
  private static final String DEVICE_ID_2 = "device_id_2";
  private static final Path TRADEFED_INVOCATION_LOG_DIR = Path.of("/tradefed_invocation_log_dir");
  private static final ResultTypeWithCause PASS_RESULT =
      ResultTypeWithCause.create(
          com.google.devtools.mobileharness.api.model.proto.Test.TestResult.PASS, null);
  private static final ResultTypeWithCause ERROR_RESULT =
      ResultTypeWithCause.create(
          com.google.devtools.mobileharness.api.model.proto.Test.TestResult.ERROR,
          new MobileHarnessException(BasicErrorId.JOB_TIMEOUT, "test failed with exception"));

  private CommandInfo commandInfo = CommandInfo.getDefaultInstance();
  private NewMultiCommandRequest request = NewMultiCommandRequest.getDefaultInstance();
  private String outputFileUploadPath = "";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  @Bind @Mock private SessionResultHandlerUtil sessionResultHandlerUtil;
  @Bind @Mock private XtsJobCreator xtsJobCreator;
  @Bind @Mock private CommandExecutor commandExecutor;
  @Bind @Mock private Clock clock;
  @Bind @Mock private XtsTypeLoader xtsTypeLoader;
  @Bind @Spy private LocalFileUtil localFileUtil = new LocalFileUtil();
  @Bind @Mock private XtsTradefedRuntimeInfoFileUtil xtsTradefedRuntimeInfoFileUtil;
  @Bind @Mock private Sleeper sleeper;
  @Bind @Mock private AtsServerSessionUtil atsServerSessionUtil;

  @Mock private SessionInfo sessionInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Files files;
  @Mock private TestInfo testInfo;
  @Mock private TestInfos testInfos;
  @Mock private com.google.wireless.qa.mobileharness.shared.model.job.out.Result jobResult;
  @Mock private com.google.wireless.qa.mobileharness.shared.model.job.out.Result testResult;
  @Mock private com.google.devtools.mobileharness.api.model.job.out.Result newJobResult;
  @Mock private com.google.devtools.mobileharness.api.model.job.out.Result newTestResult;
  private final Properties properties = new Properties(new Timing());

  @Captor private ArgumentCaptor<SessionRequestInfo> sessionRequestInfoCaptor;

  @Inject private NewMultiCommandRequestHandler newMultiCommandRequestHandler;

  @Before
  public void setup() throws Exception {
    String publicDir = tmpFolder.newFolder("public_dir").getAbsolutePath();
    flags.setAllFlags(ImmutableMap.of("public_dir", publicDir));
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(sessionInfo.getSessionId()).thenReturn("session_id");
    properties.add(Job.IS_XTS_TF_JOB, "true");
    when(jobInfo.locator()).thenReturn(new JobLocator("job_id", "job_name"));
    when(jobInfo.properties()).thenReturn(properties);
    when(jobInfo.files()).thenReturn(files);
    when(jobInfo.tests()).thenReturn(testInfos);
    when(testInfos.getAll()).thenReturn(ImmutableListMultimap.of("test_id", testInfo));
    when(sessionRequestHandlerUtil.addNonTradefedModuleInfo(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId(DEVICE_ID_1).addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId(DEVICE_ID_2).addType("AndroidOnlineDevice"))
                .build());
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    when(xtsTypeLoader.getXtsType(eq(xtsRootDir), any())).thenReturn("cts");
    commandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine(DEFAULT_COMMAND_LINE)
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_1)
                    .build())
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_2)
                    .build())
            .build();
    outputFileUploadPath = tmpFolder.newFolder("output_file_upload_path").getAbsolutePath();
    request =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(commandInfo)
            .setQueueTimeout(toProtoDuration(Duration.ofSeconds(1000L)))
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl(ANDROID_XTS_ZIP)
                    .setName("android-cts.zip")
                    .build())
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///data/path/to/file1")
                    .setName("test-name-1")
                    .build())
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///data/path/to/file2")
                    .setName("test-name-2")
                    .build())
            .setTestEnvironment(
                TestEnvironment.newBuilder()
                    .setInvocationTimeout(toProtoDuration(Duration.ofSeconds(2000L)))
                    .setOutputFileUploadUrl("file://" + outputFileUploadPath)
                    .putAllEnvVars(ImmutableMap.of("env_key1", "env_value1"))
                    .setUseParallelSetup(true)
                    .build())
            .build();
    when(clock.millis()).thenReturn(1000L);
    when(sessionInfo.getSessionProperty(SessionProperties.PROPERTY_KEY_SERVER_SESSION_LOG_PATH))
        .thenReturn(Optional.of("/path/to/server_session_log.txt"));
    when(sessionResultHandlerUtil.getTradefedInvocationLogDir(any(), any()))
        .thenReturn(TRADEFED_INVOCATION_LOG_DIR);
    doReturn(true)
        .when(localFileUtil)
        .isFileExist(
            eq(TRADEFED_INVOCATION_LOG_DIR.resolve(XtsConstants.TRADEFED_RUNTIME_INFO_FILE_NAME)));
    // Mock the content of the tradefed invocation runtime info log file.
    // The list of tradefed invocations will only be non-empty if there was any invocation error.
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        /* invocations= */ ImmutableList.of(), /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));
    when(sessionRequestHandlerUtil.getSubDeviceSpecListForTradefed(any()))
        .thenReturn(
            ImmutableList.of(
                SubDeviceSpec.getDefaultInstance(), SubDeviceSpec.getDefaultInstance()));
    when(sessionRequestHandlerUtil.getHostIp(any())).thenReturn("127.0.0.1");
  }

  @Test
  public void createTradefedJobs_invalidCommandLine() throws Exception {
    CommandInfo commandInfoWithInvalidCommandLine =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine("cts -m module1 --logcat-on-failure --shard-count")
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_1)
                    .build())
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_2)
                    .build())
            .build();
    request =
        request.toBuilder().clearCommands().addCommands(commandInfoWithInvalidCommandLine).build();
    when(clock.millis()).thenReturn(1000L).thenReturn(2000L).thenReturn(3000L);
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.jobInfos()).isEmpty();

    // Verify request detail.
    assertThat(createJobsResult.errorReason().get()).isEqualTo(ErrorReason.INVALID_REQUEST);
    assertThat(createJobsResult.state()).isEqualTo(RequestState.ERROR);

    // Verify command detail.
    assertThat(createJobsResult.commandDetails()).hasSize(1);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfoWithInvalidCommandLine.getCommandLine().getBytes(UTF_8))
            .toString();
    CommandDetail commandDetail = createJobsResult.commandDetails().get(commandId);
    assertThat(commandDetail.getCommandLine())
        .isEqualTo(commandInfoWithInvalidCommandLine.getCommandLine());
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
  }

  @Test
  public void createTradefedJobs_success() throws Exception {
    when(clock.millis()).thenReturn(1000L).thenReturn(2000L).thenReturn(3000L);
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Trigger the handler.
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.jobInfos()).containsExactly(jobInfo);
    assertThat(createJobsResult.commandDetails()).hasSize(1);
    String commandId = createJobsResult.commandDetails().keySet().iterator().next();
    assertThat(commandId)
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    CommandDetail commandDetail = createJobsResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(properties.get("xts-tradefed-job")).isEqualTo("true");
    assertThat(properties.get("xts_command_id")).isEqualTo(commandId);
    verify(xtsJobCreator).createXtsTradefedTestJob(sessionRequestInfoCaptor.capture());

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = sessionRequestInfoCaptor.getValue();
    assertThat(sessionRequestInfo.testPlan()).isEqualTo("cts-plan");
    assertThat(sessionRequestInfo.moduleNames()).containsExactly("module1");
    assertThat(sessionRequestInfo.testName()).hasValue("test1");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    assertThat(sessionRequestInfo.xtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.xtsType()).isEqualTo("cts");
    assertThat(sessionRequestInfo.androidXtsZip()).hasValue("ats-file-server::" + zipFile);
    assertThat(sessionRequestInfo.startTimeout()).isEqualTo(Duration.ofSeconds(1000));
    assertThat(sessionRequestInfo.jobTimeout()).isEqualTo(Duration.ofSeconds(2000));
    assertThat(sessionRequestInfo.deviceSerials()).containsExactly(DEVICE_ID_1, DEVICE_ID_2);
    assertThat(sessionRequestInfo.shardCount()).hasValue(2);
    assertThat(sessionRequestInfo.envVars()).containsExactly("env_key1", "env_value1");
    assertThat(sessionRequestInfo.remoteRunnerFilePathPrefix()).hasValue("ats-file-server::");

    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
    verify(files).add(eq("test-name-1"), eq("ats-file-server::/path/to/file1"));
    verify(files).add(eq("test-name-2"), eq("ats-file-server::/path/to/file2"));
  }

  @Test
  public void createTradefedJobs_withAcloud_success() throws Exception {
    when(clock.millis()).thenReturn(1000L).thenReturn(2000L).thenReturn(3000L);
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    request =
        request.toBuilder()
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file:///bin/acloud_prebuilt")
                    .setName("acloud_prebuilt")
                    .build())
            .build();
    doReturn(false).when(localFileUtil).isFileOrDirExist(eq("/data/mh_resources/acloud_prebuilt"));
    Mockito.doNothing().when(localFileUtil).prepareDir(eq("/data/mh_resources"));
    Mockito.doNothing().when(localFileUtil).copyFileOrDir(eq("/bin/acloud_prebuilt"), anyString());

    // Trigger the handler.
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.jobInfos()).containsExactly(jobInfo);
    assertThat(createJobsResult.commandDetails()).hasSize(1);
    String commandId = createJobsResult.commandDetails().keySet().iterator().next();
    assertThat(commandId)
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    CommandDetail commandDetail = createJobsResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(properties.get("xts-tradefed-job")).isEqualTo("true");
    assertThat(properties.get("xts_command_id")).isEqualTo(commandId);
    verify(xtsJobCreator).createXtsTradefedTestJob(sessionRequestInfoCaptor.capture());

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = sessionRequestInfoCaptor.getValue();
    assertThat(sessionRequestInfo.testPlan()).isEqualTo("cts-plan");
    assertThat(sessionRequestInfo.moduleNames()).containsExactly("module1");
    assertThat(sessionRequestInfo.testName()).hasValue("test1");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    assertThat(sessionRequestInfo.xtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.xtsType()).isEqualTo("cts");
    assertThat(sessionRequestInfo.androidXtsZip()).hasValue("ats-file-server::" + zipFile);
    assertThat(sessionRequestInfo.startTimeout()).isEqualTo(Duration.ofSeconds(1000));
    assertThat(sessionRequestInfo.jobTimeout()).isEqualTo(Duration.ofSeconds(2000));
    assertThat(sessionRequestInfo.deviceSerials()).containsExactly(DEVICE_ID_1, DEVICE_ID_2);
    assertThat(sessionRequestInfo.shardCount()).hasValue(2);
    assertThat(sessionRequestInfo.envVars()).containsExactly("env_key1", "env_value1");
    assertThat(sessionRequestInfo.remoteRunnerFilePathPrefix()).hasValue("ats-file-server::");

    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
    verify(files).add(eq("test-name-1"), eq("ats-file-server::/path/to/file1"));
    verify(files).add(eq("test-name-2"), eq("ats-file-server::/path/to/file2"));
    verify(localFileUtil).copyFileOrDir(eq("/bin/acloud_prebuilt"), anyString());
    verify(files).add(eq("acloud_prebuilt"), eq("ats-file-server::/mh_resources/acloud_prebuilt"));
  }

  @Test
  public void createTradefedJobs_localMode_success() throws Exception {
    when(atsServerSessionUtil.isLocalMode()).thenReturn(true);
    doReturn("output").when(localFileUtil).unzipFile(anyString(), anyString(), any(Duration.class));
    when(clock.millis()).thenReturn(1000L).thenReturn(2000L).thenReturn(3000L);
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Trigger the handler.
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.jobInfos()).containsExactly(jobInfo);
    assertThat(createJobsResult.commandDetails()).hasSize(1);
    String commandId = createJobsResult.commandDetails().keySet().iterator().next();
    assertThat(commandId)
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    CommandDetail commandDetail = createJobsResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(properties.get("xts-tradefed-job")).isEqualTo("true");
    assertThat(properties.get("xts_command_id")).isEqualTo(commandId);
    verify(xtsJobCreator).createXtsTradefedTestJob(sessionRequestInfoCaptor.capture());

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = sessionRequestInfoCaptor.getValue();
    assertThat(sessionRequestInfo.testPlan()).isEqualTo("cts-plan");
    assertThat(sessionRequestInfo.moduleNames()).containsExactly("module1");
    assertThat(sessionRequestInfo.testName()).hasValue("test1");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    assertThat(sessionRequestInfo.xtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.xtsType()).isEqualTo("cts");
    assertThat(sessionRequestInfo.androidXtsZip()).hasValue("ats-file-server::" + zipFile);
    assertThat(sessionRequestInfo.startTimeout()).isEqualTo(Duration.ofSeconds(1000));
    assertThat(sessionRequestInfo.jobTimeout()).isEqualTo(Duration.ofSeconds(2000));
    assertThat(sessionRequestInfo.deviceSerials()).containsExactly(DEVICE_ID_1, DEVICE_ID_2);
    assertThat(sessionRequestInfo.shardCount()).hasValue(2);
    assertThat(sessionRequestInfo.envVars()).containsExactly("env_key1", "env_value1");
    assertThat(sessionRequestInfo.remoteRunnerFilePathPrefix()).hasValue("ats-file-server::");

    // Verify that handler has unzipped the zip file.
    verify(localFileUtil).unzipFile(zipFile, xtsRootDir, Duration.ofHours(1));

    verify(files).add(eq("test-name-1"), eq("ats-file-server::/path/to/file1"));
    verify(files).add(eq("test-name-2"), eq("ats-file-server::/path/to/file2"));
  }

  @Test
  public void createTradefedJobs_fromRetrySession_success() throws Exception {
    when(clock.millis()).thenReturn(1000L).thenReturn(2000L).thenReturn(3000L);
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    doReturn(Path.of("/path/to/previous_result.pb")).when(localFileUtil).checkFile(any(Path.class));
    String expectedCommandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    String retryCommandLine = "retry --retry 1";
    request =
        request.toBuilder()
            .clearCommands()
            .addCommands(commandInfo.toBuilder().setCommandLine(retryCommandLine))
            .setPrevTestContext(
                TestContext.newBuilder()
                    .setCommandLine(commandInfo.getCommandLine())
                    .addTestResource(
                        TestResource.newBuilder()
                            .setUrl(
                                "file:///path/retry_previous_test_run_id/output/"
                                    + "retry_previous_session_id/"
                                    + expectedCommandId
                                    + "/2024.07.16_15.09.01.972_5844.zip")
                            .setName("2024.07.16_15.09.01.972_5844.zip")
                            .build())
                    .build())
            .build();

    // Trigger the handler.
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.jobInfos()).containsExactly(jobInfo);

    assertThat(createJobsResult.commandDetails()).hasSize(1);
    assertThat(createJobsResult.commandDetails().keySet().iterator().next())
        .isEqualTo(expectedCommandId);
    CommandDetail commandDetail = createJobsResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getCommandLine()).isEqualTo(retryCommandLine);
    assertThat(commandDetail.getId()).isEqualTo(expectedCommandId);
    assertThat(properties.get("xts-tradefed-job")).isEqualTo("true");
    assertThat(properties.get("xts_command_id")).isEqualTo(expectedCommandId);
    verify(xtsJobCreator).createXtsTradefedTestJob(sessionRequestInfoCaptor.capture());

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = sessionRequestInfoCaptor.getValue();
    assertThat(sessionRequestInfo.testPlan()).isEqualTo("retry");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    assertThat(sessionRequestInfo.xtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.xtsType()).isEqualTo("cts");
    assertThat(sessionRequestInfo.androidXtsZip()).hasValue("ats-file-server::" + zipFile);
    assertThat(sessionRequestInfo.startTimeout()).isEqualTo(Duration.ofSeconds(1000));
    assertThat(sessionRequestInfo.jobTimeout()).isEqualTo(Duration.ofSeconds(2000));
    assertThat(sessionRequestInfo.deviceSerials()).containsExactly(DEVICE_ID_1, DEVICE_ID_2);
    assertThat(sessionRequestInfo.envVars()).containsExactly("env_key1", "env_value1");
    assertThat(sessionRequestInfo.retrySessionId()).hasValue("retry_previous_session_id");
    String retryResultDir =
        "/path/retry_previous_test_run_id/output/retry_previous_session_id/" + expectedCommandId;
    assertThat(sessionRequestInfo.retryResultDir()).hasValue(retryResultDir);

    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
    verify(files).add(eq("test-name-1"), eq("ats-file-server::/path/to/file1"));
    verify(files).add(eq("test-name-2"), eq("ats-file-server::/path/to/file2"));
  }

  @Test
  public void createTradefedJobs_retryResultNotFound_runAsNewAttempt() throws Exception {
    when(clock.millis()).thenReturn(1000L).thenReturn(2000L).thenReturn(3000L);
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    MobileHarnessException fakeException =
        new MobileHarnessException(
            BasicErrorId.LOCAL_FILE_IS_DIR, "Failed to find retry result file");
    doThrow(fakeException).when(localFileUtil).checkFile(any(Path.class));
    String expectedCommandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    request =
        request.toBuilder()
            .setPrevTestContext(
                TestContext.newBuilder()
                    .setCommandLine(commandInfo.getCommandLine())
                    .addTestResource(
                        TestResource.newBuilder()
                            .setUrl(
                                "file:///path/retry_previous_test_run_id/output/"
                                    + "retry_previous_session_id/"
                                    + expectedCommandId
                                    + "/2024.07.16_15.09.01.972_5844.zip")
                            .setName("2024.07.16_15.09.01.972_5844.zip")
                            .build())
                    .build())
            .build();

    // Trigger the handler.
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.jobInfos()).containsExactly(jobInfo);

    assertThat(createJobsResult.commandDetails()).hasSize(1);
    String commandId = createJobsResult.commandDetails().keySet().iterator().next();
    assertThat(commandId)
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    CommandDetail commandDetail = createJobsResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(properties.get("xts-tradefed-job")).isEqualTo("true");
    assertThat(properties.get("xts_command_id")).isEqualTo(commandId);
    verify(xtsJobCreator).createXtsTradefedTestJob(sessionRequestInfoCaptor.capture());

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = sessionRequestInfoCaptor.getValue();
    assertThat(sessionRequestInfo.testPlan()).isEqualTo("cts-plan");
    assertThat(sessionRequestInfo.moduleNames()).containsExactly("module1");
    assertThat(sessionRequestInfo.testName()).hasValue("test1");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    assertThat(sessionRequestInfo.xtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.xtsType()).isEqualTo("cts");
    assertThat(sessionRequestInfo.androidXtsZip()).hasValue("ats-file-server::" + zipFile);
    assertThat(sessionRequestInfo.startTimeout()).isEqualTo(Duration.ofSeconds(1000));
    assertThat(sessionRequestInfo.jobTimeout()).isEqualTo(Duration.ofSeconds(2000));
    assertThat(sessionRequestInfo.deviceSerials()).containsExactly(DEVICE_ID_1, DEVICE_ID_2);
    assertThat(sessionRequestInfo.shardCount()).hasValue(2);
    assertThat(sessionRequestInfo.envVars()).containsExactly("env_key1", "env_value1");
    assertThat(sessionRequestInfo.retrySessionId()).isEmpty();
    assertThat(sessionRequestInfo.retryResultDir()).isEmpty();
    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
    verify(files).add(eq("test-name-1"), eq("ats-file-server::/path/to/file1"));
    verify(files).add(eq("test-name-2"), eq("ats-file-server::/path/to/file2"));
  }

  @Test
  public void createTradefedJobs_fromRetryTestRun_success() throws Exception {
    when(clock.millis()).thenReturn(1000L).thenReturn(2000L).thenReturn(3000L);
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    String expectedCommandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    doReturn(Path.of("/path/to/previous_result.pb")).when(localFileUtil).checkFile(any(Path.class));
    request =
        request.toBuilder()
            .setPrevTestContext(
                TestContext.newBuilder()
                    .addTestResource(
                        TestResource.newBuilder()
                            .setUrl(
                                "file:///path/retry_previous_test_run_id/output/"
                                    + "retry_previous_session_id/"
                                    + expectedCommandId
                                    + "/2024.07.16_15.09.01.972_5844.zip")
                            .setName("2024.07.16_15.09.01.972_5844.zip")
                            .build())
                    .build())
            .build();

    // Trigger the handler.
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.jobInfos()).containsExactly(jobInfo);

    assertThat(createJobsResult.commandDetails()).hasSize(1);
    String commandId = createJobsResult.commandDetails().keySet().iterator().next();
    assertThat(commandId).isEqualTo(expectedCommandId);
    CommandDetail commandDetail = createJobsResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(properties.get("xts-tradefed-job")).isEqualTo("true");
    assertThat(properties.get("xts_command_id")).isEqualTo(commandId);
    verify(xtsJobCreator).createXtsTradefedTestJob(sessionRequestInfoCaptor.capture());

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = sessionRequestInfoCaptor.getValue();
    assertThat(sessionRequestInfo.testPlan()).isEqualTo("retry");
    assertThat(sessionRequestInfo.moduleNames()).containsExactly("module1");
    assertThat(sessionRequestInfo.testName()).hasValue("test1");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    assertThat(sessionRequestInfo.xtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.xtsType()).isEqualTo("cts");
    assertThat(sessionRequestInfo.androidXtsZip()).hasValue("ats-file-server::" + zipFile);
    assertThat(sessionRequestInfo.startTimeout()).isEqualTo(Duration.ofSeconds(1000));
    assertThat(sessionRequestInfo.jobTimeout()).isEqualTo(Duration.ofSeconds(2000));
    assertThat(sessionRequestInfo.deviceSerials()).containsExactly(DEVICE_ID_1, DEVICE_ID_2);
    assertThat(sessionRequestInfo.shardCount()).hasValue(2);
    assertThat(sessionRequestInfo.envVars()).containsExactly("env_key1", "env_value1");
    assertThat(sessionRequestInfo.retrySessionId()).hasValue("retry_previous_session_id");
    String retryResultDir =
        "/path/retry_previous_test_run_id/output/retry_previous_session_id/" + commandId;
    assertThat(sessionRequestInfo.retryResultDir()).hasValue(retryResultDir);

    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
    verify(files).add(eq("test-name-1"), eq("ats-file-server::/path/to/file1"));
    verify(files).add(eq("test-name-2"), eq("ats-file-server::/path/to/file2"));
  }

  @Test
  public void createTradefedJobs_jobCreatorThrowsInvalidResourceError() throws Exception {
    when(clock.millis()).thenReturn(1000L).thenReturn(2000L).thenReturn(3000L);
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    when(xtsJobCreator.createXtsTradefedTestJob(any()))
        .thenThrow(
            new MobileHarnessException(
                BasicErrorId.LOCAL_FILE_UNZIP_ERROR, "Failed to unzip file"));

    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.state()).isEqualTo(RequestState.ERROR);
    assertThat(createJobsResult.errorReason()).hasValue(ErrorReason.INVALID_RESOURCE);
  }

  @Test
  public void createTradefedJobs_jobCreatorThrowsInvalidRequestError() throws Exception {
    when(clock.millis()).thenReturn(1000L).thenReturn(2000L).thenReturn(3000L);
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    when(xtsJobCreator.createXtsTradefedTestJob(any()))
        .thenThrow(
            new MobileHarnessException(
                InfraErrorId.ATS_SERVER_INVALID_REQUEST_ERROR, "Invalid request"));

    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.state()).isEqualTo(RequestState.ERROR);
    assertThat(createJobsResult.errorReason()).hasValue(ErrorReason.INVALID_REQUEST);
  }

  @Test
  public void createTradefedJobs_mountOrUnzipAndroidXtsZipFailed_errorWithInvalidResourceError()
      throws Exception {
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    CommandException commandException = Mockito.mock(CommandException.class);
    when(commandException.getMessage()).thenReturn("");
    when(commandExecutor.run(any())).thenThrow(commandException);

    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.jobInfos()).isEmpty();

    // Verify request detail.
    assertThat(createJobsResult.errorReason().get()).isEqualTo(ErrorReason.INVALID_RESOURCE);
    assertThat(createJobsResult.state()).isEqualTo(RequestState.ERROR);

    // Verify command detail.
    assertThat(createJobsResult.commandDetails()).hasSize(1);

    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    CommandDetail commandDetail = createJobsResult.commandDetails().get(commandId);
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
  }

  @Test
  public void createTradefedJobsWithInvalidResource_addResultToSessionOutput() throws Exception {
    CommandInfo commandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine("cts -m module1 --logcat-on-failure")
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_1)
                    .build())
            .build();
    NewMultiCommandRequest request =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(commandInfo)
            .addTestResources(
                TestResource.newBuilder().setUrl(ANDROID_XTS_ZIP).setName("INVALID_NAME").build())
            .build();

    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.jobInfos()).isEmpty();

    // Verify request detail.
    assertThat(createJobsResult.errorReason().get()).isEqualTo(ErrorReason.INVALID_REQUEST);
    assertThat(createJobsResult.state()).isEqualTo(RequestState.ERROR);

    // Verify command detail.
    assertThat(createJobsResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        createJobsResult
            .commandDetails()
            .get(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
  }

  @Test
  public void createTradefedJobsWithEmptyCommand_addResultToSessionOutput() throws Exception {
    NewMultiCommandRequest request =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl(ANDROID_XTS_ZIP)
                    .setName("android-cts.zip")
                    .build())
            .build();

    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.jobInfos()).isEmpty();

    // Verify request detail.
    assertThat(createJobsResult.errorReason().get()).isEqualTo(ErrorReason.INVALID_REQUEST);
    assertThat(createJobsResult.state()).isEqualTo(RequestState.ERROR);
    assertThat(createJobsResult.errorMessage().get()).contains("COMMAND_NOT_AVAILABLE");
  }

  @Test
  public void createNonTradefedJobs_success() throws Exception {
    when(xtsJobCreator.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    when(files.getAll())
        .thenReturn(
            ImmutableMultimap.of(
                "tag1",
                "/data/tmp/test.json",
                "tag1",
                "/data/tmp/mobly.json",
                "tag2",
                "/data/tmp/mock.json"));
    // Trigger the handler.
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createNonTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.jobInfos()).containsExactly(jobInfo);

    assertThat(createJobsResult.state()).isEqualTo(RequestState.RUNNING);
    assertThat(createJobsResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail = createJobsResult.commandDetails().values().iterator().next();

    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getId())
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    assertThat(commandDetail.getRequestId()).isEqualTo("session_id");
    assertThat(commandDetail.getState()).isEqualTo(CommandState.RUNNING);
    assertThat(commandDetail.getOriginalCommandInfo()).isEqualTo(commandInfo);

    // Verify that handler has mounted the zip file.
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    Command mountCommand =
        Command.of("fuse-zip", "-r", "/path/to/xts/zip/file.zip", xtsRootDir)
            .timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
    verify(files)
        .replaceAll(
            eq("tag1"),
            eq(
                ImmutableSet.of(
                    "ats-file-server::/tmp/test.json", "ats-file-server::/tmp/mobly.json")));
    verify(files).replaceAll(eq("tag2"), eq(ImmutableSet.of("ats-file-server::/tmp/mock.json")));
  }

  @Test
  public void createNonTradefedJobs_invalidRequest_returnEmptyCommandList() throws Exception {
    when(xtsJobCreator.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    when(sessionInfo.getSessionId()).thenReturn("SESSION_ID");
    CommandInfo commandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine("cts -m module1 --logcat-on-failure")
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue(DEVICE_ID_1)
                    .build())
            .build();
    NewMultiCommandRequest request =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(commandInfo)
            .addTestResources(
                TestResource.newBuilder().setUrl(ANDROID_XTS_ZIP).setName("INVALID_NAME").build())
            .build();

    // Trigger the handler.
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createNonTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.jobInfos()).isEmpty();
    assertThat(createJobsResult.state()).isEqualTo(RequestState.ERROR);
    assertThat(createJobsResult.errorReason().get()).isEqualTo(ErrorReason.INVALID_REQUEST);
  }

  @Test
  public void createNonTradefedJobs_skippableException_returnEmptyCommandList() throws Exception {

    when(xtsJobCreator.createXtsNonTradefedJobs(any()))
        .thenThrow(
            new MobileHarnessException(InfraErrorId.XTS_NO_MATCHED_NON_TRADEFED_MODULES, "error"));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Trigger the handler.
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createNonTradefedJobs(request, sessionInfo);

    assertThat(createJobsResult.state()).isEqualTo(RequestState.RUNNING);
    assertThat(createJobsResult.jobInfos()).isEmpty();
  }

  @Test
  public void handleResultProcessing_passResult() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(10)
                    .setFailed(0)
                    .setModulesTotal(1)
                    .build())
            .addModuleInfo(
                ReportProto.Module.newBuilder().setName("module1").setFailedTests(0).build())
            .build();
    request = request.toBuilder().setRetryPreviousSessionId("prev_session_id").build();
    mockProcessResult(result);

    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    Path outputUploadPath = Path.of(outputFileUploadPath);
    verify(sessionResultHandlerUtil)
        .copyRetryFiles(
            eq(outputUploadPath.resolve("prev_session_id").resolve(commandId).toString()),
            eq(outputUploadPath.resolve("session_id").resolve(commandId).toString()));
    verify(sessionResultHandlerUtil)
        .generateScreenshotsMetadataFile(
            any(), eq(outputUploadPath.resolve("session_id").resolve(commandId)));

    // Verify command detail.
    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(10);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(10);
    assertThat(commandDetail.getFailedModuleCount()).isEqualTo(0);
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
    assertThat(handleResultProcessingResult.testContexts()).hasSize(1);
  }

  @Test
  public void handleResultProcessing_failResult() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(5)
                    .setFailed(5)
                    .setModulesTotal(1)
                    .build())
            .addModuleInfo(
                ReportProto.Module.newBuilder().setName("module1").setFailedTests(5).build())
            .build();

    mockProcessResult(result);
    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(5);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(5);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(10);
    assertThat(commandDetail.getFailedModuleCount()).isEqualTo(1);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
  }

  @Test
  public void handleResultProcessing_zeroTotalTest_treatAsError() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(0).setFailed(0).build())
            .build();
    mockProcessResult(result);
    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(0);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
  }

  @Test
  public void handleResultProcessing_zeroTotalTestAndOneModuleRan_treatAsCompleted()
      throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(0)
                    .setFailed(0)
                    .setModulesTotal(1)
                    .build())
            .build();
    mockProcessResult(result);
    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(0);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
  }

  @Test
  public void handleResultProcessing_zeroTotalTestWithFailedTest_setsError() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(0).setFailed(0).build())
            .build();
    mockProcessResult(result);
    when(testResult.get()).thenReturn(TestResult.FAIL);
    when(newTestResult.get()).thenReturn(ERROR_RESULT);

    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorMessage())
        .isEqualTo("test failed with exception [MH|UNDETERMINED|JOB_TIMEOUT|20103]");
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.RESULT_PROCESSING_ERROR);
  }

  @Test
  public void handleResultProcessing_zeroTotalTestWithFailedJob_setsError() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(0).setFailed(0).build())
            .build();
    mockProcessResult(result);
    when(testResult.get()).thenReturn(TestResult.PASS);
    when(jobResult.get()).thenReturn(TestResult.FAIL);
    when(newJobResult.get())
        .thenReturn(
            ResultTypeWithCause.create(
                com.google.devtools.mobileharness.api.model.proto.Test.TestResult.ERROR,
                new MobileHarnessException(BasicErrorId.JOB_TIMEOUT, "job failed with exception")));

    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorMessage())
        .isEqualTo("job failed with exception [MH|UNDETERMINED|JOB_TIMEOUT|20103]");
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.RESULT_PROCESSING_ERROR);
  }

  @Test
  public void handleResultProcessing_getMalformedOutputURL_onlyCleanup() throws Exception {
    setUpPassingJobAndTestResults();
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Add TF job.
    String malformedOutputFileUploadUrl = "malformed_URL";
    request =
        request.toBuilder()
            .setTestEnvironment(
                request.getTestEnvironment().toBuilder()
                    .setOutputFileUploadUrl(malformedOutputFileUploadUrl)
                    .build())
            .build();
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);
    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putAllCommandDetails(createJobsResult.commandDetails());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    when(files.getAll()).thenReturn(ImmutableMultimap.of());
    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);
    assertThat(handleResultProcessingResult.state()).isEqualTo(RequestState.ERROR);
    assertThat(handleResultProcessingResult.errorReason().get())
        .isEqualTo(ErrorReason.RESULT_PROCESSING_ERROR);
    verify(sessionResultHandlerUtil, never())
        .processResult(any(), any(), any(), any(), any(), any());
  }

  @Test
  public void handleResultProcessing_processResultFailed_onlyCleanup() throws Exception {
    setUpPassingJobAndTestResults();
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Add TF job.
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    when(sessionInfo.getSessionProperty(SessionProperties.PROPERTY_KEY_SERVER_SESSION_LOG_PATH))
        .thenReturn(Optional.empty());
    MobileHarnessException mhException =
        new MobileHarnessException(BasicErrorId.LOCAL_FILE_IS_DIR, "Failed to find result file");
    doThrow(mhException)
        .when(sessionResultHandlerUtil)
        .processResult(any(), any(), any(), any(), any(), any());
    Mockito.doNothing().when(localFileUtil).prepareDir(any(Path.class));
    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putAllCommandDetails(createJobsResult.commandDetails());
    var unused = newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);
    verify(sessionInfo)
        .putSessionProperty(
            SessionProperties.PROPERTY_KEY_SERVER_SESSION_LOG_PATH,
            outputFileUploadPath
                + "/session_id/olc_server_session_logs/olc_server_session_log.txt");
  }

  @Test
  public void handleResultProcessing_nonFileUrl_onlyCleanup() throws Exception {
    setUpPassingJobAndTestResults();
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Add TF job.
    String malformedOutputFileUploadUrl = "https://www.google.com";
    request =
        request.toBuilder()
            .setTestEnvironment(
                request.getTestEnvironment().toBuilder()
                    .setOutputFileUploadUrl(malformedOutputFileUploadUrl)
                    .build())
            .build();
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);
    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putAllCommandDetails(createJobsResult.commandDetails());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    var unused = newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);
    verify(sessionResultHandlerUtil, never())
        .processResult(any(), any(), any(), any(), any(), any());
  }

  @Test
  public void handleResultProcessing_tradefedError_failWithErrorMessage() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(
                ReportProto.Summary.newBuilder()
                    .setPassed(9)
                    .setFailed(1)
                    .setModulesTotal(1)
                    .build())
            .build();
    mockProcessResult(result);

    // Mock the content of the tradefed invocation runtime info log file.
    String tradefedInvocationErrorMessage = "example error message";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of(DEVICE_ID_1, DEVICE_ID_2),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(9);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(1);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(10);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
    assertThat(commandDetail.getErrorMessage()).isEqualTo(tradefedInvocationErrorMessage);
  }

  @Test
  public void handleResultProcessing_tradefedError_errorStateWithErrorMessage() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(0).setFailed(0).build())
            .build();
    mockProcessResult(result);

    // Mock the content of the tradefed invocation runtime info log file.
    String noValidTestCaseErrorMessage = "No valid test cases found in the result.";
    String tradefedInvocationErrorMessage = "example error message";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of(DEVICE_ID_1, DEVICE_ID_2),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    HandleResultProcessingResult handleResultProcessingResult =
        createJobAndHandleResultProcessing(request);

    assertThat(handleResultProcessingResult.commandDetails()).hasSize(1);
    CommandDetail commandDetail =
        handleResultProcessingResult.commandDetails().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(0);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
    assertThat(commandDetail.getErrorReason()).isEqualTo(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(commandDetail.getErrorMessage())
        .isEqualTo(noValidTestCaseErrorMessage + "\n" + tradefedInvocationErrorMessage);
    assertThat(handleResultProcessingResult.errorReason())
        .hasValue(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(handleResultProcessingResult.errorMessage())
        .hasValue(
            NewMultiCommandRequestHandler.REQUEST_ERROR_MESSAGE_FOR_TRADEFED_INVOCATION_ERROR);
  }

  @Test
  public void handleResultProcessing_commandError_updateRequestError() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(0).setFailed(0).build())
            .build();
    mockProcessResult(result);

    // Mock the content of the tradefed invocation runtime info log file.
    String tradefedInvocationErrorMessage = "example error message";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of(DEVICE_ID_1, DEVICE_ID_2),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Add TF job.
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);
    assertThat(createJobsResult.jobInfos()).containsExactly(jobInfo);

    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putAllCommandDetails(createJobsResult.commandDetails())
            .setErrorReason(ErrorReason.UNKNOWN_REASON); // Request error reason is unknown
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    assertThat(handleResultProcessingResult.errorReason())
        .hasValue(ErrorReason.TRADEFED_INVOCATION_ERROR);
    assertThat(handleResultProcessingResult.errorMessage())
        .hasValue(
            NewMultiCommandRequestHandler.REQUEST_ERROR_MESSAGE_FOR_TRADEFED_INVOCATION_ERROR);
  }

  @Test
  public void handleResultProcessing_requestErrorExist_keepRequestError() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(0).setFailed(0).build())
            .build();
    mockProcessResult(result);

    // Mock the content of the tradefed invocation runtime info log file.
    String tradefedInvocationErrorMessage = "example error message";
    when(xtsTradefedRuntimeInfoFileUtil.readInfo(any(), any()))
        .thenReturn(
            Optional.of(
                new XtsTradefedRuntimeInfoFileDetail(
                    new XtsTradefedRuntimeInfo(
                        ImmutableList.of(
                            new TradefedInvocation(
                                /* isRunning= */ false,
                                ImmutableList.of(DEVICE_ID_1, DEVICE_ID_2),
                                "failed",
                                tradefedInvocationErrorMessage)),
                        /* timestamp= */ Instant.now()),
                    /* lastModifiedTime= */ Instant.now())));

    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Add TF job.
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);
    assertThat(createJobsResult.jobInfos()).containsExactly(jobInfo);

    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putAllCommandDetails(createJobsResult.commandDetails())
            .setErrorReason(ErrorReason.INVALID_REQUEST) // Request error reason exists
            .setErrorMessage("Original request error");
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    assertThat(handleResultProcessingResult.errorReason()).hasValue(ErrorReason.INVALID_REQUEST);
    assertThat(handleResultProcessingResult.errorMessage()).hasValue("Original request error");
  }

  @Test
  public void handleResultProcessing_generatesManifestFile() throws Exception {
    setUpPassingJobAndTestResults();
    ReportProto.Result result =
        ReportProto.Result.newBuilder()
            .setSummary(ReportProto.Summary.newBuilder().setPassed(10).setFailed(0).build())
            .build();
    mockProcessResult(result);
    String logFile1 = "result.xsl";
    String logFile2 = "logs/olc_server_session_logs/log.txt";
    // Any existing manifest file with the name "FILES" won't be included in the new generated
    // manifest file:
    String logFile3 = "some-subdir/FILES";
    doAnswer(
            invocation -> {
              String outputDir = invocation.getArgument(0, String.class);
              return ImmutableList.of(
                  new File(outputDir + "/" + logFile1),
                  new File(outputDir + "/" + logFile2),
                  new File(outputDir + "/" + logFile3));
            })
        .when(localFileUtil)
        .listFiles(any(String.class), /* recursively= */ eq(true));

    HandleResultProcessingResult unused = createJobAndHandleResultProcessing(request);

    verify(localFileUtil).removeFileOrDir(endsWith(logFile3));
    verify(localFileUtil).writeToFile(isA(String.class), eq(logFile1 + "\n" + logFile2));
  }

  @Test
  public void cleanup_success() throws Exception {
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Trigger the handler.
    var unused = newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    // Verify that handler has mounted the zip file.
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    Command mountCommand =
        Command.of("fuse-zip", "-r", "/path/to/xts/zip/file.zip", xtsRootDir)
            .timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);

    // Verify that handler has unmounted the zip file after calling cleanup().
    newMultiCommandRequestHandler.cleanup(sessionInfo);
    verifyUnmountRootDir(xtsRootDir);
  }

  @Test
  public void cleanup_noXtsZip_success() throws Exception {
    when(atsServerSessionUtil.isLocalMode()).thenReturn(true);
    doReturn("output").when(localFileUtil).unzipFile(anyString(), anyString(), any(Duration.class));
    doNothing().when(localFileUtil).removeFileOrDir(anyString());
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Trigger the handler.
    var unused = newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    // Verify that handler has mounted the zip file.
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    verify(localFileUtil).unzipFile("/path/to/xts/zip/file.zip", xtsRootDir, Duration.ofHours(1));
    newMultiCommandRequestHandler.cleanup(sessionInfo);
    verify(localFileUtil).removeFileOrDir(xtsRootDir);
  }

  @Test
  public void cleanup_unmountAndRemoveAndroidXtsZipFailed_logWarningAndProceed() throws Exception {
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    Command mountCommand =
        Command.of("fuse-zip", "-r", "/path/to/xts/zip/file.zip", xtsRootDir)
            .timeout(Duration.ofMinutes(10));
    when(commandExecutor.run(mountCommand)).thenReturn("COMMAND_OUTPUT");

    // Create a tradefed job so that the xts zip file can be mounted.
    var unused = newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    // Mock an exception to be thrown when running the unmount command.
    Command unmountCommand =
        Command.of("fusermount", "-u", xtsRootDir).timeout(Duration.ofMinutes(10));
    when(commandExecutor.run(unmountCommand)).thenThrow(Mockito.mock(CommandException.class));

    // Mock an exception to be thrown when removing the xts root dir through local file util.
    doThrow(Mockito.mock(CommandException.class))
        .when(localFileUtil)
        .removeFileOrDir(eq(xtsRootDir));

    newMultiCommandRequestHandler.cleanup(sessionInfo);
  }

  @Test
  public void cleanup_cleanupJobGenDirsFailed_logWarningAndProceed() throws Exception {
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    Command mountCommand =
        Command.of("fuse-zip", "-r", "/path/to/xts/zip/file.zip", xtsRootDir)
            .timeout(Duration.ofMinutes(10));
    when(commandExecutor.run(mountCommand)).thenReturn("COMMAND_OUTPUT");

    // Create a tradefed job so that the xts zip file can be mounted.
    var unused = newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);

    // Throw exception when running unmount command.
    MobileHarnessException mhException = Mockito.mock(CommandException.class);
    doThrow(mhException).when(sessionResultHandlerUtil).cleanUpJobGenDirs(any());
    newMultiCommandRequestHandler.cleanup(sessionInfo);
  }

  private void setUpPassingJobAndTestResults() {
    when(jobInfo.result()).thenReturn(jobResult);
    when(testInfo.result()).thenReturn(testResult);
    when(jobResult.get()).thenReturn(TestResult.PASS);
    when(testResult.get()).thenReturn(TestResult.PASS);
    when(testInfo.locator())
        .thenReturn(new TestLocator("test_id", "test_name", new JobLocator("job_id", "job_name")));
    when(jobResult.toNewResult()).thenReturn(newJobResult);
    when(testResult.toNewResult()).thenReturn(newTestResult);
    when(newJobResult.get()).thenReturn(PASS_RESULT);
    when(newTestResult.get()).thenReturn(PASS_RESULT);
  }

  private void verifyUnmountRootDir(String xtsRootDir) throws Exception {
    // Verify that handler has unmounted the zip file after calling cleanup().
    Command unmountCommand =
        Command.of("fusermount", "-u", xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(unmountCommand);
    verify(sleeper).sleep(Duration.ofSeconds(5));
  }

  private void mockProcessResult(ReportProto.Result result) throws Exception {
    doAnswer(
            invocation -> {
              Path path = invocation.getArgument(0, Path.class);
              LocalFileUtil localFileUtil = new LocalFileUtil();
              localFileUtil.prepareDir(path);
              Path resultFile = path.resolve("result.xml");
              java.nio.file.Files.createFile(resultFile);
              localFileUtil.zipDir(path.toString(), path + ".zip");
              return Optional.of(result);
            })
        .when(sessionResultHandlerUtil)
        .processResult(any(), any(), any(), any(), eq(ImmutableList.of(jobInfo)), any());
  }

  private HandleResultProcessingResult createJobAndHandleResultProcessing(
      NewMultiCommandRequest request) throws Exception {
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Add TF job.
    CreateJobsResult createJobsResult =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo);
    assertThat(createJobsResult.jobInfos()).containsExactly(jobInfo);

    RequestDetail.Builder requestDetail =
        RequestDetail.newBuilder()
            .setOriginalRequest(request)
            .putAllCommandDetails(createJobsResult.commandDetails());
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    HandleResultProcessingResult handleResultProcessingResult =
        newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);

    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    Path outputPath = Path.of(outputFileUploadPath + "/session_id/" + commandId);
    Path logPath = outputPath.resolve("logs");
    ArgumentCaptor<Path> pathCaptor1 = ArgumentCaptor.forClass(Path.class);
    verify(xtsJobCreator).createXtsTradefedTestJob(sessionRequestInfoCaptor.capture());
    verify(sessionResultHandlerUtil)
        .processResult(
            pathCaptor1.capture(),
            eq(logPath),
            eq(null),
            eq(null),
            eq(ImmutableList.of(jobInfo)),
            eq(sessionRequestInfoCaptor.getValue()));

    String path1 = pathCaptor1.getValue().toString();
    Instant now = Instant.now();
    DateTimeFormatter dateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu.MM.dd_HH.mm").withZone(ZoneId.systemDefault());
    String fileNamePrefix = dateTimeFormatter.format(now);
    if (!path1.startsWith(outputPath + "/" + fileNamePrefix)) {
      // The file name prefix should be the previous minute if it's not the current minute.
      // This can happen because the time used in the test code is a little later than the time used
      // in the production code.
      fileNamePrefix = dateTimeFormatter.format(now.minus(Duration.ofMinutes(1)));
    }
    assertThat(path1).startsWith(outputPath + "/" + fileNamePrefix);
    // Verify test context.
    TestContext testContext = handleResultProcessingResult.testContexts().get(commandId);
    assertThat(testContext.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(testContext.getTestResourceCount()).isEqualTo(1);
    TestResource testResource = testContext.getTestResource(0);
    assertThat(testResource.getName()).startsWith(fileNamePrefix);
    assertThat(testResource.getName()).endsWith(".zip");
    assertThat(testResource.getUrl())
        .startsWith("file://" + outputPath + "/" + testResource.getName());
    return handleResultProcessingResult;
  }
}
