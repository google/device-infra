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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DeviceEnrichment;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsHostKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/**
 * The standalone ATS fleet data source: the local ATS controller's own fleet, enriched with device
 * WiFi SSIDs from the config service.
 *
 * <p>This is the {@link Fleet#FLEET_SELF} source in the OSS build. It pulls the core fleet from
 * {@code LabInfoService} using masks derived from {@link AtsDeviceKeyRegistry} and {@link
 * AtsHostKeyRegistry}, then asks the config service for the WiFi SSID of every device it saw. When
 * the config service is unavailable (for example the OSS server is not wired to a config server),
 * the config lookup returns no configs and the fleet is served from lab data alone.
 *
 * <p>The config lookup depends on the device ids from the lab data, so the two reads chain rather
 * than run in parallel; the chain is composed asynchronously ({@link #pull()} never blocks).
 */
public final class AtsFleetDataSource implements FleetDataSource {

  private final LabInfoFleetPuller labInfoFleetPuller;
  private final AtsDeviceKeyRegistry deviceKeyRegistry;
  private final AtsHostKeyRegistry hostKeyRegistry;
  private final ConfigurationProvider configurationProvider;
  private final ListeningExecutorService executor;

  @Inject
  AtsFleetDataSource(
      LabInfoFleetPuller labInfoFleetPuller,
      AtsDeviceKeyRegistry deviceKeyRegistry,
      AtsHostKeyRegistry hostKeyRegistry,
      ConfigurationProvider configurationProvider,
      ListeningExecutorService executor) {
    this.labInfoFleetPuller = labInfoFleetPuller;
    this.deviceKeyRegistry = deviceKeyRegistry;
    this.hostKeyRegistry = hostKeyRegistry;
    this.configurationProvider = configurationProvider;
    this.executor = executor;
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
        labData ->
            Futures.transform(
                configurationProvider.getDeviceConfigs(deviceIds(labData), UniverseScope.SELF),
                deviceConfigs ->
                    CoreFleetRawData.builder()
                        .setLabData(labData)
                        .setDeviceEnrichments(deviceEnrichments(deviceConfigs))
                        .build(),
                executor),
        executor);
  }

  @Override
  public ListenableFuture<DimensionOverlayRaw> pullDimension(String keyId) {
    return labInfoFleetPuller.pullDimension(keyId);
  }

  @Override
  public ListenableFuture<ImmutableSet<String>> pullDimensionNames() {
    return labInfoFleetPuller.pullDimensionNames(UniverseScope.SELF);
  }

  /** Enumerates the device ids in the lab query result, in lab then device order. */
  private static ImmutableList<String> deviceIds(LabQueryResult labData) {
    if (!labData.hasLabView()) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<String> deviceIds = ImmutableList.builder();
    for (LabData lab : labData.getLabView().getLabDataList()) {
      for (DeviceInfo device : lab.getDeviceList().getDeviceInfoList()) {
        String deviceId = device.getDeviceLocator().getId();
        if (!deviceId.isEmpty()) {
          deviceIds.add(deviceId);
        }
      }
    }
    return deviceIds.build();
  }

  /**
   * Maps each device config that carries a WiFi SSID to a {@link DeviceEnrichment}, keyed by the
   * config's device UUID. {@code DeviceConfig.uuid} is the same id enumerated from the lab query
   * result, so the builder can join each device to its enrichment. Configs with no SSID are dropped
   * so they leave no empty index entry.
   */
  private static ImmutableMap<String, DeviceEnrichment> deviceEnrichments(
      List<DeviceConfig> deviceConfigs) {
    ImmutableMap.Builder<String, DeviceEnrichment> deviceEnrichments = ImmutableMap.builder();
    for (DeviceConfig deviceConfig : deviceConfigs) {
      String deviceId = deviceConfig.getUuid();
      String ssid = deviceConfig.getBasicConfig().getDefaultWifi().getSsid();
      if (!deviceId.isEmpty() && !ssid.isEmpty()) {
        deviceEnrichments.put(
            deviceId, DeviceEnrichment.builder().setWifiSsid(Optional.of(ssid)).build());
      }
    }
    return deviceEnrichments.buildKeepingLast();
  }
}
