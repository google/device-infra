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

package com.google.devtools.mobileharness.infra.client.api.mode.ats;

import com.google.common.collect.ImmutableListMultimap;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.reserver.DeviceReserver;
import com.google.devtools.mobileharness.infra.client.api.util.dimension.DeviceTempRequiredDimensionManager;
import com.google.devtools.mobileharness.infra.client.api.util.dimension.DeviceTempRequiredDimensionManager.DeviceKey;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.time.Duration;
import javax.inject.Inject;
import javax.inject.Singleton;

/** ATS mode implementation of {@link DeviceReserver}. */
@Singleton
class AtsDeviceReserver extends DeviceReserver {

  private final DeviceTempRequiredDimensionManager deviceTempRequiredDimensionManager;

  @Inject
  AtsDeviceReserver(DeviceTempRequiredDimensionManager deviceTempRequiredDimensionManager) {
    this.deviceTempRequiredDimensionManager = deviceTempRequiredDimensionManager;
  }

  @Override
  public void addTempAllocationKeyToDevice(
      DeviceLocator deviceLocator,
      String allocationDimensionName,
      String allocationKey,
      Duration ttl) {
    deviceTempRequiredDimensionManager.addOrRemoveDimensions(
        new DeviceKey(deviceLocator.getLabLocator().getHostName(), deviceLocator.getSerial()),
        ImmutableListMultimap.of(allocationDimensionName, allocationKey),
        ttl);
  }
}
