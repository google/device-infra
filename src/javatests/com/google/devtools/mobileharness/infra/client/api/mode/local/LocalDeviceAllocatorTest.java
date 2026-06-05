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

package com.google.devtools.mobileharness.infra.client.api.mode.local;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceScheduleUnit;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.lab.LabScheduleUnit;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.mode.local.LocalDeviceAllocator.DeviceVerifier;
import com.google.devtools.mobileharness.infra.controller.device.proxy.ProxyDeviceManager;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.infra.controller.scheduler.simple.SimpleScheduler;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
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
public final class LocalDeviceAllocatorTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private DeviceVerifier deviceVerifier;
  @Mock private AbstractScheduler scheduler;
  @Mock private ProxyDeviceManager proxyDeviceManager;

  private ListeningExecutorService threadPool;
  private JobInfo jobInfo;
  private TestInfo testInfo;
  private LocalDeviceAllocator allocator;

  @Before
  public void setUp() throws Exception {
    threadPool = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("fake_job"))
            .setType(JobType.newBuilder().setDevice("NoOpDevice").setDriver("NoOpDriver").build())
            .build();
    testInfo = jobInfo.tests().add("test_id", "test_name");
    allocator =
        new LocalDeviceAllocator(
            jobInfo, deviceVerifier, threadPool, proxyDeviceManager, scheduler);
  }

  @After
  public void tearDown() {
    threadPool.shutdownNow();
  }

  @Test
  public void
      pollAllocations_oneDeviceFailsVerification_releasesAllocationAndRemovesOnlyFailedDevice()
          throws Exception {
    DeviceLocator device1 = DeviceLocator.of("device-1", LabLocator.LOCALHOST);
    DeviceLocator device2 = DeviceLocator.of("device-2", LabLocator.LOCALHOST);

    Allocation allocation =
        new Allocation(testInfo.locator().toNewTestLocator(), ImmutableList.of(device1, device2));

    // Stubs: device-1 fails verification, device-2 succeeds verification.
    when(deviceVerifier.verifyDeviceForAllocation("device-1")).thenReturn(Optional.of("busy"));
    when(deviceVerifier.verifyDeviceForAllocation("device-2")).thenReturn(Optional.empty());

    // Injects allocation to allocation list using reflection
    Field allocationsField = LocalDeviceAllocator.class.getDeclaredField("allocations");
    allocationsField.setAccessible(true);
    // Safe because allocator.allocations is known to be ConcurrentLinkedQueue<Allocation>.
    @SuppressWarnings("unchecked")
    ConcurrentLinkedQueue<Allocation> allocations =
        (ConcurrentLinkedQueue<Allocation>) allocationsField.get(allocator);
    allocations.add(allocation);

    var unused = allocator.pollAllocations();

    // Verifies scheduler.unallocate was called to release the allocation.
    verify(scheduler).unallocate(allocation, ImmutableList.of(device1), /* closeTest= */ true);

    // Verifies scheduler.addTest is called to add the test back after unallocation.
    verify(scheduler).addTest(testInfo);
  }

  @Test
  public void
      pollAllocations_withRealSimpleScheduler_oneDeviceFailsVerification_keepsHealthyDeviceUnremoved()
          throws Exception {
    SimpleScheduler realScheduler = new SimpleScheduler(threadPool);

    // Creates job and test requesting 2 subdevices.
    JobInfo multiDeviceJobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("multi_device_job"))
            .setType(JobType.newBuilder().setDevice("NoOpDevice").setDriver("NoOpDriver").build())
            .build();
    multiDeviceJobInfo.subDeviceSpecs().clearSubDevices();
    multiDeviceJobInfo.subDeviceSpecs().addSubDevice("NoOpDevice");
    multiDeviceJobInfo.subDeviceSpecs().addSubDevice("NoOpDevice");
    multiDeviceJobInfo.tests().add("test_id_2", "test_name_2");

    // Inits allocator with the real scheduler.
    LocalDeviceAllocator realAllocator =
        new LocalDeviceAllocator(
            multiDeviceJobInfo, deviceVerifier, threadPool, proxyDeviceManager, realScheduler);

    // Registers device-1 and device-2 (both are NoOpDevices of type "NoOpDevice")
    DeviceScheduleUnit deviceUnit1 =
        new DeviceScheduleUnit(DeviceLocator.of("device-1", LabLocator.LOCALHOST));
    deviceUnit1.types().add("NoOpDevice");
    deviceUnit1.drivers().add("NoOpDriver");
    realScheduler.upsertDevice(deviceUnit1, new LabScheduleUnit(LabLocator.LOCALHOST));

    DeviceScheduleUnit deviceUnit2 =
        new DeviceScheduleUnit(DeviceLocator.of("device-2", LabLocator.LOCALHOST));
    deviceUnit2.types().add("NoOpDevice");
    deviceUnit2.drivers().add("NoOpDriver");
    realScheduler.upsertDevice(deviceUnit2, new LabScheduleUnit(LabLocator.LOCALHOST));

    // Stubs: device-1 fails verification, device-2 succeeds verification.
    when(deviceVerifier.verifyDeviceForAllocation("device-1")).thenReturn(Optional.of("busy"));
    when(deviceVerifier.verifyDeviceForAllocation("device-2")).thenReturn(Optional.empty());

    // Sets up allocator (which adds job/test to the scheduler and registers the event handler).
    var unusedSetUp = realAllocator.setUp();
    realScheduler.start(); // Starts the scheduler in a background thread of threadPool.

    // Sleep a bit on the main thread to allow background scheduler thread to process the
    // allocation.
    Sleeper.defaultSleeper().sleep(Duration.ofMillis(500));

    // Polls the allocation to trigger verification and unallocation logic.
    // This should fail because device-1 failed verification, releasing the allocation.
    var allocations1 = realAllocator.pollAllocations();
    assertThat(allocations1).isEmpty();

    // Now, simulate device-1 coming back online:
    // 1. Update the verifier stub so it passes verification.
    when(deviceVerifier.verifyDeviceForAllocation("device-1")).thenReturn(Optional.empty());
    // 2. Re-register device-1 back into the scheduler.
    realScheduler.upsertDevice(deviceUnit1, new LabScheduleUnit(LabLocator.LOCALHOST));

    // Sleep a bit to allow the scheduler to detect device-1 and allocate both devices.
    Sleeper.defaultSleeper().sleep(Duration.ofMillis(500));

    // Polls again. Since both devices now pass verification, it should succeed.
    var allocations2 = realAllocator.pollAllocations();
    assertThat(allocations2).hasSize(1);

    // Verify that the final allocation contains both device-1 and device-2, showing that the
    // healthy
    // device-2 remained in the scheduler pool and was re-allocated.
    Allocation finalAllocation = allocations2.get(0).allocation();
    assertThat(finalAllocation.getAllDevices())
        .containsExactly(
            DeviceLocator.of("device-1", LabLocator.LOCALHOST),
            DeviceLocator.of("device-2", LabLocator.LOCALHOST));
  }

  @Test
  public void releaseAllocation_oneDeviceIsDirty_releasesAllocationAndRemovesOnlyDirtyDevice()
      throws Exception {
    DeviceLocator device1 = DeviceLocator.of("device-1", LabLocator.LOCALHOST);
    DeviceLocator device2 = DeviceLocator.of("device-2", LabLocator.LOCALHOST);

    Allocation allocation =
        new Allocation(testInfo.locator().toNewTestLocator(), ImmutableList.of(device1, device2));

    // Stubs: device-1 is dirty upon release, device-2 is clean upon release.
    when(deviceVerifier.getDeviceDirtyForAllocationRelease("device-1"))
        .thenReturn(Optional.of(true));
    when(deviceVerifier.getDeviceDirtyForAllocationRelease("device-2"))
        .thenReturn(Optional.of(false));

    allocator.releaseAllocation(allocation, TestResult.PASS, /* deviceDirty= */ false);

    // Verifies scheduler.unallocate was called to release the allocation and remove only dirty
    // device-1.
    verify(scheduler).unallocate(allocation, ImmutableList.of(device1), /* closeTest= */ true);
  }

  @Test
  public void
      releaseAllocation_withRealSimpleScheduler_oneDeviceIsDirty_keepsHealthyDeviceUnremoved()
          throws Exception {
    SimpleScheduler realScheduler = new SimpleScheduler(threadPool);

    // Creates job and test requesting 2 subdevices.
    JobInfo multiDeviceJobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("multi_device_job"))
            .setType(JobType.newBuilder().setDevice("NoOpDevice").setDriver("NoOpDriver").build())
            .build();
    multiDeviceJobInfo.subDeviceSpecs().clearSubDevices();
    multiDeviceJobInfo.subDeviceSpecs().addSubDevice("NoOpDevice");
    multiDeviceJobInfo.subDeviceSpecs().addSubDevice("NoOpDevice");
    multiDeviceJobInfo.tests().add("test_id_2", "test_name_2");

    // Inits allocator with the real scheduler.
    LocalDeviceAllocator realAllocator =
        new LocalDeviceAllocator(
            multiDeviceJobInfo, deviceVerifier, threadPool, proxyDeviceManager, realScheduler);

    // Registers device-1 and device-2 (both are NoOpDevices of type "NoOpDevice")
    DeviceScheduleUnit deviceUnit1 =
        new DeviceScheduleUnit(DeviceLocator.of("device-1", LabLocator.LOCALHOST));
    deviceUnit1.types().add("NoOpDevice");
    deviceUnit1.drivers().add("NoOpDriver");
    realScheduler.upsertDevice(deviceUnit1, new LabScheduleUnit(LabLocator.LOCALHOST));

    DeviceScheduleUnit deviceUnit2 =
        new DeviceScheduleUnit(DeviceLocator.of("device-2", LabLocator.LOCALHOST));
    deviceUnit2.types().add("NoOpDevice");
    deviceUnit2.drivers().add("NoOpDriver");
    realScheduler.upsertDevice(deviceUnit2, new LabScheduleUnit(LabLocator.LOCALHOST));

    // Stubs: both devices pass verification in the first round (allocation phase).
    when(deviceVerifier.verifyDeviceForAllocation("device-1")).thenReturn(Optional.empty());
    when(deviceVerifier.verifyDeviceForAllocation("device-2")).thenReturn(Optional.empty());

    // Sets up allocator (which adds job/test to the scheduler and registers the event handler).
    var unusedSetUp = realAllocator.setUp();
    realScheduler.start(); // Starts the scheduler in a background thread of threadPool.

    // Sleep a bit to allow the scheduler to allocate both devices.
    Sleeper.defaultSleeper().sleep(Duration.ofMillis(500));

    // Polls the allocation to trigger verification logic. This should succeed.
    var allocations1 = realAllocator.pollAllocations();
    assertThat(allocations1).hasSize(1);
    Allocation allocation = allocations1.get(0).allocation();

    // Stubs: device-1 becomes dirty upon release, device-2 remains clean.
    when(deviceVerifier.getDeviceDirtyForAllocationRelease("device-1"))
        .thenReturn(Optional.of(true));
    when(deviceVerifier.getDeviceDirtyForAllocationRelease("device-2"))
        .thenReturn(Optional.of(false));

    // Release the allocation. This should trigger the new release logic:
    // device-1 (dirty) is removed from the scheduler, device-2 (clean) is released but kept active.
    realAllocator.releaseAllocation(allocation, TestResult.PASS, /* deviceDirty= */ false);

    // To verify that device-2 remains active and was NOT removed from the scheduler:
    // Submit a second test/job that requires only 1 device.
    JobInfo singleDeviceJobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("single_device_job"))
            .setType(JobType.newBuilder().setDevice("NoOpDevice").setDriver("NoOpDriver").build())
            .build();
    singleDeviceJobInfo.tests().add("test_id_3", "test_name_3");
    LocalDeviceAllocator singleDeviceAllocator =
        new LocalDeviceAllocator(
            singleDeviceJobInfo, deviceVerifier, threadPool, proxyDeviceManager, realScheduler);

    var unusedSetUp2 = singleDeviceAllocator.setUp();
    // Sleep a bit to allow the scheduler to allocate the remaining device-2 to the second test.
    Sleeper.defaultSleeper().sleep(Duration.ofMillis(500));

    // Polls again. Since only device-2 is free (and kept in scheduler pool), it should be
    // allocated.
    var allocations2 = singleDeviceAllocator.pollAllocations();
    assertThat(allocations2).hasSize(1);
    Allocation secondAllocation = allocations2.get(0).allocation();
    assertThat(secondAllocation.getDevice().id()).isEqualTo("device-2");
  }
}
