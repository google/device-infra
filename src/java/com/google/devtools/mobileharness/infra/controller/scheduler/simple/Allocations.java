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

package com.google.devtools.mobileharness.infra.controller.scheduler.simple;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/** The allocations in the {@link SimpleScheduler}. */
@NotThreadSafe
final class Allocations {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** {{@link DeviceLocator#universalId()}, {@link Allocation}} mapping. */
  private final Map<String, Allocation> deviceAllocations = new ConcurrentHashMap<>();

  /** {TestID, Allocation} mapping. */
  private final Map<String, Allocation> testAllocations = new ConcurrentHashMap<>();

  @AutoValue
  abstract static class RemoveAllocationResult {
    public static RemoveAllocationResult create(
        Optional<TestLocator> removedTest, ImmutableList<DeviceLocator> removedDevices) {
      return new AutoValue_Allocations_RemoveAllocationResult(removedTest, removedDevices);
    }

    abstract Optional<TestLocator> removedTest();

    abstract ImmutableList<DeviceLocator> removedDevices();
  }

  /**
   * Adds the allocation to the allocations.
   *
   * @return false if the test or the devices already have allocation, Otherwise, add the allocation
   *     and return true.
   */
  boolean addAllocation(Allocation allocation) {
    Allocation testAllocation = getAllocationByTest(allocation.getTest().id());
    if (testAllocation != null) {
      logger.atWarning().log(
          "Test %s has allocation %s. Can not create allocation %s.",
          allocation.getTest().id(), testAllocation, allocation);
      return false;
    }

    for (DeviceLocator deviceLocator : allocation.getAllDevices()) {
      Allocation deviceAllocation = getAllocationByDevice(deviceLocator.universalId());
      if (deviceAllocation != null) {
        logger.atWarning().log(
            "Device %s has allocation %s. Can not create allocation %s.",
            deviceLocator, deviceAllocation, allocation);
        return false;
      }
    }

    testAllocations.put(allocation.getTest().id(), allocation);
    for (DeviceLocator deviceLocator : allocation.getAllDevices()) {
      deviceAllocations.put(deviceLocator.universalId(), allocation);
    }

    return true;
  }

  /**
   * Removes the allocation from the allocations.
   *
   * @return the removed allocations' owner test and devices.
   */
  RemoveAllocationResult removeAllocation(Allocation allocation) {
    Optional<TestLocator> removedTest = Optional.empty();
    ImmutableList.Builder<DeviceLocator> removedDevices = ImmutableList.builder();

    TestLocator testLocator = allocation.getTest();
    Allocation testAllocation = getAllocationByTest(testLocator.id());
    if (testAllocation == null) {
      logger.atInfo().log("Skip unallocate test because it is new/closed");
    } else if (!allocation.equals(testAllocation)) {
      logger.atWarning().log(
          "Inconsistent allocation info with test %s, expect %s, got %s",
          testLocator, allocation, testAllocation);
    } else {
      logger.atInfo().log("Un-assign test %s", testLocator);
      testAllocations.remove(testLocator.id());
      removedTest = Optional.of(testLocator);
    }

    for (DeviceLocator deviceLocator : allocation.getAllDevices()) {
      Allocation deviceAllocation = getAllocationByDevice(deviceLocator.universalId());
      if (deviceAllocation == null) {
        logger.atInfo().log("Skip unallocate device %s because it is already idle", deviceLocator);
      } else if (!allocation.equals(deviceAllocation)) {
        logger.atWarning().log(
            "Skip unallocate device %s because it is assigned to a different test: %s",
            deviceLocator, deviceAllocation);
      } else {
        logger.atInfo().log("Free device %s", deviceLocator);
        deviceAllocations.remove(deviceLocator.universalId());
        removedDevices.add(deviceLocator);
      }
    }

    return RemoveAllocationResult.create(removedTest, removedDevices.build());
  }

  @Nullable
  Allocation getAllocationByDevice(String deviceId) {
    return deviceAllocations.get(deviceId);
  }

  @Nullable
  Allocation getAllocationByTest(String testId) {
    return testAllocations.get(testId);
  }

  boolean containsTest(String testId) {
    return testAllocations.containsKey(testId);
  }

  boolean containsDevice(String deviceId) {
    return deviceAllocations.containsKey(deviceId);
  }
}