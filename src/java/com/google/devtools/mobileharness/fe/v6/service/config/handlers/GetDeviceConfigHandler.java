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

package com.google.devtools.mobileharness.fe.v6.service.config.handlers;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigConverter;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigUiStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.DeviceData;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetDeviceConfig RPC. */
@Singleton
public final class GetDeviceConfigHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DeviceDataLoader deviceDataLoader;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final ListeningExecutorService executor;

  @Inject
  GetDeviceConfigHandler(
      DeviceDataLoader deviceDataLoader,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      ListeningExecutorService executor) {
    this.deviceDataLoader = deviceDataLoader;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.executor = executor;
  }

  public ListenableFuture<GetDeviceConfigResponse> getDeviceConfig(
      GetDeviceConfigRequest request, UniverseScope universe) {
    logger.atInfo().log("Getting device config for %s", request.getId());
    ConfigServiceCapability configServiceCapability =
        configServiceCapabilityFactory.create(universe);
    try {
      configServiceCapability.checkConfigServiceAvailability();
    } catch (UnsupportedOperationException e) {
      return immediateFailedFuture(e);
    }

    ListenableFuture<DeviceData> deviceDataFuture =
        deviceDataLoader.loadDeviceData(request.getId(), universe);

    return Futures.transform(
        deviceDataFuture,
        deviceData -> buildResponse(configServiceCapability, deviceData),
        executor);
  }

  private GetDeviceConfigResponse buildResponse(
      ConfigServiceCapability configServiceCapability, DeviceData deviceData) {

    boolean isHostManaged = deviceData.isHostManaged();
    DeviceConfig feDeviceConfig =
        ConfigConverter.toFeDeviceConfig(deviceData.effectiveDeviceConfig().getBasicConfig());
    DeviceConfigUiStatus uiStatus = configServiceCapability.calculateDeviceUiStatus();

    return GetDeviceConfigResponse.newBuilder()
        .setDeviceConfig(feDeviceConfig)
        .setIsHostManaged(isHostManaged)
        .setHostName(deviceData.deviceInfo().getDeviceLocator().getLabLocator().getHostName())
        .setUiStatus(uiStatus)
        .build();
  }
}
