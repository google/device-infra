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

package com.google.devtools.mobileharness.shared.labinfo;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.infra.controller.device.DeviceStatusInfo;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.Map;

/** {@link LabInfoProvider} which provides {@link LabView} from {@link LocalDeviceManager}. */
public class LocalLabInfoProvider implements LabInfoProvider {

  private final LocalDeviceManager localDeviceManager;

  public LocalLabInfoProvider(LocalDeviceManager localDeviceManager) {
    this.localDeviceManager = localDeviceManager;
  }

  @Override
  public LabView getLabInfos(Filter filter) {
    // Gets device information from device manager.
    Map<Device, DeviceStatusInfo> devices;
    try {
      devices = localDeviceManager.getAllDeviceStatus(false);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      devices = ImmutableMap.of();
    }

    // TODO: Supports Filter/LabInfo/LabLocator.

    // Creates DeviceInfo.
    ImmutableList<DeviceInfo> deviceInfos =
        devices.entrySet().stream()
            .map(
                entry -> {
                  Device device = entry.getKey();
                  DeviceStatus deviceStatus =
                      entry.getValue().getDeviceStatusWithTimestamp().getStatus();
                  return DeviceInfo.newBuilder()
                      .setDeviceLocator(DeviceLocator.newBuilder().setId(device.getDeviceUuid()))
                      .setDeviceStatus(deviceStatus)
                      .setDeviceFeature(device.toFeature())
                      .build();
                })
            .collect(toImmutableList());

    // Creates LabView.
    return LabView.newBuilder()
        .setLabTotalCount(1)
        .addLabData(
            LabData.newBuilder()
                .setDeviceList(
                    DeviceList.newBuilder()
                        .setDeviceTotalCount(deviceInfos.size())
                        .addAllDeviceInfo(deviceInfos)))
        .build();
  }
}
