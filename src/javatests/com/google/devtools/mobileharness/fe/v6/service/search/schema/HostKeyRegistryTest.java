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
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.protobuf.util.FieldMaskUtil;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HostKeyRegistryTest {

  private final AtsHostKeyRegistry registry = new AtsHostKeyRegistry();

  @Test
  public void containsCommonHostKeys() {
    assertThat(registry.getKey("host_field::host_name")).isPresent();
    assertThat(registry.getKey("host_field::host_ip")).isPresent();
    assertThat(registry.getKey("host_field::connectivity")).isPresent();
    assertThat(registry.getKey("host_property::host_os")).isPresent();
    assertThat(registry.getKey("host_field::lab_server_version")).isPresent();
    assertThat(registry.getKey("host_field::device_count")).isPresent();
  }

  @Test
  public void excludesOnePAndPartnerKeys() {
    assertThat(registry.getKey("host_field::release_status")).isEmpty();
    assertThat(registry.getKey("host_field::lab_type")).isEmpty();
    assertThat(registry.getKey("host_field::ats_controller")).isEmpty();
    // A device key is not a host key.
    assertThat(registry.getKey("device_field::uuid")).isEmpty();
  }

  @Test
  public void displayNamesUseHostSearchLabels() {
    assertThat(registry.displayName("host_field::host_name")).hasValue("Host Name");
    // Host search shows the bare label, not the "Host ..." device-search form.
    assertThat(registry.displayName("host_field::connectivity"))
        .hasValue("Lab Server Connectivity");
  }

  @Test
  public void mintsLongTailHostProperty() {
    Optional<HostKeyDescriptor> prop = registry.getKey("host_property::rack");
    assertThat(prop).isPresent();
    assertThat(prop.get().isLongTail()).isTrue();
    assertThat(prop.get().display().name()).isEqualTo("rack");

    assertThat(registry.createLongTailHostPropertyKey("rack")).isPresent();
    assertThat(registry.createLongTailHostPropertyKey("rack").get().isLongTail()).isTrue();

    // Rejects bare prefixes and empty/blank/null values gracefully.
    assertThat(registry.getKey("host_property::")).isEmpty();
    assertThat(registry.getKey("host_property::   ")).isEmpty();
    assertThat(registry.createLongTailHostPropertyKey("")).isEmpty();
    assertThat(registry.createLongTailHostPropertyKey("  ")).isEmpty();
    assertThat(registry.createLongTailHostPropertyKey(null)).isEmpty();

    // A host_field:: id that is not built in cannot be minted.
    assertThat(registry.getKey("host_field::bogus")).isEmpty();
  }

  @Test
  public void deriveLabInfoMask_unionsHostSources() {
    LabInfoMask mask = registry.deriveLabInfoMask();
    assertThat(mask.getFieldMask().getPathsList())
        .containsAtLeast(
            "lab_locator.host_name",
            "lab_locator.ip",
            "lab_status",
            "lab_server_feature.host_properties");
    for (String path : mask.getFieldMask().getPathsList()) {
      assertWithMessage("%s is not a field path of LabInfo", path)
          .that(FieldMaskUtil.isValid(LabInfo.getDescriptor(), path))
          .isTrue();
    }
  }

  @Test
  public void constructor_rejectsDevicePrefixedId() {
    HostKeyDescriptor bad =
        HostKeyDescriptor.builder()
            .setId("device_field::bogus")
            .setDisplay(KeyDisplay.of("Bogus"))
            .build();
    assertThrows(
        IllegalArgumentException.class, () -> new HostKeyRegistry(ImmutableList.of(bad)) {});
  }
}
