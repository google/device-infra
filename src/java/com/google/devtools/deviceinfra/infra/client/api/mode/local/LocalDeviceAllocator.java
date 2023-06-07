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

package com.google.devtools.deviceinfra.infra.client.api.mode.local;

import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Error.ExceptionDetail;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.AbstractDeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.AllocationWithStats;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceRunner;
import com.google.devtools.mobileharness.infra.controller.scheduler.Scheduler;
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
class LocalDeviceAllocator extends AbstractDeviceAllocator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Universal device manager for managing local devices. */
  private final LocalDeviceManager deviceManager;

  /** Universal scheduler for scheduling local devices for local tests. */
  private final ListenableFuture<Scheduler> schedulerFuture;

  /** Allocations returned by scheduler, & haven't been retrieved by {@link #pollAllocations()}. */
  private final ConcurrentLinkedQueue<Allocation> allocations;

  /** Handlers for allocation events of the scheduler. */
  private final Object allocationEventHandler;

  public LocalDeviceAllocator(
      final JobInfo jobInfo,
      LocalDeviceManager deviceManager,
      ListenableFuture<Scheduler> schedulerFuture) {
    super(jobInfo);
    this.deviceManager = deviceManager;
    this.schedulerFuture = schedulerFuture;

    allocations = new ConcurrentLinkedQueue<>();
    allocationEventHandler =
        new Object() {
          @Subscribe
          public void onAllocation(AllocationEvent event) {
            Allocation allocation = event.getAllocation();
            if (allocation.getTest().jobLocator().id().equals(jobInfo.locator().getId())) {
              allocations.add(event.getAllocation());
            }
          }
        };
  }

  public LocalDeviceManager getDeviceManager() {
    return deviceManager;
  }

  @Override
  public synchronized Optional<ExceptionDetail> setUp()
      throws MobileHarnessException, InterruptedException {
    super.setUp();
    Scheduler scheduler = getScheduler();
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
    Allocation allocation = null;
    Scheduler scheduler = getScheduler();
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
      LocalDeviceRunner deviceRunner = deviceManager.getLocalDeviceRunner(deviceSerial);
      if (deviceRunner == null || !deviceRunner.isAvailable()) {
        jobInfo
            .errors()
            .addAndLog(
                ErrorCode.DEVICE_NOT_READY,
                "Failed to reserve device "
                    + allocation.getDevice()
                    + " because device is "
                    + (deviceRunner == null ? "disconnected" : "busy"),
                logger);
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
    Scheduler scheduler = getScheduler();
    scheduler.addTest(testInfo);
  }

  @Override
  public void releaseAllocation(Allocation allocation, TestResult testResult, boolean deviceDirty)
      throws MobileHarnessException, InterruptedException {
    DeviceLocator deviceLocator = allocation.getDevice();
    String deviceSerial = deviceLocator.id();
    LocalDeviceRunner deviceRunner = deviceManager.getLocalDeviceRunner(deviceSerial);
    Scheduler scheduler = getScheduler();
    try {
      if (deviceRunner == null) {
        logger.atInfo().log(
            "Device runner of device %s not found. Mark the device as DIRTY", deviceSerial);
        deviceDirty = true;
      } else if (!deviceRunner.getDevice().canReboot()) {
        logger.atInfo().log("Device %s not rebootable.", deviceSerial);
        deviceDirty = false;
      } else if (!deviceRunner.isAlive()) {
        logger.atInfo().log("Device %s is not alive.", deviceSerial);
        deviceDirty = true;
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
    Scheduler scheduler = getScheduler();
    // Closes the job and changes the device back to IDLE.
    scheduler.removeJob(jobInfo.locator().getId(), false);
    scheduler.unregisterEventHandler(allocationEventHandler);
  }

  private Scheduler getScheduler() throws MobileHarnessException, InterruptedException {
    return MoreFutures.get(
        schedulerFuture, InfraErrorId.SCHEDULER_LOCAL_DEVICE_ALLOCATOR_SCHEDULER_INIT_ERROR);
  }
}
