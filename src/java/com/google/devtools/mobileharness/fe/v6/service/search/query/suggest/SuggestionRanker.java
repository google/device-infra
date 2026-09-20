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
import static com.google.common.primitives.Booleans.falseFirst;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetFilterEngine;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyVocabulary;
import com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionCandidate.Counting;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Orders candidate suggestions, fills in their displayed counts, and cuts the list to the result
 * cap.
 *
 * <p>This class answers "of everything the patterns produced, what does the user see and in which
 * order?" It knows nothing about how candidates were generated. Ordering is by curated key priority
 * first, so a core key such as Model outranks a raw dimension whatever the counts, then match
 * quality, count, and finally main text as a stable tie-break.
 *
 * <p>Counts follow the spec's count semantics: an add-filter row shows the resulting total under
 * the current filters; a modify-include row shows the delta it would add, prefixed with "+"; a
 * modify-exclude row shows the resulting total. Value rows whose count in the current scope is zero
 * are dropped, because the user only wants values that exist in the active scope.
 *
 * <p><b>Entity purity gate.</b> Before anything else, a candidate whose key the corpus vocabulary
 * does not describe is dropped and logged at WARNING. The generators build candidates from
 * descriptors the vocabulary produced, so this cannot happen by construction; the gate exists so a
 * future regression is loud in logs instead of silently rendering a device key on the Hosts page.
 */
final class SuggestionRanker {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final FleetFilterEngine filterEngine;

  SuggestionRanker(FleetFilterEngine filterEngine) {
    this.filterEngine = filterEngine;
  }

  /** Ranks filter suggestions (conditions and key affordances). */
  FleetSuggestionResponse rank(
      SuggestionContext context, List<SuggestionCandidate> raw, int limit) {
    ImmutableList<SuggestionCandidate> admitted = admit(context, raw);
    admitted.forEach(candidate -> assignCount(context, candidate));
    FleetSuggestionResponse.Builder response = FleetSuggestionResponse.newBuilder();
    admitted.stream()
        .sorted(order(context))
        .filter(candidate -> !isDeadEndUnderFilters(candidate))
        .limit(limit)
        .forEach(candidate -> response.addItems(candidate.build()));
    return response.build();
  }

  /** Ranks group-by suggestions: priority, then name match quality, then usable groupings first. */
  FleetSuggestionResponse rankGroupBys(
      SuggestionContext context, List<SuggestionCandidate> raw, int limit) {
    KeyVocabulary vocabulary = context.vocabulary();
    List<SuggestionCandidate> ordered =
        admit(context, raw).stream()
            .sorted(
                Comparator.<SuggestionCandidate>comparingInt(
                        candidate -> vocabulary.priority(candidate.key()))
                    .reversed()
                    .thenComparingInt(SuggestionCandidate::groupRank)
                    .thenComparing(SuggestionCandidate::overMax, falseFirst())
                    .thenComparingInt(SuggestionCandidate::rankCount)
                    .thenComparing(SuggestionCandidate::mainText))
            .limit(limit)
            .toList();
    FleetSuggestionResponse.Builder response = FleetSuggestionResponse.newBuilder();
    ordered.forEach(candidate -> response.addItems(candidate.build()));
    return response.build();
  }

  /** Applies the purity gate, then removes duplicates by (label, main text), keeping the first. */
  private static ImmutableList<SuggestionCandidate> admit(
      SuggestionContext context, List<SuggestionCandidate> raw) {
    KeyVocabulary vocabulary = context.vocabulary();
    Set<ImmutableList<String>> seen = new HashSet<>();
    return raw.stream()
        .filter(candidate -> passesPurityGate(vocabulary, candidate))
        .filter(candidate -> seen.add(ImmutableList.of(candidate.label(), candidate.mainText())))
        .collect(toImmutableList());
  }

  private static boolean passesPurityGate(KeyVocabulary vocabulary, SuggestionCandidate candidate) {
    String keyId = candidate.key().id();
    if (vocabulary.knows(keyId)) {
      return true;
    }
    logger.atWarning().log(
        "Dropping %s suggestion for key %s: unknown to %s search",
        candidate.kind(), keyId, vocabulary.entity());
    return false;
  }

  private static Comparator<SuggestionCandidate> order(SuggestionContext context) {
    KeyVocabulary vocabulary = context.vocabulary();
    return Comparator.<SuggestionCandidate>comparingInt(
            candidate -> vocabulary.priority(candidate.key()))
        .reversed()
        .thenComparing(
            Comparator.<SuggestionCandidate>comparingInt(
                    candidate -> (int) Math.round(candidate.tier()))
                .reversed())
        .thenComparing(Comparator.comparingInt(SuggestionCandidate::rankCount).reversed())
        .thenComparing(SuggestionCandidate::mainText);
  }

  /** A deferred condition that matches nothing in the filtered set is a dead end. */
  private static boolean isDeadEndUnderFilters(SuggestionCandidate candidate) {
    return candidate.counting() == Counting.DEFERRED && candidate.count().isEmpty();
  }

  /**
   * Computes the displayed count of a deferred candidate; other candidates already carry theirs.
   */
  private void assignCount(SuggestionContext context, SuggestionCandidate candidate) {
    if (candidate.counting() != Counting.DEFERRED) {
      return;
    }
    String keyId = candidate.key().id();
    String value = candidate.valueLower();

    if (candidate.inChip() && !candidate.exclude()) {
      // Modify-include: the delta is how many records match every other chip and this value but
      // are not already in the result. Counting within the current set would under-count, because
      // the current set is already narrowed by this key's own chip.
      BitSet withoutThisKey =
          SuggestionContext.toBitSet(
              filterEngine.match(context.corpus(), context.filtersExcept(keyId)));
      BitSet current = context.currentBits();
      int added = 0;
      for (int position : context.postings().get(keyId, value)) {
        if (withoutThisKey.get(position) && !current.get(position)) {
          added++;
        }
      }
      candidate.assignCount(positiveOrNull(added), "+");
      return;
    }

    int matches = context.scopeCount(keyId, value);
    int shown = candidate.exclude() ? context.scopeSize() - matches : matches;
    candidate.assignCount(positiveOrNull(shown), "");
  }

  @Nullable
  private static Integer positiveOrNull(int count) {
    return count > 0 ? count : null;
  }
}
