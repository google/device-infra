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

/** Unit tests for {@link CompiledFieldMask}. */
@RunWith(JUnit4.class)
public final class CompiledFieldMaskTest {

  private static FieldMask fieldMask(String... paths) {
    FieldMask.Builder builder = FieldMask.newBuilder();
    for (String path : paths) {
      builder.addPaths(path);
    }
    return builder.build();
  }

  @Test
  public void retainAll_retainsEverything() {
    CompiledFieldMask mask = CompiledFieldMask.retainAll();

    assertThat(mask.retainsAll()).isTrue();
    assertThat(mask.retainsNothing()).isFalse();
    assertThat(mask.isRequested("device_status")).isTrue();
    assertThat(mask.stateOf("device_status")).isEqualTo(NodeState.FULL);
    assertThat(mask.subMask("device_status")).isEmpty();
    assertThat(mask.hasUnknownTopLevelPaths(ImmutableSet.of("device_status"))).isFalse();
  }

  @Test
  public void retainNone_retainsNothing() {
    CompiledFieldMask mask = CompiledFieldMask.retainNone();

    assertThat(mask.retainsAll()).isFalse();
    assertThat(mask.retainsNothing()).isTrue();
    assertThat(mask.isRequested("device_status")).isFalse();
    assertThat(mask.stateOf("device_status")).isEqualTo(NodeState.OMITTED);
    assertThat(mask.subMask("device_status")).isEmpty();
  }

  @Test
  public void ofEmptyMask_retainsNothing() {
    CompiledFieldMask mask = CompiledFieldMask.of(fieldMask());

    assertThat(mask.retainsAll()).isFalse();
    assertThat(mask.retainsNothing()).isTrue();
    assertThat(mask.isRequested("device_status")).isFalse();
    assertThat(mask.stateOf("device_status")).isEqualTo(NodeState.OMITTED);
    assertThat(mask.subMask("device_status")).isEmpty();
  }

  @Test
  public void scalarField_isFullOrOmitted() {
    CompiledFieldMask mask = CompiledFieldMask.of(fieldMask("device_status"));

    assertThat(mask.stateOf("device_status")).isEqualTo(NodeState.FULL);
    assertThat(mask.subMask("device_status")).isEmpty();
    assertThat(mask.isRequested("device_status")).isTrue();

    assertThat(mask.stateOf("device_locator")).isEqualTo(NodeState.OMITTED);
    assertThat(mask.isRequested("device_locator")).isFalse();
  }

  @Test
  public void partialChild_reportsPartialWithStrippedSubMask() {
    CompiledFieldMask mask = CompiledFieldMask.of(fieldMask("device_locator.id"));

    assertThat(mask.stateOf("device_locator")).isEqualTo(NodeState.PARTIAL);
    assertThat(mask.subMask("device_locator")).hasValue(fieldMask("id"));
    assertThat(mask.isRequested("device_locator")).isTrue();
    assertThat(mask.isRequested("device_locator.id")).isTrue();
    assertThat(mask.isRequested("device_locator.name")).isFalse();
    assertThat(mask.stateOf("device_status")).isEqualTo(NodeState.OMITTED);
  }

  @Test
  public void ancestorPathCoversNestedField() {
    CompiledFieldMask mask = CompiledFieldMask.of(fieldMask("device_feature"));

    assertThat(mask.stateOf("device_feature")).isEqualTo(NodeState.FULL);
    assertThat(mask.subMask("device_feature")).isEmpty();
    assertThat(mask.isRequested("device_feature.composite_dimension.supported_dimension")).isTrue();
  }

  @Test
  public void nestedPath_marksAncestorPartialAndIsRequestedAlongTheChain() {
    CompiledFieldMask mask =
        CompiledFieldMask.of(fieldMask("device_feature.composite_dimension.supported_dimension"));

    assertThat(mask.stateOf("device_feature")).isEqualTo(NodeState.PARTIAL);
    assertThat(mask.subMask("device_feature"))
        .hasValue(fieldMask("composite_dimension.supported_dimension"));
    assertThat(mask.isRequested("device_feature")).isTrue();
    assertThat(mask.isRequested("device_feature.composite_dimension")).isTrue();
    assertThat(mask.isRequested("device_feature.composite_dimension.supported_dimension")).isTrue();
    assertThat(mask.isRequested("device_feature.composite_dimension.required_dimension")).isFalse();
  }

  @Test
  public void exactAndSubPath_prefersFull() {
    CompiledFieldMask mask = CompiledFieldMask.of(fieldMask("device_locator", "device_locator.id"));

    assertThat(mask.stateOf("device_locator")).isEqualTo(NodeState.FULL);
    assertThat(mask.subMask("device_locator")).isEmpty();
  }

  @Test
  public void hasUnknownTopLevelPaths_detectsRootsOutsideKnownFields() {
    CompiledFieldMask mask =
        CompiledFieldMask.of(fieldMask("device_status", "device_feature.composite_dimension"));

    assertThat(mask.hasUnknownTopLevelPaths(ImmutableSet.of("device_status", "device_feature")))
        .isFalse();
    assertThat(mask.hasUnknownTopLevelPaths(ImmutableSet.of("device_status"))).isTrue();
  }
}
