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
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.CommandHelper;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CancelReason;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.NewMultiCommandRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail.RequestState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestEnvironment;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResource;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
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
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class NewMultiCommandRequestHandlerTest {
  private static final String ANDROID_XTS_ZIP = "file:///path/to/xts/zip/file.zip";
  private static final String OUTPUT_FILE_UPLOAD_URL = "file:///path/to/output";

  private static final String DEFAULT_COMMAND_LINE =
      "cts-plan --module module1 --test test1 --logcat-on-failure --shard-count 2"
          + " --parallel-setup true --parallel-setup-timeout 0";

  private CommandInfo commandInfo = CommandInfo.getDefaultInstance();
  private NewMultiCommandRequest request = NewMultiCommandRequest.getDefaultInstance();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  @Bind @Mock private CommandExecutor commandExecutor;
  @Bind @Mock private Clock clock;
  @Bind @Mock private CommandHelper commandHelper;

  @Mock private SessionInfo sessionInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Files files;
  @Mock private Properties properties;

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
    doAnswer(invocation -> invocation.getArgument(0))
        .when(sessionRequestHandlerUtil)
        .addNonTradefedModuleInfo(any());
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());
    when(commandHelper.getXtsType(any())).thenReturn("cts");
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
                    .setOutputFileUploadUrl(OUTPUT_FILE_UPLOAD_URL)
                    .putAllEnvVars(ImmutableMap.of("env_key1", "env_value1"))
                    .setUseParallelSetup(true)
                    .build())
            .build();
    when(clock.millis()).thenReturn(1000L);
  }

  @After
  public void tearDown() {
    Flags.resetToDefault();
  }

  @Test
  public void addTradefedJobs_invalidCommandLine() throws Exception {
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
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    RequestDetail requestDetail =
        newMultiCommandRequestHandler.addTradefedJobs(request, sessionInfo);
    // Verify request detail.
    assertThat(requestDetail.getCancelReason()).isEqualTo(CancelReason.INVALID_REQUEST);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.CANCELED);

    // Verify command detail.
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    CommandDetail commandDetail =
        requestDetail.getCommandDetailsOrThrow(
            "UNKNOWN_" + commandInfoWithInvalidCommandLine.getCommandLine());
    assertThat(commandDetail.getCommandLine())
        .isEqualTo(commandInfoWithInvalidCommandLine.getCommandLine());
    assertThat(commandDetail.getState()).isEqualTo(CommandState.CANCELED);
    assertThat(commandDetail.getCancelReason()).isEqualTo(CancelReason.INVALID_REQUEST);
  }

  @Test
  public void addTradefedJobs_success() throws Exception {
    when(clock.millis()).thenReturn(1000L).thenReturn(2000L).thenReturn(3000L);
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Trigger the handler.
    RequestDetail requestDetail =
        newMultiCommandRequestHandler.addTradefedJobs(request, sessionInfo);

    assertThat(requestDetail.getId()).isEqualTo("session_id");
    assertThat(requestDetail.getState()).isEqualTo(RequestState.RUNNING);
    assertThat(requestDetail.getCommandInfosList()).containsExactly(commandInfo);
    assertThat(requestDetail.getCreateTime()).isEqualTo(Timestamps.fromMillis(1000L));
    assertThat(requestDetail.getStartTime()).isEqualTo(Timestamps.fromMillis(2000L));
    assertThat(requestDetail.getUpdateTime()).isEqualTo(Timestamps.fromMillis(3000L));

    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    String commandId = requestDetail.getCommandDetailsMap().keySet().iterator().next();
    assertThat(commandId)
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getId()).isEqualTo(commandId);

    verify(sessionInfo).addJob(jobInfo);
    verify(properties).add("xts-tradefed-job", "true");
    verify(sessionRequestHandlerUtil).createXtsTradefedTestJob(sessionRequestInfoCaptor.capture());

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

    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
    verify(files).add(eq("test-name-1"), eq("ats-file-server::/path/to/file1"));
    verify(files).add(eq("test-name-2"), eq("ats-file-server::/path/to/file2"));
  }

  @Test
  public void addTradefedJobs_fromRetrySession_success() throws Exception {
    when(clock.millis()).thenReturn(1000L).thenReturn(2000L).thenReturn(3000L);
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    request =
        request.toBuilder()
            .setRetryPreviousSessionId("retry_previous_session_id")
            .setRetryType("FAILED")
            .build();

    // Trigger the handler.
    RequestDetail requestDetail =
        newMultiCommandRequestHandler.addTradefedJobs(request, sessionInfo);

    assertThat(requestDetail.getId()).isEqualTo("session_id");
    assertThat(requestDetail.getState()).isEqualTo(RequestState.RUNNING);
    assertThat(requestDetail.getCommandInfosList()).containsExactly(commandInfo);
    assertThat(requestDetail.getCreateTime()).isEqualTo(Timestamps.fromMillis(1000L));
    assertThat(requestDetail.getStartTime()).isEqualTo(Timestamps.fromMillis(2000L));
    assertThat(requestDetail.getUpdateTime()).isEqualTo(Timestamps.fromMillis(3000L));

    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    String commandId = requestDetail.getCommandDetailsMap().keySet().iterator().next();
    assertThat(commandId)
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getId()).isEqualTo(commandId);

    verify(sessionInfo).addJob(jobInfo);
    verify(properties).add("xts-tradefed-job", "true");
    verify(sessionRequestHandlerUtil).createXtsTradefedTestJob(sessionRequestInfoCaptor.capture());

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
    String retryResultDir = "/path/to/output/retry_previous_session_id/" + commandId;
    assertThat(sessionRequestInfo.retryResultDir()).hasValue(retryResultDir);

    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
    verify(files).add(eq("test-name-1"), eq("ats-file-server::/path/to/file1"));
    verify(files).add(eq("test-name-2"), eq("ats-file-server::/path/to/file2"));
  }

  @Test
  public void cleanup_success() throws Exception {
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Trigger the handler.
    RequestDetail requestDetail =
        newMultiCommandRequestHandler.addTradefedJobs(request, sessionInfo);
    assertThat(requestDetail.getId()).isEqualTo("session_id");
    verify(sessionInfo).addJob(jobInfo);

    // Verify that handler has mounted the zip file.
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    Command mountCommand =
        Command.of("fuse-zip", "-r", "/path/to/xts/zip/file.zip", xtsRootDir)
            .timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);

    // Verify that handler has unmounted the zip file after calling cleanup().
    newMultiCommandRequestHandler.cleanup(request, sessionInfo);
    verifyUnmountRootDir(xtsRootDir);
  }

  private void verifyUnmountRootDir(String xtsRootDir) throws Exception {
    // Verify that handler has unmounted the zip file after calling cleanup().
    Command unmountCommand =
        Command.of("fusermount", "-u", xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(unmountCommand);
  }

  @Test
  public void handleResultProcessing_success() throws Exception {
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Add TF job.
    RequestDetail requestDetail =
        newMultiCommandRequestHandler.addTradefedJobs(request, sessionInfo);
    assertThat(requestDetail.getId()).isEqualTo("session_id");
    verify(sessionInfo).addJob(jobInfo);

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);
    String commandId =
        UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString();
    Path outputPath = Path.of("/path/to/output/session_id/" + commandId);
    verify(sessionRequestHandlerUtil).createXtsTradefedTestJob(sessionRequestInfoCaptor.capture());
    verify(sessionRequestHandlerUtil)
        .processResult(
            outputPath,
            outputPath,
            Optional.empty(),
            Optional.empty(),
            ImmutableList.of(jobInfo),
            sessionRequestInfoCaptor.getValue());
    verify(sessionRequestHandlerUtil).cleanUpJobGenDirs(ImmutableList.of(jobInfo));
    verifyUnmountRootDir(DirUtil.getPublicGenDir() + "/session_session_id/file");
  }

  @Test
  public void handleResultProcessing_getMalformedOutputURL_onlyCleanup() throws Exception {
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo));
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
    RequestDetail requestDetail =
        newMultiCommandRequestHandler.addTradefedJobs(request, sessionInfo);
    assertThat(requestDetail.getId()).isEqualTo("session_id");
    verify(sessionInfo).addJob(jobInfo);

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    when(files.getAll()).thenReturn(ImmutableMultimap.of());
    newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);
    verify(sessionRequestHandlerUtil, never())
        .processResult(any(), any(), any(), any(), any(), any());
    verifyUnmountRootDir(DirUtil.getPublicGenDir() + "/session_session_id/file");
    verify(sessionRequestHandlerUtil).cleanUpJobGenDirs(ImmutableList.of(jobInfo));
  }

  @Test
  public void handleResultProcessing_processResultFailed_onlyCleanup() throws Exception {
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Add TF job.
    RequestDetail requestDetail =
        newMultiCommandRequestHandler.addTradefedJobs(request, sessionInfo);
    assertThat(requestDetail.getId()).isEqualTo("session_id");
    verify(sessionInfo).addJob(jobInfo);

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    MobileHarnessException mhException = Mockito.mock(MobileHarnessException.class);
    doThrow(mhException)
        .when(sessionRequestHandlerUtil)
        .processResult(any(), any(), any(), any(), any(), any());
    newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);
    verifyUnmountRootDir(DirUtil.getPublicGenDir() + "/session_session_id/file");
    verify(sessionRequestHandlerUtil).cleanUpJobGenDirs(ImmutableList.of(jobInfo));
  }

  @Test
  public void handleResultProcessing_nonFileUrl_onlyCleanup() throws Exception {
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo));
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
    RequestDetail requestDetail =
        newMultiCommandRequestHandler.addTradefedJobs(request, sessionInfo);
    assertThat(requestDetail.getId()).isEqualTo("session_id");
    verify(sessionInfo).addJob(jobInfo);

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));
    newMultiCommandRequestHandler.handleResultProcessing(sessionInfo, requestDetail);
    verify(sessionRequestHandlerUtil, never())
        .processResult(any(), any(), any(), any(), any(), any());
    verifyUnmountRootDir(DirUtil.getPublicGenDir() + "/session_session_id/file");
    verify(sessionRequestHandlerUtil).cleanUpJobGenDirs(ImmutableList.of(jobInfo));
  }

  @Test
  public void addTradefedJobs_mountAndroidXtsZipFailed_cancelRequestWithInvalidResourceError()
      throws Exception {
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo));
    MobileHarnessException commandExecutorException = Mockito.mock(CommandException.class);
    when(commandExecutor.run(any())).thenThrow(commandExecutorException);

    RequestDetail requestDetail =
        newMultiCommandRequestHandler.addTradefedJobs(request, sessionInfo);
    // Verify request detail.
    assertThat(requestDetail.getCancelReason()).isEqualTo(CancelReason.INVALID_REQUEST);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.CANCELED);

    // Verify command detail.
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    CommandDetail commandDetail =
        requestDetail.getCommandDetailsOrThrow("UNKNOWN_" + commandInfo.getCommandLine());
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getState()).isEqualTo(CommandState.CANCELED);
    assertThat(commandDetail.getCancelReason()).isEqualTo(CancelReason.INVALID_RESOURCE);
  }

  @Test
  public void cleanup_unmountAndroidXtsZipFailed_logWarningAndProceed() throws Exception {
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo));
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    Command mountCommand =
        Command.of("fuse-zip", "-r", "/path/to/xts/zip/file.zip", xtsRootDir)
            .timeout(Duration.ofMinutes(10));
    when(commandExecutor.run(mountCommand)).thenReturn("COMMAND_OUTPUT");

    // Create a tradefed job so that the xts zip file can be mounted.
    RequestDetail requestDetail =
        newMultiCommandRequestHandler.addTradefedJobs(request, sessionInfo);
    assertThat(requestDetail.getId()).isEqualTo("session_id");
    verify(sessionInfo).addJob(jobInfo);

    // Throw exception when running unmount command.
    Command unmountCommand =
        Command.of("fusermount", "-u", xtsRootDir).timeout(Duration.ofMinutes(10));
    MobileHarnessException commandExecutorException = Mockito.mock(CommandException.class);
    when(commandExecutor.run(unmountCommand)).thenThrow(commandExecutorException);
    newMultiCommandRequestHandler.cleanup(request, sessionInfo);
  }

  @Test
  public void addTradefedJobsWithInvalidResource_addResultToSessionOutput() throws Exception {
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

    RequestDetail requestDetail =
        newMultiCommandRequestHandler.addTradefedJobs(request, sessionInfo);

    assertThat(requestDetail.getId()).isEqualTo("session_id");
    assertThat(requestDetail.getCommandInfosList()).containsExactly(commandInfo);

    // Verify request detail.
    assertThat(requestDetail.getCancelReason()).isEqualTo(CancelReason.INVALID_REQUEST);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.CANCELED);

    // Verify command detail.
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    CommandDetail commandDetail =
        requestDetail.getCommandDetailsOrThrow("UNKNOWN_" + commandInfo.getCommandLine());
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getState()).isEqualTo(CommandState.CANCELED);
    assertThat(commandDetail.getCancelReason()).isEqualTo(CancelReason.INVALID_REQUEST);
  }

  @Test
  public void addTradefedJobsWithEmptyCommand_addResultToSessionOutput() throws Exception {
    NewMultiCommandRequest request =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl(ANDROID_XTS_ZIP)
                    .setName("android-cts.zip")
                    .build())
            .build();

    RequestDetail requestDetail =
        newMultiCommandRequestHandler.addTradefedJobs(request, sessionInfo);

    assertThat(requestDetail.getId()).isEqualTo("session_id");
    assertThat(requestDetail.getCommandInfosList()).isEmpty();

    // Verify request detail.
    assertThat(requestDetail.getCancelReason()).isEqualTo(CancelReason.COMMAND_NOT_AVAILABLE);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.CANCELED);
  }

  @Test
  public void addNonTradefedJob_success() throws Exception {
    when(sessionRequestHandlerUtil.createXtsNonTradefedJobs(any()))
        .thenReturn(ImmutableList.of(jobInfo));
    when(sessionRequestHandlerUtil.canCreateNonTradefedJobs(any())).thenReturn(true);
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
    Optional<CommandDetail> commandDetails =
        newMultiCommandRequestHandler.addNonTradefedJobs(request, commandInfo, sessionInfo);

    assertThat(commandDetails).isPresent();
    CommandDetail commandDetail = commandDetails.get();

    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getId())
        .isEqualTo(UUID.nameUUIDFromBytes(commandInfo.getCommandLine().getBytes(UTF_8)).toString());
    assertThat(commandDetail.getState()).isEqualTo(CommandState.RUNNING);
    assertThat(commandDetail.getOriginalCommandInfo()).isEqualTo(commandInfo);

    verify(sessionInfo).addJob(jobInfo);

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
  public void addNonTradefedJob_invalidRequest_returnEmptyCommandList() throws Exception {
    when(sessionRequestHandlerUtil.createXtsNonTradefedJobs(any()))
        .thenReturn(ImmutableList.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
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
    Optional<CommandDetail> commandDetail =
        newMultiCommandRequestHandler.addNonTradefedJobs(request, commandInfo, sessionInfo);

    assertThat(commandDetail).isEmpty();
  }

  @Test
  public void addNonTradefedJob_createdZeroJobInfo_returnEmptyCommandList() throws Exception {

    when(sessionRequestHandlerUtil.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    when(sessionRequestHandlerUtil.canCreateNonTradefedJobs(any())).thenReturn(true);

    // Trigger the handler.
    Optional<CommandDetail> commandDetail =
        newMultiCommandRequestHandler.addNonTradefedJobs(request, commandInfo, sessionInfo);

    assertThat(commandDetail).isEmpty();
  }

  @Test
  public void addNonTradefedJob_cannotCreateNonTradefedJobs_returnEmptyCommandList()
      throws Exception {

    when(sessionRequestHandlerUtil.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    when(sessionRequestHandlerUtil.canCreateNonTradefedJobs(any())).thenReturn(false);

    // Trigger the handler.
    Optional<CommandDetail> commandDetail =
        newMultiCommandRequestHandler.addNonTradefedJobs(request, commandInfo, sessionInfo);

    assertThat(commandDetail).isEmpty();
    verify(sessionRequestHandlerUtil, never()).createXtsNonTradefedJobs(any());
  }
}
