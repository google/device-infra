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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupCondition;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupOperation;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Pre-compiled immutable projection mask for {@link DeviceInfoMask}.
 *
 * <p>Pre-compiles boolean gates for top-level fields ({@code device_condition}, {@code
 * device_feature}) and direct inner sub-fields of {@code device_feature} ({@code owner}, {@code
 * type}, {@code driver}, {@code decorator}, {@code executor}, {@code properties}, {@code
 * supported_dimension}, {@code required_dimension}) alongside dimension whitelists. Provides a
 * companion {@link MaskedDeviceInfoBuilder} that skips unrequested sub-messages and repeated
 * collections at source and applies {@link FieldMaskUtil#trim} on {@link
 * MaskedDeviceInfoBuilder#build()} to prune any unrequested scalar fields or partial sub-paths.
 */
@Immutable
public final class CompiledDeviceInfoMask {

  private static final CompiledDeviceInfoMask RETAIN_ALL =
      new CompiledDeviceInfoMask(
          DeviceInfoMask.getDefaultInstance(),
          /* retainDeviceIdForSort= */ false,
          ImmutableList.of());

  private final Optional<FieldMask> fieldMask;
  private final Optional<DeviceInfoMask> deferredTrimMask;
  private final boolean keepsDeviceInfo;
  private final boolean keepsDeviceCondition;
  private final boolean keepsDeviceFeature;
  private final boolean keepsOwners;
  private final boolean keepsTypes;
  private final boolean keepsDrivers;
  private final boolean keepsDecorators;
  private final boolean keepsExecutors;
  private final boolean keepsProperties;
  private final boolean needsSupportedDimensions;
  private final boolean needsRequiredDimensions;
  private final boolean keepsFullDeviceFeature;
  private final Optional<ImmutableSet<String>> supportedDimensionNames;
  private final Optional<ImmutableSet<String>> requiredDimensionNames;

  /** Returns a mask that retains all fields and dimensions without pruning. */
  public static CompiledDeviceInfoMask retainAll() {
    return RETAIN_ALL;
  }

  /** Compiles the given non-null {@link DeviceInfoMask}. */
  public static CompiledDeviceInfoMask of(DeviceInfoMask mask) {
    requireNonNull(mask);
    return mask.equals(DeviceInfoMask.getDefaultInstance())
        ? RETAIN_ALL
        : new CompiledDeviceInfoMask(mask, /* retainDeviceIdForSort= */ false, ImmutableList.of());
  }

  /**
   * Compiles a {@link CompiledDeviceInfoMask} from the given {@link LabQuery}, incorporating
   * sort/group requirements (such as {@code device_locator.id} for UUID ordering and specific
   * sub-fields/dimensions for {@link DeviceGroupCondition}) directly into the compiled gates.
   */
  public static CompiledDeviceInfoMask of(LabQuery query) {
    requireNonNull(query);
    if (!query.getMask().hasDeviceInfoMask()) {
      return RETAIN_ALL;
    }
    DeviceInfoMask mask = query.getMask().getDeviceInfoMask();
    List<DeviceGroupOperation> groupOperations =
        query.getDeviceViewRequest().getDeviceGroupOperationList();
    return mask.equals(DeviceInfoMask.getDefaultInstance()) && groupOperations.isEmpty()
        ? RETAIN_ALL
        : new CompiledDeviceInfoMask(mask, /* retainDeviceIdForSort= */ true, groupOperations);
  }

  private CompiledDeviceInfoMask(
      DeviceInfoMask mask,
      boolean retainDeviceIdForSort,
      List<DeviceGroupOperation> groupOperations) {
    boolean retainAllFields = !mask.hasFieldMask();
    FieldMask rawMask = mask.getFieldMask();
    this.keepsDeviceInfo = retainAllFields || rawMask.getPathsCount() > 0;
    if (!keepsDeviceInfo) {
      this.fieldMask = Optional.of(rawMask);
      this.deferredTrimMask = Optional.empty();
      this.keepsDeviceCondition = false;
      this.keepsDeviceFeature = false;
      this.keepsOwners = false;
      this.keepsTypes = false;
      this.keepsDrivers = false;
      this.keepsDecorators = false;
      this.keepsExecutors = false;
      this.keepsProperties = false;
      this.needsSupportedDimensions = false;
      this.needsRequiredDimensions = false;
      this.supportedDimensionNames = Optional.empty();
      this.requiredDimensionNames = Optional.empty();
      this.keepsFullDeviceFeature = false;
      return;
    }

    boolean deferredTrim = false;
    boolean extraOwners = false;
    boolean extraTypes = false;
    boolean extraExecutors = false;
    Set<String> extraDimensions = new HashSet<>();
    FieldMask.Builder effectiveFieldMask = rawMask.toBuilder();

    if (retainDeviceIdForSort
        && !retainAllFields
        && !MaskUtils.isFieldRequested(rawMask, "device_locator.id")) {
      effectiveFieldMask.addPaths("device_locator.id");
      deferredTrim = true;
    }

    for (DeviceGroupOperation operation : groupOperations) {
      DeviceGroupCondition condition = operation.getDeviceGroupCondition();
      if (condition.hasSingleDimensionValue() || condition.hasDimensionValueList()) {
        String dimensionName =
            condition.hasSingleDimensionValue()
                ? condition.getSingleDimensionValue().getDimensionName()
                : condition.getDimensionValueList().getDimensionName();
        extraDimensions.add(toLowerCase(dimensionName));
        if (!retainAllFields
            && !MaskUtils.isFieldRequested(rawMask, "device_feature.composite_dimension")) {
          effectiveFieldMask.addPaths("device_feature.composite_dimension");
          deferredTrim = true;
        }
      } else if (condition.hasTypeList()) {
        extraTypes = true;
        if (!retainAllFields && !MaskUtils.isFieldRequested(rawMask, "device_feature.type")) {
          effectiveFieldMask.addPaths("device_feature.type");
          deferredTrim = true;
        }
      } else if (condition.hasOwnerList()) {
        extraOwners = true;
        if (!retainAllFields && !MaskUtils.isFieldRequested(rawMask, "device_feature.owner")) {
          effectiveFieldMask.addPaths("device_feature.owner");
          deferredTrim = true;
        }
      } else if (condition.hasExecutorList()) {
        extraExecutors = true;
        if (!retainAllFields && !MaskUtils.isFieldRequested(rawMask, "device_feature.executor")) {
          effectiveFieldMask.addPaths("device_feature.executor");
          deferredTrim = true;
        }
      } else if (condition.hasSingleStatus()) {
        if (!retainAllFields && !MaskUtils.isFieldRequested(rawMask, "device_status")) {
          effectiveFieldMask.addPaths("device_status");
          deferredTrim = true;
        }
      }
    }

    this.fieldMask = retainAllFields ? Optional.empty() : Optional.of(effectiveFieldMask.build());
    this.keepsDeviceCondition =
        retainAllFields || MaskUtils.isFieldRequested(rawMask, "device_condition");
    this.keepsOwners =
        extraOwners
            || retainAllFields
            || MaskUtils.isFieldRequested(rawMask, "device_feature.owner");
    this.keepsTypes =
        extraTypes || retainAllFields || MaskUtils.isFieldRequested(rawMask, "device_feature.type");
    this.keepsDrivers =
        retainAllFields || MaskUtils.isFieldRequested(rawMask, "device_feature.driver");
    this.keepsDecorators =
        retainAllFields || MaskUtils.isFieldRequested(rawMask, "device_feature.decorator");
    this.keepsExecutors =
        extraExecutors
            || retainAllFields
            || MaskUtils.isFieldRequested(rawMask, "device_feature.executor");
    this.keepsProperties =
        retainAllFields || MaskUtils.isFieldRequested(rawMask, "device_feature.properties");
    this.needsSupportedDimensions =
        !extraDimensions.isEmpty()
            || retainAllFields
            || MaskUtils.isFieldRequested(
                rawMask, "device_feature.composite_dimension.supported_dimension");
    this.needsRequiredDimensions =
        !extraDimensions.isEmpty()
            || retainAllFields
            || MaskUtils.isFieldRequested(
                rawMask, "device_feature.composite_dimension.required_dimension");

    if (mask.hasSupportedDimensionsMask()
        && !mask.getSupportedDimensionsMask().getDimensionNamesList().isEmpty()) {
      ImmutableSet<String> baseSupported =
          mask.getSupportedDimensionsMask().getDimensionNamesList().stream()
              .map(Ascii::toLowerCase)
              .collect(toImmutableSet());
      if (!baseSupported.containsAll(extraDimensions)) {
        this.supportedDimensionNames =
            Optional.of(
                ImmutableSet.<String>builder()
                    .addAll(baseSupported)
                    .addAll(extraDimensions)
                    .build());
        deferredTrim = true;
      } else {
        this.supportedDimensionNames = Optional.of(baseSupported);
      }
    } else {
      this.supportedDimensionNames = Optional.empty();
    }

    if (mask.hasRequiredDimensionsMask()
        && !mask.getRequiredDimensionsMask().getDimensionNamesList().isEmpty()) {
      ImmutableSet<String> baseRequired =
          mask.getRequiredDimensionsMask().getDimensionNamesList().stream()
              .map(Ascii::toLowerCase)
              .collect(toImmutableSet());
      if (!baseRequired.containsAll(extraDimensions)) {
        this.requiredDimensionNames =
            Optional.of(
                ImmutableSet.<String>builder()
                    .addAll(baseRequired)
                    .addAll(extraDimensions)
                    .build());
        deferredTrim = true;
      } else {
        this.requiredDimensionNames = Optional.of(baseRequired);
      }
    } else {
      this.requiredDimensionNames = Optional.empty();
    }

    this.keepsDeviceFeature =
        keepsOwners
            || keepsTypes
            || keepsDrivers
            || keepsDecorators
            || keepsExecutors
            || keepsProperties
            || needsSupportedDimensions
            || needsRequiredDimensions;
    this.keepsFullDeviceFeature =
        keepsDeviceFeature
            && supportedDimensionNames.isEmpty()
            && requiredDimensionNames.isEmpty()
            && keepsOwners
            && keepsTypes
            && keepsDrivers
            && keepsDecorators
            && keepsExecutors
            && keepsProperties
            && needsSupportedDimensions
            && needsRequiredDimensions;
    this.deferredTrimMask = deferredTrim ? Optional.of(mask) : Optional.empty();
  }

  /** Whether the mask keeps {@link DeviceInfo} records at all. */
  public boolean keepsDeviceInfo() {
    return keepsDeviceInfo;
  }

  /**
   * Returns the original client {@link DeviceInfoMask} if a temporary sort or group-by
   * field/dimension was added to the push-down mask, or empty if the push-down mask already matches
   * the client mask.
   */
  Optional<DeviceInfoMask> deferredTrimMask() {
    return deferredTrimMask;
  }

  /** Whether {@code device_condition} is requested and should be built. */
  public boolean keepsDeviceCondition() {
    return keepsDeviceCondition;
  }

  /** Whether {@code device_feature} is requested and should be built. */
  public boolean keepsDeviceFeature() {
    return keepsDeviceFeature;
  }

  /** Whether {@code device_feature.composite_dimension.supported_dimension} is requested. */
  public boolean needsSupportedDimensions() {
    return needsSupportedDimensions;
  }

  /** Whether {@code device_feature.composite_dimension.required_dimension} is requested. */
  public boolean needsRequiredDimensions() {
    return needsRequiredDimensions;
  }

  private boolean keepsSupportedDimension(String name) {
    return needsSupportedDimensions
        && (supportedDimensionNames.isEmpty()
            || supportedDimensionNames.get().contains(toLowerCase(name)));
  }

  private boolean keepsRequiredDimension(String name) {
    return needsRequiredDimensions
        && (requiredDimensionNames.isEmpty()
            || requiredDimensionNames.get().contains(toLowerCase(name)));
  }

  /**
   * Projects a {@link DeviceFeature} according to sub-field boolean gates and dimension whitelists.
   *
   * <p>When {@link #keepsFullDeviceFeature} is true (all sub-fields requested and no dimension
   * whitelist is active), returns {@code fullFeature} directly by reference in O(1) without builder
   * copying. Otherwise constructs a projected {@link DeviceFeature} containing only the requested
   * sub-fields and whitelisted dimensions.
   */
  public DeviceFeature projectDeviceFeature(DeviceFeature fullFeature) {
    if (keepsFullDeviceFeature) {
      return fullFeature;
    }
    DeviceFeature.Builder builder = DeviceFeature.newBuilder();
    if (keepsOwners) {
      builder.addAllOwner(fullFeature.getOwnerList());
    }
    if (keepsTypes) {
      builder.addAllType(fullFeature.getTypeList());
    }
    if (keepsDrivers) {
      builder.addAllDriver(fullFeature.getDriverList());
    }
    if (keepsDecorators) {
      builder.addAllDecorator(fullFeature.getDecoratorList());
    }
    if (keepsExecutors) {
      builder.addAllExecutor(fullFeature.getExecutorList());
    }
    if (keepsProperties && fullFeature.hasProperties()) {
      builder.setProperties(fullFeature.getProperties());
    }
    if ((needsSupportedDimensions || needsRequiredDimensions)
        && fullFeature.hasCompositeDimension()) {
      DeviceCompositeDimension sourceComposite = fullFeature.getCompositeDimension();
      DeviceCompositeDimension.Builder compositeBuilder = DeviceCompositeDimension.newBuilder();
      if (needsSupportedDimensions) {
        compositeBuilder.addAllSupportedDimension(
            supportedDimensionNames.isEmpty()
                ? sourceComposite.getSupportedDimensionList()
                : sourceComposite.getSupportedDimensionList().stream()
                    .filter(dim -> keepsSupportedDimension(dim.getName()))
                    .collect(toImmutableList()));
      }
      if (needsRequiredDimensions) {
        compositeBuilder.addAllRequiredDimension(
            requiredDimensionNames.isEmpty()
                ? sourceComposite.getRequiredDimensionList()
                : sourceComposite.getRequiredDimensionList().stream()
                    .filter(dim -> keepsRequiredDimension(dim.getName()))
                    .collect(toImmutableList()));
      }
      builder.setCompositeDimension(compositeBuilder);
    }
    return builder.build();
  }

  /** Creates a companion builder for {@link DeviceInfo} guided by this compiled mask. */
  public MaskedDeviceInfoBuilder newMaskedDeviceInfoBuilder() {
    return new MaskedDeviceInfoBuilder(this);
  }

  /**
   * Companion builder for {@link DeviceInfo} that gates expensive sub-messages at construction time
   * and trims unrequested scalar fields and partial sub-paths on {@link #build()}.
   */
  public static final class MaskedDeviceInfoBuilder {

    private final CompiledDeviceInfoMask mask;
    private final DeviceInfo.Builder protoBuilder = DeviceInfo.newBuilder();

    private MaskedDeviceInfoBuilder(CompiledDeviceInfoMask mask) {
      this.mask = mask;
    }

    /** Populates scalar and cheap fields directly using a non-capturing populator. */
    @CanIgnoreReturnValue
    public <S> MaskedDeviceInfoBuilder setFields(
        S source, BiConsumer<DeviceInfo.Builder, S> populator) {
      if (mask.keepsDeviceInfo) {
        populator.accept(protoBuilder, source);
      }
      return this;
    }

    /** Populates {@code device_condition} only when requested by the mask. */
    @CanIgnoreReturnValue
    public <S> MaskedDeviceInfoBuilder setMaskedDeviceCondition(
        S source, Function<S, Optional<DeviceCondition>> getter) {
      if (mask.keepsDeviceCondition) {
        getter
            .apply(source)
            .filter(condition -> !condition.equals(DeviceCondition.getDefaultInstance()))
            .ifPresent(protoBuilder::setDeviceCondition);
      }
      return this;
    }

    /**
     * Populates {@code device_feature} only when requested by the mask, applying inner sub-field
     * gates and dimension whitelists at source.
     */
    @CanIgnoreReturnValue
    public <S> MaskedDeviceInfoBuilder setMaskedDeviceFeature(
        S source, Function<S, DeviceFeature> getter) {
      if (mask.keepsDeviceFeature) {
        protoBuilder.setDeviceFeature(mask.projectDeviceFeature(getter.apply(source)));
      }
      return this;
    }

    /**
     * Builds the {@link DeviceInfo} as an {@link Optional}, returning empty when the mask drops
     * {@link DeviceInfo} entirely (explicitly empty field mask), and applying {@link
     * FieldMaskUtil#trim} to prune any unrequested scalar fields or partial sub-paths.
     */
    public Optional<DeviceInfo> build() {
      if (!mask.keepsDeviceInfo) {
        return Optional.empty();
      }
      DeviceInfo result = protoBuilder.build();
      if (mask.fieldMask.isPresent() && mask.fieldMask.get().getPathsCount() > 0) {
        return Optional.of(FieldMaskUtil.trim(mask.fieldMask.get(), result));
      }
      return Optional.of(result);
    }
  }
}
