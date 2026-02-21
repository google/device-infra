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
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.config.util.DeviceConfigConverter;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostDefaultDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostDefaultDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetHostDefaultDeviceConfig RPC. */
@Singleton
public final class GetHostDefaultDeviceConfigHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ConfigurationProvider configurationProvider;
  private final ConfigServiceCapability configServiceCapability;
  private final ListeningExecutorService executor;

  @Inject
  GetHostDefaultDeviceConfigHandler(
      ConfigurationProvider configurationProvider,
      ConfigServiceCapability configServiceCapability,
      ListeningExecutorService executor) {
    this.configurationProvider = configurationProvider;
    this.configServiceCapability = configServiceCapability;
    this.executor = executor;
  }

  public ListenableFuture<GetHostDefaultDeviceConfigResponse> getHostDefaultDeviceConfig(
      GetHostDefaultDeviceConfigRequest request) {
    logger.atInfo().log("Getting host default device config for %s", request.getHostName());
    if (!configServiceCapability.isUniverseSupported(request.getUniverse())) {
      throw new UnsupportedOperationException(
          String.format(
              "Configuration operations are not currently supported for universe '%s' in the Google"
                  + " Internal environment.",
              request.getUniverse()));
    }

    return Futures.transform(
        configurationProvider.getLabConfig(request.getHostName(), request.getUniverse()),
        labConfigOpt -> {
          if (labConfigOpt.isEmpty()) {
            return GetHostDefaultDeviceConfigResponse.getDefaultInstance();
          }
          LabConfig labConfig = labConfigOpt.get();
          DeviceConfig.Builder builder = DeviceConfig.newBuilder();
          if (labConfig.hasDefaultDeviceConfig()) {
            builder.mergeFrom(
                DeviceConfigConverter.toFeDeviceConfig(labConfig.getDefaultDeviceConfig()));
          }
          return GetHostDefaultDeviceConfigResponse.newBuilder()
              .setDeviceConfig(builder.build())
              .build();
        },
        executor);
  }
}
