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
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceKeyRegistryTest {

  private final AtsDeviceKeyRegistry registry = new AtsDeviceKeyRegistry();

  @Test
  public void containsCommonDeviceKeysAndProjectedCommonHostKeys() {
    assertThat(registry.getKey("device_field::uuid")).isPresent();
    assertThat(registry.getKey("dimension::model")).isPresent();
    assertThat(registry.getKey("dimension::os")).isPresent();
    assertThat(registry.getKey("dimension::device_class_name")).isPresent();
    assertThat(registry.getKey("dimension::manufacturer")).isPresent();
    // Standalone ATS exclusive key.
    assertThat(registry.getKey("device_config::wifi_ssid")).isPresent();
    // Projected common host keys are usable in device search.
    assertThat(registry.getKey("host_field::host_name")).isPresent();
    assertThat(registry.getKey("host_field::connectivity")).isPresent();
    assertThat(registry.getKey("host_property::host_os")).isPresent();
  }

  @Test
  public void excludesOnePkeysAndUnprojectedHostKeys() {
    // 1P device-native keys (owner/executor/quarantine/pool/lab_location) are not in OSS.
    assertThat(registry.getKey("device_field::owner")).isEmpty();
    assertThat(registry.getKey("device_field::executor")).isEmpty();
    assertThat(registry.getKey("device_field::quarantined")).isEmpty();
    assertThat(registry.getKey("dimension::pool")).isPresent(); // long-tail mint, not built-in
    assertThat(registry.getKey("dimension::pool").get().isLongTail()).isTrue();
    // device_count is host-search only: never projected into device search.
    assertThat(registry.getKey("host_field::device_count")).isEmpty();
    // 1P host keys are not projected in OSS.
    assertThat(registry.getKey("host_field::lab_type")).isEmpty();
  }

  @Test
  public void builtInKeysAreNotLongTail() {
    assertThat(registry.getKey("device_field::uuid").get().isLongTail()).isFalse();
    assertThat(registry.getKey("dimension::model").get().isLongTail()).isFalse();
    assertThat(registry.getKey("host_field::host_name").get().isLongTail()).isFalse();
  }

  @Test
  public void mintsLongTailDimensionAndHostProperty() {
    Optional<DeviceKeyDescriptor> dim = registry.getKey("dimension::carrier");
    assertThat(dim).isPresent();
    assertThat(dim.get().isLongTail()).isTrue();
    assertThat(dim.get().display().name()).isEqualTo("carrier");

    Optional<DeviceKeyDescriptor> prop = registry.getKey("host_property::rack");
    assertThat(prop).isPresent();
    assertThat(prop.get().isLongTail()).isTrue();
    assertThat(prop.get().display().name()).isEqualTo("rack");

    assertThat(registry.createLongTailDimensionKey("carrier").isLongTail()).isTrue();
    assertThat(registry.createProjectLongTailHostPropertyKey("rack").isLongTail()).isTrue();
    // An unknown non-mintable namespace is not a key.
    assertThat(registry.getKey("device_field::bogus")).isEmpty();
  }

  @Test
  public void displayNamesUseDeviceSearchLabels() {
    assertThat(registry.displayName("device_field::uuid")).hasValue("UUID");
    assertThat(registry.displayName("dimension::model")).hasValue("Model");
    // A projected host key shows its device-search name.
    assertThat(registry.displayName("host_field::connectivity"))
        .hasValue("Host Lab Server Connectivity");
  }

  @Test
  public void deriveDeviceInfoMask_unionsCommonSources_excludesOnePAndQuarantine() {
    DeviceInfoMask mask = registry.deriveDeviceInfoMask();
    assertThat(mask.getFieldMask().getPathsList())
        .containsAtLeast(
            "device_locator.id",
            "device_status",
            "device_feature.type",
            "device_feature.driver",
            "device_feature.decorator",
            "device_feature.composite_dimension");
    assertThat(mask.getSupportedDimensionsMask().getDimensionNamesList())
        .containsAtLeast(
            "model",
            "os",
            "sdk_version",
            "software_version",
            "device_form",
            "device_class_name",
            "manufacturer");
    // Quarantine is 1P-only, so OSS does not pull device_condition and never names "quarantined".
    assertThat(mask.getFieldMask().getPathsList()).doesNotContain("device_condition");
    assertThat(mask.getSupportedDimensionsMask().getDimensionNamesList())
        .doesNotContain("quarantined");
    // Host paths belong to the LabInfoMask.
    assertThat(mask.getFieldMask().getPathsList()).doesNotContain("lab_locator.host_name");
    assertMaskPathsExist(DeviceInfo.getDescriptor(), mask.getFieldMask());
  }

  @Test
  public void deriveLabInfoMask_fromProjectedHostKeys() {
    LabInfoMask mask = registry.deriveLabInfoMask();
    assertThat(mask.getFieldMask().getPathsList())
        .containsAtLeast(
            "lab_locator.host_name",
            "lab_locator.ip",
            "lab_status",
            "lab_server_feature.host_properties");
    assertThat(mask.getFieldMask().getPathsList()).doesNotContain("device_locator.id");
    assertMaskPathsExist(LabInfo.getDescriptor(), mask.getFieldMask());
  }

  @Test
  public void deviceInfoSource_extractsValueFromProto() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("model").setValue("Pixel 8"))))
            .build();
    DeviceInfoSource modelSource = DeviceKeys.MODEL.deviceInfoSources().get(0);
    assertThat(modelSource.extract(deviceInfo)).containsExactly("Pixel 8");
  }

  @Test
  public void constructor_rejectsDuplicateId() {
    DeviceKeyDescriptor dup =
        DeviceKeyDescriptor.builder()
            .setId("device_field::uuid")
            .setDeviceInfoSource(DeviceInfoSource.dimension("uuid"))
            .setDisplay(KeyDisplay.of("Dup"))
            .build();
    assertThrows(
        IllegalArgumentException.class, () -> new DeviceKeyRegistry(ImmutableList.of(dup)) {});
  }

  private static void assertMaskPathsExist(Descriptor descriptor, FieldMask mask) {
    for (String path : mask.getPathsList()) {
      assertWithMessage("%s is not a field path of %s", path, descriptor.getFullName())
          .that(FieldMaskUtil.isValid(descriptor, path))
          .isTrue();
    }
    assertThat(mask.getPathsList()).isNotEmpty();
  }
}
