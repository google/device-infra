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

package com.google.devtools.mobileharness.infra.ats.util;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.ats.server.proto.ConditionedDeviceConfigProto.ConditionedDeviceConfig;
import com.google.devtools.mobileharness.infra.ats.server.proto.ConditionedDeviceConfigProto.ConditionedDeviceConfigs;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.regex.Pattern;

/** Utils related to device config. */
final class DeviceConfigUtil {

  /** Filters the conditioned device configs by the device. */
  public static ImmutableList<ConditionedDeviceConfig> filterConditionedDeviceConfigsByDevice(
      ConditionedDeviceConfigs conditionedDeviceConfigs, Device device) {
    return conditionedDeviceConfigs.getConditionedDeviceConfigsList().stream()
        .filter(config -> matchesConditionedDeviceConfig(config, device))
        .collect(toImmutableList());
  }

  private static boolean matchesConditionedDeviceConfig(
      ConditionedDeviceConfig conditionedDeviceConfig, Device device) {
    return conditionedDeviceConfig.getConditionList().stream()
        .allMatch(
            condition -> {
              String conditionKey = condition.getKey();
              String conditionValueRegex = condition.getValueRegex();
              if (conditionKey.equals("type_device")) {
                return device.getDeviceTypes().stream()
                    .anyMatch(deviceType -> Pattern.matches(conditionValueRegex, deviceType));
              } else if (conditionKey.startsWith("dimension_")) {
                return device.getDimensions().stream()
                    .anyMatch(
                        dimension ->
                            dimension
                                    .getName()
                                    .equals(conditionKey.substring("dimension_".length()))
                                && Pattern.matches(conditionValueRegex, dimension.getValue()));
              }
              return false;
            });
  }

  private DeviceConfigUtil() {}
}
