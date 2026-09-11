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

import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.protobuf.FieldMask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CompiledLabInfoMaskTest {

  @Test
  public void none_keepsEverything() {
    CompiledLabInfoMask mask = CompiledLabInfoMask.none();

    assertThat(mask.keepsLabInfo()).isTrue();
    assertThat(mask.hasFieldMask()).isFalse();
    assertThat(mask.keepsLabStatus()).isTrue();
    assertThat(mask.keepsLabLocator()).isTrue();
    assertThat(mask.keepsLabServerSetting()).isTrue();
    assertThat(mask.keepsLabServerFeature()).isTrue();
    assertThat(mask.matchesField("any_field")).isTrue();

    LabInfo.Builder builder = LabInfo.newBuilder();
    if (mask.keepsLabLocator()) {
      builder.setLabLocator(
          mask.trimLabLocator(
              LabLocator.newBuilder().setHostName("host1").setIp("1.2.3.4").build()));
    }
    if (mask.keepsLabStatus()) {
      builder.setLabStatus(LabStatus.LAB_RUNNING);
    }
    LabInfo info = mask.build(builder);

    assertThat(info.getLabLocator().getHostName()).isEqualTo("host1");
    assertThat(info.getLabLocator().getIp()).isEqualTo("1.2.3.4");
    assertThat(info.getLabStatus()).isEqualTo(LabStatus.LAB_RUNNING);
  }

  @Test
  public void of_nullOrDefault_returnsNone() {
    assertThat(CompiledLabInfoMask.of(null)).isSameInstanceAs(CompiledLabInfoMask.none());
    assertThat(CompiledLabInfoMask.of(LabInfoMask.getDefaultInstance()))
        .isSameInstanceAs(CompiledLabInfoMask.none());
  }

  @Test
  public void of_emptyFieldMask_dropsLabInfo() {
    LabInfoMask mask =
        LabInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()).build();
    CompiledLabInfoMask compiled = CompiledLabInfoMask.of(mask);

    assertThat(compiled.keepsLabInfo()).isFalse();
    assertThat(compiled.matchesField("lab_status")).isFalse();
  }

  @Test
  public void of_selectiveProjection_prunesUnrequestedFieldsAndSkipsUnrequestedGetters() {
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

    LabInfo.Builder builder = LabInfo.newBuilder();
    if (compiled.keepsLabLocator()) {
      builder.setLabLocator(
          compiled.trimLabLocator(
              LabLocator.newBuilder().setHostName("host1").setIp("1.2.3.4").build()));
    }
    if (compiled.keepsLabStatus()) {
      builder.setLabStatus(LabStatus.LAB_RUNNING);
    }
    LabInfo info = compiled.build(builder);

    assertThat(info.getLabLocator().getHostName()).isEqualTo("host1");
    // IP is not in field_mask, pruned.
    assertThat(info.getLabLocator().getIp()).isEmpty();
    assertThat(info.getLabStatus()).isEqualTo(LabStatus.LAB_RUNNING);
    assertThat(info.hasLabServerSetting()).isFalse();
    assertThat(info.hasLabServerFeature()).isFalse();
  }
}
