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

import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.FlashActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManager;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Utility class to build {@link FlashActionButtonState} for flash button. */
@Singleton
public class FlashButtonBuilder {

  private final FeatureManager featureManager;

  @Inject
  FlashButtonBuilder(FeatureManager featureManager) {
    this.featureManager = featureManager;
  }

  public FlashActionButtonState build(DeviceInfo deviceInfo) {
    if (!featureManager.isDeviceFlashingEnabled()) {
      return FlashActionButtonState.newBuilder()
          .setState(ActionButtonState.newBuilder().setVisible(false))
          .build();
    }

    List<String> deviceTypes = deviceInfo.getDeviceFeature().getTypeList();
    List<DeviceDimension> dimensions =
        deviceInfo.getDeviceFeature().getCompositeDimension().getRequiredDimensionList();

    boolean isFlashableDevice =
        deviceTypes.contains("AndroidRealDevice") || deviceTypes.contains("AndroidFlashableDevice");

    if (!isFlashableDevice) {
      return FlashActionButtonState.newBuilder()
          .setState(ActionButtonState.newBuilder().setVisible(false))
          .build();
    }

    boolean isTestBed = deviceTypes.contains("TestbedDevice");
    boolean isSharedDevice =
        dimensions.stream()
            .anyMatch(dim -> dim.getName().equals("pool") && dim.getValue().equals("shared"));

    boolean isDeviceIdle = deviceInfo.getDeviceStatus().equals(DeviceStatus.IDLE);

    FlashActionButtonState.Builder stateBuilder =
        FlashActionButtonState.newBuilder()
            .setState(ActionButtonState.newBuilder().setVisible(true));

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
}
