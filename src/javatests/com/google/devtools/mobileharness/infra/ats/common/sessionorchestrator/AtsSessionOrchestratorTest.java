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

package com.google.devtools.mobileharness.infra.ats.common.sessionorchestrator;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.in.Decorators;
import com.google.devtools.mobileharness.api.model.job.in.Dimensions;
import com.google.devtools.mobileharness.infra.ats.common.jobcreator.XtsJobCreator;
import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.ShardingMode;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.controller.scheduler.model.job.in.DeviceRequirement;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.message.proto.TestMessageProto.XtsTradefedRunCancellation;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfos;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.ScopedSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Status;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AtsSessionOrchestratorTest {

  private static final SessionRequestInfo DEFAULT_REQUEST_INFO =
      SessionRequestInfo.getDefaultInstance();
  private static final SessionRequestInfo MODULE_SHARDING_REQUEST_INFO =
      SessionRequestInfo.newBuilder().setShardingMode(ShardingMode.MODULE).build();
  private static final JobType TRADEFED_JOB_TYPE =
      JobType.newBuilder().setDriver("TradefedTest").build();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private SessionInfo sessionInfo;
  @Mock private XtsJobCreator xtsJobCreator;
  @Mock private TestMessageUtil testMessageUtil;

  private AtsSessionOrchestrator orchestrator;

  @Before
  public void setUp() throws Exception {
    when(sessionInfo.getSessionId()).thenReturn("session_id");
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of());
    when(xtsJobCreator.createXtsSetupJob(any())).thenReturn(Optional.empty());
    when(xtsJobCreator.createXtsTearDownJob(any())).thenReturn(Optional.empty());
    when(xtsJobCreator.createXtsTradefedTestJob(any(), any(), anyBoolean()))
        .thenReturn(ImmutableList.of());
    when(xtsJobCreator.createXtsNonTradefedJobs(any())).thenReturn(ImmutableList.of());

    orchestrator = new AtsSessionOrchestrator(sessionInfo, xtsJobCreator, testMessageUtil);
  }

  private static JobInfo mockJobInfo(String jobId, String jobName) {
    JobInfo jobInfo = mock(JobInfo.class);
    when(jobInfo.locator()).thenReturn(new JobLocator(jobId, jobName));
    Properties properties = new Properties(new Timing());
    properties.add(XtsConstants.XTS_JOB_NAME, jobName);
    when(jobInfo.properties()).thenReturn(properties);
    when(jobInfo.type()).thenReturn(JobType.getDefaultInstance());
    when(jobInfo.status()).thenReturn(new Status(new Timing()).set(TestStatus.NEW));
    SubDeviceSpecs subDeviceSpecs = mock(SubDeviceSpecs.class);
    when(subDeviceSpecs.getAllSubDevices()).thenReturn(ImmutableList.of());
    when(jobInfo.subDeviceSpecs()).thenReturn(subDeviceSpecs);
    TestInfos testInfos = mock(TestInfos.class);
    when(testInfos.getAll()).thenReturn(ImmutableListMultimap.of());
    when(jobInfo.tests()).thenReturn(testInfos);
    return jobInfo;
  }

  @org.junit.Test
  public void startSession_withSetupJob_schedulesSetupJobAndDefersMainJobs() throws Exception {
    JobInfo setupJob = mockJobInfo("setup_job_id", XtsConstants.SETUP_JOB_NAME);
    JobInfo tfJob = mockJobInfo("tf_job_id", "tf_job");
    when(xtsJobCreator.createXtsSetupJob(DEFAULT_REQUEST_INFO)).thenReturn(Optional.of(setupJob));
    when(xtsJobCreator.createXtsTradefedTestJob(eq(DEFAULT_REQUEST_INFO), any(), anyBoolean()))
        .thenReturn(ImmutableList.of(tfJob));

    orchestrator.startSession(DEFAULT_REQUEST_INFO);

    verify(sessionInfo).addJob(setupJob);
    verify(sessionInfo, never()).addJob(tfJob);
  }

  @org.junit.Test
  public void onJobEnd_fullPhasedLifecycle_schedulesSetupThenTfThenNonTfThenTeardown()
      throws Exception {
    Properties setupTestProperties = new Properties(new Timing());
    setupTestProperties.add("phase_skippable_decorator_state::device1::decorator1::key1", "val1");
    setupTestProperties.add(
        XtsConstants.XTS_DYNAMIC_DOWNLOAD_TEST_MODULES_PROPERTY_KEY, "CtsModuleA, CtsModuleB");
    setupTestProperties.add(
        XtsConstants.XTS_DYNAMIC_DOWNLOAD_HAS_PRELOADED_MAINLINE_MODULES_PROPERTY_KEY, "true");
    TestInfo setupTest = mock(TestInfo.class);
    when(setupTest.properties()).thenReturn(setupTestProperties);
    TestInfos setupTests = mock(TestInfos.class);
    when(setupTests.getAll()).thenReturn(ImmutableListMultimap.of("setup_test_id", setupTest));
    JobInfo setupJob = mockJobInfo("setup_job_id", XtsConstants.SETUP_JOB_NAME);
    when(setupJob.tests()).thenReturn(setupTests);

    JobInfo tfJob = mockJobInfo("tf_job_id", "tf_job");
    JobInfo nonTfJob = mockJobInfo("nontf_job_id", "nontf_job");

    Properties teardownTestProperties = new Properties(new Timing());
    TestInfo teardownTest = mock(TestInfo.class);
    when(teardownTest.properties()).thenReturn(teardownTestProperties);
    TestInfos teardownTests = mock(TestInfos.class);
    when(teardownTests.getAll())
        .thenReturn(ImmutableListMultimap.of("teardown_test_id", teardownTest));
    JobInfo teardownJob = mockJobInfo("teardown_job_id", XtsConstants.TEARDOWN_JOB_NAME);
    when(teardownJob.tests()).thenReturn(teardownTests);

    when(xtsJobCreator.createXtsSetupJob(DEFAULT_REQUEST_INFO)).thenReturn(Optional.of(setupJob));
    when(xtsJobCreator.createXtsTearDownJob(DEFAULT_REQUEST_INFO))
        .thenReturn(Optional.of(teardownJob));
    when(xtsJobCreator.createXtsTradefedTestJob(
            DEFAULT_REQUEST_INFO,
            ImmutableSet.of("CtsModuleA", "CtsModuleB"),
            /* skipDynamicMctsJob= */ false))
        .thenReturn(ImmutableList.of(tfJob));
    when(xtsJobCreator.createXtsNonTradefedJobs(DEFAULT_REQUEST_INFO))
        .thenReturn(ImmutableList.of(nonTfJob));

    orchestrator.startSession(DEFAULT_REQUEST_INFO);
    verify(sessionInfo).addJob(setupJob);

    // Phase 1 -> Phase 2: Setup ends -> Tradefed job scheduled
    orchestrator.onJobEnd(new JobEndEvent(setupJob, /* jobError= */ null), DEFAULT_REQUEST_INFO);
    verify(sessionInfo).addJob(tfJob);
    verify(sessionInfo, never()).addJob(nonTfJob);
    verify(sessionInfo, never()).addJob(teardownJob);

    // Phase 2 -> Phase 3: Tradefed ends -> Non-Tradefed job scheduled
    orchestrator.onJobEnd(new JobEndEvent(tfJob, /* jobError= */ null), DEFAULT_REQUEST_INFO);
    verify(sessionInfo).addJob(nonTfJob);
    verify(sessionInfo, never()).addJob(teardownJob);

    // Phase 3 -> Phase 4: Non-Tradefed ends -> Teardown job scheduled with relayed state
    orchestrator.onJobEnd(new JobEndEvent(nonTfJob, /* jobError= */ null), DEFAULT_REQUEST_INFO);
    verify(sessionInfo).addJob(teardownJob);
    assertThat(
            teardownTestProperties.get(
                "phase_skippable_decorator_state::device1::decorator1::key1"))
        .isEqualTo("val1");
  }

  @org.junit.Test
  public void onJobEnd_runnerSharding_chainsDynamicJobAndPinsDeviceSerials() throws Exception {
    Properties staticTestProps = new Properties(new Timing());
    staticTestProps.add(Test.DEVICE_ID_LIST, "device_1,device_2");
    TestInfo staticTest = mock(TestInfo.class);
    when(staticTest.properties()).thenReturn(staticTestProps);
    TestInfos staticTests = mock(TestInfos.class);
    when(staticTests.getAll()).thenReturn(ImmutableListMultimap.of("t1", staticTest));
    JobInfo staticJob = mockJobInfo("static_job_id", "job_" + XtsConstants.STATIC_XTS_JOB_NAME);
    when(staticJob.tests()).thenReturn(staticTests);

    SubDeviceSpec subDevice1 =
        SubDeviceSpec.createForTesting(
            DeviceRequirement.create("AndroidRealDevice", new Decorators(), new Dimensions()),
            new ScopedSpecs(new Timing()),
            new Timing());
    SubDeviceSpec subDevice2 =
        SubDeviceSpec.createForTesting(
            DeviceRequirement.create("AndroidRealDevice", new Decorators(), new Dimensions()),
            new ScopedSpecs(new Timing()),
            new Timing());
    SubDeviceSpecs dynamicSubDevices = mock(SubDeviceSpecs.class);
    when(dynamicSubDevices.getAllSubDevices()).thenReturn(ImmutableList.of(subDevice1, subDevice2));
    JobInfo dynamicJob = mockJobInfo("dynamic_job_id", "job_" + XtsConstants.DYNAMIC_MCTS_JOB_NAME);
    when(dynamicJob.subDeviceSpecs()).thenReturn(dynamicSubDevices);

    when(xtsJobCreator.createXtsTradefedTestJob(
            DEFAULT_REQUEST_INFO, ImmutableSet.of(), /* skipDynamicMctsJob= */ false))
        .thenReturn(ImmutableList.of(staticJob, dynamicJob));

    orchestrator.startSession(DEFAULT_REQUEST_INFO);

    // Only static job is started initially in RUNNER mode
    verify(sessionInfo).addJob(staticJob);
    verify(sessionInfo, never()).addJob(dynamicJob);

    // When static job completes, dynamic job is pinned to device_1 and device_2 and scheduled
    orchestrator.onJobEnd(new JobEndEvent(staticJob, /* jobError= */ null), DEFAULT_REQUEST_INFO);
    assertThat(subDevice1.dimensions().get(Name.ID.lowerCaseName())).isEqualTo("device_1");
    assertThat(subDevice2.dimensions().get(Name.ID.lowerCaseName())).isEqualTo("device_2");
    verify(sessionInfo).addJob(dynamicJob);
  }

  @org.junit.Test
  public void startSession_moduleSharding_schedulesAllTradefedJobsConcurrently() throws Exception {
    JobInfo staticJob = mockJobInfo("static_job_id", "m1_" + XtsConstants.STATIC_XTS_JOB_NAME);
    JobInfo dynamicJob = mockJobInfo("dynamic_job_id", "m2_" + XtsConstants.DYNAMIC_MCTS_JOB_NAME);
    when(xtsJobCreator.createXtsTradefedTestJob(
            MODULE_SHARDING_REQUEST_INFO, ImmutableSet.of(), /* skipDynamicMctsJob= */ false))
        .thenReturn(ImmutableList.of(staticJob, dynamicJob));

    orchestrator.startSession(MODULE_SHARDING_REQUEST_INFO);

    verify(sessionInfo).addJob(staticJob);
    verify(sessionInfo).addJob(dynamicJob);
  }

  @org.junit.Test
  public void startSession_noJobsCreated_throwsUserFacingException() throws Exception {
    when(xtsJobCreator.createXtsTradefedTestJob(
            DEFAULT_REQUEST_INFO, ImmutableSet.of(), /* skipDynamicMctsJob= */ false))
        .thenThrow(
            new MobileHarnessException(
                InfraErrorId.XTS_NO_MATCHED_TRADEFED_MODULES, "No TF modules"));
    when(xtsJobCreator.createXtsNonTradefedJobs(DEFAULT_REQUEST_INFO))
        .thenThrow(
            new MobileHarnessException(
                InfraErrorId.XTS_NO_MATCHED_NON_TRADEFED_MODULES, "No non-TF modules"));

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class, () -> orchestrator.startSession(DEFAULT_REQUEST_INFO));
    assertThat(exception.getErrorId()).isEqualTo(InfraErrorId.XTS_NO_JOB_CREATED_FOR_SESSION);
  }

  @org.junit.Test
  public void onSessionCancellation_stopsNewJobsAndForwardsSignalToStartedAndLateTests()
      throws Exception {
    TestInfo startedTest = mock(TestInfo.class);
    TestLocator startedLocator = mock(TestLocator.class);
    when(startedLocator.getId()).thenReturn("started_test_id");
    when(startedTest.locator()).thenReturn(startedLocator);

    orchestrator.onTestStarting(startedTest);
    orchestrator.onSessionCancellation("Cancelled by user", 15);

    XtsTradefedRunCancellation expectedMessage =
        XtsTradefedRunCancellation.newBuilder()
            .setKillTradefedSignal(15)
            .setCancelReason("Cancelled by user")
            .build();
    verify(testMessageUtil).sendProtoMessageToTest(startedTest, expectedMessage);

    // Late-starting test also receives the cancellation message immediately
    TestInfo lateTest = mock(TestInfo.class);
    TestLocator lateLocator = mock(TestLocator.class);
    when(lateLocator.getId()).thenReturn("late_test_id");
    when(lateTest.locator()).thenReturn(lateLocator);
    orchestrator.onTestStarting(lateTest);
    verify(testMessageUtil).sendProtoMessageToTest(lateTest, expectedMessage);
  }

  @org.junit.Test
  public void onJobEnd_resumedSessionWithConcurrentTfJobs_waitsForAllTfJobsBeforeAddingNonTfJobs()
      throws Exception {
    JobInfo tfJob1 = mockJobInfo("tf_job_1_id", "tf_job_1");
    JobInfo tfJob2 = mockJobInfo("tf_job_2_id", "tf_job_2");
    JobInfo tfJob3 = mockJobInfo("tf_job_3_id", "tf_job_3");
    JobInfo nonTfJob = mockJobInfo("non_tf_job_id", "non_tf_job");

    when(tfJob1.type()).thenReturn(TRADEFED_JOB_TYPE);
    when(tfJob1.status()).thenReturn(new Status(new Timing()).set(TestStatus.DONE));
    when(tfJob2.type()).thenReturn(TRADEFED_JOB_TYPE);
    when(tfJob2.status()).thenReturn(new Status(new Timing()).set(TestStatus.RUNNING));
    when(tfJob3.type()).thenReturn(TRADEFED_JOB_TYPE);
    when(tfJob3.status()).thenReturn(new Status(new Timing()).set(TestStatus.NEW));

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(tfJob1, tfJob2, tfJob3));
    when(xtsJobCreator.createXtsTradefedTestJob(
            MODULE_SHARDING_REQUEST_INFO, ImmutableSet.of(), /* skipDynamicMctsJob= */ false))
        .thenReturn(ImmutableList.of(tfJob1, tfJob2, tfJob3));
    when(xtsJobCreator.createXtsNonTradefedJobs(MODULE_SHARDING_REQUEST_INFO))
        .thenReturn(ImmutableList.of(nonTfJob));

    // First TF job finishes while second is RUNNING and third is NEW after restart.
    orchestrator.onJobEnd(new JobEndEvent(tfJob1, null), MODULE_SHARDING_REQUEST_INFO);
    verify(sessionInfo, never()).addJob(nonTfJob);

    // Second TF job finishes while third is still NEW.
    when(tfJob2.status()).thenReturn(new Status(new Timing()).set(TestStatus.DONE));
    orchestrator.onJobEnd(new JobEndEvent(tfJob2, null), MODULE_SHARDING_REQUEST_INFO);
    verify(sessionInfo, never()).addJob(nonTfJob);

    // Third TF job finishes. Non-TF job should now be scheduled once.
    when(tfJob3.status()).thenReturn(new Status(new Timing()).set(TestStatus.DONE));
    orchestrator.onJobEnd(new JobEndEvent(tfJob3, null), MODULE_SHARDING_REQUEST_INFO);
    verify(sessionInfo).addJob(nonTfJob);
  }

  @org.junit.Test
  public void onJobEnd_resumedSessionSetupJobEnds_createsAndSchedulesTeardownJobAfterMainJobs()
      throws Exception {
    JobInfo setupJob = mockJobInfo("setup_job_id", XtsConstants.SETUP_JOB_NAME);
    Properties setupProps = new Properties(new Timing());
    setupProps.add(XtsConstants.XTS_DYNAMIC_DOWNLOAD_TEST_MODULES_PROPERTY_KEY, "CtsModule1");
    setupProps.add(
        XtsConstants.XTS_DYNAMIC_DOWNLOAD_HAS_PRELOADED_MAINLINE_MODULES_PROPERTY_KEY, "true");
    TestInfo setupTest = mock(TestInfo.class);
    when(setupTest.properties()).thenReturn(setupProps);
    TestInfos setupTestInfos = mock(TestInfos.class);
    when(setupTestInfos.getAll()).thenReturn(ImmutableListMultimap.of("setup_test", setupTest));
    when(setupJob.tests()).thenReturn(setupTestInfos);

    JobInfo mainTfJob = mockJobInfo("main_tf_job_id", "main_tf_job");
    JobInfo teardownJob = mockJobInfo("teardown_job_id", XtsConstants.TEARDOWN_JOB_NAME);

    when(xtsJobCreator.createXtsTearDownJob(MODULE_SHARDING_REQUEST_INFO))
        .thenReturn(Optional.of(teardownJob));
    when(xtsJobCreator.createXtsTradefedTestJob(
            MODULE_SHARDING_REQUEST_INFO, ImmutableSet.of("CtsModule1"), false))
        .thenReturn(ImmutableList.of(mainTfJob));
    when(xtsJobCreator.createXtsNonTradefedJobs(MODULE_SHARDING_REQUEST_INFO))
        .thenReturn(ImmutableList.of());

    // Setup job ends after restart (startSession was not called on this orchestrator instance).
    orchestrator.onJobEnd(new JobEndEvent(setupJob, null), MODULE_SHARDING_REQUEST_INFO);
    verify(xtsJobCreator).createXtsTearDownJob(MODULE_SHARDING_REQUEST_INFO);
    verify(sessionInfo).addJob(mainTfJob);
    verify(sessionInfo, never()).addJob(teardownJob);

    // When main TF job finishes, teardown job is scheduled.
    orchestrator.onJobEnd(new JobEndEvent(mainTfJob, null), MODULE_SHARDING_REQUEST_INFO);
    verify(sessionInfo).addJob(teardownJob);
  }

  @org.junit.Test
  public void onJobEnd_resumedSessionRunnerSharding_pinsDevicesAndSchedulesNextTradefedJob()
      throws Exception {
    JobInfo staticTfJob = mockJobInfo("static_tf_id", XtsConstants.STATIC_XTS_JOB_NAME);
    JobInfo dynamicTfJob = mockJobInfo("dynamic_tf_id", XtsConstants.DYNAMIC_MCTS_JOB_NAME);

    when(staticTfJob.type()).thenReturn(TRADEFED_JOB_TYPE);
    when(staticTfJob.status()).thenReturn(new Status(new Timing()).set(TestStatus.DONE));
    Properties testProps = new Properties(new Timing());
    testProps.add(Test.DEVICE_ID_LIST, "device_serial_1,device_serial_2");
    TestInfo staticTest = mock(TestInfo.class);
    when(staticTest.properties()).thenReturn(testProps);
    TestInfos staticTestInfos = mock(TestInfos.class);
    when(staticTestInfos.getAll()).thenReturn(ImmutableListMultimap.of("test_1", staticTest));
    when(staticTfJob.tests()).thenReturn(staticTestInfos);

    SubDeviceSpec spec1 =
        SubDeviceSpec.createForTesting(
            DeviceRequirement.create("AndroidRealDevice", new Decorators(), new Dimensions()),
            new ScopedSpecs(new Timing()),
            new Timing());
    SubDeviceSpec spec2 =
        SubDeviceSpec.createForTesting(
            DeviceRequirement.create("AndroidRealDevice", new Decorators(), new Dimensions()),
            new ScopedSpecs(new Timing()),
            new Timing());
    SubDeviceSpecs subDeviceSpecs = mock(SubDeviceSpecs.class);
    when(subDeviceSpecs.getAllSubDevices()).thenReturn(ImmutableList.of(spec1, spec2));
    when(dynamicTfJob.subDeviceSpecs()).thenReturn(subDeviceSpecs);

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(staticTfJob));
    when(xtsJobCreator.createXtsTradefedTestJob(
            DEFAULT_REQUEST_INFO, ImmutableSet.of(), /* skipDynamicMctsJob= */ false))
        .thenReturn(ImmutableList.of(staticTfJob, dynamicTfJob));

    // Static TF job ends after restart; dynamic TF job should be pinned and scheduled.
    orchestrator.onJobEnd(new JobEndEvent(staticTfJob, null), DEFAULT_REQUEST_INFO);
    assertThat(spec1.dimensions().get(Name.ID.lowerCaseName())).isEqualTo("device_serial_1");
    assertThat(spec2.dimensions().get(Name.ID.lowerCaseName())).isEqualTo("device_serial_2");
    verify(sessionInfo).addJob(dynamicTfJob);
  }

  @org.junit.Test
  public void startSession_runnerShardingMultipleStaticJobs_startsFirstAndQueuesRemaining()
      throws Exception {
    JobInfo staticJob1 = mockJobInfo("static_1_id", XtsConstants.STATIC_XTS_JOB_NAME + "_1");
    JobInfo staticJob2 = mockJobInfo("static_2_id", XtsConstants.STATIC_XTS_JOB_NAME + "_2");
    JobInfo dynamicJob = mockJobInfo("dynamic_id", XtsConstants.DYNAMIC_MCTS_JOB_NAME);

    when(xtsJobCreator.createXtsTradefedTestJob(
            DEFAULT_REQUEST_INFO, ImmutableSet.of(), /* skipDynamicMctsJob= */ false))
        .thenReturn(ImmutableList.of(staticJob1, staticJob2, dynamicJob));

    orchestrator.startSession(DEFAULT_REQUEST_INFO);
    verify(sessionInfo).addJob(staticJob1);
    verify(sessionInfo, never()).addJob(staticJob2);
    verify(sessionInfo, never()).addJob(dynamicJob);

    orchestrator.onJobEnd(new JobEndEvent(staticJob1, null), DEFAULT_REQUEST_INFO);
    verify(sessionInfo).addJob(staticJob2);
    verify(sessionInfo, never()).addJob(dynamicJob);

    orchestrator.onJobEnd(new JobEndEvent(staticJob2, null), DEFAULT_REQUEST_INFO);
    verify(sessionInfo).addJob(dynamicJob);
  }

  @org.junit.Test
  public void onJobEnd_resumedSessionNonTfJobEnds_schedulesRemainingNonTfJobsAndFiltersTriggered()
      throws Exception {
    JobInfo tfJob = mockJobInfo("tf_job_id", "tf_job");
    JobInfo nonTfJob1 = mockJobInfo("non_tf_1_id", "non_tf_job_1");
    JobInfo nonTfJob2 = mockJobInfo("non_tf_2_id", "non_tf_job_2");
    JobInfo teardownJob = mockJobInfo("teardown_id", XtsConstants.TEARDOWN_JOB_NAME);

    when(tfJob.type()).thenReturn(TRADEFED_JOB_TYPE);
    when(tfJob.status()).thenReturn(new Status(new Timing()).set(TestStatus.DONE));
    when(nonTfJob1.status()).thenReturn(new Status(new Timing()).set(TestStatus.DONE));

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(tfJob, nonTfJob1));
    when(xtsJobCreator.createXtsTradefedTestJob(
            DEFAULT_REQUEST_INFO, ImmutableSet.of(), /* skipDynamicMctsJob= */ false))
        .thenReturn(ImmutableList.of(tfJob));
    when(xtsJobCreator.createXtsNonTradefedJobs(DEFAULT_REQUEST_INFO))
        .thenReturn(ImmutableList.of(nonTfJob1, nonTfJob2));
    when(xtsJobCreator.createXtsTearDownJob(DEFAULT_REQUEST_INFO))
        .thenReturn(Optional.of(teardownJob));

    // nonTfJob1 ends after restart; nonTfJob1 is filtered out and nonTfJob2 is scheduled.
    orchestrator.onJobEnd(new JobEndEvent(nonTfJob1, null), DEFAULT_REQUEST_INFO);
    verify(sessionInfo, never()).addJob(nonTfJob1);
    verify(sessionInfo).addJob(nonTfJob2);
    verify(sessionInfo, never()).addJob(teardownJob);

    // When nonTfJob2 finishes, teardownJob is scheduled.
    orchestrator.onJobEnd(new JobEndEvent(nonTfJob2, null), DEFAULT_REQUEST_INFO);
    verify(sessionInfo).addJob(teardownJob);
  }

  @org.junit.Test
  public void onSessionEnded_preventsAddingJobsToSession() throws Exception {
    JobInfo setupJob = mockJobInfo("setup_job_id", "setup_job");
    when(xtsJobCreator.createXtsSetupJob(DEFAULT_REQUEST_INFO)).thenReturn(Optional.of(setupJob));

    orchestrator.onSessionEnded();
    orchestrator.startSession(DEFAULT_REQUEST_INFO);

    verify(sessionInfo, never()).addJob(any());
  }
}
