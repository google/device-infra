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

package com.google.devtools.mobileharness.fe.v6.service.search.schema;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class KeyRegistryTest {

  @Test
  public void baseKeyRegistry_partitionsDeviceAndHostKeys() {
    KeyRegistry registry = new KeyRegistry();

    // Device keys partition.
    assertThat(registry.getDeviceKey("device_field::uuid")).isPresent();
    assertThat(registry.getDeviceKey("device_field::status")).isPresent();
    assertThat(registry.getDeviceKey("dimension::model")).isPresent();
    assertThat(registry.getDeviceKey("host_field::host_name")).isEmpty();

    // Host keys partition.
    assertThat(registry.getHostKey("host_field::host_name")).isPresent();
    assertThat(registry.getHostKey("host_field::connectivity")).isPresent();
    assertThat(registry.getHostKey("host_property::host_os")).isPresent();
    assertThat(registry.getHostKey("device_field::uuid")).isEmpty();

    // Standalone ATS and 1P exclusive keys are absent from the base registry.
    assertThat(registry.getDeviceKey("device_config::wifi_ssid")).isEmpty();
    assertThat(registry.getDeviceKey("device_field::owner")).isEmpty();
    assertThat(registry.getHostKey("host_field::release_status")).isEmpty();
    assertThat(registry.getHostKey("host_field::ats_controller")).isEmpty();
  }

  @Test
  public void atsKeyRegistry_containsWifiSsidInDevicePartition() {
    AtsKeyRegistry registry = new AtsKeyRegistry();

    assertThat(registry.getDeviceKey("device_field::uuid")).isPresent();
    assertThat(registry.getDeviceKey("device_config::wifi_ssid")).isPresent();

    // 1P exclusive keys are absent.
    assertThat(registry.getDeviceKey("device_field::owner")).isEmpty();
    assertThat(registry.getHostKey("host_field::release_status")).isEmpty();
    assertThat(registry.getHostKey("host_field::ats_controller")).isEmpty();
  }

  @Test
  public void deriveDeviceInfoMask_derivesFromDevicePartitionOnly() {
    KeyRegistry registry = new KeyRegistry();
    DeviceInfoMask mask = registry.deriveDeviceInfoMask();

    // Field mask contains all DeviceInfoField proto paths + composite_dimension.
    assertThat(mask.getFieldMask().getPathsList())
        .containsAtLeast(
            "device_locator.id",
            "device_status",
            "device_feature.type",
            "device_feature.driver",
            "device_feature.decorator",
            "device_feature.composite_dimension");

    // Dimensions mask contains all core dimension names.
    assertThat(mask.getSupportedDimensionsMask().getDimensionNamesList())
        .containsAtLeast(
            "model",
            "software_version",
            "sdk_version",
            "device_form",
            "device_class_name",
            "manufacturer",
            "quarantined");

    // Host paths are NOT in DeviceInfoMask.
    assertThat(mask.getFieldMask().getPathsList()).doesNotContain("lab_locator.host_name");
  }

  @Test
  public void deriveLabInfoMask_derivesFromHostPartitionOnly() {
    KeyRegistry registry = new KeyRegistry();
    LabInfoMask mask = registry.deriveLabInfoMask();

    assertThat(mask.getFieldMask().getPathsList())
        .containsAtLeast(
            "lab_locator.host_name",
            "lab_locator.ip",
            "lab_status",
            "lab_server_feature.host_properties");

    // Device paths are NOT in LabInfoMask.
    assertThat(mask.getFieldMask().getPathsList()).doesNotContain("device_locator.id");
  }

  @Test
  public void displayName_returnsHostAndDeviceNames() {
    KeyRegistry registry = new KeyRegistry();

    // Device keys have a single name.
    assertThat(registry.displayName("device_field::uuid", /* isHostSearch= */ false))
        .hasValue("UUID");

    // Host keys have distinct names for device search (cross-entity) vs host search (Scheme A).
    assertThat(registry.displayName("host_field::connectivity", /* isHostSearch= */ false))
        .hasValue("Host Lab Server Connectivity");
    assertThat(registry.displayName("host_field::connectivity", /* isHostSearch= */ true))
        .hasValue("Lab Server Connectivity");
  }

  @Test
  public void constructor_rejectsDeviceKeyWithHostPrefix() {
    DeviceKeyDescriptor bad =
        DeviceKeyDescriptor.builder()
            .setId("host_field::bogus")
            .setSource(DeviceSource.dimension("bogus"))
            .setDisplayName("Bogus")
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> new KeyRegistry(ImmutableList.of(bad), ImmutableList.of()) {});
  }

  @Test
  public void constructor_rejectsDuplicateDeviceId() {
    DeviceKeyDescriptor dup =
        DeviceKeyDescriptor.builder()
            .setId("device_field::uuid")
            .setSource(DeviceSource.deviceInfoField("device_locator.id"))
            .setDisplayName("Dup")
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> new KeyRegistry(ImmutableList.of(dup), ImmutableList.of()) {});
  }
}
