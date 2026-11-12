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
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.HealthCategory;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
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
    assertThat(registry.getKey("dimension::label")).isPresent();
    assertThat(registry.getKey("dimension::label").get().isLongTail()).isFalse();
    assertThat(registry.getKey("dimension::label").get().isOverlay()).isFalse();
    assertThat(registry.getKey("dimension::label").get().aliases()).containsExactly("device label");
    assertThat(registry.displayName("dimension::label")).hasValue("Label");
    assertThat(registry.getKey("dimension::battery_level")).hasValue(DeviceKeys.BATTERY_LEVEL);
    assertThat(registry.getKey("dimension::battery_temperature"))
        .hasValue(DeviceKeys.BATTERY_TEMPERATURE);
    assertThat(registry.getKey("dimension::hinge_angle")).hasValue(DeviceKeys.HINGE_ANGLE);
    assertThat(DeviceKeys.BATTERY_LEVEL.isDimension()).isTrue();
    assertThat(DeviceKeys.SDK_VERSION.isIntComparable()).isTrue();
    assertThat(DeviceKeys.BATTERY_LEVEL.isIntComparable()).isTrue();
    assertThat(DeviceKeys.BATTERY_TEMPERATURE.isIntComparable()).isTrue();
    assertThat(DeviceKeys.HINGE_ANGLE.isIntComparable()).isTrue();
    assertThat(DeviceKeys.MODEL.isIntComparable()).isFalse();
    assertThat(DeviceKeys.VERSION.isIntComparable()).isFalse();
    assertThat(registry.getKey("dimension::carrier").get().isIntComparable()).isFalse();
    assertThat(registry.getKey("device_field::version")).hasValue(DeviceKeys.VERSION);
    assertThat(registry.displayName("device_field::version")).hasValue("Version");
    assertThat(registry.getKey("device_field::health")).hasValue(DeviceKeys.HEALTH);
    assertThat(registry.displayName("device_field::health")).hasValue("Health");
    assertThat(DeviceKeys.HEALTH.isLongTail()).isFalse();
    assertThat(DeviceKeys.HEALTH.isDimension()).isFalse();
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
    assertThat(registry.getKey("device_field::uuid").get().inCoreMask()).isFalse();
    assertThat(registry.getKey("dimension::model").get().isLongTail()).isFalse();
    assertThat(registry.getKey("dimension::model").get().inCoreMask()).isTrue();
    assertThat(registry.getKey("dimension::model").get().isOverlay()).isFalse();
    assertThat(registry.getKey("host_field::host_name").get().isLongTail()).isFalse();
    assertThat(registry.getKey("host_field::host_name").get().isOverlay()).isFalse();
  }

  @Test
  public void dimensionsInCoreMask_stayLongTailForPresentation_butAreNotOverlays() {
    // release_version is fetched in the core mask by DeviceKeys.VERSION: it remains a long-tail
    // dimension for UI display/catalog curation, but isOverlay() is false so it never triggers an
    // on-demand pull.
    DeviceKeyDescriptor releaseVersion = registry.getKey("dimension::release_version").get();
    assertThat(releaseVersion.isLongTail()).isTrue();
    assertThat(releaseVersion.inCoreMask()).isTrue();
    assertThat(releaseVersion.isOverlay()).isFalse();

    // Dimension names keep exact lab-reported casing and whitespace so mixed-version controllers
    // whose DimensionsMask is exact-case still trigger an on-demand pull for case/padded variants.
    DeviceKeyDescriptor upperModel = registry.getKey("dimension::Model").get();
    assertThat(upperModel.id()).isEqualTo("dimension::Model");
    assertThat(upperModel.isLongTail()).isTrue();
    assertThat(upperModel.inCoreMask()).isFalse();
    assertThat(upperModel.isOverlay()).isTrue();

    DeviceKeyDescriptor paddedRelease = registry.getKey("dimension::release_version ").get();
    assertThat(paddedRelease.inCoreMask()).isFalse();
    assertThat(paddedRelease.isOverlay()).isTrue();

    // A dimension absent from the core mask remains an on-demand overlay.
    DeviceKeyDescriptor carrier = registry.getKey("dimension::carrier").get();
    assertThat(carrier.isLongTail()).isTrue();
    assertThat(carrier.inCoreMask()).isFalse();
    assertThat(carrier.isOverlay()).isTrue();
  }

  @Test
  public void constructor_rejectsInvalidPreIndexedDimensionNames() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new DeviceKeyRegistry(ImmutableList.of(), ImmutableSet.of("")) {});
    assertThrows(
        IllegalArgumentException.class,
        () -> new DeviceKeyRegistry(ImmutableList.of(), ImmutableSet.of(" pool")) {});
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DeviceKeyRegistry(ImmutableList.of(), ImmutableSet.of("subdevice_dimensions")) {});
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

    assertThat(registry.createLongTailDimensionKey("carrier")).isPresent();
    assertThat(registry.createLongTailDimensionKey("carrier").get().isLongTail()).isTrue();
    assertThat(registry.getKey("dimension::carrier"))
        .isEqualTo(registry.getKey("dimension::carrier"));
    assertThat(registry.createProjectLongTailHostPropertyKey("rack")).isPresent();
    assertThat(registry.createProjectLongTailHostPropertyKey("rack").get().isLongTail()).isTrue();
    assertThat(registry.getKey("host_property::rack"))
        .isEqualTo(registry.getKey("host_property::rack"));

    // Rejects bare prefixes and empty/blank/null values gracefully.
    assertThat(registry.getKey("dimension::")).isEmpty();
    assertThat(registry.getKey("dimension::   ")).isEmpty();
    assertThat(registry.getKey("host_property::")).isEmpty();
    assertThat(registry.getKey("host_property::   ")).isEmpty();
    assertThat(registry.createLongTailDimensionKey("")).isEmpty();
    assertThat(registry.createLongTailDimensionKey("  ")).isEmpty();
    assertThat(registry.createLongTailDimensionKey(null)).isEmpty();
    assertThat(registry.createProjectLongTailHostPropertyKey("")).isEmpty();
    assertThat(registry.createProjectLongTailHostPropertyKey("  ")).isEmpty();
    assertThat(registry.createProjectLongTailHostPropertyKey(null)).isEmpty();

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
  public void description_isSetOnlyWhereTheValueNeedsExplaining() {
    assertThat(DeviceKeys.VERSION.display().description()).contains("release_version");
    assertThat(DeviceKeys.VERSION.display().description()).contains("software_version");
    assertThat(DeviceKeys.HEALTH.display().description()).contains("can run tests");
    assertThat(DeviceKeys.SDK_VERSION.display().description()).isEmpty();
    assertThat(DeviceKeys.MODEL.display().description()).isEmpty();
    assertThat(registry.getKey("dimension::carrier").get().display().description()).isEmpty();
  }

  @Test
  public void deriveDeviceInfoMask_unionsCommonSources_excludesOnePKeys() {
    DeviceInfoMask mask = registry.deriveDeviceInfoMask();
    assertThat(mask.getFieldMask().getPathsList())
        .containsAtLeast(
            "device_locator.id",
            "device_status",
            "device_feature.type",
            "device_feature.driver",
            "device_feature.decorator",
            "device_feature.composite_dimension",
            // The health key reads the upstream category and the quarantined temp dimension.
            "health_category",
            "device_condition");
    assertThat(mask.getSupportedDimensionsMask().getDimensionNamesList())
        .containsAtLeast(
            "model",
            "os",
            "release_version",
            "sdk_version",
            "software_version",
            "device_form",
            "device_class_name",
            "manufacturer",
            "label",
            "battery_level",
            "battery_temperature",
            "hinge_angle");
    // Quarantine is a temp dimension in device_condition, never a composite dimension name.
    assertThat(mask.getSupportedDimensionsMask().getDimensionNamesList())
        .doesNotContain("quarantined");
    // 1P-only owner and executor fields are not pulled in OSS.
    assertThat(mask.getFieldMask().getPathsList()).doesNotContain("device_feature.owner");
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
  public void healthSource_extractsTheDeviceHealthTitleFromTheFieldsItMasks() {
    DeviceInfoSource healthSource = DeviceKeys.HEALTH.deviceInfoSources().get(0);
    DeviceInfo normalLookingIdle =
        DeviceInfo.newBuilder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .build();
    DeviceInfo needsRepairUpstream =
        normalLookingIdle.toBuilder()
            .setHealthCategory(HealthCategory.HEALTH_CATEGORY_NEED_MANUAL_REPAIR)
            .build();
    DeviceInfo quarantinedIdle =
        normalLookingIdle.toBuilder()
            .setDeviceCondition(
                DeviceCondition.newBuilder()
                    .addTempDimension(
                        TempDimension.newBuilder()
                            .setDimension(
                                DeviceDimension.newBuilder()
                                    .setName("quarantined")
                                    .setValue("true"))))
            .build();

    assertThat(healthSource.extract(normalLookingIdle)).containsExactly("In Service (Idle)");
    assertThat(healthSource.extract(needsRepairUpstream)).containsExactly("Needs Manual Repair");
    assertThat(healthSource.extract(quarantinedIdle)).containsExactly("Quarantined");
    assertThat(healthSource.maskFieldPaths())
        .containsExactly(
            "device_status", "device_feature.type", "health_category", "device_condition");
    assertThat(healthSource.maskDimensionNames()).isEmpty();
  }

  @Test
  public void projectHostKey_preservesIsIntComparable() {
    DeviceKeyDescriptor projectedDeviceCount =
        DeviceKeys.projectHostKey(HostKeys.DEVICE_COUNT, KeyDisplay.of("Host Device Count"));
    assertThat(projectedDeviceCount.isIntComparable()).isTrue();
    assertThat(DeviceKeys.HOST_NAME.isIntComparable()).isFalse();
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
