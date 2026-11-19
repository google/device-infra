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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.BatchHostConfigHelper.HostEvaluation;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateHostWifiConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateHostWifiConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.TargetDeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the BatchUpdateHostWifiConfigs RPC. */
@Singleton
public final class BatchUpdateHostWifiConfigsHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final BatchHostConfigHelper batchHostConfigHelper;
  private final ConfigurationProvider configurationProvider;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final Environment environment;
  private final ListeningExecutorService executor;

  @Inject
  BatchUpdateHostWifiConfigsHandler(
      BatchHostConfigHelper batchHostConfigHelper,
      ConfigurationProvider configurationProvider,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      Environment environment,
      ListeningExecutorService executor) {
    this.batchHostConfigHelper = batchHostConfigHelper;
    this.configurationProvider = configurationProvider;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.environment = environment;
    this.executor = executor;
  }

  public ListenableFuture<BatchUpdateHostWifiConfigsResponse> batchUpdateHostWifiConfigs(
      BatchUpdateHostWifiConfigsRequest request,
      UniverseScope universe,
      Optional<String> username) {
    logger.atInfo().log(
        "BatchUpdateHostWifiConfigsRequest for %d hosts", request.getHostNamesCount());

    try {
      configServiceCapabilityFactory.create(universe).checkConfigServiceAvailability();
    } catch (UnsupportedOperationException e) {
      if (request.getHostNamesList().isEmpty()) {
        return immediateFailedFuture(e);
      }
      return immediateFuture(
          buildAllFailedResponse(
              request.getHostNamesList(), UpdateError.Code.VALIDATION_ERROR, e.getMessage()));
    }

    Optional<Optional<DeviceWifi>> targetWifiOpt = parseTargetWifi(request.getTarget());
    if (targetWifiOpt.isEmpty()) {
      return immediateFailedFuture(
          FeServiceException.invalidArgument(
              "Target Wi-Fi must be set (clearWifi, or wifi with a non-blank ssid)."));
    }
    Optional<String> invalidReason =
        BatchHostConfigHelper.checkHostCount(request.getHostNamesList());
    if (invalidReason.isPresent()) {
      return immediateFailedFuture(FeServiceException.invalidArgument(invalidReason.get()));
    }

    Optional<DeviceWifi> targetWifi = targetWifiOpt.get();

    return FluentFuture.from(
            batchHostConfigHelper.evaluateHosts(request.getHostNamesList(), universe, username))
        .transformAsync(
            evaluations -> applyHostWifiUpdates(evaluations, targetWifi, universe), executor);
  }

  private record HostUpdateTask(String hostName, LabConfig updatedConfig) {}

  private record HostUpdateOutcome(String hostName, Optional<UpdateError> error) {}

  // BasicDeviceConfig.default_wifi is cleared on ATS host Wi-Fi updates so LabConfig.wifi_config
  // remains authoritative.
  @SuppressWarnings("deprecation")
  private ListenableFuture<BatchUpdateHostWifiConfigsResponse> applyHostWifiUpdates(
      List<HostEvaluation> evaluations, Optional<DeviceWifi> targetWifi, UniverseScope universe) {
    Map<String, UpdateError> errors = new LinkedHashMap<>();
    ImmutableList.Builder<HostUpdateTask> tasksBuilder = ImmutableList.builder();

    for (HostEvaluation evaluation : evaluations) {
      String hostName = evaluation.hostName();
      if (!evaluation.writable()) {
        errors.put(
            hostName,
            UpdateError.newBuilder()
                .setCode(evaluation.errorCode())
                .setMessage(evaluation.readOnlyReason())
                .build());
        continue;
      }

      LabConfig.Builder labBuilder =
          evaluation
              .existingLabConfig()
              .map(LabConfig::toBuilder)
              .orElseGet(() -> LabConfig.newBuilder().setHostName(hostName));
      if (labBuilder.getHostName().isEmpty()) {
        labBuilder.setHostName(hostName);
      }

      if (targetWifi.isPresent()) {
        DeviceWifi wifi = targetWifi.get();
        labBuilder.setWifiConfig(
            WifiConfig.newBuilder()
                .setSsid(wifi.getSsid())
                .setPsk(wifi.getPsk())
                .setScanSsid(wifi.getScanSsid()));
      } else {
        labBuilder.clearWifiConfig();
      }
      if (environment.isAts() && labBuilder.hasDefaultDeviceConfig()) {
        labBuilder.getDefaultDeviceConfigBuilder().clearDefaultWifi();
      }

      tasksBuilder.add(new HostUpdateTask(hostName, labBuilder.build()));
    }

    ImmutableList<HostUpdateTask> tasks = tasksBuilder.build();
    if (tasks.isEmpty()) {
      return immediateFuture(
          BatchUpdateHostWifiConfigsResponse.newBuilder().putAllErrors(errors).build());
    }

    return Futures.transform(
        BatchHostConfigHelper.inWaves(tasks, task -> updateSingleHost(task, universe)),
        outcomes -> {
          outcomes.stream()
              .filter(outcome -> outcome.error().isPresent())
              .forEach(outcome -> errors.put(outcome.hostName(), outcome.error().get()));
          return BatchUpdateHostWifiConfigsResponse.newBuilder().putAllErrors(errors).build();
        },
        executor);
  }

  private ListenableFuture<HostUpdateOutcome> updateSingleHost(
      HostUpdateTask task, UniverseScope universe) {
    ListenableFuture<Void> updateFuture =
        configurationProvider.updateLabConfig(task.hostName(), task.updatedConfig(), universe);
    return FluentFuture.from(
            Futures.whenAllSucceed(updateFuture)
                .call(() -> new HostUpdateOutcome(task.hostName(), Optional.empty()), executor))
        .catching(
            Throwable.class,
            t -> {
              logger.atWarning().withCause(t).log(
                  "Failed to update host Wi-Fi configuration for %s", task.hostName());
              String message =
                  !isNullOrEmpty(t.getMessage())
                      ? t.getMessage()
                      : "Failed to update host Wi-Fi configuration.";
              return new HostUpdateOutcome(
                  task.hostName(),
                  Optional.of(
                      UpdateError.newBuilder()
                          .setCode(UpdateError.Code.UNKNOWN)
                          .setMessage(message)
                          .build()));
            },
            executor);
  }

  private static Optional<Optional<DeviceWifi>> parseTargetWifi(TargetDeviceWifi target) {
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

  private static BatchUpdateHostWifiConfigsResponse buildAllFailedResponse(
      List<String> hostNames, UpdateError.Code code, String message) {
    UpdateError error = UpdateError.newBuilder().setCode(code).setMessage(message).build();
    return BatchUpdateHostWifiConfigsResponse.newBuilder()
        .putAllErrors(
            BatchHostConfigHelper.distinctHostNames(hostNames).stream()
                .collect(toImmutableMap(host -> host, host -> error)))
        .build();
  }
}
