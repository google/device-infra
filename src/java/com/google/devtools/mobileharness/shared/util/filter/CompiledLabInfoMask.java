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

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
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

  private final FieldMaskCompiler compiler;
  private final boolean keepsLabStatus;
  private final boolean keepsLabLocator;
  private final boolean keepsLabServerSetting;
  private final boolean keepsLabServerFeature;
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
    this.compiler =
        mask.hasFieldMask() ? FieldMaskCompiler.of(mask.getFieldMask()) : FieldMaskCompiler.all();
    this.keepsLabStatus = compiler.keepsField("lab_status");
    this.keepsLabLocator = compiler.keepsField("lab_locator");
    this.keepsLabServerSetting = compiler.keepsField("lab_server_setting");
    this.keepsLabServerFeature = compiler.keepsField("lab_server_feature");
    this.hasUnknownPaths = compiler.hasUnknownPaths(KNOWN_FIELDS);
  }

  /** Whether the mask keeps LabInfo records at all. */
  public boolean keepsLabInfo() {
    return !compiler.isNoneRetained();
  }

  public boolean hasFieldMask() {
    return !compiler.isAllRetained();
  }

  @Nullable
  public FieldMask fieldMask() {
    return compiler.fieldMask();
  }

  public boolean keepsLabStatus() {
    return keepsLabStatus;
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

  public boolean matchesField(String targetField) {
    if (!keepsLabInfo()) {
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
  public LabLocator trimLabLocator(@Nullable LabLocator locator) {
    return compiler.trimSubMessage("lab_locator", locator);
  }

  @Nullable
  public LabServerSetting trimLabServerSetting(@Nullable LabServerSetting setting) {
    return compiler.trimSubMessage("lab_server_setting", setting);
  }

  @Nullable
  public LabServerFeature trimLabServerFeature(@Nullable LabServerFeature feature) {
    return compiler.trimSubMessage("lab_server_feature", feature);
  }

  /** Builds the final {@link LabInfo}, applying safety-net trimming if unknown paths exist. */
  public LabInfo build(LabInfo.Builder builder) {
    LabInfo result = builder.build();
    if (hasUnknownPaths && compiler.fieldMask() != null) {
      return FieldMaskUtil.trim(compiler.fieldMask(), result);
    }
    return result;
  }
}
