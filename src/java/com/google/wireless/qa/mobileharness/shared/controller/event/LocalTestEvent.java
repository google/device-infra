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

package com.google.wireless.qa.mobileharness.shared.controller.event;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;

/**
 * Event that signals the process of a test. These events can only be received on the same
 * workstation where the devices are physically connected.
 */
public interface LocalTestEvent extends ControllerEvent {

  /**
   * Returns all {@link Device}s of the test, whose key is {@link Device#getDeviceId()}, in the same
   * order of the allocation.
   */
  ImmutableMap<String, Device> getLocalDevices();

  /** Returns the <b>first</b> {@link Device} of the test. */
  default Device getLocalDevice() {
    return Iterables.get(getLocalDevices().values(), /* position= */ 0);
  }
}
