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
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialStore;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetRecommendedWifiRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetRecommendedWifiResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.WifiCredentialsStore;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.Arrays;
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
    when(configurationProvider.getDeviceConfigs(any(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(Arrays.asList()));

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
  public void getRecommendedWifi_oss_aggregatesFromDeviceConfigs() throws Exception {
    // Mock LabInfoProvider
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

    // Mock ConfigurationProvider
    DeviceConfig config =
        DeviceConfig.newBuilder()
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setDefaultWifi(WifiConfig.newBuilder().setSsid("ssid1").setPsk("psk1")))
            .build();

    when(configurationProvider.getDeviceConfigs(any(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(Arrays.asList(config)));

    GetRecommendedWifiRequest request =
        GetRecommendedWifiRequest.newBuilder().setUniverse("oss").build();
    GetRecommendedWifiResponse response =
        handler.getRecommendedWifi(request, new UniverseScope.RoutedUniverse("oss")).get();

    assertThat(response.getRecommendationsList()).hasSize(1);
    assertThat(response.getRecommendations(0).getSsid()).isEqualTo("ssid1");
    assertThat(response.getRecommendations(0).getPsk()).isEqualTo("psk1");
  }
}
