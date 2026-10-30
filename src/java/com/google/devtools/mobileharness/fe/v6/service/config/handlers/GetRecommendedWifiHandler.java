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
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetRecommendedWifiRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetRecommendedWifiResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.RecommendedWifi;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.WifiCredentialsStore;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetRecommendedWifi RPC. */
@Singleton
public final class GetRecommendedWifiHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LabInfoProvider labInfoProvider;
  private final ConfigurationProvider configurationProvider;
  private final ListeningExecutorService executor;
  private final WifiCredentialsStore wifiCredentialsStore;
  private final Environment environment;

  @Inject
  GetRecommendedWifiHandler(
      LabInfoProvider labInfoProvider,
      ConfigurationProvider configurationProvider,
      ListeningExecutorService executor,
      WifiCredentialsStore wifiCredentialsStore,
      Environment environment) {
    this.labInfoProvider = labInfoProvider;
    this.configurationProvider = configurationProvider;
    this.executor = executor;
    this.wifiCredentialsStore = wifiCredentialsStore;
    this.environment = environment;
  }

  public ListenableFuture<GetRecommendedWifiResponse> getRecommendedWifi(
      GetRecommendedWifiRequest request, UniverseScope universe) {
    logger.atInfo().log("GetRecommendedWifiRequest: %s", request);

    if (environment.isGoogleInternal()) {
      if (universe instanceof UniverseScope.SelfUniverse) {
        return getRecommendedWifiFromStore();
      } else {
        return immediateFuture(GetRecommendedWifiResponse.getDefaultInstance());
      }
    } else {
      return getRecommendedWifiFromFleetConfigs(universe);
    }
  }

  /**
   * Scenario 1: Reads Wi-Fi credentials from the centralized store.
   *
   * <p>The credentials are stored as plaintext proto with transparent encryption at rest.
   */
  private ListenableFuture<GetRecommendedWifiResponse> getRecommendedWifiFromStore() {
    // TODO: wifiCredentialsStore.read() returns a ListenableFuture but its internal
    // implementation (e.g., CNS encrypted file read) might block the calling thread during
    // setup or initialization. Verify if we need to offload the call itself to the executor.
    return Futures.transform(
        wifiCredentialsStore.read(),
        store -> {
          GetRecommendedWifiResponse.Builder responseBuilder =
              GetRecommendedWifiResponse.newBuilder();
          for (WifiCredentialEntry entry : store.getEntriesList()) {
            responseBuilder.addRecommendations(
                RecommendedWifi.newBuilder().setSsid(entry.getSsid()).setPsk(entry.getPsk()));
          }
          logger.atInfo().log(
              "GetRecommendedWifi (store): returning %d credentials", store.getEntriesCount());
          return responseBuilder.build();
        },
        directExecutor());
  }

  /** Scenario 3: Aggregates Wi-Fi credentials from host and device configurations in ATS. */
  private ListenableFuture<GetRecommendedWifiResponse> getRecommendedWifiFromFleetConfigs(
      UniverseScope universeScope) {
    ListenableFuture<GetLabInfoResponse> labInfoFuture =
        labInfoProvider.getLabInfoAsync(GetLabInfoRequest.getDefaultInstance(), universeScope);

    return Futures.transformAsync(
        labInfoFuture,
        labInfoResponse ->
            fetchAndAggregateWifi(
                extractHostNames(labInfoResponse),
                extractDeviceIds(labInfoResponse),
                universeScope),
        executor);
  }

  private static ImmutableList<String> extractHostNames(GetLabInfoResponse response) {
    if (!response.getLabQueryResult().hasLabView()) {
      return ImmutableList.of();
    }
    return response.getLabQueryResult().getLabView().getLabDataList().stream()
        .map(labData -> labData.getLabInfo().getLabLocator().getHostName())
        .filter(hostName -> !hostName.isEmpty())
        .distinct()
        .collect(toImmutableList());
  }

  private static ImmutableList<String> extractDeviceIds(GetLabInfoResponse response) {
    if (!response.getLabQueryResult().hasLabView()) {
      return ImmutableList.of();
    }
    return response.getLabQueryResult().getLabView().getLabDataList().stream()
        .flatMap(labData -> labData.getDeviceList().getDeviceInfoList().stream())
        .filter(DeviceInfo::hasDeviceLocator)
        .map(deviceInfo -> deviceInfo.getDeviceLocator().getId())
        .filter(deviceId -> !deviceId.isEmpty())
        .distinct()
        .collect(toImmutableList());
  }

  private ListenableFuture<GetRecommendedWifiResponse> fetchAndAggregateWifi(
      ImmutableList<String> hostNames,
      ImmutableList<String> deviceIds,
      UniverseScope universeScope) {
    if (hostNames.isEmpty() && deviceIds.isEmpty()) {
      return immediateFuture(GetRecommendedWifiResponse.getDefaultInstance());
    }

    ListenableFuture<List<ConfigResult<LabConfig>>> labConfigsFuture =
        hostNames.isEmpty()
            ? immediateFuture(ImmutableList.of())
            : Futures.allAsList(
                hostNames.stream()
                    .map(
                        hostName ->
                            Futures.catching(
                                configurationProvider.getLabConfig(hostName, universeScope),
                                Throwable.class,
                                e -> {
                                  logger.atWarning().withCause(e).log(
                                      "Failed to fetch LabConfig for host %s; skipping Wi-Fi"
                                          + " recommendations.",
                                      hostName);
                                  return ConfigResult.<LabConfig>unavailable();
                                },
                                executor))
                    .collect(toImmutableList()));

    ListenableFuture<List<DeviceConfig>> deviceConfigsFuture =
        deviceIds.isEmpty()
            ? immediateFuture(ImmutableList.of())
            : Futures.catching(
                configurationProvider.getDeviceConfigs(deviceIds, universeScope),
                Throwable.class,
                e -> {
                  logger.atWarning().withCause(e).log(
                      "Failed to fetch DeviceConfigs; skipping device Wi-Fi recommendations.");
                  return ImmutableList.of();
                },
                executor);

    return Futures.whenAllSucceed(labConfigsFuture, deviceConfigsFuture)
        .call(
            () ->
                aggregateRecommendations(
                    Futures.getDone(labConfigsFuture), Futures.getDone(deviceConfigsFuture)),
            executor);
  }

  // BasicDeviceConfig.default_wifi is read as a fallback for legacy host and device configs.
  @SuppressWarnings("deprecation")
  private static GetRecommendedWifiResponse aggregateRecommendations(
      List<ConfigResult<LabConfig>> labConfigResults, List<DeviceConfig> deviceConfigs) {
    ImmutableList<LabConfig> labConfigs =
        labConfigResults.stream()
            .map(ConfigResult::config)
            .flatMap(Optional::stream)
            .collect(toImmutableList());

    Stream<WifiConfig> hostWifiConfigs =
        labConfigs.stream().filter(LabConfig::hasWifiConfig).map(LabConfig::getWifiConfig);
    Stream<WifiConfig> legacyHostDefaultWifis =
        labConfigs.stream()
            .map(LabConfig::getDefaultDeviceConfig)
            .filter(BasicDeviceConfig::hasDefaultWifi)
            .map(BasicDeviceConfig::getDefaultWifi);
    Stream<WifiConfig> legacyDeviceWifis =
        deviceConfigs.stream()
            .map(DeviceConfig::getBasicConfig)
            .filter(BasicDeviceConfig::hasDefaultWifi)
            .map(BasicDeviceConfig::getDefaultWifi);

    ImmutableList<RecommendedWifi> recommendations =
        Streams.concat(hostWifiConfigs, legacyHostDefaultWifis, legacyDeviceWifis)
            .filter(wifi -> !wifi.getSsid().isEmpty())
            .map(
                wifi ->
                    RecommendedWifi.newBuilder()
                        .setSsid(wifi.getSsid())
                        .setPsk(wifi.getPsk())
                        .build())
            .distinct()
            .collect(toImmutableList());

    return GetRecommendedWifiResponse.newBuilder().addAllRecommendations(recommendations).build();
  }
}
