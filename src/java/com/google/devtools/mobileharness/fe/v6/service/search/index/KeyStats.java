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

package com.google.devtools.mobileharness.fe.v6.service.search.index;

import com.google.common.collect.ImmutableMap;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Whole-corpus statistics for one key, derived from its posting lists.
 *
 * <p>These are the numbers the key catalogs ask for an unfiltered corpus: how many records carry
 * the key at all (coverage), and how many distinct value combinations it takes (the groups a
 * group-by on the key would produce, before the no-value bucket). For a single-valued key each
 * distinct value is its own combination, and the whole record is derived from the posting lists
 * alone. Only a multi-valued key (one where some record carries several values, such as owner or
 * driver) needs a pass over the records that carry it to count combinations.
 *
 * @param entityCount number of records carrying at least one value for the key
 * @param distinctCombinations number of distinct value sets across the records carrying the key
 */
public record KeyStats(int entityCount, int distinctCombinations) {

  /** Statistics of a key no record carries. */
  public static final KeyStats EMPTY = new KeyStats(0, 0);

  /** Whether some record among {@code recordCount} lacks the key entirely. */
  public boolean hasMissing(int recordCount) {
    return entityCount < recordCount;
  }

  /**
   * Groups a group-by on this key produces over a corpus of {@code recordCount} records: the
   * distinct value combinations plus one no-value bucket when some record lacks the key.
   */
  public int groupCount(int recordCount) {
    return distinctCombinations + (hasMissing(recordCount) ? 1 : 0);
  }

  /**
   * Computes the statistics of a key from its posting lists.
   *
   * @param postings the key's posting lists, value to ascending record indices
   * @param valuesForKey the lowercased value set a record carries for the key, consulted only for a
   *     multi-valued key and only for the records that carry it
   */
  public static KeyStats compute(
      ImmutableMap<String, int[]> postings, IntFunction<Set<String>> valuesForKey) {
    BitSet carriers = new BitSet();
    long totalPostings = 0;
    for (int[] posting : postings.values()) {
      for (int recordIndex : posting) {
        carriers.set(recordIndex);
      }
      totalPostings += posting.length;
    }
    int entityCount = carriers.cardinality();
    if (totalPostings == entityCount) {
      // Every carrying record holds at most one value, so each value is its own combination.
      return new KeyStats(entityCount, postings.size());
    }
    Set<Set<String>> combinations = new HashSet<>();
    for (int i = carriers.nextSetBit(0); i >= 0; i = carriers.nextSetBit(i + 1)) {
      combinations.add(valuesForKey.apply(i));
    }
    return new KeyStats(entityCount, combinations.size());
  }
}
