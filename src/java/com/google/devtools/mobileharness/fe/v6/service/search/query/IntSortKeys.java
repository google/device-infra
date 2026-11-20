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

package com.google.devtools.mobileharness.fe.v6.service.search.query;

import com.google.common.base.Strings;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDescriptor;
import java.util.OptionalInt;

/**
 * Sort keys under which the values of an integer-comparable key order numerically.
 *
 * <p>Sorting compares strings, so "10000" would sort before "5". For a value that {@link
 * FleetFilterEngine#tryParseInt} reads as an integer, the key is instead a fixed-width encoding
 * whose string order is numeric order, so sorting agrees with the integer comparison filters.
 * Integers sort before any other text, and an empty value keeps the empty key, so a record without
 * a value still sorts first in ascending order.
 */
final class IntSortKeys {

  private static final String INTEGER_PREFIX = "0";
  private static final String TEXT_PREFIX = "1";

  /** Whether {@code corpus} sorts the values of {@code keyId} as integers. */
  static boolean appliesTo(SearchCorpus corpus, String keyId) {
    return corpus.vocabulary().describe(keyId).map(KeyDescriptor::isIntComparable).orElse(false);
  }

  /** Returns the sort key of {@code value} of an integer-comparable key. */
  static String of(String value) {
    if (value.isEmpty()) {
      return value;
    }
    OptionalInt parsed = FleetFilterEngine.tryParseInt(value);
    if (parsed.isEmpty()) {
      return TEXT_PREFIX + value;
    }
    // Flipping the sign bit maps signed order onto unsigned order, which fixed-width hex keeps.
    return INTEGER_PREFIX
        + Strings.padStart(Integer.toHexString(parsed.getAsInt() ^ Integer.MIN_VALUE), 8, '0');
  }

  private IntSortKeys() {}
}
