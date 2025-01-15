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

import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
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
   * <p>If the LabInfoMask exists but is empty, LabInfo won't be in result.
   *
   * <p>If the LabInfoMask doesn't exist, LabInfo won't be trimmed.
   *
   * <p>If the DeviceInfoMask exists, DeviceInfo will be trimmed based on the field mask.
   *
   * <p>If the DeviceInfoMask exists but is empty, DeviceInfo won't be in result.
   *
   * <p>If the DeviceInfoMask doesn't exist, DeviceInfo won't be trimmed.
   */
  public static LabQueryResult trimLabQueryResult(LabQueryResult result, Mask mask) {
    LabQueryResult.Builder builder = result.toBuilder();
    if (mask.hasLabInfoMask()) {
      builder
          .getLabViewBuilder()
          .getLabDataBuilderList()
          .forEach(labDataBuilder -> trimLabInfo(labDataBuilder, mask));
    }
    if (mask.hasDeviceInfoMask()) {
      builder
          .getLabViewBuilder()
          .getLabDataBuilderList()
          .forEach(
              labDataBuilder ->
                  labDataBuilder.setDeviceList(
                      trimDeviceInfo(labDataBuilder.getDeviceList(), mask)));
    }
    return builder.build();
  }

  private static void trimLabInfo(LabData.Builder labDataBuilder, Mask mask) {
    FieldMask fieldMask = mask.getLabInfoMask().getFieldMask();
    if (fieldMask.getPathsList().isEmpty()) {
      labDataBuilder.clearLabInfo();
    } else {
      labDataBuilder.setLabInfo(trim(fieldMask, labDataBuilder.getLabInfo()));
    }
  }

  private static DeviceList trimDeviceInfo(DeviceList deviceList, Mask mask) {
    FieldMask fieldMask = mask.getDeviceInfoMask().getFieldMask();
    if (fieldMask.getPathsList().isEmpty()) {
      return DeviceList.newBuilder().setDeviceTotalCount(deviceList.getDeviceTotalCount()).build();
    }
    List<DeviceInfo> deviceInfos = deviceList.getDeviceInfoList();
    return DeviceList.newBuilder()
        .setDeviceTotalCount(deviceList.getDeviceTotalCount())
        .addAllDeviceInfo(
            deviceInfos.stream()
                .map(deviceInfo -> trim(fieldMask, deviceInfo))
                .collect(toImmutableList()))
        .build();
  }
}
