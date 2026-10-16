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

/**
 * Whole-corpus statistics for one key, exposed by {@link FleetIndex#keyStats(String)}.
 *
 * <p>These are the numbers the key catalogs and search-bar suggestions ask for an unfiltered
 * corpus: how many records carry the key at all (coverage), how many distinct value combinations it
 * takes (the groups a group-by on the key would produce, before the no-value bucket), and whether
 * any record carries more than one value. {@link FleetIndexBuilder} computes them during the index
 * build pass for core keys, and {@link DimensionOverlay} computes them when binding an on-demand
 * dimension overlay to a snapshot.
 *
 * @param entityCount number of records carrying at least one value for the key
 * @param distinctCombinations number of distinct value sets across the records carrying the key
 * @param multiValued whether some record carries two or more distinct values for the key; when
 *     false, no two values ever share a record, so the number of records carrying any of several
 *     values is the sum of their value counts
 */
public record KeyStats(int entityCount, int distinctCombinations, boolean multiValued) {

  /** Statistics of a key no record carries. */
  public static final KeyStats EMPTY = new KeyStats(0, 0, /* multiValued= */ false);

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
}
