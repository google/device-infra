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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/**
 * Base class for all drivers.
 *
 * <p>See {@link Driver} about how to instantiate a driver.
 */
public abstract class BaseDriver implements Driver {
  /** The device which loads this driver. */
  private final Device device;

  private final TestInfo testInfo;

  /** Constructor with the given device instance. */
  public BaseDriver(Device device, TestInfo testInfo) {
    this.device = checkNotNull(device);
    this.testInfo = checkNotNull(testInfo);
  }

  @Override
  public Device getDevice() {
    return device;
  }

  @Override
  public TestInfo getTest() {
    return testInfo;
  }

  @Override
  @Deprecated
  public void run(com.google.wireless.qa.mobileharness.shared.api.job.TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    run(testInfo.toNewTestInfo());
  }
}
