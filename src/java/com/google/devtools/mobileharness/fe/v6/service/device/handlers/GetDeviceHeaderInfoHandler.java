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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.query.proto.FilterProto;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.fe.v6.service.device.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceHeaderInfoRequest;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetDeviceHeaderInfo RPC. */
@Singleton
public final class GetDeviceHeaderInfoHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LabInfoProvider labInfoProvider;
  private final ConfigurationProvider configurationProvider;
  private final ListeningExecutorService executor;
  private final DeviceHeaderInfoBuilder deviceHeaderInfoBuilder;

  @Inject
  GetDeviceHeaderInfoHandler(
      LabInfoProvider labInfoProvider,
      ConfigurationProvider configurationProvider,
      ListeningExecutorService executor,
      DeviceHeaderInfoBuilder deviceHeaderInfoBuilder) {
    this.labInfoProvider = labInfoProvider;
    this.configurationProvider = configurationProvider;
    this.executor = executor;
    this.deviceHeaderInfoBuilder = deviceHeaderInfoBuilder;
  }

  public ListenableFuture<DeviceHeaderInfo> getDeviceHeaderInfo(
      GetDeviceHeaderInfoRequest request) {
    String universe = request.getUniverse().isEmpty() ? "google_1p" : request.getUniverse();
    String deviceId = request.getId();
    String key = universe + ":" + deviceId;
    logger.atInfo().log("Loading device header info for %s", key);

    // 1. Fetch DeviceInfo
    logger.atInfo().log("Fetching DeviceInfo for %s", key);
    ListenableFuture<GetLabInfoResponse> getLabInfoResponseFuture =
        labInfoProvider.getLabInfoAsync(createGetLabInfoRequest(deviceId), universe);

    ListenableFuture<DeviceInfo> deviceInfoFuture =
        Futures.transform(
            getLabInfoResponseFuture,
            response -> {
              logger.atInfo().log("Received DeviceInfo response for %s", key);
              return response
                  .getLabQueryResult()
                  .getDeviceView()
                  .getGroupedDevices()
                  .getDeviceList()
                  .getDeviceInfoList()
                  .stream()
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new RuntimeException(
                              "Device not found: " + deviceId + " in universe: " + universe));
            },
            executor);

    // 2. Start ConfigProvider fetches early
    logger.atInfo().log("Fetching DeviceConfig for %s", key);
    ListenableFuture<Optional<DeviceConfig>> deviceConfigFuture =
        configurationProvider.getDeviceConfig(deviceId, universe);

    // 3. Fetch LabConfig, depends on DeviceInfo
    ListenableFuture<Optional<LabConfig>> labConfigFuture =
        Futures.transformAsync(
            deviceInfoFuture,
            deviceInfo -> {
              String hostName = deviceInfo.getDeviceLocator().getLabLocator().getHostName();
              logger.atInfo().log("Fetching LabConfig for host %s for key %s", hostName, key);
              return hostName.isEmpty()
                  ? immediateFuture(Optional.empty())
                  : configurationProvider.getLabConfig(hostName, universe);
            },
            executor);

    // 4. Combine and build
    return Futures.whenAllSucceed(deviceInfoFuture, labConfigFuture)
        .callAsync(
            () -> {
              logger.atInfo().log("All base futures succeeded for %s", key);
              DeviceInfo deviceInfo = Futures.getDone(deviceInfoFuture);
              Optional<LabConfig> labConfigOpt = Futures.getDone(labConfigFuture);

              final ListenableFuture<Optional<DeviceConfig>> finalDeviceConfigFuture;
              if (isHostConfigUsed(labConfigOpt)) {
                logger.atInfo().log("Using host config for %s", key);
                deviceConfigFuture.cancel(false);
                finalDeviceConfigFuture = immediateFuture(Optional.empty());
              } else {
                logger.atInfo().log("Using device config for %s", key);
                finalDeviceConfigFuture = deviceConfigFuture;
              }

              return Futures.transform(
                  finalDeviceConfigFuture,
                  deviceConfigOpt -> {
                    logger.atInfo().log("Building DeviceHeaderInfo for %s", key);
                    return deviceHeaderInfoBuilder.buildDeviceHeaderInfo(
                        deviceInfo, deviceConfigOpt, labConfigOpt);
                  },
                  executor);
            },
            executor);
  }

  private boolean isHostConfigUsed(Optional<LabConfig> labConfigOpt) {
    return labConfigOpt
        .map(
            labConfig ->
                labConfig.getHostProperties().getHostPropertyList().stream()
                    .anyMatch(
                        p ->
                            p.getKey().equals("device_config_mode") && p.getValue().equals("host")))
        .orElse(false);
  }

  private GetLabInfoRequest createGetLabInfoRequest(String deviceId) {
    return GetLabInfoRequest.newBuilder()
        .setLabQuery(
            LabQuery.newBuilder()
                .setFilter(
                    Filter.newBuilder()
                        .setDeviceFilter(
                            FilterProto.DeviceFilter.newBuilder()
                                .addDeviceMatchCondition(
                                    FilterProto.DeviceFilter.DeviceMatchCondition.newBuilder()
                                        .setDeviceUuidMatchCondition(
                                            FilterProto.DeviceFilter.DeviceMatchCondition
                                                .DeviceUuidMatchCondition.newBuilder()
                                                .setCondition(
                                                    FilterProto.StringMatchCondition.newBuilder()
                                                        .setInclude(
                                                            FilterProto.StringMatchCondition.Include
                                                                .newBuilder()
                                                                .addExpected(deviceId)))))))
                .setDeviceViewRequest(LabQuery.DeviceViewRequest.getDefaultInstance()))
        .build();
  }
}
