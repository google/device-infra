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
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AtsCuration}. */
@RunWith(JUnit4.class)
public final class AtsCurationTest {

  private final AtsCuration curation = new AtsCuration();

  @Test
  public void deviceFilterByRow_isAtsList() {
    assertThat(curation.deviceFilterByRow())
        .containsExactly(
            DeviceKeys.UUID,
            DeviceKeys.HOST_NAME,
            DeviceKeys.STATUS,
            DeviceKeys.MODEL,
            DeviceKeys.SDK_VERSION,
            AtsDeviceKeys.WIFI_SSID)
        .inOrder();
  }

  @Test
  public void deviceGroupByRow_isAtsList() {
    assertThat(curation.deviceGroupByRow())
        .containsExactly(DeviceKeys.HOST_NAME, AtsDeviceKeys.WIFI_SSID)
        .inOrder();
  }

  @Test
  public void deviceDefaultColumns_isAtsList() {
    assertThat(curation.deviceDefaultColumns())
        .containsExactly(
            DeviceKeys.UUID,
            DeviceKeys.HOST_NAME,
            DeviceKeys.STATUS,
            DeviceKeys.MODEL,
            DeviceKeys.OS,
            AtsDeviceKeys.WIFI_SSID)
        .inOrder();
  }

  @Test
  public void deviceRecommendedColumns_isAtsList() {
    assertThat(curation.deviceRecommendedColumns())
        .containsExactly(
            DeviceKeys.HOST_NAME,
            DeviceKeys.STATUS,
            DeviceKeys.TYPE,
            DeviceKeys.MODEL,
            DeviceKeys.SDK_VERSION,
            DeviceKeys.DEVICE_CLASS_NAME,
            DeviceKeys.MANUFACTURER,
            AtsDeviceKeys.WIFI_SSID)
        .inOrder();
  }

  @Test
  public void hostFilterByRow_isAtsList() {
    assertThat(curation.hostFilterByRow())
        .containsExactly(HostKeys.HOST_NAME, HostKeys.CONNECTIVITY, HostKeys.DEVICE_COUNT)
        .inOrder();
  }

  @Test
  public void hostGroupByRow_isAtsList_isEmpty() {
    assertThat(curation.hostGroupByRow()).isEmpty();
  }

  @Test
  public void hostDefaultColumns_isAtsList() {
    assertThat(curation.hostDefaultColumns())
        .containsExactly(
            HostKeys.HOST_NAME, HostKeys.CONNECTIVITY, HostKeys.DEVICE_COUNT, HostKeys.HOST_OS)
        .inOrder();
  }

  @Test
  public void hostRecommendedColumns_isAtsList() {
    assertThat(curation.hostRecommendedColumns())
        .containsExactly(
            HostKeys.HOST_NAME,
            HostKeys.CONNECTIVITY,
            HostKeys.DEVICE_COUNT,
            HostKeys.HOST_OS,
            HostKeys.LAB_SERVER_VERSION,
            HostKeys.HOST_IP)
        .inOrder();
  }

  @Test
  public void keyPriority_isFleetKeyPriority() {
    assertThat(curation.keyPriority()).isSameInstanceAs(FleetKeyPriority.INSTANCE);
  }

  @Test
  public void landingEnabled_isFalse() {
    assertThat(curation.landingEnabled()).isFalse();
  }
}
