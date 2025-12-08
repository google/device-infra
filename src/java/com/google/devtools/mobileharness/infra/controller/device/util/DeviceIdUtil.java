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

import com.google.common.base.Strings;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;

/** Utility for {@link DeviceId}. */
public class DeviceIdUtil {

  /** Adds the device ID and the class name to the device dimensions. */
  public static void addDeviceIdAndClassNameToDimension(DeviceId deviceId, Device device) {
    device.addDimension(Dimension.Name.ID, deviceId.controlId());
    device.addDimension(Dimension.Name.CONTROL_ID, deviceId.controlId());
    if (!Strings.isNullOrEmpty(deviceId.uuid())) {
      device.addDimension(Dimension.Name.ID, deviceId.uuid());
      device.addDimension(Dimension.Name.UUID, deviceId.uuid());
      device.addDimension(Dimension.Name.UUID_VOLATILE, String.valueOf(deviceId.isUuidVolatile()));
    }
    device.addDimension(Dimension.Name.DEVICE_CLASS_NAME, device.getClass().getSimpleName());
  }

  private DeviceIdUtil() {}
}
