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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.testbed.adhoc.AdhocTestbedConfig;
import java.util.List;

/** Factory methods for creating TestbedDevices for Fusion-based Lab Hosts. */
public class FusionTestbedDeviceFactory {

  /**
   * Creates a new testbed device with managed device info.
   *
   * @param deviceId The id of the testbed.
   * @param preCreatedSubDevices pre-created sub device instances from MH device manager. If
   *     specified, this testbed device will not create sub device instances anymore.
   * @throws MobileHarnessException Thrown if the testbed could not be loaded.
   */
  public static TestbedDevice newTestbedDeviceWithManagedDeviceInfo(
      String deviceId, List<Device> preCreatedSubDevices) throws MobileHarnessException {
    return new TestbedDevice(
        deviceId,
        () -> ImmutableMap.of(deviceId, AdhocTestbedConfig.create(deviceId, preCreatedSubDevices)),
        preCreatedSubDevices,
        /* deviceFactory= */ null,
        /* apiConfig= */ null,
        /* managedDeviceInfo= */ true) {
      @Override
      public ImmutableSet<Device> getManagedDevices() {
        return ImmutableSet.copyOf(preCreatedSubDevices);
      }
    };
  }

  private FusionTestbedDeviceFactory() {}
}
