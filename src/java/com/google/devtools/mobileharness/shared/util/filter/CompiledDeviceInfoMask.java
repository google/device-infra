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
import javax.annotation.Nullable;

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
      new CompiledDeviceInfoMask(DeviceInfoMask.getDefaultInstance(), ImmutableList.of());

  @Nullable private final FieldMask fieldMask;
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
  private final boolean needsPostSortTrim;
  @Nullable private final ImmutableSet<String> supportedDimensionNames;
  @Nullable private final ImmutableSet<String> requiredDimensionNames;

  /** Returns a mask that retains all fields and dimensions without pruning. */
  public static CompiledDeviceInfoMask retainAll() {
    return RETAIN_ALL;
  }

  /** Compiles the given non-null {@link DeviceInfoMask}. */
  public static CompiledDeviceInfoMask of(DeviceInfoMask mask) {
    requireNonNull(mask);
    return mask.equals(DeviceInfoMask.getDefaultInstance())
        ? RETAIN_ALL
        : new CompiledDeviceInfoMask(mask, ImmutableList.of());
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
        : new CompiledDeviceInfoMask(mask, groupOperations);
  }

  private CompiledDeviceInfoMask(DeviceInfoMask mask, List<DeviceGroupOperation> groupOperations) {
    boolean retainAllFields = !mask.hasFieldMask();
    FieldMask rawMask = mask.getFieldMask();
    this.keepsDeviceInfo = retainAllFields || rawMask.getPathsCount() > 0;
    if (!keepsDeviceInfo) {
      this.fieldMask = rawMask;
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
      this.supportedDimensionNames = null;
      this.requiredDimensionNames = null;
      this.keepsFullDeviceFeature = false;
      this.needsPostSortTrim = false;
      return;
    }

    boolean postSortTrim = false;
    boolean extraOwners = false;
    boolean extraTypes = false;
    boolean extraExecutors = false;
    Set<String> extraDimensions = new HashSet<>();
    FieldMask.Builder effectiveFieldMask = retainAllFields ? null : rawMask.toBuilder();

    if (!retainAllFields && !MaskUtils.isFieldRequested(rawMask, "device_locator.id")) {
      effectiveFieldMask.addPaths("device_locator.id");
      postSortTrim = true;
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
          postSortTrim = true;
        }
      } else if (condition.hasTypeList()) {
        extraTypes = true;
        if (!retainAllFields && !MaskUtils.isFieldRequested(rawMask, "device_feature.type")) {
          effectiveFieldMask.addPaths("device_feature.type");
          postSortTrim = true;
        }
      } else if (condition.hasOwnerList()) {
        extraOwners = true;
        if (!retainAllFields && !MaskUtils.isFieldRequested(rawMask, "device_feature.owner")) {
          effectiveFieldMask.addPaths("device_feature.owner");
          postSortTrim = true;
        }
      } else if (condition.hasExecutorList()) {
        extraExecutors = true;
        if (!retainAllFields && !MaskUtils.isFieldRequested(rawMask, "device_feature.executor")) {
          effectiveFieldMask.addPaths("device_feature.executor");
          postSortTrim = true;
        }
      } else if (condition.hasSingleStatus()) {
        if (!retainAllFields && !MaskUtils.isFieldRequested(rawMask, "device_status")) {
          effectiveFieldMask.addPaths("device_status");
          postSortTrim = true;
        }
      }
    }

    this.fieldMask = retainAllFields ? null : effectiveFieldMask.build();
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
            ImmutableSet.<String>builder().addAll(baseSupported).addAll(extraDimensions).build();
        postSortTrim = true;
      } else {
        this.supportedDimensionNames = baseSupported;
      }
    } else {
      this.supportedDimensionNames = null;
    }

    if (mask.hasRequiredDimensionsMask()
        && !mask.getRequiredDimensionsMask().getDimensionNamesList().isEmpty()) {
      ImmutableSet<String> baseRequired =
          mask.getRequiredDimensionsMask().getDimensionNamesList().stream()
              .map(Ascii::toLowerCase)
              .collect(toImmutableSet());
      if (!baseRequired.containsAll(extraDimensions)) {
        this.requiredDimensionNames =
            ImmutableSet.<String>builder().addAll(baseRequired).addAll(extraDimensions).build();
        postSortTrim = true;
      } else {
        this.requiredDimensionNames = baseRequired;
      }
    } else {
      this.requiredDimensionNames = null;
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
            && supportedDimensionNames == null
            && requiredDimensionNames == null
            && keepsOwners
            && keepsTypes
            && keepsDrivers
            && keepsDecorators
            && keepsExecutors
            && keepsProperties
            && needsSupportedDimensions
            && needsRequiredDimensions;
    this.needsPostSortTrim = postSortTrim;
  }

  /** Whether the mask keeps {@link DeviceInfo} records at all. */
  public boolean keepsDeviceInfo() {
    return keepsDeviceInfo;
  }

  /**
   * Whether a temporary sort or group-by field/dimension was added to the push-down mask that must
   * be trimmed after sorting and grouping complete.
   */
  public boolean needsPostSortTrim() {
    return needsPostSortTrim;
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
            supportedDimensionNames == null
                ? sourceComposite.getSupportedDimensionList()
                : sourceComposite.getSupportedDimensionList().stream()
                    .filter(dim -> keepsSupportedDimension(dim.getName()))
                    .collect(toImmutableList()));
      }
      if (needsRequiredDimensions) {
        compositeBuilder.addAllRequiredDimension(
            requiredDimensionNames == null
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
        S source, Function<S, DeviceCondition> getter) {
      if (mask.keepsDeviceCondition) {
        DeviceCondition condition = getter.apply(source);
        if (condition != null && !condition.equals(DeviceCondition.getDefaultInstance())) {
          protoBuilder.setDeviceCondition(condition);
        }
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
        DeviceFeature fullFeature = getter.apply(source);
        if (fullFeature != null) {
          protoBuilder.setDeviceFeature(mask.projectDeviceFeature(fullFeature));
        }
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
      if (mask.fieldMask != null && mask.fieldMask.getPathsCount() > 0) {
        return Optional.of(FieldMaskUtil.trim(mask.fieldMask, result));
      }
      return Optional.of(result);
    }
  }
}
