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
import static org.mockito.Mockito.doThrow;
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
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.jobcreator.XtsJobCreator;
import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.ShardingMode;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.reserver.DeviceReserver;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.controller.scheduler.model.job.in.DeviceRequirement;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.message.proto.TestMessageProto.XtsTradefedRunCancellation;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
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
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.model.lab.LabLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.time.Duration;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
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
  @Mock private DeviceReserver deviceReserver;

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

    orchestrator =
        new AtsSessionOrchestrator(sessionInfo, xtsJobCreator, testMessageUtil, deviceReserver);
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

  @Test
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

  @Test
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

  @Test
  public void onJobEnd_runnerSharding_chainsDynamicJobAndPinsDeviceSerials() throws Exception {
    Properties staticTestProps = new Properties(new Timing());
    staticTestProps.add(PropertyName.Test.DEVICE_ID_LIST, "device_1,device_2");
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

  @Test
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

  @Test
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

  @Test
  public void onSessionCancellation_stopsNewJobsAndForwardsSignalToStartedAndLateTests()
      throws Exception {
    JobLocator jobLocator = new JobLocator("job_id", "job_name");
    TestInfo startedTest = mock(TestInfo.class);
    when(startedTest.locator())
        .thenReturn(new TestLocator("started_test_id", "started_test_name", jobLocator));

    orchestrator.onTestStarting(startedTest, ImmutableList.of());
    orchestrator.onSessionCancellation("Cancelled by user", 15);

    XtsTradefedRunCancellation expectedMessage =
        XtsTradefedRunCancellation.newBuilder()
            .setKillTradefedSignal(15)
            .setCancelReason("Cancelled by user")
            .build();
    verify(testMessageUtil).sendProtoMessageToTest(startedTest, expectedMessage);

    // Late-starting test also receives the cancellation message immediately
    TestInfo lateTest = mock(TestInfo.class);
    when(lateTest.locator())
        .thenReturn(new TestLocator("late_test_id", "late_test_name", jobLocator));
    orchestrator.onTestStarting(lateTest, ImmutableList.of());
    verify(testMessageUtil).sendProtoMessageToTest(lateTest, expectedMessage);
  }

  @Test
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

  @Test
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

  @Test
  public void onJobEnd_resumedSessionRunnerSharding_pinsDevicesAndSchedulesNextTradefedJob()
      throws Exception {
    JobInfo staticTfJob = mockJobInfo("static_tf_id", XtsConstants.STATIC_XTS_JOB_NAME);
    JobInfo dynamicTfJob = mockJobInfo("dynamic_tf_id", XtsConstants.DYNAMIC_MCTS_JOB_NAME);

    when(staticTfJob.type()).thenReturn(TRADEFED_JOB_TYPE);
    when(staticTfJob.status()).thenReturn(new Status(new Timing()).set(TestStatus.DONE));
    Properties testProps = new Properties(new Timing());
    testProps.add(PropertyName.Test.DEVICE_ID_LIST, "device_serial_1,device_serial_2");
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

  @Test
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

  @Test
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

  @Test
  public void onSessionEnded_preventsAddingJobsToSession() throws Exception {
    JobInfo setupJob = mockJobInfo("setup_job_id", "setup_job");
    when(xtsJobCreator.createXtsSetupJob(DEFAULT_REQUEST_INFO)).thenReturn(Optional.of(setupJob));

    orchestrator.onSessionEnded();
    orchestrator.startSession(DEFAULT_REQUEST_INFO);

    verify(sessionInfo, never()).addJob(any());
  }

  private static SubDeviceSpec createSubDeviceSpec() {
    return createSubDeviceSpec(new Dimensions());
  }

  private static SubDeviceSpec createSubDeviceSpec(Dimensions dimensions) {
    return SubDeviceSpec.createForTesting(
        DeviceRequirement.create("AndroidRealDevice", new Decorators(), dimensions),
        new ScopedSpecs(new Timing()),
        new Timing());
  }

  private static JobInfo mockJobInfoWithSubDevices(
      String jobId, String jobName, ImmutableList<SubDeviceSpec> subDevices) {
    JobInfo jobInfo = mockJobInfo(jobId, jobName);
    SubDeviceSpecs subDeviceSpecs = mock(SubDeviceSpecs.class);
    when(subDeviceSpecs.getAllSubDevices()).thenReturn(subDevices);
    when(jobInfo.subDeviceSpecs()).thenReturn(subDeviceSpecs);
    return jobInfo;
  }

  private static TestInfo attachMockSetupTest(JobInfo setupJob) {
    JobLocator setupJobLocator = setupJob.locator();
    TestInfo setupTest = mock(TestInfo.class);
    when(setupTest.locator())
        .thenReturn(new TestLocator("setup_test_id", "setup_test_name", setupJobLocator));
    when(setupTest.jobInfo()).thenReturn(setupJob);
    when(setupTest.properties()).thenReturn(new Properties(new Timing()));
    TestInfos setupTests = mock(TestInfos.class);
    when(setupTests.getAll()).thenReturn(ImmutableListMultimap.of("setup_test_id", setupTest));
    when(setupJob.tests()).thenReturn(setupTests);
    return setupTest;
  }

  private static SessionRequestInfo narrowRequest(SessionRequestInfo base, String... serials) {
    return base.toBuilder()
        .clearDeviceSerials()
        .addAllDeviceSerials(ImmutableList.copyOf(serials))
        .setAllowPartialDeviceMatch(false)
        .build();
  }

  @Test
  public void onTestStarting_setupJobWithAllocatedDevices_reservesDevicesImmediately()
      throws Exception {
    JobInfo setupJob = mockJobInfo("setup_job_id", XtsConstants.SETUP_JOB_NAME);
    TestInfo setupTest = attachMockSetupTest(setupJob);
    when(xtsJobCreator.createXtsSetupJob(MODULE_SHARDING_REQUEST_INFO))
        .thenReturn(Optional.of(setupJob));
    DeviceLocator deviceLocator1 = new DeviceLocator("device_1", LabLocator.LOCALHOST);
    DeviceLocator deviceLocator2 = new DeviceLocator("device_2", LabLocator.LOCALHOST);

    orchestrator.startSession(MODULE_SHARDING_REQUEST_INFO);
    orchestrator.onTestStarting(setupTest, ImmutableList.of(deviceLocator1, deviceLocator2));

    verify(deviceReserver)
        .addTempAllocationKeyToDevice(
            deviceLocator1, "session_id", SessionRequestHandlerUtil.DEFAULT_TRADEFED_JOB_TIMEOUT);
    verify(deviceReserver)
        .addTempAllocationKeyToDevice(
            deviceLocator2, "session_id", SessionRequestHandlerUtil.DEFAULT_TRADEFED_JOB_TIMEOUT);
  }

  @Test
  public void onTestStarting_nonSetupJobWithAllocatedDevices_doesNotReserveDevices()
      throws Exception {
    JobInfo tfJob = mockJobInfo("tf_job_id", "tf_job");
    TestInfo tfTest = mock(TestInfo.class);
    when(tfTest.jobInfo()).thenReturn(tfJob);
    when(xtsJobCreator.createXtsTradefedTestJob(eq(MODULE_SHARDING_REQUEST_INFO), any(), eq(false)))
        .thenReturn(ImmutableList.of(tfJob));
    DeviceLocator deviceLocator = new DeviceLocator("device_1", LabLocator.LOCALHOST);

    orchestrator.startSession(MODULE_SHARDING_REQUEST_INFO);
    orchestrator.onTestStarting(tfTest, ImmutableList.of(deviceLocator));

    verify(deviceReserver, never()).addTempAllocationKeyToDevice(any(), any(), any());
  }

  @Test
  public void
      onJobEnd_setupJobWithAllocatedDevices_stampsAllocationKeyAndKeepsReservationUntilTeardownEnds()
          throws Exception {
    JobInfo setupJob = mockJobInfo("setup_job_id", XtsConstants.SETUP_JOB_NAME);
    TestInfo setupTest = attachMockSetupTest(setupJob);

    SubDeviceSpec tfSpec1 = createSubDeviceSpec();
    SubDeviceSpec tfSpec2 = createSubDeviceSpec();
    JobInfo tfJob1 = mockJobInfoWithSubDevices("tf_1", "tf_job_1", ImmutableList.of(tfSpec1));
    JobInfo tfJob2 = mockJobInfoWithSubDevices("tf_2", "tf_job_2", ImmutableList.of(tfSpec2));

    SubDeviceSpec nonTfSpec = createSubDeviceSpec();
    JobInfo nonTfJob =
        mockJobInfoWithSubDevices("nontf_1", "nontf_job", ImmutableList.of(nonTfSpec));

    SubDeviceSpec teardownSpec1 = createSubDeviceSpec();
    SubDeviceSpec teardownSpec2 = createSubDeviceSpec();
    JobInfo teardownJob =
        mockJobInfoWithSubDevices(
            "teardown_job_id",
            XtsConstants.TEARDOWN_JOB_NAME,
            ImmutableList.of(teardownSpec1, teardownSpec2));

    SessionRequestInfo narrowedModuleShardingRequestInfo =
        narrowRequest(MODULE_SHARDING_REQUEST_INFO, "device_1", "device_2");
    when(xtsJobCreator.createXtsSetupJob(MODULE_SHARDING_REQUEST_INFO))
        .thenReturn(Optional.of(setupJob));
    when(xtsJobCreator.createXtsTearDownJob(narrowedModuleShardingRequestInfo))
        .thenReturn(Optional.of(teardownJob));
    when(xtsJobCreator.createXtsTradefedTestJob(
            eq(narrowedModuleShardingRequestInfo), any(), eq(false)))
        .thenReturn(ImmutableList.of(tfJob1, tfJob2));
    when(xtsJobCreator.createXtsNonTradefedJobs(narrowedModuleShardingRequestInfo))
        .thenReturn(ImmutableList.of(nonTfJob));

    DeviceLocator deviceLocator1 = new DeviceLocator("device_1", LabLocator.LOCALHOST);
    DeviceLocator deviceLocator2 = new DeviceLocator("device_2", LabLocator.LOCALHOST);

    orchestrator.startSession(MODULE_SHARDING_REQUEST_INFO);
    verify(xtsJobCreator, never()).createXtsTearDownJob(any());
    orchestrator.onTestStarting(setupTest, ImmutableList.of(deviceLocator1, deviceLocator2));
    orchestrator.onJobEnd(
        new JobEndEvent(setupJob, /* jobError= */ null), MODULE_SHARDING_REQUEST_INFO);
    orchestrator.onJobEnd(
        new JobEndEvent(tfJob1, /* jobError= */ null), MODULE_SHARDING_REQUEST_INFO);
    orchestrator.onJobEnd(
        new JobEndEvent(tfJob2, /* jobError= */ null), MODULE_SHARDING_REQUEST_INFO);
    orchestrator.onJobEnd(
        new JobEndEvent(nonTfJob, /* jobError= */ null), MODULE_SHARDING_REQUEST_INFO);

    verify(deviceReserver)
        .addTempAllocationKeyToDevice(
            deviceLocator1, "session_id", SessionRequestHandlerUtil.DEFAULT_TRADEFED_JOB_TIMEOUT);
    verify(deviceReserver)
        .addTempAllocationKeyToDevice(
            deviceLocator2, "session_id", SessionRequestHandlerUtil.DEFAULT_TRADEFED_JOB_TIMEOUT);
    assertThat(tfSpec1.dimensions().get(Name.ALLOCATION_KEY)).isEqualTo("session_id");
    assertThat(tfSpec2.dimensions().get(Name.ALLOCATION_KEY)).isEqualTo("session_id");
    assertThat(nonTfSpec.dimensions().get(Name.ALLOCATION_KEY)).isEqualTo("session_id");
    assertThat(teardownSpec1.dimensions().get(Name.ALLOCATION_KEY)).isEqualTo("session_id");
    assertThat(teardownSpec2.dimensions().get(Name.ALLOCATION_KEY)).isEqualTo("session_id");
    verify(sessionInfo).addJob(teardownJob);
    verify(deviceReserver, never())
        .addTempAllocationKeyToDevice(any(), eq("session_id"), eq(Duration.ZERO));
  }

  @Test
  public void onJobEnd_teardownJobEnds_releasesReservedSetupDevicesOnce() throws Exception {
    JobInfo setupJob = mockJobInfo("setup_job_id", XtsConstants.SETUP_JOB_NAME);
    TestInfo setupTest = attachMockSetupTest(setupJob);

    JobInfo tfJob = mockJobInfo("tf_1", "tf_job_1");
    JobInfo teardownJob = mockJobInfo("teardown_job_id", XtsConstants.TEARDOWN_JOB_NAME);

    SessionRequestInfo narrowedModuleShardingRequestInfo =
        narrowRequest(MODULE_SHARDING_REQUEST_INFO, "device_1", "device_2");
    when(xtsJobCreator.createXtsSetupJob(MODULE_SHARDING_REQUEST_INFO))
        .thenReturn(Optional.of(setupJob));
    when(xtsJobCreator.createXtsTearDownJob(narrowedModuleShardingRequestInfo))
        .thenReturn(Optional.of(teardownJob));
    when(xtsJobCreator.createXtsTradefedTestJob(
            eq(narrowedModuleShardingRequestInfo), any(), eq(false)))
        .thenReturn(ImmutableList.of(tfJob));

    DeviceLocator deviceLocator1 = new DeviceLocator("device_1", LabLocator.LOCALHOST);
    DeviceLocator deviceLocator2 = new DeviceLocator("device_2", LabLocator.LOCALHOST);

    orchestrator.startSession(MODULE_SHARDING_REQUEST_INFO);
    orchestrator.onTestStarting(setupTest, ImmutableList.of(deviceLocator1, deviceLocator2));
    orchestrator.onJobEnd(
        new JobEndEvent(setupJob, /* jobError= */ null), MODULE_SHARDING_REQUEST_INFO);
    orchestrator.onJobEnd(
        new JobEndEvent(tfJob, /* jobError= */ null), MODULE_SHARDING_REQUEST_INFO);
    orchestrator.onJobEnd(
        new JobEndEvent(teardownJob, /* jobError= */ null), MODULE_SHARDING_REQUEST_INFO);
    orchestrator.onSessionEnded();

    verify(deviceReserver)
        .addTempAllocationKeyToDevice(deviceLocator1, "session_id", Duration.ZERO);
    verify(deviceReserver)
        .addTempAllocationKeyToDevice(deviceLocator2, "session_id", Duration.ZERO);
  }

  @Test
  public void onTestStarting_setupDeviceReservationFailsPartway_rollsBackAndContinuesUnreserved()
      throws Exception {
    JobInfo setupJob = mockJobInfo("setup_job_id", XtsConstants.SETUP_JOB_NAME);
    TestInfo setupTest = attachMockSetupTest(setupJob);

    SubDeviceSpec tfSpec = createSubDeviceSpec();
    JobInfo tfJob = mockJobInfoWithSubDevices("tf_id", "tf_job", ImmutableList.of(tfSpec));

    when(xtsJobCreator.createXtsSetupJob(MODULE_SHARDING_REQUEST_INFO))
        .thenReturn(Optional.of(setupJob));
    when(xtsJobCreator.createXtsTradefedTestJob(eq(MODULE_SHARDING_REQUEST_INFO), any(), eq(false)))
        .thenReturn(ImmutableList.of(tfJob));

    DeviceLocator deviceLocator1 = new DeviceLocator("device_1", LabLocator.LOCALHOST);
    DeviceLocator deviceLocator2 = new DeviceLocator("device_2", LabLocator.LOCALHOST);
    doThrow(
            new MobileHarnessException(
                InfraErrorId.XTS_NO_JOB_CREATED_FOR_SESSION, "Reservation error"))
        .when(deviceReserver)
        .addTempAllocationKeyToDevice(
            deviceLocator2, "session_id", SessionRequestHandlerUtil.DEFAULT_TRADEFED_JOB_TIMEOUT);

    orchestrator.startSession(MODULE_SHARDING_REQUEST_INFO);
    orchestrator.onTestStarting(setupTest, ImmutableList.of(deviceLocator1, deviceLocator2));
    orchestrator.onJobEnd(
        new JobEndEvent(setupJob, /* jobError= */ null), MODULE_SHARDING_REQUEST_INFO);

    verify(deviceReserver)
        .addTempAllocationKeyToDevice(
            deviceLocator1, "session_id", SessionRequestHandlerUtil.DEFAULT_TRADEFED_JOB_TIMEOUT);
    verify(deviceReserver)
        .addTempAllocationKeyToDevice(deviceLocator1, "session_id", Duration.ZERO);
    verify(deviceReserver, never())
        .addTempAllocationKeyToDevice(deviceLocator2, "session_id", Duration.ZERO);
    assertThat(tfSpec.dimensions().get(Name.ALLOCATION_KEY)).isNull();
  }

  @Test
  public void onJobEnd_setupJobWithoutTeardownJob_releasesReservedDevicesAfterMainJobsEnd()
      throws Exception {
    JobInfo setupJob = mockJobInfo("setup_job_id", XtsConstants.SETUP_JOB_NAME);
    TestInfo setupTest = attachMockSetupTest(setupJob);

    SubDeviceSpec tfSpec = createSubDeviceSpec();
    JobInfo tfJob = mockJobInfoWithSubDevices("tf_id", "tf_job", ImmutableList.of(tfSpec));

    SessionRequestInfo narrowedRequestInfo = narrowRequest(DEFAULT_REQUEST_INFO, "device_1");
    when(xtsJobCreator.createXtsSetupJob(DEFAULT_REQUEST_INFO)).thenReturn(Optional.of(setupJob));
    when(xtsJobCreator.createXtsTearDownJob(narrowedRequestInfo)).thenReturn(Optional.empty());
    when(xtsJobCreator.createXtsTradefedTestJob(eq(narrowedRequestInfo), any(), eq(false)))
        .thenReturn(ImmutableList.of(tfJob));

    DeviceLocator deviceLocator = new DeviceLocator("device_1", LabLocator.LOCALHOST);

    orchestrator.startSession(DEFAULT_REQUEST_INFO);
    orchestrator.onTestStarting(setupTest, ImmutableList.of(deviceLocator));
    orchestrator.onJobEnd(new JobEndEvent(setupJob, /* jobError= */ null), DEFAULT_REQUEST_INFO);
    orchestrator.onJobEnd(new JobEndEvent(tfJob, /* jobError= */ null), DEFAULT_REQUEST_INFO);

    assertThat(tfSpec.dimensions().get(Name.ALLOCATION_KEY)).isEqualTo("session_id");
    verify(deviceReserver).addTempAllocationKeyToDevice(deviceLocator, "session_id", Duration.ZERO);
  }

  @Test
  public void onSessionCancellation_afterSetupDevicesReserved_releasesReservedDevices()
      throws Exception {
    JobInfo setupJob = mockJobInfo("setup_job_id", XtsConstants.SETUP_JOB_NAME);
    TestInfo setupTest = attachMockSetupTest(setupJob);

    SessionRequestInfo narrowedRequestInfo = narrowRequest(DEFAULT_REQUEST_INFO, "device_1");
    JobInfo tfJob = mockJobInfo("tf_id", "tf_job");
    when(xtsJobCreator.createXtsSetupJob(DEFAULT_REQUEST_INFO)).thenReturn(Optional.of(setupJob));
    when(xtsJobCreator.createXtsTradefedTestJob(eq(narrowedRequestInfo), any(), eq(false)))
        .thenReturn(ImmutableList.of(tfJob));

    DeviceLocator deviceLocator = new DeviceLocator("device_1", LabLocator.LOCALHOST);

    orchestrator.startSession(DEFAULT_REQUEST_INFO);
    orchestrator.onTestStarting(setupTest, ImmutableList.of(deviceLocator));
    orchestrator.onJobEnd(new JobEndEvent(setupJob, /* jobError= */ null), DEFAULT_REQUEST_INFO);

    orchestrator.onSessionCancellation("Cancelled by user", 15);

    verify(deviceReserver).addTempAllocationKeyToDevice(deviceLocator, "session_id", Duration.ZERO);
  }

  @Test
  public void onSessionEnded_beforeTeardownEnds_releasesReservedDevices() throws Exception {
    JobInfo setupJob = mockJobInfo("setup_job_id", XtsConstants.SETUP_JOB_NAME);
    TestInfo setupTest = attachMockSetupTest(setupJob);

    SessionRequestInfo narrowedRequestInfo = narrowRequest(DEFAULT_REQUEST_INFO, "device_1");
    JobInfo tfJob = mockJobInfo("tf_id", "tf_job");
    JobInfo teardownJob = mockJobInfo("teardown_id", XtsConstants.TEARDOWN_JOB_NAME);
    when(xtsJobCreator.createXtsSetupJob(DEFAULT_REQUEST_INFO)).thenReturn(Optional.of(setupJob));
    when(xtsJobCreator.createXtsTearDownJob(narrowedRequestInfo))
        .thenReturn(Optional.of(teardownJob));
    when(xtsJobCreator.createXtsTradefedTestJob(eq(narrowedRequestInfo), any(), eq(false)))
        .thenReturn(ImmutableList.of(tfJob));

    DeviceLocator deviceLocator = new DeviceLocator("device_1", LabLocator.LOCALHOST);

    orchestrator.startSession(DEFAULT_REQUEST_INFO);
    orchestrator.onTestStarting(setupTest, ImmutableList.of(deviceLocator));
    orchestrator.onJobEnd(new JobEndEvent(setupJob, /* jobError= */ null), DEFAULT_REQUEST_INFO);

    orchestrator.onSessionEnded();

    verify(deviceReserver).addTempAllocationKeyToDevice(deviceLocator, "session_id", Duration.ZERO);
  }

  @Test
  public void onJobEnd_setupJobMainJobCreationFails_releasesReservedDevices() throws Exception {
    JobInfo setupJob = mockJobInfo("setup_job_id", XtsConstants.SETUP_JOB_NAME);
    TestInfo setupTest = attachMockSetupTest(setupJob);
    when(xtsJobCreator.createXtsSetupJob(DEFAULT_REQUEST_INFO)).thenReturn(Optional.of(setupJob));

    DeviceLocator deviceLocator = new DeviceLocator("device_1", LabLocator.LOCALHOST);

    orchestrator.startSession(DEFAULT_REQUEST_INFO);
    orchestrator.onTestStarting(setupTest, ImmutableList.of(deviceLocator));

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () ->
                orchestrator.onJobEnd(
                    new JobEndEvent(setupJob, /* jobError= */ null), DEFAULT_REQUEST_INFO));

    assertThat(exception.getErrorId()).isEqualTo(InfraErrorId.XTS_NO_JOB_CREATED_FOR_SESSION);
    verify(deviceReserver).addTempAllocationKeyToDevice(deviceLocator, "session_id", Duration.ZERO);
  }

  @Test
  public void onJobEnd_partialDeviceMatchSetupJob_narrowsSubsequentJobsToReservedDevices()
      throws Exception {
    SessionRequestInfo partialMatchRequest =
        SessionRequestInfo.newBuilder()
            .setIsAtsServerRequest(true)
            .setAllowPartialDeviceMatch(true)
            .addDeviceSerials("device_1")
            .addDeviceSerials("device_2")
            .addDeviceSerials("device_3")
            .build();
    SessionRequestInfo expectedNarrowedRequest =
        narrowRequest(partialMatchRequest, "device_1", "device_2");

    JobInfo setupJob = mockJobInfo("setup_job_id", XtsConstants.SETUP_JOB_NAME);
    TestInfo setupTest = attachMockSetupTest(setupJob);

    SubDeviceSpec tfSpec1 = createSubDeviceSpec(new Dimensions().add(Name.UUID, "device_1"));
    SubDeviceSpec tfSpec2 = createSubDeviceSpec(new Dimensions().add(Name.UUID, "device_2"));
    JobInfo tfJob =
        mockJobInfoWithSubDevices("tf_id", "tf_job", ImmutableList.of(tfSpec1, tfSpec2));
    JobInfo teardownJob = mockJobInfo("teardown_id", XtsConstants.TEARDOWN_JOB_NAME);

    when(xtsJobCreator.createXtsSetupJob(partialMatchRequest)).thenReturn(Optional.of(setupJob));
    when(xtsJobCreator.createXtsTradefedTestJob(eq(expectedNarrowedRequest), any(), eq(false)))
        .thenReturn(ImmutableList.of(tfJob));
    when(xtsJobCreator.createXtsTearDownJob(expectedNarrowedRequest))
        .thenReturn(Optional.of(teardownJob));

    // Only device_1 and device_2 were IDLE and allocated when setupJob ran (device_3 was BUSY).
    DeviceLocator deviceLocator1 = new DeviceLocator("device_1", LabLocator.LOCALHOST);
    DeviceLocator deviceLocator2 = new DeviceLocator("device_2", LabLocator.LOCALHOST);

    orchestrator.startSession(partialMatchRequest);
    verify(xtsJobCreator, never()).createXtsTearDownJob(any());
    orchestrator.onTestStarting(setupTest, ImmutableList.of(deviceLocator1, deviceLocator2));
    orchestrator.onJobEnd(new JobEndEvent(setupJob, /* jobError= */ null), partialMatchRequest);

    verify(xtsJobCreator).createXtsTradefedTestJob(eq(expectedNarrowedRequest), any(), eq(false));
    verify(xtsJobCreator).createXtsNonTradefedJobs(expectedNarrowedRequest);
    verify(xtsJobCreator).createXtsTearDownJob(expectedNarrowedRequest);
    assertThat(tfSpec1.dimensions().get(Name.ALLOCATION_KEY)).isEqualTo("session_id");
    assertThat(tfSpec2.dimensions().get(Name.ALLOCATION_KEY)).isEqualTo("session_id");
  }

  @Test
  public void onJobEnd_multiDeviceMoblyJob_stampsAllSubDeviceSpecs() throws Exception {
    JobInfo setupJob = mockJobInfo("setup_job_id", XtsConstants.SETUP_JOB_NAME);
    TestInfo setupTest = attachMockSetupTest(setupJob);

    // 2-device Mobly module: all SubDeviceSpecs must receive allocation_key so no slot can match an
    // unprepared device.
    SubDeviceSpec subDeviceSpec1 = createSubDeviceSpec();
    SubDeviceSpec subDeviceSpec2 = createSubDeviceSpec();
    JobInfo twoDeviceMoblyJob =
        mockJobInfoWithSubDevices(
            "mobly_2dev", "mobly_2dev_job", ImmutableList.of(subDeviceSpec1, subDeviceSpec2));

    SessionRequestInfo narrowedRequestInfo = narrowRequest(DEFAULT_REQUEST_INFO, "device_1");
    when(xtsJobCreator.createXtsSetupJob(DEFAULT_REQUEST_INFO)).thenReturn(Optional.of(setupJob));
    when(xtsJobCreator.createXtsNonTradefedJobs(narrowedRequestInfo))
        .thenReturn(ImmutableList.of(twoDeviceMoblyJob));

    DeviceLocator deviceLocator1 = new DeviceLocator("device_1", LabLocator.LOCALHOST);

    orchestrator.startSession(DEFAULT_REQUEST_INFO);
    orchestrator.onTestStarting(setupTest, ImmutableList.of(deviceLocator1));
    orchestrator.onJobEnd(new JobEndEvent(setupJob, /* jobError= */ null), DEFAULT_REQUEST_INFO);

    assertThat(subDeviceSpec1.dimensions().get(Name.ALLOCATION_KEY)).isEqualTo("session_id");
    assertThat(subDeviceSpec2.dimensions().get(Name.ALLOCATION_KEY)).isEqualTo("session_id");
  }
}
