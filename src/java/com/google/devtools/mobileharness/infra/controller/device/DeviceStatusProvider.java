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

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatusWithTimestamp;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.Map;
import javax.annotation.Nullable;

/** Interface for providing device status. */
public interface DeviceStatusProvider {
  /** Inforamtion with a device and the status info. */
  @AutoValue
  public abstract static class DeviceWithStatusInfo {
    public abstract Device device();

    public abstract DeviceStatusInfo deviceStatusInfo();

    public static DeviceWithStatusInfo create(Device device, DeviceStatusInfo deviceStatusInfo) {
      return new AutoValue_DeviceStatusProvider_DeviceWithStatusInfo(device, deviceStatusInfo);
    }
  }

  /** Gets the device status according to its device ID. */
  DeviceWithStatusInfo getDeviceAndStatusInfo(String deviceId);

  /** Gets the device status according to its device ID and device type. */
  DeviceWithStatusInfo getDeviceAndStatusInfo(String deviceId, @Nullable String deviceType);

  /**
   * Gets the status of all devices. The devices which are not ready are excluded from the result.
   *
   * @param realtimeDetect whether to do a real-time detection to make sure the devices of the
   *     runners are detectable. All other calls except for the one in MasterSyncerForDevice should
   *     set realtimeDetect=false otherwise all related threads might be blocked for seconds.
   * @return {Device, {@link DeviceStatusWithTimestamp}} mapping of all the ready devices
   */
  Map<Device, DeviceStatusInfo> getAllDeviceStatus(boolean realtimeDetect)
      throws InterruptedException;

  /**
   * Gets the status of all devices. The devices which are not ready or mark as duplicated from
   * master are excluded from the result.
   *
   * @param realtimeDispatch whether to do a real-time dispatch to make sure the devices of the
   *     runners are detectable by the previous detection result
   * @return {Device, {@link DeviceStatusWithTimestamp}} mapping of all the ready and unique uuid
   *     devices
   */
  Map<Device, DeviceStatusInfo> getAllDeviceStatusWithoutDuplicatedUuid(boolean realtimeDispatch)
      throws InterruptedException;

  /** Updates a new duplicated uuid device. */
  void updateDuplicatedUuid(String deviceUuid);
}
