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

package com.google.devtools.mobileharness.infra.controller.device.util;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.BaseEncoding;
import com.google.devtools.mobileharness.api.deviceconfig.proto.ConditionedDeviceConfigProto.ConditionedDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.ConditionedDeviceConfigProto.ConditionedDeviceConfigs;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Utils related to conditioned device config. */
final class ConditionedDeviceConfigUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CONDITIONED_DEVICE_CONFIGS_DIMENSION_KEY =
      "conditioned_device_configs";

  /** Get a list of adb commands for the current device to run by the conditioned device configs. */
  public static ImmutableList<String> getBeforeFinishSetupAdbCommandsByDevice(
      ConditionedDeviceConfigs conditionedDeviceConfigs, Device device) {
    return conditionedDeviceConfigs.getConditionedDeviceConfigsList().stream()
        .filter(config -> matchesConditionedDeviceConfig(config, device))
        .flatMap(config -> config.getBeforeFinishSetupAdbCommandsList().stream())
        .collect(toImmutableList());
  }

  /** Returns whether the current device matches all conditions in the conditioned device config. */
  public static boolean matchesConditionedDeviceConfig(
      ConditionedDeviceConfig conditionedDeviceConfig, Device device) {
    return conditionedDeviceConfig.getConditionsList().stream()
        .allMatch(
            condition -> {
              String conditionKey = condition.getKey();
              Pattern conditionValuePattern;
              try {
                conditionValuePattern = Pattern.compile(condition.getValueRegex());
              } catch (PatternSyntaxException e) {
                logger.atWarning().withCause(e).log(
                    "Invalid condition value regex [%s]", condition.getValueRegex());
                return false;
              }

              if (conditionKey.equals("type_device")) {
                return device.getDeviceTypes().stream()
                    .anyMatch(deviceType -> conditionValuePattern.matcher(deviceType).matches());
              }
              if (conditionKey.startsWith("dimension_")) {
                return device.getDimensions().stream()
                    .anyMatch(
                        dimension ->
                            dimension
                                    .getName()
                                    .equals(conditionKey.substring("dimension_".length()))
                                && conditionValuePattern.matcher(dimension.getValue()).matches());
              }
              return false;
            });
  }

  /**
   * Returns the conditioned device configs from the device dimensions.
   *
   * <ol>
   *   <li>The conditioned device configs are stored in the device dimensions with the key
   *       "conditioned_device_configs".
   *   <li>The value is a base64 encoded string of the ConditionedDeviceConfigs proto.
   * </ol>
   */
  public static Optional<ConditionedDeviceConfigs> getConditionedDeviceConfigsFromDimensions(
      Device device) {
    List<String> conditionedDeviceConfigsValueList =
        device.getDimension(CONDITIONED_DEVICE_CONFIGS_DIMENSION_KEY);
    if (conditionedDeviceConfigsValueList.isEmpty()) {
      return Optional.empty();
    }

    // Only the first value is used for now.
    String conditionedDeviceConfigsDimensionValue = conditionedDeviceConfigsValueList.get(0);
    try {
      ConditionedDeviceConfigs.Builder conditionedDeviceConfigs =
          ConditionedDeviceConfigs.newBuilder();
      TextFormat.merge(
          new String(BaseEncoding.base64().decode(conditionedDeviceConfigsDimensionValue), UTF_8),
          conditionedDeviceConfigs);
      return Optional.of(conditionedDeviceConfigs.build());
    } catch (IllegalArgumentException | ParseException e) {
      logger.atWarning().withCause(e).log(
          "Failed to parse conditioned device configs from dimension value [%s]",
          conditionedDeviceConfigsDimensionValue);
      return Optional.empty();
    }
  }

  private ConditionedDeviceConfigUtil() {}
}
