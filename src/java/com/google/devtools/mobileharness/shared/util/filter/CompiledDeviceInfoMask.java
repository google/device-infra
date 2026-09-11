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

import static com.google.common.base.Ascii.toLowerCase;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
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

  private final FieldMaskCompiler compiler;
  @Nullable private final ImmutableSet<String> supportedDimensionNames;
  @Nullable private final ImmutableSet<String> requiredDimensionNames;

  private final boolean keepsDeviceStatus;
  private final boolean keepsHealthCategory;
  private final boolean keepsDeviceLocator;
  private final boolean keepsDeviceCondition;
  private final boolean keepsDeviceFeature;
  private final boolean needsSupportedDimensions;
  private final boolean needsRequiredDimensions;
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
    this.compiler =
        mask.hasFieldMask() ? FieldMaskCompiler.of(mask.getFieldMask()) : FieldMaskCompiler.all();

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

    this.keepsDeviceStatus = compiler.keepsField("device_status");
    this.keepsHealthCategory = compiler.keepsField("health_category");
    this.keepsDeviceLocator = compiler.keepsField("device_locator");
    this.keepsDeviceCondition = compiler.keepsField("device_condition");
    this.keepsDeviceFeature = compiler.keepsField("device_feature");

    // Precompute hot-path dimension flags once in the constructor to avoid linear string scans!
    this.needsSupportedDimensions =
        keepsDeviceFeature
            && matchesField("device_feature.composite_dimension.supported_dimension");
    this.needsRequiredDimensions =
        keepsDeviceFeature && matchesField("device_feature.composite_dimension.required_dimension");

    this.hasUnknownPaths = compiler.hasUnknownPaths(KNOWN_FIELDS);
  }

  /** Whether the mask keeps DeviceInfo records at all. */
  public boolean keepsDeviceInfo() {
    return !compiler.isNoneRetained();
  }

  public boolean hasFieldMask() {
    return !compiler.isAllRetained();
  }

  @Nullable
  public FieldMask fieldMask() {
    return compiler.fieldMask();
  }

  public boolean hasDimensionMask() {
    return supportedDimensionNames != null || requiredDimensionNames != null;
  }

  public boolean keepsDeviceStatus() {
    return keepsDeviceStatus;
  }

  public boolean keepsHealthCategory() {
    return keepsHealthCategory;
  }

  public boolean keepsDeviceLocator() {
    return keepsDeviceLocator;
  }

  public boolean keepsDeviceCondition() {
    return keepsDeviceCondition;
  }

  public boolean keepsDeviceFeature() {
    return keepsDeviceFeature;
  }

  public boolean isDeviceFeatureFull() {
    return compiler.isFull("device_feature");
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
    if (!keepsDeviceInfo()) {
      return false;
    }
    if (compiler.isAllRetained()) {
      return true;
    }
    ImmutableSet<String> fieldPaths = compiler.fieldPaths();
    if (fieldPaths == null) {
      return true;
    }
    for (String path : fieldPaths) {
      if (path.equals(targetField)
          || targetField.startsWith(path + ".")
          || path.startsWith(targetField + ".")) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public DeviceLocator trimDeviceLocator(@Nullable DeviceLocator locator) {
    return compiler.trimSubMessage("device_locator", locator);
  }

  @Nullable
  public DeviceCondition trimDeviceCondition(@Nullable DeviceCondition condition) {
    return compiler.trimSubMessage("device_condition", condition);
  }

  /**
   * Projects the given {@link DeviceFeature} according to the field mask and dimension whitelist.
   *
   * <p>If the entire feature is requested in full without dimension filtering, returns {@code
   * fullFeature} by reference with zero heap allocation.
   */
  @Nullable
  public DeviceFeature projectDeviceFeature(@Nullable DeviceFeature fullFeature) {
    if (!keepsDeviceFeature
        || fullFeature == null
        || fullFeature.equals(DeviceFeature.getDefaultInstance())) {
      return null;
    }
    if (compiler.isFull("device_feature") && !hasDimensionMask()) {
      return fullFeature;
    }
    DeviceFeature.Builder featureBuilder = fullFeature.toBuilder();
    if (hasDimensionMask()) {
      DeviceCompositeDimension.Builder composite =
          featureBuilder.getCompositeDimensionBuilder().clearSupportedDimension();
      if (needsSupportedDimensions) {
        composite.addAllSupportedDimension(
            fullFeature.getCompositeDimension().getSupportedDimensionList().stream()
                .filter(dim -> keepsSupportedDimension(dim.getName()))
                .collect(toImmutableList()));
      }
      composite.clearRequiredDimension();
      if (needsRequiredDimensions) {
        composite.addAllRequiredDimension(
            fullFeature.getCompositeDimension().getRequiredDimensionList().stream()
                .filter(dim -> keepsRequiredDimension(dim.getName()))
                .collect(toImmutableList()));
      }
    }
    FieldMask featureSubMask = compiler.extractSubMask("device_feature");
    if (featureSubMask != null && !featureSubMask.getPathsList().isEmpty()) {
      return FieldMaskUtil.trim(featureSubMask, featureBuilder.build());
    }
    return featureBuilder.build();
  }

  /** Builds the final {@link DeviceInfo}, applying safety-net trimming if unknown paths exist. */
  public DeviceInfo build(DeviceInfo.Builder builder) {
    DeviceInfo result = builder.build();
    if (hasUnknownPaths && compiler.fieldMask() != null) {
      return FieldMaskUtil.trim(compiler.fieldMask(), result);
    }
    return result;
  }
}
