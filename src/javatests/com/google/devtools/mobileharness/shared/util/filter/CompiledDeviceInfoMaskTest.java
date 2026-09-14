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

package com.google.devtools.mobileharness.shared.util.filter;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceProperties;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceProperty;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask.DimensionsMask;
import com.google.protobuf.FieldMask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CompiledDeviceInfoMaskTest {

  private static final DeviceFeature FULL_FEATURE =
      DeviceFeature.newBuilder()
          .addOwner("owner1")
          .addType("AndroidRealDevice")
          .addDriver("AndroidInstrumentation")
          .setProperties(
              DeviceProperties.newBuilder()
                  .addProperty(DeviceProperty.newBuilder().setName("p1").setValue("v1")))
          .setCompositeDimension(
              DeviceCompositeDimension.newBuilder()
                  .addSupportedDimension(
                      DeviceDimension.newBuilder().setName("model").setValue("pixel 5"))
                  .addSupportedDimension(
                      DeviceDimension.newBuilder().setName("carrier").setValue("verizon"))
                  .addRequiredDimension(
                      DeviceDimension.newBuilder().setName("pool").setValue("shared")))
          .build();

  @Test
  public void retainAll_keepsEverything() {
    CompiledDeviceInfoMask mask = CompiledDeviceInfoMask.retainAll();

    assertThat(mask.keepsDeviceInfo()).isTrue();
    assertThat(mask.keepsDeviceCondition()).isTrue();
    assertThat(mask.keepsDeviceFeature()).isTrue();
    assertThat(mask.projectDeviceFeature(FULL_FEATURE)).isSameInstanceAs(FULL_FEATURE);
  }

  @Test
  public void of_defaultInstance_returnsRetainAll() {
    assertThat(CompiledDeviceInfoMask.of(DeviceInfoMask.getDefaultInstance()))
        .isSameInstanceAs(CompiledDeviceInfoMask.retainAll());
  }

  @Test
  public void of_emptyFieldMask_dropsDeviceInfo() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()).build();
    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(mask);

    assertThat(compiled.keepsDeviceInfo()).isFalse();
    assertThat(compiled.keepsDeviceFeature()).isFalse();
    assertThat(
            compiled
                .newMaskedDeviceInfoBuilder()
                .setFields(
                    "id1",
                    (deviceInfoBuilder, deviceId) ->
                        deviceInfoBuilder.setDeviceStatus(DeviceStatus.IDLE))
                .build())
        .isEmpty();
  }

  @Test
  public void of_singleDimensionProjection_filtersDimensionsAndPreservesProperties() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder()
                    .addPaths("device_locator")
                    .addPaths("device_feature.composite_dimension.supported_dimension")
                    .addPaths("device_feature.properties"))
            .setSupportedDimensionsMask(DimensionsMask.newBuilder().addDimensionNames("model"))
            .build();
    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(mask);

    assertThat(compiled.keepsDeviceInfo()).isTrue();
    assertThat(compiled.keepsDeviceCondition()).isFalse();
    assertThat(compiled.keepsDeviceFeature()).isTrue();

    DeviceFeature projected = compiled.projectDeviceFeature(FULL_FEATURE);
    assertThat(projected.getCompositeDimension().getSupportedDimensionList())
        .containsExactly(DeviceDimension.newBuilder().setName("model").setValue("pixel 5").build());
    assertThat(projected.getCompositeDimension().getRequiredDimensionList()).isEmpty();
    assertThat(projected.getOwnerList()).isEmpty();
    assertThat(projected.getTypeList()).isEmpty();
    assertThat(projected.getProperties().getPropertyList()).hasSize(1);
    assertThat(projected.getProperties().getProperty(0).getName()).isEqualTo("p1");
  }

  @Test
  public void projectDeviceFeature_whenUnmasked_returnsSameReference() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder().addPaths("device_locator").addPaths("device_feature"))
            .build();
    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(mask);

    assertThat(compiled.projectDeviceFeature(FULL_FEATURE)).isSameInstanceAs(FULL_FEATURE);
  }

  @Test
  public void maskedDeviceInfoBuilder_appliesSubFieldGatesAndBuildTrim() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder()
                    .addPaths("device_locator.id")
                    .addPaths("device_feature.type"))
            .build();
    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(mask);

    DeviceInfo built =
        compiled
            .newMaskedDeviceInfoBuilder()
            .setFields(
                "dev-1",
                (deviceInfoBuilder, deviceId) ->
                    deviceInfoBuilder
                        .setDeviceLocator(DeviceLocator.newBuilder().setId(deviceId))
                        .setDeviceStatus(DeviceStatus.IDLE))
            .setMaskedDeviceCondition(
                "dev-1",
                deviceId -> {
                  throw new AssertionError("device_condition should not be evaluated");
                })
            .setMaskedDeviceFeature("dev-1", deviceId -> FULL_FEATURE)
            .build()
            .orElseThrow();

    assertThat(built.getDeviceLocator().getId()).isEqualTo("dev-1");
    assertThat(built.getDeviceStatus()).isEqualTo(DeviceStatus.INIT);
    assertThat(built.hasDeviceCondition()).isFalse();
    assertThat(built.getDeviceFeature().getTypeList()).containsExactly("AndroidRealDevice");
    assertThat(built.getDeviceFeature().getOwnerList()).isEmpty();
    assertThat(built.getDeviceFeature().hasCompositeDimension()).isFalse();
  }

  @Test
  public void of_labQuery_enablesOnlyGroupBySubFieldGatesAndMarksNeedsPostSortTrim() {
    com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery query =
        com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.newBuilder()
            .setMask(
                com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask
                    .newBuilder()
                    .setDeviceInfoMask(
                        DeviceInfoMask.newBuilder()
                            .setFieldMask(FieldMask.newBuilder().addPaths("device_locator.id"))))
            .setDeviceViewRequest(
                com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery
                    .DeviceViewRequest.newBuilder()
                    .addDeviceGroupOperation(
                        com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery
                            .DeviceViewRequest.DeviceGroupOperation.newBuilder()
                            .setDeviceGroupCondition(
                                com.google.devtools.mobileharness.api.query.proto.LabQueryProto
                                    .LabQuery.DeviceViewRequest.DeviceGroupCondition.newBuilder()
                                    .setTypeList(
                                        com.google.devtools.mobileharness.api.query.proto
                                            .LabQueryProto.LabQuery.DeviceViewRequest
                                            .DeviceGroupCondition.TypeList.getDefaultInstance()))))
            .build();
    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(query);

    assertThat(compiled.needsPostSortTrim()).isTrue();
    DeviceFeature projected = compiled.projectDeviceFeature(FULL_FEATURE);
    assertThat(projected.getTypeList()).containsExactly("AndroidRealDevice");
    assertThat(projected.getOwnerList()).isEmpty();
    assertThat(projected.getDriverList()).isEmpty();
    assertThat(projected.hasCompositeDimension()).isFalse();
  }
}
