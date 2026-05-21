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
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UiLabType;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HostTypesTest {

  @Test
  public void determineUiLabTypes_noData_returnsEmpty() {
    assertThat(HostTypes.determineUiLabTypes(Optional.empty(), Optional.empty())).isEmpty();
  }

  @Test
  public void determineUiLabTypes_fusion() {
    assertThat(HostTypes.determineUiLabTypes(Optional.empty(), Optional.of("FUSION_LAB")))
        .containsExactly(UiLabType.FUSION);
  }

  @Test
  public void determineUiLabTypes_core_fromEnum() {
    assertThat(HostTypes.determineUiLabTypes(Optional.empty(), Optional.of("SHARED_LAB")))
        .containsExactly(UiLabType.CORE);
  }

  @Test
  public void determineUiLabTypes_core_fromProp() {
    assertThat(
            HostTypes.determineUiLabTypes(
                Optional.of(createLabInfoWithProperty("lab_type", "core")), Optional.empty()))
        .containsExactly(UiLabType.CORE);
  }

  @Test
  public void determineUiLabTypes_slaas() {
    assertThat(
            HostTypes.determineUiLabTypes(
                Optional.of(createLabInfoWithProperty("lab_type", "slaas")), Optional.empty()))
        .containsExactly(UiLabType.SATELLITE, UiLabType.SLAAS)
        .inOrder();
  }

  @Test
  public void determineUiLabTypes_satellite_fromProp() {
    assertThat(
            HostTypes.determineUiLabTypes(
                Optional.of(createLabInfoWithProperty("lab_type", "satellite")), Optional.empty()))
        .containsExactly(UiLabType.SATELLITE);
  }

  @Test
  public void determineUiLabTypes_ate() {
    assertThat(
            HostTypes.determineUiLabTypes(
                Optional.of(createLabInfoWithProperty("lab_type", "satellite")),
                Optional.of("MH_ATE_LAB")))
        .containsExactly(UiLabType.SATELLITE, UiLabType.ATE)
        .inOrder();
  }

  @Test
  public void determineUiLabTypes_field() {
    assertThat(
            HostTypes.determineUiLabTypes(
                Optional.of(createLabInfoWithProperty("lab_type", "satellite")),
                Optional.of("RIEMANN_FIELD_LAB")))
        .containsExactly(UiLabType.SATELLITE, UiLabType.RIEMANN_FIELD)
        .inOrder();
  }

  @Test
  public void determineUiLabTypes_slaasAndFusion() {
    LabInfo labInfo =
        LabInfo.newBuilder()
            .setLabServerFeature(
                LabServerFeature.newBuilder()
                    .setHostProperties(
                        HostProperties.newBuilder()
                            .addHostProperty(
                                HostProperty.newBuilder().setKey("lab_type").setValue("slaas"))
                            .addHostProperty(
                                HostProperty.newBuilder().setKey("dm_type").setValue("fusion"))))
            .build();
    assertThat(HostTypes.determineUiLabTypes(Optional.of(labInfo), Optional.empty()))
        .containsExactly(UiLabType.SATELLITE, UiLabType.SLAAS, UiLabType.FUSION)
        .inOrder();
  }

  @Test
  public void determineUiLabTypes_coreAndFusion() {
    LabInfo labInfo =
        LabInfo.newBuilder()
            .setLabServerFeature(
                LabServerFeature.newBuilder()
                    .setHostProperties(
                        HostProperties.newBuilder()
                            .addHostProperty(
                                HostProperty.newBuilder().setKey("lab_type").setValue("core"))
                            .addHostProperty(
                                HostProperty.newBuilder().setKey("dm_type").setValue("fusion"))))
            .build();
    assertThat(HostTypes.determineUiLabTypes(Optional.of(labInfo), Optional.empty()))
        .containsExactly(UiLabType.CORE, UiLabType.FUSION)
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
