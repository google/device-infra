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

package com.google.devtools.mobileharness.infra.controller.scheduler.model.job;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Preconditions;
import com.google.devtools.mobileharness.api.model.job.JobLocator;
import com.google.devtools.mobileharness.api.model.job.in.Timeout;
import com.google.devtools.mobileharness.api.model.job.out.Timing;
import com.google.devtools.mobileharness.api.model.proto.Job;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceAllocationPriority;
import com.google.devtools.mobileharness.api.model.proto.Job.JobFeature;
import com.google.devtools.mobileharness.api.model.proto.Job.JobSetting;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.devtools.mobileharness.api.model.proto.Job.Priority;
import com.google.devtools.mobileharness.infra.controller.scheduler.model.job.in.DeviceRequirement;
import com.google.devtools.mobileharness.infra.controller.scheduler.model.job.in.DeviceRequirements;
import com.google.devtools.mobileharness.infra.controller.test.util.JobTimer;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Optional;

/**
 * Schedule unit of a Mobile Harness job. It contains the job information needed for the scheduler.
 */
@AutoValue
public abstract class JobScheduleUnit {
  /** Returns the ID and name of the job. */
  public abstract JobLocator locator();

  /** Returns the user of the job. */
  public abstract String user();

  /** Gets the driver type. */
  public abstract String driver();

  /** The requirements of the devices for every allocation for this job. */
  public abstract DeviceRequirements deviceRequirements();

  /** Job priority. */
  public abstract Priority priority();

  /** Job timeout setting. See go/mh-timing for more detail. */
  public abstract Timeout timeout();

  /** Time records of the job. */
  public abstract Timing timing();

  /** Timer of the job which starts when the job starts and expires when the job expires. */
  public abstract CountDownTimer timer();

  public abstract DeviceAllocationPriority deviceAllocationPriority();

  /** Create a builder for creating {@link JobScheduleUnit} instances. */
  public static Builder newBuilder() {
    return new $AutoValue_JobScheduleUnit.Builder()
        .setPriority(Priority.DEFAULT)
        .setTimeout(Timeout.getDefaultInstance());
  }

  public static JobScheduleUnit fromProtos(
      JobLocator jobLocator, JobFeature jobFeatureProto, JobSetting jobSettingProto) {
    Builder builder =
        newBuilder()
            .setLocator(jobLocator)
            .setUser(jobFeatureProto.getUser().getRunAs())
            .setDriver(jobFeatureProto.getDriver())
            .setPriority(jobSettingProto.getPriority())
            .setDeviceAllocationPriority(jobFeatureProto.getDeviceAllocationPriority())
            .setDeviceRequirements(DeviceRequirements.fromProto(jobFeatureProto));
    if (jobSettingProto.hasTimeout()) {
      builder.setTimeout(Timeout.fromProto(jobSettingProto.getTimeout()));
    }
    return builder.build();
  }

  /** Builder for creating {@link JobScheduleUnit} instances. */
  @AutoValue.Builder
  public abstract static class Builder {
    private String deviceType;

    /** Required. */
    public abstract Builder setLocator(JobLocator locator);

    /** Required. */
    public abstract Builder setUser(String user);

    /** Required. */
    public abstract Builder setDriver(String driver);

    /** Required. */
    @CanIgnoreReturnValue
    public Builder setDeviceType(String deviceType) {
      this.deviceType = Preconditions.checkNotNull(deviceType, "Device type is not specified");
      return this;
    }

    /** Optional. */
    public abstract Builder setPriority(Priority priority);

    /** Optional. */
    public abstract Builder setTimeout(Timeout timeout);

    /** Optional. */
    public abstract Builder setDeviceAllocationPriority(DeviceAllocationPriority priority);

    /** DO NOT set this field unless you have to. */
    public abstract Builder setTiming(Timing timing);

    /** DO NOT set this field directly. Uses {@link #setDeviceType(String)} instead. */
    abstract Builder setDeviceRequirements(DeviceRequirements deviceRequirements);

    /** DO NOT set this field directly. It will be auto generated. */
    abstract Builder setTimer(CountDownTimer timer);

    abstract Timeout timeout();

    abstract Optional<DeviceRequirements> deviceRequirements();

    abstract Optional<Timing> timing();

    abstract JobScheduleUnit autoBuild();

    public JobScheduleUnit build() {
      if (timing().isEmpty()) {
        setTiming(new Timing());
      }
      setTimer(JobTimer.create(timing().orElseThrow(AssertionError::new), timeout()));

      if (deviceType != null) {
        setDeviceRequirements(new DeviceRequirements(deviceType));
      } else if (deviceRequirements().isEmpty()) {
        // Changes the error message to suggest setting the deviceType field. Otherwise, autoBuild()
        // will suggest setting the deviceRequirements field directly.
        throw new IllegalStateException("Missing required properties: deviceType");
      }
      return autoBuild();
    }
  }

  /** Converts to {@link JobFeature} proto. */
  @Memoized
  public JobFeature toFeature() {
    JobFeature.Builder jobFeatureBuilder =
        JobFeature.newBuilder()
            // TODO: Scheduler doesn't care about the original user. But
            // JobScheduleUnit.fromProto(...).toFeature() will drop the real user info in JobFeature
            // proto.
            .setUser(JobUser.newBuilder().setRunAs(user()).build())
            .setDriver(driver());
    Job.DeviceRequirements.Builder deviceRequirements =
        Job.DeviceRequirements.newBuilder()
            .addAllSharedDimension(deviceRequirements().sharedDimensions());
    for (DeviceRequirement deviceRequirement : deviceRequirements().getAll()) {
      Job.DeviceRequirement deviceRequirementProto = deviceRequirement.toProto();
      deviceRequirements.addDeviceRequirement(deviceRequirementProto);
    }
    jobFeatureBuilder.setDeviceRequirements(deviceRequirements);
    jobFeatureBuilder.setDeviceAllocationPriority(deviceAllocationPriority());
    return jobFeatureBuilder.build();
  }
}
