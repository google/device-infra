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

package com.google.devtools.mobileharness.infra.ats.common;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceSelectionTest {

  private static final String DEVICE_ID = "device_id";

  @Test
  public void matches_productType() {
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).setProductType("redfin").build(),
                DeviceSelectionOptions.builder()
                    .setProductTypes(ImmutableList.of("redfin", "cheetah"))
                    .build()))
        .isTrue();

    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).setProductType("redfin").build(),
                DeviceSelectionOptions.builder()
                    .setProductTypes(ImmutableList.of("cheetah"))
                    .build()))
        .isFalse();
  }

  @Test
  public void matches_batteryLevel() {
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).setBatteryLevel(90).build(),
                DeviceSelectionOptions.builder().setMaxBatteryLevel(100).build()))
        .isTrue();
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).setBatteryLevel(90).build(),
                DeviceSelectionOptions.builder().setMinBatteryLevel(80).build()))
        .isTrue();
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).setBatteryLevel(90).build(),
                DeviceSelectionOptions.builder().setMaxBatteryLevel(80).build()))
        .isFalse();
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).setBatteryLevel(90).build(),
                DeviceSelectionOptions.builder().setMinBatteryLevel(95).build()))
        .isFalse();
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).build(),
                DeviceSelectionOptions.builder().setMinBatteryLevel(95).build()))
        .isFalse();
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).build(),
                DeviceSelectionOptions.builder().setMaxBatteryLevel(95).build()))
        .isFalse();
  }

  @Test
  public void matches_batteryTemperature() {
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).setBatteryTemperature(30).build(),
                DeviceSelectionOptions.builder().setMaxBatteryTemperature(40).build()))
        .isTrue();
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).setBatteryTemperature(30).build(),
                DeviceSelectionOptions.builder().setMaxBatteryTemperature(20).build()))
        .isFalse();
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).build(),
                DeviceSelectionOptions.builder().setMaxBatteryTemperature(30).build()))
        .isFalse();
  }

  @Test
  public void matches_sdkLevel() {
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).setSdkVersion(30).build(),
                DeviceSelectionOptions.builder().setMinSdkLevel(20).build()))
        .isTrue();
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).setSdkVersion(30).build(),
                DeviceSelectionOptions.builder().setMinSdkLevel(40).build()))
        .isFalse();
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).setSdkVersion(30).build(),
                DeviceSelectionOptions.builder().setMaxSdkLevel(40).build()))
        .isTrue();
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).setSdkVersion(30).build(),
                DeviceSelectionOptions.builder().setMaxSdkLevel(20).build()))
        .isFalse();
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).build(),
                DeviceSelectionOptions.builder().setMaxSdkLevel(30).build()))
        .isFalse();
  }

  @Test
  public void matches_productTypeWithVariant() {
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder()
                    .setId(DEVICE_ID)
                    .setProductType("redfin")
                    .setProductVariant("redfin")
                    .build(),
                DeviceSelectionOptions.builder()
                    .setProductTypes(ImmutableList.of("cheetah:cheetah", "redfin:redfin"))
                    .build()))
        .isTrue();

    // Device missing product variant
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder().setId(DEVICE_ID).setProductType("redfin").build(),
                DeviceSelectionOptions.builder()
                    .setProductTypes(ImmutableList.of("redfin:redfin"))
                    .build()))
        .isFalse();

    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder()
                    .setId(DEVICE_ID)
                    .setProductType("redfin")
                    .setProductVariant("redfin")
                    .build(),
                DeviceSelectionOptions.builder()
                    .setProductTypes(ImmutableList.of("cheetah:cheetah"))
                    .build()))
        .isFalse();

    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder()
                    .setId(DEVICE_ID)
                    .setProductType("redfin")
                    .setProductVariant("redfin")
                    .build(),
                DeviceSelectionOptions.builder()
                    .setProductTypes(ImmutableList.of("redfin:redfin-variant"))
                    .build()))
        .isFalse();
  }

  @Test
  public void matches_deviceProperties() {
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder()
                    .setId(DEVICE_ID)
                    .setDeviceProperties(
                        ImmutableMap.of(
                            "ro.product.board", "cheetah", "ro.build.version.sdk", "34"))
                    .build(),
                DeviceSelectionOptions.builder()
                    .setDeviceProperties(
                        ImmutableMap.of(
                            "ro.product.board", "cheetah", "ro.build.version.sdk", "34"))
                    .build()))
        .isTrue();

    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder()
                    .setId(DEVICE_ID)
                    .setDeviceProperties(
                        ImmutableMap.of("ro.product.board", "redfin", "ro.build.version.sdk", "34"))
                    .build(),
                DeviceSelectionOptions.builder()
                    .setDeviceProperties(
                        ImmutableMap.of(
                            "ro.product.board", "cheetah", "ro.build.version.sdk", "34"))
                    .build()))
        .isFalse();

    // Device missing some properties
    assertThat(
            DeviceSelection.matches(
                DeviceDetails.builder()
                    .setId(DEVICE_ID)
                    .setDeviceProperties(ImmutableMap.of("ro.product.board", "redfin"))
                    .build(),
                DeviceSelectionOptions.builder()
                    .setDeviceProperties(
                        ImmutableMap.of(
                            "ro.product.board", "cheetah", "ro.build.version.sdk", "34"))
                    .build()))
        .isFalse();
  }
}
