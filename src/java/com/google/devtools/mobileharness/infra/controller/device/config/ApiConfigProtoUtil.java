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

import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.LabDevice.LabDeviceConfig;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.TextFormat.ParseException;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.proto.Config;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
  public static List<String> verifyConfig(LabDeviceConfig labDeviceConfig) {
    List<String> errors = new ArrayList<>();

    BasicDeviceConfig basicDeviceConfig = labDeviceConfig.getLabConfig().getDefaultDeviceConfig();
    // Makes sure there isn't any public owners in the lab owner list.
    errors.addAll(verifyOwners(basicDeviceConfig.getOwnerList()));

    // Makes sure the lab-server-wide dimension values don't start with "regex".
    List<DeviceDimension> labWideDimensions = new ArrayList<>();
    labWideDimensions.addAll(basicDeviceConfig.getCompositeDimension().getRequiredDimensionList());
    labWideDimensions.addAll(basicDeviceConfig.getCompositeDimension().getSupportedDimensionList());
    errors.addAll(verifyDimensions(/* deviceId= */ null, labWideDimensions));

    // Makes sure the device ids are unique, and dimension values don't start with "regex".
    Set<String> ids = new HashSet<>();
    for (DeviceConfig deviceConfig : labDeviceConfig.getDeviceConfigList()) {
      errors.addAll(verifyDeviceConfig(deviceConfig, ids));
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
  public static List<String> verifyDeviceConfig(DeviceConfig deviceConfig, Set<String> ids) {
    List<String> errors = new ArrayList<>();

    if (deviceConfig.getUuid().isEmpty()) {
      errors.add("Must specify the device id in the device config");
    } else {
      String id = deviceConfig.getUuid();
      if (ids.contains(id)) {
        errors.add("Two device_configs should NOT share the same device id: " + id);
      }
      ids.add(id);
    }

    // Makes sure there isn't any public owners in the device owner list.
    errors.addAll(verifyOwners(deviceConfig.getBasicConfig().getOwnerList()));

    List<DeviceDimension> deviceWideDimensions = new ArrayList<>();
    deviceWideDimensions.addAll(
        deviceConfig.getBasicConfig().getCompositeDimension().getRequiredDimensionList());
    deviceWideDimensions.addAll(
        deviceConfig.getBasicConfig().getCompositeDimension().getSupportedDimensionList());
    errors.addAll(verifyDimensions(deviceConfig.getUuid(), deviceWideDimensions));
    return errors;
  }

  /** Verifies the given list of lab/device dimensions. */
  private static List<String> verifyDimensions(
      @Nullable String deviceId, List<DeviceDimension> dimensions) {
    List<String> errors = new ArrayList<>();
    for (DeviceDimension dimension : dimensions) {
      String dimensionValue = dimension.getValue();
      if (dimensionValue.toLowerCase(Locale.ROOT).startsWith(Dimension.Value.PREFIX_REGEX)) {
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
  public static Config.ApiConfig fromApiConfigText(String text) throws MobileHarnessException {
    try {
      return ProtoTextFormat.parse(text, Config.ApiConfig.class);
    } catch (ParseException e) {
      throw new MobileHarnessException(
          BasicErrorId.API_CONFIG_FILE_READ_ERROR,
          "Failed to parse Config.ApiConfig from text: " + text,
          e);
    }
  }

  @CanIgnoreReturnValue
  public static LabDeviceConfig fromLabDeviceConfigText(String text) throws MobileHarnessException {
    try {
      return ProtoTextFormat.parse(text, LabDeviceConfig.class);
    } catch (ParseException e) {
      throw new MobileHarnessException(
          BasicErrorId.LAB_DEVICE_CONFIG_FILE_READ_ERROR,
          "Failed to convert text file to lab device config proto.",
          e);
    }
  }

  private ApiConfigProtoUtil() {}
}
