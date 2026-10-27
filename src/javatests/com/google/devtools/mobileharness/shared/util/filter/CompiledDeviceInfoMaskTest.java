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
import com.google.common.collect.Streams;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceProperties;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceProperty;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.HealthCategory;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask.DimensionsMask;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.FieldMask;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CompiledDeviceInfoMaskTest {

  private static final DeviceFeature FULL_FEATURE =
      DeviceFeature.newBuilder()
          .addOwner("owner1")
          .addType("AndroidRealDevice")
          .addDriver("AndroidInstrumentation")
          .setProperties(
              DeviceProperties.newBuilder()
                  .addProperty(DeviceProperty.newBuilder().setName("p1").setValue("v1")))
          .setCompositeDimension(
              DeviceCompositeDimension.newBuilder()
                  .addSupportedDimension(
                      DeviceDimension.newBuilder().setName("model").setValue("pixel 5"))
                  .addSupportedDimension(
                      DeviceDimension.newBuilder().setName("carrier").setValue("verizon"))
                  .addRequiredDimension(
                      DeviceDimension.newBuilder().setName("pool").setValue("shared")))
          .build();

  @Test
  public void nativePathsAndFieldEnums_coverAllProtoFields() {
    ImmutableSet<String> deviceInfoProtoPaths =
        DeviceInfo.getDescriptor().getFields().stream()
            .map(FieldDescriptor::getName)
            .collect(toImmutableSet());
    ImmutableSet<String> locatorProtoPaths =
        DeviceLocator.getDescriptor().getFields().stream()
            .map(field -> "device_locator." + field.getName())
            .collect(toImmutableSet());
    ImmutableSet<String> featureProtoPaths =
        DeviceFeature.getDescriptor().getFields().stream()
            .map(field -> "device_feature." + field.getName())
            .collect(toImmutableSet());
    ImmutableSet<String> compositeDimensionProtoPaths =
        DeviceCompositeDimension.getDescriptor().getFields().stream()
            .map(field -> "device_feature.composite_dimension." + field.getName())
            .collect(toImmutableSet());

    assertThat(
            CompiledDeviceInfoMask.LocatorField.ALL.stream()
                .map(field -> field.path)
                .collect(toImmutableSet()))
        .containsExactlyElementsIn(locatorProtoPaths);
    assertThat(
            CompiledDeviceInfoMask.FeatureField.ALL.stream()
                .map(field -> field.path)
                .collect(toImmutableSet()))
        .containsExactlyElementsIn(
            Sets.union(
                Sets.difference(
                    featureProtoPaths, ImmutableSet.of("device_feature.composite_dimension")),
                compositeDimensionProtoPaths));
    assertThat(CompiledDeviceInfoMask.NATIVE_PATHS)
        .containsExactlyElementsIn(
            Streams.concat(
                    deviceInfoProtoPaths.stream(),
                    locatorProtoPaths.stream(),
                    featureProtoPaths.stream(),
                    compositeDimensionProtoPaths.stream())
                .collect(toImmutableSet()));
  }

  @Test
  public void retainAll_keepsEverything() {
    CompiledDeviceInfoMask mask = CompiledDeviceInfoMask.retainAll();

    assertThat(mask.keepsDeviceInfo()).isTrue();
    assertThat(mask.keepsDeviceStatus()).isTrue();
    assertThat(mask.keepsDeviceCondition()).isTrue();
    assertThat(mask.keepsHealthCategory()).isTrue();
    assertThat(mask.keepsDeviceFeature()).isTrue();
    assertThat(mask.projectDeviceFeature(FULL_FEATURE)).isSameInstanceAs(FULL_FEATURE);
  }

  @Test
  public void of_defaultInstance_returnsRetainAll() {
    assertThat(CompiledDeviceInfoMask.of(DeviceInfoMask.getDefaultInstance()))
        .isSameInstanceAs(CompiledDeviceInfoMask.retainAll());
  }

  @Test
  public void of_noFieldMaskAndEmptyDimensionWhitelists_returnsRetainAll() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setSupportedDimensionsMask(DimensionsMask.getDefaultInstance())
            .setRequiredDimensionsMask(DimensionsMask.getDefaultInstance())
            .build();
    DeviceInfo full = fullDeviceInfo();

    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(mask);

    assertThat(compiled).isSameInstanceAs(CompiledDeviceInfoMask.retainAll());
    assertThat(compiled.project(full).orElseThrow()).isSameInstanceAs(full);
  }

  @Test
  public void of_noFieldMaskWithSingleDimensionWhitelist_doesNotReturnRetainAll() {
    CompiledDeviceInfoMask supportedOnly =
        CompiledDeviceInfoMask.of(
            DeviceInfoMask.newBuilder()
                .setSupportedDimensionsMask(DimensionsMask.newBuilder().addDimensionNames("model"))
                .build());
    CompiledDeviceInfoMask requiredOnly =
        CompiledDeviceInfoMask.of(
            DeviceInfoMask.newBuilder()
                .setRequiredDimensionsMask(DimensionsMask.newBuilder().addDimensionNames("pool"))
                .build());

    assertThat(supportedOnly).isNotSameInstanceAs(CompiledDeviceInfoMask.retainAll());
    assertThat(requiredOnly).isNotSameInstanceAs(CompiledDeviceInfoMask.retainAll());
  }

  @Test
  public void of_emptyFieldMask_dropsDeviceInfo() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()).build();
    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(mask);

    assertThat(compiled.keepsDeviceInfo()).isFalse();
    assertThat(compiled.keepsDeviceLocator()).isFalse();
    assertThat(compiled.keepsDeviceStatus()).isFalse();
    assertThat(compiled.keepsDeviceCondition()).isFalse();
    assertThat(compiled.keepsHealthCategory()).isFalse();
    assertThat(compiled.keepsDeviceFeature()).isFalse();
    assertThat(
            compiled
                .newMaskedDeviceInfoBuilder()
                .setMaskedDeviceStatus("id1", deviceId -> DeviceStatus.IDLE)
                .build())
        .isEmpty();
  }

  @Test
  public void of_singleDimensionProjection_filtersDimensionsAndPreservesProperties() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder()
                    .addPaths("device_locator")
                    .addPaths("device_feature.composite_dimension.supported_dimension")
                    .addPaths("device_feature.properties"))
            .setSupportedDimensionsMask(DimensionsMask.newBuilder().addDimensionNames("model"))
            .build();
    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(mask);

    assertThat(compiled.keepsDeviceInfo()).isTrue();
    assertThat(compiled.keepsDeviceCondition()).isFalse();
    assertThat(compiled.keepsDeviceFeature()).isTrue();

    DeviceFeature projected = compiled.projectDeviceFeature(FULL_FEATURE);
    assertThat(projected.getCompositeDimension().getSupportedDimensionList())
        .containsExactly(DeviceDimension.newBuilder().setName("model").setValue("pixel 5").build());
    assertThat(projected.getCompositeDimension().getRequiredDimensionList()).isEmpty();
    assertThat(projected.getOwnerList()).isEmpty();
    assertThat(projected.getTypeList()).isEmpty();
    assertThat(projected.getProperties().getPropertyList()).hasSize(1);
    assertThat(projected.getProperties().getProperty(0).getName()).isEqualTo("p1");
  }

  @Test
  public void projectDeviceFeature_whenUnmasked_returnsSameReference() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder().addPaths("device_locator").addPaths("device_feature"))
            .build();
    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(mask);

    assertThat(compiled.projectDeviceFeature(FULL_FEATURE)).isSameInstanceAs(FULL_FEATURE);
  }

  @Test
  public void projectDeviceFeature_subsetWithUnmaskedCompositeDimension_reusesCompositeReference() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder()
                    .addPaths("device_feature.owner")
                    .addPaths("device_feature.composite_dimension"))
            .build();
    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(mask);

    DeviceFeature projected = compiled.projectDeviceFeature(FULL_FEATURE);

    assertThat(projected.getOwnerList()).containsExactly("owner1");
    assertThat(projected.getTypeList()).isEmpty();
    assertThat(projected.getCompositeDimension())
        .isSameInstanceAs(FULL_FEATURE.getCompositeDimension());
  }

  @Test
  public void projectDeviceFeature_singleDimensionListWithoutWhitelist_keepsOnlyRequestedList() {
    CompiledDeviceInfoMask supportedOnly =
        CompiledDeviceInfoMask.of(
            DeviceInfoMask.newBuilder()
                .setFieldMask(
                    FieldMask.newBuilder()
                        .addPaths("device_feature.composite_dimension.supported_dimension"))
                .build());
    CompiledDeviceInfoMask requiredOnly =
        CompiledDeviceInfoMask.of(
            DeviceInfoMask.newBuilder()
                .setFieldMask(
                    FieldMask.newBuilder()
                        .addPaths("device_feature.composite_dimension.required_dimension"))
                .build());

    DeviceFeature projectedSupported = supportedOnly.projectDeviceFeature(FULL_FEATURE);
    assertThat(projectedSupported.getCompositeDimension().getSupportedDimensionCount())
        .isEqualTo(2);
    assertThat(projectedSupported.getCompositeDimension().getRequiredDimensionList()).isEmpty();

    DeviceFeature projectedRequired = requiredOnly.projectDeviceFeature(FULL_FEATURE);
    assertThat(projectedRequired.getCompositeDimension().getSupportedDimensionList()).isEmpty();
    assertThat(projectedRequired.getCompositeDimension().getRequiredDimensionList())
        .containsExactly(DeviceDimension.newBuilder().setName("pool").setValue("shared").build());
  }

  @Test
  public void projectDeviceFeature_noWhitelistAndNoCompositeDimension_leavesCompositeUnset() {
    DeviceFeature withoutComposite = DeviceFeature.newBuilder().addOwner("owner1").build();
    CompiledDeviceInfoMask subsetWithComposite =
        CompiledDeviceInfoMask.of(
            DeviceInfoMask.newBuilder()
                .setFieldMask(
                    FieldMask.newBuilder()
                        .addPaths("device_feature.owner")
                        .addPaths("device_feature.composite_dimension"))
                .build());
    CompiledDeviceInfoMask supportedOnly =
        CompiledDeviceInfoMask.of(
            DeviceInfoMask.newBuilder()
                .setFieldMask(
                    FieldMask.newBuilder()
                        .addPaths("device_feature.composite_dimension.supported_dimension"))
                .build());

    assertThat(subsetWithComposite.projectDeviceFeature(withoutComposite).hasCompositeDimension())
        .isFalse();
    assertThat(supportedOnly.projectDeviceFeature(withoutComposite).hasCompositeDimension())
        .isFalse();
  }

  @Test
  public void projectDeviceFeature_whitelistWithoutCompositeDimension_materialisesEmptyComposite() {
    DeviceInfoMask supportedWhitelistMask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(FieldMask.newBuilder().addPaths("device_feature"))
            .setSupportedDimensionsMask(DimensionsMask.newBuilder().addDimensionNames("model"))
            .build();
    DeviceInfoMask requiredWhitelistMask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(FieldMask.newBuilder().addPaths("device_feature"))
            .setRequiredDimensionsMask(DimensionsMask.newBuilder().addDimensionNames("pool"))
            .build();
    DeviceFeature withoutComposite = DeviceFeature.newBuilder().addOwner("owner1").build();

    DeviceFeature projectedSupported =
        CompiledDeviceInfoMask.of(supportedWhitelistMask).projectDeviceFeature(withoutComposite);
    DeviceFeature projectedRequired =
        CompiledDeviceInfoMask.of(requiredWhitelistMask).projectDeviceFeature(withoutComposite);

    // Matches MaskUtils.trimLabQueryResult, which always creates composite_dimension when a
    // dimension whitelist is active.
    assertThat(projectedSupported.hasCompositeDimension()).isTrue();
    assertThat(projectedSupported.getCompositeDimension().getSupportedDimensionList()).isEmpty();
    assertThat(projectedSupported.getOwnerList()).containsExactly("owner1");
    assertThat(projectedRequired.hasCompositeDimension()).isTrue();
    assertThat(projectedRequired.getCompositeDimension().getRequiredDimensionList()).isEmpty();
  }

  @Test
  public void projectDeviceFeature_eachRepeatedField_copiesOnlyThatField() {
    DeviceFeature feature =
        DeviceFeature.newBuilder()
            .addOwner("owner1")
            .addType("AndroidRealDevice")
            .addDriver("AndroidInstrumentation")
            .addDecorator("AndroidCleanAppsDecorator")
            .addExecutor("AndroidExecutor")
            .build();

    assertThat(projectFeature(feature, "device_feature.owner"))
        .isEqualTo(DeviceFeature.newBuilder().addOwner("owner1").build());
    assertThat(projectFeature(feature, "device_feature.type"))
        .isEqualTo(DeviceFeature.newBuilder().addType("AndroidRealDevice").build());
    assertThat(projectFeature(feature, "device_feature.driver"))
        .isEqualTo(DeviceFeature.newBuilder().addDriver("AndroidInstrumentation").build());
    assertThat(projectFeature(feature, "device_feature.decorator"))
        .isEqualTo(DeviceFeature.newBuilder().addDecorator("AndroidCleanAppsDecorator").build());
    assertThat(projectFeature(feature, "device_feature.executor"))
        .isEqualTo(DeviceFeature.newBuilder().addExecutor("AndroidExecutor").build());
  }

  @Test
  public void maskedDeviceInfoBuilder_evaluatesOnlyKeptFields() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder()
                    .addPaths("device_locator.id")
                    .addPaths("device_feature.type"))
            .build();
    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(mask);

    DeviceInfo built =
        compiled
            .newMaskedDeviceInfoBuilder()
            .setMaskedDeviceLocator(
                "dev-1",
                deviceId -> deviceId,
                deviceId -> {
                  throw new AssertionError("only device_locator.id is kept");
                })
            .setMaskedDeviceStatus(
                "dev-1",
                deviceId -> {
                  throw new AssertionError("device_status should not be evaluated");
                })
            .setMaskedHealthCategory(
                "dev-1",
                deviceId -> {
                  throw new AssertionError("health_category should not be evaluated");
                })
            .setMaskedDeviceCondition(
                "dev-1",
                deviceId -> {
                  throw new AssertionError("device_condition should not be evaluated");
                })
            .setMaskedDeviceFeature("dev-1", deviceId -> FULL_FEATURE)
            .build()
            .orElseThrow();

    assertThat(built)
        .isEqualTo(
            DeviceInfo.newBuilder()
                .setDeviceLocator(DeviceLocator.newBuilder().setId("dev-1"))
                .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
                .build());
  }

  @Test
  public void setMaskedDeviceLocator_wholeLocatorKept_usesLocatorGetter() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(FieldMask.newBuilder().addPaths("device_locator"))
            .build();
    DeviceLocator locator =
        DeviceLocator.newBuilder()
            .setId("dev-1")
            .setLabLocator(LabLocator.newBuilder().setHostName("host"))
            .build();

    DeviceInfo built =
        CompiledDeviceInfoMask.of(mask)
            .newMaskedDeviceInfoBuilder()
            .setMaskedDeviceLocator(
                locator,
                full -> {
                  throw new AssertionError("the whole locator is kept");
                },
                full -> full)
            .build()
            .orElseThrow();

    assertThat(built.getDeviceLocator()).isSameInstanceAs(locator);
  }

  @Test
  public void setMaskedDeviceLocator_labLocatorOnly_dropsId() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(FieldMask.newBuilder().addPaths("device_locator.lab_locator"))
            .build();
    DeviceLocator locator =
        DeviceLocator.newBuilder()
            .setId("dev-1")
            .setLabLocator(LabLocator.newBuilder().setHostName("host"))
            .build();

    DeviceInfo built =
        CompiledDeviceInfoMask.of(mask)
            .newMaskedDeviceInfoBuilder()
            .setMaskedDeviceLocator(locator, DeviceLocator::getId, full -> full)
            .build()
            .orElseThrow();

    assertThat(built.getDeviceLocator())
        .isEqualTo(
            DeviceLocator.newBuilder()
                .setLabLocator(LabLocator.newBuilder().setHostName("host"))
                .build());
  }

  @Test
  public void locatorFieldCopy_copiesRequestedFieldIndependently() {
    DeviceLocator full =
        DeviceLocator.newBuilder()
            .setId("dev-1")
            .setLabLocator(LabLocator.newBuilder().setHostName("host"))
            .build();

    DeviceLocator.Builder idOnly = DeviceLocator.newBuilder();
    CompiledDeviceInfoMask.LocatorField.ID.copy(full, idOnly);
    assertThat(idOnly.build()).isEqualTo(DeviceLocator.newBuilder().setId("dev-1").build());

    DeviceLocator.Builder labWithoutLabLocator = DeviceLocator.newBuilder();
    CompiledDeviceInfoMask.LocatorField.LAB_LOCATOR.copy(
        DeviceLocator.newBuilder().setId("dev-1").build(), labWithoutLabLocator);
    assertThat(labWithoutLabLocator.build().hasLabLocator()).isFalse();
  }

  @Test
  public void build_maskBelowGatedPaths_trimsToMask() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder()
                    .addPaths("device_status")
                    .addPaths("device_locator.lab_locator.host_name"))
            .build();
    DeviceLocator locator =
        DeviceLocator.newBuilder()
            .setId("dev-1")
            .setLabLocator(LabLocator.newBuilder().setHostName("host").setIp("1.2.3.4"))
            .build();

    DeviceInfo built =
        CompiledDeviceInfoMask.of(mask)
            .newMaskedDeviceInfoBuilder()
            .setMaskedDeviceLocator(locator, DeviceLocator::getId, full -> full)
            .setMaskedDeviceStatus(locator, full -> DeviceStatus.IDLE)
            .build()
            .orElseThrow();

    assertThat(built)
        .isEqualTo(
            DeviceInfo.newBuilder()
                .setDeviceLocator(
                    DeviceLocator.newBuilder()
                        .setLabLocator(LabLocator.newBuilder().setHostName("host")))
                .setDeviceStatus(DeviceStatus.IDLE)
                .build());
  }

  @Test
  public void build_maskBelowGatedPaths_preservesExplicitFieldPresence() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder()
                    .addPaths("health_category")
                    .addPaths("device_locator.lab_locator.host_name")
                    .addPaths("device_locator.lab_locator.master_detected_ip"))
            .build();
    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(mask);
    DeviceLocator unsetLocator =
        DeviceLocator.newBuilder()
            .setId("dev-1")
            .setLabLocator(LabLocator.newBuilder().setHostName("host").setIp("1.2.3.4"))
            .build();

    DeviceInfo unset =
        compiled
            .newMaskedDeviceInfoBuilder()
            .setMaskedDeviceLocator(unsetLocator, DeviceLocator::getId, full -> full)
            .setMaskedHealthCategory(unsetLocator, full -> Optional.empty())
            .build()
            .orElseThrow();
    assertThat(unset)
        .isEqualTo(
            DeviceInfo.newBuilder()
                .setDeviceLocator(
                    DeviceLocator.newBuilder()
                        .setLabLocator(LabLocator.newBuilder().setHostName("host")))
                .build());
    assertThat(unset.hasHealthCategory()).isFalse();
    assertThat(unset.getDeviceLocator().getLabLocator().hasMasterDetectedIp()).isFalse();

    DeviceLocator setLocator =
        unsetLocator.toBuilder()
            .setLabLocator(unsetLocator.getLabLocator().toBuilder().setMasterDetectedIp("10.0.0.1"))
            .build();
    DeviceInfo set =
        compiled
            .newMaskedDeviceInfoBuilder()
            .setMaskedDeviceLocator(setLocator, DeviceLocator::getId, full -> full)
            .setMaskedHealthCategory(
                setLocator, full -> Optional.of(HealthCategory.HEALTH_CATEGORY_IN_SERVICE))
            .build()
            .orElseThrow();
    assertThat(set.getHealthCategory()).isEqualTo(HealthCategory.HEALTH_CATEGORY_IN_SERVICE);
    assertThat(set.getDeviceLocator().getLabLocator().getMasterDetectedIp()).isEqualTo("10.0.0.1");
  }

  @Test
  public void setMaskedHealthCategory_emptyGetter_leavesFieldUnset() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(FieldMask.newBuilder().addPaths("health_category"))
            .build();
    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(mask);

    DeviceInfo unset =
        compiled
            .newMaskedDeviceInfoBuilder()
            .setMaskedHealthCategory("dev-1", deviceId -> Optional.empty())
            .build()
            .orElseThrow();
    DeviceInfo set =
        compiled
            .newMaskedDeviceInfoBuilder()
            .setMaskedHealthCategory(
                "dev-1", deviceId -> Optional.of(HealthCategory.HEALTH_CATEGORY_IN_SERVICE))
            .build()
            .orElseThrow();

    assertThat(unset.hasHealthCategory()).isFalse();
    assertThat(set.getHealthCategory()).isEqualTo(HealthCategory.HEALTH_CATEGORY_IN_SERVICE);
  }

  @Test
  public void setMaskedDeviceCondition_suppressesEmptyAndDefaultInstance() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(FieldMask.newBuilder().addPaths("device_condition"))
            .build();
    CompiledDeviceInfoMask compiled = CompiledDeviceInfoMask.of(mask);
    DeviceCondition populatedCondition =
        DeviceCondition.newBuilder()
            .addTempDimension(
                TempDimension.newBuilder()
                    .setDimension(DeviceDimension.newBuilder().setName("t").setValue("v")))
            .build();

    DeviceInfo empty =
        compiled
            .newMaskedDeviceInfoBuilder()
            .setMaskedDeviceCondition("dev-1", deviceId -> Optional.empty())
            .build()
            .orElseThrow();
    DeviceInfo defaultCondition =
        compiled
            .newMaskedDeviceInfoBuilder()
            .setMaskedDeviceCondition(
                "dev-1", deviceId -> Optional.of(DeviceCondition.getDefaultInstance()))
            .build()
            .orElseThrow();
    DeviceInfo populated =
        compiled
            .newMaskedDeviceInfoBuilder()
            .setMaskedDeviceCondition("dev-1", deviceId -> Optional.of(populatedCondition))
            .build()
            .orElseThrow();

    assertThat(empty.hasDeviceCondition()).isFalse();
    assertThat(defaultCondition.hasDeviceCondition()).isFalse();
    assertThat(populated.getDeviceCondition()).isEqualTo(populatedCondition);
  }

  @Test
  public void of_dimensionWhitelist_matchesNamesCaseInsensitively() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(FieldMask.newBuilder().addPaths("device_feature.composite_dimension"))
            .setSupportedDimensionsMask(DimensionsMask.newBuilder().addDimensionNames("MODEL"))
            .setRequiredDimensionsMask(DimensionsMask.newBuilder().addDimensionNames("Pool"))
            .build();

    DeviceFeature projected = CompiledDeviceInfoMask.of(mask).projectDeviceFeature(FULL_FEATURE);

    assertThat(projected.getCompositeDimension().getSupportedDimensionList())
        .containsExactly(DeviceDimension.newBuilder().setName("model").setValue("pixel 5").build());
    assertThat(projected.getCompositeDimension().getRequiredDimensionList())
        .containsExactly(DeviceDimension.newBuilder().setName("pool").setValue("shared").build());
  }

  @Test
  public void of_emptyDimensionWhitelist_keepsEveryDimension() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(FieldMask.newBuilder().addPaths("device_feature.composite_dimension"))
            .setSupportedDimensionsMask(DimensionsMask.getDefaultInstance())
            .build();

    DeviceFeature projected = CompiledDeviceInfoMask.of(mask).projectDeviceFeature(FULL_FEATURE);

    assertThat(projected.getCompositeDimension().getSupportedDimensionCount()).isEqualTo(2);
    assertThat(projected.getCompositeDimension().getRequiredDimensionCount()).isEqualTo(1);
  }

  @Test
  public void project_retainAll_returnsSameReference() {
    DeviceInfo full = fullDeviceInfo();

    assertThat(CompiledDeviceInfoMask.retainAll().project(full)).hasValue(full);
    assertThat(CompiledDeviceInfoMask.retainAll().project(full).get()).isSameInstanceAs(full);
  }

  @Test
  public void project_emptyFieldMask_returnsEmpty() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()).build();

    assertThat(CompiledDeviceInfoMask.of(mask).project(fullDeviceInfo())).isEmpty();
  }

  @Test
  public void project_fieldMaskAndWhitelist_matchesMaskedBuilderOutput() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder()
                    .addPaths("device_locator")
                    .addPaths("device_feature.composite_dimension.supported_dimension"))
            .setSupportedDimensionsMask(DimensionsMask.newBuilder().addDimensionNames("model"))
            .build();

    DeviceInfo projected = CompiledDeviceInfoMask.of(mask).project(fullDeviceInfo()).orElseThrow();

    assertThat(projected.getDeviceLocator().getId()).isEqualTo("dev-1");
    assertThat(projected.getDeviceStatus()).isEqualTo(DeviceStatus.INIT);
    assertThat(projected.hasDeviceCondition()).isFalse();
    assertThat(projected.getDeviceFeature().getOwnerList()).isEmpty();
    assertThat(projected.getDeviceFeature().getCompositeDimension().getSupportedDimensionList())
        .containsExactly(DeviceDimension.newBuilder().setName("model").setValue("pixel 5").build());
    assertThat(projected.getDeviceFeature().getCompositeDimension().getRequiredDimensionList())
        .isEmpty();
  }

  @Test
  public void project_whitelistWithoutFieldMask_keepsAllFieldsButFiltersDimensions() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setRequiredDimensionsMask(DimensionsMask.newBuilder().addDimensionNames("pool"))
            .setSupportedDimensionsMask(DimensionsMask.newBuilder().addDimensionNames("carrier"))
            .build();

    DeviceInfo projected = CompiledDeviceInfoMask.of(mask).project(fullDeviceInfo()).orElseThrow();

    assertThat(projected.getDeviceStatus()).isEqualTo(DeviceStatus.IDLE);
    assertThat(projected.hasDeviceCondition()).isTrue();
    assertThat(projected.getDeviceFeature().getOwnerList()).containsExactly("owner1");
    assertThat(projected.getDeviceFeature().getCompositeDimension().getSupportedDimensionList())
        .containsExactly(
            DeviceDimension.newBuilder().setName("carrier").setValue("verizon").build());
    assertThat(projected.getDeviceFeature().getCompositeDimension().getRequiredDimensionList())
        .containsExactly(DeviceDimension.newBuilder().setName("pool").setValue("shared").build());
  }

  @Test
  public void project_deviceIdAndHealthCategory_projectsOnlyRequestedFields() {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(
                FieldMask.newBuilder().addPaths("device_locator.id").addPaths("health_category"))
            .build();

    DeviceInfo projected = CompiledDeviceInfoMask.of(mask).project(fullDeviceInfo()).orElseThrow();

    assertThat(projected)
        .isEqualTo(
            DeviceInfo.newBuilder()
                .setDeviceLocator(DeviceLocator.newBuilder().setId("dev-1"))
                .setHealthCategory(HealthCategory.HEALTH_CATEGORY_IN_SERVICE)
                .build());
    assertThat(
            CompiledDeviceInfoMask.of(mask).project(DeviceInfo.getDefaultInstance()).orElseThrow())
        .isEqualTo(DeviceInfo.getDefaultInstance());
  }

  private static DeviceInfo fullDeviceInfo() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(
            DeviceLocator.newBuilder()
                .setId("dev-1")
                .setLabLocator(LabLocator.newBuilder().setHostName("host")))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setHealthCategory(HealthCategory.HEALTH_CATEGORY_IN_SERVICE)
        .setDeviceCondition(
            DeviceCondition.newBuilder()
                .addTempDimension(
                    TempDimension.newBuilder()
                        .setDimension(DeviceDimension.newBuilder().setName("t").setValue("v"))))
        .setDeviceFeature(FULL_FEATURE)
        .build();
  }

  private static DeviceFeature projectFeature(DeviceFeature feature, String path) {
    DeviceInfoMask mask =
        DeviceInfoMask.newBuilder().setFieldMask(FieldMask.newBuilder().addPaths(path)).build();
    return CompiledDeviceInfoMask.of(mask).projectDeviceFeature(feature);
  }
}
