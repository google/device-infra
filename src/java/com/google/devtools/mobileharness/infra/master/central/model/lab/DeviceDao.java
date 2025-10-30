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

package com.google.devtools.mobileharness.infra.master.central.model.lab;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceScheduleUnit;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.infra.master.central.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.infra.master.central.proto.Device.DeviceProfile;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
import java.time.Instant;

/** Data access object of a device. */
@AutoValue
public abstract class DeviceDao {

  public static DeviceDao create(
      DeviceLocator locator, DeviceProfile profile, DeviceCondition condition) {
    return new AutoValue_DeviceDao(locator, profile, condition);
  }

  public abstract DeviceLocator locator();

  public abstract DeviceProfile profile();

  public abstract DeviceCondition condition();

  public DeviceStatus getStatus() {
    return DeviceConditionUtil.summarizeStatus(condition());
  }

  /**
   * Merges the status from MH lab and Device Master. It's for client and FE to query device status.
   */
  public DeviceStatus getMergedMnmDeviceStatusIfAny() {
    return DeviceConditionUtil.mergeMnmDeviceStatusIfAny(
        condition(),
        DeviceConditionUtil.isSharedPoolDevice(this) ? DeviceStatus.LAMEDUCK : DeviceStatus.IDLE);
  }

  public Instant getStatusModifyTime() {
    return DeviceConditionUtil.summarizeStatusModifyTime(condition());
  }

  public DeviceScheduleUnit toScheduleUnit() {
    return new DeviceScheduleUnit(locator()).addFeature(profile().getFeature());
  }

  public DeviceInfo toDeviceInfo() {
    DeviceFeature deviceFeature = profile().getFeature();
    DeviceCondition deviceCondition = condition();
    DeviceInfo.Builder deviceInfoBuilder =
        DeviceInfo.newBuilder()
            .setId(locator().id())
            .setStatus(
                DeviceConditionUtil.mergeMnmDeviceStatusIfAny(deviceCondition, DeviceStatus.IDLE)
                    .name())
            .addAllOwner(deviceFeature.getOwnerList())
            .addAllExecutor(deviceFeature.getExecutorList())
            .addAllType(deviceFeature.getTypeList())
            .addAllDriver(deviceFeature.getDriverList())
            .addAllDecorator(deviceFeature.getDecoratorList());

    deviceInfoBuilder.addAllDimension(
        deviceFeature.getCompositeDimension().getSupportedDimensionList().stream()
            // Ignores the subdeviced_dimension since is too large and we don't use is for
            // allocation.
            .filter(dimension -> !dimension.getName().equals("subdevice_dimensions"))
            .map(
                dimension ->
                    Dimension.newBuilder()
                        .setName(dimension.getName())
                        .setValue(dimension.getValue())
                        .build())
            .collect(toImmutableList()));
    deviceInfoBuilder.addAllDimension(
        deviceFeature.getCompositeDimension().getRequiredDimensionList().stream()
            .map(
                dimension ->
                    Dimension.newBuilder()
                        .setRequired(true)
                        .setName(dimension.getName())
                        .setValue(dimension.getValue())
                        .build())
            .collect(toImmutableList()));
    deviceInfoBuilder.addAllDimension(
        deviceCondition.getTempDimensionList().stream()
            .map(
                dimension ->
                    Dimension.newBuilder()
                        .setRequired(dimension.getRequired())
                        .setName(dimension.getDimension().getName())
                        .setValue(dimension.getDimension().getValue())
                        .build())
            .collect(toImmutableList()));
    return deviceInfoBuilder.build();
  }
}
