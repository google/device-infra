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

package com.google.wireless.qa.mobileharness.shared.api.device;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * A virtual device which acts as a placeholder for any testbed subdevice that has no supported
 * representation in Mobile Harness (e.g., a callbox, attenuators, etc.).
 *
 * <p>NOTE: These Devices should only ever be instantiated within a {@link TestbedDevice}, and
 * should never affect the operation of any "core" Mobile Harness device types.
 */
public class MiscTestbedSubDevice extends MiscDevice implements PortServiceDevice {

  private volatile ImmutableSet<String> registeredPortNames = ImmutableSet.of();

  public MiscTestbedSubDevice(String deviceId) {
    super(deviceId);
  }

  @Override
  public void setRegisteredPortNames(Set<String> portNames) {
    this.registeredPortNames = ImmutableSet.copyOf(portNames);
  }

  @Override
  public ImmutableSet<String> getRegisteredPortNames() {
    return registeredPortNames;
  }
}
