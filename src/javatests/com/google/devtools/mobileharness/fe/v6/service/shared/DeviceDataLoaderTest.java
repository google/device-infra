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

package com.google.devtools.mobileharness.fe.v6.service.shared;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.DeviceData;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.ManagementMode;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
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
public final class DeviceDataLoaderTest {

  private static final String DEVICE_ID = "test_device_id";
  private static final String UNIVERSE = "test_universe";
  private static final String HOST_NAME = "test_host";

  private static final DeviceInfo DEFAULT_DEVICE_INFO =
      DeviceInfo.newBuilder()
          .setDeviceLocator(
              DeviceLocator.newBuilder()
                  .setId(DEVICE_ID)
                  .setLabLocator(LabLocator.newBuilder().setHostName(HOST_NAME)))
          .build();

  private static final GetLabInfoResponse DEFAULT_LAB_INFO_RESPONSE =
      GetLabInfoResponse.newBuilder()
          .setLabQueryResult(
              LabQueryResult.newBuilder()
                  .setDeviceView(
                      DeviceView.newBuilder()
                          .setGroupedDevices(
                              GroupedDevices.newBuilder()
                                  .setDeviceList(
                                      DeviceList.newBuilder().addDeviceInfo(DEFAULT_DEVICE_INFO)))))
          .build();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private ConfigServiceCapability configServiceCapability;
  @Bind @Mock private Environment environment;
  @Bind private ListeningExecutorService executor = newDirectExecutorService();

  @Inject private DeviceDataLoader deviceDataLoader;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

    when(labInfoProvider.getLabInfoAsync(any(GetLabInfoRequest.class), any()))
        .thenReturn(immediateFuture(DEFAULT_LAB_INFO_RESPONSE));
    when(configurationProvider.getDeviceConfig(any(), any()))
        .thenReturn(immediateFuture(Optional.empty()));
    when(configurationProvider.getLabConfig(any(), any()))
        .thenReturn(immediateFuture(Optional.of(LabConfig.getDefaultInstance())));
    when(environment.isGoogleInternal()).thenReturn(true);
    when(configServiceCapability.isUniverseSupported(any())).thenReturn(true);
  }

  @Test
  public void loadDeviceData_perDevice_success() throws Exception {
    DeviceConfig individualConfig = DeviceConfig.newBuilder().setUuid(DEVICE_ID).build();
    when(configurationProvider.getDeviceConfig(DEVICE_ID, UNIVERSE))
        .thenReturn(immediateFuture(Optional.of(individualConfig)));

    DeviceData deviceData = deviceDataLoader.loadDeviceData(DEVICE_ID, UNIVERSE).get();

    assertThat(deviceData.managementMode()).isEqualTo(ManagementMode.PER_DEVICE);
    assertThat(deviceData.effectiveDeviceConfig()).isEqualTo(individualConfig);
    assertThat(deviceData.isConfigSupported()).isTrue();
  }

  @Test
  public void loadDeviceData_notSupported_universe() throws Exception {
    when(configServiceCapability.isUniverseSupported("not_supported_universe")).thenReturn(false);

    DeviceData deviceData =
        deviceDataLoader.loadDeviceData(DEVICE_ID, "not_supported_universe").get();

    assertThat(deviceData.managementMode()).isEqualTo(ManagementMode.NOT_SUPPORTED);
    assertThat(deviceData.isConfigSupported()).isFalse();
    assertThat(deviceData.effectiveDeviceConfig()).isEqualTo(DeviceConfig.getDefaultInstance());
  }

  @Test
  public void loadDeviceData_notSupported_labConfigMissing() throws Exception {
    when(configurationProvider.getLabConfig(HOST_NAME, UNIVERSE))
        .thenReturn(immediateFuture(Optional.empty()));

    DeviceData deviceData = deviceDataLoader.loadDeviceData(DEVICE_ID, UNIVERSE).get();

    assertThat(deviceData.managementMode()).isEqualTo(ManagementMode.NOT_SUPPORTED);
    assertThat(deviceData.isConfigSupported()).isFalse();
  }

  @Test
  public void loadDeviceData_hostManaged_success() throws Exception {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setHostProperties(
                com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties.newBuilder()
                    .addHostProperty(
                        com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty
                            .newBuilder()
                            .setKey("device_config_mode")
                            .setValue("host")))
            .setDefaultDeviceConfig(
                com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig
                    .newBuilder()
                    .addOwner("host_owner"))
            .build();
    when(configurationProvider.getLabConfig(HOST_NAME, UNIVERSE))
        .thenReturn(immediateFuture(Optional.of(labConfig)));

    DeviceData deviceData = deviceDataLoader.loadDeviceData(DEVICE_ID, UNIVERSE).get();

    assertThat(deviceData.managementMode()).isEqualTo(ManagementMode.HOST_MANAGED);
    assertThat(deviceData.effectiveDeviceConfig().getBasicConfig().getOwner(0))
        .isEqualTo("host_owner");
    assertThat(deviceData.isHostManaged()).isTrue();
  }

  @Test
  public void loadDeviceData_deviceConfigFetchFailure_returnsDefault() throws Exception {
    when(configurationProvider.getDeviceConfig(any(), any()))
        .thenReturn(Futures.immediateFailedFuture(new RuntimeException("Config service error")));

    DeviceData deviceData = deviceDataLoader.loadDeviceData(DEVICE_ID, UNIVERSE).get();

    assertThat(deviceData.managementMode()).isEqualTo(ManagementMode.PER_DEVICE);
    assertThat(deviceData.effectiveDeviceConfig()).isEqualTo(DeviceConfig.getDefaultInstance());
  }

  @Test
  public void loadDeviceData_labConfigFetchFailure_returnsNotSupported() throws Exception {
    when(configurationProvider.getLabConfig(any(), any()))
        .thenReturn(Futures.immediateFailedFuture(new RuntimeException("Config service error")));

    DeviceData deviceData = deviceDataLoader.loadDeviceData(DEVICE_ID, UNIVERSE).get();

    assertThat(deviceData.managementMode()).isEqualTo(ManagementMode.NOT_SUPPORTED);
    assertThat(deviceData.isConfigSupported()).isFalse();
  }

  @Test
  public void loadDeviceData_deviceInfoNotFound_throwsException() throws Exception {
    when(labInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(
            immediateFuture(
                GetLabInfoResponse.newBuilder()
                    .setLabQueryResult(
                        LabQueryResult.newBuilder()
                            .setDeviceView(
                                DeviceView.newBuilder()
                                    .setGroupedDevices(
                                        GroupedDevices.newBuilder()
                                            .setDeviceList(DeviceList.getDefaultInstance()))))
                    .build()));

    ListenableFuture<DeviceData> future = deviceDataLoader.loadDeviceData(DEVICE_ID, UNIVERSE);

    java.util.concurrent.ExecutionException exception =
        org.junit.Assert.assertThrows(
            java.util.concurrent.ExecutionException.class, () -> future.get());
    assertThat(exception.getCause()).isInstanceOf(RuntimeException.class);
    assertThat(exception.getCause()).hasMessageThat().contains("Device not found");
  }
}
