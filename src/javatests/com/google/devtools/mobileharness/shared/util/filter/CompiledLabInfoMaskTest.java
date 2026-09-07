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

import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.protobuf.FieldMask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CompiledLabInfoMaskTest {

  @Test
  public void retainAll_keepsEverything() {
    CompiledLabInfoMask mask = CompiledLabInfoMask.retainAll();

    assertThat(mask.keepsLabInfo()).isTrue();
    assertThat(mask.keepsLabLocator()).isTrue();
    assertThat(mask.keepsLabStatus()).isTrue();
    assertThat(mask.keepsLabServerSetting()).isTrue();
    assertThat(mask.keepsLabServerFeature()).isTrue();
    assertThat(mask.keepsHostProperties()).isTrue();
    assertThat(mask.hasFieldMask()).isFalse();
    assertThat(mask.matchesField("any_field")).isTrue();

    LabLocator locator = LabLocator.newBuilder().setHostName("host1").setIp("1.2.3.4").build();
    assertThat(mask.projectLabLocator(locator)).isSameInstanceAs(locator);
  }

  @Test
  public void of_defaultInstance_returnsRetainAll() {
    assertThat(CompiledLabInfoMask.of(LabInfoMask.getDefaultInstance()))
        .isSameInstanceAs(CompiledLabInfoMask.retainAll());
  }

  @Test
  public void of_emptyFieldMask_dropsLabInfo() {
    LabInfoMask mask =
        LabInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()).build();
    CompiledLabInfoMask compiled = CompiledLabInfoMask.of(mask);

    assertThat(compiled.keepsLabInfo()).isFalse();
    assertThat(compiled.keepsLabLocator()).isFalse();
    assertThat(compiled.keepsLabStatus()).isFalse();
    assertThat(compiled.matchesField("lab_status")).isFalse();
  }

  @Test
  public void of_selectiveProjection_prunesUnrequestedFields() {
    LabInfoMask mask =
        LabInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder().addPaths("lab_locator.host_name").addPaths("lab_status"))
            .build();
    CompiledLabInfoMask compiled = CompiledLabInfoMask.of(mask);

    assertThat(compiled.keepsLabInfo()).isTrue();
    assertThat(compiled.keepsLabLocator()).isTrue();
    assertThat(compiled.keepsLabStatus()).isTrue();
    assertThat(compiled.keepsLabServerSetting()).isFalse();
    assertThat(compiled.keepsLabServerFeature()).isFalse();
    assertThat(compiled.matchesField("lab_locator.host_name")).isTrue();
    assertThat(compiled.matchesField("lab_locator.ip")).isFalse();

    LabLocator fullLocator = LabLocator.newBuilder().setHostName("host1").setIp("1.2.3.4").build();
    LabLocator projected = compiled.projectLabLocator(fullLocator);
    assertThat(projected.getHostName()).isEqualTo("host1");
    assertThat(projected.getIp()).isEmpty();
  }
}
