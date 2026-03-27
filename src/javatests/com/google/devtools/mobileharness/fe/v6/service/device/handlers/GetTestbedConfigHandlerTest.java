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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetTestbedConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TestbedConfig;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.DeviceData;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.ManagementMode;
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
public final class GetTestbedConfigHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private DeviceDataLoader deviceDataLoader;
  @Bind @Mock private TestbedConfigBuilder testbedConfigBuilder;

  @Inject private GetTestbedConfigHandler getTestbedConfigHandler;

  private DeviceData deviceData;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    deviceData =
        DeviceData.create(
            DeviceInfo.getDefaultInstance(),
            DeviceConfig.getDefaultInstance(),
            ManagementMode.PER_DEVICE,
            Optional.empty(),
            Optional.empty());
  }

  @Test
  public void getTestbedConfig_success() throws Exception {
    String deviceId = "test_id";
    String universe = "google_1p";
    GetTestbedConfigRequest request = GetTestbedConfigRequest.newBuilder().setId(deviceId).build();
    TestbedConfig response = TestbedConfig.newBuilder().setYamlContent("test_yaml").build();

    when(deviceDataLoader.loadDeviceData(deviceId, universe))
        .thenReturn(immediateFuture(deviceData));
    when(testbedConfigBuilder.buildTestbedConfig(deviceId, deviceData)).thenReturn(response);

    TestbedConfig result = getTestbedConfigHandler.getTestbedConfig(request).get();

    assertThat(result).isEqualTo(response);
  }
}
