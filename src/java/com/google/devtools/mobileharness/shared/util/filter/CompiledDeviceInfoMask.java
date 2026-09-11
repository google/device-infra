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
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Pre-compiled immutable projection mask for {@link DeviceInfoMask}.
 *
 * <p>Delegates path parsing to {@link CompiledFieldMask} once per query and caches primitive
 * boolean gates and dimension whitelists so that projection push-down executes with zero string
 * scanning, wrapper allocations, or reflection on the hot path.
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

  private static final CompiledDeviceInfoMask RETAIN_ALL =
      new CompiledDeviceInfoMask(DeviceInfoMask.getDefaultInstance());

  private final CompiledFieldMask compiledFieldMask;
  private final boolean hasUnknownTopLevelPaths;
  private final boolean keepsDeviceInfo;
  private final boolean keepsDeviceLocator;
  private final boolean keepsDeviceStatus;
  private final boolean keepsHealthCategory;
  private final boolean keepsDeviceCondition;
  private final boolean keepsDeviceFeature;
  private final boolean needsSupportedDimensions;
  private final boolean needsRequiredDimensions;

  private final Optional<FieldMask> deviceLocatorSubMask;
  private final Optional<FieldMask> deviceConditionSubMask;
  private final Optional<FieldMask> deviceFeatureSubMask;
  @Nullable private final ImmutableSet<String> supportedDimensionNames;
  @Nullable private final ImmutableSet<String> requiredDimensionNames;

  /** Returns a mask that retains all fields and dimensions without pruning. */
  public static CompiledDeviceInfoMask retainAll() {
    return RETAIN_ALL;
  }

  /** Compiles the given non-null {@link DeviceInfoMask}. */
  public static CompiledDeviceInfoMask of(DeviceInfoMask mask) {
    requireNonNull(mask);
    if (mask.equals(DeviceInfoMask.getDefaultInstance())) {
      return RETAIN_ALL;
    }
    return new CompiledDeviceInfoMask(mask);
  }

  private CompiledDeviceInfoMask(DeviceInfoMask mask) {
    if (!mask.hasFieldMask()) {
      this.compiledFieldMask = CompiledFieldMask.retainAll();
    } else if (mask.getFieldMask().getPathsCount() == 0) {
      this.compiledFieldMask = CompiledFieldMask.retainNone();
    } else {
      this.compiledFieldMask = CompiledFieldMask.of(mask.getFieldMask());
    }
    this.hasUnknownTopLevelPaths = compiledFieldMask.hasUnknownTopLevelPaths(KNOWN_FIELDS);
    this.keepsDeviceInfo = !compiledFieldMask.retainsNothing();
    this.keepsDeviceLocator = keepsDeviceInfo && compiledFieldMask.keepsField("device_locator");
    this.keepsDeviceStatus = keepsDeviceInfo && compiledFieldMask.keepsField("device_status");
    this.keepsHealthCategory = keepsDeviceInfo && compiledFieldMask.keepsField("health_category");
    this.keepsDeviceCondition = keepsDeviceInfo && compiledFieldMask.keepsField("device_condition");
    this.keepsDeviceFeature = keepsDeviceInfo && compiledFieldMask.keepsField("device_feature");
    this.needsSupportedDimensions =
        keepsDeviceInfo
            && compiledFieldMask.isRequested(
                "device_feature.composite_dimension.supported_dimension");
    this.needsRequiredDimensions =
        keepsDeviceInfo
            && compiledFieldMask.isRequested(
                "device_feature.composite_dimension.required_dimension");

    this.deviceLocatorSubMask = compiledFieldMask.subMask("device_locator");
    this.deviceConditionSubMask = compiledFieldMask.subMask("device_condition");
    this.deviceFeatureSubMask = compiledFieldMask.subMask("device_feature");

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

  public boolean hasFieldMask() {
    return !compiledFieldMask.retainsAll();
  }

  public Optional<FieldMask> fieldMask() {
    return compiledFieldMask.rawFieldMask();
  }

  public boolean keepsDeviceLocator() {
    return keepsDeviceLocator;
  }

  public boolean keepsDeviceStatus() {
    return keepsDeviceStatus;
  }

  public boolean keepsHealthCategory() {
    return keepsHealthCategory;
  }

  public boolean keepsDeviceCondition() {
    return keepsDeviceCondition;
  }

  public boolean keepsDeviceFeature() {
    return keepsDeviceFeature;
  }

  public boolean hasDimensionMask() {
    return supportedDimensionNames != null || requiredDimensionNames != null;
  }

  public boolean needsDeviceCondition() {
    return keepsDeviceCondition;
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
    return keepsDeviceInfo && compiledFieldMask.isRequested(targetField);
  }

  public DeviceLocator projectDeviceLocator(DeviceLocator locator) {
    return CompiledFieldMask.trimSubMessage(deviceLocatorSubMask, locator);
  }

  public DeviceCondition projectDeviceCondition(DeviceCondition condition) {
    return CompiledFieldMask.trimSubMessage(deviceConditionSubMask, condition);
  }

  /**
   * Projects a {@link DeviceFeature} according to dimension whitelists and sub-field paths.
   *
   * <p>When no dimension whitelist is active and {@code device_feature} is requested in full,
   * returns {@code fullFeature} directly by reference in O(1) without builder copying or
   * reflection.
   */
  public DeviceFeature projectDeviceFeature(DeviceFeature fullFeature) {
    if (!hasDimensionMask()
        && (deviceFeatureSubMask.isEmpty() || deviceFeatureSubMask.get().getPathsCount() == 0)) {
      return fullFeature;
    }
    DeviceFeature featureToSet = fullFeature;
    if (hasDimensionMask()) {
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
      featureToSet = featureBuilder.build();
    }
    return CompiledFieldMask.trimSubMessage(deviceFeatureSubMask, featureToSet);
  }

  public DeviceInfo finalizeDeviceInfo(DeviceInfo deviceInfo) {
    return compiledFieldMask.trimIfUnknownPaths(hasUnknownTopLevelPaths, deviceInfo);
  }
}
