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

package com.google.devtools.mobileharness.api.model.allocation;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import java.util.Collection;
import java.util.Objects;

/** Represents an allocation of devices for a test. */
public class Allocation implements Cloneable {

  /** The test whom the devices are assigned to. */
  private final TestLocator test;

  /** A list of locators for the allocated devices. */
  private final ImmutableList<DeviceLocator> devices;

  /** Creates an allocation which assigns the given device to the given test. */
  public Allocation(TestLocator testLocator, DeviceLocator deviceLocator) {
    this(testLocator, ImmutableList.of(deviceLocator));
  }

  /**
   * Creates an allocation which assigns the given device list to the given test. The allocation
   * should have at least one device.
   */
  public Allocation(TestLocator testLocator, Collection<DeviceLocator> deviceLocators) {
    test = Preconditions.checkNotNull(testLocator);
    Preconditions.checkState(
        !deviceLocators.isEmpty(), "One allocation should have at least one device.");
    devices = ImmutableList.copyOf(deviceLocators);
  }

  /** Gets the test whom the devices are assigned to. */
  public TestLocator getTest() {
    return test;
  }

  /** Gets the locator of the first allocated device. */
  public DeviceLocator getDevice() {
    return devices.get(0);
  }

  /** Returns whether there are multiple devices in the current allocation. */
  public boolean hasSubDevices() {
    return devices.size() > 1;
  }

  /** Gets locators for all the devices of this allocation. */
  public ImmutableList<DeviceLocator> getAllDevices() {
    return devices;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("test", getTest())
        .add("devices", getAllDevices())
        .omitNullValues()
        .toString();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Allocation)) {
      return false;
    }
    Allocation otherAlloc = (Allocation) other;
    return Objects.equals(getTest(), otherAlloc.getTest())
        && Objects.equals(getAllDevices(), otherAlloc.getAllDevices());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getTest(), getAllDevices());
  }
}
