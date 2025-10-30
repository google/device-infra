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

package com.google.devtools.mobileharness.shared.constant.device;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;

/** Shared constants for device. */
public final class DeviceConstants {
  /** The device types that are considered unhealthy. */
  public static final ImmutableSet<String> UNHEALTHY_DEVICE_TYPES =
      ImmutableSet.of(
          "AbnormalTestbedDevice",
          "AndroidFastbootDevice",
          "AndroidFastbootdModeDevice",
          "AndroidOfflineDevice",
          "AndroidUnauthorizedDevice",
          "DisconnectedDevice",
          "FailedDevice");

  public static final ImmutableSet<DeviceStatus> HEALTHY_STATUS =
      ImmutableSet.of(DeviceStatus.IDLE, DeviceStatus.BUSY, DeviceStatus.LAMEDUCK);

  private DeviceConstants() {}
}
