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

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TestbedConfig;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.DeviceData;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.ManagementMode;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class OssTestbedConfigBuilderImplTest {

  private DeviceData deviceData;
  private OssTestbedConfigBuilderImpl builder;

  @Before
  public void setUp() {
    builder = new OssTestbedConfigBuilderImpl();
    deviceData =
        DeviceData.create(
            DeviceInfo.getDefaultInstance(),
            DeviceConfig.getDefaultInstance(),
            ManagementMode.PER_DEVICE,
            Optional.empty(),
            Optional.empty());
  }

  @Test
  public void buildTestbedConfig_returnsEmpty() throws Exception {
    TestbedConfig response = builder.buildTestbedConfig("test_id", deviceData);

    assertThat(response.getYamlContent()).isEmpty();
    assertThat(response.getCodeSearchLink()).isEmpty();
  }
}
