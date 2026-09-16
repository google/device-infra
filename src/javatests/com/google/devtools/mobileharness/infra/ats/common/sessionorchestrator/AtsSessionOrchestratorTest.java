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
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
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
    when(jobInfo.properties()).thenReturn(new Properties(new Timing()));
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
