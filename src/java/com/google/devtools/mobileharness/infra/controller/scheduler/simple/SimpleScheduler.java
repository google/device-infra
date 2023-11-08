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

package com.google.devtools.mobileharness.infra.controller.scheduler.simple;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceScheduleUnit;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.lab.LabScheduleUnit;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.infra.controller.scheduler.AdhocTestbedSchedulingUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.controller.event.AllocationEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestScheduleUnit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Simple scheduler. It rotately assigns devices to waiting jobs. So a huge job won't blocking the
 * latter jobs.
 */
public class SimpleScheduler extends AbstractScheduler implements Runnable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration SCHEDULING_SMALL_INTERVAL = Duration.ofMillis(10L);
  private static final Duration SCHEDULING_LARGE_INTERVAL = Duration.ofMillis(50L);

  private static final AdhocTestbedSchedulingUtil adhocTestbedSchedulingUtil =
      new AdhocTestbedSchedulingUtil();

  /** {Job ID, {@link SimpleJobInfo}} mapping. */
  private final ConcurrentHashMap<String, SimpleJobInfo> jobs = new ConcurrentHashMap<>();

  /** {Lab IP, {@link SimpleLabInfo}} mapping. */
  private final ConcurrentHashMap<String, SimpleLabInfo> labs = new ConcurrentHashMap<>();

  /** Synchronization lock for {@link #deviceAllocations} & {@link #testAllocations}. */
  private final Object allocationLock = new Object();

  /** {{@link DeviceLocator#universalId()}, {@link Allocation}} mapping. */
  private final Map<String, Allocation> deviceAllocations = new HashMap<>();

  /** {TestID, Allocation} mapping. */
  private final Map<String, Allocation> testAllocations = new HashMap<>();

  private final Sleeper sleeper;
  private final ExecutorService threadPool;

  public SimpleScheduler(ExecutorService threadPool) {
    this(threadPool, Sleeper.defaultSleeper());
  }

  @Inject
  SimpleScheduler(ExecutorService threadPool, Sleeper sleeper) {
    this.threadPool = threadPool;
    this.sleeper = sleeper;
  }

  @Override
  public void start() {
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = threadPool.submit(this);
  }

  @Override
  public void run() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        boolean hasNewAllocation = false;
        for (SimpleJobInfo job : jobs.values()) {
          sleeper.sleep(SCHEDULING_SMALL_INTERVAL);
          for (TestLocator testLocator : job.getTests().values()) {
            if (!testAllocations.containsKey(testLocator.getId())) {
              // Found a new test.
              if (allocate(job.getScheduleUnit(), testLocator)) {
                hasNewAllocation = true;
              }
              // No matter successfully allocate devices to the new test or not, skips the remaining
              // tests and allocates devices for the next job.
              break;
            }
          }
        }
        if (!hasNewAllocation) {
          sleeper.sleep(SCHEDULING_LARGE_INTERVAL);
        }
      } catch (InterruptedException e) {
        logger.atWarning().log("Sleep interrupted.");
        Thread.currentThread().interrupt();
        return;
      } catch (Exception e) {
        // Catches all exceptions to keep the scheduler running.
        logger.atSevere().withCause(e).log("Exception in SimpleScheduler, ignoring");
      }
    }
  }

  @Override
  public void addJob(JobScheduleUnit jobUnit) throws MobileHarnessException {
    JobLocator jobLocator = jobUnit.locator();
    SimpleJobInfo job = new SimpleJobInfo(jobUnit);
    SimpleJobInfo exJob = jobs.putIfAbsent(jobLocator.getId(), job);
    if (exJob == null) {
      logger.atInfo().log("Added job %s", jobLocator);
    } else {
      throw new MobileHarnessException(
          ErrorCode.JOB_DUPLICATED, "Job " + jobLocator.getId() + " already exist");
    }
  }

  @Override
  public void removeJob(String jobId, boolean removeDevices) {
    synchronized (allocationLock) {
      SimpleJobInfo job = jobs.remove(jobId);
      if (job != null) {
        logger.atInfo().log("Job deleted: %s", jobId);
        for (String testId : job.getTests().keySet()) {
          // No need to close test, because the job is removed.
          unallocate(testAllocations.get(testId), removeDevices, false);
        }
      } else {
        logger.atInfo().log("Job does not exist: %s", jobId);
      }
    }
  }

  @Override
  public void addTest(TestScheduleUnit test) throws MobileHarnessException {
    TestLocator testLocator = test.locator();
    SimpleJobInfo job = checkJob(testLocator.getJobLocator().getId());
    job.addTest(testLocator);
    logger.atInfo().log("Added test %s", testLocator);
  }

  /**
   * Removes a test. Note this method doesn't take care of the devices assigned to this test. If you
   * want to release the devices at the same time, use {@link #unallocate(Allocation, boolean,
   * boolean)} instead.
   */
  private void removeTest(String jobId, String testId) {
    synchronized (allocationLock) {
      SimpleJobInfo job;
      try {
        job = checkJob(jobId);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log("Error checking the job.");
        return;
      }

      TestLocator testLocator = job.removeTest(testId);
      if (testLocator == null) {
        logger.atWarning().log("%s", String.format("Test %s not found in job %s", testId, jobId));
      } else {
        Allocation allocation = testAllocations.get(testId);
        if (allocation != null) {
          logger.atSevere().log(
              "%s",
              String.format(
                  "Test %s removed from job %s, but its allocation is not released: %s",
                  testId, jobId, allocation));
        } else {
          logger.atInfo().log("%s", String.format("Test %s removed from job %s", testId, jobId));
        }
      }
    }
  }

  @Override
  public void upsertDevice(DeviceScheduleUnit deviceUnit, LabScheduleUnit labUnit) {
    SimpleLabInfo lab = new SimpleLabInfo(labUnit);
    SimpleLabInfo exLab = labs.putIfAbsent(labUnit.locator().ip(), lab);
    if (exLab != null) {
      lab = exLab;
    }

    DeviceScheduleUnit exDevice = null;
    synchronized (allocationLock) {
      exDevice = lab.upsertDevice(deviceUnit);
    }
    logger.atInfo().log(
        "%s device %s", (exDevice == null ? "Added" : "Updated"), deviceUnit.locator());
  }

  /**
   * Removes the device from scheduler. Note this method will leave running test of the device
   * unchanged. If you want to release the allocation at the same time, uses {@link
   * #unallocate(DeviceLocator, boolean, boolean)} instead.
   */
  private void removeDevice(DeviceLocator deviceLocator) {
    LabLocator labLocator = deviceLocator.labLocator();
    SimpleLabInfo lab = labs.get(labLocator.ip());
    if (lab == null) {
      logger.atWarning().log(
          "Failed to remove device %s because lab does not exist", deviceLocator);
    } else {
      synchronized (allocationLock) {
        DeviceScheduleUnit device = lab.removeDevice(deviceLocator.id());
        if (device == null) {
          logger.atInfo().log("Skip removing device %s because device not exist", deviceLocator);
        } else {
          Allocation allocation = deviceAllocations.get(deviceLocator.universalId());
          if (allocation != null) {
            logger.atSevere().log(
                "%s",
                String.format(
                    "Device %s removed. But its allocation is not release: %s",
                    deviceLocator, allocation));
          } else {
            logger.atInfo().log("%s", String.format("Device %s removed", deviceLocator));
          }
        }
      }
    }
  }

  @Override
  public void unallocate(DeviceLocator deviceLocator, boolean removeDevices, boolean closeTest) {
    synchronized (allocationLock) {
      Allocation allocation = deviceAllocations.get(deviceLocator.universalId());
      if (allocation != null) {
        unallocate(allocation, removeDevices, closeTest);
      } else if (removeDevices) {
        removeDevice(deviceLocator);
      }
    }
  }

  @Override
  public void unallocate(
      @Nullable Allocation allocation, boolean removeDevices, boolean closeTest) {
    if (allocation == null) {
      return;
    }
    // Makes sure we release all devices related to this allocation.
    synchronized (allocationLock) {
      boolean unallocated = false;
      ImmutableList<DeviceLocator> deviceLocators = allocation.getAllDevices();
      for (DeviceLocator deviceLocator : deviceLocators) {
        String deviceId = deviceLocator.universalId();
        Allocation deviceAllocation = deviceAllocations.get(deviceId);
        if (deviceAllocation == null) {
          logger.atInfo().log(
              "Skip unallocate device %s because it is already idle", deviceLocator);
        } else if (deviceAllocation.equals(allocation)) {
          deviceAllocations.remove(deviceId);
          unallocated = true;
          if (removeDevices) {
            removeDevice(deviceLocator);
            logger.atInfo().log("Free and remove device %s", deviceLocator);
          } else {
            logger.atInfo().log("Free device %s", deviceLocator);
          }
        } else {
          logger.atWarning().log(
              "%s",
              String.format(
                  "Skip unallocate device %s because it is assigned to a different test: %s",
                  deviceLocator, deviceAllocation));
        }
      }
      // Closes the test.
      com.google.devtools.mobileharness.api.model.job.TestLocator testLocator =
          allocation.getTest();
      String testId = testLocator.id();
      Allocation testAllocation = testAllocations.get(testId);
      if (testAllocation == null) {
        logger.atInfo().log("Skip unallocate test because it is new/closed");
      } else if (testAllocation.equals(allocation)) {
        testAllocations.remove(testId);
        unallocated = true;
        if (closeTest) {
          logger.atInfo().log("Unassign and remove test %s", testLocator);
          removeTest(testLocator.jobLocator().id(), testId);
        } else {
          logger.atInfo().log("Unassign test %s", testLocator);
        }
      } else {
        // Should not reach here.
        logger.atSevere().log(
            "%s",
            String.format(
                "Inconsistent allocation info with test %s, expect %s, got %s",
                testLocator, allocation, testAllocation));
      }
      if (unallocated) {
        logger.atInfo().log("Allocation %s released", allocation);
      }
    }
  }

  /** Goes through all labs to allocate devices for the given test. */
  private boolean allocate(JobScheduleUnit job, TestLocator test) {
    // TODO Use only one flow for all allocations after 1) verifying no one relies on
    // decorators in job type, 2) verifying the driver and subdevices match.
    if (!job.subDeviceSpecs().hasMultipleDevices()) {
      return allocateSingleDeviceJob(job, test);
    }
    try {
      return allocateAdhocTestbedJob(job, test);
    } catch (InterruptedException e) {
      logger.atWarning().withCause(e).log("Ad hoc testbed allocation was interrupted.");
    }
    return false;
  }

  /**
   * Allocates the (single) given device to the given test. Fires {@link AllocationEvent} if
   * required.
   */
  private boolean allocate(TestLocator test, DeviceScheduleUnit device, boolean fireEvent) {
    return allocate(test, ImmutableList.of(device), fireEvent);
  }

  /**
   * Allocates the given (multiple) devices to the given test. Fires {@link AllocationEvent} if
   * required.
   */
  private boolean allocate(
      TestLocator test, List<DeviceScheduleUnit> deviceUnits, boolean fireEvent) {
    List<DeviceLocator> deviceLocators = new ArrayList<>();
    for (DeviceScheduleUnit device : deviceUnits) {
      deviceLocators.add(device.locator());
    }
    Allocation allocation = new Allocation(test.toNewTestLocator(), deviceLocators);
    synchronized (allocationLock) {
      // Double checks job.
      String jobId = test.getJobLocator().getId();
      SimpleJobInfo job;
      try {
        job = checkJob(jobId);
      } catch (MobileHarnessException e) {
        logger.atInfo().log("Job %s removed. Can not create allocation %s", jobId, allocation);
        return false;
      }

      // Double check test.
      String testId = test.getId();
      if (!job.containsTest(testId)) {
        logger.atInfo().log("Test %s removed. Can not create allocation %s", testId, allocation);
        return false;
      } else if (testAllocations.containsKey(testId)) {
        logger.atWarning().log(
            "%s",
            String.format(
                "Test %s has allocation %s. Can not create allocation %s.",
                testId, testAllocations.get(testId), allocation));
        return false;
      }

      // Double check lab.
      LabLocator labLocator = deviceLocators.get(0).labLocator();
      SimpleLabInfo labInfo = labs.get(labLocator.ip());
      if (labInfo == null) {
        logger.atInfo().log("Lab %s removed. Can not create allocation %s", labLocator, allocation);
        return false;
      }
      for (DeviceLocator deviceLocator : deviceLocators) {
        SimpleLabInfo loopLabInfo = labs.get(deviceLocator.labLocator().ip());
        if (!loopLabInfo.equals(labInfo)) {
          logger.atInfo().log(
              "Lab locators do not match. Can not create allocation %s", allocation);
          return false;
        }
        // Double checks device.
        if (labInfo.getDevice(deviceLocator.id()) == null) {
          logger.atInfo().log(
              "Device %s removed. Can not create allocation %s", deviceLocator, allocation);
          return false;
        }
        if (deviceAllocations.containsKey(deviceLocator.universalId())) {
          logger.atWarning().log(
              "%s",
              String.format(
                  "Device %s has allocation %s. Can not create allocation %s.",
                  deviceLocator, deviceAllocations.get(deviceLocator.universalId()), allocation));
          return false;
        }
      }
      // Creates the allocation.
      testAllocations.put(testId, allocation);
      for (DeviceLocator deviceLocator : deviceLocators) {
        deviceAllocations.put(deviceLocator.universalId(), allocation);
      }
    }
    logger.atInfo().log("Created allocation %s", allocation);

    // After the allocation, we send out event to notify external framework. If the framework can
    // not accept the event because the test/devices are removed, it will call scheduler to undo the
    // allocation.
    if (fireEvent) {
      postEvent(new AllocationEvent(allocation));
    }
    return true;
  }

  /** Goes through all labs to allocate devices for the given single-device test. */
  private boolean allocateSingleDeviceJob(JobScheduleUnit job, TestLocator test) {
    for (SimpleLabInfo labInfo : labs.values()) {
      for (DeviceScheduleUnit device : labInfo.getDevices()) {
        DeviceLocator deviceLocator = device.locator();
        if (!deviceAllocations.containsKey(deviceLocator.universalId())
            && ifDeviceSupports(device, job)) {
          // Found a suitable and idle device for the new test.
          return allocate(test, device, true);
        }
      }
    }
    return false;
  }

  /** Goes through all labs to allocate devices for the given adhoc testbed test. */
  private boolean allocateAdhocTestbedJob(JobScheduleUnit job, TestLocator test)
      throws InterruptedException {
    Set<String> types = job.subDeviceSpecs().getAllSubDeviceTypes();

    for (SimpleLabInfo labInfo : labs.values()) {
      List<DeviceScheduleUnit> filteredDevices =
          labInfo.getDevices().stream()
              // Filter out already allocated devices
              .filter(device -> !deviceAllocations.containsKey(device.locator().universalId()))
              // Filter out devices that don't support any desired types
              .filter(device -> !Collections.disjoint(device.types().getAll(), types))
              // Filter out devices that the user does not own
              .filter(device -> device.owners().support(job.jobUser().getRunAs()))
              .collect(Collectors.toList());
      if (!filteredDevices.isEmpty()) {
        List<DeviceScheduleUnit> deviceList =
            adhocTestbedSchedulingUtil.findSubDevicesSupportingJob(filteredDevices, job);
        // The order matters in the allocated list as it needs to match the spec.
        if (!deviceList.isEmpty()) {
          return allocate(test, deviceList, true);
        }
      }
    }
    return false;
  }

  /**
   * Gets the {@link SimpleJobInfo} according to the job id.
   *
   * @throws MobileHarnessException if job not found
   */
  private SimpleJobInfo checkJob(String jobId) throws MobileHarnessException {
    SimpleJobInfo jobInfo = jobs.get(jobId);
    return MobileHarnessException.checkNotNull(
        jobInfo, ErrorCode.JOB_NOT_FOUND, "Job " + jobId + " not found");
  }
}
