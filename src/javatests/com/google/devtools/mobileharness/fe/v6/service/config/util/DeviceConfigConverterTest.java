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

package com.google.devtools.mobileharness.fe.v6.service.config.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigMode;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceConfigConverterTest {

  @Test
  public void toFeHostConfig_extractsHostAdminsFromDefaultDeviceConfig() {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setDefaultDeviceConfig(
                BasicDeviceConfig.newBuilder().addOwner("admin1").addOwner("admin2").build())
            .build();

    HostConfig feConfig = DeviceConfigConverter.toFeHostConfig(labConfig);

    assertThat(feConfig.getPermissions().getHostAdminsList()).containsExactly("admin1", "admin2");
  }

  @Test
  public void toFeHostConfig_detectsSharedMode_fromPropertyPresence() {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setHostProperties(
                HostProperties.newBuilder()
                    .addHostProperty(
                        HostProperty.newBuilder()
                            .setKey("device_config_mode")
                            .setValue("host")
                            .build())
                    .build())
            .build();

    HostConfig feConfig = DeviceConfigConverter.toFeHostConfig(labConfig);

    assertThat(feConfig.getDeviceConfigMode()).isEqualTo(DeviceConfigMode.SHARED);
  }

  @Test
  public void toFeHostConfig_detectsPerDeviceMode_fromPropertyAbsence() {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setHostProperties(
                HostProperties.newBuilder()
                    .addHostProperty(
                        HostProperty.newBuilder().setKey("other").setValue("val").build())
                    .build())
            .build();

    HostConfig feConfig = DeviceConfigConverter.toFeHostConfig(labConfig);

    assertThat(feConfig.getDeviceConfigMode()).isEqualTo(DeviceConfigMode.PER_DEVICE);
  }
}
