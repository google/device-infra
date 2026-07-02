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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionCancellation;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginNotification;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionNotificationEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionNotification;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionDeviceCache;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.xts.message.proto.TestMessageProto.XtsTradefedRunCancellation;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.Any;
import com.google.protobuf.TextFormat;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfos;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AtsSessionPluginTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Bind @Mock private SessionInfo sessionInfo;
  @Bind @Mock private DumpEnvVarCommandHandler dumpEnvVarCommandHandler;
  @Bind @Mock private DumpStackTraceCommandHandler dumpStackCommandHandler;
  @Bind @Mock private DumpUptimeCommandHandler dumpUptimeCommandHandler;
  @Bind @Mock private ListDevicesCommandHandler listDevicesCommandHandler;
  @Bind @Mock private ListModulesCommandHandler listModulesCommandHandler;
  @Bind @Mock private RunCommandHandler runCommandHandler;
  @Bind @Mock private TestMessageUtil testMessageUtil;
  @Bind @Mock private XtsTradefedRuntimeInfoFileUtil xtsTradefedRuntimeInfoFileUtil;
  @Bind @Mock private LocalFileUtil localFileUtil;
  @Bind @Mock private SessionDeviceCache sessionDeviceCache;

  @Inject private AtsSessionPlugin atsSessionPlugin;

  @Before
  public void setUp() {
    AtsSessionPlugin.NEXT_RUN_COMMAND_ID.set(1);
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void onSessionStarting() throws Exception {
    RunCommand runCommand = RunCommand.getDefaultInstance();
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(AtsSessionPluginConfig.newBuilder().setRunCommand(runCommand).build()))
                .build());
    atsSessionPlugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    verify(sessionInfo).putSessionProperty(SessionProperties.PROPERTY_KEY_COMMAND_ID, "1");
  }

  @Test
  public void onSessionStarted_tradefedJobCreated_addsTradefedJobToSession() throws Exception {
    RunCommand runCommand = RunCommand.getDefaultInstance();
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(AtsSessionPluginConfig.newBuilder().setRunCommand(runCommand).build()))
                .build());
    atsSessionPlugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    JobInfo tfJob = mock(JobInfo.class);
    when(tfJob.locator()).thenReturn(new JobLocator("tf_job_id", "tf_job_name"));
    when(runCommandHandler.createTradefedJobs(runCommand)).thenReturn(ImmutableList.of(tfJob));
    when(runCommandHandler.createNonTradefedJobs(runCommand)).thenReturn(ImmutableList.of());

    atsSessionPlugin.onSessionStarted(new SessionStartedEvent(sessionInfo));

    verify(sessionInfo).addJob(tfJob);
  }

  @Test
  public void onSessionStarted_noTradefedJobCreated_fallsBackToAddNonTradefedJob()
      throws Exception {
    RunCommand runCommand = RunCommand.getDefaultInstance();
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(AtsSessionPluginConfig.newBuilder().setRunCommand(runCommand).build()))
                .build());
    atsSessionPlugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    JobInfo nonTfJob = mock(JobInfo.class);
    when(nonTfJob.locator()).thenReturn(new JobLocator("non_tf_job_id", "non_tf_job_name"));
    Properties properties = new Properties(new Timing());
    when(nonTfJob.properties()).thenReturn(properties);
    when(runCommandHandler.createTradefedJobs(runCommand)).thenReturn(ImmutableList.of());
    when(runCommandHandler.createNonTradefedJobs(runCommand))
        .thenReturn(ImmutableList.of(nonTfJob));

    atsSessionPlugin.onSessionStarted(new SessionStartedEvent(sessionInfo));

    verify(sessionInfo).addJob(nonTfJob);
  }

  @Test
  public void onJobEnd_nonTradefedJob_atsModuleRunResultFileWritten() throws Exception {
    JobInfo jobInfo = mock(JobInfo.class);
    TestInfos testInfos = mock(TestInfos.class);
    TestInfo testInfo = mock(TestInfo.class);
    Result result = mock(Result.class);

    when(jobInfo.locator()).thenReturn(new JobLocator("job_id", "job_name"));
    Properties jobProperties = new Properties(new Timing());
    jobProperties.add(Job.IS_XTS_NON_TF_JOB, "true");
    when(jobInfo.properties()).thenReturn(jobProperties);
    when(jobInfo.tests()).thenReturn(testInfos);
    when(testInfos.getAll()).thenReturn(ImmutableListMultimap.of("test_id", testInfo));
    when(testInfo.resultWithCause()).thenReturn(result);
    when(result.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, /* cause= */ null));
    when(testInfo.getGenFileDir()).thenReturn("/tmp/test_gen_file_dir");

    JobEndEvent event = new JobEndEvent(jobInfo, /* jobError= */ null);

    atsSessionPlugin.onJobEnd(event);

    verify(localFileUtil)
        .writeToFile("/tmp/test_gen_file_dir/ats_module_run_result.textproto", "result: PASS\n");
  }

  @Test
  public void onJobEnd_nonTradefedJobFailure_atsModuleRunResultFileWritten() throws Exception {
    JobInfo jobInfo = mock(JobInfo.class);
    TestInfos testInfos = mock(TestInfos.class);
    TestInfo testInfo = mock(TestInfo.class);
    Result result = mock(Result.class);

    when(jobInfo.locator()).thenReturn(new JobLocator("job_id", "job_name"));
    Properties jobProperties = new Properties(new Timing());
    jobProperties.add(Job.IS_XTS_NON_TF_JOB, "true");
    when(jobInfo.properties()).thenReturn(jobProperties);
    when(jobInfo.tests()).thenReturn(testInfos);
    when(testInfos.getAll()).thenReturn(ImmutableListMultimap.of("test_id", testInfo));
    when(testInfo.resultWithCause()).thenReturn(result);
    when(result.get())
        .thenReturn(
            ResultTypeWithCause.create(
                TestResult.FAIL,
                new MobileHarnessException(
                    ExtErrorId.MOBLY_TEST_FAILURE,
                    "The Mobly test run had some failures. Please see Mobly test results.")));
    when(testInfo.getGenFileDir()).thenReturn("/tmp/test_gen_file_dir");

    JobEndEvent event = new JobEndEvent(jobInfo, /* jobError= */ null);

    atsSessionPlugin.onJobEnd(event);

    verify(localFileUtil)
        .writeToFile(
            eq("/tmp/test_gen_file_dir/ats_module_run_result.textproto"),
            startsWith(
                "result: FAIL\n"
                    + "cause: \"FAIL[cause=MobileHarnessException: The Mobly test run had some"
                    + " failures. Please see Mobly test results."
                    + " [MH|CUSTOMER_ISSUE|MOBLY_TEST_FAILURE|81022] [MobileHarnessException]"));
  }

  @Test
  public void onSessionNotification_cancellationWithSignal_forwardDirectSignal() throws Exception {
    TestInfo testInfo = mock(TestInfo.class);
    TestLocator locator = mock(TestLocator.class);
    when(testInfo.locator()).thenReturn(locator);
    when(locator.getId()).thenReturn("test_id");
    JobInfo jobInfo = mock(JobInfo.class);
    Properties properties = new Properties(new Timing());
    when(jobInfo.properties()).thenReturn(properties);
    when(testInfo.jobInfo()).thenReturn(jobInfo);

    LocalTestStartingEvent startingEvent = mock(LocalTestStartingEvent.class);
    Allocation allocation = mock(Allocation.class);
    when(startingEvent.getTest()).thenReturn(testInfo);
    when(startingEvent.getAllocation()).thenReturn(allocation);
    when(allocation.getAllDeviceLocators()).thenReturn(ImmutableList.of());
    when(startingEvent.getLocalDevices()).thenReturn(ImmutableMap.of());

    atsSessionPlugin.onTestStarting(startingEvent);

    AtsSessionPluginNotification notification =
        AtsSessionPluginNotification.newBuilder()
            .setSessionCancellation(
                AtsSessionCancellation.newBuilder()
                    .setReason("Manually stopped.")
                    .setSignal(3) // SIGQUIT
                    .build())
            .build();

    SessionNotificationEvent notificationEvent =
        new SessionNotificationEvent(
            sessionInfo,
            SessionNotification.newBuilder().setNotification(Any.pack(notification)).build(),
            TextFormat.printer());

    atsSessionPlugin.onSessionNotification(notificationEvent);

    verify(testMessageUtil)
        .sendProtoMessageToTest(
            testInfo,
            XtsTradefedRunCancellation.newBuilder()
                .setKillTradefedSignal(3)
                .setCancelReason("Manually stopped.")
                .build());
  }

  @Test
  public void onSessionNotification_cancellationAggressive_sendsSigterm() throws Exception {
    TestInfo testInfo = mock(TestInfo.class);
    TestLocator locator = mock(TestLocator.class);
    when(testInfo.locator()).thenReturn(locator);
    when(locator.getId()).thenReturn("test_id");
    JobInfo jobInfo = mock(JobInfo.class);
    Properties properties = new Properties(new Timing());
    when(jobInfo.properties()).thenReturn(properties);
    when(testInfo.jobInfo()).thenReturn(jobInfo);

    LocalTestStartingEvent startingEvent = mock(LocalTestStartingEvent.class);
    Allocation allocation = mock(Allocation.class);
    when(startingEvent.getTest()).thenReturn(testInfo);
    when(startingEvent.getAllocation()).thenReturn(allocation);
    when(allocation.getAllDeviceLocators()).thenReturn(ImmutableList.of());
    when(startingEvent.getLocalDevices()).thenReturn(ImmutableMap.of());

    atsSessionPlugin.onTestStarting(startingEvent);

    AtsSessionPluginNotification notification =
        AtsSessionPluginNotification.newBuilder()
            .setSessionCancellation(
                AtsSessionCancellation.newBuilder()
                    .setReason("Aggressive stop.")
                    .setAggressive(true)
                    .build())
            .build();

    SessionNotificationEvent notificationEvent =
        new SessionNotificationEvent(
            sessionInfo,
            SessionNotification.newBuilder().setNotification(Any.pack(notification)).build(),
            TextFormat.printer());

    atsSessionPlugin.onSessionNotification(notificationEvent);

    verify(testMessageUtil)
        .sendProtoMessageToTest(
            testInfo,
            XtsTradefedRunCancellation.newBuilder()
                .setKillTradefedSignal(15) // SIGTERM
                .setCancelReason("Aggressive stop.")
                .build());
  }

  @Test
  public void onSessionNotification_cancellationWithoutSignal_fallbackToAggressive()
      throws Exception {
    TestInfo testInfo = mock(TestInfo.class);
    TestLocator locator = mock(TestLocator.class);
    when(testInfo.locator()).thenReturn(locator);
    when(locator.getId()).thenReturn("test_id");
    JobInfo jobInfo = mock(JobInfo.class);
    Properties properties = new Properties(new Timing());
    when(jobInfo.properties()).thenReturn(properties);
    when(testInfo.jobInfo()).thenReturn(jobInfo);

    LocalTestStartingEvent startingEvent = mock(LocalTestStartingEvent.class);
    Allocation allocation = mock(Allocation.class);
    when(startingEvent.getTest()).thenReturn(testInfo);
    when(startingEvent.getAllocation()).thenReturn(allocation);
    when(allocation.getAllDeviceLocators()).thenReturn(ImmutableList.of());
    when(startingEvent.getLocalDevices()).thenReturn(ImmutableMap.of());

    atsSessionPlugin.onTestStarting(startingEvent);

    AtsSessionPluginNotification notification =
        AtsSessionPluginNotification.newBuilder()
            .setSessionCancellation(
                AtsSessionCancellation.newBuilder()
                    .setReason("Standard stopped.")
                    .setAggressive(false)
                    .build())
            .build();

    SessionNotificationEvent notificationEvent =
        new SessionNotificationEvent(
            sessionInfo,
            SessionNotification.newBuilder().setNotification(Any.pack(notification)).build(),
            TextFormat.printer());

    atsSessionPlugin.onSessionNotification(notificationEvent);

    verify(testMessageUtil)
        .sendProtoMessageToTest(
            testInfo,
            XtsTradefedRunCancellation.newBuilder()
                .setKillTradefedSignal(20) // SIGTSTP
                .setCancelReason("Standard stopped.")
                .build());
  }

  @Test
  public void onSessionNotification_multipleCancellations_sendMultipleMessages() throws Exception {
    TestInfo testInfo = mock(TestInfo.class);
    TestLocator locator = mock(TestLocator.class);
    when(testInfo.locator()).thenReturn(locator);
    when(locator.getId()).thenReturn("test_id");
    JobInfo jobInfo = mock(JobInfo.class);
    Properties properties = new Properties(new Timing());
    when(jobInfo.properties()).thenReturn(properties);
    when(testInfo.jobInfo()).thenReturn(jobInfo);

    LocalTestStartingEvent startingEvent = mock(LocalTestStartingEvent.class);
    Allocation allocation = mock(Allocation.class);
    when(startingEvent.getTest()).thenReturn(testInfo);
    when(startingEvent.getAllocation()).thenReturn(allocation);
    when(allocation.getAllDeviceLocators()).thenReturn(ImmutableList.of());
    when(startingEvent.getLocalDevices()).thenReturn(ImmutableMap.of());

    atsSessionPlugin.onTestStarting(startingEvent);

    // First cancellation
    AtsSessionPluginNotification notification1 =
        AtsSessionPluginNotification.newBuilder()
            .setSessionCancellation(
                AtsSessionCancellation.newBuilder().setReason("Reason 1").setSignal(3).build())
            .build();
    SessionNotificationEvent event1 =
        new SessionNotificationEvent(
            sessionInfo,
            SessionNotification.newBuilder().setNotification(Any.pack(notification1)).build(),
            TextFormat.printer());
    atsSessionPlugin.onSessionNotification(event1);

    // Second cancellation
    AtsSessionPluginNotification notification2 =
        AtsSessionPluginNotification.newBuilder()
            .setSessionCancellation(
                AtsSessionCancellation.newBuilder().setReason("Reason 2").setSignal(9).build())
            .build();
    SessionNotificationEvent event2 =
        new SessionNotificationEvent(
            sessionInfo,
            SessionNotification.newBuilder().setNotification(Any.pack(notification2)).build(),
            TextFormat.printer());
    atsSessionPlugin.onSessionNotification(event2);

    verify(testMessageUtil)
        .sendProtoMessageToTest(
            testInfo,
            XtsTradefedRunCancellation.newBuilder()
                .setKillTradefedSignal(3)
                .setCancelReason("Reason 1")
                .build());
    verify(testMessageUtil)
        .sendProtoMessageToTest(
            testInfo,
            XtsTradefedRunCancellation.newBuilder()
                .setKillTradefedSignal(9)
                .setCancelReason("Reason 2")
                .build());
  }

  @Test
  public void onSessionStarted_setupJobPresent_scheduledFirstAndMainJobsDeferred()
      throws Exception {
    RunCommand runCommand = RunCommand.getDefaultInstance();
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(AtsSessionPluginConfig.newBuilder().setRunCommand(runCommand).build()))
                .build());
    atsSessionPlugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    JobInfo setupJob = mock(JobInfo.class);
    when(setupJob.locator())
        .thenReturn(new JobLocator("setup_job_id", XtsConstants.SETUP_JOB_NAME));
    when(setupJob.properties()).thenReturn(new Properties(new Timing()));

    JobInfo tfJob = mock(JobInfo.class);
    when(tfJob.locator()).thenReturn(new JobLocator("tf_job_id", "tf_job"));
    when(tfJob.properties()).thenReturn(new Properties(new Timing()));

    when(runCommandHandler.createTradefedJobs(runCommand)).thenReturn(ImmutableList.of(tfJob));
    when(runCommandHandler.createNonTradefedJobs(runCommand))
        .thenReturn(ImmutableList.of(setupJob));

    atsSessionPlugin.onSessionStarted(new SessionStartedEvent(sessionInfo));

    verify(sessionInfo).addJob(setupJob);
    verify(sessionInfo, never()).addJob(tfJob);
  }

  @Test
  public void onJobEnd_setupJobEnds_schedulesDeferredTradefedJobs() throws Exception {
    RunCommand runCommand = RunCommand.getDefaultInstance();
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(AtsSessionPluginConfig.newBuilder().setRunCommand(runCommand).build()))
                .build());
    atsSessionPlugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    JobInfo setupJob = mock(JobInfo.class);
    when(setupJob.locator())
        .thenReturn(new JobLocator("setup_job_id", XtsConstants.SETUP_JOB_NAME));
    Properties setupProperties = new Properties(new Timing());
    when(setupJob.properties()).thenReturn(setupProperties);

    JobInfo tfJob = mock(JobInfo.class);
    when(tfJob.locator()).thenReturn(new JobLocator("tf_job_id", "tf_job"));
    when(tfJob.properties()).thenReturn(new Properties(new Timing()));

    when(runCommandHandler.createTradefedJobs(runCommand)).thenReturn(ImmutableList.of(tfJob));
    when(runCommandHandler.createNonTradefedJobs(runCommand))
        .thenReturn(ImmutableList.of(setupJob));

    atsSessionPlugin.onSessionStarted(new SessionStartedEvent(sessionInfo));

    verify(sessionInfo).addJob(setupJob);
    verify(sessionInfo, never()).addJob(tfJob);

    atsSessionPlugin.onJobEnd(new JobEndEvent(setupJob, /* jobError= */ null));

    verify(sessionInfo).addJob(tfJob);
  }

  @Test
  public void onJobEnd_setupJobEnds_noTradefedJobs_schedulesRemainingNonTradefedJobs()
      throws Exception {
    RunCommand runCommand = RunCommand.getDefaultInstance();
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(AtsSessionPluginConfig.newBuilder().setRunCommand(runCommand).build()))
                .build());
    atsSessionPlugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    JobInfo setupJob = mock(JobInfo.class);
    when(setupJob.locator())
        .thenReturn(new JobLocator("setup_job_id", XtsConstants.SETUP_JOB_NAME));
    Properties setupProperties = new Properties(new Timing());
    when(setupJob.properties()).thenReturn(setupProperties);

    JobInfo nonTfJob = mock(JobInfo.class);
    when(nonTfJob.locator()).thenReturn(new JobLocator("nontf_job_id", "nontf_job"));
    when(nonTfJob.properties()).thenReturn(new Properties(new Timing()));

    when(runCommandHandler.createTradefedJobs(runCommand)).thenReturn(ImmutableList.of());
    when(runCommandHandler.createNonTradefedJobs(runCommand))
        .thenReturn(ImmutableList.of(setupJob, nonTfJob));

    atsSessionPlugin.onSessionStarted(new SessionStartedEvent(sessionInfo));

    verify(sessionInfo).addJob(setupJob);
    verify(sessionInfo, never()).addJob(nonTfJob);

    atsSessionPlugin.onJobEnd(new JobEndEvent(setupJob, /* jobError= */ null));

    verify(sessionInfo).addJob(nonTfJob);
  }

  @Test
  public void onSessionStarted_noSetupJobPresent_schedulesMainJobsImmediately() throws Exception {
    RunCommand runCommand = RunCommand.getDefaultInstance();
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(AtsSessionPluginConfig.newBuilder().setRunCommand(runCommand).build()))
                .build());
    atsSessionPlugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    JobInfo tfJob = mock(JobInfo.class);
    when(tfJob.locator()).thenReturn(new JobLocator("tf_job_id", "tf_job"));
    when(tfJob.properties()).thenReturn(new Properties(new Timing()));
    JobInfo nonTfJob = mock(JobInfo.class);
    when(nonTfJob.locator()).thenReturn(new JobLocator("nontf_job_id", "nontf_job"));
    when(nonTfJob.properties()).thenReturn(new Properties(new Timing()));

    when(runCommandHandler.createTradefedJobs(runCommand)).thenReturn(ImmutableList.of(tfJob));
    when(runCommandHandler.createNonTradefedJobs(runCommand))
        .thenReturn(ImmutableList.of(nonTfJob));

    atsSessionPlugin.onSessionStarted(new SessionStartedEvent(sessionInfo));

    verify(sessionInfo).addJob(tfJob);
    verify(sessionInfo, never()).addJob(nonTfJob);
  }
}
