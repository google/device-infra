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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.BatchHostConfigHelper.HostEvaluation;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetHostWifiConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetHostWifiConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetHostWifiConfigsResponse.HostWifiConfigEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigChangeStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.TargetDeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the BatchGetHostWifiConfigs RPC. */
@Singleton
public final class BatchGetHostWifiConfigsHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final BatchHostConfigHelper batchHostConfigHelper;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final ListeningExecutorService executor;

  @Inject
  BatchGetHostWifiConfigsHandler(
      BatchHostConfigHelper batchHostConfigHelper,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      ListeningExecutorService executor) {
    this.batchHostConfigHelper = batchHostConfigHelper;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.executor = executor;
  }

  public ListenableFuture<BatchGetHostWifiConfigsResponse> batchGetHostWifiConfigs(
      BatchGetHostWifiConfigsRequest request, UniverseScope universe, Optional<String> username) {
    logger.atInfo().log("BatchGetHostWifiConfigsRequest for %d hosts", request.getHostNamesCount());

    try {
      configServiceCapabilityFactory.create(universe).checkConfigServiceAvailability();
    } catch (UnsupportedOperationException e) {
      return immediateFailedFuture(e);
    }

    Optional<String> invalidReason =
        BatchHostConfigHelper.checkHostCount(request.getHostNamesList());
    if (invalidReason.isPresent()) {
      return immediateFailedFuture(FeServiceException.invalidArgument(invalidReason.get()));
    }

    ParsedTargetWifi parsedTarget = parseTargetWifi(request.getTarget());

    return Futures.transform(
        batchHostConfigHelper.evaluateHosts(request.getHostNamesList(), universe, username),
        evaluations -> {
          ImmutableList<HostWifiConfigEntry> entries =
              evaluations.stream()
                  .map(evaluation -> buildEntry(evaluation, parsedTarget))
                  .collect(toImmutableList());
          return BatchGetHostWifiConfigsResponse.newBuilder().addAllEntries(entries).build();
        },
        executor);
  }

  private static HostWifiConfigEntry buildEntry(
      HostEvaluation evaluation, ParsedTargetWifi parsedTarget) {
    DeviceWifi currentWifi =
        BatchHostConfigHelper.extractCurrentHostWifi(evaluation.existingLabConfig());

    HostConfigChangeStatus status;
    if (!evaluation.writable()) {
      status = HostConfigChangeStatus.HOST_CONFIG_CHANGE_STATUS_READ_ONLY;
    } else if (parsedTarget.mode == TargetMode.UNSET) {
      status = HostConfigChangeStatus.HOST_CONFIG_CHANGE_STATUS_WRITABLE;
    } else if (parsedTarget.mode == TargetMode.CLEAR) {
      status =
          currentWifi.getSsid().isEmpty()
              ? HostConfigChangeStatus.HOST_CONFIG_CHANGE_STATUS_ALREADY_MATCHES
              : HostConfigChangeStatus.HOST_CONFIG_CHANGE_STATUS_WILL_UPDATE;
    } else {
      status =
          currentWifi.equals(parsedTarget.wifi)
              ? HostConfigChangeStatus.HOST_CONFIG_CHANGE_STATUS_ALREADY_MATCHES
              : HostConfigChangeStatus.HOST_CONFIG_CHANGE_STATUS_WILL_UPDATE;
    }

    return HostWifiConfigEntry.newBuilder()
        .setHostName(evaluation.hostName())
        .setCurrentWifi(currentWifi)
        .setStatus(status)
        .setReadOnlyReason(evaluation.readOnlyReason())
        .build();
  }

  private enum TargetMode {
    UNSET,
    CLEAR,
    WIFI
  }

  private record ParsedTargetWifi(TargetMode mode, DeviceWifi wifi) {}

  private static ParsedTargetWifi parseTargetWifi(TargetDeviceWifi target) {
    return switch (target.getTargetCase()) {
      case CLEAR_WIFI -> new ParsedTargetWifi(TargetMode.CLEAR, DeviceWifi.getDefaultInstance());
      case WIFI -> {
        String ssid = target.getWifi().getSsid().trim();
        if (ssid.isEmpty()) {
          yield new ParsedTargetWifi(TargetMode.UNSET, DeviceWifi.getDefaultInstance());
        }
        yield new ParsedTargetWifi(
            TargetMode.WIFI,
            DeviceWifi.newBuilder()
                .setSsid(ssid)
                .setPsk(target.getWifi().getPsk())
                .setScanSsid(target.getWifi().getScanSsid())
                .build());
      }
      case TARGET_NOT_SET ->
          new ParsedTargetWifi(TargetMode.UNSET, DeviceWifi.getDefaultInstance());
    };
  }
}
