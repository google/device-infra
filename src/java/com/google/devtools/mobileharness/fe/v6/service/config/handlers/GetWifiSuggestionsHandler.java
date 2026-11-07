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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.primitives.Booleans.falseFirst;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.Comparator.comparingInt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.fe.v6.service.config.util.BatchDeviceConfigUtil;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsResponse.CandidateWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsResponse.CuratedWifiPreset;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsResponse.FleetWifiInUse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.WifiCredentialsStore;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetWifiSuggestions RPC. */
@Singleton
public final class GetWifiSuggestionsHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String DEVICE_CONFIG_MODE_KEY = "device_config_mode";
  private static final String HOST_MODE_VALUE = "host";

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
      GetWifiSuggestionsRequest request) {
    logger.atInfo().log("GetWifiSuggestionsRequest: %s", request);

    if (!configServiceCapabilityFactory.create(UniverseScope.SELF).isConfigServiceAvailable()) {
      return immediateFuture(GetWifiSuggestionsResponse.getDefaultInstance());
    }

    if (environment.isGoogleInternal()) {
      return getWifiSuggestionsFromStore();
    }
    return getWifiSuggestionsFromFleetConfigs();
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

  private ListenableFuture<GetWifiSuggestionsResponse> getWifiSuggestionsFromFleetConfigs() {
    ListenableFuture<GetLabInfoResponse> labInfoFuture =
        labInfoProvider.getLabInfoAsync(GetLabInfoRequest.getDefaultInstance(), UniverseScope.SELF);
    return Futures.transformAsync(
        labInfoFuture,
        labInfoResponse -> {
          ImmutableList<String> hostNames = extractFleetHostNames(labInfoResponse);
          ImmutableList<String> deviceIds =
              BatchDeviceConfigUtil.extractFleetDeviceIds(labInfoResponse);
          if (hostNames.isEmpty() && deviceIds.isEmpty()) {
            return immediateFuture(GetWifiSuggestionsResponse.getDefaultInstance());
          }
          ListenableFuture<ImmutableMap<String, LabConfig>> labConfigsFuture =
              fetchLabConfigs(hostNames);
          ListenableFuture<List<DeviceConfig>> deviceConfigsFuture =
              deviceIds.isEmpty()
                  ? immediateFuture(ImmutableList.of())
                  : Futures.catching(
                      configurationProvider.getDeviceConfigs(deviceIds, UniverseScope.SELF),
                      Throwable.class,
                      e -> {
                        logger.atWarning().withCause(e).log(
                            "Failed to fetch DeviceConfigs for GetWifiSuggestions.");
                        return ImmutableList.of();
                      },
                      executor);
          return Futures.whenAllSucceed(labConfigsFuture, deviceConfigsFuture)
              .call(
                  () ->
                      aggregateFleetWifis(
                          labInfoResponse,
                          Futures.getDone(labConfigsFuture),
                          Futures.getDone(deviceConfigsFuture)),
                  executor);
        },
        executor);
  }

  private ListenableFuture<ImmutableMap<String, LabConfig>> fetchLabConfigs(
      ImmutableList<String> hostNames) {
    if (hostNames.isEmpty()) {
      return immediateFuture(ImmutableMap.of());
    }
    ImmutableList<ListenableFuture<Optional<Map.Entry<String, LabConfig>>>> entryFutures =
        hostNames.stream()
            .map(
                hostName ->
                    Futures.transform(
                        Futures.catching(
                            configurationProvider.getLabConfig(hostName, UniverseScope.SELF),
                            Throwable.class,
                            e -> {
                              logger.atWarning().withCause(e).log(
                                  "Failed to fetch LabConfig for host %s in GetWifiSuggestions.",
                                  hostName);
                              return ConfigResult.<LabConfig>unavailable();
                            },
                            executor),
                        result -> result.config().map(config -> Map.entry(hostName, config)),
                        executor))
            .collect(toImmutableList());

    return Futures.transform(
        Futures.allAsList(entryFutures),
        entries ->
            entries.stream()
                .flatMap(Optional::stream)
                .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b)),
        executor);
  }

  private static ImmutableList<String> extractFleetHostNames(GetLabInfoResponse response) {
    if (!response.getLabQueryResult().hasLabView()) {
      return ImmutableList.of();
    }
    return response.getLabQueryResult().getLabView().getLabDataList().stream()
        .map(labData -> labData.getLabInfo().getLabLocator().getHostName())
        .filter(hostName -> !hostName.isEmpty())
        .distinct()
        .collect(toImmutableList());
  }

  private static GetWifiSuggestionsResponse aggregateFleetWifis(
      GetLabInfoResponse labInfoResponse,
      ImmutableMap<String, LabConfig> labConfigsByHost,
      List<DeviceConfig> deviceConfigs) {
    ImmutableMap<String, DeviceConfig> deviceConfigsById =
        deviceConfigs.stream()
            .filter(config -> !config.getUuid().isEmpty())
            .collect(toImmutableMap(DeviceConfig::getUuid, config -> config, (a, b) -> b));

    Map<DeviceWifi, Long> counts = new LinkedHashMap<>();

    // Seed configured host-level Wi-Fi networks with count 0 so zero-device hosts still surface.
    for (LabConfig labConfig : labConfigsByHost.values()) {
      Optional<DeviceWifi> hostWifi = resolveHostWifi(labConfig);
      hostWifi.ifPresent(wifi -> counts.putIfAbsent(wifi, 0L));
    }

    if (labInfoResponse.getLabQueryResult().hasLabView()) {
      for (LabData labData : labInfoResponse.getLabQueryResult().getLabView().getLabDataList()) {
        String hostName = labData.getLabInfo().getLabLocator().getHostName();
        LabConfig labConfig =
            labConfigsByHost.getOrDefault(hostName, LabConfig.getDefaultInstance());
        for (DeviceInfo deviceInfo : labData.getDeviceList().getDeviceInfoList()) {
          String deviceId = deviceInfo.getDeviceLocator().getId();
          if (deviceId.isEmpty()) {
            continue;
          }
          Optional<DeviceWifi> effectiveWifi =
              resolveEffectiveDeviceWifi(
                  labConfig, Optional.ofNullable(deviceConfigsById.get(deviceId)));
          effectiveWifi.ifPresent(wifi -> counts.merge(wifi, 1L, Long::sum));
        }
      }
    }

    Comparator<CandidateWifi> comparator =
        comparingInt((CandidateWifi c) -> c.getFleetInUse().getDeviceCount())
            .reversed()
            .thenComparing(c -> c.getWifi().getSsid())
            .thenComparing(c -> c.getWifi().getPsk())
            .thenComparing(c -> c.getWifi().getScanSsid(), falseFirst());

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

  // BasicDeviceConfig.default_wifi is read as a fallback beneath LabConfig.wifi_config.
  @SuppressWarnings("deprecation")
  private static Optional<DeviceWifi> resolveHostWifi(LabConfig labConfig) {
    if (!labConfig.getWifiConfig().getSsid().isEmpty()) {
      return Optional.of(toDeviceWifi(labConfig.getWifiConfig()));
    }
    if (!labConfig.getDefaultDeviceConfig().getDefaultWifi().getSsid().isEmpty()) {
      return Optional.of(toDeviceWifi(labConfig.getDefaultDeviceConfig().getDefaultWifi()));
    }
    return Optional.empty();
  }

  // BasicDeviceConfig.default_wifi is read as a fallback beneath LabConfig.wifi_config.
  @SuppressWarnings("deprecation")
  private static Optional<DeviceWifi> resolveEffectiveDeviceWifi(
      LabConfig labConfig, Optional<DeviceConfig> deviceConfig) {
    if (!labConfig.getWifiConfig().getSsid().isEmpty()) {
      return Optional.of(toDeviceWifi(labConfig.getWifiConfig()));
    }
    if (isHostMode(labConfig)) {
      return nonEmptyDeviceWifi(labConfig.getDefaultDeviceConfig().getDefaultWifi());
    }
    if (deviceConfig.isPresent()) {
      return nonEmptyDeviceWifi(deviceConfig.get().getBasicConfig().getDefaultWifi());
    }
    return nonEmptyDeviceWifi(labConfig.getDefaultDeviceConfig().getDefaultWifi());
  }

  private static Optional<DeviceWifi> nonEmptyDeviceWifi(WifiConfig wifi) {
    return wifi.getSsid().isEmpty() ? Optional.empty() : Optional.of(toDeviceWifi(wifi));
  }

  private static DeviceWifi toDeviceWifi(WifiConfig wifi) {
    return DeviceWifi.newBuilder()
        .setSsid(wifi.getSsid())
        .setPsk(wifi.getPsk())
        .setScanSsid(wifi.getScanSsid())
        .build();
  }

  private static boolean isHostMode(LabConfig labConfig) {
    return labConfig.getHostProperties().getHostPropertyList().stream()
        .anyMatch(
            property ->
                property.getKey().equals(DEVICE_CONFIG_MODE_KEY)
                    && property.getValue().equals(HOST_MODE_VALUE));
  }
}
