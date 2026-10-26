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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabPort;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.PortType;
import com.google.devtools.mobileharness.api.model.proto.UniverseProto.Universe;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
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

  private static final Universe UNIVERSE =
      Universe.newBuilder()
          .setAtsLab(Universe.AtsLab.newBuilder().setAtsControllerId("ats-1"))
          .build();

  @Test
  public void nativePathsAndLocatorFields_coverAllProtoFields() {
    ImmutableSet<String> locatorProtoPaths =
        LabLocator.getDescriptor().getFields().stream()
            .map(field -> "lab_locator." + field.getName())
            .collect(toImmutableSet());
    ImmutableSet<String> labInfoProtoPaths =
        LabInfo.getDescriptor().getFields().stream()
            .map(FieldDescriptor::getName)
            .collect(toImmutableSet());

    assertThat(
            CompiledLabInfoMask.LocatorField.ALL.stream()
                .map(field -> field.path)
                .collect(toImmutableSet()))
        .containsExactlyElementsIn(locatorProtoPaths);
    assertThat(CompiledLabInfoMask.NATIVE_PATHS)
        .containsExactlyElementsIn(Sets.union(labInfoProtoPaths, locatorProtoPaths));
  }

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
            .setMaskedLabServerSetting("host1", hostName -> SETTING)
            .setMaskedLabServerFeature("host1", hostName -> FEATURE)
            .setMaskedLabStatus("host1", hostName -> LabStatus.LAB_RUNNING)
            .build()
            .orElseThrow();
    assertThat(built.getLabServerSetting()).isEqualTo(SETTING);
    assertThat(built.getLabServerFeature()).isEqualTo(FEATURE);
    assertThat(built.getLabStatus()).isEqualTo(LabStatus.LAB_RUNNING);
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
            .setMaskedLabServerSetting("host1", hostName -> SETTING)
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
  public void setMaskedLabLocator_portMasterDetectedIpAndUniverseWhenSet_copiesRequestedFields() {
    LabInfoMask mask =
        LabInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder()
                    .addPaths("lab_locator.port")
                    .addPaths("lab_locator.master_detected_ip")
                    .addPaths("lab_locator.universe"))
            .build();
    LabPort rpcPort = LabPort.newBuilder().setType(PortType.LAB_SERVER_RPC).setNum(1234).build();
    LabPort httpPort = LabPort.newBuilder().setType(PortType.LAB_SERVER_HTTP).setNum(5678).build();
    LabLocator locator =
        LabLocator.newBuilder()
            .setHostName("host1")
            .setIp("1.2.3.4")
            .addPort(rpcPort)
            .addPort(httpPort)
            .setMasterDetectedIp("10.0.0.1")
            .setUniverse(UNIVERSE)
            .build();

    LabInfo built =
        CompiledLabInfoMask.of(mask)
            .newMaskedLabInfoBuilder()
            .setMaskedLabLocator(locator, LabLocator::getHostName, full -> full)
            .build()
            .orElseThrow();

    assertThat(built.getLabLocator())
        .isEqualTo(
            LabLocator.newBuilder()
                .addPort(rpcPort)
                .addPort(httpPort)
                .setMasterDetectedIp("10.0.0.1")
                .setUniverse(UNIVERSE)
                .build());
  }

  @Test
  public void setMaskedLabLocator_masterDetectedIpAndUniverseWhenUnset_preservesUnsetPresence() {
    LabInfoMask mask =
        LabInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder()
                    .addPaths("lab_locator.host_name")
                    .addPaths("lab_locator.master_detected_ip")
                    .addPaths("lab_locator.universe"))
            .build();
    LabLocator locator = LabLocator.newBuilder().setHostName("host1").setIp("1.2.3.4").build();

    LabInfo built =
        CompiledLabInfoMask.of(mask)
            .newMaskedLabInfoBuilder()
            .setMaskedLabLocator(locator, LabLocator::getHostName, full -> full)
            .build()
            .orElseThrow();

    assertThat(built.getLabLocator())
        .isEqualTo(LabLocator.newBuilder().setHostName("host1").build());
    assertThat(built.getLabLocator().hasMasterDetectedIp()).isFalse();
    assertThat(built.getLabLocator().hasUniverse()).isFalse();
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
    // Paths deeper than LabInfo's top-level sub-messages (outside LabLocator) fall back to
    // FieldMaskUtil.merge on build(). Because lab_server_setting.port is repeated and
    // FieldMaskUtil does not descend into repeated fields, merging keeps only the empty
    // parent LabServerSetting.
    LabInfoMask mask =
        LabInfoMask.newBuilder()
            .setFieldMask(FieldMask.newBuilder().addPaths("lab_server_setting.port.num"))
            .build();

    LabInfo built =
        CompiledLabInfoMask.of(mask)
            .newMaskedLabInfoBuilder()
            .setMaskedLabServerSetting("host1", hostName -> SETTING)
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
            .setMaskedLabServerFeature("host1", hostName -> FEATURE)
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
  @TestParameters({
    "{paths: ['lab_locator']}",
    "{paths: ['lab_locator.ip']}",
    "{paths: ['lab_locator.host_name']}",
    "{paths: ['lab_locator.port']}",
    "{paths: ['lab_locator.master_detected_ip']}",
    "{paths: ['lab_locator.universe']}",
    "{paths: ['lab_server_setting']}",
    "{paths: ['lab_server_feature']}",
    "{paths: ['lab_status']}",
    "{paths: ['lab_locator.host_name', 'lab_server_setting']}",
    "{paths: ['lab_locator.port', 'lab_locator.master_detected_ip', 'lab_locator.universe']}",
    "{paths: ['lab_locator.ip', 'lab_server_feature', 'lab_status']}",
    "{paths: ['lab_server_feature.host_properties', 'lab_locator.universe']}",
    "{paths: ['lab_server_feature.host_properties', 'lab_locator.master_detected_ip']}",
    "{paths: ['lab_server_feature.host_properties', 'lab_locator']}",
  })
  public void project_matchesFieldMaskUtilTrim(List<String> paths) {
    FieldMask fieldMask = FieldMask.newBuilder().addAllPaths(paths).build();
    CompiledLabInfoMask compiled =
        CompiledLabInfoMask.of(LabInfoMask.newBuilder().setFieldMask(fieldMask).build());

    LabInfo populated = fullLabInfo();
    assertThat(compiled.project(populated).orElseThrow())
        .isEqualTo(FieldMaskUtil.trim(fieldMask, populated));

    // On a sparse LabInfo with unset optional fields, FieldMaskUtil.trim's default MergeOptions
    // (replacePrimitiveFields = false) materialises proto3-optional master_detected_ip as "",
    // whereas CompiledLabInfoMask preserves unset presence (matching replacePrimitiveFields =
    // true).
    LabInfo sparse =
        LabInfo.newBuilder()
            .setLabLocator(LabLocator.newBuilder().setHostName("host1"))
            .setLabStatus(LabStatus.LAB_MISSING)
            .build();
    LabInfo.Builder expectedSparse = LabInfo.newBuilder();
    FieldMaskUtil.merge(
        fieldMask,
        sparse,
        expectedSparse,
        new FieldMaskUtil.MergeOptions().setReplacePrimitiveFields(true));
    LabInfo projectedSparse = compiled.project(sparse).orElseThrow();
    assertThat(projectedSparse).isEqualTo(expectedSparse.build());
    assertThat(projectedSparse.getLabLocator().hasMasterDetectedIp()).isFalse();
    assertThat(projectedSparse.getLabLocator().hasUniverse()).isFalse();
    assertThat(projectedSparse.hasLabServerSetting()).isFalse();
    assertThat(projectedSparse.hasLabServerFeature()).isFalse();

    LabInfo empty = LabInfo.getDefaultInstance();
    assertThat(compiled.project(empty).orElseThrow())
        .isEqualTo(FieldMaskUtil.trim(fieldMask, empty));
  }

  private static LabInfo fullLabInfo() {
    return LabInfo.newBuilder()
        .setLabLocator(
            LabLocator.newBuilder()
                .setHostName("host1")
                .setIp("1.2.3.4")
                .addPort(LabPort.newBuilder().setType(PortType.LAB_SERVER_RPC).setNum(1234))
                .setMasterDetectedIp("10.0.0.1")
                .setUniverse(UNIVERSE))
        .setLabStatus(LabStatus.LAB_RUNNING)
        .setLabServerSetting(SETTING)
        .setLabServerFeature(FEATURE)
        .build();
  }
}
