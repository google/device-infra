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
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.HealthCategory;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask.DimensionsMask;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * A {@link DeviceInfoMask} compiled into cheap boolean gates, so that a data source can build only
 * the requested parts of each {@link DeviceInfo} instead of building everything and trimming
 * afterwards.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Building the complete {@link DeviceInfo}, including the full {@link DeviceFeature} with every
 * dimension, for every device in a fleet and then trimming it down to the mask makes the untrimmed
 * intermediate graph the dominant heap and GC cost of a masked query. This class puts the mask in
 * front of construction: fields the mask does not keep are never built.
 *
 * <h2>How to use it</h2>
 *
 * <ol>
 *   <li>Compile the mask once per query with {@link #of(DeviceInfoMask)}, or use {@link
 *       #retainAll()} when there is no mask. Instances are immutable and may be shared.
 *   <li>For each device, obtain a {@link #newMaskedDeviceInfoBuilder()} and feed every field
 *       through its {@code setMasked*} method. Each takes a source object and a getter rather than
 *       a value and calls the getter only when the mask keeps that field, so expensive work belongs
 *       inside the getter.
 *   <li>{@link MaskedDeviceInfoBuilder#build()} yields the projected {@link DeviceInfo}, or empty
 *       when the mask drops {@link DeviceInfo} entirely. Dropped devices still count towards {@code
 *       device_total_count}.
 * </ol>
 *
 * <h2>Mask semantics</h2>
 *
 * <ul>
 *   <li><b>No {@code field_mask}</b>: every field is kept. {@link #of} returns the shared {@link
 *       #retainAll()} instance for a default mask.
 *   <li><b>{@code field_mask} present but empty</b>: the {@link DeviceInfo} is dropped. Every gate
 *       is false and {@link MaskedDeviceInfoBuilder#build()} returns empty.
 *   <li><b>{@code field_mask} with paths</b>: a field is kept when the mask lists it, an ancestor
 *       of it, or a descendant of it (see {@link MaskUtils#isFieldRequested}). Every field of
 *       {@link DeviceInfo}, every field of {@link DeviceLocator} and every field of {@link
 *       DeviceFeature} has its own gate, so a mask made of those paths is honoured by construction
 *       alone. A mask that reaches deeper, for example {@code device_condition.device_error}, is
 *       honoured by {@link FieldMaskUtil#trim} on {@code build()}; such masks are rare and the trim
 *       costs a reflective copy per device.
 *   <li><b>Dimension whitelists</b> ({@code supported_dimensions_mask}, {@code
 *       required_dimensions_mask}) apply on top of the field mask, only when the corresponding
 *       {@code device_feature.composite_dimension.*_dimension} path is kept. Names match ignoring
 *       ASCII case. An absent or empty whitelist keeps every dimension, matching {@code MaskUtils}.
 * </ul>
 *
 * <p>This class compiles exactly the mask it is given. Anything a query needs beyond what the
 * client asked for, such as sort keys, is the caller's concern; see {@code
 * com.google.devtools.mobileharness.shared.labinfo.Projection}.
 */
@Immutable
public final class CompiledDeviceInfoMask {

  private static final String DEVICE_LOCATOR_PATH = "device_locator";
  private static final String DEVICE_STATUS_PATH = "device_status";
  private static final String DEVICE_FEATURE_PATH = "device_feature";
  private static final String DEVICE_CONDITION_PATH = "device_condition";
  private static final String HEALTH_CATEGORY_PATH = "health_category";
  private static final String COMPOSITE_DIMENSION_PATH = "device_feature.composite_dimension";

  /** The {@link DeviceLocator} fields, each with its mask path and how to copy it. */
  private enum LocatorField {
    ID("device_locator.id") {
      @Override
      void copy(DeviceLocator full, DeviceLocator.Builder out) {
        out.setId(full.getId());
      }
    },
    LAB_LOCATOR("device_locator.lab_locator") {
      @Override
      void copy(DeviceLocator full, DeviceLocator.Builder out) {
        if (full.hasLabLocator()) {
          out.setLabLocator(full.getLabLocator());
        }
      }
    };

    private static final ImmutableSet<LocatorField> ALL = ImmutableSet.copyOf(values());

    private final String path;

    LocatorField(String path) {
      this.path = path;
    }

    abstract void copy(DeviceLocator full, DeviceLocator.Builder out);
  }

  /**
   * The {@code device_feature} sub-fields that can be projected independently, each with its mask
   * path and how to copy it from a full feature into a projected one.
   */
  private enum FeatureField {
    OWNER("device_feature.owner") {
      @Override
      void copy(DeviceFeature full, DeviceFeature.Builder out) {
        out.addAllOwner(full.getOwnerList());
      }
    },
    TYPE("device_feature.type") {
      @Override
      void copy(DeviceFeature full, DeviceFeature.Builder out) {
        out.addAllType(full.getTypeList());
      }
    },
    DRIVER("device_feature.driver") {
      @Override
      void copy(DeviceFeature full, DeviceFeature.Builder out) {
        out.addAllDriver(full.getDriverList());
      }
    },
    DECORATOR("device_feature.decorator") {
      @Override
      void copy(DeviceFeature full, DeviceFeature.Builder out) {
        out.addAllDecorator(full.getDecoratorList());
      }
    },
    EXECUTOR("device_feature.executor") {
      @Override
      void copy(DeviceFeature full, DeviceFeature.Builder out) {
        out.addAllExecutor(full.getExecutorList());
      }
    },
    PROPERTIES("device_feature.properties") {
      @Override
      void copy(DeviceFeature full, DeviceFeature.Builder out) {
        if (full.hasProperties()) {
          out.setProperties(full.getProperties());
        }
      }
    },
    // The two dimension lists are whitelisted rather than copied verbatim, so they keep the no-op.
    SUPPORTED_DIMENSION("device_feature.composite_dimension.supported_dimension"),
    REQUIRED_DIMENSION("device_feature.composite_dimension.required_dimension");

    private static final ImmutableSet<FeatureField> ALL = ImmutableSet.copyOf(values());

    private final String path;

    FeatureField(String path) {
      this.path = path;
    }

    /** Copies this sub-field verbatim from {@code full} into {@code out}. */
    void copy(DeviceFeature full, DeviceFeature.Builder out) {}
  }

  /**
   * Every path the builder projects by construction. A field mask made only of these paths needs no
   * {@link FieldMaskUtil#trim} on {@code build()}.
   */
  private static final ImmutableSet<String> NATIVE_PATHS =
      Stream.of(
              Stream.of(
                  DEVICE_LOCATOR_PATH,
                  DEVICE_STATUS_PATH,
                  DEVICE_FEATURE_PATH,
                  DEVICE_CONDITION_PATH,
                  HEALTH_CATEGORY_PATH,
                  COMPOSITE_DIMENSION_PATH),
              LocatorField.ALL.stream().map(field -> field.path),
              FeatureField.ALL.stream().map(field -> field.path))
          .flatMap(Function.identity())
          .collect(toImmutableSet());

  private static final CompiledDeviceInfoMask RETAIN_ALL =
      new CompiledDeviceInfoMask(DeviceInfoMask.getDefaultInstance());

  /**
   * Field mask to trim with on {@code build()}; null when every field is kept or every mask path is
   * projected by construction.
   */
  @Nullable private final FieldMask trimMask;

  private final boolean keepsDeviceInfo;
  private final boolean keepsDeviceStatus;
  private final boolean keepsDeviceCondition;
  private final boolean keepsHealthCategory;

  /** The {@code device_locator} fields that are kept; empty drops {@code device_locator}. */
  private final ImmutableSet<LocatorField> keptLocatorFields;

  /** True when {@code device_locator.id} is the only kept locator field. */
  private final boolean keepsOnlyDeviceId;

  /** The {@code device_feature} sub-fields that are kept; empty drops {@code device_feature}. */
  private final ImmutableSet<FeatureField> keptFeatureFields;

  /** Dimension-name whitelists; empty means "keep every dimension". */
  private final Optional<DimensionNameMatcher> supportedDimensions;

  private final Optional<DimensionNameMatcher> requiredDimensions;

  /** True when {@link #projectDeviceFeature} can return its input by reference. */
  private final boolean keepsFullDeviceFeature;

  /** Returns the shared mask that keeps every field and dimension. */
  public static CompiledDeviceInfoMask retainAll() {
    return RETAIN_ALL;
  }

  /** Compiles {@code mask}; a default mask yields {@link #retainAll()}. */
  public static CompiledDeviceInfoMask of(DeviceInfoMask mask) {
    requireNonNull(mask);
    return mask.equals(DeviceInfoMask.getDefaultInstance())
        ? RETAIN_ALL
        : new CompiledDeviceInfoMask(mask);
  }

  private CompiledDeviceInfoMask(DeviceInfoMask mask) {
    boolean retainAllFields = !mask.hasFieldMask();
    FieldMask fieldMask = mask.getFieldMask();
    Predicate<String> keeps =
        path -> retainAllFields || MaskUtils.isFieldRequested(fieldMask, path);

    this.keepsDeviceInfo = retainAllFields || fieldMask.getPathsCount() > 0;
    this.trimMask =
        keepsDeviceInfo && !retainAllFields && !NATIVE_PATHS.containsAll(fieldMask.getPathsList())
            ? fieldMask
            : null;
    this.keepsDeviceStatus = keeps.test(DEVICE_STATUS_PATH);
    this.keepsDeviceCondition = keeps.test(DEVICE_CONDITION_PATH);
    this.keepsHealthCategory = keeps.test(HEALTH_CATEGORY_PATH);
    this.keptLocatorFields =
        LocatorField.ALL.stream().filter(field -> keeps.test(field.path)).collect(toImmutableSet());
    this.keepsOnlyDeviceId = keptLocatorFields.equals(ImmutableSet.of(LocatorField.ID));
    this.keptFeatureFields =
        FeatureField.ALL.stream().filter(field -> keeps.test(field.path)).collect(toImmutableSet());
    this.supportedDimensions = matcherOf(mask.getSupportedDimensionsMask());
    this.requiredDimensions = matcherOf(mask.getRequiredDimensionsMask());
    this.keepsFullDeviceFeature =
        keptFeatureFields.equals(FeatureField.ALL)
            && supportedDimensions.isEmpty()
            && requiredDimensions.isEmpty();
  }

  /** An absent or empty whitelist keeps every dimension, so it needs no matcher. */
  private static Optional<DimensionNameMatcher> matcherOf(DimensionsMask dimensionsMask) {
    return dimensionsMask.getDimensionNamesCount() == 0
        ? Optional.empty()
        : Optional.of(DimensionNameMatcher.of(dimensionsMask.getDimensionNamesList()));
  }

  /**
   * Whether any {@link DeviceInfo} is produced at all. False only for a present-but-empty {@code
   * field_mask}; data sources should still count such devices in {@code device_total_count}.
   */
  public boolean keepsDeviceInfo() {
    return keepsDeviceInfo;
  }

  /** Whether any part of {@code device_locator} is kept and its getter will be evaluated. */
  public boolean keepsDeviceLocator() {
    return !keptLocatorFields.isEmpty();
  }

  /** Whether {@code device_status} is kept and its getter will be evaluated. */
  public boolean keepsDeviceStatus() {
    return keepsDeviceStatus;
  }

  /** Whether {@code device_condition} is kept and its getter will be evaluated. */
  public boolean keepsDeviceCondition() {
    return keepsDeviceCondition;
  }

  /** Whether {@code health_category} is kept and its getter will be evaluated. */
  public boolean keepsHealthCategory() {
    return keepsHealthCategory;
  }

  /** Whether any part of {@code device_feature} is kept and its getter will be evaluated. */
  public boolean keepsDeviceFeature() {
    return !keptFeatureFields.isEmpty();
  }

  /** Whether {@code device_feature.composite_dimension.supported_dimension} is kept. */
  public boolean keepsSupportedDimensions() {
    return keptFeatureFields.contains(FeatureField.SUPPORTED_DIMENSION);
  }

  /** Whether {@code device_feature.composite_dimension.required_dimension} is kept. */
  public boolean keepsRequiredDimensions() {
    return keptFeatureFields.contains(FeatureField.REQUIRED_DIMENSION);
  }

  /**
   * Projects {@code fullFeature} down to the kept {@code device_feature} sub-fields and whitelisted
   * dimensions.
   *
   * <p>Returns {@code fullFeature} itself, without copying, when every sub-field is kept and no
   * dimension whitelist is active. Callers that already hold a fully built {@link DeviceFeature}
   * (for example one cached per device) can therefore pass it in unconditionally.
   */
  public DeviceFeature projectDeviceFeature(DeviceFeature fullFeature) {
    if (keepsFullDeviceFeature) {
      return fullFeature;
    }
    DeviceFeature.Builder projected = DeviceFeature.newBuilder();
    for (FeatureField field : keptFeatureFields) {
      field.copy(fullFeature, projected);
    }
    // MaskUtils.trimLabQueryResult materialises composite_dimension whenever a whitelist is active,
    // even for a feature that has none; mirror that so both paths produce identical messages.
    boolean whitelistActive = supportedDimensions.isPresent() || requiredDimensions.isPresent();
    if ((keepsSupportedDimensions() || keepsRequiredDimensions())
        && (fullFeature.hasCompositeDimension() || whitelistActive)) {
      DeviceCompositeDimension full = fullFeature.getCompositeDimension();
      DeviceCompositeDimension.Builder composite = DeviceCompositeDimension.newBuilder();
      if (keepsSupportedDimensions()) {
        composite.addAllSupportedDimension(
            supportedDimensions.isPresent()
                ? supportedDimensions.get().filter(full.getSupportedDimensionList())
                : full.getSupportedDimensionList());
      }
      if (keepsRequiredDimensions()) {
        composite.addAllRequiredDimension(
            requiredDimensions.isPresent()
                ? requiredDimensions.get().filter(full.getRequiredDimensionList())
                : full.getRequiredDimensionList());
      }
      projected.setCompositeDimension(composite);
    }
    return projected.build();
  }

  /** Projects {@code fullLocator} down to the kept fields; by reference when all are kept. */
  private DeviceLocator projectDeviceLocator(DeviceLocator fullLocator) {
    if (keptLocatorFields.equals(LocatorField.ALL)) {
      return fullLocator;
    }
    DeviceLocator.Builder projected = DeviceLocator.newBuilder();
    for (LocatorField field : keptLocatorFields) {
      field.copy(fullLocator, projected);
    }
    return projected.build();
  }

  /** Creates a single-use builder for one {@link DeviceInfo} guided by this mask. */
  public MaskedDeviceInfoBuilder newMaskedDeviceInfoBuilder() {
    return new MaskedDeviceInfoBuilder(this);
  }

  /**
   * Applies this mask to an already built {@link DeviceInfo}. Data sources that can avoid building
   * the unrequested parts should use {@link #newMaskedDeviceInfoBuilder()} instead; this is the
   * fallback for sources that only produce full messages. Returns empty when the mask drops {@link
   * DeviceInfo} entirely.
   */
  public Optional<DeviceInfo> project(DeviceInfo deviceInfo) {
    if (this == RETAIN_ALL) {
      return Optional.of(deviceInfo);
    }
    MaskedDeviceInfoBuilder builder = newMaskedDeviceInfoBuilder();
    if (deviceInfo.hasDeviceLocator()) {
      builder.setMaskedDeviceLocator(
          deviceInfo, full -> full.getDeviceLocator().getId(), DeviceInfo::getDeviceLocator);
    }
    builder.setMaskedDeviceStatus(deviceInfo, DeviceInfo::getDeviceStatus);
    if (deviceInfo.hasDeviceFeature()) {
      builder.setMaskedDeviceFeature(deviceInfo, DeviceInfo::getDeviceFeature);
    }
    if (deviceInfo.hasDeviceCondition()) {
      builder.setMaskedDeviceCondition(deviceInfo, full -> Optional.of(full.getDeviceCondition()));
    }
    if (deviceInfo.hasHealthCategory()) {
      builder.setMaskedHealthCategory(deviceInfo, full -> Optional.of(full.getHealthCategory()));
    }
    return builder.build();
  }

  /**
   * Builds one {@link DeviceInfo} under a {@link CompiledDeviceInfoMask}.
   *
   * <p>Each {@code setMasked*} method takes a source object and a getter rather than a value, and
   * calls the getter only when the mask keeps that field. Put the expensive work inside the getter.
   * Nothing the mask does not keep is ever set, so {@link #build()} copies nothing unless the mask
   * reaches below the fields this class gates.
   *
   * <p>A builder is single-use and not thread-safe.
   */
  public static final class MaskedDeviceInfoBuilder {

    private final CompiledDeviceInfoMask mask;
    private final DeviceInfo.Builder protoBuilder = DeviceInfo.newBuilder();

    private MaskedDeviceInfoBuilder(CompiledDeviceInfoMask mask) {
      this.mask = mask;
    }

    /**
     * Sets {@code device_locator} when the mask keeps any part of it. When only {@code
     * device_locator.id} is kept, which is what ordering devices needs, only {@code idGetter} is
     * called and the locator is built from the id alone; otherwise {@code locatorGetter} is called
     * and its result projected to the kept fields.
     */
    @CanIgnoreReturnValue
    public <S> MaskedDeviceInfoBuilder setMaskedDeviceLocator(
        S source, Function<S, String> idGetter, Function<S, DeviceLocator> locatorGetter) {
      if (mask.keepsOnlyDeviceId) {
        protoBuilder.setDeviceLocator(DeviceLocator.newBuilder().setId(idGetter.apply(source)));
      } else if (mask.keepsDeviceLocator()) {
        protoBuilder.setDeviceLocator(mask.projectDeviceLocator(locatorGetter.apply(source)));
      }
      return this;
    }

    /** Sets {@code device_status} from {@code getter} when the mask keeps it. */
    @CanIgnoreReturnValue
    public <S> MaskedDeviceInfoBuilder setMaskedDeviceStatus(
        S source, Function<S, DeviceStatus> getter) {
      if (mask.keepsDeviceStatus) {
        protoBuilder.setDeviceStatus(getter.apply(source));
      }
      return this;
    }

    /**
     * Sets {@code health_category} from {@code getter} when the mask keeps it. A getter returning
     * empty leaves the field unset; {@code health_category} has explicit presence, so a data source
     * that does not know the category should return empty rather than {@code
     * HEALTH_CATEGORY_UNSPECIFIED}.
     */
    @CanIgnoreReturnValue
    public <S> MaskedDeviceInfoBuilder setMaskedHealthCategory(
        S source, Function<S, Optional<HealthCategory>> getter) {
      if (mask.keepsHealthCategory) {
        getter.apply(source).ifPresent(protoBuilder::setHealthCategory);
      }
      return this;
    }

    /**
     * Sets {@code device_condition} from {@code getter} when the mask keeps it. A getter returning
     * empty or a default-instance condition leaves the field unset.
     */
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
     * Sets {@code device_feature} from {@code getter} when the mask keeps any part of it, projected
     * with {@link CompiledDeviceInfoMask#projectDeviceFeature}. The getter may return the data
     * source's fully built feature; it is passed through by reference when nothing needs pruning.
     */
    @CanIgnoreReturnValue
    public <S> MaskedDeviceInfoBuilder setMaskedDeviceFeature(
        S source, Function<S, DeviceFeature> getter) {
      if (mask.keepsDeviceFeature()) {
        protoBuilder.setDeviceFeature(mask.projectDeviceFeature(getter.apply(source)));
      }
      return this;
    }

    /**
     * Returns the projected {@link DeviceInfo}, or empty when the mask drops {@link DeviceInfo}
     * entirely.
     */
    public Optional<DeviceInfo> build() {
      if (!mask.keepsDeviceInfo) {
        return Optional.empty();
      }
      DeviceInfo result = protoBuilder.build();
      return Optional.of(
          mask.trimMask == null ? result : FieldMaskUtil.trim(mask.trimMask, result));
    }
  }
}
