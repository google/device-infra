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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/**
 * Base decorator for ChromeOS devices. This class provides common functionality for interacting
 * with ChromeOS devices and the inventory system.
 */
public abstract class CrosBaseDecorator extends BaseDecorator implements CrosDecoratorSpec {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public CrosBaseDecorator(Driver decoratedDriver, TestInfo testInfo) {
    super(decoratedDriver, testInfo);
  }

  /**
   * Executes the decorator logic. This method prepares the decorator, runs the decorated driver,
   * and then tears down the decorator. The {@code inventoryClient} is closed automatically after
   * the execution.
   *
   * @param testInfo The test information.
   * @throws MobileHarnessException if a MobileHarness error occurs.
   * @throws InterruptedException if the thread is interrupted.
   */
  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    try {
      // Prepare the decorator.
      prepare(testInfo);
      // Pass the control to the driver.
      getDecorated().run(testInfo);
    } finally {
      // Clean up decorator.
      try {
        tearDown(testInfo);
      } catch (MobileHarnessException e) {
        getTest()
            .log()
            .atWarning()
            .alsoTo(logger)
            .withCause(e)
            .log("Failed to tear down decorator.");
      }
    }
  }

  protected abstract void prepare(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException;

  protected abstract void tearDown(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException;

  protected String getInventoryServiceHostname() {
    return getTest().jobInfo().params().get(INVENTORY_SERVICE_HOST, DEFAULT_INVENTORY_SERVICE_HOST);
  }

  protected int getInventoryServicePort() {
    return getTest()
        .jobInfo()
        .params()
        .getInt(INVENTORY_SERVICE_PORT, DEFAULT_INVENTORY_SERVICE_PORT);
  }

  protected String getInventoryServiceAddress() {
    return String.format("%s:%d", getInventoryServiceHostname(), getInventoryServicePort());
  }

  protected String deviceId() {
    return getDevice().getDeviceId();
  }

  /**
   * Returns the device name from the device ID.
   *
   * <p>The device ID is expected to be in the format "device_name:port_number". This method removes
   * the port number if it is present and returns the device name.
   *
   * <p>Example: "test_device:1234" -> "test_device"
   *
   * @param deviceId The device ID.
   * @return The device name.
   */
  protected String deviceName(String deviceId) {
    int lastColonIndex = deviceId.lastIndexOf(':');
    if (lastColonIndex > 0) {
      return deviceId.substring(0, lastColonIndex);
    }
    return deviceId;
  }
}
