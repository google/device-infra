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
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.BatchDeviceConfigHelper.DeviceEvaluation;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceWifiConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceWifiConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.TargetDeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the BatchUpdateDeviceWifiConfigs RPC. */
@Singleton
public final class BatchUpdateDeviceWifiConfigsHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final BatchDeviceConfigHelper batchDeviceConfigHelper;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final ListeningExecutorService executor;

  @Inject
  BatchUpdateDeviceWifiConfigsHandler(
      BatchDeviceConfigHelper batchDeviceConfigHelper,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      ListeningExecutorService executor) {
    this.batchDeviceConfigHelper = batchDeviceConfigHelper;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.executor = executor;
  }

  public ListenableFuture<BatchUpdateDeviceWifiConfigsResponse> batchUpdateDeviceWifiConfigs(
      BatchUpdateDeviceWifiConfigsRequest request, Optional<String> username) {
    logger.atInfo().log(
        "BatchUpdateDeviceWifiConfigsRequest for %d devices", request.getDevicesCount());

    try {
      configServiceCapabilityFactory.create(UniverseScope.SELF).checkConfigServiceAvailability();
    } catch (UnsupportedOperationException e) {
      return immediateFailedFuture(FeServiceException.failedPrecondition(e.getMessage()));
    }

    Optional<Optional<DeviceWifi>> targetWifiOpt = parseTargetWifi(request);
    if (targetWifiOpt.isEmpty()) {
      return immediateFailedFuture(
          FeServiceException.invalidArgument(
              "Target Wi-Fi must be set (clearWifi, or wifi with a non-blank ssid)."));
    }
    Optional<String> invalidReason =
        BatchDeviceConfigHelper.checkDeviceTargetCount(request.getDevicesList());
    if (invalidReason.isPresent()) {
      return immediateFailedFuture(FeServiceException.invalidArgument(invalidReason.get()));
    }

    Optional<DeviceWifi> targetWifi = targetWifiOpt.get();

    return FluentFuture.from(
            batchDeviceConfigHelper.evaluateDevices(request.getDevicesList(), username))
        .transformAsync(evaluations -> applyWifiUpdates(evaluations, targetWifi), executor);
  }

  private ListenableFuture<BatchUpdateDeviceWifiConfigsResponse> applyWifiUpdates(
      List<DeviceEvaluation> evaluations, Optional<DeviceWifi> targetWifi) {
    return FluentFuture.from(
            batchDeviceConfigHelper.commitDeviceConfigUpdates(
                evaluations,
                existingConfig -> {
                  BasicDeviceConfig.Builder basicBuilder =
                      existingConfig.getBasicConfig().toBuilder();
                  if (targetWifi.isPresent()) {
                    DeviceWifi wifi = targetWifi.get();
                    basicBuilder.setDefaultWifi(
                        WifiConfig.newBuilder()
                            .setSsid(wifi.getSsid())
                            .setPsk(wifi.getPsk())
                            .setScanSsid(wifi.getScanSsid()));
                  } else {
                    basicBuilder.clearDefaultWifi();
                  }
                  return existingConfig.toBuilder().setBasicConfig(basicBuilder.build()).build();
                },
                "Failed to update device Wi-Fi configuration."))
        .transform(
            errors ->
                BatchUpdateDeviceWifiConfigsResponse.newBuilder().putAllErrors(errors).build(),
            executor);
  }

  /**
   * Returns {@code Optional.empty()} when {@code target} is unset or invalid, {@code
   * Optional.of(Optional.empty())} for {@code clear_wifi}, or {@code
   * Optional.of(Optional.of(wifi))} for a valid target network.
   */
  private static Optional<Optional<DeviceWifi>> parseTargetWifi(
      BatchUpdateDeviceWifiConfigsRequest request) {
    TargetDeviceWifi target = request.getTarget();
    return switch (target.getTargetCase()) {
      case CLEAR_WIFI -> Optional.of(Optional.empty());
      case WIFI -> {
        String ssid = target.getWifi().getSsid().trim();
        if (ssid.isEmpty()) {
          yield Optional.empty();
        }
        yield Optional.of(
            Optional.of(
                DeviceWifi.newBuilder()
                    .setSsid(ssid)
                    .setPsk(target.getWifi().getPsk())
                    .setScanSsid(target.getWifi().getScanSsid())
                    .build()));
      }
      case TARGET_NOT_SET -> Optional.empty();
    };
  }
}
