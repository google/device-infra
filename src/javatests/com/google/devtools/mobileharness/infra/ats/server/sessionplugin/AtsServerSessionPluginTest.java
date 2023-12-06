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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.NewMultiCommandRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.SessionRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResource;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.Any;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
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
public final class AtsServerSessionPluginTest {
  private static final String ANDROID_XTS_ZIP = "file:///path/to/xts/zip/file";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private SessionInfo sessionInfo;

  @Captor private ArgumentCaptor<UnaryOperator<RequestDetail>> unaryOperatorCaptor;
  @Captor private ArgumentCaptor<JobInfo> jobInfoCaptor;

  @Inject private AtsServerSessionPlugin plugin;
  RequestDetail requestDetail;

  @Before
  public void setup() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(sessionInfo.getSessionId()).thenReturn("session_id");
    requestDetail = RequestDetail.getDefaultInstance();
    doAnswer(
            invocation -> {
              UnaryOperator<RequestDetail> operator = invocation.getArgument(0);
              requestDetail = operator.apply(requestDetail);
              return null;
            })
        .when(sessionInfo)
        .setSessionPluginOutput(any(), eq(RequestDetail.class));
  }

  @Test
  public void handleNewRequest_success() throws Exception {
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
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(any());
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    assertThat(requestDetail.getId()).isEqualTo("session_id");
  }

  @Test
  public void handleJobEndEvent_jobCompleted() throws Exception {
    verifyState(TestStatus.DONE, TestResult.PASS, CommandState.COMPLETED);
  }

  @Test
  public void handleJobEndEvent_jobFailed() throws Exception {
    verifyState(TestStatus.DONE, TestResult.FAIL, CommandState.ERROR);
  }

  private void verifyState(TestStatus teststatus, TestResult testResult, CommandState commandState)
      throws Exception {
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
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfoCaptor.capture());
    JobInfo jobInfo = jobInfoCaptor.getValue();
    jobInfo.status().set(teststatus);
    jobInfo.result().set(testResult);

    when(sessionInfo.getSessionPluginOutput(eq(RequestDetail.class)))
        .thenReturn(Optional.of(requestDetail));
    plugin.onJobEnded(new JobEndEvent(jobInfo, null));

    // sessionInfo.setSessionPluginOutput() is called for three times. First two times in
    // OnSessionStarting(). Third time in OnJobEnded().
    verify(sessionInfo, times(3))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    ImmutableList<RequestDetail> requestDetails =
        unaryOperatorCaptor.getAllValues().stream()
            .map(e -> e.apply(null))
            .collect(toImmutableList());

    assertThat(requestDetails.get(2).getCommandDetailsCount()).isEqualTo(1);
    String jobId = requestDetails.get(2).getCommandDetailsMap().keySet().iterator().next();
    assertThat(jobId).isEqualTo(jobInfo.locator().getId());
    CommandDetail commandDetail =
        requestDetails.get(2).getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(commandState);
  }
}
