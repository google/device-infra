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

package com.google.devtools.mobileharness.fe.v6.service.shared.providers.oss;

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
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.Annotation.DeviceConfigGrpcStub;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.DeviceConfigStub;
import java.util.Optional;
import javax.inject.Inject;

/** OSS implementation of {@link ConfigurationProvider}. */
public class OssConfigurationProviderImpl implements ConfigurationProvider {

  private final DeviceConfigStub deviceConfigStub;
  private final ListeningExecutorService executor;

  @Inject
  OssConfigurationProviderImpl(
      @DeviceConfigGrpcStub DeviceConfigStub deviceConfigStub, ListeningExecutorService executor) {
    this.deviceConfigStub = deviceConfigStub;
    this.executor = executor;
  }

  @Override
  public ListenableFuture<Optional<DeviceConfig>> getDeviceConfig(
      String deviceId, String universe) {
    GetDeviceConfigsRequest request =
        GetDeviceConfigsRequest.newBuilder()
            .addDeviceLocator(DeviceLocator.newBuilder().setDeviceUuid(deviceId))
            .build();
    return Futures.transform(
        deviceConfigStub.getDeviceConfigsAsync(request, false),
        response -> response.getDeviceConfigList().stream().findFirst(),
        executor);
  }

  @Override
  public ListenableFuture<Optional<LabConfig>> getLabConfig(String hostName, String universe) {
    GetLabConfigRequest request = GetLabConfigRequest.newBuilder().setLabHost(hostName).build();
    return Futures.transform(
        deviceConfigStub.getLabConfigAsync(request, false),
        response ->
            response.hasLabConfig() ? Optional.of(response.getLabConfig()) : Optional.empty(),
        executor);
  }

  @Override
  public ListenableFuture<Void> updateDeviceConfig(
      String deviceId, DeviceConfig deviceConfig, String universe) {
    UpdateDeviceConfigsRequest request =
        UpdateDeviceConfigsRequest.newBuilder()
            .addDeviceLocatorConfig(
                DeviceLocatorConfigPair.newBuilder()
                    .setDeviceLocator(DeviceLocator.newBuilder().setDeviceUuid(deviceId))
                    .setDeviceConfig(deviceConfig))
            .build();
    return Futures.transform(
        deviceConfigStub.updateDeviceConfigsAsync(request, false), response -> null, executor);
  }

  @Override
  public ListenableFuture<Void> updateLabConfig(
      String hostName, LabConfig labConfig, String universe) {
    UpdateLabConfigRequest request =
        UpdateLabConfigRequest.newBuilder().setLabConfig(labConfig).build();
    return Futures.transform(
        deviceConfigStub.updateLabConfigAsync(request, false), response -> null, executor);
  }
}
