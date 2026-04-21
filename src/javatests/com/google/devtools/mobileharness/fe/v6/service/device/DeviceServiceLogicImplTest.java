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

package com.google.devtools.mobileharness.fe.v6.service.device;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.fe.v6.service.device.handlers.DeviceHeaderInfoBuilder;
import com.google.devtools.mobileharness.fe.v6.service.device.handlers.LogcatActionHelper;
import com.google.devtools.mobileharness.fe.v6.service.device.handlers.TestbedConfigBuilder;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceOverviewPageData;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceHeaderInfoRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetTestbedConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TestbedConfig;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.DeviceData;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.ManagementMode;
import com.google.devtools.mobileharness.fe.v6.service.shared.SubDeviceInfoListFactory;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.infra.master.rpc.stub.JobSyncStub;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class DeviceServiceLogicImplTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private DeviceDataLoader deviceDataLoader;
  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private TestbedConfigBuilder testbedConfigBuilder;
  @Bind @Mock private DeviceHeaderInfoBuilder deviceHeaderInfoBuilder;
  @Bind @Mock private LogcatActionHelper logcatActionHelper;
  @Bind @Mock private SubDeviceInfoListFactory subDeviceInfoListFactory;
  @Bind @Mock private InstantSource instantSource;
  @Bind @Mock private UniverseFactory universeFactory;
  @Bind @Mock private JobSyncStub jobSyncStub;
  @Bind private final ListeningExecutorService executor = newDirectExecutorService();

  private DeviceServiceLogicImpl deviceServiceLogicImpl;

  @Before
  public void setUp() {
    when(universeFactory.create(anyString())).thenReturn(new UniverseScope.SelfUniverse());
    deviceServiceLogicImpl =
        Guice.createInjector(BoundFieldModule.of(this)).getInstance(DeviceServiceLogicImpl.class);
  }

  @Test
  public void getDeviceOverview_success() throws Exception {
    GetDeviceOverviewRequest request =
        GetDeviceOverviewRequest.newBuilder().setId("device").build();
    DeviceData deviceData =
        DeviceData.create(
            DeviceInfo.getDefaultInstance(),
            DeviceConfig.getDefaultInstance(),
            ManagementMode.PER_DEVICE,
            Optional.empty(),
            Optional.empty());

    when(deviceDataLoader.loadDeviceData(anyString(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(deviceData));
    when(deviceHeaderInfoBuilder.buildDeviceHeaderInfo(
            any(), any(), any(), any(UniverseScope.class)))
        .thenReturn(DeviceHeaderInfo.getDefaultInstance());
    when(instantSource.instant()).thenReturn(Instant.now());

    DeviceOverviewPageData response = deviceServiceLogicImpl.getDeviceOverview(request).get();
    assertThat(response.getHeaderInfo()).isEqualTo(DeviceHeaderInfo.getDefaultInstance());
  }

  @Test
  public void getDeviceHeaderInfo_success() throws Exception {
    GetDeviceHeaderInfoRequest request =
        GetDeviceHeaderInfoRequest.newBuilder().setId("device").build();

    GetLabInfoResponse labInfoResponse =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setDeviceView(
                        DeviceView.newBuilder()
                            .setGroupedDevices(
                                GroupedDevices.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder()
                                            .addDeviceInfo(DeviceInfo.getDefaultInstance())
                                            .build()))))
            .build();

    when(labInfoProvider.getLabInfoAsync(any(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(labInfoResponse));

    when(configurationProvider.getDeviceConfig(anyString(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(Optional.empty()));
    when(deviceHeaderInfoBuilder.buildDeviceHeaderInfo(
            any(), any(), any(), any(UniverseScope.class)))
        .thenReturn(DeviceHeaderInfo.getDefaultInstance());

    DeviceHeaderInfo response = deviceServiceLogicImpl.getDeviceHeaderInfo(request).get();
    assertThat(response).isEqualTo(DeviceHeaderInfo.getDefaultInstance());
  }

  @Test
  public void getTestbedConfig_success() throws Exception {
    GetTestbedConfigRequest request = GetTestbedConfigRequest.newBuilder().setId("testbed").build();
    DeviceData deviceData =
        DeviceData.create(
            DeviceInfo.getDefaultInstance(),
            DeviceConfig.getDefaultInstance(),
            ManagementMode.PER_DEVICE,
            Optional.empty(),
            Optional.empty());

    when(deviceDataLoader.loadDeviceData(anyString(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(deviceData));
    when(testbedConfigBuilder.buildTestbedConfig(anyString(), any()))
        .thenReturn(TestbedConfig.getDefaultInstance());

    assertThat(deviceServiceLogicImpl.getTestbedConfig(request).get())
        .isEqualTo(TestbedConfig.getDefaultInstance());
  }

  @Test
  public void getDeviceOverview_invalidUniverse_fails() throws Exception {
    GetDeviceOverviewRequest request =
        GetDeviceOverviewRequest.newBuilder().setId("device").setUniverse("invalid").build();

    when(universeFactory.create("invalid")).thenThrow(new IllegalArgumentException("invalid"));

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> deviceServiceLogicImpl.getDeviceOverview(request).get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }
}
