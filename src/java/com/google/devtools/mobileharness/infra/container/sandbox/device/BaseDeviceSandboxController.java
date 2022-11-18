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

package com.google.devtools.mobileharness.infra.container.sandbox.device;

import com.google.wireless.qa.mobileharness.shared.api.device.Device;

/** Base implementation for {@link DeviceSandboxController}. */
public abstract class BaseDeviceSandboxController implements DeviceSandboxController {

  private final Device device;

  protected BaseDeviceSandboxController(Device device) {
    this.device = device;
  }

  protected Device getDevice() {
    return device;
  }
}
