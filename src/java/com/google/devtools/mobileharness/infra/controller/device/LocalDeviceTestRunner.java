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
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;

/** Running test life cycle. */
public interface LocalDeviceTestRunner {

  /** Returns whether the current device is still alive. */
  boolean isAlive();

  /**
   * Tries to stop the current device runner if it is running the giving test. It will be stopped as
   * quickly as possible.
   */
  void cancel(LocalDeviceTestExecutor test);

  /** Checks whether the job type is supported by this device. */
  boolean isJobSupported(JobType jobType);

  /** Returns the device which is associated with this runner. */
  Device getDevice();

  /** Gets status of a device. */
  DeviceStatus getDeviceStatus();

  /**
   * Adds the test to run on this device.
   *
   * @throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException if device is
   *     not available
   */
  void reserve(LocalDeviceTestExecutor test) throws MobileHarnessException;

  /** Gets the current running test. */
  LocalDeviceTestExecutor getTest();
}
