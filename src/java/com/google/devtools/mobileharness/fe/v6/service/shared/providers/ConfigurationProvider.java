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

package com.google.devtools.mobileharness.fe.v6.service.shared.providers;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;

/** Interface for fetching and updating device and lab configurations. */
public interface ConfigurationProvider {
  ListenableFuture<Optional<DeviceConfig>> getDeviceConfig(String deviceId, UniverseScope universe);

  /**
   * @deprecated Use {@link #getDeviceConfig(String, UniverseScope)} instead. TODO: Remove after all
   *     callers are migrated to UniverseScope.
   */
  @Deprecated
  default ListenableFuture<Optional<DeviceConfig>> getDeviceConfig(
      String deviceId, String universe) {
    return getDeviceConfig(deviceId, UniverseScope.fromString(universe));
  }

  ListenableFuture<Optional<LabConfig>> getLabConfig(String hostName, UniverseScope universe);

  /**
   * @deprecated Use {@link #getLabConfig(String, UniverseScope)} instead. TODO: Remove after all
   *     callers are migrated to UniverseScope.
   */
  @Deprecated
  default ListenableFuture<Optional<LabConfig>> getLabConfig(String hostName, String universe) {
    return getLabConfig(hostName, UniverseScope.fromString(universe));
  }

  ListenableFuture<Void> updateDeviceConfig(
      String deviceId, DeviceConfig deviceConfig, UniverseScope universe);

  /**
   * @deprecated Use {@link #updateDeviceConfig(String, DeviceConfig, UniverseScope)} instead. TODO:
   *     Remove after all callers are migrated to UniverseScope.
   */
  @Deprecated
  default ListenableFuture<Void> updateDeviceConfig(
      String deviceId, DeviceConfig deviceConfig, String universe) {
    return updateDeviceConfig(deviceId, deviceConfig, UniverseScope.fromString(universe));
  }

  ListenableFuture<Void> updateLabConfig(
      String hostName, LabConfig labConfig, UniverseScope universe);

  /**
   * @deprecated Use {@link #updateLabConfig(String, LabConfig, UniverseScope)} instead. TODO:
   *     Remove after all callers are migrated to UniverseScope.
   */
  @Deprecated
  default ListenableFuture<Void> updateLabConfig(
      String hostName, LabConfig labConfig, String universe) {
    return updateLabConfig(hostName, labConfig, UniverseScope.fromString(universe));
  }
}
