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

package com.google.devtools.mobileharness.infra.controller.scheduler;

import com.google.common.eventbus.EventBus;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceScheduleUnit;
import com.google.devtools.mobileharness.api.model.lab.LabScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.TestScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import javax.annotation.Nullable;

/** Scheduler for scheduling the device resource to execute multiple jobs in parallel. */
public abstract class AbstractScheduler {
  /** Event bus for scheduler events. */
  private final EventBus schedulerInternalBus = new EventBus();

  /** Starts the scheduler to do allocation. */
  public abstract void start();

  /**
   * Adds the given job if not exist.
   *
   * @throws MobileHarnessException if job already exists
   */
  public abstract void addJob(JobScheduleUnit job) throws MobileHarnessException;

  /**
   * Removes the job and all belonging tests, and releases the devices. Not effect if job doesn't
   * exists.
   *
   * @param removeDevices whether to remove the devices from scheduler
   */
  public abstract void removeJob(String jobId, boolean removeDevices) throws InterruptedException;

  /**
   * Adds the given test if not exist.
   *
   * @throws MobileHarnessException if test already exists, or the belonging job not found
   */
  public abstract void addTest(TestScheduleUnit test) throws MobileHarnessException;

  /** Updates/inserts the device to scheduler. */
  public abstract void upsertDevice(DeviceScheduleUnit device, LabScheduleUnit belongingLab);

  /** Frees the given device. It will release all devices in the same allocation. */
  public abstract void unallocate(
      DeviceLocator deviceLocator, boolean removeDevices, boolean closeTest)
      throws InterruptedException;

  /** Undoes the allocation. */
  public abstract void unallocate(
      @Nullable Allocation allocation, boolean removeDevices, boolean closeTest)
      throws InterruptedException;

  /** Registers the handler to receive scheduler events. */
  public void registerEventHandler(Object handler) {
    schedulerInternalBus.register(handler);
  }

  /** Unregisters the event handler to stop receiving scheduler events. */
  public void unregisterEventHandler(Object handler) {
    schedulerInternalBus.unregister(handler);
  }

  /** Posts the event to all event handlers. */
  protected void postEvent(Object event) {
    schedulerInternalBus.post(event);
  }

  /** Checks whether the device supports the given job info. Supports regex in job dimensions. */
  public final boolean ifDeviceSupports(DeviceScheduleUnit device, JobScheduleUnit job) {
    // Checks job type.
    JobType jobType = job.type();
    boolean isJobTypeSupported =
        device.types().support(jobType.getDevice())
            && device.drivers().support(jobType.getDriver())
            && device.decorators().support(jobType.getDecoratorList());
    return isJobTypeSupported
        && device.owners().support(job.jobUser().getRunAs())
        && device.dimensions().supportAndSatisfied(job.dimensions().getAll());
  }
}
