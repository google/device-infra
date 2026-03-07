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
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigConverter;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigUiStatus;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetHostConfig RPC. */
@Singleton
public final class GetHostConfigHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ConfigurationProvider configurationProvider;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final ListeningExecutorService executor;

  @Inject
  GetHostConfigHandler(
      ConfigurationProvider configurationProvider,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      ListeningExecutorService executor) {
    this.configurationProvider = configurationProvider;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.executor = executor;
  }

  public ListenableFuture<GetHostConfigResponse> getHostConfig(GetHostConfigRequest request) {
    logger.atInfo().log("Getting host config for %s", request.getHostName());
    ConfigServiceCapability configServiceCapability =
        configServiceCapabilityFactory.create(request.getUniverse());
    try {
      configServiceCapability.checkConfigServiceAvailability();
    } catch (UnsupportedOperationException e) {
      return immediateFailedFuture(e);
    }

    return Futures.transform(
        configurationProvider.getLabConfig(request.getHostName(), request.getUniverse()),
        labConfigOpt -> {
          if (labConfigOpt.isEmpty()) {
            return GetHostConfigResponse.getDefaultInstance();
          }
          LabConfig labConfig = labConfigOpt.get();
          HostConfig hostConfig = ConfigConverter.toFeHostConfig(labConfig);
          HostConfigUiStatus uiStatus = configServiceCapability.calculateHostUiStatus();

          return GetHostConfigResponse.newBuilder()
              .setHostConfig(hostConfig)
              .setUiStatus(uiStatus)
              .build();
        },
        executor);
  }
}
