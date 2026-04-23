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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.fe.v6.service.host.builder.RemoteControlUrlBuilder;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DeviceProxyType;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RemoteControlDeviceConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RemoteControlDevicesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RemoteControlDevicesResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class RemoteControlDevicesHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private RemoteControlUrlBuilder remoteControlUrlBuilder;
  @Bind private ListeningExecutorService executorService = newDirectExecutorService();

  @Inject private RemoteControlDevicesHandler handler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void remoteControlDevices_success() throws Exception {
    String hostName = "test_host";
    String deviceId = "device_1";
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceLocator(
                DeviceLocator.newBuilder()
                    .setId(deviceId)
                    .setLabLocator(LabLocator.newBuilder().setHostName(hostName).setIp("1.2.3.4")))
            .build();
    GetLabInfoResponse labInfoResponse =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setLabView(
                        LabView.newBuilder()
                            .addLabData(
                                LabData.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder().addDeviceInfo(deviceInfo)))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(labInfoResponse));
    String expectedUrl = "https://example.com/expected";
    when(remoteControlUrlBuilder.generateRemoteControlUrl(any(), any(), any()))
        .thenReturn(Optional.of(expectedUrl));

    RemoteControlDevicesRequest request =
        RemoteControlDevicesRequest.newBuilder()
            .setHostName(hostName)
            .addDeviceConfigs(
                RemoteControlDeviceConfig.newBuilder().setDeviceId(deviceId).setRunAs("user1"))
            .setDurationSeconds(3600)
            .setProxyType(DeviceProxyType.ADB_AND_VIDEO)
            .build();

    RemoteControlDevicesResponse response =
        handler.remoteControlDevices(request, UniverseScope.SELF).get();

    assertThat(response.getSessionsCount()).isEqualTo(1);
    assertThat(response.getSessions(0).getDeviceId()).isEqualTo(deviceId);
    assertThat(response.getSessions(0).getSessionUrl()).isEqualTo(expectedUrl);
    verify(remoteControlUrlBuilder)
        .generateRemoteControlUrl(deviceInfo, request.getDeviceConfigs(0), request);
  }

  @Test
  public void remoteControlDevices_notSupported_returnsError() throws Exception {
    String hostName = "test_host";
    String deviceId = "device_1";
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceLocator(
                DeviceLocator.newBuilder()
                    .setId(deviceId)
                    .setLabLocator(LabLocator.newBuilder().setHostName(hostName)))
            .build();
    GetLabInfoResponse labInfoResponse =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setLabView(
                        LabView.newBuilder()
                            .addLabData(
                                LabData.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder().addDeviceInfo(deviceInfo)))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(labInfoResponse));
    when(remoteControlUrlBuilder.generateRemoteControlUrl(any(), any(), any()))
        .thenReturn(Optional.empty());

    RemoteControlDevicesRequest request =
        RemoteControlDevicesRequest.newBuilder()
            .setHostName(hostName)
            .addDeviceConfigs(RemoteControlDeviceConfig.newBuilder().setDeviceId(deviceId))
            .build();

    RemoteControlDevicesResponse response =
        handler.remoteControlDevices(request, UniverseScope.SELF).get();

    assertThat(response.getSessionsCount()).isEqualTo(1);
    assertThat(response.getSessions(0).getErrorMessage()).contains("not supported");
  }

  @Test
  public void remoteControlDevices_duplicateDeviceIDs_handlesSuccessfully() throws Exception {
    String hostName = "test_host";
    String deviceId = "device_1";
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceLocator(
                DeviceLocator.newBuilder()
                    .setId(deviceId)
                    .setLabLocator(LabLocator.newBuilder().setHostName(hostName)))
            .build();
    // Device info appears twice in the lab info response.
    GetLabInfoResponse labInfoResponse =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setLabView(
                        LabView.newBuilder()
                            .addLabData(
                                LabData.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder()
                                            .addDeviceInfo(deviceInfo)
                                            .addDeviceInfo(deviceInfo)))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(labInfoResponse));
    when(remoteControlUrlBuilder.generateRemoteControlUrl(any(), any(), any()))
        .thenReturn(Optional.of("https://example.com"));

    RemoteControlDevicesRequest request =
        RemoteControlDevicesRequest.newBuilder()
            .setHostName(hostName)
            .addDeviceConfigs(RemoteControlDeviceConfig.newBuilder().setDeviceId(deviceId))
            .build();

    RemoteControlDevicesResponse response =
        handler.remoteControlDevices(request, UniverseScope.SELF).get();

    assertThat(response.getSessionsCount()).isEqualTo(1);
    assertThat(response.getSessions(0).getDeviceId()).isEqualTo(deviceId);
  }

  @Test
  public void remoteControlDevices_deviceNotFound_returnsError() throws Exception {
    String hostName = "test_host";
    String deviceId = "device_1";
    GetLabInfoResponse labInfoResponse = GetLabInfoResponse.getDefaultInstance();
    when(labInfoProvider.getLabInfoAsync(any(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(labInfoResponse));

    RemoteControlDevicesRequest request =
        RemoteControlDevicesRequest.newBuilder()
            .setHostName(hostName)
            .addDeviceConfigs(RemoteControlDeviceConfig.newBuilder().setDeviceId(deviceId))
            .build();

    RemoteControlDevicesResponse response =
        handler.remoteControlDevices(request, UniverseScope.SELF).get();

    assertThat(response.getSessionsCount()).isEqualTo(1);
    assertThat(response.getSessions(0).getDeviceId()).isEqualTo(deviceId);
    assertThat(response.getSessions(0).getErrorMessage()).contains("not found");
  }
}
