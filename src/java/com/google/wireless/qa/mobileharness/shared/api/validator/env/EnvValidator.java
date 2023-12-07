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

package com.google.wireless.qa.mobileharness.shared.api.validator.env;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;

/**
 * Validates whether a driver/decorator is supported by the current system environment. Each env
 * validator class is associated with one driver/decorator class.
 */
public interface EnvValidator {
  /**
   * Whether the driver/decorator is supported by the current system environment.
   *
   * @param device the device to load this driver/decorator
   * @throws MobileHarnessException if the env is not valid
   * @throws InterruptedException if the current thread or its sub-thread is {@linkplain
   *     Thread#interrupt() interrupted} by another thread
   */
  default void validate(Device device) throws MobileHarnessException, InterruptedException {
    // Does nothing.
  }
}
