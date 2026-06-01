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

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.Futures.transformAsync;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigPusherHelper;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UnlockHostPropertiesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UnlockHostPropertiesResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the UnlockHostProperties RPC. */
@Singleton
public final class UnlockHostPropertiesHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ConfigurationProvider configurationProvider;
  private final ConfigPusherHelper configPusherHelper;
  private final ListeningExecutorService executor;

  @Inject
  UnlockHostPropertiesHandler(
      ConfigurationProvider configurationProvider,
      ConfigPusherHelper configPusherHelper,
      ListeningExecutorService executor) {
    this.configurationProvider = configurationProvider;
    this.configPusherHelper = configPusherHelper;
    this.executor = executor;
  }

  public ListenableFuture<UnlockHostPropertiesResponse> unlockHostProperties(
      UnlockHostPropertiesRequest request, UniverseScope universe) {
    String hostName = request.getHostName();
    logger.atInfo().log("Unlocking host properties for %s", hostName);

    return transformAsync(
        configurationProvider.getLabConfig(hostName, universe),
        labConfigResult -> {
          Optional<LabConfig> existingConfigOpt = labConfigResult.config();
          if (existingConfigOpt.isEmpty()) {
            return immediateFuture(
                UnlockHostPropertiesResponse.newBuilder()
                    .setSuccess(false)
                    .setError(
                        UpdateError.newBuilder()
                            .setCode(UpdateError.Code.VALIDATION_ERROR)
                            .setMessage("Host config not found: " + hostName))
                    .build());
          }
          LabConfig existingConfig = existingConfigOpt.get();

          LabConfig.Builder builder = existingConfig.toBuilder();
          boolean modified = configPusherHelper.unlockHostProperties(builder);

          if (!modified) {
            logger.atInfo().log("Host %s was not locked, nothing to do.", hostName);
            return immediateFuture(
                UnlockHostPropertiesResponse.newBuilder().setSuccess(true).build());
          }

          return transform(
              configurationProvider.updateLabConfig(hostName, builder.build(), universe),
              unused -> UnlockHostPropertiesResponse.newBuilder().setSuccess(true).build(),
              executor);
        },
        executor);
  }
}
