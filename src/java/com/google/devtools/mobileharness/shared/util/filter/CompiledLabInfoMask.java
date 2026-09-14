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

import static java.util.Objects.requireNonNull;

import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Pre-compiled immutable projection mask for {@link LabInfoMask}.
 *
 * <p>Evaluates top-level and inner sub-field gates once per query and provides a companion {@link
 * MaskedLabInfoBuilder} that skips unrequested sub-messages at construction time and applies {@link
 * FieldMaskUtil#trim} on {@link MaskedLabInfoBuilder#build()} to prune unrequested scalar fields or
 * partial sub-paths.
 */
@Immutable
public final class CompiledLabInfoMask {

  private static final CompiledLabInfoMask RETAIN_ALL =
      new CompiledLabInfoMask(LabInfoMask.getDefaultInstance(), /* hasDeviceViewRequest= */ true);

  private final Optional<FieldMask> fieldMask;
  private final Optional<LabInfoMask> deferredTrimMask;
  private final boolean keepsLabInfo;
  private final boolean keepsLabServerSetting;
  private final boolean keepsLabServerFeature;
  private final boolean keepsHostProperties;

  /** Returns a mask that keeps all {@link LabInfo} fields. */
  public static CompiledLabInfoMask retainAll() {
    return RETAIN_ALL;
  }

  /** Compiles the given non-null {@link LabInfoMask}. */
  public static CompiledLabInfoMask of(LabInfoMask mask) {
    requireNonNull(mask);
    return mask.equals(LabInfoMask.getDefaultInstance())
        ? RETAIN_ALL
        : new CompiledLabInfoMask(mask, /* hasDeviceViewRequest= */ true);
  }

  /**
   * Compiles a {@link CompiledLabInfoMask} from the given {@link LabQuery}, retaining {@code
   * lab_locator.host_name} during provider construction when {@link LabQuery} requires sorting labs
   * by hostname.
   */
  public static CompiledLabInfoMask of(LabQuery query) {
    requireNonNull(query);
    if (!query.getMask().hasLabInfoMask()) {
      return RETAIN_ALL;
    }
    LabInfoMask mask = query.getMask().getLabInfoMask();
    return mask.equals(LabInfoMask.getDefaultInstance())
        ? RETAIN_ALL
        : new CompiledLabInfoMask(mask, query.hasDeviceViewRequest());
  }

  private CompiledLabInfoMask(LabInfoMask mask, boolean hasDeviceViewRequest) {
    boolean retainAllFields = !mask.hasFieldMask();
    FieldMask rawMask = mask.getFieldMask();
    this.keepsLabInfo = retainAllFields || rawMask.getPathsCount() > 0;
    this.keepsLabServerSetting =
        keepsLabInfo
            && (retainAllFields || MaskUtils.isFieldRequested(rawMask, "lab_server_setting"));
    this.keepsLabServerFeature =
        keepsLabInfo
            && (retainAllFields || MaskUtils.isFieldRequested(rawMask, "lab_server_feature"));
    this.keepsHostProperties =
        keepsLabInfo
            && (retainAllFields
                || MaskUtils.isFieldRequested(rawMask, "lab_server_feature.host_properties"));
    if (!keepsLabInfo || retainAllFields) {
      this.fieldMask = retainAllFields ? Optional.empty() : Optional.of(rawMask);
      this.deferredTrimMask = Optional.empty();
    } else if (!hasDeviceViewRequest
        && !MaskUtils.isFieldRequested(rawMask, "lab_locator.host_name")) {
      this.fieldMask = Optional.of(rawMask.toBuilder().addPaths("lab_locator.host_name").build());
      this.deferredTrimMask = Optional.of(mask);
    } else {
      this.fieldMask = Optional.of(rawMask);
      this.deferredTrimMask = Optional.empty();
    }
  }

  /** Whether the mask keeps {@link LabInfo} records at all. */
  public boolean keepsLabInfo() {
    return keepsLabInfo;
  }

  /**
   * Returns the original client {@link LabInfoMask} if a temporary sort field was added to the
   * push-down mask, or empty if the push-down mask already matches the client mask.
   */
  Optional<LabInfoMask> deferredTrimMask() {
    return deferredTrimMask;
  }

  /** Whether {@code lab_server_setting} is requested and should be populated. */
  public boolean keepsLabServerSetting() {
    return keepsLabServerSetting;
  }

  /** Whether {@code lab_server_feature} is requested and should be populated. */
  public boolean keepsLabServerFeature() {
    return keepsLabServerFeature;
  }

  /** Whether inner sub-field {@code lab_server_feature.host_properties} is requested. */
  public boolean keepsHostProperties() {
    return keepsHostProperties;
  }

  /** Creates a companion builder for {@link LabInfo} guided by this compiled mask. */
  public MaskedLabInfoBuilder newMaskedLabInfoBuilder() {
    return new MaskedLabInfoBuilder(this);
  }

  /**
   * Companion builder for {@link LabInfo} that gates sub-messages at construction time and trims
   * unrequested scalar fields and partial sub-paths on {@link #build()}.
   */
  public static final class MaskedLabInfoBuilder {

    private final CompiledLabInfoMask mask;
    private final LabInfo.Builder protoBuilder = LabInfo.newBuilder();

    private MaskedLabInfoBuilder(CompiledLabInfoMask mask) {
      this.mask = mask;
    }

    /** Populates scalar and cheap fields directly using a non-capturing populator. */
    @CanIgnoreReturnValue
    public <S> MaskedLabInfoBuilder setFields(S source, BiConsumer<LabInfo.Builder, S> populator) {
      if (mask.keepsLabInfo) {
        populator.accept(protoBuilder, source);
      }
      return this;
    }

    /** Populates {@code lab_server_setting} only when requested by the mask. */
    @CanIgnoreReturnValue
    public <S> MaskedLabInfoBuilder setMaskedLabServerSetting(
        S source, Function<S, Optional<LabServerSetting>> getter) {
      if (mask.keepsLabServerSetting) {
        getter.apply(source).ifPresent(protoBuilder::setLabServerSetting);
      }
      return this;
    }

    /** Populates {@code lab_server_feature} only when requested by the mask. */
    @CanIgnoreReturnValue
    public <S> MaskedLabInfoBuilder setMaskedLabServerFeature(
        S source, Function<S, Optional<LabServerFeature>> getter) {
      if (mask.keepsLabServerFeature) {
        getter.apply(source).ifPresent(protoBuilder::setLabServerFeature);
      }
      return this;
    }

    /**
     * Builds the {@link LabInfo} as an {@link Optional}, returning empty when the mask drops {@link
     * LabInfo} entirely (explicitly empty field mask), and applying {@link FieldMaskUtil#trim} to
     * prune any unrequested scalar fields or partial sub-paths.
     */
    public Optional<LabInfo> build() {
      if (!mask.keepsLabInfo) {
        return Optional.empty();
      }
      LabInfo result = protoBuilder.build();
      if (mask.fieldMask.isPresent() && mask.fieldMask.get().getPathsCount() > 0) {
        return Optional.of(FieldMaskUtil.trim(mask.fieldMask.get(), result));
      }
      return Optional.of(result);
    }
  }
}
