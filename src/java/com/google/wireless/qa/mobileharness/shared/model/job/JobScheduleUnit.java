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

package com.google.wireless.qa.mobileharness.shared.model.job;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Dimensions;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.in.ScopedSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.util.concurrent.TimeUnit;

/**
 * Schedule unit of a Mobile Harness job. It contains the job information needed for the scheduler.
 */
public class JobScheduleUnit implements Cloneable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Locator of this job. */
  private final JobLocator locator;

  /** User of the job. */
  private final JobUser jobUser;

  /** Job type which contains the required device, driver and decorators information of the job. */
  private final JobType type;

  /** Job setting. */
  private final JobSetting setting;

  /** Parameters of the job. */
  private final Params params;

  /** Scoped Parameters of the job. */
  private final ScopedSpecs scopedSpecs;

  /** Specifications for subDevices. Used when creating an ad hoc testbed. */
  private final SubDeviceSpecs subDeviceSpecs;

  /** Time records. */
  private final Timing timing;

  /** Creates a schedule unit of a Mobile Harness job. */
  @VisibleForTesting
  public JobScheduleUnit(JobLocator locator, JobUser jobUser, JobType type, JobSetting setting) {
    this(locator, jobUser, type, setting, new Timing());
  }

  /**
   * Creates a schedule unit of a Mobile Harness job.
   *
   * <p>Sub-device specs will be set with the device in {@code type}.
   */
  public JobScheduleUnit(
      JobLocator locator, JobUser jobUser, JobType type, JobSetting setting, Timing timing) {
    this.locator = locator;
    this.jobUser = jobUser;
    this.type = type;
    this.setting = setting;

    this.timing = timing;
    this.params = new Params(timing);
    this.scopedSpecs = new ScopedSpecs(this.params, timing);
    this.subDeviceSpecs = new SubDeviceSpecs(this.params, timing);

    // Adds the first device.
    this.subDeviceSpecs.addSubDevice(
        type.getDevice(), ImmutableMap.of(), Lists.reverse(type.getDecoratorList()));
  }

  protected JobScheduleUnit(JobScheduleUnit other) {
    this(other.locator, other.jobUser, other.type, other.setting, (Timing) other.timing().clone());

    this.params.addAll(other.params.getAll());
    this.scopedSpecs.addAll(other.scopedSpecs.getAll());
    this.subDeviceSpecs.addAllSubDevices(other.subDeviceSpecs.getAllSubDevices());
    this.timing.setModifyTime(other.timing.getModifyTime());
  }

  /**
   * Creates a schedule unit of a Mobile Harness job by all the given final fields. Note: please
   * don't make this public at any time.
   */
  JobScheduleUnit(
      JobLocator locator,
      JobUser jobUser,
      JobType type,
      JobSetting setting,
      Timing timing,
      Params params,
      ScopedSpecs scopedSpecs,
      SubDeviceSpecs subDeviceSpecs) {
    this.locator = locator;
    this.jobUser = jobUser;
    this.type = type;
    this.setting = setting;
    this.timing = timing;
    this.params = params;
    this.scopedSpecs = scopedSpecs;
    this.subDeviceSpecs = subDeviceSpecs;
  }

  @Override
  public Object clone() {
    return new JobScheduleUnit(this);
  }

  /** Returns the locator of the job. */
  public JobLocator locator() {
    return locator;
  }

  /** Returns the job user of the test. */
  public JobUser jobUser() {
    return jobUser;
  }

  /** Gets the job type, which contains the required device, driver and decorators information. */
  public JobType type() {
    return type;
  }

  /** Gets the immutable job setting. */
  public JobSetting setting() {
    return setting;
  }

  /** Gets parameters of the job. */
  public Params params() {
    return params;
  }

  /** Scoped parameters of the job. */
  public ScopedSpecs scopedSpecs() {
    return scopedSpecs;
  }

  /** Required dimensions of the first device of the job. */
  public Dimensions dimensions() {
    /**
     * All dimensions are now stored in the subDeviceSpecs. For jobs requiring only a single device
     * (i.e., typical usage), this returns the Dimensions object of the first and only device spec.
     * TODO: this is a temporary fix to avoid changing the JobInfo dimensions API while supporting
     * ad hoc testbeds. JobInfo#dimensions() should be deprecated, but only after adhoc testbed
     * infrastructure is ready to onboard users.
     */
    if (subDeviceSpecs.hasMultipleDevices()) {
      logger.atWarning().atMostEvery(15, TimeUnit.SECONDS).log(
          "Calling dimensions() on Job %s"
              + " with multiple sub-device spec. Only returning first sub-device's dimensions.",
          locator());
    }
    return subDeviceSpecs.getAllSubDevices().get(0).dimensions();
  }

  /**
   * SubDevice specifications.
   *
   * <p>It contains the first device specified by the job type when creating
   * JobInfo/JobScheduleUnit.
   */
  public SubDeviceSpecs subDeviceSpecs() {
    return subDeviceSpecs;
  }

  /** Time records of the job. */
  public Timing timing() {
    return timing;
  }
}
