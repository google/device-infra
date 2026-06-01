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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.FlashActionInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.FlashButtonParams;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureReadiness;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Utility class to build {@link FlashActionInfo} for flash button. */
@Singleton
public class FlashButtonBuilder {

  private final FeatureManagerFactory featureManagerFactory;
  private final FeatureReadiness featureReadiness;

  @Inject
  FlashButtonBuilder(
      FeatureManagerFactory featureManagerFactory, FeatureReadiness featureReadiness) {
    this.featureManagerFactory = featureManagerFactory;
    this.featureReadiness = featureReadiness;
  }

  public FlashActionInfo build(DeviceInfo deviceInfo, UniverseScope universe) {
    if (!featureManagerFactory.create(universe).isDeviceFlashingFeatureEnabled()) {
      return FlashActionInfo.newBuilder()
          .setState(ActionButtonState.newBuilder().setVisible(false))
          .build();
    }

    List<String> deviceTypes = deviceInfo.getDeviceFeature().getTypeList();
    List<DeviceDimension> dimensions =
        deviceInfo.getDeviceFeature().getCompositeDimension().getRequiredDimensionList();

    boolean isFlashableDevice =
        deviceTypes.contains("AndroidRealDevice") || deviceTypes.contains("AndroidFlashableDevice");

    if (!isFlashableDevice) {
      return FlashActionInfo.newBuilder()
          .setState(ActionButtonState.newBuilder().setVisible(false))
          .build();
    }

    boolean isTestBed = deviceTypes.contains("TestbedDevice");
    boolean isSharedDevice =
        dimensions.stream()
            .anyMatch(dim -> dim.getName().equals("pool") && dim.getValue().equals("shared"));

    boolean isDeviceIdle = deviceInfo.getDeviceStatus().equals(DeviceStatus.IDLE);

    FlashActionInfo.Builder stateBuilder =
        FlashActionInfo.newBuilder()
            .setState(
                ActionButtonState.newBuilder()
                    .setVisible(true)
                    .setIsReady(featureReadiness.isDeviceFlashingReady()))
            // Fill parameters required by the frontend to construct the flash command snippet.
            .setParams(
                FlashButtonParams.newBuilder()
                    .setDeviceType(getAcidDeviceType(deviceTypes))
                    .setRequiredDimensions(getRequiredDimensions(dimensions)));

    if (isSharedDevice || isTestBed) {
      stateBuilder
          .getStateBuilder()
          .setEnabled(false)
          .setTooltip("Device flashing is only supported on satellite lab Android devices.");
    } else {
      if (isDeviceIdle) {
        stateBuilder.getStateBuilder().setEnabled(true).setTooltip("Flash the device");
      } else {
        stateBuilder
            .getStateBuilder()
            .setEnabled(false)
            .setTooltip(
                "Device flash is only allowed on IDLE device. Please wait for device idle and"
                    + " retry.");
      }
    }
    return stateBuilder.build();
  }

  /**
   * Determines the primary device type to be used by ACID for flashing.
   *
   * <p>Follows a predefined order of preference to select the most specific type supported by ACID.
   * Defaults to "AndroidRealDevice" if no specific type matches or if the list is empty.
   *
   * <p>Refer to MHFE V5 implementation in:
   * java/com/google/devtools/mobileharness/fe/v5/ui/util.js:getAcidDeviceType
   */
  @VisibleForTesting
  static String getAcidDeviceType(List<String> deviceTypeList) {
    if (deviceTypeList.contains("TestbedDevice")) {
      return "TestbedDevice";
    } else if (deviceTypeList.contains("NestFctDevice")) {
      return "NestFctDevice";
    } else if (deviceTypeList.contains("NestUartDevice")) {
      return "NestUartDevice";
    } else if (deviceTypeList.contains("UsbDevice")) {
      return "UsbDevice";
    } else if (deviceTypeList.contains("EmbeddedLinuxDevice")) {
      return "EmbeddedLinuxDevice";
    } else if (deviceTypeList.contains("AndroidLocalEmulator")) {
      return "AndroidLocalEmulator";
    } else if (deviceTypeList.contains("AndroidFastbootDevice")) {
      return "AndroidFastbootDevice";
    } else if (deviceTypeList.contains("VideoDevice")) {
      return "VideoDevice";
    } else if (deviceTypeList.contains("AndroidRealDevice")) {
      return "AndroidRealDevice";
    }
    if (!deviceTypeList.isEmpty()) {
      return deviceTypeList.get(0);
    }
    return "AndroidRealDevice";
  }

  /**
   * Formats a list of required dimensions into a comma-separated string of {@code key=value} pairs.
   *
   * <p>If duplicate dimension names exist, it keeps the first one (first come first serve) to avoid
   * conflicting parameters in the flash command. Uses {@link LinkedHashMap} to maintain insertion
   * order for predictable output.
   *
   * <p>Refer to MHFE V5 implementation in:
   * java/com/google/devtools/mobileharness/fe/v5/ui/devicecontrol/remote_control_controller.js:flashDevice
   */
  private static String getRequiredDimensions(List<DeviceDimension> dimensions) {
    return dimensions.stream()
        .collect(
            Collectors.toMap(
                DeviceDimension::getName,
                DeviceDimension::getValue,
                (v1, v2) -> v1, // keep first (deduplication)
                LinkedHashMap::new))
        .entrySet()
        .stream()
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(Collectors.joining(","));
  }
}
