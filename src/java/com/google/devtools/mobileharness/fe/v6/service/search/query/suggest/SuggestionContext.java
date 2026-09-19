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

package com.google.devtools.mobileharness.fe.v6.service.search.query.suggest;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.Postings;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetFilterEngine;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyVocabulary;
import com.google.devtools.mobileharness.fe.v6.service.search.query.SearchCorpus;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

/**
 * What one suggestion request knows about the corpus it searches and the filters already applied,
 * plus the counting primitives every suggestion pattern shares.
 *
 * <p>Built once per request by {@link #create}. It answers three questions for the pattern
 * generators and the ranker: which entity vocabulary is in force, which records match the active
 * filters, and how many records a candidate condition would leave. It holds no request text and
 * makes no ranking decisions.
 *
 * <p>Counting vocabulary used throughout: the <b>scope</b> is the set of records a count is taken
 * over. With active filters the scope is the filtered set; without them it is the whole corpus.
 * "Global" counts always mean the whole corpus regardless of filters.
 */
final class SuggestionContext {

  private final SearchCorpus corpus;
  private final FleetIndex index;
  private final Postings postings;
  private final ImmutableList<Filter> filters;
  private final ImmutableSet<String> activeKeys;
  private final ImmutableList<Integer> current;
  private final BitSet currentBits;

  private SuggestionContext(
      SearchCorpus corpus, ImmutableList<Filter> filters, ImmutableList<Integer> current) {
    this.corpus = corpus;
    this.index = corpus.index();
    this.postings = corpus.postings();
    this.filters = filters;
    this.activeKeys = filters.stream().map(Filter::getKey).collect(toImmutableSet());
    this.current = current;
    this.currentBits = toBitSet(current);
  }

  /** Evaluates the active filters against the corpus once and captures the result. */
  static SuggestionContext create(
      SearchCorpus corpus, List<Filter> filters, FleetFilterEngine filterEngine) {
    ImmutableList<Filter> active = ImmutableList.copyOf(filters);
    return new SuggestionContext(corpus, active, filterEngine.match(corpus, active));
  }

  SearchCorpus corpus() {
    return corpus;
  }

  /** The key world of the searched entity; the only way a pattern may learn about keys. */
  KeyVocabulary vocabulary() {
    return corpus.vocabulary();
  }

  FleetIndex index() {
    return index;
  }

  Postings postings() {
    return postings;
  }

  ImmutableList<Filter> filters() {
    return filters;
  }

  boolean hasFilters() {
    return !filters.isEmpty();
  }

  /** Whether the key already carries a filter chip in this request. */
  boolean isActiveKey(String keyId) {
    return activeKeys.contains(keyId);
  }

  /** The chip applied on {@code keyId}, if any. */
  Optional<Filter> chipFor(String keyId) {
    return filters.stream().filter(filter -> filter.getKey().equals(keyId)).findFirst();
  }

  /** The active filters with the chip on {@code keyId} removed. */
  ImmutableList<Filter> filtersExcept(String keyId) {
    return filters.stream()
        .filter(filter -> !filter.getKey().equals(keyId))
        .collect(ImmutableList.toImmutableList());
  }

  /** Whether the index holds values for the key, as opposed to a cold long-tail key. */
  boolean isIndexed(String keyId) {
    return index.keyIds().contains(keyId);
  }

  /** Record positions matching the active filters, in index order. */
  ImmutableList<Integer> current() {
    return current;
  }

  BitSet currentBits() {
    return currentBits;
  }

  /** Number of records in scope. */
  int scopeSize() {
    return hasFilters() ? current.size() : corpus.recordCount();
  }

  /** Number of records in scope carrying {@code valueLower} under {@code keyId}. */
  int scopeCount(String keyId, String valueLower) {
    if (!hasFilters()) {
      return index.valueCount(keyId, valueLower);
    }
    return intersectionCount(postings.get(keyId, valueLower), currentBits);
  }

  /** Number of records in scope carrying any of {@code valuesLower} under {@code keyId}. */
  int scopeUnionCount(String keyId, ImmutableList<String> valuesLower) {
    BitSet union = new BitSet();
    for (String value : valuesLower) {
      Arrays.stream(postings.get(keyId, value)).forEach(union::set);
    }
    if (hasFilters()) {
      union.and(currentBits);
    }
    return union.cardinality();
  }

  /** Number of records in scope that have at least one value for {@code keyId}. */
  int scopePresenceCount(String keyId) {
    BitSet withKey = recordsWithKey(keyId);
    if (hasFilters()) {
      withKey.and(currentBits);
    }
    return withKey.cardinality();
  }

  /** Number of records in the whole corpus that have at least one value for {@code keyId}. */
  int globalPresenceCount(String keyId) {
    return recordsWithKey(keyId).cardinality();
  }

  private BitSet recordsWithKey(String keyId) {
    BitSet withKey = new BitSet();
    for (int[] posting : postings.forKey(keyId).values()) {
      Arrays.stream(posting).forEach(withKey::set);
    }
    return withKey;
  }

  static int intersectionCount(int[] posting, BitSet set) {
    return (int) Arrays.stream(posting).filter(set::get).count();
  }

  static BitSet toBitSet(ImmutableList<Integer> positions) {
    BitSet set = new BitSet();
    positions.forEach(set::set);
    return set;
  }
}
