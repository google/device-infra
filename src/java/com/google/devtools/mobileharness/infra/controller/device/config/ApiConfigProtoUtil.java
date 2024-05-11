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

package com.google.devtools.mobileharness.infra.controller.device.config;

import com.google.devtools.mobileharness.shared.util.message.StrPairUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.ClassUtil;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import com.google.wireless.qa.mobileharness.shared.proto.Config;
import com.google.wireless.qa.mobileharness.shared.proto.Config.ApiConfigOrBuilder;
import com.google.wireless.qa.mobileharness.shared.proto.Config.DeviceConfig;
import com.google.wireless.qa.mobileharness.shared.proto.Config.DeviceConfigOrBuilder;
import com.google.wireless.qa.mobileharness.shared.proto.Config.DriverConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Utility to operate ApiConfig protobuf. */
public final class ApiConfigProtoUtil {

  /** The public owner that should be forbidden to set to the owner list of a lab or device. */
  private static final String PUBLIC_OWNER = "PUBLIC";

  /**
   * Verifies the given lab configuration.
   *
   * @return the errors of the given config, or an empty list if the config is ok
   */
  public static List<String> verifyConfig(ApiConfigOrBuilder apiConfig) {
    List<String> errors = new ArrayList<>();

    // Makes sure there isn't any public owners in the lab owner list.
    errors.addAll(verifyOwners(apiConfig.getOwnerList()));

    // Makes sure the lab-server-wide dimension values don't start with "regex".
    List<StrPair> labWideDimensions = new ArrayList<>(apiConfig.getDimensionList());
    labWideDimensions.addAll(
        StrPairUtil.convertFromDeviceDimension(
            apiConfig.getCompositeDimension().getRequiredDimensionList()));
    labWideDimensions.addAll(
        StrPairUtil.convertFromDeviceDimension(
            apiConfig.getCompositeDimension().getSupportedDimensionList()));
    errors.addAll(verifyDimensions(/* deviceId= */ null, labWideDimensions));

    // Makes sure the device ids are unique, and dimension values don't start with "regex".
    Set<String> ids = new HashSet<>();
    for (DeviceConfig deviceConfig : apiConfig.getDeviceConfigList()) {
      errors.addAll(verifyDeviceConfig(deviceConfig, ids));
    }

    // Makes sure the core drivers exist and their names are unique.
    Set<String> driverNames = new HashSet<>();
    for (DriverConfig driverConfig : apiConfig.getDriverConfigList()) {
      String name = driverConfig.getName();
      try {
        ClassUtil.getDriverClass(name);
      } catch (MobileHarnessException e) {
        errors.add(e.getMessage());
      }
      try {
        ClassUtil.getDecoratorClass(name);
        errors.add("driver_config.name should NOT be a name of a decorator");
      } catch (MobileHarnessException e) {
        // Makes sure the driver is a core driver.
      }
      if (driverNames.contains(name)) {
        errors.add("Two driver_configs should NOT share the same driver name: " + name);
      }
      driverNames.add(name);
    }
    return errors;
  }

  /**
   * Verifies whether DeviceConfig message is legal.
   *
   * @param deviceConfig the DeviceConfig message to be verified
   * @param ids existed device ids. It should be mutable
   * @return the errors found in the DeviceConfig
   */
  public static List<String> verifyDeviceConfig(
      DeviceConfigOrBuilder deviceConfig, Set<String> ids) {
    List<String> errors = new ArrayList<>();

    if (!deviceConfig.hasId()) {
      errors.add("Must specify the device id in the device config");
    } else {
      String id = deviceConfig.getId();
      if (ids.contains(id)) {
        errors.add("Two device_configs should NOT share the same device id: " + id);
      }
      ids.add(id);
    }

    // Makes sure there isn't any public owners in the device owner list.
    errors.addAll(verifyOwners(deviceConfig.getOwnerList()));

    List<StrPair> deviceWideDimensions = new ArrayList<>(deviceConfig.getDimensionList());
    deviceWideDimensions.addAll(
        StrPairUtil.convertFromDeviceDimension(
            deviceConfig.getCompositeDimension().getRequiredDimensionList()));
    deviceWideDimensions.addAll(
        StrPairUtil.convertFromDeviceDimension(
            deviceConfig.getCompositeDimension().getSupportedDimensionList()));
    errors.addAll(verifyDimensions(deviceConfig.getId(), deviceWideDimensions));
    return errors;
  }

  /** Verifies the given list of lab/device dimensions. */
  private static List<String> verifyDimensions(
      @Nullable String deviceId, List<StrPair> dimensions) {
    List<String> errors = new ArrayList<>();
    for (StrPair dimension : dimensions) {
      String dimensionValue = dimension.getValue();
      if (dimensionValue.toLowerCase().startsWith(Dimension.Value.PREFIX_REGEX)) {
        errors.add(
            String.format(
                "The value of dimension %s %s should NOT start with %s",
                dimension.getName(),
                deviceId == null ? "" : "of device " + deviceId,
                Dimension.Value.PREFIX_REGEX));
      } else if (dimensionValue.equalsIgnoreCase(Dimension.Value.EXCLUDE)) {
        errors.add(
            String.format(
                "The value of dimension %s %s should NOT set as %s",
                dimension.getName(),
                deviceId == null ? "" : "of device " + deviceId,
                Dimension.Value.EXCLUDE));
      }
    }
    return errors;
  }

  /** Verifies the given list of lab/device owners. */
  private static List<String> verifyOwners(List<String> owners) {
    List<String> errors = new ArrayList<>();
    for (String owner : owners) {
      if (owner.equalsIgnoreCase(PUBLIC_OWNER)) {
        errors.add(
            "It is forbidden to set \"PUBLIC\" as a device owner. "
                + "If you want to public the device to everyone, don't set any owner");
      }
    }
    return errors;
  }

  @CanIgnoreReturnValue
  public static Config.ApiConfig.Builder fromText(String text) throws MobileHarnessException {
    Config.ApiConfig.Builder builder = Config.ApiConfig.newBuilder();
    try {
      TextFormat.Parser.newBuilder().setAllowUnknownFields(true).build().merge(text, builder);
    } catch (ParseException e) {
      throw new MobileHarnessException(
          ErrorCode.DEVICE_CONFIG_ERROR, "Failed to parse Config.ApiConfig from text: " + text, e);
    }
    return builder;
  }

  public static DeviceConfig.Builder deviceConfigFromText(String text)
      throws MobileHarnessException {
    DeviceConfig.Builder builder = DeviceConfig.newBuilder();
    try {
      TextFormat.Parser.newBuilder().setAllowUnknownFields(true).build().merge(text, builder);
    } catch (ParseException e) {
      throw new MobileHarnessException(
          ErrorCode.DEVICE_CONFIG_ERROR,
          "Failed to parse Config.DeviceConfig from text: " + text,
          e);
    }
    return builder;
  }

  private ApiConfigProtoUtil() {}
}
