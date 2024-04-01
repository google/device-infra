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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.XtsType;
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
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
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

  private CommandInfo commandInfo = CommandInfo.getDefaultInstance();
  private NewMultiCommandRequest request = NewMultiCommandRequest.getDefaultInstance();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  @Bind @Mock private LocalFileUtil localFileUtil;
  @Bind @Mock private CommandExecutor commandExecutor;
  @Bind @Mock private Clock clock;

  @Mock private SessionInfo sessionInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Properties properties;

  @Captor private ArgumentCaptor<SessionRequestInfo> sessionRequestInfoCaptor;

  @Inject private NewMultiCommandRequestHandler newMultiCommandRequestHandler;

  @Before
  public void setup() throws Exception {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(sessionInfo.getSessionId()).thenReturn("session_id");
    when(jobInfo.locator()).thenReturn(new JobLocator("job_id", "job_name"));
    when(jobInfo.properties()).thenReturn(properties);
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
    commandInfo =
        CommandInfo.newBuilder()
            .setName("command")
            .setCommandLine(
                "cts --module module1 --test test1 --logcat-on-failure --shard-count 2"
                    + " --parallel-setup true --parallel-setup-timeout 0")
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
    String jobId = requestDetail.getCommandDetailsMap().keySet().iterator().next();
    assertThat(jobId).isEqualTo("job_id");
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getId()).isEqualTo(jobId);

    verify(sessionInfo).addJob(jobInfo);
    verify(properties).add("xts-tradefed-job", "true");
    verify(sessionRequestHandlerUtil).createXtsTradefedTestJob(sessionRequestInfoCaptor.capture());

    // Verify sessionRequestInfo has been correctly generated.
    SessionRequestInfo sessionRequestInfo = sessionRequestInfoCaptor.getValue();
    assertThat(sessionRequestInfo.testPlan()).isEqualTo("cts");
    assertThat(sessionRequestInfo.moduleNames()).containsExactly("module1");
    assertThat(sessionRequestInfo.testName()).hasValue("test1");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    assertThat(sessionRequestInfo.xtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.xtsType()).isEqualTo(XtsType.CTS);
    assertThat(sessionRequestInfo.androidXtsZip()).hasValue(zipFile);
    assertThat(sessionRequestInfo.startTimeout()).isEqualTo(Duration.ofSeconds(1000));
    assertThat(sessionRequestInfo.jobTimeout()).isEqualTo(Duration.ofSeconds(2000));
    assertThat(sessionRequestInfo.deviceSerials()).containsExactly("device_id_1", "device_id_2");
    assertThat(sessionRequestInfo.shardCount()).hasValue(2);
    assertThat(sessionRequestInfo.envVars()).containsExactly("env_key1", "env_value1");
    assertThat(sessionRequestInfo.useParallelSetup()).isTrue();

    // Verify that handler has mounted the zip file.
    Command mountCommand =
        Command.of("fuse-zip", "-r", zipFile, xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
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
    newMultiCommandRequestHandler.handleResultProcessing(request, sessionInfo);
    verify(sessionRequestHandlerUtil)
        .processResult(
            Path.of("/path/to/output"),
            Path.of("/path/to/output"),
            ImmutableList.of(jobInfo),
            SessionRequestInfo.builder()
                .setTestPlan("") // set the test plan as empty so it won't merge the retry result
                .setXtsType(XtsType.CTS)
                .setXtsRootDir("/fake/path")
                .build());
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
    newMultiCommandRequestHandler.handleResultProcessing(request, sessionInfo);
    verify(sessionRequestHandlerUtil, never()).processResult(any(), any(), any(), any());
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
    doThrow(mhException).when(sessionRequestHandlerUtil).processResult(any(), any(), any(), any());
    newMultiCommandRequestHandler.handleResultProcessing(request, sessionInfo);
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
    newMultiCommandRequestHandler.handleResultProcessing(request, sessionInfo);
    verify(sessionRequestHandlerUtil, never()).processResult(any(), any(), any(), any());
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

    // Trigger the handler.
    ImmutableList<CommandDetail> commandDetails =
        newMultiCommandRequestHandler.addNonTradefedJobs(request, commandInfo, sessionInfo);

    assertThat(commandDetails).hasSize(1);
    CommandDetail commandDetail = commandDetails.get(0);

    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getId()).isEqualTo("job_id");
    assertThat(commandDetail.getState()).isEqualTo(CommandState.UNKNOWN_STATE);
    assertThat(commandDetail.getOriginalCommandInfo()).isEqualTo(commandInfo);

    verify(sessionInfo).addJob(jobInfo);

    // Verify that handler has mounted the zip file.
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    Command mountCommand =
        Command.of("fuse-zip", "-r", "/path/to/xts/zip/file.zip", xtsRootDir)
            .timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);
  }

  @Test
  public void addNonTradefedJob_invalidRequest_returnEmptyCommandist() throws Exception {
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
    ImmutableList<CommandDetail> commandDetails =
        newMultiCommandRequestHandler.addNonTradefedJobs(request, commandInfo, sessionInfo);

    assertThat(commandDetails).isEmpty();
  }

  @Test
  public void addNonTradefedJob_createdZeroJobInfo_returnEmptyCommandist() throws Exception {

    when(sessionRequestHandlerUtil.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    when(sessionRequestHandlerUtil.canCreateNonTradefedJobs(any())).thenReturn(true);

    // Trigger the handler.
    ImmutableList<CommandDetail> commandDetails =
        newMultiCommandRequestHandler.addNonTradefedJobs(request, commandInfo, sessionInfo);

    assertThat(commandDetails).isEmpty();
  }

  @Test
  public void addNonTradefedJob_cannotCreateNonTradefedJobs_returnEmptyCommandist()
      throws Exception {

    when(sessionRequestHandlerUtil.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");
    when(sessionRequestHandlerUtil.canCreateNonTradefedJobs(any())).thenReturn(false);

    // Trigger the handler.
    ImmutableList<CommandDetail> commandDetails =
        newMultiCommandRequestHandler.addNonTradefedJobs(request, commandInfo, sessionInfo);

    assertThat(commandDetails).isEmpty();
    verify(sessionRequestHandlerUtil, never()).createXtsNonTradefedJobs(any());
  }
}
