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

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.shared.util.filter.FieldMaskCompiler.FieldState;
import com.google.protobuf.FieldMask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FieldMaskCompilerTest {

  @Test
  public void of_nullMask_retainsAll() {
    FieldMaskCompiler compiler = FieldMaskCompiler.of(null);

    assertThat(compiler.isAllRetained()).isTrue();
    assertThat(compiler.isNoneRetained()).isFalse();
    assertThat(compiler.keepsField("device_status")).isTrue();
    assertThat(compiler.isFull("device_status")).isTrue();
    assertThat(compiler.getFieldState("device_status")).isEqualTo(FieldState.FULL);
    assertThat(compiler.extractSubMask("device_locator")).isEqualTo(FieldMask.getDefaultInstance());

    DeviceLocator locator =
        DeviceLocator.newBuilder()
            .setId("d1")
            .setLabLocator(LabLocator.newBuilder().setIp("1.2.3.4").build())
            .build();
    assertThat(compiler.trimSubMessage("device_locator", locator)).isEqualTo(locator);
    assertThat(compiler.hasUnknownPaths(ImmutableSet.of("device_status"))).isFalse();
  }

  @Test
  public void of_emptyMask_retainsNone() {
    FieldMaskCompiler compiler = FieldMaskCompiler.of(FieldMask.getDefaultInstance());

    assertThat(compiler.isAllRetained()).isFalse();
    assertThat(compiler.isNoneRetained()).isTrue();
    assertThat(compiler.keepsField("device_status")).isFalse();
    assertThat(compiler.isFull("device_status")).isFalse();
    assertThat(compiler.getFieldState("device_status")).isEqualTo(FieldState.OMITTED);
    assertThat(compiler.extractSubMask("device_locator")).isNull();

    DeviceLocator locator = DeviceLocator.newBuilder().setId("d1").build();
    assertThat(compiler.trimSubMessage("device_locator", locator)).isNull();
    assertThat(compiler.hasUnknownPaths(ImmutableSet.of("device_status"))).isFalse();
  }

  @Test
  public void of_partialAndFullPaths_correctStates() {
    FieldMask mask =
        FieldMask.newBuilder()
            .addPaths("device_locator.id")
            .addPaths("device_status")
            .addPaths("device_feature.composite_dimension.supported_dimension")
            .build();
    FieldMaskCompiler compiler = FieldMaskCompiler.of(mask);

    assertThat(compiler.isAllRetained()).isFalse();
    assertThat(compiler.isNoneRetained()).isFalse();

    // Full field
    assertThat(compiler.keepsField("device_status")).isTrue();
    assertThat(compiler.isFull("device_status")).isTrue();
    assertThat(compiler.getFieldState("device_status")).isEqualTo(FieldState.FULL);
    assertThat(compiler.extractSubMask("device_status")).isEqualTo(FieldMask.getDefaultInstance());

    // Partial field: device_locator
    assertThat(compiler.keepsField("device_locator")).isTrue();
    assertThat(compiler.isFull("device_locator")).isFalse();
    assertThat(compiler.getFieldState("device_locator")).isEqualTo(FieldState.PARTIAL);
    FieldMask locatorSubMask = compiler.extractSubMask("device_locator");
    assertThat(locatorSubMask).isNotNull();
    assertThat(locatorSubMask.getPathsList()).containsExactly("id");

    // Partial field: device_feature
    assertThat(compiler.keepsField("device_feature")).isTrue();
    assertThat(compiler.isFull("device_feature")).isFalse();
    assertThat(compiler.getFieldState("device_feature")).isEqualTo(FieldState.PARTIAL);
    FieldMask featureSubMask = compiler.extractSubMask("device_feature");
    assertThat(featureSubMask).isNotNull();
    assertThat(featureSubMask.getPathsList())
        .containsExactly("composite_dimension.supported_dimension");

    // Omitted field
    assertThat(compiler.keepsField("device_condition")).isFalse();
    assertThat(compiler.isFull("device_condition")).isFalse();
    assertThat(compiler.getFieldState("device_condition")).isEqualTo(FieldState.OMITTED);
    assertThat(compiler.extractSubMask("device_condition")).isNull();
  }

  @Test
  public void trimSubMessage_partialPruning() {
    FieldMask mask = FieldMask.newBuilder().addPaths("device_locator.id").build();
    FieldMaskCompiler compiler = FieldMaskCompiler.of(mask);

    DeviceLocator fullLocator =
        DeviceLocator.newBuilder()
            .setId("d1")
            .setLabLocator(LabLocator.newBuilder().setIp("1.2.3.4").build())
            .build();

    DeviceLocator trimmedLocator = compiler.trimSubMessage("device_locator", fullLocator);
    assertThat(trimmedLocator).isNotNull();
    assertThat(trimmedLocator.getId()).isEqualTo("d1");
    assertThat(trimmedLocator.hasLabLocator()).isFalse();
  }

  @Test
  public void fullPathSubsumesSubPaths() {
    FieldMask mask =
        FieldMask.newBuilder()
            .addPaths("device_locator.id")
            .addPaths("device_locator") // Full path specified
            .build();
    FieldMaskCompiler compiler = FieldMaskCompiler.of(mask);

    assertThat(compiler.isFull("device_locator")).isTrue();
    assertThat(compiler.getFieldState("device_locator")).isEqualTo(FieldState.FULL);
    assertThat(compiler.extractSubMask("device_locator")).isEqualTo(FieldMask.getDefaultInstance());
  }

  @Test
  public void hasUnknownPaths() {
    FieldMask mask =
        FieldMask.newBuilder().addPaths("device_status").addPaths("unknown_field.sub").build();
    FieldMaskCompiler compiler = FieldMaskCompiler.of(mask);

    assertThat(compiler.hasUnknownPaths(ImmutableSet.of("device_status", "device_locator")))
        .isTrue();
    assertThat(
            compiler.hasUnknownPaths(
                ImmutableSet.of("device_status", "device_locator", "unknown_field")))
        .isFalse();
  }
}
