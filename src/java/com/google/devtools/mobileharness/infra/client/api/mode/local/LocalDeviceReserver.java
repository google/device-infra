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

package com.google.devtools.mobileharness.infra.client.api.mode.local;

import com.google.common.collect.ImmutableListMultimap;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.reserver.DeviceReserver;
import com.google.devtools.mobileharness.infra.client.api.util.dimension.DeviceTempRequiredDimensionManager;
import com.google.devtools.mobileharness.infra.client.api.util.dimension.DeviceTempRequiredDimensionManager.DeviceKey;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.time.Duration;
import javax.inject.Inject;

/** Local mode implementation of {@link DeviceReserver}. */
public class LocalDeviceReserver implements DeviceReserver {

  private final DeviceTempRequiredDimensionManager deviceTempRequiredDimensionManager;

  @Inject
  LocalDeviceReserver(DeviceTempRequiredDimensionManager deviceTempRequiredDimensionManager) {
    this.deviceTempRequiredDimensionManager = deviceTempRequiredDimensionManager;
  }

  @Override
  public void addTempAllocationKeyToDevice(
      DeviceLocator deviceLocator,
      String allocationDimensionName,
      String allocationKey,
      Duration ttl) {
    deviceTempRequiredDimensionManager.addOrRemoveDimensions(
        // Uses the same host name in LocalDeviceManagerSchedulerSyncer.
        new DeviceKey(LabLocator.LOCALHOST.hostName(), deviceLocator.getSerial()),
        ImmutableListMultimap.of(allocationDimensionName, allocationKey),
        ttl);
  }
}
