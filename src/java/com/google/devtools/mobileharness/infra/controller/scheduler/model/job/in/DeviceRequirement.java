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

package com.google.devtools.mobileharness.infra.controller.scheduler.model.job.in;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.job.in.Decorators;
import com.google.devtools.mobileharness.api.model.job.in.Dimensions;
import com.google.devtools.mobileharness.api.model.proto.Job;

/**
 * The requirement of one device for every allocation. Note the job can specify multiple {@link
 * DeviceRequirement} to request multiple devices for a single allocation.
 */
@AutoValue
public abstract class DeviceRequirement {

  /** Creates the requirement of one device for every allocation. */
  public static DeviceRequirement create(String deviceType) {
    return new AutoValue_DeviceRequirement(deviceType, new Decorators(), new Dimensions());
  }

  static DeviceRequirement fromProto(Job.DeviceRequirement proto) {
    DeviceRequirement deviceRequirement = create(proto.getDeviceType());
    deviceRequirement.decorators().addAll(proto.getDecoratorList());
    deviceRequirement.dimensions().addAll(proto.getDimensionsMap());
    return deviceRequirement;
  }

  public Job.DeviceRequirement toProto() {
    return Job.DeviceRequirement.newBuilder()
        .setDeviceType(deviceType())
        .addAllDecorator(decorators().getAll())
        .putAllDimensions(dimensions().getAll())
        .build();
  }

  /** The type that the device must support, e.g., "AndroidRealDevice". */
  public abstract String deviceType();

  /** The decorators that the device must support. */
  public abstract Decorators decorators();

  /** The dimensions that the device must support. */
  public abstract Dimensions dimensions();
}
