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

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.infra.controller.device.DeviceStatusInfo;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.shared.labinfo.DeviceTempRequiredDimensionManager.DeviceKey;
import com.google.devtools.mobileharness.shared.labinfo.DeviceTempRequiredDimensionManager.DeviceTempRequiredDimensions;
import com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures;
import com.google.devtools.mobileharness.shared.util.filter.CompiledDeviceInfoMask;
import com.google.devtools.mobileharness.shared.util.filter.CompiledLabInfoMask;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.Map;
import java.util.Optional;
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
    return getLabInfos(filter, CompiledLabInfoMask.retainAll(), CompiledDeviceInfoMask.retainAll());
  }

  @Override
  public LabView getLabInfos(
      Filter filter, CompiledLabInfoMask labMask, CompiledDeviceInfoMask deviceMask) {
    if (!deviceMask.keepsDeviceInfo() && !labMask.keepsLabInfo()) {
      return LabView.getDefaultInstance();
    }
    // Gets device information from device manager.
    Map<Device, DeviceStatusInfo> devices;
    try {
      devices = MoreFutures.getUnchecked(localDeviceManager).getAllDeviceStatus(false);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      devices = ImmutableMap.of();
    }

    DeviceList.Builder deviceList = DeviceList.newBuilder().setDeviceTotalCount(devices.size());
    devices
        .entrySet()
        .forEach(
            entry -> {
              Device device = entry.getKey();
              DeviceKey devKey =
                  new DeviceKey(LabLocator.LOCALHOST.hostName(), device.getDeviceUuid());
              deviceMask
                  .newMaskedDeviceInfoBuilder()
                  .setFields(
                      entry,
                      (deviceInfoBuilder, deviceEntry) ->
                          deviceInfoBuilder
                              .setDeviceLocator(
                                  DeviceLocator.newBuilder()
                                      .setId(deviceEntry.getKey().getDeviceUuid())
                                      .build())
                              .setDeviceStatus(
                                  deviceEntry
                                      .getValue()
                                      .getDeviceStatusWithTimestamp()
                                      .getStatus()))
                  .setMaskedDeviceCondition(
                      devKey,
                      key -> {
                        if (tempRequiredDimensionManager == null) {
                          return null;
                        }
                        Optional<DeviceTempRequiredDimensions> dimensions =
                            tempRequiredDimensionManager.getDimensions(key);
                        if (dimensions.isEmpty()) {
                          return null;
                        }
                        DeviceCondition.Builder conditionBuilder = DeviceCondition.newBuilder();
                        dimensions
                            .get()
                            .dimensions()
                            .forEach(
                                (name, value) ->
                                    conditionBuilder.addTempDimension(
                                        TempDimension.newBuilder()
                                            .setDimension(
                                                DeviceDimension.newBuilder()
                                                    .setName(name)
                                                    .setValue(value))
                                            .setExpireTimestampMs(
                                                dimensions.get().expireTime().toEpochMilli())
                                            .setRequired(true)));
                        return conditionBuilder.build();
                      })
                  .setMaskedDeviceFeature(
                      device,
                      currentDevice -> {
                        DeviceFeature fullFeature = currentDevice.toFeature();
                        if (tempRequiredDimensionManager == null) {
                          return fullFeature;
                        }
                        Optional<DeviceTempRequiredDimensions> tempDimensions =
                            tempRequiredDimensionManager.getDimensions(devKey);
                        if (tempDimensions.isEmpty()) {
                          return fullFeature;
                        }
                        DeviceFeature.Builder featureBuilder = fullFeature.toBuilder();
                        tempDimensions
                            .get()
                            .dimensions()
                            .forEach(
                                (name, value) ->
                                    featureBuilder
                                        .getCompositeDimensionBuilder()
                                        .addRequiredDimension(
                                            DeviceDimension.newBuilder()
                                                .setName(name)
                                                .setValue(value)));
                        return featureBuilder.build();
                      })
                  .build()
                  .ifPresent(deviceList::addDeviceInfo);
            });

    // Creates LabView.
    return LabView.newBuilder()
        .setLabTotalCount(1)
        .addLabData(LabData.newBuilder().setDeviceList(deviceList))
        .build();
  }
}
