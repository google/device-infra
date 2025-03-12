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

package com.google.devtools.mobileharness.infra.controller.device;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import javax.annotation.Nullable;

/** Executor responsible for driving tests on a single Device. */
public interface TestExecutor {
  /**
   * Adds the test to run on this device.
   *
   * @throws MobileHarnessException if device is not available
   */
  void reserve(LocalDeviceTestExecutor test) throws MobileHarnessException;

  /**
   * Tries to stop the current device runner if it is running the giving test. It will be stopped as
   * quickly as possible.
   */
  void cancel(LocalDeviceTestExecutor test);

  /** Get the Device associated with this TestExecutor. */
  Device getDevice();

  /** Whether this executor is capable of running tests. */
  boolean isAlive();

  /** The current test running on this TestExecutor, or null if no test is running. */
  @Nullable
  LocalDeviceTestExecutor getTest();
}
