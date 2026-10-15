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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.Postings;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The per-key numbers the key catalogs need: how many records carry a key, and how many groups a
 * group-by on it would produce.
 *
 * <p>Over the whole corpus both come from {@link Postings#stats} (cached per key per snapshot for
 * core-indexed keys), so opening a dialog or typing in it costs no corpus scan. Over a filtered
 * result set the group count is one pass over that set, which is the price of answering for the
 * data the user is looking at.
 */
final class KeyCounts {

  private KeyCounts() {}

  /** Number of records carrying each indexed key, from the per-snapshot key statistics. */
  static ImmutableMap<String, Integer> entityCounts(FleetIndex index, Postings postings) {
    return index.keyIds().stream()
        .collect(toImmutableMap(keyId -> keyId, keyId -> postings.stats(keyId).entityCount()));
  }

  /**
   * Group count of a key over the current result set, or over the whole corpus when {@code current}
   * is empty (no filter is active).
   */
  static int groupCount(
      SearchCorpus corpus, Optional<ImmutableList<Integer>> current, String keyId) {
    return current.isPresent()
        ? filteredGroupCount(corpus, current.get(), keyId)
        : globalGroupCount(corpus, keyId);
  }

  /** Group count of a key over the whole corpus, from the per-snapshot key statistics. */
  static int globalGroupCount(SearchCorpus corpus, String keyId) {
    return corpus.postings().stats(keyId).groupCount(corpus.recordCount());
  }

  /**
   * Group count of a key over a filtered result set, in one pass over the set: the distinct value
   * combinations plus one no-value bucket when some record in the set lacks the key.
   */
  static int filteredGroupCount(SearchCorpus corpus, ImmutableList<Integer> current, String keyId) {
    Set<Set<String>> combinations = new HashSet<>();
    boolean hasMissing = false;
    for (int recordIndex : current) {
      ImmutableSet<String> values = corpus.valuesForKey(recordIndex, keyId);
      if (values.isEmpty()) {
        hasMissing = true;
      } else {
        combinations.add(values);
      }
    }
    return combinations.size() + (hasMissing ? 1 : 0);
  }
}
