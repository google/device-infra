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

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocatorConfigPair;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.DeviceConfigStub;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/** Unified implementation of {@link ConfigurationProvider}. */
public class ConfigurationProviderImpl implements ConfigurationProvider {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DeviceConfigStub deviceConfigStub;
  private final ListeningExecutorService executor;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final boolean useClientRpcAuthority;

  @Inject
  ConfigurationProviderImpl(
      DeviceConfigStub deviceConfigStub,
      ListeningExecutorService executor,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      Environment environment) {
    // TODO: Use different stubs for different universes when we support multi-universe
    // in 1P FE.
    this.deviceConfigStub = deviceConfigStub;
    this.executor = executor;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.useClientRpcAuthority = environment.isGoogleInternal();
  }

  @Override
  public ListenableFuture<ConfigResult<DeviceConfig>> getDeviceConfig(
      String deviceId, UniverseScope universe) {
    if (!configServiceCapabilityFactory.create(universe).isConfigServiceAvailable()) {
      return immediateFuture(ConfigResult.unavailable());
    }

    GetDeviceConfigsRequest request =
        GetDeviceConfigsRequest.newBuilder()
            .addDeviceLocator(DeviceLocator.newBuilder().setDeviceUuid(deviceId))
            .build();
    return Futures.transform(
        deviceConfigStub.getDeviceConfigsAsync(request, false),
        response -> ConfigResult.available(response.getDeviceConfigList().stream().findFirst()),
        executor);
  }

  @Override
  public ListenableFuture<List<DeviceConfig>> getDeviceConfigs(
      List<String> deviceIds, UniverseScope universe) {
    if (!configServiceCapabilityFactory.create(universe).isConfigServiceAvailable()) {
      return immediateFuture(ImmutableList.of());
    }

    GetDeviceConfigsRequest.Builder requestBuilder = GetDeviceConfigsRequest.newBuilder();
    for (String deviceId : deviceIds) {
      requestBuilder.addDeviceLocator(DeviceLocator.newBuilder().setDeviceUuid(deviceId));
    }

    return Futures.transform(
        deviceConfigStub.getDeviceConfigsAsync(requestBuilder.build(), false),
        response -> response.getDeviceConfigList(),
        executor);
  }

  @Override
  public ListenableFuture<ConfigResult<LabConfig>> getLabConfig(
      String hostName, UniverseScope universe) {
    if (!configServiceCapabilityFactory.create(universe).isConfigServiceAvailable()) {
      return immediateFuture(ConfigResult.unavailable());
    }
    GetLabConfigRequest request = GetLabConfigRequest.newBuilder().setLabHost(hostName).build();
    return Futures.transform(
        deviceConfigStub.getLabConfigAsync(request, false),
        response ->
            ConfigResult.available(
                response.hasLabConfig() ? Optional.of(response.getLabConfig()) : Optional.empty()),
        executor);
  }

  @Override
  public ListenableFuture<Void> updateDeviceConfig(
      String deviceId, DeviceConfig deviceConfig, UniverseScope universe) {
    if (!configServiceCapabilityFactory.create(universe).isConfigServiceAvailable()) {
      return immediateVoidFuture();
    }
    UpdateDeviceConfigsRequest request =
        UpdateDeviceConfigsRequest.newBuilder()
            .addDeviceLocatorConfig(
                DeviceLocatorConfigPair.newBuilder()
                    .setDeviceLocator(DeviceLocator.newBuilder().setDeviceUuid(deviceId))
                    .setDeviceConfig(deviceConfig))
            .build();
    return Futures.transform(
        deviceConfigStub.updateDeviceConfigsAsync(request, useClientRpcAuthority),
        response -> {
          // The server only adds a map entry when the update for that device FAILED, so the
          // presence of the key (not the message content) is the failure signal. Checking
          // containsKey() also correctly treats an empty error message as a failure.
          if (response.getErrorMessageMap().containsKey(deviceId)) {
            String errorMessage = response.getErrorMessageMap().get(deviceId);
            logger.atWarning().log(
                "Device config service returned error for %s: %s", deviceId, errorMessage);
            // TODO: Throw a typed exception (e.g. DeviceConfigUpdateException carrying
            // deviceId + errorMessage) instead of a generic IllegalStateException, so callers can
            // catch this specific failure and surface the server-provided error detail rather than
            // a generic message. Apply the same pattern to updateLabConfig(), which has the same
            // latent error_message handling gap.
            throw new IllegalStateException(
                String.format("Failed to update config of device %s: %s", deviceId, errorMessage));
          }
          return null;
        },
        executor);
  }

  @Override
  public ListenableFuture<Void> updateLabConfig(
      String hostName, LabConfig labConfig, UniverseScope universe) {
    if (!configServiceCapabilityFactory.create(universe).isConfigServiceAvailable()) {
      return immediateVoidFuture();
    }
    UpdateLabConfigRequest request =
        UpdateLabConfigRequest.newBuilder().setLabConfig(labConfig).build();
    return Futures.transform(
        deviceConfigStub.updateLabConfigAsync(request, useClientRpcAuthority),
        response -> null,
        executor);
  }
}
