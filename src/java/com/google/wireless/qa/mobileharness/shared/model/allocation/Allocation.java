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

package com.google.wireless.qa.mobileharness.shared.model.allocation;


import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Represents an allocation of devices for a test. */
public class Allocation implements Cloneable {

  /** The test whom the devices are assigned to. */
  private final TestLocator test;

  /** A list of locators for the allocated devices. */
  private final ImmutableList<DeviceLocator> devices;

  /**
   * A list of dimensions for each allocated device. Entries correspond to those in {@link
   * #devices}, and therefore the number of entries in {@code dimensionsList} should be the same as
   * the number in {@link #devices}.
   */
  private volatile ImmutableList<Multimap<String, String>> dimensionsList;

  /** Creates an allocation which assigns the given single device to the given test. */
  public Allocation(
      TestLocator testLocator,
      DeviceLocator deviceLocator,
      @Nullable Multimap<String, String> dimensions) {
    this(
        testLocator,
        Collections.singletonList(deviceLocator),
        Collections.singletonList(dimensions == null ? ImmutableSetMultimap.of() : dimensions));
  }

  /** Creates an allocation which assigns the given device list to the given test. */
  public Allocation(
      TestLocator testLocator,
      List<DeviceLocator> deviceLocators,
      List<Multimap<String, String>> dimensionsList) {
    test = Preconditions.checkNotNull(testLocator);
    Preconditions.checkNotNull(deviceLocators);
    Preconditions.checkState(!deviceLocators.isEmpty()); // Should have at least one device.
    Preconditions.checkNotNull(deviceLocators.get(0)); // Should have at least one device.
    Preconditions.checkState(dimensionsList.size() == deviceLocators.size());
    devices = ImmutableList.copyOf(deviceLocators);
    ImmutableList.Builder<Multimap<String, String>> dimensionsListBuilder =
        new ImmutableList.Builder<>();
    for (Multimap<String, String> deviceDimension : dimensionsList) {
      dimensionsListBuilder.add(ImmutableSetMultimap.copyOf(deviceDimension));
    }
    this.dimensionsList = dimensionsListBuilder.build();
  }

  public Allocation(
      com.google.devtools.mobileharness.api.model.allocation.Allocation newAllocation) {
    this(
        new TestLocator(newAllocation.getTest()),
        newAllocation.getAllDevices().stream().map(DeviceLocator::new).collect(Collectors.toList()),
        Collections.nCopies(newAllocation.getAllDevices().size(), ImmutableMultimap.of()));
  }

  /** Returns whether the (multiple) allocated devices need to be combined into a single testbed. */
  public boolean hasMultipleDevices() {
    return devices.size() > 1;
  }

  /** Gets the test whom the devices are assigned to. */
  public TestLocator getTest() {
    return test;
  }

  /** Gets the locator of the single allocated device. */
  public DeviceLocator getDevice() {
    // TODO: when rolling out ad-hoc testbeds on remote lab runs, calling this method on a
    // multi-device allocation should throw.
    return devices.get(0);
  }

  /**
   * Gets the index in {@link #devices} by the given deviceId.
   *
   * <p>The index is corresponding to the index of SubDeviceSpec stored in {@code SubDeviceSpecs}.
   */
  public Optional<Integer> getDeviceIndexById(String deviceId) {

    int i = 0;
    for (DeviceLocator locator : devices) {
      if (locator.getSerial().equals(deviceId)) {
        return Optional.of(Integer.valueOf(i));
      }
      ++i;
    }
    return Optional.empty();
  }

  /** Gets locators for all the devices of this allocation. */
  public ImmutableList<DeviceLocator> getAllDeviceLocators() {
    return devices;
  }

  /** Gets the dimensions of the single allocated device. */
  public Multimap<String, String> getDimensions() {
    // TODO: when rolling out ad-hoc testbeds on remote lab runs, calling this method on a
    // multi-device allocation should throw.
    return dimensionsList.get(0);
  }

  /** Gets a map of locator to dimensions of all devices. */
  public Map<DeviceLocator, Multimap<String, String>> getAllDevices() {
    ImmutableMap.Builder<DeviceLocator, Multimap<String, String>> dimensionsMapBuilder =
        new ImmutableMap.Builder<>();
    for (int i = 0; i < dimensionsList.size(); i++) {
      dimensionsMapBuilder.put(devices.get(i), dimensionsList.get(i));
    }
    return dimensionsMapBuilder.buildOrThrow();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("test", getTest())
        .add("devices", getAllDeviceLocators())
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
        && Objects.equals(getAllDeviceLocators(), otherAlloc.getAllDeviceLocators())
        && Objects.equals(getAllDevices(), otherAlloc.getAllDevices());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getTest(), getAllDeviceLocators(), getAllDevices());
  }

  public com.google.devtools.mobileharness.api.model.allocation.Allocation toNewAllocation() {
    return new com.google.devtools.mobileharness.api.model.allocation.Allocation(
        test.toNewTestLocator(),
        devices.stream().map(DeviceLocator::toNewDeviceLocator).collect(Collectors.toList()));
  }
}
