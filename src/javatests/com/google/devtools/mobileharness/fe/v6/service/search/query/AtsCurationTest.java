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

import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetKeyPriority.Scenario;
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
            "device_field::uuid",
            "host_field::host_name",
            "device_field::status",
            "dimension::model",
            "dimension::sdk_version",
            "device_config::wifi_ssid")
        .inOrder();
  }

  @Test
  public void deviceGroupByRow_isAtsList() {
    assertThat(curation.deviceGroupByRow())
        .containsExactly("host_field::host_name", "device_config::wifi_ssid")
        .inOrder();
  }

  @Test
  public void deviceDefaultColumns_isAtsList() {
    assertThat(curation.deviceDefaultColumns())
        .containsExactly(
            "device_field::uuid",
            "host_field::host_name",
            "device_field::status",
            "dimension::model",
            "dimension::os",
            "device_config::wifi_ssid")
        .inOrder();
  }

  @Test
  public void deviceRecommendedColumns_isAtsList() {
    assertThat(curation.deviceRecommendedColumns())
        .containsExactly(
            "host_field::host_name",
            "device_field::status",
            "device_field::type",
            "dimension::model",
            "dimension::sdk_version",
            "dimension::device_class_name",
            "dimension::manufacturer",
            "device_config::wifi_ssid")
        .inOrder();
  }

  @Test
  public void hostFilterByRow_isAtsList() {
    assertThat(curation.hostFilterByRow())
        .containsExactly(
            "host_field::host_name", "host_field::connectivity", "host_field::device_count")
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
            "host_field::host_name",
            "host_field::connectivity",
            "host_field::device_count",
            "host_property::host_os")
        .inOrder();
  }

  @Test
  public void hostRecommendedColumns_isAtsList() {
    assertThat(curation.hostRecommendedColumns())
        .containsExactly(
            "host_field::host_name",
            "host_field::connectivity",
            "host_field::device_count",
            "host_property::host_os",
            "host_field::lab_server_version",
            "host_field::host_ip")
        .inOrder();
  }

  @Test
  public void keyPriority_delegatesToAtsScenario() {
    // The WiFi SSID key is promoted in ats (rank 3).
    assertThat(curation.keyPriority("device_config::wifi_ssid"))
        .isEqualTo(FleetKeyPriority.priority("device_config::wifi_ssid", Scenario.ATS));
    assertThat(curation.keyPriority("device_config::wifi_ssid")).isEqualTo(3);
    // The ATS controller key is not curated in ats (rank 1).
    assertThat(curation.keyPriority("host_field::ats_controller"))
        .isEqualTo(FleetKeyPriority.priority("host_field::ats_controller", Scenario.ATS));
    assertThat(curation.keyPriority("host_field::ats_controller")).isEqualTo(1);
    // A tier 2 key ranks 1 in ats, distinguishing it from 1p and ats-all (2).
    assertThat(curation.keyPriority("device_field::driver"))
        .isEqualTo(FleetKeyPriority.priority("device_field::driver", Scenario.ATS));
    assertThat(curation.keyPriority("device_field::driver")).isEqualTo(1);
    // A raw discovered dimension is kept out of the way in ats (rank 0).
    assertThat(curation.keyPriority("dimension::pool"))
        .isEqualTo(FleetKeyPriority.priority("dimension::pool", Scenario.ATS));
    assertThat(curation.keyPriority("dimension::pool")).isEqualTo(0);
  }

  @Test
  public void landingEnabled_isFalse() {
    assertThat(curation.landingEnabled()).isFalse();
  }
}
