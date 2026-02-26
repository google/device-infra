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
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigUiStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.DeviceData;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetDeviceConfig RPC. */
@Singleton
public final class GetDeviceConfigHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Environment environment;
  private final DeviceDataLoader deviceDataLoader;
  private final ListeningExecutorService executor;

  @Inject
  GetDeviceConfigHandler(
      Environment environment,
      DeviceDataLoader deviceDataLoader,
      ListeningExecutorService executor) {
    this.environment = environment;
    this.deviceDataLoader = deviceDataLoader;
    this.executor = executor;
  }

  public ListenableFuture<GetDeviceConfigResponse> getDeviceConfig(GetDeviceConfigRequest request) {
    logger.atInfo().log("Getting device config for %s", request.getDeviceId());
    if (environment.isGoogleInternal()
        && !request.getUniverse().isEmpty()
        && !request.getUniverse().equals("google_1p")) {
      throw new UnsupportedOperationException(
          String.format(
              "Configuration operations are not currently supported for universe '%s' in the Google"
                  + " Internal environment.",
              request.getUniverse()));
    }

    ListenableFuture<DeviceData> deviceDataFuture =
        deviceDataLoader.loadDeviceData(request.getDeviceId(), request.getUniverse());

    return Futures.transform(
        deviceDataFuture, deviceData -> buildResponse(request.getDeviceId(), deviceData), executor);
  }

  private GetDeviceConfigResponse buildResponse(
      @SuppressWarnings("unused") String deviceId, DeviceData deviceData) {

    boolean isHostManaged = deviceData.isHostManaged();
    DeviceConfig feDeviceConfig = convertToFeDeviceConfig(deviceData, isHostManaged);
    DeviceConfigUiStatus uiStatus = calculateUiStatus(environment.getEnvironmentType());

    return GetDeviceConfigResponse.newBuilder()
        .setDeviceConfig(feDeviceConfig)
        .setIsHostManaged(isHostManaged)
        .setHostName(deviceData.deviceInfo().getDeviceLocator().getLabLocator().getHostName())
        .setUiStatus(uiStatus)
        .build();
  }

  private DeviceConfig convertToFeDeviceConfig(
      @SuppressWarnings("unused") DeviceData deviceData,
      @SuppressWarnings("unused") boolean isHostManaged) {
    // TODO: Implement proto conversion, considering shared mode.
    // Use deviceData.getEffectiveDeviceConfig() here.
    return DeviceConfig.getDefaultInstance();
  }

  private DeviceConfigUiStatus calculateUiStatus(
      @SuppressWarnings("unused") Environment.EnvironmentType envType) {
    // TODO: Implement UI status logic based on envType.
    return DeviceConfigUiStatus.getDefaultInstance();
  }
}
