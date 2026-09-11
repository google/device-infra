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
  public void none_keepsEverything() {
    CompiledDeviceInfoMask mask = CompiledDeviceInfoMask.none();

    assertThat(mask.keepsDeviceInfo()).isTrue();
    assertThat(mask.hasDimensionMask()).isFalse();
    assertThat(mask.keepsDeviceCondition()).isTrue();
    assertThat(mask.keepsDeviceLocator()).isTrue();
    assertThat(mask.keepsDeviceStatus()).isTrue();
    assertThat(mask.keepsDeviceFeature()).isTrue();
    assertThat(mask.keepsSupportedDimension("model")).isTrue();
    assertThat(mask.keepsRequiredDimension("sim_type")).isTrue();

    DeviceInfo.Builder builder = DeviceInfo.newBuilder();
    if (mask.keepsDeviceLocator()) {
      builder.setDeviceLocator(
          mask.trimDeviceLocator(DeviceLocator.newBuilder().setId("d1").build()));
    }
    if (mask.keepsDeviceStatus()) {
      builder.setDeviceStatus(DeviceStatus.IDLE);
    }
    if (mask.keepsDeviceFeature()) {
      builder.setDeviceFeature(mask.projectDeviceFeature(FULL_FEATURE));
    }
    DeviceInfo info = mask.build(builder);

    assertThat(info.getDeviceLocator().getId()).isEqualTo("d1");
    assertThat(info.getDeviceStatus()).isEqualTo(DeviceStatus.IDLE);
    assertThat(info.getDeviceFeature()).isEqualTo(FULL_FEATURE);
  }

  @Test
  public void of_nullOrDefault_returnsNone() {
    assertThat(CompiledDeviceInfoMask.of(null)).isSameInstanceAs(CompiledDeviceInfoMask.none());
    assertThat(CompiledDeviceInfoMask.of(DeviceInfoMask.getDefaultInstance()))
        .isSameInstanceAs(CompiledDeviceInfoMask.none());
  }

  @Test
  public void of_emptyFieldMask_dropsDeviceInfo() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()).build();
    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(mask);

    assertThat(compiled.keepsDeviceInfo()).isFalse();
    assertThat(compiled.matchesField("device_locator")).isFalse();
    assertThat(compiled.matchesField("device_feature")).isFalse();
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
    assertThat(compiled.needsSupportedDimensions()).isTrue();
    assertThat(compiled.needsRequiredDimensions()).isFalse();
    assertThat(compiled.keepsSupportedDimension("model")).isTrue();
    assertThat(compiled.keepsSupportedDimension("MODEL")).isTrue(); // case-insensitive
    assertThat(compiled.keepsSupportedDimension("carrier")).isFalse();

    boolean[] conditionCalled = new boolean[] {false};
    DeviceInfo.Builder builder = DeviceInfo.newBuilder();
    if (compiled.keepsDeviceLocator()) {
      builder.setDeviceLocator(
          compiled.trimDeviceLocator(DeviceLocator.newBuilder().setId("d1").build()));
    }
    if (compiled.keepsDeviceStatus()) {
      builder.setDeviceStatus(DeviceStatus.BUSY);
    }
    if (compiled.keepsDeviceCondition()) {
      conditionCalled[0] = true;
    }
    if (compiled.keepsDeviceFeature()) {
      builder.setDeviceFeature(compiled.projectDeviceFeature(FULL_FEATURE));
    }
    DeviceInfo info = compiled.build(builder);

    assertThat(conditionCalled[0]).isFalse();
    assertThat(info.getDeviceLocator().getId()).isEqualTo("d1");
    // Device status was not requested in field_mask so it was skipped.
    assertThat(info.getDeviceStatus()).isEqualTo(DeviceStatus.INIT);
    // Composite dimension should only retain model dimension.
    assertThat(info.getDeviceFeature().getCompositeDimension().getSupportedDimensionList())
        .containsExactly(DeviceDimension.newBuilder().setName("model").setValue("pixel 5").build());
    assertThat(info.getDeviceFeature().getCompositeDimension().getRequiredDimensionList())
        .isEmpty();
    // Properties must be preserved!
    assertThat(info.getDeviceFeature().getProperties().getPropertyList()).hasSize(1);
    assertThat(info.getDeviceFeature().getProperties().getProperty(0).getName()).isEqualTo("p1");
  }
}
