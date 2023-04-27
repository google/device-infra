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

package com.google.devtools.mobileharness.api.model.job.in;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.devtools.mobileharness.api.model.proto.Job;
import com.google.devtools.mobileharness.infra.controller.scheduler.model.job.in.DeviceRequirement;

/**
 * The requirement of one device for every allocation. Note the job can specify multiple {@link
 * DeviceRequirement} to request multiple devices for a single allocation.
 *
 * <p>TODO: Use ScopedSpecs scopedSpecs() to support params/files for decorators stored in scoped in
 * Decorator namespaces.
 */
@AutoValue
public abstract class DeviceSpec {

  abstract DeviceRequirement deviceRequirement();

  static DeviceSpec create(String deviceType) {
    return new AutoValue_DeviceSpec(DeviceRequirement.create(deviceType));
  }

  /** The type that the device must support, e.g., "AndroidRealDevice". */
  @Memoized
  public String deviceType() {
    return deviceRequirement().deviceType();
  }

  /** The dimensions that the device must support. */
  @Memoized
  public Dimensions dimensions() {
    return deviceRequirement().dimensions();
  }

  /** The decorators that the device must support. */
  @Memoized
  public Decorators decorators() {
    return deviceRequirement().decorators();
  }

  public Job.DeviceRequirement toDeviceRequirementProto() {
    return deviceRequirement().toProto();
  }
}
