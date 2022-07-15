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

package com.google.devtools.common.metrics.stability.util;

import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ErrorIdProto;
import javax.annotation.Nullable;

/** {@link ErrorId} comparator. */
public class ErrorIdComparator {

  private ErrorIdComparator() {}

  public static boolean equal(@Nullable ErrorId first, @Nullable ErrorId second) {
    return equal(ErrorModelConverter.toErrorIdProto(first), second);
  }

  public static boolean equal(
      @Nullable ErrorIdProto.ErrorIdOrBuilder first, @Nullable ErrorId second) {
    return equal(first, ErrorModelConverter.toErrorIdProto(second));
  }

  public static boolean equal(
      @Nullable ErrorId first, @Nullable ErrorIdProto.ErrorIdOrBuilder second) {
    return equal(second, first);
  }

  public static boolean equal(
      @Nullable ErrorIdProto.ErrorIdOrBuilder first,
      @Nullable ErrorIdProto.ErrorIdOrBuilder second) {
    if (first == second) {
      return true;
    }
    if (first == null || second == null) {
      return false;
    }
    return first.getCode() == second.getCode()
        && first.getName().equals(second.getName())
        && first.getType().equals(second.getType())
        && first.getNamespace().equals(second.getNamespace());
  }
}
