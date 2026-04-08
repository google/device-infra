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

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;

/** No-op implementation of {@link ConfigurationProvider}. */
public class NoOpConfigurationProvider implements ConfigurationProvider {

  @Override
  public ListenableFuture<Optional<DeviceConfig>> getDeviceConfig(
      String deviceId, UniverseScope universe) {
    return immediateFuture(Optional.empty());
  }

  @Override
  public ListenableFuture<Optional<LabConfig>> getLabConfig(
      String hostName, UniverseScope universe) {
    return immediateFuture(Optional.empty());
  }

  @Override
  public ListenableFuture<Void> updateDeviceConfig(
      String deviceId, DeviceConfig deviceConfig, UniverseScope universe) {
    return immediateFailedFuture(
        new UnsupportedOperationException("NoOpConfigurationProvider does not support updates"));
  }

  @Override
  public ListenableFuture<Void> updateLabConfig(
      String hostName, LabConfig labConfig, UniverseScope universe) {
    return immediateFailedFuture(
        new UnsupportedOperationException("NoOpConfigurationProvider does not support updates"));
  }
}
