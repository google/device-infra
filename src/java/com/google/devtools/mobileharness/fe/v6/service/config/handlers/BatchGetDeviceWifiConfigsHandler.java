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
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.BatchDeviceConfigHelper.DeviceEvaluation;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceWifiConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceWifiConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceWifiConfigsResponse.DeviceWifiConfigEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigChangeStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.TargetDeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the BatchGetDeviceWifiConfigs RPC. */
@Singleton
public final class BatchGetDeviceWifiConfigsHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final BatchDeviceConfigHelper batchDeviceConfigHelper;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final ListeningExecutorService executor;

  @Inject
  BatchGetDeviceWifiConfigsHandler(
      BatchDeviceConfigHelper batchDeviceConfigHelper,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      ListeningExecutorService executor) {
    this.batchDeviceConfigHelper = batchDeviceConfigHelper;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.executor = executor;
  }

  public ListenableFuture<BatchGetDeviceWifiConfigsResponse> batchGetDeviceWifiConfigs(
      BatchGetDeviceWifiConfigsRequest request, UniverseScope universe, Optional<String> username) {
    logger.atInfo().log(
        "BatchGetDeviceWifiConfigsRequest for %d devices", request.getDeviceIdsCount());

    try {
      configServiceCapabilityFactory.create(universe).checkConfigServiceAvailability();
    } catch (UnsupportedOperationException e) {
      return immediateFailedFuture(e);
    }
    Optional<String> invalidReason =
        BatchDeviceConfigHelper.checkDeviceIdCount(request.getDeviceIdsList());
    if (invalidReason.isPresent()) {
      return immediateFailedFuture(FeServiceException.invalidArgument(invalidReason.get()));
    }

    ParsedTargetWifi parsedTarget = parseTargetWifi(request);

    return Futures.transform(
        batchDeviceConfigHelper.evaluateDevices(request.getDeviceIdsList(), universe, username),
        evaluations -> {
          ImmutableList<DeviceWifiConfigEntry> entries =
              evaluations.stream()
                  .map(evaluation -> buildEntry(evaluation, parsedTarget))
                  .collect(toImmutableList());
          return BatchGetDeviceWifiConfigsResponse.newBuilder().addAllEntries(entries).build();
        },
        executor);
  }

  private static DeviceWifiConfigEntry buildEntry(
      DeviceEvaluation evaluation, ParsedTargetWifi parsedTarget) {
    DeviceWifi currentWifi =
        extractCurrentWifi(evaluation.effectiveDeviceConfig().getBasicConfig());

    DeviceConfigChangeStatus status;
    if (!evaluation.writable()) {
      status = DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY;
    } else if (parsedTarget.mode == TargetMode.UNSET) {
      status = DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE;
    } else if (parsedTarget.mode == TargetMode.CLEAR) {
      status =
          currentWifi.getSsid().isEmpty()
              ? DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES
              : DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE;
    } else {
      status =
          currentWifi.equals(parsedTarget.wifi)
              ? DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES
              : DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE;
    }

    return DeviceWifiConfigEntry.newBuilder()
        .setDeviceId(evaluation.deviceId())
        .setCurrentWifi(currentWifi)
        .setStatus(status)
        .setReadOnlyReason(evaluation.readOnlyReason())
        .build();
  }

  private static DeviceWifi extractCurrentWifi(BasicDeviceConfig basicConfig) {
    if (!basicConfig.hasDefaultWifi()) {
      return DeviceWifi.getDefaultInstance();
    }
    WifiConfig wifi = basicConfig.getDefaultWifi();
    return DeviceWifi.newBuilder()
        .setSsid(wifi.getSsid())
        .setPsk(wifi.getPsk())
        .setScanSsid(wifi.getScanSsid())
        .build();
  }

  private enum TargetMode {
    UNSET,
    CLEAR,
    WIFI
  }

  private record ParsedTargetWifi(TargetMode mode, DeviceWifi wifi) {}

  private static ParsedTargetWifi parseTargetWifi(BatchGetDeviceWifiConfigsRequest request) {
    TargetDeviceWifi target = request.getTarget();
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
