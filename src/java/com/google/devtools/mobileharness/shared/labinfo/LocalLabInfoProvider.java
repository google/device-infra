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
            .map(entry -> createDeviceInfo(entry.getKey(), entry.getValue(), deviceMask))
            .flatMap(Optional::stream)
            .collect(toImmutableList());

    // Creates LabView.
    return LabView.newBuilder()
        .setLabTotalCount(1)
        .addLabData(
            LabData.newBuilder()
                .setDeviceList(
                    DeviceList.newBuilder()
                        .setDeviceTotalCount(devices.size())
                        .addAllDeviceInfo(deviceInfos)))
        .build();
  }

  private Optional<DeviceInfo> createDeviceInfo(
      Device device, DeviceStatusInfo deviceStatusInfo, CompiledDeviceInfoMask deviceMask) {
    Optional<DeviceTempRequiredDimensions> tempDimensions =
        Optional.ofNullable(tempRequiredDimensionManager)
            .flatMap(
                manager ->
                    manager.getDimensions(
                        new DeviceKey(LabLocator.LOCALHOST.hostName(), device.getDeviceUuid())));
    return deviceMask
        .newMaskedDeviceInfoBuilder()
        .setMaskedDeviceLocator(
            device,
            Device::getDeviceUuid,
            currentDevice ->
                DeviceLocator.newBuilder().setId(currentDevice.getDeviceUuid()).build())
        .setMaskedDeviceStatus(
            deviceStatusInfo, statusInfo -> statusInfo.getDeviceStatusWithTimestamp().getStatus())
        .setMaskedDeviceCondition(tempDimensions, LocalLabInfoProvider::toDeviceCondition)
        .setMaskedDeviceFeature(
            device, currentDevice -> toDeviceFeature(currentDevice, tempDimensions))
        .build();
  }

  private static Optional<DeviceCondition> toDeviceCondition(
      Optional<DeviceTempRequiredDimensions> tempDimensions) {
    return tempDimensions.map(
        dimensions -> {
          DeviceCondition.Builder conditionBuilder = DeviceCondition.newBuilder();
          dimensions
              .dimensions()
              .forEach(
                  (name, value) ->
                      conditionBuilder.addTempDimension(
                          TempDimension.newBuilder()
                              .setDimension(
                                  DeviceDimension.newBuilder().setName(name).setValue(value))
                              .setExpireTimestampMs(dimensions.expireTime().toEpochMilli())
                              .setRequired(true)));
          return conditionBuilder.build();
        });
  }

  private static DeviceFeature toDeviceFeature(
      Device device, Optional<DeviceTempRequiredDimensions> tempDimensions) {
    DeviceFeature fullFeature = device.toFeature();
    return tempDimensions
        .map(
            dimensions -> {
              DeviceFeature.Builder featureBuilder = fullFeature.toBuilder();
              dimensions
                  .dimensions()
                  .forEach(
                      (name, value) ->
                          featureBuilder
                              .getCompositeDimensionBuilder()
                              .addRequiredDimension(
                                  DeviceDimension.newBuilder().setName(name).setValue(value)));
              return featureBuilder.build();
            })
        .orElse(fullFeature);
  }
}
