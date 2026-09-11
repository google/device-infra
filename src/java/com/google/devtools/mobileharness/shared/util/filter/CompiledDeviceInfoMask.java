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
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import javax.annotation.Nullable;

/**
 * Pre-compiled projection gates for {@link DeviceInfoMask}.
 *
 * <p>Only the fields whose construction is worth avoiding are decided here. {@code device_feature}
 * (its composite dimension list dominates a DeviceInfo, and materializing it on the ATS controller
 * builds every dimension) and {@code device_condition} (a proto conversion) are gated so an
 * unrequested one is never built. The supported and required dimension whitelists are applied while
 * the feature is built, because a {@link FieldMask} cannot express value-level filtering. Every
 * cheap scalar is built unconditionally by the caller and pruned afterward by a single {@link
 * MaskUtils#trimLabQueryResult} over the whole result.
 */
@Immutable
public final class CompiledDeviceInfoMask {

  private static final CompiledDeviceInfoMask RETAIN_ALL =
      new CompiledDeviceInfoMask(DeviceInfoMask.getDefaultInstance());

  @Nullable private final FieldMask fieldMask;
  private final boolean keepsDeviceInfo;
  private final boolean keepsDeviceFeature;
  private final boolean keepsDeviceCondition;
  private final boolean needsSupportedDimensions;
  private final boolean needsRequiredDimensions;
  @Nullable private final ImmutableSet<String> supportedDimensionNames;
  @Nullable private final ImmutableSet<String> requiredDimensionNames;

  /** A mask that keeps every field and dimension. */
  public static CompiledDeviceInfoMask retainAll() {
    return RETAIN_ALL;
  }

  /** Compiles the given non-null {@link DeviceInfoMask}. */
  public static CompiledDeviceInfoMask of(DeviceInfoMask mask) {
    requireNonNull(mask);
    return mask.equals(DeviceInfoMask.getDefaultInstance())
        ? RETAIN_ALL
        : new CompiledDeviceInfoMask(mask);
  }

  private CompiledDeviceInfoMask(DeviceInfoMask mask) {
    // An absent field mask keeps every field; a present but empty field mask drops the DeviceInfo.
    boolean retainAllFields = !mask.hasFieldMask();
    FieldMask fieldMask = mask.getFieldMask();
    this.fieldMask = retainAllFields ? null : fieldMask;
    this.keepsDeviceInfo = retainAllFields || fieldMask.getPathsCount() > 0;
    this.keepsDeviceFeature =
        keepsDeviceInfo
            && (retainAllFields || MaskUtils.isFieldRequested(fieldMask, "device_feature"));
    this.keepsDeviceCondition =
        keepsDeviceInfo
            && (retainAllFields || MaskUtils.isFieldRequested(fieldMask, "device_condition"));
    this.needsSupportedDimensions =
        keepsDeviceFeature
            && (retainAllFields
                || MaskUtils.isFieldRequested(
                    fieldMask, "device_feature.composite_dimension.supported_dimension"));
    this.needsRequiredDimensions =
        keepsDeviceFeature
            && (retainAllFields
                || MaskUtils.isFieldRequested(
                    fieldMask, "device_feature.composite_dimension.required_dimension"));
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
  }

  /** Whether the mask keeps DeviceInfo records at all. */
  public boolean keepsDeviceInfo() {
    return keepsDeviceInfo;
  }

  /** Whether device_feature is requested and should be built. */
  public boolean keepsDeviceFeature() {
    return keepsDeviceFeature;
  }

  /** Whether device_condition is requested and should be built. */
  public boolean keepsDeviceCondition() {
    return keepsDeviceCondition;
  }

  private boolean hasDimensionMask() {
    return supportedDimensionNames != null || requiredDimensionNames != null;
  }

  private boolean keepsSupportedDimension(@Nullable String name) {
    if (!needsSupportedDimensions || name == null) {
      return false;
    }
    return supportedDimensionNames == null || supportedDimensionNames.contains(toLowerCase(name));
  }

  private boolean keepsRequiredDimension(@Nullable String name) {
    if (!needsRequiredDimensions || name == null) {
      return false;
    }
    return requiredDimensionNames == null || requiredDimensionNames.contains(toLowerCase(name));
  }

  /**
   * Applies the supported and required dimension whitelists to {@code fullFeature}. Field-level
   * pruning inside the feature (for example {@code device_feature.owner} alone) is left to the
   * final {@link MaskUtils#trimLabQueryResult}, so when no dimension whitelist is active this
   * returns {@code fullFeature} directly with no copy.
   */
  public DeviceFeature projectDeviceFeature(DeviceFeature fullFeature) {
    if (!hasDimensionMask()) {
      return fullFeature;
    }
    DeviceFeature.Builder featureBuilder = fullFeature.toBuilder();
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
    return featureBuilder.build();
  }

  /**
   * Trims a fully built {@link DeviceInfo} to the field mask, so the caller can prune each record
   * as it is produced rather than deferring to one pass over the whole result. Returns {@code
   * deviceInfo} unchanged when no field mask is set.
   */
  public DeviceInfo trimFields(DeviceInfo deviceInfo) {
    if (fieldMask == null || fieldMask.getPathsCount() == 0) {
      return deviceInfo;
    }
    return FieldMaskUtil.trim(fieldMask, deviceInfo);
  }
}
