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

package com.google.devtools.mobileharness.shared.util.truth;

import static java.util.Objects.requireNonNull;

import com.google.common.truth.Correspondence;

/** Common {@link Correspondence} definitions. */
public class Correspondences {

  private static final Correspondence<String, String> CONTAINS =
      Correspondence.from(
          (actual, expected) -> requireNonNull(actual).contains(requireNonNull(expected)),
          "contains");

  private static final Correspondence<String, String> STARTS_WITH =
      Correspondence.from(
          (actual, expected) -> requireNonNull(actual).startsWith(requireNonNull(expected)),
          "starts with");

  public static Correspondence<String, String> contains() {
    return CONTAINS;
  }

  public static Correspondence<String, String> startsWith() {
    return STARTS_WITH;
  }

  private Correspondences() {}
}
