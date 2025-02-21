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

package com.google.devtools.mobileharness.shared.util.filter;

import static com.google.common.base.Ascii.toLowerCase;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.protobuf.util.FieldMaskUtil.trim;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroup;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.protobuf.FieldMask;
import java.util.List;

/** Utility class to trim the {@link LabQueryResult} based on the {@link Mask}. */
public final class MaskUtils {

  private MaskUtils() {}

  /**
   * Trims the {@link LabQueryResult} based on the {@link Mask}.
   *
   * <p>If the LabInfoMask exists, LabInfo will be trimmed based on the field mask.
   *
   * <p>If the LabInfoMask exists but field_mask is not set, returns all fields of LabInfo.
   *
   * <p>If the LabInfoMask exists but field_mask is empty, LabInfo won't be in result.
   *
   * <p>If the LabInfoMask doesn't exist, LabInfo won't be trimmed.
   *
   * <p>If the DeviceInfoMask exists, DeviceInfo will be trimmed based on the field mask.
   *
   * <p>If the DeviceInfoMask exists but field_mask is not set, returns all fields of DeviceInfo.
   *
   * <p>If the DeviceInfoMask exists but field_mask is empty, DeviceInfo won't be in result.
   *
   * <p>If the DeviceInfoMask doesn't exist, DeviceInfo won't be trimmed.
   *
   * <p>BE CAREFUL. The {@link DeviceGroupResult} inside {@link LabQueryResult} should never have
   * cycles. Otherwise, the method might run into indefinite loop.
   */
  public static void trimLabQueryResult(LabQueryResult.Builder resultBuilder, Mask mask) {
    if (resultBuilder.hasLabView()) {
      trimLabView(resultBuilder.getLabViewBuilder(), mask);
    } else if (resultBuilder.hasDeviceView()) {
      // If DeviceInfoMask is present, trims the DeviceInfo in GroupedDevices. Otherwise, returns
      // all fields of DeviceInfo.
      if (mask.hasDeviceInfoMask()) {
        trimGroupedDevices(
            resultBuilder.getDeviceViewBuilder().getGroupedDevicesBuilder(),
            mask.getDeviceInfoMask());
      }
    }
  }

  private static void trimLabView(LabView.Builder labViewBuilder, Mask mask) {
    // If LabInfoMask is present, trims the LabInfo in LabData. Otherwise, returns all fields of
    // LabInfo.
    if (mask.hasLabInfoMask()) {
      labViewBuilder
          .getLabDataBuilderList()
          .forEach(labDataBuilder -> trimLabData(labDataBuilder, mask.getLabInfoMask()));
    }
    // If DeviceInfoMask is present, trims the DeviceInfo in DeviceList. Otherwise, returns all
    // fields of DeviceInfo.
    if (mask.hasDeviceInfoMask()) {
      labViewBuilder
          .getLabDataBuilderList()
          .forEach(
              labDataBuilder -> {
                // If LabData doesn't have DeviceList, it means the LabData doesn't have any
                // devices and we don't need to trim it.
                if (labDataBuilder.hasDeviceList()) {
                  labDataBuilder.setDeviceList(
                      trimDeviceList(labDataBuilder.getDeviceList(), mask.getDeviceInfoMask()));
                }
              });
    }
  }

  private static void trimLabData(LabData.Builder labDataBuilder, LabInfoMask labInfoMask) {
    if (!labInfoMask.hasFieldMask()) {
      return;
    }

    FieldMask fieldMask = labInfoMask.getFieldMask();
    if (fieldMask.getPathsList().isEmpty()) {
      labDataBuilder.clearLabInfo();
    } else {
      labDataBuilder.setLabInfo(trim(fieldMask, labDataBuilder.getLabInfo()));
    }
  }

  private static DeviceList trimDeviceList(DeviceList deviceList, DeviceInfoMask deviceInfoMask) {
    if (!deviceInfoMask.hasFieldMask()) {
      return deviceList;
    }

    FieldMask fieldMask = deviceInfoMask.getFieldMask();
    if (fieldMask.getPathsList().isEmpty()) {
      return DeviceList.newBuilder().setDeviceTotalCount(deviceList.getDeviceTotalCount()).build();
    }
    List<DeviceInfo> deviceInfos = deviceList.getDeviceInfoList();
    return DeviceList.newBuilder()
        .setDeviceTotalCount(deviceList.getDeviceTotalCount())
        .addAllDeviceInfo(
            deviceInfos.stream()
                .map(deviceInfo -> trimDeviceInfo(deviceInfo, deviceInfoMask))
                .collect(toImmutableList()))
        .build();
  }

  private static void trimGroupedDevices(
      GroupedDevices.Builder groupedDevicesBuilder, DeviceInfoMask deviceInfoMask) {
    if (groupedDevicesBuilder.hasDeviceList()) {
      groupedDevicesBuilder.setDeviceList(
          trimDeviceList(groupedDevicesBuilder.getDeviceList(), deviceInfoMask));
    } else if (groupedDevicesBuilder.hasDeviceGroupResult()) {
      groupedDevicesBuilder.setDeviceGroupResult(
          trimDeviceGroupResult(groupedDevicesBuilder.getDeviceGroupResult(), deviceInfoMask));
    }
  }

  private static DeviceGroupResult trimDeviceGroupResult(
      DeviceGroupResult deviceGroupResult, DeviceInfoMask deviceInfoMask) {
    DeviceGroupResult.Builder builder = deviceGroupResult.toBuilder();
    builder
        .getDeviceGroupBuilderList()
        .forEach(deviceGroupBuilder -> trimDeviceGroup(deviceGroupBuilder, deviceInfoMask));
    return builder.build();
  }

  private static void trimDeviceGroup(
      DeviceGroup.Builder deviceGroupBuilder, DeviceInfoMask deviceInfoMask) {
    if (deviceGroupBuilder.hasGroupedDevices()) {
      trimGroupedDevices(deviceGroupBuilder.getGroupedDevicesBuilder(), deviceInfoMask);
    }
  }

  private static DeviceInfo trimDeviceInfo(DeviceInfo deviceInfo, DeviceInfoMask deviceInfoMask) {
    FieldMask fieldMask = deviceInfoMask.getFieldMask();
    if (fieldMask.getPathsList().isEmpty()) {
      return deviceInfo;
    }

    DeviceInfo.Builder deviceInfoBuilder = deviceInfo.toBuilder();
    if (deviceInfoMask.hasSupportedDimensionsMask()) {
      deviceInfoBuilder
          .getDeviceFeatureBuilder()
          .getCompositeDimensionBuilder()
          .clearSupportedDimension()
          .addAllSupportedDimension(
              trimDimension(
                  deviceInfo.getDeviceFeature().getCompositeDimension().getSupportedDimensionList(),
                  deviceInfoMask.getSupportedDimensionsMask().getDimensionNamesList()));
    }
    if (deviceInfoMask.hasRequiredDimensionsMask()) {
      deviceInfoBuilder
          .getDeviceFeatureBuilder()
          .getCompositeDimensionBuilder()
          .clearRequiredDimension()
          .addAllRequiredDimension(
              trimDimension(
                  deviceInfo.getDeviceFeature().getCompositeDimension().getRequiredDimensionList(),
                  deviceInfoMask.getRequiredDimensionsMask().getDimensionNamesList()));
    }

    return trim(
        fieldMask,
        trimDeviceDimension(
            deviceInfoBuilder.build(), deviceInfoMask.getSelectedDimensionNamesList()));
  }

  private static DeviceInfo trimDeviceDimension(
      DeviceInfo deviceInfo, List<String> dimensionNames) {
    if (dimensionNames.isEmpty()) {
      return deviceInfo;
    }

    ImmutableSet<String> dimensionNamesLowerCase =
        dimensionNames.stream().map(String::toLowerCase).collect(toImmutableSet());

    DeviceInfo.Builder deviceInfoBuilder = deviceInfo.toBuilder();

    deviceInfoBuilder
        .getDeviceFeatureBuilder()
        .getCompositeDimensionBuilder()
        .clearSupportedDimension()
        .addAllSupportedDimension(
            deviceInfo
                .getDeviceFeature()
                .getCompositeDimension()
                .getSupportedDimensionList()
                .stream()
                .filter(
                    dimension -> dimensionNamesLowerCase.contains(toLowerCase(dimension.getName())))
                .collect(toImmutableList()));

    deviceInfoBuilder
        .getDeviceFeatureBuilder()
        .getCompositeDimensionBuilder()
        .clearRequiredDimension()
        .addAllRequiredDimension(
            deviceInfo
                .getDeviceFeature()
                .getCompositeDimension()
                .getRequiredDimensionList()
                .stream()
                .filter(
                    dimension -> dimensionNamesLowerCase.contains(toLowerCase(dimension.getName())))
                .collect(toImmutableList()));

    return deviceInfoBuilder.build();
  }

  private static List<DeviceDimension> trimDimension(
      List<DeviceDimension> dimensions, List<String> dimensionNames) {
    if (dimensionNames.isEmpty()) {
      return dimensions;
    }

    ImmutableSet<String> dimensionNamesLowerCase =
        dimensionNames.stream().map(String::toLowerCase).collect(toImmutableSet());

    return dimensions.stream()
        .filter(dimension -> dimensionNamesLowerCase.contains(toLowerCase(dimension.getName())))
        .collect(toImmutableList());
  }
}
