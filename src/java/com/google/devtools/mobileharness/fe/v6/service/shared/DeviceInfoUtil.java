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

package com.google.devtools.mobileharness.fe.v6.service.shared;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import java.util.stream.Stream;

/** Utility for extracting information from {@link DeviceInfo}. */
public final class DeviceInfoUtil {

  private DeviceInfoUtil() {}

  /**
   * Extracts device dimensions from {@link DeviceInfo} and converts them to an {@link
   * ImmutableMap}.
   *
   * <p>This method concatenates both supported and required dimensions. If a dimension key exists
   * in both lists, the value from the supported dimension list is kept.
   */
  public static ImmutableMap<String, String> getDimensions(DeviceInfo deviceInfo) {
    return Stream.concat(
            deviceInfo
                .getDeviceFeature()
                .getCompositeDimension()
                .getSupportedDimensionList()
                .stream(),
            deviceInfo
                .getDeviceFeature()
                .getCompositeDimension()
                .getRequiredDimensionList()
                .stream())
        .collect(
            toImmutableMap(DeviceDimension::getName, DeviceDimension::getValue, (v1, v2) -> v2));
  }
}
