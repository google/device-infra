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

import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManager;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Utility class to build {@link ActionButtonState} for logcat button. */
@Singleton
class LogcatButtonBuilder {

  private final FeatureManager featureManager;

  @Inject
  LogcatButtonBuilder(FeatureManager featureManager) {
    this.featureManager = featureManager;
  }

  public ActionButtonState build(DeviceInfo deviceInfo) {
    if (!featureManager.isDeviceLogcatButtonEnabled()) {
      return ActionButtonState.newBuilder().setVisible(false).build();
    }

    List<String> deviceTypes = deviceInfo.getDeviceFeature().getTypeList();
    boolean isAndroidRealDevice =
        deviceTypes.contains("AndroidRealDevice") && !deviceTypes.contains("TestbedDevice");

    if (!isAndroidRealDevice) {
      return ActionButtonState.newBuilder().setVisible(false).build();
    }

    boolean isDeviceMissing = deviceInfo.getDeviceStatus().equals(DeviceStatus.MISSING);

    boolean isSharedDevice =
        deviceInfo.getDeviceFeature().getCompositeDimension().getRequiredDimensionList().stream()
            .anyMatch(d -> d.getName().equals("pool") && d.getValue().equals("shared"));

    ActionButtonState.Builder stateBuilder = ActionButtonState.newBuilder().setVisible(true);

    if (isDeviceMissing || isSharedDevice) {
      stateBuilder
          .setEnabled(false)
          .setTooltip(
              "It's only supported to get the device logcat on the satellite Android devices.");
    } else {
      stateBuilder.setEnabled(true).setTooltip("Get logcat of the device");
    }

    return stateBuilder.build();
  }
}
