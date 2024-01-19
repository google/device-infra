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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.XtsType;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CancelReason;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.NewMultiCommandRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail.RequestState;
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
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
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

  CommandInfo commandInfo = CommandInfo.getDefaultInstance();
  NewMultiCommandRequest request = NewMultiCommandRequest.getDefaultInstance();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  @Bind @Mock private LocalFileUtil localFileUtil;
  @Bind @Mock private CommandExecutor commandExecutor;

  @Mock private SessionInfo sessionInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Properties properties;

  @Captor
  private ArgumentCaptor<SessionRequestHandlerUtil.SessionRequestInfo> sessionRequestInfoCaptor;

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
            .setCommandLine("cts -m module1 --logcat-on-failure")
            .putDeviceDimensions("device_serial", "device_id_1")
            .build();
    request =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addCommands(commandInfo)
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl(ANDROID_XTS_ZIP)
                    .setName("android-cts.zip")
                    .build())
            .build();
  }

  @Test
  public void addTradefedJobs_success() throws Exception {
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo));
    when(commandExecutor.run(any())).thenReturn("COMMAND_OUTPUT");

    // Trigger the handler.
    RequestDetail requestDetail =
        newMultiCommandRequestHandler.addTradefedJobs(request, sessionInfo);

    assertThat(requestDetail.getId()).isEqualTo("session_id");
    assertThat(requestDetail.getState()).isEqualTo(RequestState.RUNNING);
    assertThat(requestDetail.getCommandInfosList()).containsExactly(commandInfo);

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
    SessionRequestHandlerUtil.SessionRequestInfo sessionRequestInfo =
        sessionRequestInfoCaptor.getValue();
    assertThat(sessionRequestInfo.testPlan()).isEqualTo("cts");
    assertThat(sessionRequestInfo.moduleNames()).containsExactly("module1");
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    String zipFile = "/path/to/xts/zip/file.zip";
    assertThat(sessionRequestInfo.xtsRootDir()).isEqualTo(xtsRootDir);
    assertThat(sessionRequestInfo.deviceSerials()).containsExactly("device_id_1");
    assertThat(sessionRequestInfo.xtsType()).isEqualTo(XtsType.CTS);
    assertThat(sessionRequestInfo.androidXtsZip()).isEqualTo(Optional.of(zipFile));

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
    Command unmountCommand =
        Command.of("fusermount", "-u", xtsRootDir).timeout(Duration.ofMinutes(10));
    newMultiCommandRequestHandler.cleanup(request, sessionInfo);
    verify(commandExecutor).run(unmountCommand);
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
            .putDeviceDimensions("device_serial", "device_id_1")
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
            .putDeviceDimensions("device_serial", "device_id_1")
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
