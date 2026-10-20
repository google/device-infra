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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.Postings;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetFilterEngine;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyVocabulary;
import com.google.devtools.mobileharness.fe.v6.service.search.query.SearchCorpus;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private final FleetFilterEngine filterEngine;
  private final ImmutableList<Filter> filters;
  private final ImmutableSet<String> activeKeys;
  private final ImmutableList<Integer> current;
  private final BitSet currentBits;

  /**
   * Per key, the positions matching every active chip except that key's own, for modify-include
   * deltas. A context serves one request on one thread, so a plain map is enough.
   */
  private final Map<String, BitSet> matchesExceptChip = new HashMap<>();

  /**
   * Per key, the value counts and presence count within the filtered {@link #current} set, computed
   * in one pass over {@link #current} on first access so unapplied candidate keys in a filtered
   * request never trigger full-corpus posting list builds.
   */
  private final Map<String, ScopedKeyCounts> scopedKeyCounts = new HashMap<>();

  private SuggestionContext(
      SearchCorpus corpus,
      ImmutableList<Filter> filters,
      ImmutableList<Integer> current,
      FleetFilterEngine filterEngine) {
    this.corpus = corpus;
    this.index = corpus.index();
    this.postings = corpus.postings();
    this.filterEngine = filterEngine;
    this.filters = filters;
    this.activeKeys = filters.stream().map(Filter::getKey).collect(toImmutableSet());
    this.current = current;
    this.currentBits = toBitSet(current);
  }

  /**
   * Captures the request's filters and, when there are any, evaluates them against the corpus once.
   *
   * <p>With no filter the scope is the whole corpus, so {@link #current} stays empty and every
   * counting method except a multi-value OR or identifier prefix count on a multi-valued key
   * answers from the index or the per-snapshot key statistics instead. Listing every record
   * position (about 150,000 for the 1P device fleet) would otherwise cost a corpus pass on each
   * keystroke of unfiltered typeahead, the most common way the search box is used.
   */
  static SuggestionContext create(
      SearchCorpus corpus, List<Filter> filters, FleetFilterEngine filterEngine) {
    ImmutableList<Filter> active = ImmutableList.copyOf(filters);
    ImmutableList<Integer> current =
        active.isEmpty() ? ImmutableList.of() : filterEngine.match(corpus, active);
    return new SuggestionContext(corpus, active, current, filterEngine);
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
        .collect(toImmutableList());
  }

  /** Whether the index holds values for the key, as opposed to a cold long-tail key. */
  boolean isIndexed(String keyId) {
    return index.keyIds().contains(keyId);
  }

  /**
   * Record positions matching the active filters, in index order. Empty when no filter is active:
   * the scope is then the whole corpus and is never listed, so check {@link #hasFilters} first.
   */
  ImmutableList<Integer> current() {
    return current;
  }

  /** Number of records in scope. */
  int scopeSize() {
    return hasFilters() ? current.size() : corpus.recordCount();
  }

  /**
   * Number of records in scope carrying {@code valueLower} under {@code keyId}.
   *
   * <p>Without filters this is the value count precomputed in the index, so no posting list is
   * built. With filters, an already-applied filter key reuses the posting list built when matching
   * that chip, while any unapplied candidate key is counted in one cached pass over {@link
   * #current} so typing a value prefix never builds posting lists across dozens of candidate keys.
   */
  int scopeCount(String keyId, String valueLower) {
    if (!hasFilters()) {
      return index.valueCount(keyId, valueLower);
    }
    if (isActiveKey(keyId)) {
      return intersectionCount(postings.get(keyId, valueLower), currentBits);
    }
    return scopedCounts(keyId).countFor(valueLower);
  }

  /**
   * Number of records that adding {@code valueLower} to the existing chip on {@code keyId} would
   * bring into the scope: records that match every other active chip and carry {@code valueLower},
   * but are not in the scope yet.
   *
   * <p>The count cannot be taken within the scope, because the scope is already narrowed by this
   * key's own chip. The positions matching the other chips are evaluated once per key and reused
   * for every value of that key in the request. When this key's chip is the only one, every record
   * matches the other chips and nothing needs evaluating.
   */
  int modifyIncludeDelta(String keyId, String valueLower) {
    int[] posting = postings.get(keyId, valueLower);
    ImmutableList<Filter> otherChips = filtersExcept(keyId);
    if (otherChips.isEmpty()) {
      return posting.length - intersectionCount(posting, currentBits);
    }
    BitSet matchesOtherChips =
        matchesExceptChip.computeIfAbsent(
            keyId, key -> toBitSet(filterEngine.match(corpus, filtersExcept(key))));
    int added = 0;
    for (int position : posting) {
      if (matchesOtherChips.get(position) && !currentBits.get(position)) {
        added++;
      }
    }
    return added;
  }

  /**
   * Number of records in scope carrying any of {@code valuesLower} under {@code keyId}.
   *
   * <p>No posting list is built for a key that carries no chip. Without filters, a key on which no
   * record carries two values (its {@link FleetIndex#keyStats} are not {@code multiValued}) answers
   * from the index as the sum of the value counts; a multi-valued key takes one pass over the
   * corpus. With filters, a key that carries no chip takes one pass over the filtered records, and
   * a chip key reuses the posting lists built when its chip was matched.
   */
  int scopeUnionCount(String keyId, ImmutableList<String> valuesLower) {
    ImmutableSet<String> target = ImmutableSet.copyOf(valuesLower);
    if (target.size() == 1) {
      return scopeCount(keyId, target.iterator().next());
    }
    if (!hasFilters()) {
      if (!index.keyStats(keyId).multiValued()) {
        return target.stream().mapToInt(value -> index.valueCount(keyId, value)).sum();
      }
      int count = 0;
      for (int position = 0; position < corpus.recordCount(); position++) {
        if (carriesAny(position, keyId, target)) {
          count++;
        }
      }
      return count;
    }
    if (!isActiveKey(keyId)) {
      int count = 0;
      for (int position : current) {
        if (carriesAny(position, keyId, target)) {
          count++;
        }
      }
      return count;
    }
    BitSet union = new BitSet();
    for (String value : target) {
      for (int position : postings.get(keyId, value)) {
        union.set(position);
      }
    }
    union.and(currentBits);
    return union.cardinality();
  }

  /**
   * Number of records in scope carrying any value that starts with {@code prefixLower} under {@code
   * keyId}, where {@code matchingValues} is the slice of {@link FleetIndex#sortedValues} with that
   * prefix.
   *
   * <p>Without filters, a key on which no record carries two values (its {@link
   * FleetIndex#keyStats} are not {@code multiValued}) answers from the index as the sum of the
   * value counts across {@code matchingValues}; a multi-valued key takes one pass over the corpus.
   * With filters, one pass over {@link #current} checks {@link String#startsWith} directly without
   * materializing a value set.
   */
  int scopePrefixCount(String keyId, String prefixLower, ImmutableList<String> matchingValues) {
    if (!hasFilters() && !index.keyStats(keyId).multiValued()) {
      return matchingValues.stream().mapToInt(value -> index.valueCount(keyId, value)).sum();
    }
    if (!hasFilters()) {
      int count = 0;
      for (int position = 0; position < corpus.recordCount(); position++) {
        if (carriesPrefix(position, keyId, prefixLower)) {
          count++;
        }
      }
      return count;
    }
    int count = 0;
    for (int position : current) {
      if (carriesPrefix(position, keyId, prefixLower)) {
        count++;
      }
    }
    return count;
  }

  private boolean carriesAny(int position, String keyId, ImmutableSet<String> valuesLower) {
    return corpus.valuesForKey(position, keyId).stream().anyMatch(valuesLower::contains);
  }

  private boolean carriesPrefix(int position, String keyId, String prefixLower) {
    return corpus.valuesForKey(position, keyId).stream()
        .anyMatch(value -> value.startsWith(prefixLower));
  }

  /** Number of records in scope that have at least one value for {@code keyId}. */
  int scopePresenceCount(String keyId) {
    if (!hasFilters()) {
      return globalPresenceCount(keyId);
    }
    return scopedCounts(keyId).presentCount();
  }

  /**
   * Number of records in the whole corpus that have at least one value for {@code keyId}, read from
   * the key statistics {@link FleetIndex#keyStats} precomputes per snapshot.
   */
  int globalPresenceCount(String keyId) {
    return index.keyStats(keyId).entityCount();
  }

  private ScopedKeyCounts scopedCounts(String keyId) {
    return scopedKeyCounts.computeIfAbsent(keyId, this::countKeyInScope);
  }

  private ScopedKeyCounts countKeyInScope(String keyId) {
    Map<String, Integer> valueCounts = new HashMap<>();
    int presentCount = 0;
    for (int position : current) {
      ImmutableSet<String> values = corpus.valuesForKey(position, keyId);
      if (!values.isEmpty()) {
        presentCount++;
        for (String value : values) {
          valueCounts.merge(value, 1, Integer::sum);
        }
      }
    }
    return new ScopedKeyCounts(presentCount, valueCounts);
  }

  /** Counts of one key within {@link #current}; request-scoped, so the map is not copied. */
  private record ScopedKeyCounts(int presentCount, Map<String, Integer> valueCounts) {
    int countFor(String valueLower) {
      return valueCounts.getOrDefault(valueLower, 0);
    }
  }

  static int intersectionCount(int[] posting, BitSet set) {
    int count = 0;
    for (int position : posting) {
      if (set.get(position)) {
        count++;
      }
    }
    return count;
  }

  static BitSet toBitSet(ImmutableList<Integer> positions) {
    BitSet set = new BitSet();
    for (int position : positions) {
      set.set(position);
    }
    return set;
  }
}
