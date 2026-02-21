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

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.config.util.DeviceConfigConverter;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigUiStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.DeviceData;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetDeviceConfig RPC. */
@Singleton
public final class GetDeviceConfigHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DeviceDataLoader deviceDataLoader;
  private final ConfigServiceCapability configServiceCapability;
  private final ListeningExecutorService executor;

  @Inject
  GetDeviceConfigHandler(
      DeviceDataLoader deviceDataLoader,
      ConfigServiceCapability configServiceCapability,
      ListeningExecutorService executor) {
    this.deviceDataLoader = deviceDataLoader;
    this.configServiceCapability = configServiceCapability;
    this.executor = executor;
  }

  public ListenableFuture<GetDeviceConfigResponse> getDeviceConfig(GetDeviceConfigRequest request) {
    logger.atInfo().log("Getting device config for %s", request.getId());
    if (!configServiceCapability.isUniverseSupported(request.getUniverse())) {
      throw new UnsupportedOperationException(
          String.format(
              "Configuration operations are not currently supported for universe '%s' in the Google"
                  + " Internal environment.",
              request.getUniverse()));
    }

    ListenableFuture<DeviceData> deviceDataFuture =
        deviceDataLoader.loadDeviceData(request.getId(), request.getUniverse());

    return Futures.transform(
        deviceDataFuture, deviceData -> buildResponse(request.getId(), deviceData), executor);
  }

  private GetDeviceConfigResponse buildResponse(
      @SuppressWarnings("unused") String deviceId, DeviceData deviceData) {

    boolean isHostManaged = deviceData.isHostManaged();
    DeviceConfig feDeviceConfig =
        DeviceConfigConverter.toFeDeviceConfig(deviceData.effectiveDeviceConfig().getBasicConfig());
    DeviceConfigUiStatus uiStatus = configServiceCapability.calculateDeviceUiStatus();

    return GetDeviceConfigResponse.newBuilder()
        .setDeviceConfig(feDeviceConfig)
        .setIsHostManaged(isHostManaged)
        .setHostName(deviceData.deviceInfo().getDeviceLocator().getLabLocator().getHostName())
        .setUiStatus(uiStatus)
        .build();
  }
}
