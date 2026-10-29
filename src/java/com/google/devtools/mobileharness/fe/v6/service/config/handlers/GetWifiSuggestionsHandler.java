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
import static java.util.Comparator.comparingInt;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.util.BatchDeviceConfigUtil;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsResponse.CandidateWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsResponse.CuratedWifiPreset;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsResponse.FleetWifiInUse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.WifiCredentialsStore;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetWifiSuggestions RPC. */
@Singleton
public final class GetWifiSuggestionsHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LabInfoProvider labInfoProvider;
  private final ConfigurationProvider configurationProvider;
  private final WifiCredentialsStore wifiCredentialsStore;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final Environment environment;
  private final ListeningExecutorService executor;

  @Inject
  GetWifiSuggestionsHandler(
      LabInfoProvider labInfoProvider,
      ConfigurationProvider configurationProvider,
      WifiCredentialsStore wifiCredentialsStore,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      Environment environment,
      ListeningExecutorService executor) {
    this.labInfoProvider = labInfoProvider;
    this.configurationProvider = configurationProvider;
    this.wifiCredentialsStore = wifiCredentialsStore;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.environment = environment;
    this.executor = executor;
  }

  public ListenableFuture<GetWifiSuggestionsResponse> getWifiSuggestions(
      GetWifiSuggestionsRequest request, UniverseScope universe) {
    logger.atInfo().log("GetWifiSuggestionsRequest: %s", request);

    if (!configServiceCapabilityFactory.create(universe).isConfigServiceAvailable()) {
      return immediateFuture(GetWifiSuggestionsResponse.getDefaultInstance());
    }

    if (environment.isGoogleInternal()) {
      return getWifiSuggestionsFromStore();
    }
    return getWifiSuggestionsFromDeviceConfigs(universe);
  }

  private ListenableFuture<GetWifiSuggestionsResponse> getWifiSuggestionsFromStore() {
    return Futures.transform(
        wifiCredentialsStore.read(),
        store -> {
          ImmutableList<CandidateWifi> candidateWifis =
              store.getEntriesList().stream()
                  .filter(entry -> !entry.getSsid().isEmpty())
                  .map(GetWifiSuggestionsHandler::toCuratedCandidate)
                  .collect(toImmutableList());
          return GetWifiSuggestionsResponse.newBuilder()
              .addAllCandidateWifis(candidateWifis)
              .build();
        },
        directExecutor());
  }

  private static CandidateWifi toCuratedCandidate(WifiCredentialEntry entry) {
    return CandidateWifi.newBuilder()
        .setWifi(
            DeviceWifi.newBuilder()
                .setSsid(entry.getSsid())
                .setPsk(entry.getPsk())
                .setScanSsid(false))
        .setCuratedPreset(CuratedWifiPreset.getDefaultInstance())
        .build();
  }

  private ListenableFuture<GetWifiSuggestionsResponse> getWifiSuggestionsFromDeviceConfigs(
      UniverseScope universe) {
    ListenableFuture<GetLabInfoResponse> labInfoFuture =
        labInfoProvider.getLabInfoAsync(GetLabInfoRequest.getDefaultInstance(), universe);
    return Futures.transformAsync(
        labInfoFuture,
        labInfoResponse -> {
          ImmutableList<String> deviceIds =
              BatchDeviceConfigUtil.extractFleetDeviceIds(labInfoResponse);
          if (deviceIds.isEmpty()) {
            return immediateFuture(GetWifiSuggestionsResponse.getDefaultInstance());
          }
          return Futures.transform(
              configurationProvider.getDeviceConfigs(deviceIds, universe),
              GetWifiSuggestionsHandler::aggregateFleetWifis,
              executor);
        },
        executor);
  }

  private static GetWifiSuggestionsResponse aggregateFleetWifis(List<DeviceConfig> configs) {
    Map<DeviceWifi, Long> counts =
        configs.stream()
            .filter(config -> config.getBasicConfig().hasDefaultWifi())
            .map(config -> config.getBasicConfig().getDefaultWifi())
            .filter(wifi -> !wifi.getSsid().isEmpty())
            .map(
                wifi ->
                    DeviceWifi.newBuilder()
                        .setSsid(wifi.getSsid())
                        .setPsk(wifi.getPsk())
                        .setScanSsid(wifi.getScanSsid())
                        .build())
            .collect(groupingBy(identity(), counting()));

    Comparator<CandidateWifi> comparator =
        comparingInt((CandidateWifi c) -> c.getFleetInUse().getDeviceCount())
            .reversed()
            .thenComparing(c -> c.getWifi().getSsid());

    ImmutableList<CandidateWifi> candidateWifis =
        counts.entrySet().stream()
            .map(
                entry ->
                    CandidateWifi.newBuilder()
                        .setWifi(entry.getKey())
                        .setFleetInUse(
                            FleetWifiInUse.newBuilder()
                                .setDeviceCount(
                                    (int) Math.min(entry.getValue(), Integer.MAX_VALUE)))
                        .build())
            .sorted(comparator)
            .collect(toImmutableList());

    return GetWifiSuggestionsResponse.newBuilder().addAllCandidateWifis(candidateWifis).build();
  }
}
