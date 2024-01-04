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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
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
import java.util.function.UnaryOperator;
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

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  @Bind @Mock private LocalFileUtil localFileUtil;
  @Bind @Mock private CommandExecutor commandExecutor;

  @Mock private SessionInfo sessionInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Properties properties;
  @Captor private ArgumentCaptor<UnaryOperator<RequestDetail>> unaryOperatorCaptor;

  @Inject private NewMultiCommandRequestHandler newMultiCommandRequestHandler;

  @Before
  public void setup() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(sessionInfo.getSessionId()).thenReturn("session_id");
    when(jobInfo.locator()).thenReturn(new JobLocator("job_id", "job_name"));
    when(jobInfo.properties()).thenReturn(properties);
  }

  @Test
  public void handle_success() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo));
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
                TestResource.newBuilder()
                    .setUrl(ANDROID_XTS_ZIP)
                    .setName("android-cts.zip")
                    .build())
            .build();

    // Trigger the handler.
    newMultiCommandRequestHandler.handle(request, sessionInfo);

    // Verify the session plugin output.
    verify(sessionInfo, times(2))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    ImmutableList<RequestDetail> requestDetails =
        unaryOperatorCaptor.getAllValues().stream()
            .map(e -> e.apply(null))
            .collect(toImmutableList());

    assertThat(requestDetails.get(0).getId()).isEqualTo("session_id");
    assertThat(requestDetails.get(0).getState()).isEqualTo(RequestState.RUNNING);
    assertThat(requestDetails.get(0).getCommandInfosList()).containsExactly(commandInfo);

    assertThat(requestDetails.get(1).getId()).isEqualTo("session_id");
    assertThat(requestDetails.get(1).getCommandDetailsCount()).isEqualTo(1);
    String jobId = requestDetails.get(1).getCommandDetailsMap().keySet().iterator().next();
    assertThat(jobId).isEqualTo("job_id");
    CommandDetail commandDetail =
        requestDetails.get(1).getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getId()).isEqualTo(jobId);

    verify(sessionInfo).addJob(jobInfo);
    verify(properties).add("xts-tradefed-job", "true");

    // Verify that handler has mounted the zip file.
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    Command mountCommand =
        Command.of("fuse-zip", "-r", "/path/to/xts/zip/file.zip", xtsRootDir)
            .timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(mountCommand);

    Command unmountCommand =
        Command.of("fusermount", "-u", xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(unmountCommand);
  }

  @Test
  public void handle_mountAndroidXtsZipFailed() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo));
    MobileHarnessException commandExecutorException = Mockito.mock(CommandException.class);
    when(commandExecutor.run(any())).thenThrow(commandExecutorException);
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
                TestResource.newBuilder()
                    .setUrl(ANDROID_XTS_ZIP)
                    .setName("android-cts.zip")
                    .build())
            .build();

    // Trigger the handler.
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> newMultiCommandRequestHandler.handle(request, sessionInfo))
                .getErrorId())
        .isEqualTo(BasicErrorId.LOCAL_MOUNT_ZIP_TO_DIR_ERROR);
  }

  @Test
  public void handle_unmountAndroidXtsZipFailed() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo));
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    Command mountCommand =
        Command.of("fuse-zip", "-r", "/path/to/xts/zip/file.zip", xtsRootDir)
            .timeout(Duration.ofMinutes(10));
    when(commandExecutor.run(mountCommand)).thenReturn("COMMAND_OUTPUT");

    // Throw exception when running unmount command.
    Command unmountCommand =
        Command.of("fusermount", "-u", xtsRootDir).timeout(Duration.ofMinutes(10));
    MobileHarnessException commandExecutorException = Mockito.mock(CommandException.class);
    when(commandExecutor.run(unmountCommand)).thenThrow(commandExecutorException);
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
                TestResource.newBuilder()
                    .setUrl(ANDROID_XTS_ZIP)
                    .setName("android-cts.zip")
                    .build())
            .build();

    // Trigger the handler.
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> newMultiCommandRequestHandler.handle(request, sessionInfo))
                .getErrorId())
        .isEqualTo(BasicErrorId.LOCAL_UNMOUNT_DIR_ERROR);
  }

  @Test
  public void handleRequestWithInvalidResource_addResultToSessionOutput() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());
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

    newMultiCommandRequestHandler.handle(request, sessionInfo);
    verify(sessionInfo, times(2))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));

    ImmutableList<RequestDetail> requestDetails =
        unaryOperatorCaptor.getAllValues().stream()
            .map(e -> e.apply(null))
            .collect(toImmutableList());

    assertThat(requestDetails.get(0).getId()).isEqualTo("session_id");
    assertThat(requestDetails.get(0).getState()).isEqualTo(RequestState.RUNNING);
    assertThat(requestDetails.get(0).getCommandInfosList()).containsExactly(commandInfo);

    // Verify request detail.
    assertThat(requestDetails.get(1).getId()).isEqualTo("session_id");
    assertThat(requestDetails.get(1).getCommandInfosList()).containsExactly(commandInfo);
    assertThat(requestDetails.get(1).getCancelReason()).isEqualTo(CancelReason.INVALID_REQUEST);
    assertThat(requestDetails.get(1).getState()).isEqualTo(RequestState.CANCELED);

    // Verify command detail.
    assertThat(requestDetails.get(1).getCommandDetailsCount()).isEqualTo(1);
    CommandDetail commandDetail =
        requestDetails.get(1).getCommandDetailsOrThrow("UNKNOWN_" + commandInfo.getCommandLine());
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getState()).isEqualTo(CommandState.CANCELED);
    assertThat(commandDetail.getCancelReason()).isEqualTo(CancelReason.INVALID_REQUEST);
  }

  @Test
  public void handleRequestWithEmptyCommand_addResultToSessionOutput() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());
    NewMultiCommandRequest request =
        NewMultiCommandRequest.newBuilder()
            .setUserId("user_id")
            .addTestResources(
                TestResource.newBuilder()
                    .setUrl(ANDROID_XTS_ZIP)
                    .setName("android-cts.zip")
                    .build())
            .build();

    newMultiCommandRequestHandler.handle(request, sessionInfo);
    verify(sessionInfo, times(2))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));

    ImmutableList<RequestDetail> requestDetails =
        unaryOperatorCaptor.getAllValues().stream()
            .map(e -> e.apply(null))
            .collect(toImmutableList());

    assertThat(requestDetails.get(0).getId()).isEqualTo("session_id");
    assertThat(requestDetails.get(0).getState()).isEqualTo(RequestState.RUNNING);
    assertThat(requestDetails.get(0).getCommandInfosList()).isEmpty();

    // Verify request detail.
    assertThat(requestDetails.get(1).getId()).isEqualTo("session_id");
    assertThat(requestDetails.get(1).getCancelReason())
        .isEqualTo(CancelReason.COMMAND_NOT_AVAILABLE);
    assertThat(requestDetails.get(1).getState()).isEqualTo(RequestState.CANCELED);
  }
}
