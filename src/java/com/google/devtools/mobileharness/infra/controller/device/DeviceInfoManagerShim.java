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

package com.google.devtools.mobileharness.infra.controller.device;

import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import java.time.Duration;
import javax.annotation.Nullable;

/**
 * A shim layer to allow the OmniLab Lab Server to access package private methods of {@link
 * DeviceInfoManager}.
 */
public final class DeviceInfoManagerShim {

  /** See {@link DeviceInfoManager#add}. */
  public static void add(
      DeviceInfoManager deviceInfoManager,
      DeviceId deviceId,
      @Nullable ApiConfig apiConfig,
      boolean retainDeviceInfo) {
    deviceInfoManager.add(deviceId, apiConfig, retainDeviceInfo);
  }

  /** See {@link DeviceInfoManager#remove}. */
  public static void remove(DeviceInfoManager deviceInfoManager, String deviceControlId) {
    deviceInfoManager.remove(deviceControlId);
  }

  /** See {@link DeviceInfoManager#removeDelayed}. */
  public static void removeDelayed(
      DeviceInfoManager deviceInfoManager, String deviceControlId, Duration delay) {
    deviceInfoManager.removeDelayed(deviceControlId, delay);
  }

  private DeviceInfoManagerShim() {}
}
