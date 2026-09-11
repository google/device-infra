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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.util.FieldMaskUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Pre-compiles a {@link FieldMask} once per request into fast, immutable field-state lookups and
 * extracted sub-masks.
 *
 * <p>Encapsulates path segmentation, prefix analysis, and unknown-path checking, eliminating
 * repetitive string-slicing across compiled mask implementations.
 */
@Immutable
public final class FieldMaskCompiler {

  /** The retention state of a top-level field. */
  public enum FieldState {
    /** The field is omitted entirely. */
    OMITTED,
    /** The entire field is requested without child pruning. */
    FULL,
    /** Specific sub-fields of this message are requested. */
    PARTIAL
  }

  private static final FieldMaskCompiler ALL = new FieldMaskCompiler(/* fieldMask= */ null);
  private static final FieldMaskCompiler NONE =
      new FieldMaskCompiler(FieldMask.getDefaultInstance());

  @Nullable private final FieldMask fieldMask;
  @Nullable private final ImmutableSet<String> fieldPaths;
  private final ImmutableMap<String, FieldState> rootFieldStates;
  private final ImmutableMap<String, FieldMask> subMasks;

  /** Returns a compiler representing full retention (mask is null / unset). */
  public static FieldMaskCompiler all() {
    return ALL;
  }

  /** Returns a compiler representing empty retention (mask has empty paths list). */
  public static FieldMaskCompiler none() {
    return NONE;
  }

  /**
   * Compiles the given {@link FieldMask}.
   *
   * @param fieldMask the field mask to compile. If {@code null}, retains all fields. If empty,
   *     retains none.
   */
  public static FieldMaskCompiler of(@Nullable FieldMask fieldMask) {
    if (fieldMask == null) {
      return ALL;
    }
    if (fieldMask.getPathsList().isEmpty()) {
      return NONE;
    }
    return new FieldMaskCompiler(fieldMask);
  }

  private FieldMaskCompiler(@Nullable FieldMask fieldMask) {
    this.fieldMask = fieldMask;
    if (fieldMask == null) {
      this.fieldPaths = null;
      this.rootFieldStates = ImmutableMap.of();
      this.subMasks = ImmutableMap.of();
      return;
    }

    List<String> paths = fieldMask.getPathsList();
    if (paths.isEmpty()) {
      this.fieldPaths = ImmutableSet.of();
      this.rootFieldStates = ImmutableMap.of();
      this.subMasks = ImmutableMap.of();
      return;
    }

    this.fieldPaths = ImmutableSet.copyOf(paths);
    Map<String, FieldState> states = new HashMap<>();
    Map<String, List<String>> partialSubPaths = new HashMap<>();

    for (String path : paths) {
      int dotIndex = path.indexOf('.');
      if (dotIndex < 0) {
        // Root field is requested in full.
        states.put(path, FieldState.FULL);
        partialSubPaths.remove(path);
      } else {
        String rootField = path.substring(0, dotIndex);
        // Only collect sub-paths if root field is not already FULL.
        if (states.get(rootField) != FieldState.FULL) {
          states.put(rootField, FieldState.PARTIAL);
          partialSubPaths
              .computeIfAbsent(rootField, k -> new ArrayList<>())
              .add(path.substring(dotIndex + 1));
        }
      }
    }

    this.rootFieldStates = ImmutableMap.copyOf(states);

    ImmutableMap.Builder<String, FieldMask> subMasksBuilder = ImmutableMap.builder();
    for (Map.Entry<String, FieldState> entry : states.entrySet()) {
      String root = entry.getKey();
      if (entry.getValue() == FieldState.FULL) {
        subMasksBuilder.put(root, FieldMask.getDefaultInstance());
      } else {
        List<String> subPaths = partialSubPaths.get(root);
        if (subPaths != null && !subPaths.isEmpty()) {
          subMasksBuilder.put(root, FieldMask.newBuilder().addAllPaths(subPaths).build());
        }
      }
    }
    this.subMasks = subMasksBuilder.buildOrThrow();
  }

  /** Whether all fields are retained without masking (field_mask is unset). */
  public boolean isAllRetained() {
    return fieldMask == null;
  }

  /** Whether no fields are retained (field_mask is set but empty). */
  public boolean isNoneRetained() {
    return fieldMask != null && fieldPaths != null && fieldPaths.isEmpty();
  }

  /** Returns the original {@link FieldMask}, or {@code null} if full retention. */
  @Nullable
  public FieldMask fieldMask() {
    return fieldMask;
  }

  /** Returns the raw field paths set, or {@code null} if full retention. */
  @Nullable
  public ImmutableSet<String> fieldPaths() {
    return fieldPaths;
  }

  /** Returns the retention state of the given top-level field name. */
  public FieldState getFieldState(String fieldName) {
    if (isAllRetained()) {
      return FieldState.FULL;
    }
    if (isNoneRetained()) {
      return FieldState.OMITTED;
    }
    return rootFieldStates.getOrDefault(fieldName, FieldState.OMITTED);
  }

  /** Returns whether the top-level field is retained (either FULL or PARTIAL). */
  public boolean keepsField(String fieldName) {
    return getFieldState(fieldName) != FieldState.OMITTED;
  }

  /** Returns whether the top-level field is retained in full (no child pruning). */
  public boolean isFull(String fieldName) {
    return getFieldState(fieldName) == FieldState.FULL;
  }

  /**
   * Returns the sub-{@link FieldMask} for the given top-level field:
   *
   * <ul>
   *   <li>{@code null} if the field is {@link FieldState#OMITTED}.
   *   <li>An empty default instance {@link FieldMask#getDefaultInstance()} if {@link
   *       FieldState#FULL} (all sub-fields retained, no pruning needed).
   *   <li>A pruned sub-{@link FieldMask} with stripped prefixes if {@link FieldState#PARTIAL}.
   * </ul>
   */
  @Nullable
  public FieldMask extractSubMask(String fieldName) {
    if (isAllRetained()) {
      return FieldMask.getDefaultInstance();
    }
    if (isNoneRetained()) {
      return null;
    }
    FieldState state = getFieldState(fieldName);
    if (state == FieldState.OMITTED) {
      return null;
    }
    if (state == FieldState.FULL) {
      return FieldMask.getDefaultInstance();
    }
    return subMasks.get(fieldName);
  }

  /**
   * Trims a sub-message according to its extracted sub-mask.
   *
   * <p>If the field is {@link FieldState#FULL}, returns {@code message} directly with zero
   * reflection copy. If {@link FieldState#PARTIAL}, applies {@link FieldMaskUtil#trim}. If {@link
   * FieldState#OMITTED}, returns {@code null}.
   */
  @Nullable
  public <M extends Message> M trimSubMessage(String fieldName, @Nullable M message) {
    if (message == null) {
      return null;
    }
    FieldState state = getFieldState(fieldName);
    if (state == FieldState.OMITTED) {
      return null;
    }
    if (state == FieldState.FULL) {
      return message;
    }
    FieldMask subMask = subMasks.get(fieldName);
    if (subMask == null || subMask.getPathsList().isEmpty()) {
      return message;
    }
    return FieldMaskUtil.trim(subMask, message);
  }

  /** Returns whether the mask contains any paths unknown to the given set of known root fields. */
  public boolean hasUnknownPaths(Set<String> knownTopLevelFields) {
    if (isAllRetained() || isNoneRetained()) {
      return false;
    }
    for (String rootField : rootFieldStates.keySet()) {
      if (!knownTopLevelFields.contains(rootField)) {
        return true;
      }
    }
    return false;
  }
}
