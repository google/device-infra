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

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;
import java.util.Optional;

/**
 * Pre-compiled immutable projection mask for {@link LabInfoMask}.
 *
 * <p>Delegates path parsing to {@link CompiledFieldMask} once per query and caches primitive
 * boolean gates so that projection push-down executes with zero string scanning, wrapper
 * allocations, or reflection on the hot path.
 */
@Immutable
public final class CompiledLabInfoMask {

  private static final ImmutableSet<String> KNOWN_FIELDS =
      ImmutableSet.of("lab_locator", "lab_server_setting", "lab_server_feature", "lab_status");

  private static final CompiledLabInfoMask RETAIN_ALL =
      new CompiledLabInfoMask(LabInfoMask.getDefaultInstance());

  private final CompiledFieldMask compiledFieldMask;
  private final boolean hasUnknownTopLevelPaths;
  private final boolean keepsLabInfo;
  private final boolean keepsLabLocator;
  private final boolean keepsLabServerSetting;
  private final boolean keepsLabServerFeature;
  private final boolean keepsLabStatus;
  private final boolean keepsHostProperties;
  private final Optional<FieldMask> labLocatorSubMask;
  private final Optional<FieldMask> labServerSettingSubMask;
  private final Optional<FieldMask> labServerFeatureSubMask;

  /** A mask that keeps all LabInfo fields. */
  public static CompiledLabInfoMask retainAll() {
    return RETAIN_ALL;
  }

  /** Compiles the given non-null {@link LabInfoMask}. */
  public static CompiledLabInfoMask of(LabInfoMask mask) {
    requireNonNull(mask);
    if (mask.equals(LabInfoMask.getDefaultInstance())) {
      return RETAIN_ALL;
    }
    return new CompiledLabInfoMask(mask);
  }

  private CompiledLabInfoMask(LabInfoMask mask) {
    if (!mask.hasFieldMask()) {
      this.compiledFieldMask = CompiledFieldMask.retainAll();
    } else if (mask.getFieldMask().getPathsCount() == 0) {
      this.compiledFieldMask = CompiledFieldMask.retainNone();
    } else {
      this.compiledFieldMask = CompiledFieldMask.of(mask.getFieldMask());
    }
    this.hasUnknownTopLevelPaths = compiledFieldMask.hasUnknownTopLevelPaths(KNOWN_FIELDS);
    this.keepsLabInfo = !compiledFieldMask.retainsNothing();
    this.keepsLabLocator = keepsLabInfo && compiledFieldMask.keepsField("lab_locator");
    this.keepsLabServerSetting = keepsLabInfo && compiledFieldMask.keepsField("lab_server_setting");
    this.keepsLabServerFeature = keepsLabInfo && compiledFieldMask.keepsField("lab_server_feature");
    this.keepsLabStatus = keepsLabInfo && compiledFieldMask.keepsField("lab_status");
    this.keepsHostProperties =
        keepsLabInfo && compiledFieldMask.isRequested("lab_server_feature.host_properties");
    this.labLocatorSubMask = compiledFieldMask.subMask("lab_locator");
    this.labServerSettingSubMask = compiledFieldMask.subMask("lab_server_setting");
    this.labServerFeatureSubMask = compiledFieldMask.subMask("lab_server_feature");
  }

  /** Whether the mask keeps LabInfo records at all. */
  public boolean keepsLabInfo() {
    return keepsLabInfo;
  }

  public boolean hasFieldMask() {
    return !compiledFieldMask.retainsAll();
  }

  public Optional<FieldMask> fieldMask() {
    return compiledFieldMask.rawFieldMask();
  }

  public boolean keepsLabLocator() {
    return keepsLabLocator;
  }

  public boolean keepsLabServerSetting() {
    return keepsLabServerSetting;
  }

  public boolean keepsLabServerFeature() {
    return keepsLabServerFeature;
  }

  public boolean keepsLabStatus() {
    return keepsLabStatus;
  }

  public boolean keepsHostProperties() {
    return keepsHostProperties;
  }

  public boolean matchesField(String targetField) {
    return keepsLabInfo && compiledFieldMask.isRequested(targetField);
  }

  public LabLocator projectLabLocator(LabLocator locator) {
    return CompiledFieldMask.trimSubMessage(labLocatorSubMask, locator);
  }

  public LabServerSetting projectLabServerSetting(LabServerSetting setting) {
    return CompiledFieldMask.trimSubMessage(labServerSettingSubMask, setting);
  }

  public LabServerFeature projectLabServerFeature(LabServerFeature feature) {
    return CompiledFieldMask.trimSubMessage(labServerFeatureSubMask, feature);
  }

  public LabInfo finalizeLabInfo(LabInfo labInfo) {
    return compiledFieldMask.trimIfUnknownPaths(hasUnknownTopLevelPaths, labInfo);
  }
}
