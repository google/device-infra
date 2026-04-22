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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.infra.controller.device.DeviceStatusInfo;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.shared.labinfo.DeviceTempRequiredDimensionManager.DeviceKey;
import com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** {@link LabInfoProvider} which provides {@link LabView} from {@link LocalDeviceManager}. */
public class LocalLabInfoProvider implements LabInfoProvider {

  private final ListenableFuture<LocalDeviceManager> localDeviceManager;
  @Nullable private final DeviceTempRequiredDimensionManager tempRequiredDimensionManager;

  @Inject
  LocalLabInfoProvider(
      ListenableFuture<LocalDeviceManager> localDeviceManager,
      @Nullable DeviceTempRequiredDimensionManager tempRequiredDimensionManager) {
    this.localDeviceManager = localDeviceManager;
    this.tempRequiredDimensionManager = tempRequiredDimensionManager;
  }

  @Override
  public LabView getLabInfos(Filter filter) {
    // Gets device information from device manager.
    Map<Device, DeviceStatusInfo> devices;
    try {
      devices = MoreFutures.getUnchecked(localDeviceManager).getAllDeviceStatus(false);
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
                  DeviceInfo.Builder builder =
                      DeviceInfo.newBuilder()
                          .setDeviceLocator(
                              DeviceLocator.newBuilder().setId(device.getDeviceUuid()))
                          .setDeviceStatus(deviceStatus);
                  DeviceFeature.Builder featureBuilder = device.toFeature().toBuilder();

                  if (tempRequiredDimensionManager != null) {
                    DeviceKey deviceKey =
                        new DeviceKey(LabLocator.LOCALHOST.hostName(), device.getDeviceUuid());
                    tempRequiredDimensionManager
                        .getDimensions(deviceKey)
                        .ifPresent(
                            dimensions -> {
                              DeviceCondition.Builder conditionBuilder =
                                  DeviceCondition.newBuilder();
                              dimensions
                                  .dimensions()
                                  .forEach(
                                      (name, value) -> {
                                        conditionBuilder.addTempDimension(
                                            TempDimension.newBuilder()
                                                .setDimension(
                                                    DeviceDimension.newBuilder()
                                                        .setName(name)
                                                        .setValue(value))
                                                .setExpireTimestampMs(
                                                    dimensions.expireTime().toEpochMilli())
                                                .setRequired(true));

                                        featureBuilder
                                            .getCompositeDimensionBuilder()
                                            .addRequiredDimension(
                                                DeviceDimension.newBuilder()
                                                    .setName(name)
                                                    .setValue(value));
                                      });
                              builder.setDeviceCondition(conditionBuilder.build());
                            });
                  }

                  return builder.setDeviceFeature(featureBuilder.build()).build();
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
