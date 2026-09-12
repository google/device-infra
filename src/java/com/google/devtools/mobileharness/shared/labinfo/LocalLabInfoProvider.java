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
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.infra.controller.device.DeviceStatusInfo;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.shared.labinfo.DeviceTempRequiredDimensionManager.DeviceKey;
import com.google.devtools.mobileharness.shared.labinfo.DeviceTempRequiredDimensionManager.DeviceTempRequiredDimensions;
import com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures;
import com.google.devtools.mobileharness.shared.util.filter.CompiledDeviceInfoMask;
import com.google.devtools.mobileharness.shared.util.filter.MaskUtils;
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
    return getLabInfos(filter, Mask.getDefaultInstance());
  }

  @Override
  public LabView getLabInfos(Filter filter, Mask mask) {
    CompiledDeviceInfoMask deviceMask = CompiledDeviceInfoMask.of(mask.getDeviceInfoMask());
    if (!deviceMask.keepsDeviceInfo() && !MaskUtils.keepsLabInfo(mask.getLabInfoMask())) {
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

    if (!deviceMask.keepsDeviceInfo()) {
      return LabView.newBuilder()
          .setLabTotalCount(1)
          .addLabData(
              LabData.newBuilder()
                  .setDeviceList(DeviceList.newBuilder().setDeviceTotalCount(devices.size())))
          .build();
    }

    // Creates DeviceInfo.
    ImmutableList<DeviceInfo> deviceInfos =
        devices.entrySet().stream()
            .map(
                entry -> {
                  Device device = entry.getKey();
                  DeviceKey devKey =
                      new DeviceKey(LabLocator.LOCALHOST.hostName(), device.getDeviceUuid());
                  DeviceInfo.Builder builder =
                      DeviceInfo.newBuilder()
                          .setDeviceLocator(
                              DeviceLocator.newBuilder().setId(device.getDeviceUuid()).build())
                          .setDeviceStatus(
                              entry.getValue().getDeviceStatusWithTimestamp().getStatus());
                  if (deviceMask.keepsDeviceCondition() && tempRequiredDimensionManager != null) {
                    Optional<DeviceTempRequiredDimensions> dimensions =
                        tempRequiredDimensionManager.getDimensions(devKey);
                    if (dimensions.isPresent()) {
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
                      builder.setDeviceCondition(conditionBuilder.build());
                    }
                  }
                  if (deviceMask.keepsDeviceFeature()) {
                    DeviceFeature fullFeature = device.toFeature();
                    if (tempRequiredDimensionManager != null) {
                      Optional<DeviceTempRequiredDimensions> tempDimensions =
                          tempRequiredDimensionManager.getDimensions(devKey);
                      if (tempDimensions.isPresent()) {
                        DeviceFeature.Builder fb = fullFeature.toBuilder();
                        tempDimensions
                            .get()
                            .dimensions()
                            .forEach(
                                (name, value) ->
                                    fb.getCompositeDimensionBuilder()
                                        .addRequiredDimension(
                                            DeviceDimension.newBuilder()
                                                .setName(name)
                                                .setValue(value)));
                        fullFeature = fb.build();
                      }
                    }
                    builder.setDeviceFeature(deviceMask.projectDeviceFeature(fullFeature));
                  }
                  return deviceMask.trimFields(builder.build());
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
