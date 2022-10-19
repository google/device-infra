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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The requirements of the devices for every allocation. */
public class DeviceSpecs {
  private final List<DeviceSpec> devices = new ArrayList<>();

  /**
   * Dimension keys that must be present and share a common value across all devices being used in
   * this job. Only used when multiple devices are required.
   */
  private final List<String> sharedDimensions = new ArrayList<>();

  /**
   * The requirements of the first device for every allocations. Mobile Harness by default will
   * allocate one device for every allocations. The job can request more devices(a.k.a. sub-devices)
   * to every allocations via {@link #addSubDevice(String)}.
   *
   * @param deviceType the required type of the first device of every allocation
   */
  public DeviceSpecs(String deviceType) {
    DeviceSpec device = DeviceSpec.create(deviceType);
    devices.add(device);
  }

  /** Adds one extra device to every allocations. */
  public DeviceSpec addSubDevice(String subDeviceType) {
    DeviceSpec subDevice = DeviceSpec.create(subDeviceType);
    devices.add(subDevice);
    return subDevice;
  }

  /**
   * Adds shared dimension key, which must be present and share a common value across all devices
   * being used in this job. Only used when multiple devices are required.
   */
  @CanIgnoreReturnValue
  public DeviceSpecs addSharedDimension(String dimensionKey) {
    sharedDimensions.add(dimensionKey);
    return this;
  }

  /**
   * Gets the requirement of the only device for every allocations if the job only requires one
   * device for every allocation. If the job is requiring multiple devices for every allocation, the
   * requirement of the first device is returned.
   */
  public DeviceSpec get() {
    return devices.get(0);
  }

  /**
   * Gets the requirements of all the devices for every allocations. Every entry of the list
   * represents the requirements of one device in every allocation. The entry number >= 1.
   */
  public List<DeviceSpec> getAll() {
    return Collections.unmodifiableList(devices);
  }

  /**
   * Whether the current job is requesting sub-devices, meaning whether there are >= 2 devices in
   * every allocation.
   */
  public boolean hasSubDevices() {
    return devices.size() > 1;
  }

  /**
   * Gets the dimension keys that must be present and share a common value across all devices being
   * used in this job. Only used when multiple devices are required.
   */
  public List<String> sharedDimensions() {
    return ImmutableList.copyOf(sharedDimensions);
  }

  /** Returns the number of devices in every allocation. Should be >=1. */
  public int size() {
    return devices.size();
  }
}
