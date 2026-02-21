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

package com.google.devtools.mobileharness.fe.v6.service.shared.providers.oss;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
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
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.DeviceConfigStub;
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
public final class OssConfigurationProviderImplTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private DeviceConfigStub deviceConfigStub;
  private ListeningExecutorService executor = newDirectExecutorService();

  private OssConfigurationProviderImpl ossConfigurationProvider;

  @Before
  public void setUp() {
    ossConfigurationProvider = new OssConfigurationProviderImpl(deviceConfigStub, executor);
  }

  @Test
  public void getDeviceConfig_success() throws Exception {
    DeviceConfig deviceConfig = DeviceConfig.newBuilder().setUuid("device_id").build();
    GetDeviceConfigsResponse response =
        GetDeviceConfigsResponse.newBuilder().addDeviceConfig(deviceConfig).build();
    when(deviceConfigStub.getDeviceConfigsAsync(any(), eq(false)))
        .thenReturn(immediateFuture(response));

    Optional<DeviceConfig> result =
        ossConfigurationProvider.getDeviceConfig("device_id", "universe").get();

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(deviceConfig);
  }

  @Test
  public void getLabConfig_success() throws Exception {
    LabConfig labConfig = LabConfig.newBuilder().build();
    GetLabConfigResponse response =
        GetLabConfigResponse.newBuilder().setLabConfig(labConfig).build();
    when(deviceConfigStub.getLabConfigAsync(any(), eq(false)))
        .thenReturn(immediateFuture(response));

    Optional<LabConfig> result =
        ossConfigurationProvider.getLabConfig("host_name", "universe").get();

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(labConfig);
  }

  @Test
  public void updateDeviceConfig_success() throws Exception {
    DeviceConfig deviceConfig = DeviceConfig.newBuilder().setUuid("device_id").build();
    UpdateDeviceConfigsResponse response = UpdateDeviceConfigsResponse.getDefaultInstance();
    when(deviceConfigStub.updateDeviceConfigsAsync(any(), eq(false)))
        .thenReturn(immediateFuture(response));

    ossConfigurationProvider.updateDeviceConfig("device_id", deviceConfig, "universe").get();

    verify(deviceConfigStub)
        .updateDeviceConfigsAsync(any(UpdateDeviceConfigsRequest.class), eq(false));
  }

  @Test
  public void updateLabConfig_success() throws Exception {
    LabConfig labConfig = LabConfig.newBuilder().build();
    UpdateLabConfigResponse response = UpdateLabConfigResponse.getDefaultInstance();
    when(deviceConfigStub.updateLabConfigAsync(any(), eq(false)))
        .thenReturn(immediateFuture(response));

    ossConfigurationProvider.updateLabConfig("host_name", labConfig, "universe").get();

    verify(deviceConfigStub).updateLabConfigAsync(any(UpdateLabConfigRequest.class), eq(false));
  }
}
