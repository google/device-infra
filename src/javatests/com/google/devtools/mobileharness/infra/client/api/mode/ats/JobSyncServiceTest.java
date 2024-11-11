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

package com.google.devtools.mobileharness.infra.client.api.mode.ats;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.util.Objects.requireNonNull;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceAllocationPriority;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceRequirement;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceRequirements;
import com.google.devtools.mobileharness.api.model.proto.Job.JobFeature;
import com.google.devtools.mobileharness.api.model.proto.Job.JobFile;
import com.google.devtools.mobileharness.api.model.proto.Job.JobSetting;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.devtools.mobileharness.api.model.proto.Job.Priority;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry.Level;
import com.google.devtools.mobileharness.api.model.proto.Job.Timeout;
import com.google.devtools.mobileharness.api.model.proto.Test.TestIdName;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.Annotations.AtsModeAbstractScheduler;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler.JobWithTests;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler.JobsAndAllocations;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.OpenJobRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.OpenJobResponse;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.proto.Version.VersionCheckRequest;
import com.google.devtools.mobileharness.shared.version.proto.Version.VersionCheckResponse;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.Job;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
public class JobSyncServiceTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private static final OpenJobRequest OPEN_JOB_REQUEST =
      OpenJobRequest.newBuilder()
          .setVersionCheckRequest(
              VersionCheckRequest.newBuilder()
                  .setStubVersion(Version.CLIENT_VERSION.toString())
                  .setMinServiceVersion(Version.MIN_MASTER_V5_VERSION.toString()))
          .setId("fake_job_id")
          .setName("fake_job_name")
          .setFeature(
              JobFeature.newBuilder()
                  .setUser(
                      JobUser.newBuilder()
                          .setRunAs("fake_run_as")
                          .setActualUser("fake_actual_user")
                          .setJobAccessAccount("fake_job_access_account"))
                  .setDriver("FakeDriver")
                  .setDeviceRequirements(
                      DeviceRequirements.newBuilder()
                          .addDeviceRequirement(
                              DeviceRequirement.newBuilder()
                                  .setDeviceType("FakeDeviceType1")
                                  .addDecorator("FakeDevice1Decorator1")
                                  .addDecorator("FakeDevice1Decorator2")
                                  .putDimensions(
                                      "fake_device1_dimension1_key",
                                      "fake_device1_dimension1_value")
                                  .putDimensions(
                                      "fake_device1_dimension2_key",
                                      "fake_device1_dimension2_value"))
                          .addDeviceRequirement(
                              DeviceRequirement.newBuilder()
                                  .setDeviceType("FakeDeviceType2")
                                  .addDecorator("FakeDevice2Decorator1")
                                  .addDecorator("FakeDevice2Decorator2")
                                  .putDimensions(
                                      "fake_device2_dimension1_key",
                                      "fake_device2_dimension1_value")
                                  .putDimensions(
                                      "fake_device2_dimension2_key",
                                      "fake_device2_dimension2_value"))
                          .addSharedDimension("fake_shared_dimension_name"))
                  .setDeviceAllocationPriority(
                      DeviceAllocationPriority.DEVICE_ALLOCATION_PRIORITY_LOW))
          .setSetting(
              JobSetting.newBuilder()
                  .setTimeout(
                      Timeout.newBuilder()
                          .setJobTimeoutMs(Duration.ofHours(2L).toMillis())
                          .setTestTimeoutMs(Duration.ofMinutes(30L).toMillis())
                          .setStartTimeoutMs(Duration.ofMinutes(10L).toMillis()))
                  .setRetry(Retry.newBuilder().setTestAttempts(4).setRetryLevel(Level.FAIL))
                  .setPriority(Priority.HIGH))
          .addFile(JobFile.newBuilder().setTag("fake_file_tag").addLocation("fake_file_location"))
          .putParam("fake_param_name", "fake_param_value")
          .addTest(TestIdName.newBuilder().setId("fake_test_id").setName("fake_test_name"))
          .setKeepAliveTimeoutMs(Duration.ofMinutes(10L).toMillis())
          .build();

  private static final OpenJobResponse OPEN_JOB_RESPONSE =
      OpenJobResponse.newBuilder()
          .setVersionCheckResponse(
              VersionCheckResponse.newBuilder()
                  .setServiceVersion(Version.MASTER_V5_VERSION.toString()))
          .build();

  @Bind
  private final ListeningExecutorService threadPool =
      ThreadPools.createStandardThreadPool("testing-thread-pool");

  @Bind
  private final ListeningScheduledExecutorService scheduledThreadPool =
      ThreadPools.createStandardScheduledThreadPool("testing-scheduled-thread-pool", 10);

  @Bind private final Sleeper sleeper = Sleeper.defaultSleeper();

  @Bind @Mock private Clock clock;

  @Inject private JobSyncService jobSyncService;
  @Inject @AtsModeAbstractScheduler private AbstractScheduler scheduler;

  @Before
  public void setUp() throws Exception {
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(123L));

    Guice.createInjector(BoundFieldModule.of(this), new AtsModeModule()).injectMembers(this);
  }

  @Test
  public void openJob() throws Exception {
    OpenJobResponse openJobResponse;

    openJobResponse = jobSyncService.doOpenJob(OPEN_JOB_REQUEST);
    assertThat(openJobResponse).isEqualTo(OPEN_JOB_RESPONSE);

    openJobResponse = jobSyncService.doOpenJob(OPEN_JOB_REQUEST);
    assertThat(openJobResponse).isEqualTo(OPEN_JOB_RESPONSE);

    JobsAndAllocations jobsAndAllocations = scheduler.getJobsAndAllocations();
    ImmutableMap<String, JobWithTests> jobsWithTests = jobsAndAllocations.jobsWithTests();
    assertThat(jobsWithTests).containsKey("fake_job_id");

    JobWithTests jobWithTests = requireNonNull(jobsWithTests.get("fake_job_id"));
    JobScheduleUnit job = jobWithTests.jobScheduleUnit();

    assertThat(job.locator().getId()).isEqualTo("fake_job_id");
    assertThat(job.locator().getName()).isEqualTo("fake_job_name");
    assertThat(job.jobUser()).isEqualTo(OPEN_JOB_REQUEST.getFeature().getUser());
    assertThat(job.type())
        .isEqualTo(
            JobType.newBuilder()
                .setDevice("FakeDeviceType1")
                .setDriver("FakeDriver")
                .addDecorator("FakeDevice1Decorator2")
                .addDecorator("FakeDevice1Decorator1")
                .build());
    assertThat(job.setting().getTimeout())
        .isEqualTo(
            Job.Timeout.newBuilder()
                .setJobTimeoutMs(OPEN_JOB_REQUEST.getSetting().getTimeout().getJobTimeoutMs())
                .setTestTimeoutMs(OPEN_JOB_REQUEST.getSetting().getTimeout().getTestTimeoutMs())
                .setStartTimeoutMs(OPEN_JOB_REQUEST.getSetting().getTimeout().getStartTimeoutMs())
                .build());
    assertThat(job.setting().getRetry()).isEqualTo(OPEN_JOB_REQUEST.getSetting().getRetry());
    assertThat(job.setting().getPriority()).isEqualTo(Job.Priority.LOW);
    assertThat(job.params().getAll()).containsExactly("fake_param_name", "fake_param_value");
    assertThat(job.timing().getCreateTime()).isEqualTo(Instant.ofEpochMilli(123L));

    List<SubDeviceSpec> subDevices = job.subDeviceSpecs().getAllSubDevices();
    assertWithMessage("Devices: %s", subDevices).that(subDevices.size()).isEqualTo(2);
    SubDeviceSpec device1 = subDevices.get(0);
    assertThat(device1.type()).isEqualTo("FakeDeviceType1");
    assertThat(device1.dimensions().getAll())
        .containsExactly(
            "fake_device1_dimension1_key",
            "fake_device1_dimension1_value",
            "fake_device1_dimension2_key",
            "fake_device1_dimension2_value");
    assertThat(device1.decorators().getAll())
        .containsExactly("FakeDevice1Decorator1", "FakeDevice1Decorator2")
        .inOrder();
    SubDeviceSpec device2 = subDevices.get(1);
    assertThat(device2.type()).isEqualTo("FakeDeviceType2");
    assertThat(device2.dimensions().getAll())
        .containsExactly(
            "fake_device2_dimension1_key",
            "fake_device2_dimension1_value",
            "fake_device2_dimension2_key",
            "fake_device2_dimension2_value");
    assertThat(device2.decorators().getAll())
        .containsExactly("FakeDevice2Decorator1", "FakeDevice2Decorator2")
        .inOrder();
    assertThat(job.subDeviceSpecs().getSharedDimensionNames())
        .containsExactly("fake_shared_dimension_name");

    ImmutableMap<String, TestLocator> tests = jobWithTests.tests();
    assertThat(tests)
        .containsExactly(
            "fake_test_id",
            new TestLocator(
                "fake_test_id", "fake_test_name", new JobLocator("fake_job_id", "fake_job_name")));

    assertThat(jobsAndAllocations.testAllocations()).isEmpty();
  }
}
