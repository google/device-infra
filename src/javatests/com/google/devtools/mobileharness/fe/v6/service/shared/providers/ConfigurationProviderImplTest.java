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

package com.google.devtools.mobileharness.fe.v6.service.shared.providers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetLabConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateLabConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.DeviceConfigStub;
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
public final class ConfigurationProviderImplTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private DeviceConfigStub deviceConfigStub;
  @Mock private ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  @Mock private ConfigServiceCapability configServiceCapability;
  @Mock private Environment environment;

  private final ListeningExecutorService executor = newDirectExecutorService();

  private ConfigurationProviderImpl configurationProvider;

  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();

  @Before
  public void setUp() {
    when(configServiceCapabilityFactory.create(any(UniverseScope.class)))
        .thenReturn(configServiceCapability);
    when(configServiceCapability.isConfigServiceAvailable()).thenReturn(true);
    when(environment.isGoogleInternal()).thenReturn(false);

    configurationProvider =
        new ConfigurationProviderImpl(
            deviceConfigStub, executor, configServiceCapabilityFactory, environment);
  }

  @Test
  public void getDeviceConfig_success() throws Exception {
    DeviceConfig deviceConfig = DeviceConfig.newBuilder().setUuid("device_id").build();
    GetDeviceConfigsResponse response =
        GetDeviceConfigsResponse.newBuilder().addDeviceConfig(deviceConfig).build();
    when(deviceConfigStub.getDeviceConfigsAsync(any(), eq(false)))
        .thenReturn(immediateFuture(response));

    ConfigResult<DeviceConfig> result =
        configurationProvider.getDeviceConfig("device_id", UNIVERSE).get();

    assertThat(result.isAvailable()).isTrue();
    assertThat(result.config()).isPresent();
    assertThat(result.config().get()).isEqualTo(deviceConfig);
  }

  @Test
  public void getLabConfig_success() throws Exception {
    LabConfig labConfig = LabConfig.getDefaultInstance();
    GetLabConfigResponse response =
        GetLabConfigResponse.newBuilder().setLabConfig(labConfig).build();
    when(deviceConfigStub.getLabConfigAsync(any(), eq(false)))
        .thenReturn(immediateFuture(response));

    ConfigResult<LabConfig> result =
        configurationProvider.getLabConfig("host_name", UNIVERSE).get();

    assertThat(result.isAvailable()).isTrue();
    assertThat(result.config()).isPresent();
    assertThat(result.config().get()).isEqualTo(labConfig);
  }

  @Test
  public void getLabConfig_noConfigOnServer_returnsEmptyConfig() throws Exception {
    GetLabConfigResponse response = GetLabConfigResponse.getDefaultInstance();
    when(deviceConfigStub.getLabConfigAsync(any(), eq(false)))
        .thenReturn(immediateFuture(response));

    ConfigResult<LabConfig> result =
        configurationProvider.getLabConfig("host_name", UNIVERSE).get();

    assertThat(result.isAvailable()).isTrue();
    assertThat(result.config()).isEmpty();
  }

  @Test
  public void updateDeviceConfig_success() throws Exception {
    DeviceConfig deviceConfig = DeviceConfig.newBuilder().setUuid("device_id").build();
    UpdateDeviceConfigsResponse response = UpdateDeviceConfigsResponse.getDefaultInstance();
    when(deviceConfigStub.updateDeviceConfigsAsync(any(), eq(false)))
        .thenReturn(immediateFuture(response));

    configurationProvider.updateDeviceConfig("device_id", deviceConfig, UNIVERSE).get();

    verify(deviceConfigStub)
        .updateDeviceConfigsAsync(any(UpdateDeviceConfigsRequest.class), eq(false));
  }

  @Test
  public void updateLabConfig_success() throws Exception {
    LabConfig labConfig = LabConfig.getDefaultInstance();
    UpdateLabConfigResponse response = UpdateLabConfigResponse.getDefaultInstance();
    when(deviceConfigStub.updateLabConfigAsync(any(), eq(false)))
        .thenReturn(immediateFuture(response));

    configurationProvider.updateLabConfig("host_name", labConfig, UNIVERSE).get();

    verify(deviceConfigStub).updateLabConfigAsync(any(UpdateLabConfigRequest.class), eq(false));
  }

  @Test
  public void getDeviceConfig_configServiceUnavailable_returnsUnavailable() throws Exception {
    when(configServiceCapability.isConfigServiceAvailable()).thenReturn(false);

    ConfigResult<DeviceConfig> result =
        configurationProvider.getDeviceConfig("device_id", UNIVERSE).get();

    assertThat(result.isAvailable()).isFalse();
  }

  @Test
  public void getLabConfig_configServiceUnavailable_returnsUnavailable() throws Exception {
    when(configServiceCapability.isConfigServiceAvailable()).thenReturn(false);

    ConfigResult<LabConfig> result =
        configurationProvider.getLabConfig("host_name", UNIVERSE).get();

    assertThat(result.isAvailable()).isFalse();
  }

  @Test
  public void updateDeviceConfig_configServiceUnavailable_returnsVoid() throws Exception {
    when(configServiceCapability.isConfigServiceAvailable()).thenReturn(false);

    assertThat(
            configurationProvider
                .updateDeviceConfig("device_id", DeviceConfig.getDefaultInstance(), UNIVERSE)
                .get())
        .isNull();
  }

  @Test
  public void updateLabConfig_configServiceUnavailable_returnsVoid() throws Exception {
    when(configServiceCapability.isConfigServiceAvailable()).thenReturn(false);

    assertThat(
            configurationProvider
                .updateLabConfig("host_name", LabConfig.getDefaultInstance(), UNIVERSE)
                .get())
        .isNull();
  }

  @Test
  public void updateDeviceConfig_errorMessageInResponse_futureFailsWithException()
      throws Exception {
    String deviceId = "device_id";
    DeviceConfig deviceConfig = DeviceConfig.newBuilder().setUuid(deviceId).build();
    UpdateDeviceConfigsResponse response =
        UpdateDeviceConfigsResponse.newBuilder()
            .putErrorMessage(deviceId, "invalid owner: bad_user")
            .build();
    when(deviceConfigStub.updateDeviceConfigsAsync(any(), eq(false)))
        .thenReturn(immediateFuture(response));

    ExecutionException thrown =
        assertThrows(
            ExecutionException.class,
            () -> configurationProvider.updateDeviceConfig(deviceId, deviceConfig, UNIVERSE).get());

    assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);
    assertThat(thrown).hasCauseThat().hasMessageThat().contains(deviceId);
    assertThat(thrown).hasCauseThat().hasMessageThat().contains("invalid owner: bad_user");
  }
}
