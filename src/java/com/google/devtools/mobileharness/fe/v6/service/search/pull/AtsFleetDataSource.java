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

package com.google.devtools.mobileharness.fe.v6.service.search.pull;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DeviceEnrichment;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.FleetRefreshExecutor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsHostKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

/**
 * The standalone ATS fleet data source: the local ATS controller's own fleet, enriched with device
 * WiFi SSIDs from the config service.
 *
 * <p>This is the {@link Fleet#FLEET_SELF} source in the OSS build. It pulls the core fleet from
 * {@code LabInfoService} using masks derived from {@link AtsDeviceKeyRegistry} and {@link
 * AtsHostKeyRegistry}, then queries {@code ConfigService} for each host's {@link LabConfig} and
 * each device's {@link DeviceConfig} to resolve effective WiFi SSIDs using the same precedence as
 * {@code ApiConfigV5} (host {@code LabConfig.wifi_config}, then shared-mode host default WiFi, then
 * per-device {@code DeviceConfig} falling back to host default WiFi). When the config service is
 * unavailable, config lookups degrade gracefully and the fleet is served from lab data alone.
 *
 * <p>The config lookups depend on the host names and device ids from the lab data, so they chain
 * after {@code GetLabInfo} and run in parallel with each other ({@link #pull()} never blocks).
 */
public final class AtsFleetDataSource implements FleetDataSource {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String DEVICE_CONFIG_MODE_KEY = "device_config_mode";
  private static final String HOST_MODE_VALUE = "host";

  private final LabInfoFleetPuller labInfoFleetPuller;
  private final AtsDeviceKeyRegistry deviceKeyRegistry;
  private final AtsHostKeyRegistry hostKeyRegistry;
  private final ConfigurationProvider configurationProvider;
  private final ListeningExecutorService refreshExecutor;

  @Inject
  AtsFleetDataSource(
      LabInfoFleetPuller labInfoFleetPuller,
      AtsDeviceKeyRegistry deviceKeyRegistry,
      AtsHostKeyRegistry hostKeyRegistry,
      ConfigurationProvider configurationProvider,
      @FleetRefreshExecutor ListeningExecutorService refreshExecutor) {
    this.labInfoFleetPuller = labInfoFleetPuller;
    this.deviceKeyRegistry = deviceKeyRegistry;
    this.hostKeyRegistry = hostKeyRegistry;
    this.configurationProvider = configurationProvider;
    this.refreshExecutor = refreshExecutor;
  }

  @Override
  public Fleet fleet() {
    return Fleet.FLEET_SELF;
  }

  @Override
  public ListenableFuture<CoreFleetRawData> pull() {
    return Futures.transformAsync(
        labInfoFleetPuller.pull(
            deviceKeyRegistry.deriveDeviceInfoMask(), hostKeyRegistry.deriveLabInfoMask()),
        this::enrichWithWifiConfigs,
        refreshExecutor);
  }

  private ListenableFuture<CoreFleetRawData> enrichWithWifiConfigs(LabQueryResult labData) {
    ImmutableList<String> deviceIds = deviceIds(labData);
    ImmutableList<String> hostNames = hostNames(labData);

    ListenableFuture<List<DeviceConfig>> deviceConfigsFuture =
        deviceIds.isEmpty()
            ? immediateFuture(ImmutableList.of())
            : Futures.catching(
                configurationProvider.getDeviceConfigs(deviceIds, UniverseScope.SELF),
                Throwable.class,
                e -> {
                  logger.atWarning().withCause(e).log(
                      "Failed to fetch DeviceConfigs for ATS fleet Wi-Fi enrichment.");
                  return ImmutableList.of();
                },
                refreshExecutor);

    ListenableFuture<ImmutableMap<String, LabConfig>> labConfigsFuture = fetchLabConfigs(hostNames);

    return Futures.whenAllSucceed(deviceConfigsFuture, labConfigsFuture)
        .call(
            () ->
                CoreFleetRawData.builder()
                    .setLabData(labData)
                    .setDeviceEnrichments(
                        deviceEnrichments(
                            labData,
                            Futures.getDone(deviceConfigsFuture),
                            Futures.getDone(labConfigsFuture)))
                    .build(),
            refreshExecutor);
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
                                  "Failed to fetch LabConfig for host %s during ATS fleet Wi-Fi"
                                      + " enrichment.",
                                  hostName);
                              return ConfigResult.<LabConfig>unavailable();
                            },
                            refreshExecutor),
                        result -> result.config().map(config -> Map.entry(hostName, config)),
                        refreshExecutor))
            .collect(toImmutableList());

    return Futures.transform(
        Futures.allAsList(entryFutures),
        entries ->
            entries.stream()
                .flatMap(Optional::stream)
                .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b)),
        refreshExecutor);
  }

  @Override
  public ListenableFuture<DimensionOverlayRaw> pullDimension(DeviceKeyDescriptor dimensionKey) {
    return labInfoFleetPuller.pullDimension(dimensionKey);
  }

  @Override
  public ListenableFuture<ImmutableMap<String, DimensionOverlayRaw>> pullDimensions(
      Set<DeviceKeyDescriptor> dimensionKeys) {
    return labInfoFleetPuller.pullDimensions(dimensionKeys);
  }

  @Override
  public ListenableFuture<ImmutableMap<String, Integer>> pullDimensionCounts() {
    return labInfoFleetPuller.pullDimensionCounts(UniverseScope.SELF);
  }

  /** Enumerates distinct non-empty host names in the lab query result. */
  private static ImmutableList<String> hostNames(LabQueryResult labData) {
    if (!labData.hasLabView()) {
      return ImmutableList.of();
    }
    return labData.getLabView().getLabDataList().stream()
        .map(lab -> lab.getLabInfo().getLabLocator().getHostName())
        .filter(hostName -> !hostName.isEmpty())
        .distinct()
        .collect(toImmutableList());
  }

  /** Enumerates the device ids in the lab query result, in lab then device order. */
  private static ImmutableList<String> deviceIds(LabQueryResult labData) {
    if (!labData.hasLabView()) {
      return ImmutableList.of();
    }
    return labData.getLabView().getLabDataList().stream()
        .flatMap(lab -> lab.getDeviceList().getDeviceInfoList().stream())
        .map(device -> device.getDeviceLocator().getId())
        .filter(deviceId -> !deviceId.isEmpty())
        .collect(toImmutableList());
  }

  /**
   * Maps each device in {@code labData} that resolves to a non-empty WiFi SSID to a {@link
   * DeviceEnrichment}, keyed by device UUID. Devices with no effective SSID are omitted so they
   * leave no empty index entry.
   */
  private static ImmutableMap<String, DeviceEnrichment> deviceEnrichments(
      LabQueryResult labData,
      List<DeviceConfig> deviceConfigs,
      ImmutableMap<String, LabConfig> labConfigsByHost) {
    if (!labData.hasLabView()) {
      return ImmutableMap.of();
    }
    ImmutableMap<String, DeviceConfig> deviceConfigsById =
        deviceConfigs.stream()
            .filter(config -> !config.getUuid().isEmpty())
            .collect(toImmutableMap(DeviceConfig::getUuid, config -> config, (a, b) -> b));

    ImmutableMap.Builder<String, DeviceEnrichment> deviceEnrichments = ImmutableMap.builder();
    for (LabData lab : labData.getLabView().getLabDataList()) {
      String hostName = lab.getLabInfo().getLabLocator().getHostName();
      LabConfig labConfig = labConfigsByHost.getOrDefault(hostName, LabConfig.getDefaultInstance());
      for (DeviceInfo device : lab.getDeviceList().getDeviceInfoList()) {
        String deviceId = device.getDeviceLocator().getId();
        if (deviceId.isEmpty()) {
          continue;
        }
        String ssid =
            resolveEffectiveWifiSsid(
                labConfig, Optional.ofNullable(deviceConfigsById.get(deviceId)));
        if (!ssid.isEmpty()) {
          deviceEnrichments.put(
              deviceId, DeviceEnrichment.builder().setWifiSsid(Optional.of(ssid)).build());
        }
      }
    }
    return deviceEnrichments.buildKeepingLast();
  }

  // BasicDeviceConfig.default_wifi is read as a fallback beneath LabConfig.wifi_config.
  @SuppressWarnings("deprecation")
  private static String resolveEffectiveWifiSsid(
      LabConfig labConfig, Optional<DeviceConfig> deviceConfig) {
    String hostWifiSsid = labConfig.getWifiConfig().getSsid();
    if (!hostWifiSsid.isEmpty()) {
      return hostWifiSsid;
    }
    if (isHostMode(labConfig)) {
      return labConfig.getDefaultDeviceConfig().getDefaultWifi().getSsid();
    }
    if (deviceConfig.isPresent()) {
      return deviceConfig.get().getBasicConfig().getDefaultWifi().getSsid();
    }
    return labConfig.getDefaultDeviceConfig().getDefaultWifi().getSsid();
  }

  private static boolean isHostMode(LabConfig labConfig) {
    return labConfig.getHostProperties().getHostPropertyList().stream()
        .anyMatch(
            property ->
                property.getKey().equals(DEVICE_CONFIG_MODE_KEY)
                    && property.getValue().equals(HOST_MODE_VALUE));
  }
}
