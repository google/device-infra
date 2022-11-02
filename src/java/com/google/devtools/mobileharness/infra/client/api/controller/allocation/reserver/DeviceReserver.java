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

package com.google.devtools.mobileharness.infra.client.api.controller.allocation.reserver;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.time.Duration;

/** For reserving devices for tests. */
public abstract class DeviceReserver {
  protected static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Duration TEMP_ALLOCATION_KEY_TTL = Duration.ofMinutes(20);

  /**
   * Temporarily adds allocation key to a given device.
   *
   * <p>Other tests must specify the dimension {"allocation_key", <allocation_key>} to get allocated
   * to the same devices. The allocation key will expire in 20 minutes.
   *
   * <p>IMPORTANT: If this is used with the purpose to get the next test to use the same device as
   * the current one, it is not recommended to call this method during the test that is using the
   * given device, and better call it on receiving TestEndedEvent. Otherwise, if the device is
   * occupied by a test that needs more than 20 minutes to finish, the allocation key will expire
   * and the following tests with dimension {"allocation_key, <allocatin_key>} will never get to
   * allocate the device.
   *
   * @param deviceLocator the device to add temp allocation key to
   * @param allocationKey the allocation key that need to be specified by tests that attempt to
   *     allocate the device
   */
  public void addTempAllocationKeyToDevice(DeviceLocator deviceLocator, String allocationKey)
      throws MobileHarnessException, InterruptedException {
    addTempAllocationKeyToDevice(deviceLocator, allocationKey, TEMP_ALLOCATION_KEY_TTL);
    logger.atInfo().log(
        "Set temp allocation key %s to device %s with ttl %s",
        allocationKey, deviceLocator, TEMP_ALLOCATION_KEY_TTL);
  }

  /**
   * Temporarily reserves allocation of a device, makes it can only be allocated with a given
   * allocationKey.
   *
   * <p>After this method is called, the device can only be allocated by the test that specified
   * {"allocation_key", <allocationKey>} in its dimensions.
   *
   * @param deviceLocator the device to add temp allocation key to
   * @param allocationKey the allocation key that need to be specified by tests that attempt to
   *     allocate the device
   * @param ttl the duration for the reservation
   */
  public void addTempAllocationKeyToDevice(
      DeviceLocator deviceLocator, String allocationKey, Duration ttl)
      throws MobileHarnessException, InterruptedException {
    logger.atWarning().log("Method [tempReserveAllocation] has no real implementation.");
  }

  /**
   * Temporarily reserves allocation of a device, makes it can only be allocated with a given
   * allocationKey.
   *
   * <p>After this method is called, the device can only be allocated by the test that specified
   * {<allocationDimensionName>, <allocationKey>} in its dimensions.
   *
   * @param deviceLocator the device to add temp allocation key to
   * @param allocationDimensionName the name of the dimension to use as allocation key
   * @param allocationKey the allocation key that need to be specified by tests that attempt to
   *     allocate the device
   * @param ttl the duration for the reservation
   */
  public void addTempAllocationKeyToDevice(
      DeviceLocator deviceLocator,
      String allocationDimensionName,
      String allocationKey,
      Duration ttl)
      throws MobileHarnessException, InterruptedException {
    logger.atWarning().log("Method [tempReserveAllocation] has no real implementation.");
  }
}
