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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceScheduleUnit;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.lab.LabScheduleUnit;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.infra.controller.scheduler.AdhocTestbedSchedulingUtil;
import com.google.devtools.mobileharness.infra.controller.scheduler.simple.persistence.AllocationPersistenceUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Simple scheduler. It rotationally assigns devices to waiting jobs. So a huge job won't block the
 * latter jobs.
 *
 * @implSpec WARNING: {@link Allocations} is not thread safe, so at least all r/w operations of
 *     {@link Allocations} should be locked.
 */
public class SimpleScheduler extends AbstractScheduler implements Runnable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration SCHEDULING_SMALL_INTERVAL = Duration.ofMillis(10L);
  private static final Duration SCHEDULING_LARGE_INTERVAL = Duration.ofMillis(50L);

  private final AdhocTestbedSchedulingUtil adhocTestbedSchedulingUtil =
      new AdhocTestbedSchedulingUtil();

  /** {Job ID, {@link SimpleJobInfo}} mapping. */
  private final ConcurrentHashMap<String, SimpleJobInfo> jobs = new ConcurrentHashMap<>();

  /** {Lab IP, {@link SimpleLabInfo}} mapping. */
  private final ConcurrentHashMap<String, SimpleLabInfo> labs = new ConcurrentHashMap<>();

  /** Synchronization lock for {@link #allocations}. */
  private final Object allocationLock = new Object();

  private final Allocations allocations;

  private final Sleeper sleeper;
  private final ExecutorService threadPool;

  public SimpleScheduler(ExecutorService threadPool) {
    this(threadPool, Sleeper.defaultSleeper());
  }

  @Inject
  SimpleScheduler(ExecutorService threadPool, Sleeper sleeper) {
    this.threadPool = threadPool;
    this.sleeper = sleeper;
    // TODO: Inject the real AllocationPersistenceUtil implementation in ATS.
    this.allocations =
        new Allocations(new AllocationPersistenceUtil.NoOpAllocationPersistenceUtil());
  }

  @Override
  public void start() {
    Future<?> possiblyIgnoredError = threadPool.submit(this);
    allocations.initialize();
  }

  @Override
  public void run() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        boolean hasNewAllocation = false;
        for (SimpleJobInfo job : jobs.values()) {
          sleeper.sleep(SCHEDULING_SMALL_INTERVAL);
          for (TestLocator testLocator : job.getTests().values()) {
            if (!allocations.containsTest(testLocator.getId())) {
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
      } catch (RuntimeException | Error e) {
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
          unallocate(allocations.getAllocationByTest(testId), removeDevices, false);
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
        Allocation allocation = allocations.getAllocationByTest(testId);
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

    DeviceScheduleUnit exDevice;
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
          Allocation allocation = allocations.getAllocationByDevice(deviceLocator.universalId());
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
      Allocation allocation = allocations.getAllocationByDevice(deviceLocator.universalId());
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
      Allocations.RemoveAllocationResult removeAllocationResult =
          allocations.removeAllocation(allocation);
      if (removeDevices) {
        for (DeviceLocator deviceLocator : removeAllocationResult.removedDevices()) {
          removeDevice(deviceLocator);
          logger.atInfo().log("Remove device %s", deviceLocator);
        }
      }
      if (closeTest) {
        if (removeAllocationResult.removedTest().isPresent()) {
          // Closes the test.
          com.google.devtools.mobileharness.api.model.job.TestLocator testLocator =
              removeAllocationResult.removedTest().get();
          String testId = testLocator.id();

          logger.atInfo().log("Remove test %s", testLocator);
          removeTest(testLocator.jobLocator().id(), testId);
        }
      }
      if (removeAllocationResult.removedTest().isPresent()
          || !removeAllocationResult.removedDevices().isEmpty()) {
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
    return allocateAdhocTestbedJob(job, test);
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
      }
      boolean added = allocations.addAllocation(allocation);
      if (!added) {
        return false;
      }
    }
    logger.atInfo().log("Created allocation %s", allocation);

    // After the allocation, we send events to notify external framework. If the framework can
    // not accept the event because the test/devices are removed, it will call scheduler to undo the
    // allocation.
    if (fireEvent) {
      postEvent(new AllocationEvent(allocation));
    }
    return true;
  }

  /** Goes through all labs to allocate devices for the given single-device test. */
  private boolean allocateSingleDeviceJob(JobScheduleUnit job, TestLocator test) {
    if (Flags.instance().enableSimpleSchedulerShuffle.getNonNull()) {
      List<DeviceScheduleUnit> allDevices = new ArrayList<>();
      labs.values().forEach(simpleLabInfo -> allDevices.addAll(simpleLabInfo.getDevices()));
      Collections.shuffle(allDevices);
      for (DeviceScheduleUnit device : allDevices) {
        if (checkAndAllocateSingleDevice(job, test, device, true)) {
          return true;
        }
      }
    } else {
      for (SimpleLabInfo labInfo : labs.values()) {
        for (DeviceScheduleUnit device : labInfo.getDevices()) {
          if (checkAndAllocateSingleDevice(job, test, device, true)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /** Checks whether the device can meet the job requirement. If so, allocates it to the test. */
  private boolean checkAndAllocateSingleDevice(
      JobScheduleUnit job, TestLocator test, DeviceScheduleUnit device, boolean fireEvent) {
    if (!allocations.containsDevice(device.locator().universalId())
        && ifDeviceSupports(device, job)) {
      // Found a suitable and idle device for the new test.
      return allocate(test, device, fireEvent);
    }
    return false;
  }

  /** Goes through all labs to allocate devices for the given adhoc testbed test. */
  private boolean allocateAdhocTestbedJob(JobScheduleUnit job, TestLocator test) {
    Set<String> types = job.subDeviceSpecs().getAllSubDeviceTypes();
    Collection<SimpleLabInfo> labInfos = labs.values();
    if (Flags.instance().enableSimpleSchedulerShuffle.getNonNull()) {
      List<SimpleLabInfo> labList = new ArrayList<>(labInfos);
      Collections.shuffle(labList);
      labInfos = labList;
    }
    for (SimpleLabInfo labInfo : labInfos) {
      ImmutableList<DeviceScheduleUnit> filteredDevices =
          labInfo.getDevices().stream()
              // Filter out already allocated devices
              .filter(device -> !allocations.containsDevice(device.locator().universalId()))
              // Filter out devices that don't support any desired types
              .filter(device -> !Collections.disjoint(device.types().getAll(), types))
              // Filter out devices that the user does not own
              .filter(device -> device.owners().support(job.jobUser().getRunAs()))
              .collect(toImmutableList());
      if (!filteredDevices.isEmpty()) {
        ImmutableList<DeviceScheduleUnit> deviceList =
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
