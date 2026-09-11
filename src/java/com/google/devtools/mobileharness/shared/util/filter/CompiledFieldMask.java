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
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Pre-compiled immutable representation of a {@link FieldMask} for fast top-level field state
 * lookups and sub-mask extraction.
 *
 * <p>Intended to be constructed once per RPC query to populate pre-computed primitive gates and
 * sub-masks on domain-specific compiled mask classes without repeated string parsing on per-record
 * hot paths.
 */
@Immutable
public final class CompiledFieldMask {

  /** The projection state of a message field with respect to a {@link FieldMask}. */
  public enum NodeState {
    /** The field and all of its sub-fields are omitted. */
    OMITTED,
    /** The field is requested in full without sub-field pruning. */
    FULL,
    /** Only specific descendant sub-paths of this message field are requested. */
    PARTIAL
  }

  private static final CompiledFieldMask ALL = new CompiledFieldMask(null, null);
  private static final CompiledFieldMask NONE =
      new CompiledFieldMask(FieldMask.getDefaultInstance(), ImmutableSet.of());

  @Nullable private final FieldMask rawFieldMask;
  @Nullable private final ImmutableSet<String> fieldPaths;

  /** Returns a compiled mask that retains all fields. */
  public static CompiledFieldMask all() {
    return ALL;
  }

  /** Compiles the given {@link FieldMask}, or returns {@link #all()} if {@code mask} is null. */
  public static CompiledFieldMask of(@Nullable FieldMask mask) {
    if (mask == null) {
      return ALL;
    }
    if (mask.getPathsList().isEmpty()) {
      return NONE;
    }
    return new CompiledFieldMask(mask, ImmutableSet.copyOf(mask.getPathsList()));
  }

  private CompiledFieldMask(
      @Nullable FieldMask rawFieldMask, @Nullable ImmutableSet<String> fieldPaths) {
    this.rawFieldMask = rawFieldMask;
    this.fieldPaths = fieldPaths;
  }

  /** Whether all fields are retained without filtering. */
  public boolean isAllRetained() {
    return fieldPaths == null;
  }

  /** Whether an empty field mask was specified, meaning no fields are retained. */
  public boolean isNoneRetained() {
    return fieldPaths != null && fieldPaths.isEmpty();
  }

  /** Returns the underlying {@link FieldMask}, or null if all fields are retained. */
  @Nullable
  public FieldMask rawFieldMask() {
    return rawFieldMask;
  }

  /** Determines the {@link NodeState} of the given field path. */
  public NodeState stateOf(String targetField) {
    if (isNoneRetained()) {
      return NodeState.OMITTED;
    }
    if (isAllRetained()) {
      return NodeState.FULL;
    }
    if (fieldPaths.contains(targetField)) {
      return NodeState.FULL;
    }
    String childPrefix = targetField + ".";
    boolean hasChildPath = false;
    for (String path : fieldPaths) {
      if (targetField.startsWith(path + ".")) {
        return NodeState.FULL;
      }
      if (path.startsWith(childPrefix)) {
        hasChildPath = true;
      }
    }
    return hasChildPath ? NodeState.PARTIAL : NodeState.OMITTED;
  }

  /** Whether the given field path (or any ancestor/descendant) is requested. */
  public boolean keepsField(String targetField) {
    return stateOf(targetField) != NodeState.OMITTED;
  }

  /**
   * Extracts the sub-{@link FieldMask} for a nested message field.
   *
   * @return {@code null} if {@code fieldName} is {@link NodeState#OMITTED}; an empty {@link
   *     FieldMask} if {@code fieldName} is {@link NodeState#FULL}; or a non-empty {@link FieldMask}
   *     containing relative child paths if {@code fieldName} is {@link NodeState#PARTIAL}.
   */
  @Nullable
  public FieldMask extractSubMask(String fieldName) {
    NodeState state = stateOf(fieldName);
    if (state == NodeState.OMITTED) {
      return null;
    }
    if (state == NodeState.FULL) {
      return FieldMask.getDefaultInstance();
    }
    FieldMask.Builder subMaskBuilder = FieldMask.newBuilder();
    String prefix = fieldName + ".";
    for (String path : fieldPaths) {
      if (path.startsWith(prefix)) {
        subMaskBuilder.addPaths(path.substring(prefix.length()));
      }
    }
    return subMaskBuilder.getPathsCount() > 0
        ? subMaskBuilder.build()
        : FieldMask.getDefaultInstance();
  }

  /** Returns true if any path in this mask targets a top-level field not in {@code knownFields}. */
  public boolean hasUnknownPaths(Set<String> knownFields) {
    if (isAllRetained() || isNoneRetained()) {
      return false;
    }
    for (String path : fieldPaths) {
      int dotIndex = path.indexOf('.');
      String rootField = dotIndex >= 0 ? path.substring(0, dotIndex) : path;
      if (!knownFields.contains(rootField)) {
        return true;
      }
    }
    return false;
  }

  /** Returns the set of raw paths if present. */
  public Optional<ImmutableSet<String>> fieldPaths() {
    return Optional.ofNullable(fieldPaths);
  }
}
