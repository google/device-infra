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

package com.google.wireless.qa.mobileharness.shared.util;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
import java.util.stream.Collectors;

/** Util for creating {@link DeviceInfo}s. */
public class DeviceInfoUtil {

  private DeviceInfoUtil() {}

  public static DeviceInfo getDeviceInfoForCurrentTest(Device device, TestInfo testInfo) {
    return DeviceInfo.newBuilder()
        .setId(device.getDeviceId())
        .setStatus("busy")
        .addAllOwner(device.getOwners())
        .addAllExecutor(device.getExecutors())
        .addAllType(device.getDeviceTypes())
        .addAllDriver(device.getDriverTypes())
        .addAllDecorator(device.getDecoratorTypes())
        .addAllDimension(
            device.getDimensions().stream()
                .map(
                    dimension ->
                        Dimension.newBuilder()
                            .setRequired(false)
                            .setName(dimension.getName())
                            .setValue(dimension.getValue())
                            .build())
                .collect(Collectors.toList()))
        .addAllDimension(
            device.getRequiredDimensions().stream()
                .map(
                    dimension ->
                        Dimension.newBuilder()
                            .setRequired(true)
                            .setName(dimension.getName())
                            .setValue(dimension.getValue())
                            .build())
                .collect(Collectors.toList()))
        .setJobId(testInfo.jobInfo().locator().getId())
        .setJobName(testInfo.jobInfo().locator().getName())
        .setTestId(testInfo.locator().getId())
        .setTestName(testInfo.locator().getName())
        .build();
  }

  public static DeviceInfo getDeviceInfoForCurrentTest(
      String deviceId, DeviceFeature deviceFeature, TestInfo testInfo) {
    return DeviceInfo.newBuilder()
        .setId(deviceId)
        .setStatus("busy")
        .addAllOwner(deviceFeature.getOwnerList())
        .addAllExecutor(deviceFeature.getExecutorList())
        .addAllType(deviceFeature.getTypeList())
        .addAllDriver(deviceFeature.getDriverList())
        .addAllDecorator(deviceFeature.getDecoratorList())
        .addAllDimension(
            deviceFeature.getCompositeDimension().getSupportedDimensionList().stream()
                .map(
                    dimension ->
                        Dimension.newBuilder()
                            .setRequired(false)
                            .setName(dimension.getName())
                            .setValue(dimension.getValue())
                            .build())
                .collect(Collectors.toList()))
        .addAllDimension(
            deviceFeature.getCompositeDimension().getRequiredDimensionList().stream()
                .map(
                    dimension ->
                        Dimension.newBuilder()
                            .setRequired(true)
                            .setName(dimension.getName())
                            .setValue(dimension.getValue())
                            .build())
                .collect(Collectors.toList()))
        .setJobId(testInfo.jobInfo().locator().getId())
        .setJobName(testInfo.jobInfo().locator().getName())
        .setTestId(testInfo.locator().getId())
        .setTestName(testInfo.locator().getName())
        .build();
  }

  public static ImmutableSetMultimap<String, String> getDimensions(DeviceInfo deviceInfo) {
    return deviceInfo.getDimensionList().stream()
        .collect(
            ImmutableSetMultimap.toImmutableSetMultimap(Dimension::getName, Dimension::getValue));
  }
}
