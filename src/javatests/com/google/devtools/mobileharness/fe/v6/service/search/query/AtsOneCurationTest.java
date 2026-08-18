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

@RunWith(JUnit4.class)
public final class AtsOneCurationTest {

  private final AtsOneCuration curation = new AtsOneCuration();

  @Test
  public void deviceFilterByRow_isAtsOneList() {
    assertThat(curation.deviceFilterByRow())
        .containsExactly(
            "field::uuid",
            "host::host_name",
            "field::status",
            "dim::model",
            "dim::sdk_version",
            "config::wifi_ssid")
        .inOrder();
  }

  @Test
  public void deviceGroupByRow_isAtsOneList() {
    assertThat(curation.deviceGroupByRow())
        .containsExactly("host::host_name", "config::wifi_ssid")
        .inOrder();
  }

  @Test
  public void deviceDefaultColumns_isAtsOneList() {
    assertThat(curation.deviceDefaultColumns())
        .containsExactly(
            "field::uuid",
            "host::host_name",
            "field::status",
            "dim::model",
            "dim::os",
            "config::wifi_ssid")
        .inOrder();
  }

  @Test
  public void deviceRecommendedColumns_isAtsOneList() {
    assertThat(curation.deviceRecommendedColumns())
        .containsExactly(
            "host::host_name",
            "field::status",
            "field::type",
            "dim::model",
            "dim::sdk_version",
            "dim::device_class_name",
            "dim::manufacturer",
            "config::wifi_ssid")
        .inOrder();
  }

  @Test
  public void keyPriority_delegatesToAtsOneScenario() {
    // A tier 2 key ranks 1 in ats-one, distinguishing it from 1p and ats-all (which rank it 2).
    assertThat(curation.keyPriority("field::driver"))
        .isEqualTo(FleetKeyPriority.priority("field::driver", Scenario.ATS_ONE));
    assertThat(curation.keyPriority("field::driver")).isEqualTo(1);
    // The WiFi SSID key is promoted in ats-one.
    assertThat(curation.keyPriority("config::wifi_ssid"))
        .isEqualTo(FleetKeyPriority.priority("config::wifi_ssid", Scenario.ATS_ONE));
  }

  @Test
  public void landingEnabled_isFalse() {
    assertThat(curation.landingEnabled()).isFalse();
  }
}
