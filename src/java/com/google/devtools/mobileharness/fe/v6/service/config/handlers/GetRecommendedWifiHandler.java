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
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetRecommendedWifiRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetRecommendedWifiResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.RecommendedWifi;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.WifiCredentialsStore;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.ArrayList;
import java.util.List;
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
      return getRecommendedWifiFromDeviceConfigs(universe);
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

  /** Scenario 3: Aggregates Wi-Fi credentials from individual device configurations. */
  private ListenableFuture<GetRecommendedWifiResponse> getRecommendedWifiFromDeviceConfigs(
      UniverseScope universeScope) {
    GetLabInfoRequest request = GetLabInfoRequest.getDefaultInstance();
    ListenableFuture<GetLabInfoResponse> labInfoFuture =
        labInfoProvider.getLabInfoAsync(request, universeScope);

    return Futures.transformAsync(
        labInfoFuture,
        labInfoResponse -> {
          List<String> deviceIds = extractDeviceIds(labInfoResponse);
          return fetchAndAggregateWifi(deviceIds, universeScope);
        },
        executor);
  }

  private List<String> extractDeviceIds(GetLabInfoResponse response) {
    List<String> deviceIds = new ArrayList<>();
    if (response.getLabQueryResult().hasLabView()) {
      for (LabData labData : response.getLabQueryResult().getLabView().getLabDataList()) {
        for (DeviceInfo deviceInfo : labData.getDeviceList().getDeviceInfoList()) {
          if (deviceInfo.hasDeviceLocator()) {
            deviceIds.add(deviceInfo.getDeviceLocator().getId());
          }
        }
      }
    }
    return deviceIds;
  }

  private ListenableFuture<GetRecommendedWifiResponse> fetchAndAggregateWifi(
      List<String> deviceIds, UniverseScope universeScope) {
    if (deviceIds.isEmpty()) {
      return immediateFuture(GetRecommendedWifiResponse.getDefaultInstance());
    }
    ListenableFuture<List<DeviceConfig>> configsFuture =
        configurationProvider.getDeviceConfigs(deviceIds, universeScope);
    return Futures.transform(
        configsFuture,
        configs -> {
          ImmutableList<RecommendedWifi> recommendations =
              configs.stream()
                  .filter(config -> config.getBasicConfig().hasDefaultWifi())
                  .map(config -> config.getBasicConfig().getDefaultWifi())
                  .filter(wifi -> !wifi.getSsid().isEmpty())
                  .distinct()
                  .map(
                      wifi ->
                          RecommendedWifi.newBuilder()
                              .setSsid(wifi.getSsid())
                              .setPsk(wifi.getPsk())
                              .build())
                  .collect(toImmutableList());

          return GetRecommendedWifiResponse.newBuilder()
              .addAllRecommendations(recommendations)
              .build();
        },
        executor);
  }
}
