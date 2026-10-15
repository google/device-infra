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
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
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

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private SessionInfo sessionInfo;
  @Mock private XtsJobCreator xtsJobCreator;
  @Mock private TestMessageUtil testMessageUtil;

  private AtsSessionOrchestrator orchestrator;

  @Before
  public void setUp() throws Exception {
    when(sessionInfo.getSessionId()).thenReturn("session_id");
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
    when(jobInfo.properties()).thenReturn(new Properties(new Timing()));
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
    SessionRequestInfo moduleShardingRequest =
        SessionRequestInfo.newBuilder().setShardingMode(ShardingMode.MODULE).build();
    JobInfo staticJob = mockJobInfo("static_job_id", "m1_" + XtsConstants.STATIC_XTS_JOB_NAME);
    JobInfo dynamicJob = mockJobInfo("dynamic_job_id", "m2_" + XtsConstants.DYNAMIC_MCTS_JOB_NAME);
    when(xtsJobCreator.createXtsTradefedTestJob(
            moduleShardingRequest, ImmutableSet.of(), /* skipDynamicMctsJob= */ false))
        .thenReturn(ImmutableList.of(staticJob, dynamicJob));

    orchestrator.startSession(moduleShardingRequest);

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
}
