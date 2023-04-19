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

package com.google.devtools.mobileharness.shared.util.message;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.FieldMask;
import java.util.Optional;
import java.util.stream.Stream;

/** More utilities for {@link FieldMask}. */
public class FieldMaskUtils {

  /**
   * Returns a sub field mask relative to a sub field described by {@code fieldDescriptor} and
   * {@code otherFieldDescriptors}.
   *
   * <p>For example, subFieldMask(["a", "b.c.d", "b.c.e.f"], b, c) will return ["d", "e.f"].
   *
   * <p>Additionally, subFieldMask(["a", "b.c", "d"], b, c) will return Optional.empty() which means
   * no mask should be applied and all fields in c should be kept.
   *
   * <p>In detail, this method will go through each path in the given field mask (assuming the given
   * field descriptors are "b, c, d"),
   *
   * <ol>
   *   <li>If a path equals to "b" or "b.c" or "b.c.d", Optional.empty() will be returned
   *       immediately.
   *   <li>If a path doesn't start with "b.c.d.", it will be omitted.
   *   <li>If a path starts with "b.c.d.", it will be added to the result and the "b.c.d." prefix
   *       will be removed.
   * </ol>
   *
   * This method doesn't check if the given field mask is valid.
   */
  public static Optional<FieldMask> subFieldMask(
      FieldMask fieldMask,
      FieldDescriptor fieldDescriptor,
      FieldDescriptor... otherFieldDescriptors) {
    String pathPrefix =
        Stream.concat(Stream.of(fieldDescriptor), stream(otherFieldDescriptors))
            .map(FieldDescriptor::getName)
            .collect(joining("."));
    String pathPrefixWithDot = pathPrefix + ".";

    FieldMask.Builder result = FieldMask.newBuilder();
    for (String path : fieldMask.getPathsList()) {
      if (path.equals(pathPrefix)
          || (pathPrefix.startsWith(path) && pathPrefix.charAt(path.length()) == '.')) {
        return Optional.empty();
      }
      if (path.startsWith(pathPrefixWithDot)) {
        result.addPaths(path.substring(pathPrefixWithDot.length()));
      }
    }
    return Optional.of(result.build());
  }

  private FieldMaskUtils() {}
}
