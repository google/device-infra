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

import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabPort;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.PortType;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.protobuf.FieldMask;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CompiledLabInfoMaskTest {

  private static final LabServerSetting SETTING =
      LabServerSetting.newBuilder()
          .addPort(LabPort.newBuilder().setType(PortType.LAB_SERVER_RPC).setNum(1234))
          .build();

  private static final LabServerFeature FEATURE =
      LabServerFeature.newBuilder()
          .setHostProperties(
              HostProperties.newBuilder()
                  .addHostProperty(HostProperty.newBuilder().setKey("k1").setValue("v1")))
          .build();

  @Test
  public void retainAll_keepsEverything() {
    CompiledLabInfoMask mask = CompiledLabInfoMask.retainAll();

    assertThat(mask.keepsLabInfo()).isTrue();
    assertThat(mask.keepsLabServerSetting()).isTrue();
    assertThat(mask.keepsLabServerFeature()).isTrue();
    assertThat(mask.keepsHostProperties()).isTrue();

    LabInfo built =
        mask.newMaskedLabInfoBuilder()
            .setMaskedLabServerSetting("host1", hostName -> SETTING)
            .setMaskedLabServerFeature("host1", hostName -> FEATURE)
            .build()
            .orElseThrow();
    assertThat(built.getLabServerSetting()).isEqualTo(SETTING);
    assertThat(built.getLabServerFeature()).isEqualTo(FEATURE);
  }

  @Test
  public void of_emptyFieldMask_returnsEmptyOptional() {
    LabInfoMask mask =
        LabInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()).build();
    CompiledLabInfoMask compiled = CompiledLabInfoMask.of(mask);

    assertThat(compiled.keepsLabInfo()).isFalse();
    Optional<LabInfo> built =
        compiled
            .newMaskedLabInfoBuilder()
            .setFields(
                "host1",
                (labInfoBuilder, hostName) -> labInfoBuilder.setLabStatus(LabStatus.LAB_RUNNING))
            .build();
    assertThat(built).isEmpty();
  }

  @Test
  public void maskedLabInfoBuilder_skipsUnrequestedFeatureAndTrimsSubPaths() {
    LabInfoMask mask =
        LabInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder()
                    .addPaths("lab_locator.host_name")
                    .addPaths("lab_server_setting"))
            .build();
    CompiledLabInfoMask compiled = CompiledLabInfoMask.of(mask);

    assertThat(compiled.keepsLabServerSetting()).isTrue();
    assertThat(compiled.keepsLabServerFeature()).isFalse();
    assertThat(compiled.keepsHostProperties()).isFalse();

    LabInfo built =
        compiled
            .newMaskedLabInfoBuilder()
            .setFields(
                "host1",
                (labInfoBuilder, hostName) ->
                    labInfoBuilder
                        .setLabLocator(
                            LabLocator.newBuilder().setHostName(hostName).setIp("1.2.3.4"))
                        .setLabStatus(LabStatus.LAB_RUNNING))
            .setMaskedLabServerSetting("host1", hostName -> SETTING)
            .setMaskedLabServerFeature(
                "host1",
                hostName -> {
                  throw new AssertionError("lab_server_feature should not be evaluated");
                })
            .build()
            .orElseThrow();

    assertThat(built.getLabLocator().getHostName()).isEqualTo("host1");
    assertThat(built.getLabLocator().getIp()).isEmpty();
    assertThat(built.getLabStatus()).isEqualTo(LabStatus.LAB_STATUS_UNSPECIFIED);
    assertThat(built.hasLabServerSetting()).isTrue();
    assertThat(built.hasLabServerFeature()).isFalse();
  }

  @Test
  public void of_labQuery_retainsHostNameForLabSortAndMarksNeedsPostSortTrim() {
    com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery query =
        com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.newBuilder()
            .setMask(
                com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask
                    .newBuilder()
                    .setLabInfoMask(
                        LabInfoMask.newBuilder()
                            .setFieldMask(FieldMask.newBuilder().addPaths("lab_status"))))
            .build();
    CompiledLabInfoMask compiled = CompiledLabInfoMask.of(query);

    assertThat(compiled.needsPostSortTrim()).isTrue();
    LabInfo built =
        compiled
            .newMaskedLabInfoBuilder()
            .setFields(
                "host1",
                (labInfoBuilder, hostName) ->
                    labInfoBuilder
                        .setLabLocator(
                            LabLocator.newBuilder().setHostName(hostName).setIp("1.2.3.4"))
                        .setLabStatus(LabStatus.LAB_RUNNING))
            .build()
            .orElseThrow();
    assertThat(built.getLabLocator().getHostName()).isEqualTo("host1");
    assertThat(built.getLabLocator().getIp()).isEmpty();
  }
}
