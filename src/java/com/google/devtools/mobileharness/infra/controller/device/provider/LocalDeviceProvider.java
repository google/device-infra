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

package com.google.devtools.mobileharness.infra.controller.device.provider;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.infra.controller.device.DeviceHelperFactory;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.List;
import javax.inject.Inject;

/** Provides devices from the LocalDeviceManager. */
public class LocalDeviceProvider implements DeviceProvider {
  private final DeviceHelperFactory deviceHelperFactory;

  @Inject
  LocalDeviceProvider(DeviceHelperFactory deviceHelperFactory) {
    this.deviceHelperFactory = deviceHelperFactory;
  }

  @Override
  public ImmutableList<Device> getDevices(
      List<String> deviceIds, List<DeviceFeature> deviceFeatures) throws MobileHarnessException {
    ImmutableList.Builder<Device> devices = ImmutableList.builder();
    for (String deviceId : deviceIds) {
      devices.add(deviceHelperFactory.getDeviceHelper(deviceId));
    }
    return devices.build();
  }
}
