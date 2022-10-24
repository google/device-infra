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

package com.google.wireless.qa.mobileharness.shared.model.job.in;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.devtools.mobileharness.api.model.job.in.Decorators;
import com.google.devtools.mobileharness.infra.controller.scheduler.model.job.in.DeviceRequirement;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;

/** Value class that represents a complete device spec (type and dimensions) */
@AutoValue
public abstract class SubDeviceSpec {

  static SubDeviceSpec create(String deviceType, ScopedSpecs scopedSpecs, Timing timing) {
    return new AutoValue_SubDeviceSpec(DeviceRequirement.create(deviceType), scopedSpecs, timing);
  }

  public static SubDeviceSpec createForTesting(
      DeviceRequirement deviceRequirement, ScopedSpecs scopedSpecs, Timing timing) {
    return new AutoValue_SubDeviceSpec(deviceRequirement, scopedSpecs, timing);
  }

  public abstract DeviceRequirement deviceRequirement();

  /** Params for decorators stored in scoped in Decorator namespaces. */
  public abstract ScopedSpecs scopedSpecs();

  abstract Timing timing();

  /** The sub device type (e.g., "AndroidRealDevice"). */
  @Memoized
  public String type() {
    return deviceRequirement().deviceType();
  }

  /** Requested dimensions to match. */
  @Memoized
  public Dimensions dimensions() {
    return new Dimensions(timing(), deviceRequirement().dimensions());
  }

  /** Decorators to apply to this subdevice. */
  @Memoized
  public Decorators decorators() {
    return deviceRequirement().decorators();
  }
}
