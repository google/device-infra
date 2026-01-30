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
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManager;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Utility class to build {@link ActionButtonState} for quarantine button. */
@Singleton
class QuarantineButtonBuilder {

  private final FeatureManager featureManager;

  @Inject
  QuarantineButtonBuilder(FeatureManager featureManager) {
    this.featureManager = featureManager;
  }

  public ActionButtonState build(DeviceInfo deviceInfo) {
    if (!featureManager.isDeviceQuarantineEnabled()) {
      return ActionButtonState.newBuilder().setVisible(false).build();
    }

    boolean isQuarantined =
        deviceInfo.getDeviceCondition().getTempDimensionList().stream()
            .anyMatch(
                dim ->
                    dim.getDimension().getName().equals("quarantined")
                        && Ascii.toLowerCase(dim.getDimension().getValue()).equals("true"));

    String tooltip =
        isQuarantined
            ? "Unquarantine the device to allow it to be allocated by other tests."
            : "Quarantine the device to prevent it from being allocated by other tests.";

    return ActionButtonState.newBuilder()
        .setVisible(true)
        .setEnabled(true)
        .setTooltip(tooltip)
        .build();
  }
}
