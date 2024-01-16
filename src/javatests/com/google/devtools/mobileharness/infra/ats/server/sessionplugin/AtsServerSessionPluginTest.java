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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CancelReason;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.NewMultiCommandRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail.RequestState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.SessionRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResource;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.Any;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Result;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Status;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
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
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AtsServerSessionPluginTest {
  private static final String ANDROID_XTS_ZIP = "file:///path/to/xts/zip/file";

  private NewMultiCommandRequest request = NewMultiCommandRequest.getDefaultInstance();
  private CommandInfo commandInfo = CommandInfo.getDefaultInstance();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Bind @Mock private DeviceQuerier deviceQuerier;
  @Bind @Mock private SessionInfo sessionInfo;
  @Bind @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  @Bind @Mock private LocalFileUtil localFileUtil;
  @Bind @Mock private CommandExecutor commandExecutor;
  @Mock private JobInfo jobInfo;
  @Mock private JobInfo jobInfo2;
  @Mock private JobInfo moblyJobInfo;
  @Mock private JobInfo moblyJobInfo2;
  @Mock private Properties properties;

  @Captor private ArgumentCaptor<UnaryOperator<RequestDetail>> unaryOperatorCaptor;
  @Inject private AtsServerSessionPlugin plugin;

  @Before
  public void setup() throws Exception {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(sessionInfo.getSessionId()).thenReturn("session_id");
    when(jobInfo.locator()).thenReturn(new JobLocator("job_id", "job_name"));
    when(jobInfo.properties()).thenReturn(properties);
    when(jobInfo2.locator()).thenReturn(new JobLocator("job_id2", "job_name2"));
    when(jobInfo2.properties()).thenReturn(properties);
    when(moblyJobInfo.locator()).thenReturn(new JobLocator("mobly_job_id", "mobly_job_name"));
    when(moblyJobInfo.properties()).thenReturn(properties);
    when(moblyJobInfo2.locator()).thenReturn(new JobLocator("mobly_job_id2", "mobly_job_name2"));
    when(moblyJobInfo2.properties()).thenReturn(properties);
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any()))
        .thenReturn(Optional.of(jobInfo))
        .thenReturn(Optional.of(jobInfo2));
    when(sessionRequestHandlerUtil.createXtsNonTradefedJobs(any()))
        .thenReturn(ImmutableList.of(moblyJobInfo))
        .thenReturn(ImmutableList.of(moblyJobInfo2));
    doAnswer(invocation -> invocation.getArgument(0))
        .when(sessionRequestHandlerUtil)
        .addNonTradefedModuleInfo(any());
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
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());
  }

  @Test
  public void onSessionStarting_success() throws Exception {
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    verify(sessionInfo)
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = unaryOperatorCaptor.getValue().apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    assertThat(requestDetail.getId()).isEqualTo("session_id");
  }

  @Test
  public void onSessionStarting_requestHasZeroTradefedJob_tryCreateNonTradefedJob()
      throws Exception {
    // Intentionally make it fail to create any tradefed test.
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any())).thenReturn(Optional.empty());
    // Though the command cannot generate a tradefed job, it can generate non-tradefed job.
    when(sessionRequestHandlerUtil.canCreateNonTradefedJobs(any())).thenReturn(true);
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(moblyJobInfo);
  }

  @Test
  public void onSessionStarting_requestHasNeitherTradefedJobNorNonTradefedJob_cancelSession()
      throws Exception {
    // Intentionally make it fail to create any tradefed test and fail non tradefed test check.
    when(sessionRequestHandlerUtil.createXtsTradefedTestJob(any())).thenReturn(Optional.empty());
    when(sessionRequestHandlerUtil.canCreateNonTradefedJobs(any())).thenReturn(false);
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo, never()).addJob(any());
    verify(sessionInfo)
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = unaryOperatorCaptor.getValue().apply(null);
    assertThat(requestDetail.getState()).isEqualTo(RequestState.CANCELED);
    // Contains the failed command detail.
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    assertThat(requestDetail.getCancelReason()).isEqualTo(CancelReason.INVALID_REQUEST);
  }

  @Test
  public void onJobEnded_tradefedJobEnded_triggerNonTradefedJob() throws Exception {
    when(sessionRequestHandlerUtil.canCreateNonTradefedJobs(any())).thenReturn(true);
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    when(jobInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing, new Params(timing)).set(TestResult.PASS);
    when(jobInfo.result()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);

    plugin.onJobEnded(new JobEndEvent(jobInfo, null));

    // Verify added non tradefed jobs.
    verify(sessionInfo).addJob(moblyJobInfo);
    verify(sessionInfo, times(3))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = unaryOperatorCaptor.getAllValues().get(2).apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(2);
    assertThat(requestDetail.containsCommandDetails("mobly_job_id")).isTrue();
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().get("mobly_job_id");
    assertThat(commandDetail.getOriginalCommandInfo()).isEqualTo(commandInfo);
  }

  @Test
  public void onJobEnded_mulitpleTradefedJobsEnded_lastOneTriggerNonTradefedJob() throws Exception {
    when(sessionRequestHandlerUtil.canCreateNonTradefedJobs(any())).thenReturn(true);
    request = request.toBuilder().addCommands(commandInfo).build();
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    verify(sessionInfo).addJob(jobInfo2);

    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    when(jobInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing, new Params(timing)).set(TestResult.PASS);
    when(jobInfo.result()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo.type()).thenReturn(jobType);

    // Verify first jobInfo end signal won't trigger non-TF job creation.
    plugin.onJobEnded(new JobEndEvent(jobInfo, null));
    verify(sessionRequestHandlerUtil, never()).createXtsNonTradefedJobs(any());

    when(jobInfo2.timing()).thenReturn(timing);
    when(jobInfo2.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result2 = new Result(timing, new Params(timing)).set(TestResult.PASS);
    when(jobInfo2.result()).thenReturn(result2);
    JobType jobType2 = JobType.newBuilder().setDriver("XtsTradefedTest").build();
    when(jobInfo2.type()).thenReturn(jobType2);

    // Verify added non tradefed jobs when the last jobInfo has ended.
    plugin.onJobEnded(new JobEndEvent(jobInfo2, null));
    verify(sessionInfo).addJob(moblyJobInfo);
    verify(sessionInfo).addJob(moblyJobInfo2);
    verify(sessionRequestHandlerUtil, times(2)).createXtsNonTradefedJobs(any());
  }

  @Test
  public void onJobEnded_nonTradefedJobEnded_onlyUpdateSessionOutput() throws Exception {
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    when(jobInfo.status()).thenReturn(new Status(timing).set(TestStatus.DONE));
    Result result = new Result(timing, new Params(timing)).set(TestResult.PASS);
    when(jobInfo.result()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("MoblyDriver").build();
    when(jobInfo.type()).thenReturn(jobType);

    plugin.onJobEnded(new JobEndEvent(jobInfo, null));

    // Verify that plugin didn't create any new job.
    verify(sessionInfo).addJob(jobInfo);
    verify(sessionInfo, times(2))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = unaryOperatorCaptor.getAllValues().get(1).apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    assertThat(requestDetail.getCommandDetailsMap().keySet().iterator().next()).isEqualTo("job_id");
  }

  @Test
  public void onSessionEnd_triggerCleanup() throws Exception {
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    verify(sessionInfo)
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = unaryOperatorCaptor.getValue().apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    assertThat(requestDetail.getId()).isEqualTo("session_id");

    plugin.onSessionEnded(new SessionEndedEvent(sessionInfo, null));
    String xtsRootDir = DirUtil.getPublicGenDir() + "/session_session_id/file";
    // Verify that handler has unmounted the zip file after calling cleanup().
    Command unmountCommand =
        Command.of("fusermount", "-u", xtsRootDir).timeout(Duration.ofMinutes(10));
    verify(commandExecutor).run(unmountCommand);
  }

  @Test
  public void onJobEnded_jobCompleted_updateSessionOutput() throws Exception {
    verifyState(TestStatus.DONE, TestResult.PASS, CommandState.COMPLETED);
  }

  @Test
  public void onJobEnded_jobFailed_updateSessionOutput() throws Exception {
    verifyState(TestStatus.DONE, TestResult.FAIL, CommandState.ERROR);
  }

  private void verifyState(TestStatus teststatus, TestResult testResult, CommandState commandState)
      throws Exception {
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        SessionRequest.newBuilder().setNewMultiCommandRequest(request).build()))
                .build());
    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));
    verify(sessionInfo).addJob(jobInfo);
    Timing timing = new Timing();
    when(jobInfo.timing()).thenReturn(timing);
    when(jobInfo.status()).thenReturn(new Status(timing).set(teststatus));
    Result result = new Result(timing, new Params(timing)).set(testResult);
    when(jobInfo.result()).thenReturn(result);
    JobType jobType = JobType.newBuilder().setDriver("NoOpDriver").build();
    when(jobInfo.type()).thenReturn(jobType);
    plugin.onJobEnded(new JobEndEvent(jobInfo, null));

    // sessionInfo.setSessionPluginOutput() is called twice. First time in
    // OnSessionStarting() after creating tradefed jobs. Second time in OnJobEnded() after job ended
    // signal trigger session output update.
    verify(sessionInfo, times(2))
        .setSessionPluginOutput(unaryOperatorCaptor.capture(), eq(RequestDetail.class));
    RequestDetail requestDetail = unaryOperatorCaptor.getAllValues().get(1).apply(null);
    assertThat(requestDetail.getCommandDetailsCount()).isEqualTo(1);
    assertThat(requestDetail.getCommandDetailsMap().keySet().iterator().next()).isEqualTo("job_id");
    CommandDetail commandDetail = requestDetail.getCommandDetailsMap().values().iterator().next();
    assertThat(commandDetail.getState()).isEqualTo(commandState);
  }
}
