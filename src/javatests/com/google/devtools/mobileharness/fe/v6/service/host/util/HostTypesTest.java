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

package com.google.devtools.mobileharness.fe.v6.service.host.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HostTypesTest {

  @Test
  public void determineLabTypeDisplayNames_noData_returnsUnknown() {
    assertThat(HostTypes.determineLabTypeDisplayNames(Optional.empty(), Optional.empty()))
        .containsExactly("Unknown");
  }

  @Test
  public void determineLabTypeDisplayNames_fusion() {
    assertThat(HostTypes.determineLabTypeDisplayNames(Optional.empty(), Optional.of("FUSION_LAB")))
        .containsExactly("Fusion Lab");
  }

  @Test
  public void determineLabTypeDisplayNames_fusion_fromProp() {
    assertThat(
            HostTypes.determineLabTypeDisplayNames(
                Optional.of(createLabInfoWithProperty("lab_type", "fusion")), Optional.empty()))
        .containsExactly("Fusion Lab");
  }

  @Test
  public void determineLabTypeDisplayNames_core_fromEnum() {
    assertThat(HostTypes.determineLabTypeDisplayNames(Optional.empty(), Optional.of("SHARED_LAB")))
        .containsExactly("Core Lab");
  }

  @Test
  public void determineLabTypeDisplayNames_core_fromProp() {
    assertThat(
            HostTypes.determineLabTypeDisplayNames(
                Optional.of(createLabInfoWithProperty("lab_type", "core")), Optional.empty()))
        .containsExactly("Core Lab");
  }

  @Test
  public void determineLabTypeDisplayNames_slaas() {
    assertThat(
            HostTypes.determineLabTypeDisplayNames(
                Optional.of(createLabInfoWithProperty("lab_type", "slaas")), Optional.empty()))
        .containsExactly("Satellite Lab (SLaaS)");
  }

  @Test
  public void determineLabTypeDisplayNames_satellite_fromProp() {
    assertThat(
            HostTypes.determineLabTypeDisplayNames(
                Optional.of(createLabInfoWithProperty("lab_type", "satellite")), Optional.empty()))
        .containsExactly("Satellite Lab");
  }

  @Test
  public void determineLabTypeDisplayNames_ate() {
    assertThat(HostTypes.determineLabTypeDisplayNames(Optional.empty(), Optional.of("MH_ATE_LAB")))
        .containsExactly("Satellite Lab", "ATE Lab")
        .inOrder();
  }

  @Test
  public void determineLabTypeDisplayNames_field() {
    assertThat(
            HostTypes.determineLabTypeDisplayNames(
                Optional.empty(), Optional.of("RIEMANN_FIELD_LAB")))
        .containsExactly("Satellite Lab", "Riemann Field Lab")
        .inOrder();
  }

  private LabInfo createLabInfoWithProperty(String key, String value) {
    return LabInfo.newBuilder()
        .setLabServerFeature(
            LabServerFeature.newBuilder()
                .setHostProperties(
                    HostProperties.newBuilder()
                        .addHostProperty(HostProperty.newBuilder().setKey(key).setValue(value))))
        .build();
  }
}
