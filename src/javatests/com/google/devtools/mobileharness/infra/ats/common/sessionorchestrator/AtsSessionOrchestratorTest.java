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
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.ShardingMode;
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
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
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

  private static final String SESSION_ID = "session_id_123";
  private static final JobType TRADEFED_JOB_TYPE =
      JobType.newBuilder().setDriver("TradefedTest").build();

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock private SessionInfo sessionInfo;
  @Mock private SessionOrchestratorDelegate delegate;
  @Mock private TestMessageUtil testMessageUtil;

  private AtsSessionOrchestrator orchestrator;

  @Before
  public void setUp() {
    when(sessionInfo.getSessionId()).thenReturn(SESSION_ID);
    orchestrator = new AtsSessionOrchestrator(sessionInfo, delegate, testMessageUtil);
  }

  private static JobInfo createMockJob(String jobId, String jobName) {
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

  private static TestInfos createMockTestInfos(String testId, Properties properties) {
    TestInfo testInfo = mock(TestInfo.class);
    when(testInfo.locator())
        .thenReturn(new TestLocator(testId, testId, new JobLocator("job_id", "job_name")));
    when(testInfo.properties()).thenReturn(properties);
    TestInfos testInfos = mock(TestInfos.class);
    when(testInfos.getAll()).thenReturn(ImmutableListMultimap.of(testId, testInfo));
    return testInfos;
  }

  @Test
  public void startSession_withSetupJob_addsSetupJobOnly() throws Exception {
    JobInfo setupJob = createMockJob("setup_job_id", "setup_job");
    when(delegate.createSetupJob()).thenReturn(Optional.of(setupJob));
    when(delegate.createTeardownJob()).thenReturn(Optional.empty());

    orchestrator.startSession();

    verify(sessionInfo).addJob(setupJob);
    verify(delegate, never()).createTradefedJobs(any(), anyBoolean());
    verify(delegate, never()).createNonTradefedJobs();
  }

  @Test
  public void startSession_withoutSetupJob_createsAndAddsMainJobs() throws Exception {
    JobInfo tfJob = createMockJob("tf_job_id", "tradefed_job");
    JobInfo nonTfJob = createMockJob("non_tf_job_id", "non_tf_job");
    when(delegate.createSetupJob()).thenReturn(Optional.empty());
    when(delegate.createTeardownJob()).thenReturn(Optional.empty());
    when(delegate.createTradefedJobs(ImmutableSet.of(), false)).thenReturn(ImmutableList.of(tfJob));
    when(delegate.createNonTradefedJobs()).thenReturn(ImmutableList.of(nonTfJob));
    when(delegate.getEffectiveShardingMode()).thenReturn(ShardingMode.MODULE);

    orchestrator.startSession();

    verify(sessionInfo).addJob(tfJob);
    // Non-TF job is queued until TF jobs complete.
    verify(sessionInfo, never()).addJob(nonTfJob);
  }

  @Test
  public void startSession_noJobsCreated_throwsException() throws Exception {
    when(delegate.createSetupJob()).thenReturn(Optional.empty());
    when(delegate.createTeardownJob()).thenReturn(Optional.empty());
    when(delegate.createTradefedJobs(any(), anyBoolean())).thenReturn(ImmutableList.of());
    when(delegate.createNonTradefedJobs()).thenReturn(ImmutableList.of());

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> orchestrator.startSession());
    assertThat(thrown.getErrorId()).isEqualTo(InfraErrorId.XTS_NO_JOB_CREATED_FOR_SESSION);
  }

  @Test
  public void onJobEnd_setupJobEnds_extractsMctsAndStartsMainJobs() throws Exception {
    JobInfo setupJob = createMockJob("setup_job_id", "setup_job");
    Properties properties = new Properties(new Timing());
    properties.add(
        XtsConstants.XTS_DYNAMIC_DOWNLOAD_TEST_MODULES_PROPERTY_KEY, "CtsModule1,CtsModule2");
    properties.add(
        XtsConstants.XTS_DYNAMIC_DOWNLOAD_HAS_PRELOADED_MAINLINE_MODULES_PROPERTY_KEY, "true");
    TestInfos testInfos = createMockTestInfos("setup_test_1", properties);
    when(setupJob.tests()).thenReturn(testInfos);

    when(delegate.createSetupJob()).thenReturn(Optional.of(setupJob));
    when(delegate.createTeardownJob()).thenReturn(Optional.empty());
    orchestrator.startSession();

    JobInfo mainTfJob = createMockJob("main_tf_job_id", "main_tf_job");
    when(delegate.createTradefedJobs(ImmutableSet.of("CtsModule1", "CtsModule2"), false))
        .thenReturn(ImmutableList.of(mainTfJob));
    when(delegate.createNonTradefedJobs()).thenReturn(ImmutableList.of());
    when(delegate.getEffectiveShardingMode()).thenReturn(ShardingMode.MODULE);

    orchestrator.onJobEnd(new JobEndEvent(setupJob, null));

    verify(sessionInfo).addJob(mainTfJob);
  }

  @Test
  public void onJobEnd_tradefedJobEnds_runnerShardingPinsDevicesAndAddsNext() throws Exception {
    JobInfo staticTfJob = createMockJob("static_tf_id", XtsConstants.STATIC_XTS_JOB_NAME);
    JobInfo dynamicTfJob = createMockJob("dynamic_tf_id", XtsConstants.DYNAMIC_MCTS_JOB_NAME);

    Properties testProps = new Properties(new Timing());
    testProps.add(PropertyName.Test.DEVICE_ID_LIST, "device_serial_1,device_serial_2");
    TestInfos staticTestInfos = createMockTestInfos("test_1", testProps);
    when(staticTfJob.tests()).thenReturn(staticTestInfos);

    SubDeviceSpecs subDeviceSpecs = mock(SubDeviceSpecs.class);
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
    when(subDeviceSpecs.getAllSubDevices()).thenReturn(ImmutableList.of(spec1, spec2));
    when(dynamicTfJob.subDeviceSpecs()).thenReturn(subDeviceSpecs);

    when(delegate.createSetupJob()).thenReturn(Optional.empty());
    when(delegate.createTeardownJob()).thenReturn(Optional.empty());
    when(delegate.createTradefedJobs(any(), anyBoolean()))
        .thenReturn(ImmutableList.of(staticTfJob, dynamicTfJob));
    when(delegate.createNonTradefedJobs()).thenReturn(ImmutableList.of());
    when(delegate.getEffectiveShardingMode()).thenReturn(ShardingMode.RUNNER);

    orchestrator.startSession();
    // Static job added, dynamic queued.
    verify(sessionInfo).addJob(staticTfJob);
    verify(sessionInfo, never()).addJob(dynamicTfJob);

    // When static job ends, dynamic job is pinned and added.
    orchestrator.onJobEnd(new JobEndEvent(staticTfJob, null));
    assertThat(spec1.dimensions().get(Name.ID.lowerCaseName())).isEqualTo("device_serial_1");
    assertThat(spec2.dimensions().get(Name.ID.lowerCaseName())).isEqualTo("device_serial_2");
    verify(sessionInfo).addJob(dynamicTfJob);
  }

  @Test
  public void onJobEnd_allTfJobsEnd_addsNonTfJobs() throws Exception {
    JobInfo tfJob = createMockJob("tf_job_id", "tf_job");
    JobInfo nonTfJob = createMockJob("non_tf_job_id", "non_tf_job");

    when(delegate.createSetupJob()).thenReturn(Optional.empty());
    when(delegate.createTeardownJob()).thenReturn(Optional.empty());
    when(delegate.createTradefedJobs(any(), anyBoolean())).thenReturn(ImmutableList.of(tfJob));
    when(delegate.createNonTradefedJobs()).thenReturn(ImmutableList.of(nonTfJob));
    when(delegate.getEffectiveShardingMode()).thenReturn(ShardingMode.MODULE);

    orchestrator.startSession();
    verify(sessionInfo).addJob(tfJob);
    verify(sessionInfo, never()).addJob(nonTfJob);

    orchestrator.onJobEnd(new JobEndEvent(tfJob, null));
    verify(sessionInfo).addJob(nonTfJob);
  }

  @Test
  public void onJobEnd_allJobsEnd_addsTeardownJob() throws Exception {
    JobInfo tfJob = createMockJob("tf_job_id", "tf_job");
    JobInfo teardownJob = createMockJob("teardown_job_id", "teardown_job");

    when(delegate.createSetupJob()).thenReturn(Optional.empty());
    when(delegate.createTeardownJob()).thenReturn(Optional.of(teardownJob));
    when(delegate.createTradefedJobs(any(), anyBoolean())).thenReturn(ImmutableList.of(tfJob));
    when(delegate.createNonTradefedJobs()).thenReturn(ImmutableList.of());
    when(delegate.getEffectiveShardingMode()).thenReturn(ShardingMode.MODULE);

    orchestrator.startSession();
    orchestrator.onJobEnd(new JobEndEvent(tfJob, null));

    verify(sessionInfo).addJob(teardownJob);
  }

  @Test
  public void onJobEnd_resumedSessionWithConcurrentTfJobs_waitsForAllTfJobsBeforeAddingNonTfJobs()
      throws Exception {
    JobInfo tfJob1 = createMockJob("tf_job_1_id", "tf_job_1");
    JobInfo tfJob2 = createMockJob("tf_job_2_id", "tf_job_2");
    JobInfo tfJob3 = createMockJob("tf_job_3_id", "tf_job_3");
    JobInfo nonTfJob = createMockJob("non_tf_job_id", "non_tf_job");

    when(tfJob1.type()).thenReturn(TRADEFED_JOB_TYPE);
    when(tfJob1.status()).thenReturn(new Status(new Timing()).set(TestStatus.DONE));
    when(tfJob2.type()).thenReturn(TRADEFED_JOB_TYPE);
    when(tfJob2.status()).thenReturn(new Status(new Timing()).set(TestStatus.RUNNING));
    when(tfJob3.type()).thenReturn(TRADEFED_JOB_TYPE);
    when(tfJob3.status()).thenReturn(new Status(new Timing()).set(TestStatus.NEW));

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(tfJob1, tfJob2, tfJob3));
    when(delegate.createTradefedJobs(ImmutableSet.of(), false))
        .thenReturn(ImmutableList.of(tfJob1, tfJob2, tfJob3));
    when(delegate.createNonTradefedJobs()).thenReturn(ImmutableList.of(nonTfJob));
    when(delegate.createTeardownJob()).thenReturn(Optional.empty());
    when(delegate.getEffectiveShardingMode()).thenReturn(ShardingMode.MODULE);

    // First TF job finishes while second is RUNNING and third is NEW after restart.
    orchestrator.onJobEnd(new JobEndEvent(tfJob1, null));
    verify(sessionInfo, never()).addJob(nonTfJob);

    // Second TF job finishes while third is still NEW.
    when(tfJob2.status()).thenReturn(new Status(new Timing()).set(TestStatus.DONE));
    orchestrator.onJobEnd(new JobEndEvent(tfJob2, null));
    verify(sessionInfo, never()).addJob(nonTfJob);

    // Third TF job finishes. Non-TF job should now be scheduled once.
    when(tfJob3.status()).thenReturn(new Status(new Timing()).set(TestStatus.DONE));
    orchestrator.onJobEnd(new JobEndEvent(tfJob3, null));
    verify(sessionInfo).addJob(nonTfJob);
  }

  @Test
  public void onJobEnd_resumedSessionSetupJobEnds_createsAndSchedulesTeardownJobAfterMainJobs()
      throws Exception {
    JobInfo setupJob = createMockJob("setup_job_id", XtsConstants.SETUP_JOB_NAME);
    Properties setupProps = new Properties(new Timing());
    setupProps.add(XtsConstants.XTS_DYNAMIC_DOWNLOAD_TEST_MODULES_PROPERTY_KEY, "CtsModule1");
    setupProps.add(
        XtsConstants.XTS_DYNAMIC_DOWNLOAD_HAS_PRELOADED_MAINLINE_MODULES_PROPERTY_KEY, "true");
    TestInfos setupTestInfos = createMockTestInfos("setup_test", setupProps);
    when(setupJob.tests()).thenReturn(setupTestInfos);

    JobInfo mainTfJob = createMockJob("main_tf_job_id", "main_tf_job");
    JobInfo teardownJob = createMockJob("teardown_job_id", XtsConstants.TEARDOWN_JOB_NAME);

    when(delegate.createTeardownJob()).thenReturn(Optional.of(teardownJob));
    when(delegate.createTradefedJobs(ImmutableSet.of("CtsModule1"), false))
        .thenReturn(ImmutableList.of(mainTfJob));
    when(delegate.createNonTradefedJobs()).thenReturn(ImmutableList.of());
    when(delegate.getEffectiveShardingMode()).thenReturn(ShardingMode.MODULE);

    // Setup job ends after restart (startSession was not called on this orchestrator instance).
    orchestrator.onJobEnd(new JobEndEvent(setupJob, null));
    verify(delegate).createTeardownJob();
    verify(sessionInfo).addJob(mainTfJob);
    verify(sessionInfo, never()).addJob(teardownJob);

    // When main TF job finishes, teardown job is scheduled.
    orchestrator.onJobEnd(new JobEndEvent(mainTfJob, null));
    verify(sessionInfo).addJob(teardownJob);
  }

  @Test
  public void onJobEnd_resumedSessionRunnerSharding_pinsDevicesAndSchedulesNextTradefedJob()
      throws Exception {
    JobInfo staticTfJob = createMockJob("static_tf_id", XtsConstants.STATIC_XTS_JOB_NAME);
    JobInfo dynamicTfJob = createMockJob("dynamic_tf_id", XtsConstants.DYNAMIC_MCTS_JOB_NAME);

    when(staticTfJob.type()).thenReturn(TRADEFED_JOB_TYPE);
    when(staticTfJob.status()).thenReturn(new Status(new Timing()).set(TestStatus.DONE));
    Properties testProps = new Properties(new Timing());
    testProps.add(PropertyName.Test.DEVICE_ID_LIST, "device_serial_1,device_serial_2");
    TestInfos staticTestInfos = createMockTestInfos("test_1", testProps);
    when(staticTfJob.tests()).thenReturn(staticTestInfos);

    SubDeviceSpecs subDeviceSpecs = mock(SubDeviceSpecs.class);
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
    when(subDeviceSpecs.getAllSubDevices()).thenReturn(ImmutableList.of(spec1, spec2));
    when(dynamicTfJob.subDeviceSpecs()).thenReturn(subDeviceSpecs);

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(staticTfJob));
    when(delegate.createTradefedJobs(ImmutableSet.of(), false))
        .thenReturn(ImmutableList.of(staticTfJob, dynamicTfJob));
    when(delegate.createNonTradefedJobs()).thenReturn(ImmutableList.of());
    when(delegate.createTeardownJob()).thenReturn(Optional.empty());
    when(delegate.getEffectiveShardingMode()).thenReturn(ShardingMode.RUNNER);

    // Static TF job ends after restart; dynamic TF job should be pinned and scheduled.
    orchestrator.onJobEnd(new JobEndEvent(staticTfJob, null));
    assertThat(spec1.dimensions().get(Name.ID.lowerCaseName())).isEqualTo("device_serial_1");
    assertThat(spec2.dimensions().get(Name.ID.lowerCaseName())).isEqualTo("device_serial_2");
    verify(sessionInfo).addJob(dynamicTfJob);
  }

  @Test
  public void startSession_runnerShardingMultipleStaticJobs_startsFirstAndQueuesRemaining()
      throws Exception {
    JobInfo staticJob1 = createMockJob("static_1_id", XtsConstants.STATIC_XTS_JOB_NAME + "_1");
    JobInfo staticJob2 = createMockJob("static_2_id", XtsConstants.STATIC_XTS_JOB_NAME + "_2");
    JobInfo dynamicJob = createMockJob("dynamic_id", XtsConstants.DYNAMIC_MCTS_JOB_NAME);

    when(delegate.createSetupJob()).thenReturn(Optional.empty());
    when(delegate.createTeardownJob()).thenReturn(Optional.empty());
    when(delegate.createTradefedJobs(any(), anyBoolean()))
        .thenReturn(ImmutableList.of(staticJob1, staticJob2, dynamicJob));
    when(delegate.createNonTradefedJobs()).thenReturn(ImmutableList.of());
    when(delegate.getEffectiveShardingMode()).thenReturn(ShardingMode.RUNNER);

    orchestrator.startSession();
    verify(sessionInfo).addJob(staticJob1);
    verify(sessionInfo, never()).addJob(staticJob2);
    verify(sessionInfo, never()).addJob(dynamicJob);

    orchestrator.onJobEnd(new JobEndEvent(staticJob1, null));
    verify(sessionInfo).addJob(staticJob2);
    verify(sessionInfo, never()).addJob(dynamicJob);

    orchestrator.onJobEnd(new JobEndEvent(staticJob2, null));
    verify(sessionInfo).addJob(dynamicJob);
  }

  @Test
  public void onJobEnd_resumedSessionNonTfJobEnds_schedulesRemainingNonTfJobsAndFiltersTriggered()
      throws Exception {
    JobInfo tfJob = createMockJob("tf_job_id", "tf_job");
    JobInfo nonTfJob1 = createMockJob("non_tf_1_id", "non_tf_job_1");
    JobInfo nonTfJob2 = createMockJob("non_tf_2_id", "non_tf_job_2");
    JobInfo teardownJob = createMockJob("teardown_id", XtsConstants.TEARDOWN_JOB_NAME);

    when(tfJob.type()).thenReturn(TRADEFED_JOB_TYPE);
    when(tfJob.status()).thenReturn(new Status(new Timing()).set(TestStatus.DONE));
    when(nonTfJob1.status()).thenReturn(new Status(new Timing()).set(TestStatus.DONE));

    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(tfJob, nonTfJob1));
    when(delegate.createTradefedJobs(ImmutableSet.of(), false)).thenReturn(ImmutableList.of(tfJob));
    when(delegate.createNonTradefedJobs()).thenReturn(ImmutableList.of(nonTfJob1, nonTfJob2));
    when(delegate.createTeardownJob()).thenReturn(Optional.of(teardownJob));

    // nonTfJob1 ends after restart; nonTfJob1 is filtered out and nonTfJob2 is scheduled.
    orchestrator.onJobEnd(new JobEndEvent(nonTfJob1, null));
    verify(sessionInfo, never()).addJob(nonTfJob1);
    verify(sessionInfo).addJob(nonTfJob2);
    verify(sessionInfo, never()).addJob(teardownJob);

    // When nonTfJob2 finishes, teardownJob is scheduled.
    orchestrator.onJobEnd(new JobEndEvent(nonTfJob2, null));
    verify(sessionInfo).addJob(teardownJob);
  }

  @Test
  public void onSessionCancellation_broadcastsTestMessageToStartedTests() throws Exception {
    TestInfo testInfo = mock(TestInfo.class);
    when(testInfo.locator())
        .thenReturn(new TestLocator("test_id_1", "test_1", new JobLocator("job_id", "job_name")));

    orchestrator.onTestStarting(testInfo);

    XtsTradefedRunCancellation cancellationMessage =
        XtsTradefedRunCancellation.newBuilder()
            .setKillTradefedSignal(2)
            .setCancelReason("Test cancelled")
            .build();

    orchestrator.onSessionCancellation(cancellationMessage);

    verify(testMessageUtil).sendProtoMessageToTest(testInfo, cancellationMessage);
  }

  @Test
  public void onTestStarting_afterCancellation_immediatelyCancelsNewTest() throws Exception {
    XtsTradefedRunCancellation cancellationMessage =
        XtsTradefedRunCancellation.newBuilder()
            .setKillTradefedSignal(2)
            .setCancelReason("Test cancelled")
            .build();
    orchestrator.onSessionCancellation(cancellationMessage);

    TestInfo newTest = mock(TestInfo.class);
    when(newTest.locator())
        .thenReturn(new TestLocator("test_id_2", "test_2", new JobLocator("job_id", "job_name")));

    orchestrator.onTestStarting(newTest);

    verify(testMessageUtil).sendProtoMessageToTest(newTest, cancellationMessage);
  }

  @Test
  public void onSessionEnded_preventsAddingJobsToSession() throws Exception {
    JobInfo setupJob = createMockJob("setup_job_id", "setup_job");
    when(delegate.createSetupJob()).thenReturn(Optional.of(setupJob));
    when(delegate.createTeardownJob()).thenReturn(Optional.empty());

    orchestrator.onSessionEnded();
    orchestrator.startSession();

    verify(sessionInfo, never()).addJob(any());
  }
}
