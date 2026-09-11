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
import com.google.devtools.mobileharness.shared.util.filter.CompiledFieldMask.NodeState;
import com.google.protobuf.FieldMask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CompiledFieldMaskTest {

  @Test
  public void all_retainsEverything() {
    CompiledFieldMask mask = CompiledFieldMask.all();
    assertThat(mask.isAllRetained()).isTrue();
    assertThat(mask.isNoneRetained()).isFalse();
    assertThat(mask.stateOf("device_status")).isEqualTo(NodeState.FULL);
    assertThat(mask.keepsField("device_locator.id")).isTrue();
    assertThat(mask.extractSubMask("device_locator")).isEqualTo(FieldMask.getDefaultInstance());
    assertThat(mask.hasUnknownPaths(ImmutableSet.of("device_status"))).isFalse();
  }

  @Test
  public void emptyFieldMask_retainsNothing() {
    CompiledFieldMask mask = CompiledFieldMask.of(FieldMask.getDefaultInstance());
    assertThat(mask.isAllRetained()).isFalse();
    assertThat(mask.isNoneRetained()).isTrue();
    assertThat(mask.stateOf("device_status")).isEqualTo(NodeState.OMITTED);
    assertThat(mask.keepsField("device_status")).isFalse();
    assertThat(mask.extractSubMask("device_locator")).isNull();
    assertThat(mask.hasUnknownPaths(ImmutableSet.of("device_status"))).isFalse();
  }

  @Test
  public void stateOf_and_extractSubMask_distinguishesFullPartialAndOmitted() {
    FieldMask fieldMask =
        FieldMask.newBuilder()
            .addPaths("device_status")
            .addPaths("device_locator.id")
            .addPaths("device_locator.lab_locator.host_name")
            .build();
    CompiledFieldMask mask = CompiledFieldMask.of(fieldMask);

    assertThat(mask.stateOf("device_status")).isEqualTo(NodeState.FULL);
    assertThat(mask.extractSubMask("device_status")).isEqualTo(FieldMask.getDefaultInstance());

    assertThat(mask.stateOf("device_locator")).isEqualTo(NodeState.PARTIAL);
    assertThat(mask.extractSubMask("device_locator"))
        .isEqualTo(FieldMask.newBuilder().addPaths("id").addPaths("lab_locator.host_name").build());

    assertThat(mask.stateOf("device_feature")).isEqualTo(NodeState.OMITTED);
    assertThat(mask.extractSubMask("device_feature")).isNull();
  }

  @Test
  public void hasUnknownPaths_detectsUnrecognizedTopLevelFields() {
    FieldMask knownMask =
        FieldMask.newBuilder().addPaths("device_status").addPaths("device_locator.id").build();
    CompiledFieldMask compiledKnown = CompiledFieldMask.of(knownMask);
    assertThat(
            compiledKnown.hasUnknownPaths(
                ImmutableSet.of("device_status", "device_locator", "device_feature")))
        .isFalse();

    FieldMask unknownMask =
        FieldMask.newBuilder().addPaths("device_status").addPaths("future_field.sub_field").build();
    CompiledFieldMask compiledUnknown = CompiledFieldMask.of(unknownMask);
    assertThat(
            compiledUnknown.hasUnknownPaths(
                ImmutableSet.of("device_status", "device_locator", "device_feature")))
        .isTrue();
  }
}
