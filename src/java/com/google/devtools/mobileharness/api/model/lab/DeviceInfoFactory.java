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

package com.google.devtools.mobileharness.api.model.lab;

import com.google.devtools.mobileharness.api.model.lab.in.DimensionsFactory;
import com.google.devtools.mobileharness.api.model.lab.in.LocalDimensions;
import com.google.devtools.mobileharness.api.model.lab.out.PropertiesFactory;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import javax.annotation.Nullable;

/** Factory for creating {@link DeviceInfo}. */
public class DeviceInfoFactory {

  public static DeviceInfo create(DeviceId deviceId, @Nullable ApiConfig apiConfig) {
    LocalDimensions supportedLocalDimensions = new LocalDimensions();
    LocalDimensions requiredLocalDimensions = new LocalDimensions();
    return DeviceInfo.create(
        deviceId,
        DimensionsFactory.createCompositeDimensions(
            deviceId.controlId(), apiConfig, supportedLocalDimensions, requiredLocalDimensions),
        PropertiesFactory.create(deviceId.controlId()));
  }

  private DeviceInfoFactory() {}
}
