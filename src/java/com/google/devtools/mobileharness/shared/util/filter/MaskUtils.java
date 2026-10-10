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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.protobuf.util.FieldMaskUtil.trim;

import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroup;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask.DimensionsMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.protobuf.FieldMask;
import java.util.List;
import java.util.Optional;

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
        DeviceInfoTrimmer.of(mask.getDeviceInfoMask())
            .trimGroupedDevices(resultBuilder.getDeviceViewBuilder().getGroupedDevicesBuilder());
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
      DeviceInfoTrimmer trimmer = DeviceInfoTrimmer.of(mask.getDeviceInfoMask());
      labViewBuilder
          .getLabDataBuilderList()
          .forEach(
              labDataBuilder -> {
                // If LabData doesn't have DeviceList, it means the LabData doesn't have any
                // devices and we don't need to trim it.
                if (labDataBuilder.hasDeviceList()) {
                  labDataBuilder.setDeviceList(
                      trimmer.trimDeviceList(labDataBuilder.getDeviceList()));
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

  /**
   * A {@link DeviceInfoMask} prepared once per request: the dimension name matchers are built here
   * instead of for every device.
   */
  private static final class DeviceInfoTrimmer {

    private final FieldMask fieldMask;

    /** Empty when the corresponding {@code DimensionsMask} is absent or lists no names. */
    private final Optional<DimensionNameMatcher> supportedDimensions;

    private final Optional<DimensionNameMatcher> requiredDimensions;

    static DeviceInfoTrimmer of(DeviceInfoMask deviceInfoMask) {
      return new DeviceInfoTrimmer(
          deviceInfoMask.hasFieldMask() ? deviceInfoMask.getFieldMask() : null,
          matcherOf(deviceInfoMask.getSupportedDimensionsMask()),
          matcherOf(deviceInfoMask.getRequiredDimensionsMask()));
    }

    private DeviceInfoTrimmer(
        FieldMask fieldMask,
        Optional<DimensionNameMatcher> supportedDimensions,
        Optional<DimensionNameMatcher> requiredDimensions) {
      this.fieldMask = fieldMask;
      this.supportedDimensions = supportedDimensions;
      this.requiredDimensions = requiredDimensions;
    }

    /** An absent or empty list of names keeps every dimension, so it needs no matcher. */
    private static Optional<DimensionNameMatcher> matcherOf(DimensionsMask dimensionsMask) {
      return dimensionsMask.getDimensionNamesCount() == 0
          ? Optional.empty()
          : Optional.of(DimensionNameMatcher.of(dimensionsMask.getDimensionNamesList()));
    }

    DeviceList trimDeviceList(DeviceList deviceList) {
      if (fieldMask == null) {
        return deviceList;
      }
      if (fieldMask.getPathsList().isEmpty()) {
        return DeviceList.newBuilder()
            .setDeviceTotalCount(deviceList.getDeviceTotalCount())
            .build();
      }
      List<DeviceInfo> deviceInfos = deviceList.getDeviceInfoList();
      return DeviceList.newBuilder()
          .setDeviceTotalCount(deviceList.getDeviceTotalCount())
          .addAllDeviceInfo(
              deviceInfos.stream().map(this::trimDeviceInfo).collect(toImmutableList()))
          .build();
    }

    void trimGroupedDevices(GroupedDevices.Builder groupedDevicesBuilder) {
      if (groupedDevicesBuilder.hasDeviceList()) {
        groupedDevicesBuilder.setDeviceList(trimDeviceList(groupedDevicesBuilder.getDeviceList()));
      } else if (groupedDevicesBuilder.hasDeviceGroupResult()) {
        groupedDevicesBuilder.setDeviceGroupResult(
            trimDeviceGroupResult(groupedDevicesBuilder.getDeviceGroupResult()));
      }
    }

    private DeviceGroupResult trimDeviceGroupResult(DeviceGroupResult deviceGroupResult) {
      DeviceGroupResult.Builder builder = deviceGroupResult.toBuilder();
      builder.getDeviceGroupBuilderList().forEach(this::trimDeviceGroup);
      return builder.build();
    }

    private void trimDeviceGroup(DeviceGroup.Builder deviceGroupBuilder) {
      if (deviceGroupBuilder.hasGroupedDevices()) {
        trimGroupedDevices(deviceGroupBuilder.getGroupedDevicesBuilder());
      }
    }

    private DeviceInfo trimDeviceInfo(DeviceInfo deviceInfo) {
      if (fieldMask == null || fieldMask.getPathsList().isEmpty()) {
        return deviceInfo;
      }

      DeviceInfo.Builder deviceInfoBuilder = deviceInfo.toBuilder();
      if (supportedDimensions.isPresent()) {
        deviceInfoBuilder
            .getDeviceFeatureBuilder()
            .getCompositeDimensionBuilder()
            .clearSupportedDimension()
            .addAllSupportedDimension(
                supportedDimensions
                    .get()
                    .filter(
                        deviceInfo
                            .getDeviceFeature()
                            .getCompositeDimension()
                            .getSupportedDimensionList()));
      }
      if (requiredDimensions.isPresent()) {
        deviceInfoBuilder
            .getDeviceFeatureBuilder()
            .getCompositeDimensionBuilder()
            .clearRequiredDimension()
            .addAllRequiredDimension(
                requiredDimensions
                    .get()
                    .filter(
                        deviceInfo
                            .getDeviceFeature()
                            .getCompositeDimension()
                            .getRequiredDimensionList()));
      }

      return trim(fieldMask, deviceInfoBuilder.build());
    }
  }
}
