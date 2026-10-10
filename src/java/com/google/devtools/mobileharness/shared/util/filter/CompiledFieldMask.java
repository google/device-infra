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
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.util.FieldMaskUtil;
import java.util.Optional;

/**
 * An immutable, pre-parsed view of a {@link FieldMask} that classifies the direct child fields of a
 * single message into {@link NodeState#OMITTED}, {@link NodeState#FULL}, or {@link
 * NodeState#PARTIAL}.
 *
 * <p>Projection push-down over a large fleet must decide, for every message it emits, which fields
 * to populate. Doing that from the raw dot-separated {@link FieldMask} paths per record means a
 * linear string scan per field per record. This class parses the paths once, so a compiled mask can
 * cache the resulting states and sub-masks in its constructor and the per-record hot path reads
 * only cheap primitives.
 *
 * <p>The view is deliberately flat. It resolves the state of the direct children of one message
 * and, for a partially requested child, returns a sub-{@link FieldMask} that the caller delegates
 * to {@link FieldMaskUtil#trim}. It does not recurse, because each compiled mask only dispatches on
 * its own fields and delegates any deeper trimming.
 *
 * <p>Field paths are matched exactly as protobuf field names, i.e. case-sensitively. Value-level
 * filters such as dimension-name whitelists are a separate concern and are not expressible as a
 * {@link FieldMask}, so they are handled by the individual compiled masks rather than here.
 */
@Immutable
public final class CompiledFieldMask {

  /** The projection state of a field. */
  public enum NodeState {
    /** Neither the field nor any descendant is requested; skip it entirely. */
    OMITTED,
    /** The whole field is retained, with no sub-field pruning. */
    FULL,
    /** Only some descendants of the field are retained; trim it by the sub-mask. */
    PARTIAL,
  }

  private static final CompiledFieldMask RETAIN_ALL =
      new CompiledFieldMask(/* retainsAll= */ true, Optional.empty(), ImmutableSet.of());
  private static final CompiledFieldMask RETAIN_NONE =
      new CompiledFieldMask(
          /* retainsAll= */ false, Optional.of(FieldMask.getDefaultInstance()), ImmutableSet.of());

  private final boolean retainsAll;
  private final Optional<FieldMask> rawFieldMask;
  private final ImmutableSet<String> paths;

  private CompiledFieldMask(
      boolean retainsAll, Optional<FieldMask> rawFieldMask, ImmutableSet<String> paths) {
    this.retainsAll = retainsAll;
    this.rawFieldMask = rawFieldMask;
    this.paths = paths;
  }

  /** Returns a mask that retains every field, equivalent to an absent field mask. */
  public static CompiledFieldMask retainAll() {
    return RETAIN_ALL;
  }

  /** Returns a mask that retains no fields, equivalent to an explicitly empty field mask. */
  public static CompiledFieldMask retainNone() {
    return RETAIN_NONE;
  }

  /**
   * Compiles the given non-null {@link FieldMask}.
   *
   * <p>An empty mask (present but with no paths) retains nothing ({@link #retainNone()}), which for
   * a message-typed field means the message is dropped. Callers whose protocol treats an absent
   * field mask differently from an empty field mask should call {@link #retainAll()} when no field
   * mask is set.
   */
  public static CompiledFieldMask of(FieldMask mask) {
    requireNonNull(mask);
    if (mask.getPathsCount() == 0) {
      return RETAIN_NONE;
    }
    return new CompiledFieldMask(
        /* retainsAll= */ false, Optional.of(mask), ImmutableSet.copyOf(mask.getPathsList()));
  }

  /** Whether every field is retained because no field mask was set. */
  public boolean retainsAll() {
    return retainsAll;
  }

  /** Whether no field is retained because an empty field mask was set. */
  public boolean retainsNothing() {
    return !retainsAll && paths.isEmpty();
  }

  /** Returns the raw {@link FieldMask}, or empty if all fields are retained. */
  public Optional<FieldMask> rawFieldMask() {
    return rawFieldMask;
  }

  /**
   * Whether the given dot-separated {@code path} is touched by this mask: the path is listed, an
   * ancestor of it is listed (so it is fully covered), or a descendant of it is listed (so it is
   * partially needed).
   *
   * <p>This accepts an arbitrary nested path, which is what callers use to test a deep field such
   * as {@code device_feature.composite_dimension.supported_dimension}.
   */
  public boolean isRequested(String path) {
    if (retainsAll) {
      return true;
    }
    for (String candidate : paths) {
      if (candidate.equals(path)
          || path.startsWith(candidate + ".")
          || candidate.startsWith(path + ".")) {
        return true;
      }
    }
    return false;
  }

  /** Classifies a direct child {@code field} of the masked message. */
  public NodeState stateOf(String field) {
    if (retainsAll || paths.contains(field)) {
      return NodeState.FULL;
    }
    String prefix = field + ".";
    for (String candidate : paths) {
      if (candidate.startsWith(prefix)) {
        return NodeState.PARTIAL;
      }
    }
    return NodeState.OMITTED;
  }

  /** Whether a direct child {@code field} is retained (either FULL or PARTIAL). */
  public boolean keepsField(String field) {
    return stateOf(field) != NodeState.OMITTED;
  }

  /**
   * Returns the sub-mask for a {@link NodeState#PARTIAL} child {@code field}: the descendant paths
   * with the {@code field + "."} prefix stripped.
   *
   * <p>Returns empty for a {@link NodeState#FULL} or {@link NodeState#OMITTED} child, where no
   * sub-mask trimming applies.
   */
  public Optional<FieldMask> subMask(String field) {
    if (retainsAll || paths.contains(field)) {
      return Optional.empty();
    }
    String prefix = field + ".";
    FieldMask.Builder builder = FieldMask.newBuilder();
    for (String candidate : paths) {
      if (candidate.startsWith(prefix)) {
        builder.addPaths(candidate.substring(prefix.length()));
      }
    }
    return builder.getPathsCount() == 0 ? Optional.empty() : Optional.of(builder.build());
  }

  /**
   * Whether any path has a top-level segment that is not in {@code knownFields}.
   *
   * <p>A compiled mask uses this to decide whether a safety-net {@link FieldMaskUtil#trim} is
   * needed on build for paths it does not recognize, for example fields added to the proto after
   * the compiled mask was written.
   */
  public boolean hasUnknownTopLevelPaths(ImmutableSet<String> knownFields) {
    if (retainsAll) {
      return false;
    }
    for (String candidate : paths) {
      int dot = candidate.indexOf('.');
      String root = dot < 0 ? candidate : candidate.substring(0, dot);
      if (!knownFields.contains(root)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Trims {@code message} using {@code subMask} if and only if {@code subMask} contains partial
   * sub-paths; returns {@code message} directly in O(1) without reflection if {@code subMask} is
   * empty.
   */
  public static <M extends Message> M trimSubMessage(Optional<FieldMask> subMask, M message) {
    if (subMask.isEmpty() || subMask.get().getPathsCount() == 0) {
      return message;
    }
    return FieldMaskUtil.trim(subMask.get(), message);
  }

  /**
   * Safety-net fallback that trims {@code message} via {@link FieldMaskUtil#trim} only if the mask
   * contains unknown top-level paths not handled by the compiled mask.
   */
  public <M extends Message> M trimIfUnknownPaths(boolean hasUnknownPaths, M message) {
    if (hasUnknownPaths && rawFieldMask.isPresent()) {
      return FieldMaskUtil.trim(rawFieldMask.get(), message);
    }
    return message;
  }
}
