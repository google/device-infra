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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.errorprone.annotations.Immutable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Matches dimension names against the names a {@code DimensionsMask} asks for, ignoring ASCII case.
 *
 * <p>Built once per request and then asked about every dimension of every device, so {@link
 * #matches} allocates nothing: the requested names are bucketed by length, a dimension whose name
 * length has no bucket is rejected without reading its characters, and the few candidates of the
 * same length are compared with {@link Ascii#equalsIgnoreCase}.
 */
@Immutable
public final class DimensionNameMatcher {

  private static final DimensionNameMatcher NONE = new DimensionNameMatcher(new String[0][]);

  /** Index is the name length; a null entry means no requested name has that length. */
  @SuppressWarnings("Immutable") // Filled in by of() and never written afterwards.
  private final String[][] namesByLength;

  private DimensionNameMatcher(String[][] namesByLength) {
    this.namesByLength = namesByLength;
  }

  /** Creates a matcher for {@code names}; duplicates that differ only in ASCII case collapse. */
  public static DimensionNameMatcher of(Collection<String> names) {
    if (names.isEmpty()) {
      return NONE;
    }
    int maxLength = 0;
    for (String name : names) {
      maxLength = Math.max(maxLength, name.length());
    }
    List<List<String>> buckets = new ArrayList<>();
    for (int i = 0; i <= maxLength; i++) {
      buckets.add(null);
    }
    for (String name : names) {
      List<String> bucket = buckets.get(name.length());
      if (bucket == null) {
        bucket = new ArrayList<>();
        buckets.set(name.length(), bucket);
      }
      if (!containsIgnoreCase(bucket, name)) {
        bucket.add(name);
      }
    }
    String[][] namesByLength = new String[maxLength + 1][];
    for (int i = 0; i <= maxLength; i++) {
      List<String> bucket = buckets.get(i);
      if (bucket != null) {
        namesByLength[i] = bucket.toArray(new String[0]);
      }
    }
    return new DimensionNameMatcher(namesByLength);
  }

  /** Whether no name was requested. Callers decide what that means for them. */
  public boolean matchesNothing() {
    return namesByLength.length == 0;
  }

  /** Whether {@code dimensionName} equals a requested name ignoring ASCII case. */
  public boolean matches(String dimensionName) {
    int length = dimensionName.length();
    if (length >= namesByLength.length) {
      return false;
    }
    @Nullable String[] candidates = namesByLength[length];
    if (candidates == null) {
      return false;
    }
    for (String candidate : candidates) {
      if (Ascii.equalsIgnoreCase(candidate, dimensionName)) {
        return true;
      }
    }
    return false;
  }

  /** Returns the dimensions whose names match, in their original order. */
  public ImmutableList<DeviceDimension> filter(List<DeviceDimension> dimensions) {
    ImmutableList.Builder<DeviceDimension> kept = null;
    // Indexed loop: this runs for every dimension of every device in a response.
    for (int i = 0, size = dimensions.size(); i < size; i++) {
      DeviceDimension dimension = dimensions.get(i);
      if (matches(dimension.getName())) {
        if (kept == null) {
          kept = ImmutableList.builder();
        }
        kept.add(dimension);
      }
    }
    return kept == null ? ImmutableList.of() : kept.build();
  }

  private static boolean containsIgnoreCase(List<String> names, String name) {
    for (String candidate : names) {
      if (Ascii.equalsIgnoreCase(candidate, name)) {
        return true;
      }
    }
    return false;
  }
}
