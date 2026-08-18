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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ComplexMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ContainsSubstring;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesAtLeast;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesExactly;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesRegex;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.StartsWith;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.Postings;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.inject.Inject;

/**
 * Resolves a list of proto {@link Filter}s against a {@link SearchCorpus} into the set of matching
 * record indices, using the corpus's inverted index.
 *
 * <p>This is the Java port of the search prototype's {@code filter_devices} and {@code _chip_set}.
 * Filters are AND'd together: a record matches only if it satisfies every filter. Within a single
 * simple filter the listed values are OR'd. An empty filter list matches every record.
 *
 * <p>The index stores all values lowercased, so every value the caller supplies is lowercased
 * before lookup and regular expressions are compiled case-insensitively. Posting lists hold record
 * indices into the corpus in ascending order; the returned list is likewise ascending.
 */
public final class FleetFilterEngine {

  @Inject
  FleetFilterEngine() {}

  /**
   * Returns the record indices matching all of the given filters, in ascending order.
   *
   * <p>Indices point into the corpus records. An empty filter list returns every record index (0 to
   * recordCount - 1).
   */
  public ImmutableList<Integer> match(SearchCorpus corpus, List<Filter> filters) {
    int recordCount = corpus.recordCount();
    BitSet result = null;
    for (Filter filter : filters) {
      BitSet filterSet = matchFilter(corpus, filter);
      if (result == null) {
        result = filterSet;
      } else {
        result.and(filterSet);
      }
    }
    if (result == null) {
      result = allRecords(recordCount);
    }
    return toSortedList(result);
  }

  /** Record-index set for one filter. A filter with no mode set imposes no constraint. */
  private static BitSet matchFilter(SearchCorpus corpus, Filter filter) {
    String keyId = filter.getKey();
    return switch (filter.getModeCase()) {
      case SIMPLE -> matchSimple(corpus, keyId, filter.getSimple());
      case COMPLEX -> matchComplex(corpus, keyId, filter.getComplex());
      case MODE_NOT_SET -> allRecords(corpus.recordCount());
    };
  }

  /**
   * Simple match: the union of the listed values (OR). A {@code no_value} entry contributes the
   * records that lack the key entirely. When {@code negated} is set the whole result is inverted.
   */
  private static BitSet matchSimple(SearchCorpus corpus, String keyId, SimpleMatch simple) {
    Postings postings = corpus.postings();
    BitSet include = new BitSet();
    for (FilterValue value : simple.getValuesList()) {
      switch (value.getKindCase()) {
        case VALUE -> orInto(include, postings.get(keyId, Ascii.toLowerCase(value.getValue())));
        case NO_VALUE -> include.or(noValueSet(corpus, keyId));
        case KIND_NOT_SET -> {}
      }
    }
    return negateIfNeeded(include, simple.getNegated(), corpus.recordCount());
  }

  /** Complex match: exactly one advanced mode. */
  private static BitSet matchComplex(SearchCorpus corpus, String keyId, ComplexMatch complex) {
    return switch (complex.getKindCase()) {
      case STARTS_WITH -> matchStartsWith(corpus, keyId, complex.getStartsWith());
      case CONTAINS_SUBSTRING -> matchContains(corpus, keyId, complex.getContainsSubstring());
      case MATCHES_REGEX -> matchRegex(corpus, keyId, complex.getMatchesRegex());
      case MATCHES_EXACTLY -> matchExactly(corpus, keyId, complex.getMatchesExactly());
      case MATCHES_AT_LEAST -> matchAtLeast(corpus, keyId, complex.getMatchesAtLeast());
      case KIND_NOT_SET -> allRecords(corpus.recordCount());
    };
  }

  /**
   * Prefix match over the key's sorted distinct values. Locates the contiguous run of values that
   * begin with the prefix via two binary searches, then unions their posting lists. Not negatable.
   */
  private static BitSet matchStartsWith(SearchCorpus corpus, String keyId, StartsWith startsWith) {
    FleetIndex index = corpus.index();
    Postings postings = corpus.postings();
    ImmutableList<String> sorted = index.sortedValues(keyId);
    String prefix = Ascii.toLowerCase(startsWith.getValue());
    int lo = lowerBound(sorted, prefix);
    // '\uffff' is the largest basic-plane code unit, so prefix + '\uffff' bounds the prefix run.
    int hi = lowerBound(sorted, prefix + '\uffff');
    BitSet result = new BitSet();
    for (int i = lo; i < hi; i++) {
      orInto(result, postings.get(keyId, sorted.get(i)));
    }
    return result;
  }

  /** Substring match: scans distinct values for the needle and unions their postings. Negatable. */
  private static BitSet matchContains(
      SearchCorpus corpus, String keyId, ContainsSubstring contains) {
    FleetIndex index = corpus.index();
    Postings postings = corpus.postings();
    String needle = Ascii.toLowerCase(contains.getValue());
    BitSet matched = new BitSet();
    for (String value : index.sortedValues(keyId)) {
      if (value.contains(needle)) {
        orInto(matched, postings.get(keyId, value));
      }
    }
    return negateIfNeeded(matched, contains.getNegated(), corpus.recordCount());
  }

  /**
   * Regular expression match: scans distinct values with a case-insensitive unanchored search and
   * unions their postings. Negatable. An invalid pattern matches nothing (so a negated invalid
   * pattern matches everything), mirroring the prototype.
   */
  private static BitSet matchRegex(SearchCorpus corpus, String keyId, MatchesRegex regex) {
    FleetIndex index = corpus.index();
    Postings postings = corpus.postings();
    Pattern pattern;
    try {
      pattern = Pattern.compile(regex.getValue(), Pattern.CASE_INSENSITIVE);
    } catch (PatternSyntaxException pse) {
      return negateIfNeeded(new BitSet(), regex.getNegated(), corpus.recordCount());
    }
    BitSet matched = new BitSet();
    for (String value : index.sortedValues(keyId)) {
      if (pattern.matcher(value).find()) {
        orInto(matched, postings.get(keyId, value));
      }
    }
    return negateIfNeeded(matched, regex.getNegated(), corpus.recordCount());
  }

  /**
   * Exact set match: the record's value set for the key equals the given set. Scans the forward
   * store, keeping only records whose full value set equals the wanted set. Not negatable.
   */
  private static BitSet matchExactly(SearchCorpus corpus, String keyId, MatchesExactly exactly) {
    // Exact set matching scans the forward store, so it does not need posting lists.
    ImmutableList<String> wanted = lowercased(exactly.getValuesList());
    if (wanted.isEmpty()) {
      return new BitSet();
    }
    Set<String> want = new HashSet<>(wanted);
    BitSet result = new BitSet();
    int recordCount = corpus.recordCount();
    for (int i = 0; i < recordCount; i++) {
      if (corpus.valuesForKey(i, keyId).equals(want)) {
        result.set(i);
      }
    }
    return result;
  }

  /**
   * Superset match: the record's value set for the key contains all the given values. Equivalent to
   * intersecting the postings of the wanted values. Not negatable.
   */
  private static BitSet matchAtLeast(SearchCorpus corpus, String keyId, MatchesAtLeast atLeast) {
    return intersectPostings(corpus.postings(), keyId, lowercased(atLeast.getValuesList()));
  }

  /** Records that lack the key entirely: all records minus those carrying any value for it. */
  private static BitSet noValueSet(SearchCorpus corpus, String keyId) {
    BitSet result = allRecords(corpus.recordCount());
    result.andNot(recordsWithKey(corpus.postings(), keyId));
    return result;
  }

  /** Union of every posting list for the key: the records that carry at least one value for it. */
  private static BitSet recordsWithKey(Postings postings, String keyId) {
    BitSet withKey = new BitSet();
    for (int[] posting : postings.forKey(keyId).values()) {
      orInto(withKey, posting);
    }
    return withKey;
  }

  /**
   * Intersection (AND) of the posting lists of the given values. Empty values yield an empty set.
   */
  private static BitSet intersectPostings(Postings postings, String keyId, List<String> values) {
    BitSet clause = null;
    for (String value : values) {
      BitSet posting = new BitSet();
      orInto(posting, postings.get(keyId, value));
      if (clause == null) {
        clause = posting;
      } else {
        clause.and(posting);
      }
    }
    return clause == null ? new BitSet() : clause;
  }

  private static ImmutableList<String> lowercased(List<String> values) {
    ImmutableList.Builder<String> result = ImmutableList.builder();
    for (String value : values) {
      result.add(Ascii.toLowerCase(value));
    }
    return result.build();
  }

  /** Inverts the set over the record space when negated, otherwise returns it unchanged. */
  private static BitSet negateIfNeeded(BitSet matched, boolean negated, int recordCount) {
    if (!negated) {
      return matched;
    }
    BitSet all = allRecords(recordCount);
    all.andNot(matched);
    return all;
  }

  private static BitSet allRecords(int recordCount) {
    BitSet all = new BitSet(recordCount);
    all.set(0, recordCount);
    return all;
  }

  private static void orInto(BitSet target, int[] posting) {
    for (int index : posting) {
      target.set(index);
    }
  }

  private static ImmutableList<Integer> toSortedList(BitSet set) {
    ImmutableList.Builder<Integer> result = ImmutableList.builder();
    for (int i = set.nextSetBit(0); i >= 0; i = set.nextSetBit(i + 1)) {
      result.add(i);
    }
    return result.build();
  }

  /**
   * Locates the first index in the sorted list whose value is greater than or equal to the key. The
   * list must be sorted ascending, matching {@link FleetIndex#sortedValues(String)}.
   */
  static int lowerBound(List<String> sorted, String key) {
    int lo = 0;
    int hi = sorted.size();
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (sorted.get(mid).compareTo(key) < 0) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    return lo;
  }
}
