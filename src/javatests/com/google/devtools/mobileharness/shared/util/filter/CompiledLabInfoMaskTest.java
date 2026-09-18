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
    assertThat(mask.keepsLabLocator()).isTrue();
    assertThat(mask.keepsLabServerSetting()).isTrue();
    assertThat(mask.keepsLabServerFeature()).isTrue();
    assertThat(mask.keepsLabStatus()).isTrue();

    LabInfo built =
        mask.newMaskedLabInfoBuilder()
            .setMaskedLabServerSetting("host1", hostName -> Optional.of(SETTING))
            .setMaskedLabServerFeature("host1", hostName -> Optional.of(FEATURE))
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
            .setMaskedLabStatus("host1", hostName -> LabStatus.LAB_RUNNING)
            .build();
    assertThat(built).isEmpty();
  }

  @Test
  public void maskedLabInfoBuilder_evaluatesOnlyKeptFields() {
    LabInfoMask mask =
        LabInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder()
                    .addPaths("lab_locator.host_name")
                    .addPaths("lab_server_setting"))
            .build();
    CompiledLabInfoMask compiled = CompiledLabInfoMask.of(mask);

    assertThat(compiled.keepsLabLocator()).isTrue();
    assertThat(compiled.keepsLabServerSetting()).isTrue();
    assertThat(compiled.keepsLabServerFeature()).isFalse();
    assertThat(compiled.keepsLabStatus()).isFalse();

    LabInfo built =
        compiled
            .newMaskedLabInfoBuilder()
            .setMaskedLabLocator(
                "host1",
                hostName -> hostName,
                hostName -> {
                  throw new AssertionError("only lab_locator.host_name is kept");
                })
            .setMaskedLabStatus(
                "host1",
                hostName -> {
                  throw new AssertionError("lab_status should not be evaluated");
                })
            .setMaskedLabServerSetting("host1", hostName -> Optional.of(SETTING))
            .setMaskedLabServerFeature(
                "host1",
                hostName -> {
                  throw new AssertionError("lab_server_feature should not be evaluated");
                })
            .build()
            .orElseThrow();

    assertThat(built)
        .isEqualTo(
            LabInfo.newBuilder()
                .setLabLocator(LabLocator.newBuilder().setHostName("host1"))
                .setLabServerSetting(SETTING)
                .build());
  }

  @Test
  public void setMaskedLabLocator_hostNameAndIp_projectsLocatorWithoutTrim() {
    LabInfoMask mask =
        LabInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder().addPaths("lab_locator.host_name").addPaths("lab_locator.ip"))
            .build();
    LabLocator locator =
        LabLocator.newBuilder()
            .setHostName("host1")
            .setIp("1.2.3.4")
            .addPort(LabPort.newBuilder().setType(PortType.LAB_SERVER_RPC).setNum(1))
            .build();

    LabInfo built =
        CompiledLabInfoMask.of(mask)
            .newMaskedLabInfoBuilder()
            .setMaskedLabLocator(locator, LabLocator::getHostName, full -> full)
            .build()
            .orElseThrow();

    assertThat(built.getLabLocator())
        .isEqualTo(LabLocator.newBuilder().setHostName("host1").setIp("1.2.3.4").build());
  }

  @Test
  public void setMaskedLabLocator_wholeLocatorKept_returnsSameReference() {
    LabInfoMask mask =
        LabInfoMask.newBuilder()
            .setFieldMask(FieldMask.newBuilder().addPaths("lab_locator"))
            .build();
    LabLocator locator = LabLocator.newBuilder().setHostName("host1").setIp("1.2.3.4").build();

    LabInfo built =
        CompiledLabInfoMask.of(mask)
            .newMaskedLabInfoBuilder()
            .setMaskedLabLocator(
                locator,
                full -> {
                  throw new AssertionError("the whole locator is kept");
                },
                full -> full)
            .build()
            .orElseThrow();

    assertThat(built.getLabLocator()).isSameInstanceAs(locator);
  }

  @Test
  public void build_maskBelowGatedPaths_trimsWithFieldMaskUtil() {
    // lab_server_setting.port is repeated, and FieldMaskUtil.trim does not descend into repeated
    // fields, so only the empty parent survives. This is the same result the mask produced before
    // push-down; such masks are not projected by construction.
    LabInfoMask mask =
        LabInfoMask.newBuilder()
            .setFieldMask(FieldMask.newBuilder().addPaths("lab_server_setting.port.num"))
            .build();

    LabInfo built =
        CompiledLabInfoMask.of(mask)
            .newMaskedLabInfoBuilder()
            .setMaskedLabServerSetting("host1", hostName -> Optional.of(SETTING))
            .setMaskedLabStatus("host1", hostName -> LabStatus.LAB_RUNNING)
            .build()
            .orElseThrow();

    assertThat(built)
        .isEqualTo(
            LabInfo.newBuilder()
                .setLabServerSetting(LabServerSetting.getDefaultInstance())
                .build());
  }

  @Test
  public void of_defaultInstance_returnsRetainAll() {
    assertThat(CompiledLabInfoMask.of(LabInfoMask.getDefaultInstance()))
        .isSameInstanceAs(CompiledLabInfoMask.retainAll());
  }

  @Test
  public void of_hostPropertiesPath_keepsFeatureAndTrimsToHostProperties() {
    LabInfoMask mask =
        LabInfoMask.newBuilder()
            .setFieldMask(FieldMask.newBuilder().addPaths("lab_server_feature.host_properties"))
            .build();
    CompiledLabInfoMask compiled = CompiledLabInfoMask.of(mask);

    assertThat(compiled.keepsLabServerFeature()).isTrue();
    assertThat(compiled.keepsLabServerSetting()).isFalse();

    LabInfo built =
        compiled
            .newMaskedLabInfoBuilder()
            .setMaskedLabServerFeature("host1", hostName -> Optional.of(FEATURE))
            .setMaskedLabStatus("host1", hostName -> LabStatus.LAB_RUNNING)
            .build()
            .orElseThrow();

    assertThat(built)
        .isEqualTo(
            LabInfo.newBuilder()
                .setLabServerFeature(
                    LabServerFeature.newBuilder().setHostProperties(FEATURE.getHostProperties()))
                .build());
  }

  @Test
  public void project_retainAll_returnsSameReference() {
    LabInfo full = fullLabInfo();

    assertThat(CompiledLabInfoMask.retainAll().project(full).get()).isSameInstanceAs(full);
  }

  @Test
  public void project_emptyFieldMask_returnsEmpty() {
    LabInfoMask mask =
        LabInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()).build();

    assertThat(CompiledLabInfoMask.of(mask).project(fullLabInfo())).isEmpty();
  }

  @Test
  public void project_fieldMask_matchesMaskedBuilderOutput() {
    LabInfoMask mask =
        LabInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder()
                    .addPaths("lab_locator.host_name")
                    .addPaths("lab_server_setting"))
            .build();

    LabInfo projected = CompiledLabInfoMask.of(mask).project(fullLabInfo()).orElseThrow();

    assertThat(projected.getLabLocator().getHostName()).isEqualTo("host1");
    assertThat(projected.getLabLocator().getIp()).isEmpty();
    assertThat(projected.getLabStatus()).isEqualTo(LabStatus.LAB_STATUS_UNSPECIFIED);
    assertThat(projected.getLabServerSetting()).isEqualTo(SETTING);
    assertThat(projected.hasLabServerFeature()).isFalse();
  }

  private static LabInfo fullLabInfo() {
    return LabInfo.newBuilder()
        .setLabLocator(LabLocator.newBuilder().setHostName("host1").setIp("1.2.3.4"))
        .setLabStatus(LabStatus.LAB_RUNNING)
        .setLabServerSetting(SETTING)
        .setLabServerFeature(FEATURE)
        .build();
  }
}
