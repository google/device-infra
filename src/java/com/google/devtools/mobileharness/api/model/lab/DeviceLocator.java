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

package com.google.devtools.mobileharness.api.model.lab;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.devtools.mobileharness.api.model.proto.Device;

/** For locating a local, or remote device. */
@AutoValue
public abstract class DeviceLocator {
  /** Device ID. Unique within a lab, but may not be unique across labs. */
  public abstract String id();

  /** Belong lab locator, or {@link LabLocator#LOCALHOST} for local device. */
  public abstract LabLocator labLocator();

  /** Creates a locator for a device. */
  public static DeviceLocator of(String deviceId, LabLocator labLocator) {
    return new AutoValue_DeviceLocator(deviceId, labLocator);
  }

  /** Creates a locator from a proto. */
  public static DeviceLocator of(Device.DeviceLocator proto) {
    return of(proto.getId(), LabLocator.of(proto.getLabLocator()));
  }

  /** Returns universal unique ID, which is unique across labs. */
  public String universalId() {
    if (labLocator().equals(LabLocator.LOCALHOST)) {
      return id();
    } else {
      return id() + "@" + labLocator().ip();
    }
  }

  @Memoized
  @Override
  public String toString() {
    if (labLocator().equals(LabLocator.LOCALHOST)) {
      return id();
    } else {
      return id() + "@" + labLocator();
    }
  }

  // Does not memorize this. Lab ports can be added afterwards.
  public Device.DeviceLocator toProto() {
    return Device.DeviceLocator.newBuilder()
        .setId(id())
        .setLabLocator(labLocator().toProto())
        .build();
  }
}
