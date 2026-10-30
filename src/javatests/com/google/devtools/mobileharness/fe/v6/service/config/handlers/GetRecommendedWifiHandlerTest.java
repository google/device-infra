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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialStore;
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
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class GetRecommendedWifiHandlerTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private LabInfoProvider labInfoProvider;
  @Mock private ConfigurationProvider configurationProvider;
  @Mock private Environment environment;
  @Mock private WifiCredentialsStore wifiCredentialsStore;

  private final ListeningExecutorService executorService = newDirectExecutorService();
  private GetRecommendedWifiHandler handler;

  @Before
  public void setUp() {
    handler =
        new GetRecommendedWifiHandler(
            labInfoProvider,
            configurationProvider,
            executorService,
            wifiCredentialsStore,
            environment);
  }

  @Test
  public void getRecommendedWifi_unsupportedUniverse_returnsEmpty() throws Exception {
    when(environment.isGoogleInternal()).thenReturn(false);
    when(labInfoProvider.getLabInfoAsync(any(GetLabInfoRequest.class), any(UniverseScope.class)))
        .thenReturn(immediateFuture(GetLabInfoResponse.getDefaultInstance()));

    GetRecommendedWifiRequest request =
        GetRecommendedWifiRequest.newBuilder().setUniverse("unsupported").build();
    GetRecommendedWifiResponse response =
        handler.getRecommendedWifi(request, new UniverseScope.RoutedUniverse("unsupported")).get();
    assertThat(response.getRecommendationsList()).isEmpty();
  }

  @Test
  public void getRecommendedWifi_google1p_readsFromStore() throws Exception {
    when(environment.isGoogleInternal()).thenReturn(true);
    WifiCredentialStore store =
        WifiCredentialStore.newBuilder()
            .addEntries(WifiCredentialEntry.newBuilder().setSsid("GoogleGuest").setPsk("guest-psk"))
            .addEntries(WifiCredentialEntry.newBuilder().setSsid("GoogleCorp").setPsk("corp-psk"))
            .build();
    when(wifiCredentialsStore.read()).thenReturn(immediateFuture(store));

    GetRecommendedWifiRequest request =
        GetRecommendedWifiRequest.newBuilder().setUniverse("google_1p").build();
    GetRecommendedWifiResponse response =
        handler.getRecommendedWifi(request, UniverseScope.SELF).get();

    assertThat(response.getRecommendationsList()).hasSize(2);
    assertThat(response.getRecommendations(0).getSsid()).isEqualTo("GoogleGuest");
    assertThat(response.getRecommendations(0).getPsk()).isEqualTo("guest-psk");
    assertThat(response.getRecommendations(1).getSsid()).isEqualTo("GoogleCorp");
    assertThat(response.getRecommendations(1).getPsk()).isEqualTo("corp-psk");
  }

  @Test
  @SuppressWarnings("deprecation") // Exercises fallback to BasicDeviceConfig.default_wifi.
  public void getRecommendedWifi_oss_aggregatesFromDeviceConfigs() throws Exception {
    when(labInfoProvider.getLabInfoAsync(any(GetLabInfoRequest.class), any(UniverseScope.class)))
        .thenReturn(
            immediateFuture(
                GetLabInfoResponse.newBuilder()
                    .setLabQueryResult(
                        LabQueryResult.newBuilder()
                            .setLabView(
                                LabView.newBuilder()
                                    .addLabData(
                                        LabData.newBuilder()
                                            .setDeviceList(
                                                DeviceList.newBuilder()
                                                    .addDeviceInfo(
                                                        DeviceInfo.newBuilder()
                                                            .setDeviceLocator(
                                                                DeviceLocator.newBuilder()
                                                                    .setId("device1")))))))
                    .build()));

    DeviceConfig config =
        DeviceConfig.newBuilder()
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setDefaultWifi(WifiConfig.newBuilder().setSsid("ssid1").setPsk("psk1")))
            .build();

    when(configurationProvider.getDeviceConfigs(any(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(ImmutableList.of(config)));

    GetRecommendedWifiRequest request =
        GetRecommendedWifiRequest.newBuilder().setUniverse("oss").build();
    GetRecommendedWifiResponse response =
        handler.getRecommendedWifi(request, new UniverseScope.RoutedUniverse("oss")).get();

    assertThat(response.getRecommendationsList())
        .containsExactly(RecommendedWifi.newBuilder().setSsid("ssid1").setPsk("psk1").build());
  }

  @Test
  public void getRecommendedWifi_oss_zeroDeviceHostWithWifiConfig_returnsHostWifi()
      throws Exception {
    when(environment.isGoogleInternal()).thenReturn(false);
    when(labInfoProvider.getLabInfoAsync(any(GetLabInfoRequest.class), any(UniverseScope.class)))
        .thenReturn(
            immediateFuture(
                GetLabInfoResponse.newBuilder()
                    .setLabQueryResult(
                        LabQueryResult.newBuilder()
                            .setLabView(
                                LabView.newBuilder()
                                    .addLabData(
                                        LabData.newBuilder()
                                            .setLabInfo(
                                                LabInfo.newBuilder()
                                                    .setLabLocator(
                                                        LabLocator.newBuilder()
                                                            .setHostName("host-empty"))))))
                    .build()));
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setHostName("host-empty")
            .setWifiConfig(WifiConfig.newBuilder().setSsid("ats-host-wifi").setPsk("ats-psk"))
            .build();
    when(configurationProvider.getLabConfig(eq("host-empty"), any(UniverseScope.class)))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(labConfig))));

    GetRecommendedWifiResponse response =
        handler
            .getRecommendedWifi(GetRecommendedWifiRequest.getDefaultInstance(), UniverseScope.SELF)
            .get();

    assertThat(response.getRecommendationsList())
        .containsExactly(
            RecommendedWifi.newBuilder().setSsid("ats-host-wifi").setPsk("ats-psk").build());
    verify(configurationProvider, never()).getDeviceConfigs(any(), any(UniverseScope.class));
  }

  @Test
  @SuppressWarnings("deprecation") // Exercises fallback to BasicDeviceConfig.default_wifi.
  public void getRecommendedWifi_oss_prioritizesHostWifiDeduplicatesAndToleratesHostFailure()
      throws Exception {
    when(environment.isGoogleInternal()).thenReturn(false);
    when(labInfoProvider.getLabInfoAsync(any(GetLabInfoRequest.class), any(UniverseScope.class)))
        .thenReturn(
            immediateFuture(
                GetLabInfoResponse.newBuilder()
                    .setLabQueryResult(
                        LabQueryResult.newBuilder()
                            .setLabView(
                                LabView.newBuilder()
                                    .addLabData(
                                        LabData.newBuilder()
                                            .setLabInfo(
                                                LabInfo.newBuilder()
                                                    .setLabLocator(
                                                        LabLocator.newBuilder()
                                                            .setHostName("host-a")))
                                            .setDeviceList(
                                                DeviceList.newBuilder()
                                                    .addDeviceInfo(
                                                        DeviceInfo.newBuilder()
                                                            .setDeviceLocator(
                                                                DeviceLocator.newBuilder()
                                                                    .setId("device-1")))))
                                    .addLabData(
                                        LabData.newBuilder()
                                            .setLabInfo(
                                                LabInfo.newBuilder()
                                                    .setLabLocator(
                                                        LabLocator.newBuilder()
                                                            .setHostName("host-b"))))
                                    .addLabData(
                                        LabData.newBuilder()
                                            .setLabInfo(
                                                LabInfo.newBuilder()
                                                    .setLabLocator(
                                                        LabLocator.newBuilder()
                                                            .setHostName("host-failing"))))))
                    .build()));

    LabConfig hostAConfig =
        LabConfig.newBuilder()
            .setHostName("host-a")
            .setWifiConfig(WifiConfig.newBuilder().setSsid("host-net").setPsk("psk-1"))
            .build();
    LabConfig hostBConfig =
        LabConfig.newBuilder()
            .setHostName("host-b")
            .setWifiConfig(
                WifiConfig.newBuilder().setSsid("host-net").setPsk("psk-1").setScanSsid(true))
            .setDefaultDeviceConfig(
                BasicDeviceConfig.newBuilder()
                    .setDefaultWifi(
                        WifiConfig.newBuilder().setSsid("legacy-host-net").setPsk("psk-2")))
            .build();
    when(configurationProvider.getLabConfig(eq("host-a"), any(UniverseScope.class)))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(hostAConfig))));
    when(configurationProvider.getLabConfig(eq("host-b"), any(UniverseScope.class)))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(hostBConfig))));
    when(configurationProvider.getLabConfig(eq("host-failing"), any(UniverseScope.class)))
        .thenReturn(immediateFailedFuture(new RuntimeException("RPC error")));

    DeviceConfig deviceConfig =
        DeviceConfig.newBuilder()
            .setUuid("device-1")
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setDefaultWifi(WifiConfig.newBuilder().setSsid("device-net").setPsk("psk-3")))
            .build();
    when(configurationProvider.getDeviceConfigs(eq(ImmutableList.of("device-1")), any()))
        .thenReturn(immediateFuture(ImmutableList.of(deviceConfig)));

    GetRecommendedWifiResponse response =
        handler
            .getRecommendedWifi(GetRecommendedWifiRequest.getDefaultInstance(), UniverseScope.SELF)
            .get();

    assertThat(response.getRecommendationsList())
        .containsExactly(
            RecommendedWifi.newBuilder().setSsid("host-net").setPsk("psk-1").build(),
            RecommendedWifi.newBuilder().setSsid("legacy-host-net").setPsk("psk-2").build(),
            RecommendedWifi.newBuilder().setSsid("device-net").setPsk("psk-3").build())
        .inOrder();
  }
}
