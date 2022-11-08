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

import com.google.common.collect.ImmutableSortedSet;
import java.util.List;

/** A simple {@link CompositeDevice}. */
public class SimpleCompositeDevice extends BaseDevice implements CompositeDevice {

  private final ImmutableSortedSet<Device> subDevices;

  public SimpleCompositeDevice(String deviceId, List<Device> subDevices) {
    super(deviceId, /* managedDeviceInfo= */ false);
    this.subDevices = ImmutableSortedSet.copyOf(subDevices);
  }

  @Override
  public ImmutableSortedSet<Device> getManagedDevices() {
    return subDevices;
  }
}
