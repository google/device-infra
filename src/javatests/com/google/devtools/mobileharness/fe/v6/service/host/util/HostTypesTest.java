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
// Tests deprecated HostTypes methods retained for backward compatibility.
@SuppressWarnings("deprecation")
public final class HostTypesTest {

  @Test
  public void determineLabType_noData_returnsEmpty() {
    assertThat(HostTypes.determineLabType(Optional.empty(), Optional.empty())).isEmpty();
    assertThat(HostTypes.determineSearchLabType(Optional.empty(), Optional.empty())).isEmpty();
    assertThat(HostTypes.determineLabTypeDisplayNames(Optional.empty(), Optional.empty()))
        .isEmpty();
  }

  @Test
  public void determineDeviceManagerType_fusionFromEnum() {
    assertThat(HostTypes.determineLabType(Optional.empty(), Optional.of("FUSION_LAB"))).isEmpty();
    assertThat(HostTypes.determineDeviceManagerType(Optional.empty(), Optional.of("FUSION_LAB")))
        .isEqualTo("Fusion");
    assertThat(HostTypes.determineLabTypeDisplayNames(Optional.empty(), Optional.of("FUSION_LAB")))
        .containsExactly("Fusion Lab");
  }

  @Test
  public void determineDeviceManagerType_mhDefault() {
    assertThat(HostTypes.determineDeviceManagerType(Optional.empty(), Optional.of("SHARED_LAB")))
        .isEqualTo("MH");
  }

  @Test
  public void determineLabType_core_fromEnum() {
    assertThat(HostTypes.determineLabType(Optional.empty(), Optional.of("SHARED_LAB")))
        .hasValue("Core");
    assertThat(HostTypes.determineSearchLabType(Optional.empty(), Optional.of("SHARED_LAB")))
        .hasValue("Core Lab");
    assertThat(HostTypes.determineLabTypeDisplayNames(Optional.empty(), Optional.of("SHARED_LAB")))
        .containsExactly("Core Lab");
  }

  @Test
  public void determineLabType_core_fromProp() {
    Optional<LabInfo> labInfo = Optional.of(createLabInfoWithProperty("lab_type", "core"));
    assertThat(HostTypes.determineLabType(labInfo, Optional.empty())).hasValue("Core");
    assertThat(HostTypes.determineSearchLabType(labInfo, Optional.empty())).hasValue("Core Lab");
    assertThat(HostTypes.determineLabTypeDisplayNames(labInfo, Optional.empty()))
        .containsExactly("Core Lab");
  }

  @Test
  public void determineLabType_slaas() {
    Optional<LabInfo> labInfo = Optional.of(createLabInfoWithProperty("lab_type", "slaas"));
    assertThat(HostTypes.determineLabType(labInfo, Optional.empty())).hasValue("SLaaS");
    assertThat(HostTypes.determineSearchLabType(labInfo, Optional.empty())).hasValue("SLaaS");
    assertThat(HostTypes.determineLabTypeDisplayNames(labInfo, Optional.empty()))
        .containsExactly("Satellite Lab", "SLaaS")
        .inOrder();
  }

  @Test
  public void determineLabType_satellite_fromProp() {
    Optional<LabInfo> labInfo = Optional.of(createLabInfoWithProperty("lab_type", "satellite"));
    assertThat(HostTypes.determineLabType(labInfo, Optional.empty())).hasValue("Satellite");
    assertThat(HostTypes.determineSearchLabType(labInfo, Optional.empty()))
        .hasValue("Satellite Lab");
    assertThat(HostTypes.determineLabTypeDisplayNames(labInfo, Optional.empty()))
        .containsExactly("Satellite Lab");
  }

  @Test
  public void determineLabType_ate() {
    Optional<LabInfo> labInfo = Optional.of(createLabInfoWithProperty("lab_type", "satellite"));
    assertThat(HostTypes.determineLabType(labInfo, Optional.of("MH_ATE_LAB")))
        .hasValue("Satellite");
    assertThat(HostTypes.isAteLab(Optional.of("MH_ATE_LAB"))).isTrue();
    assertThat(HostTypes.isAteLab(Optional.of("MH_SATELLITE_LAB"))).isFalse();
    assertThat(HostTypes.determineLabTypeDisplayNames(labInfo, Optional.of("MH_ATE_LAB")))
        .containsExactly("Satellite Lab", "ATE Lab")
        .inOrder();
  }

  @Test
  public void determineLabType_field() {
    Optional<LabInfo> labInfo = Optional.of(createLabInfoWithProperty("lab_type", "satellite"));
    assertThat(HostTypes.determineLabType(labInfo, Optional.of("RIEMANN_FIELD_LAB")))
        .hasValue("Satellite");
    assertThat(HostTypes.determineLabTypeDisplayNames(labInfo, Optional.of("RIEMANN_FIELD_LAB")))
        .containsExactly("Satellite Lab", "Riemann Field Lab")
        .inOrder();
  }

  @Test
  public void determineLabType_slaasAndFusion() {
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
    assertThat(HostTypes.determineLabType(Optional.of(labInfo), Optional.empty()))
        .hasValue("SLaaS");
    assertThat(HostTypes.determineSearchLabType(Optional.of(labInfo), Optional.empty()))
        .hasValue("SLaaS");
    assertThat(HostTypes.determineDeviceManagerType(Optional.of(labInfo), Optional.empty()))
        .isEqualTo("Fusion");
    assertThat(HostTypes.determineLabTypeDisplayNames(Optional.of(labInfo), Optional.empty()))
        .containsExactly("Satellite Lab", "SLaaS", "Fusion Lab")
        .inOrder();
  }

  @Test
  public void determineLabType_coreAndFusion() {
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
    assertThat(HostTypes.determineLabType(Optional.of(labInfo), Optional.empty())).hasValue("Core");
    assertThat(HostTypes.determineSearchLabType(Optional.of(labInfo), Optional.empty()))
        .hasValue("Core Lab");
    assertThat(HostTypes.determineDeviceManagerType(Optional.of(labInfo), Optional.empty()))
        .isEqualTo("Fusion");
    assertThat(HostTypes.determineLabTypeDisplayNames(Optional.of(labInfo), Optional.empty()))
        .containsExactly("Core Lab", "Fusion Lab")
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
