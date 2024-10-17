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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.SessionResultHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.XtsTypeLoader;
import com.google.devtools.mobileharness.infra.ats.common.jobcreator.XtsJobCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Summary;
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
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import org.junit.After;
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

  private CommandInfo commandInfo = CommandInfo.getDefaultInstance();
  private NewMultiCommandRequest request = NewMultiCommandRequest.getDefaultInstance();
  private String outputFileUploadPath = "";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  @Bind @Mock private SessionResultHandlerUtil sessionResultHandlerUtil;
  @Bind @Mock private XtsJobCreator xtsJobCreator;
  @Bind @Mock private CommandExecutor commandExecutor;
  @Bind @Mock private Clock clock;
  @Bind @Mock private XtsTypeLoader xtsTypeLoader;
  @Bind @Spy private LocalFileUtil localFileUtil = new LocalFileUtil();

  @Mock private SessionInfo sessionInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Files files;
  private final Properties properties = new Properties(new Timing());

  @Captor private ArgumentCaptor<SessionRequestInfo> sessionRequestInfoCaptor;

  @Inject private NewMultiCommandRequestHandler newMultiCommandRequestHandler;

  @Before
  public void setup() throws Exception {
    String publicDir = tmpFolder.newFolder("public_dir").getAbsolutePath();
    Flags.parse(new String[] {String.format("--public_dir=%s", publicDir)});
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(sessionInfo.getSessionId()).thenReturn("session_id");
    when(jobInfo.locator()).thenReturn(new JobLocator("job_id", "job_name"));
    when(jobInfo.properties()).thenReturn(properties);
    when(jobInfo.files()).thenReturn(files);
    when(sessionRequestHandlerUtil.addNonTradefedModuleInfo(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
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
                    .setValue("device_id_1")
                    .build())
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue("device_id_2")
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
                    .setUrl("file://data/path/to/file1")
                    .setName("test-name-1")
                    .build())
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl("file://data/path/to/file2")
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
  }

  @After
  public void tearDown() {
    Flags.resetToDefault();
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
                    .setValue("device_id_1")
                    .build())
            .addDeviceDimensions(
                CommandInfo.DeviceDimension.newBuilder()
                    .setName("device_serial")
                    .setValue("device_id_2")
                    .build())
            .build();
    request =
        request.toBuilder().clearCommands().addCommands(commandInfoWithInvalidCommandLine).build();
    when(clock.millis()).thenReturn(1000L).thenReturn(2000L).thenReturn(3000L);
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder();

    ImmutableList<JobInfo> jobInfos =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

    assertThat(jobInfos).isEmpty();

    // Verify request detail.
    assertThat(requestDetail.getErrorReason()).isEqualTo(ErrorReason.INVALID_REQUEST);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.ERROR);

    // Verify command detail.
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    CommandDetail commandDetail =
        requestDetail.getCommandDetailsOrThrow(
            "UNKNOWN_" + commandInfoWithInvalidCommandLine.getCommandLine());
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
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder();
    ImmutableList<JobInfo> jobInfos =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

    assertThat(jobInfos).containsExactly(jobInfo);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    String commandId = requestDetail.getCommandDetailsMap().keySet().iterator().next();
    assertThat(commandId)
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
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
    String testPlanFile = DirUtil.getPublicGenDir() + "/session_session_id/command.xml";
    assertThat(sessionRequestInfo.xtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.xtsType()).isEqualTo("cts");
    assertThat(sessionRequestInfo.androidXtsZip()).hasValue("ats-file-server::" + zipFile);
    assertThat(sessionRequestInfo.startTimeout()).isEqualTo(Duration.ofSeconds(1000));
    assertThat(sessionRequestInfo.jobTimeout()).isEqualTo(Duration.ofSeconds(2000));
    assertThat(sessionRequestInfo.deviceSerials()).containsExactly("device_id_1", "device_id_2");
    assertThat(sessionRequestInfo.shardCount()).hasValue(2);
    assertThat(sessionRequestInfo.envVars()).containsExactly("env_key1", "env_value1");
    assertThat(sessionRequestInfo.testPlanFile()).hasValue("ats-file-server::" + testPlanFile);
    assertThat(sessionRequestInfo.remoteRunnerFilePathPrefix()).hasValue("ats-file-server::");

    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
    verify(files).add(eq("test-name-1"), eq("ats-file-server::/path/to/file1"));
    verify(files).add(eq("test-name-2"), eq("ats-file-server::/path/to/file2"));
  }

  @Test
  public void createTradefedJobs_fromRetrySession_success() throws Exception {
    when(clock.millis()).thenReturn(1000L).thenReturn(2000L).thenReturn(3000L);
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    Mockito.doReturn(Path.of("/path/to/previous_result.pb"))
        .when(localFileUtil)
        .checkFile(any(Path.class));
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
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder();
    ImmutableList<JobInfo> jobInfos =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

    assertThat(jobInfos).containsExactly(jobInfo);

    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    assertThat(requestDetail.getCommandDetailsMap().keySet().iterator().next())
        .isEqualTo(expectedCommandId);
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
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
    assertThat(sessionRequestInfo.deviceSerials()).containsExactly("device_id_1", "device_id_2");
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
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder();
    ImmutableList<JobInfo> jobInfos =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

    assertThat(jobInfos).containsExactly(jobInfo);

    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    String commandId = requestDetail.getCommandDetailsMap().keySet().iterator().next();
    assertThat(commandId)
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
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
    assertThat(sessionRequestInfo.deviceSerials()).containsExactly("device_id_1", "device_id_2");
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
    Mockito.doReturn(Path.of("/path/to/previous_result.pb"))
        .when(localFileUtil)
        .checkFile(any(Path.class));
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
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder();
    ImmutableList<JobInfo> jobInfos =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

    assertThat(jobInfos).containsExactly(jobInfo);

    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    String commandId = requestDetail.getCommandDetailsMap().keySet().iterator().next();
    assertThat(commandId).isEqualTo(expectedCommandId);
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
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
    assertThat(sessionRequestInfo.deviceSerials()).containsExactly("device_id_1", "device_id_2");
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
  public void createTradefedJobs_mountAndroidXtsZipFailed_errorWithInvalidResourceError()
      throws Exception {
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    MobileHarnessException commandExecutorException = Mockito.mock(CommandException.class);
    when(commandExecutor.run(any())).thenThrow(commandExecutorException);

    RequestDetail.Builder requestDetail = RequestDetail.newBuilder();
    ImmutableList<JobInfo> jobInfos =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

    assertThat(jobInfos).isEmpty();

    // Verify request detail.
    assertThat(requestDetail.getErrorReason()).isEqualTo(ErrorReason.INVALID_RESOURCE);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.ERROR);

    // Verify command detail.
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    CommandDetail commandDetail =
        requestDetail.getCommandDetailsOrThrow("UNKNOWN_" + commandInfo.getCommandLine());
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
                    .setValue("device_id_1")
                    .build())
            .build();
    NewMultiCommandRequest request =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(commandInfo)
            .addTestResources(
                TestResource.newBuilder().setUrl(ANDROID_XTS_ZIP).setName("INVALID_NAME").build())
            .build();

    RequestDetail.Builder requestDetail = RequestDetail.newBuilder().setOriginalRequest(request);
    ImmutableList<JobInfo> jobInfos =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

    assertThat(jobInfos).isEmpty();

    // Verify request detail.
    assertThat(requestDetail.getErrorReason()).isEqualTo(ErrorReason.INVALID_REQUEST);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.ERROR);

    // Verify command detail.
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    CommandDetail commandDetail =
        requestDetail.getCommandDetailsOrThrow("UNKNOWN_" + commandInfo.getCommandLine());
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

    RequestDetail.Builder requestDetail = RequestDetail.newBuilder();
    ImmutableList<JobInfo> jobInfos =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

    assertThat(jobInfos).isEmpty();

    // Verify request detail.
    assertThat(requestDetail.getErrorReason()).isEqualTo(ErrorReason.INVALID_REQUEST);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.ERROR);
    assertThat(requestDetail.getErrorMessage()).contains("COMMAND_NOT_AVAILABLE");
  }

  @Test
  public void createNonTradefedJobs_success() throws Exception {
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder().setState(RequestState.RUNNING);

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
    ImmutableList<JobInfo> jobInfos =
        newMultiCommandRequestHandler.createNonTradefedJobs(request, sessionInfo, requestDetail);

    assertThat(jobInfos).containsExactly(jobInfo);

    assertThat(requestDetail.getState()).isEqualTo(RequestState.RUNNING);
    assertThat(requestDetail.getCommandDetailsMap().values()).hasSize(1);
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();

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
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder().setState(RequestState.RUNNING);

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
                    .setValue("device_id_1")
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
    ImmutableList<JobInfo> jobInfos =
        newMultiCommandRequestHandler.createNonTradefedJobs(request, sessionInfo, requestDetail);

    assertThat(jobInfos).isEmpty();
    assertThat(requestDetail.getState()).isEqualTo(RequestState.ERROR);
    assertThat(requestDetail.getErrorReason()).isEqualTo(ErrorReason.INVALID_REQUEST);
  }

  @Test
  public void createNonTradefedJobs_skippableException_returnEmptyCommandList() throws Exception {
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder().setState(RequestState.RUNNING);

    when(xtsJobCreator.createXtsNonTradefedJobs(any()))
        .thenThrow(
            new MobileHarnessException(InfraErrorId.XTS_NO_MATCHED_NON_TRADEFED_MODULES, "error"));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Trigger the handler.
    ImmutableList<JobInfo> jobInfos =
        newMultiCommandRequestHandler.createNonTradefedJobs(request, sessionInfo, requestDetail);

    assertThat(requestDetail.getState()).isEqualTo(RequestState.RUNNING);
    assertThat(jobInfos).isEmpty();
  }

  @Test
  public void handleResultProcessing_passResult() throws Exception {
    Result result =
        Result.newBuilder()
            .setSummary(Summary.newBuilder().setPassed(10).setFailed(0).build())
            .build();
    request = request.toBuilder().setRetryPreviousSessionId("prev_session_id").build();
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder().setOriginalRequest(request);
    mockProcessResult(result);

    createJobAndHandleResultProcessing(requestDetail);

    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    Path outputUploadPath = Path.of(outputFileUploadPath);
    verify(sessionResultHandlerUtil)
        .copyRetryFiles(
            eq(outputUploadPath.resolve("prev_session_id").resolve(commandId).toString()),
            eq(outputUploadPath.resolve("session_id").resolve(commandId).toString()));

    // Verify command detail.
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(10);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(10);
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.COMPLETED);
    assertThat(requestDetail.getTestContextCount()).isEqualTo(1);
  }

  @Test
  public void handleResultProcessing_failResult() throws Exception {
    Result result =
        Result.newBuilder()
            .setSummary(Summary.newBuilder().setPassed(5).setFailed(5).build())
            .build();
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder().setOriginalRequest(request);

    mockProcessResult(result);
    createJobAndHandleResultProcessing(requestDetail);

    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(5);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(5);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(10);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
  }

  @Test
  public void handleResultProcessing_zeroTotalTest_treatAsFailure() throws Exception {
    Result.Builder resultBuilder =
        Result.newBuilder().setSummary(Summary.newBuilder().setPassed(0).setFailed(0).build());
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder().setOriginalRequest(request);

    mockProcessResult(resultBuilder.build());
    createJobAndHandleResultProcessing(requestDetail);

    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getPassedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getFailedTestCount()).isEqualTo(0);
    assertThat(commandDetail.getTotalTestCount()).isEqualTo(0);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    assertThat(commandDetail.getId()).isEqualTo(commandId);
    assertThat(commandDetail.getState()).isEqualTo(CommandState.ERROR);
  }

  @Test
  public void handleResultProcessing_getMalformedOutputURL_onlyCleanup() throws Exception {
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
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder().setOriginalRequest(request);
    var unused =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    when(files.getAll()).thenReturn(ImmutableMultimap.of());
    newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);
    verify(sessionResultHandlerUtil, never())
        .processResult(any(), any(), any(), any(), any(), any());
    verifyUnmountRootDir(DirUtil.getPublicGenDir() + "/session_session_id/file");
    verify(sessionResultHandlerUtil).cleanUpJobGenDirs(ImmutableList.of(jobInfo));
  }

  @Test
  public void handleResultProcessing_processResultFailed_onlyCleanup() throws Exception {
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Add TF job.
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder().setOriginalRequest(request);
    var unused =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    when(sessionInfo.getSessionProperty(SessionProperties.PROPERTY_KEY_SERVER_SESSION_LOG_PATH))
        .thenReturn(Optional.empty());
    MobileHarnessException mhException =
        new MobileHarnessException(BasicErrorId.LOCAL_FILE_IS_DIR, "Failed to find result file");
    doThrow(mhException)
        .when(sessionResultHandlerUtil)
        .processResult(any(), any(), any(), any(), any(), any());
    Mockito.doNothing().when(localFileUtil).prepareDir(any(Path.class));
    newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);
    verify(sessionInfo)
        .putSessionProperty(
            SessionProperties.PROPERTY_KEY_SERVER_SESSION_LOG_PATH,
            outputFileUploadPath
                + "/session_id/olc_server_session_logs/olc_server_session_log.txt");
    verifyUnmountRootDir(DirUtil.getPublicGenDir() + "/session_session_id/file");
    verify(sessionResultHandlerUtil).cleanUpJobGenDirs(ImmutableList.of(jobInfo));
  }

  @Test
  public void handleResultProcessing_nonFileUrl_onlyCleanup() throws Exception {
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
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder().setOriginalRequest(request);
    var unused =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);
    verify(sessionResultHandlerUtil, never())
        .processResult(any(), any(), any(), any(), any(), any());
    verifyUnmountRootDir(DirUtil.getPublicGenDir() + "/session_session_id/file");
    verify(sessionResultHandlerUtil).cleanUpJobGenDirs(ImmutableList.of(jobInfo));
  }

  @Test
  public void cleanup_success() throws Exception {
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Trigger the handler.
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder();
    var unused =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

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
  public void cleanup_unmountAndroidXtsZipFailed_logWarningAndProceed() throws Exception {
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    Command mountCommand =
        Command.of("fuse-zip", "-r", "/path/to/xts/zip/file.zip", xtsRootDir)
            .timeout(Duration.ofMinutes(10));
    when(commandExecutor.run(mountCommand)).thenReturn("COMMAND_OUTPUT");

    // Create a tradefed job so that the xts zip file can be mounted.
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder();
    var unused =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

    // Throw exception when running unmount command.
    Command unmountCommand =
        Command.of("fusermount", "-u", xtsRootDir).timeout(Duration.ofMinutes(10));
    MobileHarnessException commandExecutorException = Mockito.mock(CommandException.class);
    when(commandExecutor.run(unmountCommand)).thenThrow(commandExecutorException);
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
    RequestDetail.Builder requestDetail = RequestDetail.newBuilder();
    var unused =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

    // Throw exception when running unmount command.

    MobileHarnessException mhException = Mockito.mock(CommandException.class);

    doThrow(mhException).when(sessionResultHandlerUtil).cleanUpJobGenDirs(any());
    newMultiCommandRequestHandler.cleanup(sessionInfo);
  }

  private void verifyUnmountRootDir(String xtsRootDir) throws Exception {
    // Verify that handler has unmounted the zip file after calling cleanup().
    Command unmountCommand =
        Command.of("fusermount", "-u", xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(unmountCommand);
  }

  private void mockProcessResult(Result result) throws Exception {
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

  private void createJobAndHandleResultProcessing(RequestDetail.Builder requestDetail)
      throws Exception {
    when(xtsJobCreator.createXtsTradefedTestJob(any())).thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Add TF job.
    ImmutableList<JobInfo> jobInfos =
        newMultiCommandRequestHandler.createTradefedJobs(request, sessionInfo, requestDetail);

    assertThat(jobInfos).containsExactly(jobInfo);

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
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

    verify(sessionResultHandlerUtil).cleanUpJobGenDirs(ImmutableList.of(jobInfo));
    verifyUnmountRootDir(DirUtil.getPublicGenDir() + "/session_session_id/file");
    String path1 = pathCaptor1.getValue().toString();
    String fileNamePrefix =
        DateTimeFormatter.ofPattern("uuuu.MM.dd_HH.mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now());
    assertThat(path1).startsWith(outputPath + "/" + fileNamePrefix);
    // Verify test context.
    TestContext testContext = requestDetail.getTestContextMap().get(commandId);
    assertThat(testContext.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(testContext.getTestResourceCount()).isEqualTo(1);
    TestResource testResource = testContext.getTestResource(0);
    assertThat(testResource.getName()).startsWith(fileNamePrefix);
    assertThat(testResource.getName()).endsWith(".zip");
    assertThat(testResource.getUrl())
        .startsWith("file://" + outputPath + "/" + testResource.getName());
  }
}
