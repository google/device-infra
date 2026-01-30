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

import com.google.common.base.Ascii;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManager;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Utility class to build {@link ActionButtonState} for screenshot button. */
@Singleton
class ScreenshotButtonBuilder {

  private final FeatureManager featureManager;

  @Inject
  ScreenshotButtonBuilder(FeatureManager featureManager) {
    this.featureManager = featureManager;
  }

  public ActionButtonState build(DeviceInfo deviceInfo) {
    if (!featureManager.isDeviceScreenshotEnabled()) {
      return ActionButtonState.newBuilder().setVisible(false).build();
    }

    boolean isDeviceMissing = deviceInfo.getDeviceStatus().equals(DeviceStatus.MISSING);

    boolean isScreenshotSupported =
        deviceInfo.getDeviceFeature().getCompositeDimension().getSupportedDimensionList().stream()
            .anyMatch(
                dimension ->
                    dimension.getName().equals("screenshot_able")
                        && Ascii.equalsIgnoreCase(dimension.getValue(), "true"));

    if (!isDeviceMissing && isScreenshotSupported) {
      return ActionButtonState.newBuilder()
          .setVisible(true)
          .setEnabled(true)
          .setTooltip("Take screenshot of the device")
          .build();
    } else {
      return ActionButtonState.newBuilder()
          .setVisible(true)
          .setEnabled(false)
          .setTooltip(
              "It's only supported to take a screenshot when the device is not missing and the "
                  + "\"screenshot_able\" dimension is existing for this device.")
          .build();
    }
  }
}
