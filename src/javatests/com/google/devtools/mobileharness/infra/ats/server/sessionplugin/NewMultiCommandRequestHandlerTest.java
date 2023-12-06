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
import static com.google.common.truth.Truth8.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.util.Map;
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
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class NewMultiCommandRequestHandlerTest {
  private static final String ANDROID_XTS_ZIP = "file:///path/to/xts/zip/file";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Mock private SessionInfo sessionInfo;
  @Captor private ArgumentCaptor<UnaryOperator<RequestDetail>> unaryOperatorCaptor;
  @Captor private ArgumentCaptor<JobInfo> jobInfoCaptor;

  @Inject private NewMultiCommandRequestHandler newMultiCommandRequestHandler;

  @Before
  public void setup() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(sessionInfo.getSessionId()).thenReturn("session_id");
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

    assertThat(requestDetails.get(1).getId()).isEqualTo("session_id");
    assertThat(requestDetails.get(1).getCommandDetailsCount()).isEqualTo(1);
    String jobId = requestDetails.get(1).getCommandDetailsMap().keySet().iterator().next();
    CommandDetail commandDetail =
        requestDetails.get(1).getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getCommandLine()).isEqualTo(commandInfo.getCommandLine());
    assertThat(commandDetail.getId()).isEqualTo(jobId);

    verify(sessionInfo).addJob(jobInfoCaptor.capture());
    assertThat(jobInfoCaptor.getValue().locator().getId()).isEqualTo(jobId);
  }

  @Test
  public void createXtsTradefedTestJobConfig_success() throws Exception {
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
                TestResource.newBuilder()
                    .setUrl(ANDROID_XTS_ZIP)
                    .setName("android-cts.zip")
                    .build())
            .build();
    Optional<JobConfig> jobConfigOpt =
        newMultiCommandRequestHandler.createXtsTradefedTestJobConfig(request, commandInfo);

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidRealDevice")
                .setDimensions(StringMap.newBuilder().putContent("serial", "device_id_1"))
                .build());
    // Asserts the driver
    assertThat(jobConfigOpt.get().getDriver().getName()).isEqualTo("XtsTradefedTest");
    String driverParams = jobConfigOpt.get().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParams, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap)
        .containsExactly(
            "xts_type",
            "CTS",
            "android_xts_zip",
            ANDROID_XTS_ZIP,
            "xts_test_plan",
            "cts",
            "run_command_args",
            "-m module1 --logcat-on-failure");
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
