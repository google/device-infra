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

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Pre-compiled immutable projection mask for {@link LabInfoMask}.
 *
 * <p>Caches parsed field paths so that projection push-down can be evaluated efficiently across all
 * labs without re-parsing paths per lab.
 */
@Immutable
public final class CompiledLabInfoMask {

  private static final ImmutableSet<String> KNOWN_FIELDS =
      ImmutableSet.of("lab_locator", "lab_server_setting", "lab_server_feature", "lab_status");

  private static final CompiledLabInfoMask NONE =
      new CompiledLabInfoMask(LabInfoMask.getDefaultInstance());

  private final boolean keepsLabInfo;
  private final CompiledFieldMask compiledFieldMask;

  private final boolean keepsLabStatus;
  @Nullable private final FieldMask labLocatorSubMask;
  @Nullable private final FieldMask labServerSettingSubMask;
  @Nullable private final FieldMask labServerFeatureSubMask;
  private final boolean hasUnknownPaths;

  /** A mask that keeps all LabInfo fields. */
  public static CompiledLabInfoMask none() {
    return NONE;
  }

  /** Compiles the given {@link LabInfoMask}. */
  public static CompiledLabInfoMask of(@Nullable LabInfoMask mask) {
    if (mask == null || mask.equals(LabInfoMask.getDefaultInstance())) {
      return NONE;
    }
    return new CompiledLabInfoMask(mask);
  }

  private CompiledLabInfoMask(LabInfoMask mask) {
    this.compiledFieldMask =
        mask.hasFieldMask() ? CompiledFieldMask.of(mask.getFieldMask()) : CompiledFieldMask.all();
    this.keepsLabInfo = !this.compiledFieldMask.isNoneRetained();

    if (!this.keepsLabInfo) {
      this.keepsLabStatus = false;
      this.labLocatorSubMask = null;
      this.labServerSettingSubMask = null;
      this.labServerFeatureSubMask = null;
      this.hasUnknownPaths = false;
    } else {
      this.keepsLabStatus = this.compiledFieldMask.keepsField("lab_status");
      this.labLocatorSubMask = this.compiledFieldMask.extractSubMask("lab_locator");
      this.labServerSettingSubMask = this.compiledFieldMask.extractSubMask("lab_server_setting");
      this.labServerFeatureSubMask = this.compiledFieldMask.extractSubMask("lab_server_feature");
      this.hasUnknownPaths = this.compiledFieldMask.hasUnknownPaths(KNOWN_FIELDS);
    }
  }

  /** Whether the mask keeps LabInfo records at all. */
  public boolean keepsLabInfo() {
    return keepsLabInfo;
  }

  public boolean hasFieldMask() {
    return !compiledFieldMask.isAllRetained();
  }

  @Nullable
  public FieldMask fieldMask() {
    return compiledFieldMask.rawFieldMask();
  }

  public boolean matchesField(String targetField) {
    return keepsLabInfo && compiledFieldMask.keepsField(targetField);
  }

  /** Creates a companion builder for {@link LabInfo} guided by this compiled mask. */
  public MaskedLabInfoBuilder newMaskedLabInfoBuilder() {
    return new MaskedLabInfoBuilder(this);
  }

  /** Companion builder that filters fields during construction and trims on build. */
  public static final class MaskedLabInfoBuilder {
    private final CompiledLabInfoMask mask;
    private final LabInfo.Builder protoBuilder;

    private MaskedLabInfoBuilder(CompiledLabInfoMask mask) {
      this.mask = mask;
      this.protoBuilder = LabInfo.newBuilder();
    }

    @CanIgnoreReturnValue
    public <S> MaskedLabInfoBuilder setLabLocator(S source, Function<S, LabLocator> getter) {
      if (mask.labLocatorSubMask == null) {
        return this;
      }
      LabLocator locator = getter.apply(source);
      if (locator != null && !locator.equals(LabLocator.getDefaultInstance())) {
        if (!mask.labLocatorSubMask.getPathsList().isEmpty()) {
          locator = FieldMaskUtil.trim(mask.labLocatorSubMask, locator);
        }
        protoBuilder.setLabLocator(locator);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public <S> MaskedLabInfoBuilder setLabServerSetting(
        S source, Function<S, Optional<LabServerSetting>> getter) {
      if (mask.labServerSettingSubMask == null) {
        return this;
      }
      getter
          .apply(source)
          .ifPresent(
              setting -> {
                if (!mask.labServerSettingSubMask.getPathsList().isEmpty()) {
                  setting = FieldMaskUtil.trim(mask.labServerSettingSubMask, setting);
                }
                protoBuilder.setLabServerSetting(setting);
              });
      return this;
    }

    @CanIgnoreReturnValue
    public <S> MaskedLabInfoBuilder setLabServerFeature(
        S source, Function<S, Optional<LabServerFeature>> getter) {
      if (mask.labServerFeatureSubMask == null) {
        return this;
      }
      getter
          .apply(source)
          .ifPresent(
              feature -> {
                if (!mask.labServerFeatureSubMask.getPathsList().isEmpty()) {
                  feature = FieldMaskUtil.trim(mask.labServerFeatureSubMask, feature);
                }
                protoBuilder.setLabServerFeature(feature);
              });
      return this;
    }

    @CanIgnoreReturnValue
    public <S> MaskedLabInfoBuilder setLabStatus(
        S source, Function<S, Optional<LabStatus>> getter) {
      if (!mask.keepsLabStatus) {
        return this;
      }
      getter.apply(source).ifPresent(protoBuilder::setLabStatus);
      return this;
    }

    public LabInfo build() {
      LabInfo result = protoBuilder.build();
      if (mask.hasUnknownPaths && mask.fieldMask() != null) {
        return FieldMaskUtil.trim(mask.fieldMask(), result);
      }
      return result;
    }
  }
}
