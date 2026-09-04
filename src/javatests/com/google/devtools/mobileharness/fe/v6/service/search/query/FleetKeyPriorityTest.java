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

package com.google.devtools.mobileharness.fe.v6.service.search.query;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDisplay;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetKeyPriority}. */
@RunWith(JUnit4.class)
public final class FleetKeyPriorityTest {

  private final FleetKeyPriority priority = FleetKeyPriority.INSTANCE;

  @Test
  public void devicePriority_tier1AndWifiSsid_isTop() {
    assertThat(priority.devicePriority(DeviceKeys.STATUS)).isEqualTo(3);
    assertThat(priority.devicePriority(DeviceKeys.UUID)).isEqualTo(3);
    assertThat(priority.devicePriority(AtsDeviceKeys.WIFI_SSID)).isEqualTo(3);
  }

  @Test
  public void devicePriority_tier2_isOneInAts() {
    assertThat(priority.devicePriority(DeviceKeys.DRIVER)).isEqualTo(1);
    assertThat(priority.devicePriority(DeviceKeys.DECORATOR)).isEqualTo(1);
    assertThat(priority.devicePriority(DeviceKeys.HOST_IP)).isEqualTo(1);
  }

  @Test
  public void devicePriority_otherKeys_isZero() {
    assertThat(
            priority.devicePriority(
                DeviceKeyDescriptor.builder()
                    .setId("dimension::battery_level")
                    .setDisplay(KeyDisplay.of("Battery Level"))
                    .build()))
        .isEqualTo(0);
  }

  @Test
  public void hostPriority_tier1_isTop() {
    assertThat(priority.hostPriority(HostKeys.HOST_NAME)).isEqualTo(3);
    assertThat(priority.hostPriority(HostKeys.CONNECTIVITY)).isEqualTo(3);
    assertThat(priority.hostPriority(HostKeys.DEVICE_COUNT)).isEqualTo(3);
  }

  @Test
  public void hostPriority_tier2_isOne() {
    assertThat(priority.hostPriority(HostKeys.HOST_IP)).isEqualTo(1);
  }

  @Test
  public void hostPriority_otherKeys_isZero() {
    assertThat(
            priority.hostPriority(
                HostKeyDescriptor.builder()
                    .setId("host_property::rack")
                    .setDisplay(KeyDisplay.of("Rack"))
                    .build()))
        .isEqualTo(0);
  }
}
