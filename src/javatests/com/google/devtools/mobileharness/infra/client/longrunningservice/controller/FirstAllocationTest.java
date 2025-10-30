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

package com.google.devtools.mobileharness.infra.client.longrunningservice.controller;

import static com.google.common.truth.Truth.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.mobileharness.infra.client.api.ClientApiModule;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.AllocationWithStats;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionDetailHolder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunnerSetting;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.junit.rule.CaptureLogs;
import com.google.devtools.mobileharness.shared.util.junit.rule.PrintTestName;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Job;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.model.lab.LabLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class FirstAllocationTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final CaptureLogs captureLogs = new CaptureLogs();
  @Rule public final PrintTestName printTestName = new PrintTestName();

  @Bind @GlobalInternalEventBus private final EventBus globalInternalEventBus = new EventBus();

  @Mock @Bind private ExecMode execMode;
  @Mock @Bind private Sleeper sleeper;

  @Inject private SessionJobRunner sessionJobRunner;

  private ListeningExecutorService executor;

  @Before
  public void setUp() throws Exception {
    when(execMode.createTestRunner(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              DirectTestRunnerSetting setting = invocation.getArgument(0);
              TestInfo testInfo = setting.testInfo();
              DirectTestRunner mockTestRunner = mock(DirectTestRunner.class);
              when(mockTestRunner.getTestExecutionUnit())
                  .thenReturn(testInfo.toTestExecutionUnit());
              return mockTestRunner;
            });

    Guice.createInjector(new ClientApiModule(), BoundFieldModule.of(this)).injectMembers(this);

    executor = ThreadPools.createStandardThreadPool("test-thread-pool");
  }

  @After
  public void tearDown() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Test
  public void runJobs_firstTestAllocated() throws Exception {
    // Arrange
    JobInfo realJob1 = createJobInfo("job_1");
    JobInfo realJob2 = createJobInfo("job_2");
    JobInfo realJob3 = createJobInfo("job_3");

    SessionDetailHolder sessionDetailHolder =
        new SessionDetailHolder(
            SessionDetail.getDefaultInstance(),
            /* sessionDetailListener= */ () -> {},
            /* sessionPersistenceUtil= */ null,
            /* initialSessionPersistenceStatus= */ null);
    sessionJobRunner.setSessionDetailHolder(sessionDetailHolder);

    Stopwatch stopwatch = Stopwatch.createStarted();
    Set<String> allocatedJobIds = new HashSet<>();
    when(execMode.createDeviceAllocator(any(JobInfo.class), any(EventBus.class)))
        .thenAnswer(
            invocation -> {
              JobInfo jobInfo = invocation.getArgument(0);
              String jobId = jobInfo.locator().getId();
              TestInfo testInfo = jobInfo.tests().getAll().values().iterator().next();
              TestLocator testLocator = testInfo.locator();
              DeviceLocator deviceLocator =
                  new DeviceLocator("fake_device_id", new LabLocator("fake_host", "fake_lab"));
              Allocation allocation =
                  new Allocation(testLocator, deviceLocator, ImmutableMultimap.of());

              DeviceAllocator mockDeviceAllocator = mock(DeviceAllocator.class);
              when(mockDeviceAllocator.pollAllocations())
                  .thenAnswer(
                      inv -> {
                        if (stopwatch.elapsed().compareTo(Duration.ofMillis(1200)) < 0) {
                          return ImmutableList.of();
                        } else if (!allocatedJobIds.contains(jobId)) {
                          allocatedJobIds.add(jobId);
                          return ImmutableList.of(
                              new AllocationWithStats(allocation.toNewAllocation()));
                        } else {
                          return ImmutableList.of();
                        }
                      });
              return mockDeviceAllocator;
            });

    // Act and assert
    Callable<Void> sessionJobCallable =
        () -> {
          try {
            sessionJobRunner.runJobs(ImmutableList.of());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return null;
        };

    // Add job1 and job2, then check the HAS_ASSOCIATED_ALLOCATION property.
    sessionDetailHolder.addJob(realJob1);
    sessionDetailHolder.addJob(realJob2);
    ListenableFuture<Void> sessionJobFuture = executor.submit(sessionJobCallable);
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              assertThat(
                      realJob1.properties().getBoolean(Job.HAS_ASSOCIATED_ALLOCATION).orElse(false))
                  .isTrue();
              assertThat(
                      realJob2.properties().getBoolean(Job.HAS_ASSOCIATED_ALLOCATION).orElse(false))
                  .isTrue();
            });
    assertThat(realJob3.properties().getBoolean(Job.HAS_ASSOCIATED_ALLOCATION).orElse(false))
        .isFalse();

    // Add and check job3 after job1 and job2 are allocated.
    sessionDetailHolder.addJob(realJob3);
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              assertThat(
                      realJob3.properties().getBoolean(Job.HAS_ASSOCIATED_ALLOCATION).orElse(false))
                  .isTrue();
            });

    // Since sessionJobFuture contains a while(true) loop, and in this case it will never done.
    sessionJobFuture.cancel(true);
  }

  private JobInfo createJobInfo(String jobId) throws Exception {
    JobType jobType = JobType.newBuilder().setDevice("AndroidRealDevice").build();
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator(jobId, "real_" + jobId))
            .setType(jobType)
            .build();
    jobInfo.properties().add("client", "ait");
    jobInfo.tests().add("fake_test", "fake_test");
    return jobInfo;
  }
}
