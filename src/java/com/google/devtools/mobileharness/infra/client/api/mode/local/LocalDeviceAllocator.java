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

import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Error.ExceptionDetail;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.AbstractDeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.AllocationWithStats;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.controller.event.AllocationEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/** For managing the local device resources and allocating devices for a single job. */
public class LocalDeviceAllocator extends AbstractDeviceAllocator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DeviceVerifier deviceVerifier;

  /** Universal scheduler for scheduling local devices for local tests. */
  private final ListenableFuture<AbstractScheduler> schedulerFuture;

  /** Allocations returned by scheduler, & haven't been retrieved by {@link #pollAllocations()}. */
  private final ConcurrentLinkedQueue<Allocation> allocations = new ConcurrentLinkedQueue<>();

  /** Handlers for allocation events of the scheduler. */
  private final AllocationEventHandler allocationEventHandler = new AllocationEventHandler();

  public LocalDeviceAllocator(
      final JobInfo jobInfo,
      DeviceVerifier deviceVerifier,
      ListenableFuture<AbstractScheduler> schedulerFuture) {
    super(jobInfo);
    this.deviceVerifier = deviceVerifier;
    this.schedulerFuture = schedulerFuture;
  }

  @Override
  public synchronized Optional<ExceptionDetail> setUp()
      throws MobileHarnessException, InterruptedException {
    super.setUp();
    AbstractScheduler scheduler = getScheduler();
    scheduler.registerEventHandler(allocationEventHandler);
    scheduler.addJob(jobInfo);
    for (TestInfo test : jobInfo.tests().getAll().values()) {
      scheduler.addTest(test);
    }
    return Optional.empty();
  }

  @Override
  public List<AllocationWithStats> pollAllocations()
      throws MobileHarnessException, InterruptedException {
    List<AllocationWithStats> results = new ArrayList<>();
    Allocation allocation;
    AbstractScheduler scheduler = getScheduler();
    while ((allocation = allocations.poll()) != null) {
      // Finds the TestInfo in the current job.
      TestInfo test = jobInfo.tests().getById(allocation.getTest().id());

      if (test == null) {
        jobInfo
            .errors()
            .addAndLog(
                ErrorCode.DEVICE_ALLOCATOR_ERROR,
                String.format(
                    "Unknown test %s of job %s in the allocation.",
                    allocation.getTest().id(), jobInfo.locator().getId()),
                logger);
        scheduler.unallocate(
            allocation,
            // Releases the device back to IDLE.
            false,
            // Closes the test because it doesn't exist.
            true);
        continue;
      } else if (test.status().get() != TestStatus.NEW) {
        jobInfo
            .errors()
            .addAndLog(
                ErrorCode.DEVICE_ALLOCATOR_ERROR,
                "Unexpected allocation to test with status " + test.status().get(),
                logger);
        scheduler.unallocate(
            allocation,
            // Releases the device back to IDLE.
            false,
            // Closes the test in scheduler because it is not new and doesn't need new allocation.
            true);
        continue;
      }

      String deviceSerial = allocation.getDevice().id();
      Optional<String> verificationError = deviceVerifier.verifyDeviceForAllocation(deviceSerial);
      if (verificationError.isPresent()) {
        jobInfo.errors().addAndLog(ErrorCode.DEVICE_NOT_READY, verificationError.get(), logger);
        scheduler.unallocate(
            allocation,
            // Device is not active. Also removes it from scheduler.
            true,
            // Leaves the test open to wait for allocation with different devices.
            false);
        continue;
      }

      // Marks the test as assigned. The scheduler uses cloned test objects so it won't update the
      // status of the real test.
      test.status().set(TestStatus.ASSIGNED);

      results.add(new AllocationWithStats(allocation));
    }
    return results;
  }

  @Override
  public void extraAllocation(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    AbstractScheduler scheduler = getScheduler();
    scheduler.addTest(testInfo);
  }

  @Override
  public void releaseAllocation(Allocation allocation, TestResult testResult, boolean deviceDirty)
      throws MobileHarnessException, InterruptedException {
    DeviceLocator deviceLocator = allocation.getDevice();
    String deviceSerial = deviceLocator.id();
    AbstractScheduler scheduler = getScheduler();
    Optional<Boolean> deviceDirtyFromVerifier =
        deviceVerifier.getDeviceDirtyForAllocationRelease(deviceSerial);
    try {
      if (deviceDirtyFromVerifier.isPresent()) {
        deviceDirty = deviceDirtyFromVerifier.get();
      }
    } finally {
      jobInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Release device %s in scheduler, DeviceDirty=%s", deviceSerial, deviceDirty);
      scheduler.unallocate(deviceLocator, deviceDirty, true);
    }
  }

  @Override
  public synchronized void tearDown() throws MobileHarnessException, InterruptedException {
    AbstractScheduler scheduler = getScheduler();
    // Closes the job and changes the device back to IDLE.
    scheduler.removeJob(jobInfo.locator().getId(), false);
    scheduler.unregisterEventHandler(allocationEventHandler);
  }

  private AbstractScheduler getScheduler() throws MobileHarnessException, InterruptedException {
    return MoreFutures.get(
        schedulerFuture, InfraErrorId.SCHEDULER_LOCAL_DEVICE_ALLOCATOR_SCHEDULER_INIT_ERROR);
  }

  private class AllocationEventHandler {

    @Subscribe
    private void onAllocation(AllocationEvent event) {
      Allocation allocation = event.getAllocation();
      if (allocation.getTest().jobLocator().id().equals(jobInfo.locator().getId())) {
        allocations.add(event.getAllocation());
      }
    }
  }

  /**
   * Device verifier for verifying a device allocation based on device status or getting device
   * dirty status when releasing an allocation.
   */
  public interface DeviceVerifier {

    /** Returns an error message if the device is invalid for a new created allocation. */
    Optional<String> verifyDeviceForAllocation(String deviceId);

    /** Returns whether the device is dirty (or empty for unknown) for an allocation release. */
    Optional<Boolean> getDeviceDirtyForAllocationRelease(String deviceId)
        throws InterruptedException;
  }

  /** An empty implementation for {@link DeviceVerifier}. */
  public static class EmptyDeviceVerifier implements DeviceVerifier {

    @Override
    public Optional<String> verifyDeviceForAllocation(String deviceId) {
      return Optional.empty();
    }

    @Override
    public Optional<Boolean> getDeviceDirtyForAllocationRelease(String deviceId) {
      return Optional.empty();
    }
  }
}
