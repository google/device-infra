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

import static com.google.common.base.Ascii.toLowerCase;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.HealthCategory;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Pre-compiled immutable projection mask for {@link DeviceInfoMask}.
 *
 * <p>Caches parsed field paths and dimension whitelists so that projection push-down can be
 * evaluated efficiently across large fleets without parsing FieldMask paths per device.
 */
@Immutable
public final class CompiledDeviceInfoMask {

  private static final ImmutableSet<String> KNOWN_FIELDS =
      ImmutableSet.of(
          "device_locator",
          "device_status",
          "device_feature",
          "device_condition",
          "health_category");

  private static final CompiledDeviceInfoMask NONE =
      new CompiledDeviceInfoMask(DeviceInfoMask.getDefaultInstance());

  private final boolean keepsDeviceInfo;
  private final CompiledFieldMask compiledFieldMask;
  @Nullable private final ImmutableSet<String> supportedDimensionNames;
  @Nullable private final ImmutableSet<String> requiredDimensionNames;

  private final boolean keepsDeviceStatus;
  private final boolean keepsHealthCategory;
  private final boolean needsSupportedDimensions;
  private final boolean needsRequiredDimensions;
  @Nullable private final FieldMask deviceLocatorSubMask;
  @Nullable private final FieldMask deviceConditionSubMask;
  @Nullable private final FieldMask deviceFeatureSubMask;
  private final boolean hasUnknownPaths;

  /** Returns a mask that retains all fields and dimensions without pruning. */
  public static CompiledDeviceInfoMask none() {
    return NONE;
  }

  /** Compiles the given {@link DeviceInfoMask}. */
  public static CompiledDeviceInfoMask of(@Nullable DeviceInfoMask mask) {
    if (mask == null || mask.equals(DeviceInfoMask.getDefaultInstance())) {
      return NONE;
    }
    return new CompiledDeviceInfoMask(mask);
  }

  private CompiledDeviceInfoMask(DeviceInfoMask mask) {
    this.compiledFieldMask =
        mask.hasFieldMask() ? CompiledFieldMask.of(mask.getFieldMask()) : CompiledFieldMask.all();
    this.keepsDeviceInfo = !this.compiledFieldMask.isNoneRetained();

    this.supportedDimensionNames =
        mask.hasSupportedDimensionsMask()
                && !mask.getSupportedDimensionsMask().getDimensionNamesList().isEmpty()
            ? mask.getSupportedDimensionsMask().getDimensionNamesList().stream()
                .map(String::toLowerCase)
                .collect(toImmutableSet())
            : null;
    this.requiredDimensionNames =
        mask.hasRequiredDimensionsMask()
                && !mask.getRequiredDimensionsMask().getDimensionNamesList().isEmpty()
            ? mask.getRequiredDimensionsMask().getDimensionNamesList().stream()
                .map(String::toLowerCase)
                .collect(toImmutableSet())
            : null;

    if (!this.keepsDeviceInfo) {
      this.keepsDeviceStatus = false;
      this.keepsHealthCategory = false;
      this.needsSupportedDimensions = false;
      this.needsRequiredDimensions = false;
      this.deviceLocatorSubMask = null;
      this.deviceConditionSubMask = null;
      this.deviceFeatureSubMask = null;
      this.hasUnknownPaths = false;
    } else {
      this.keepsDeviceStatus = this.compiledFieldMask.keepsField("device_status");
      this.keepsHealthCategory = this.compiledFieldMask.keepsField("health_category");
      this.needsSupportedDimensions =
          this.compiledFieldMask.keepsField(
              "device_feature.composite_dimension.supported_dimension");
      this.needsRequiredDimensions =
          this.compiledFieldMask.keepsField(
              "device_feature.composite_dimension.required_dimension");
      this.deviceLocatorSubMask = this.compiledFieldMask.extractSubMask("device_locator");
      this.deviceConditionSubMask = this.compiledFieldMask.extractSubMask("device_condition");
      this.deviceFeatureSubMask = this.compiledFieldMask.extractSubMask("device_feature");
      this.hasUnknownPaths = this.compiledFieldMask.hasUnknownPaths(KNOWN_FIELDS);
    }
  }

  /** Whether the mask keeps DeviceInfo records at all. */
  public boolean keepsDeviceInfo() {
    return keepsDeviceInfo;
  }

  public boolean hasFieldMask() {
    return !compiledFieldMask.isAllRetained();
  }

  @Nullable
  public FieldMask fieldMask() {
    return compiledFieldMask.rawFieldMask();
  }

  public boolean hasDimensionMask() {
    return supportedDimensionNames != null || requiredDimensionNames != null;
  }

  public boolean needsDeviceCondition() {
    return keepsDeviceInfo && deviceConditionSubMask != null;
  }

  public boolean needsSupportedDimensions() {
    return needsSupportedDimensions;
  }

  public boolean keepsSupportedDimension(@Nullable String name) {
    if (!needsSupportedDimensions || name == null) {
      return false;
    }
    return supportedDimensionNames == null || supportedDimensionNames.contains(toLowerCase(name));
  }

  public boolean needsRequiredDimensions() {
    return needsRequiredDimensions;
  }

  public boolean keepsRequiredDimension(@Nullable String name) {
    if (!needsRequiredDimensions || name == null) {
      return false;
    }
    return requiredDimensionNames == null || requiredDimensionNames.contains(toLowerCase(name));
  }

  public boolean matchesField(String targetField) {
    return keepsDeviceInfo && compiledFieldMask.keepsField(targetField);
  }

  /** Creates a companion builder for {@link DeviceInfo} guided by this compiled mask. */
  public MaskedDeviceInfoBuilder newMaskedDeviceInfoBuilder() {
    return new MaskedDeviceInfoBuilder(this);
  }

  /** Companion builder for {@link DeviceInfo}. */
  public static final class MaskedDeviceInfoBuilder {
    private final CompiledDeviceInfoMask mask;
    private final DeviceInfo.Builder protoBuilder;

    private MaskedDeviceInfoBuilder(CompiledDeviceInfoMask mask) {
      this.mask = mask;
      this.protoBuilder = DeviceInfo.newBuilder();
    }

    @CanIgnoreReturnValue
    public <S> MaskedDeviceInfoBuilder setDeviceLocator(
        S source, Function<S, DeviceLocator> getter) {
      if (mask.deviceLocatorSubMask == null) {
        return this;
      }
      DeviceLocator locator = getter.apply(source);
      if (locator != null && !locator.equals(DeviceLocator.getDefaultInstance())) {
        if (!mask.deviceLocatorSubMask.getPathsList().isEmpty()) {
          locator = FieldMaskUtil.trim(mask.deviceLocatorSubMask, locator);
        }
        protoBuilder.setDeviceLocator(locator);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public <S> MaskedDeviceInfoBuilder setDeviceStatus(S source, Function<S, DeviceStatus> getter) {
      if (!mask.keepsDeviceStatus) {
        return this;
      }
      protoBuilder.setDeviceStatus(getter.apply(source));
      return this;
    }

    @CanIgnoreReturnValue
    public <S> MaskedDeviceInfoBuilder setHealthCategory(
        S source, Function<S, HealthCategory> getter) {
      if (!mask.keepsHealthCategory) {
        return this;
      }
      HealthCategory healthCategory = getter.apply(source);
      if (healthCategory != null && healthCategory != HealthCategory.HEALTH_CATEGORY_UNSPECIFIED) {
        protoBuilder.setHealthCategory(healthCategory);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public <S> MaskedDeviceInfoBuilder setMaskedDeviceCondition(
        S source, Function<S, DeviceCondition> supplier) {
      if (mask.deviceConditionSubMask == null) {
        return this;
      }
      DeviceCondition condition = supplier.apply(source);
      if (condition != null && !condition.equals(DeviceCondition.getDefaultInstance())) {
        if (!mask.deviceConditionSubMask.getPathsList().isEmpty()) {
          condition = FieldMaskUtil.trim(mask.deviceConditionSubMask, condition);
        }
        protoBuilder.setDeviceCondition(condition);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public <S> MaskedDeviceInfoBuilder setMaskedDeviceFeature(
        S source, Function<S, DeviceFeature> supplier) {
      if (mask.deviceFeatureSubMask == null) {
        return this;
      }
      DeviceFeature fullFeature = supplier.apply(source);
      if (fullFeature == null || fullFeature.equals(DeviceFeature.getDefaultInstance())) {
        return this;
      }
      DeviceFeature featureToSet = fullFeature;
      if (mask.hasDimensionMask()) {
        DeviceFeature.Builder featureBuilder = fullFeature.toBuilder();
        DeviceCompositeDimension.Builder composite =
            featureBuilder.getCompositeDimensionBuilder().clearSupportedDimension();
        if (mask.needsSupportedDimensions()) {
          composite.addAllSupportedDimension(
              fullFeature.getCompositeDimension().getSupportedDimensionList().stream()
                  .filter(dim -> mask.keepsSupportedDimension(dim.getName()))
                  .collect(toImmutableList()));
        }
        composite.clearRequiredDimension();
        if (mask.needsRequiredDimensions()) {
          composite.addAllRequiredDimension(
              fullFeature.getCompositeDimension().getRequiredDimensionList().stream()
                  .filter(dim -> mask.keepsRequiredDimension(dim.getName()))
                  .collect(toImmutableList()));
        }
        featureToSet = featureBuilder.build();
      }
      if (!mask.deviceFeatureSubMask.getPathsList().isEmpty()) {
        featureToSet = FieldMaskUtil.trim(mask.deviceFeatureSubMask, featureToSet);
      }
      protoBuilder.setDeviceFeature(featureToSet);
      return this;
    }

    @CanIgnoreReturnValue
    public MaskedDeviceInfoBuilder setMaskedDeviceFeature(DeviceFeature fullFeature) {
      return setMaskedDeviceFeature(fullFeature, Function.identity());
    }

    public DeviceInfo build() {
      DeviceInfo result = protoBuilder.build();
      if (mask.hasUnknownPaths && mask.fieldMask() != null) {
        return FieldMaskUtil.trim(mask.fieldMask(), result);
      }
      return result;
    }
  }
}
