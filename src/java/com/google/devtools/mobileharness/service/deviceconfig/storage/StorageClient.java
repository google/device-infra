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

package com.google.devtools.mobileharness.service.deviceconfig.storage;

import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.util.Optional;

/** Interface for storage backends of device config service to store lab configurations. */
public interface StorageClient {

  /**
   * Gets the config for the given device UUID.
   *
   * @param deviceUuid device UUID
   * @return an {@link Optional} containing the {@link DeviceConfig}, or empty if not found
   */
  Optional<DeviceConfig> getDeviceConfig(String deviceUuid) throws MobileHarnessException;

  /**
   * Gets the config for the given lab host.
   *
   * @param hostName lab host name
   * @return an {@link Optional} containing the {@link LabConfig}, or empty if not found
   */
  Optional<LabConfig> getLabConfig(String hostName) throws MobileHarnessException;

  /**
   * Upserts the config for the given device.
   *
   * @param deviceConfig the {@link DeviceConfig} to upsert
   */
  void upsertDeviceConfig(DeviceConfig deviceConfig) throws MobileHarnessException;

  /**
   * Upserts the config for the given lab host.
   *
   * @param labConfig the {@link LabConfig} to upsert
   */
  void upsertLabConfig(LabConfig labConfig) throws MobileHarnessException;

  /**
   * Deletes the config for the given device UUID.
   *
   * @param deviceUuid device UUID
   */
  void deleteDeviceConfig(String deviceUuid) throws MobileHarnessException;

  /**
   * Deletes the config for the given lab host.
   *
   * @param hostName lab host name
   */
  void deleteLabConfig(String hostName) throws MobileHarnessException;
}
