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
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetFilterEngine;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyVocabulary;
import com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionCandidate.Counting;
import com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionCandidate.Kind;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

/**
 * Orders candidate suggestions, fills in their displayed counts, and cuts the list to the result
 * cap.
 *
 * <p>This class answers "of everything the patterns produced, what does the user see and in which
 * order?" It knows nothing about how candidates were generated. Ordering is by curated key priority
 * first, so a core key such as Model outranks a raw dimension whatever the counts, then match
 * quality, personalization, count, and finally main text as a stable tie-break. Personalization is
 * deferred (see {@link #PERSONALIZED_KEYS}), so that tier is currently a no-op.
 *
 * <p>Counts follow the spec's count semantics: an add-filter row shows the resulting total under
 * the current filters; a modify-include row shows the delta it would add, prefixed with "+"; a
 * modify-exclude row shows the resulting total. Without active filters counts come straight from
 * the index; with them, every candidate is counted against the filtered set and value rows whose
 * count is zero are dropped, because the user only wants values that exist in the filtered set.
 *
 * <p><b>Entity purity gate.</b> Before anything else, a candidate whose key the corpus vocabulary
 * does not describe is dropped and logged at WARNING. The generators build candidates from
 * descriptors the vocabulary produced, so this cannot happen by construction; the gate exists so a
 * future regression is loud in logs instead of silently rendering a device key on the Hosts page.
 */
final class SuggestionRanker {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Personalized (recent / frequent) key set. Personalization is deferred, so this is empty and no
   * candidate is ever treated as personalized.
   *
   * <p>TODO: when personalization ships, populate this per request from the user's query and view
   * history, and add recent-condition and recent-key candidates. Until then the ranking's
   * personalization tier is a no-op.
   */
  private static final ImmutableSet<String> PERSONALIZED_KEYS = ImmutableSet.of();

  private final FleetFilterEngine filterEngine;

  SuggestionRanker(FleetFilterEngine filterEngine) {
    this.filterEngine = filterEngine;
  }

  /** Ranks filter suggestions (conditions and key affordances). */
  FleetSuggestionResponse rank(
      SuggestionContext context, List<SuggestionCandidate> raw, int limit) {
    ImmutableList<SuggestionCandidate> admitted = admit(context, raw);
    FleetSuggestionResponse.Builder response = FleetSuggestionResponse.newBuilder();

    if (!context.hasFilters()) {
      // Fleet-wide counts are already known, so order first and count only what is shown.
      List<SuggestionCandidate> ordered =
          admitted.stream().sorted(order(context, SuggestionCandidate::rankCount)).toList();
      for (SuggestionCandidate candidate : ordered.subList(0, Math.min(ordered.size(), limit))) {
        assignCount(context, candidate);
        response.addItems(candidate.build());
      }
      return response.build();
    }

    // Under active filters the filtered count decides the order, so count everything first.
    admitted.forEach(candidate -> assignCount(context, candidate));
    List<SuggestionCandidate> ordered =
        admitted.stream()
            .sorted(order(context, candidate -> candidate.count().orElse(0)))
            .filter(candidate -> !isDeadEndUnderFilters(candidate))
            .limit(limit)
            .toList();
    ordered.forEach(candidate -> response.addItems(candidate.build()));
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
                        candidate -> -vocabulary.priority(candidate.key()))
                    .thenComparingInt(SuggestionCandidate::groupRank)
                    .thenComparing(SuggestionCandidate::overMax, falseFirst())
                    .thenComparingInt(candidate -> candidate.count().orElse(0)))
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
    Set<String> seen = new HashSet<>();
    return raw.stream()
        .filter(candidate -> passesPurityGate(vocabulary, candidate))
        .filter(candidate -> seen.add(candidate.label() + '\u0000' + candidate.mainText()))
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

  private static Comparator<SuggestionCandidate> order(
      SuggestionContext context, ToIntFunction<SuggestionCandidate> count) {
    KeyVocabulary vocabulary = context.vocabulary();
    return Comparator.<SuggestionCandidate>comparingInt(
            candidate -> -vocabulary.priority(candidate.key()))
        .thenComparingInt(candidate -> -(int) Math.round(candidate.tier()))
        .thenComparingInt(candidate -> PERSONALIZED_KEYS.contains(candidate.key().id()) ? -1 : 0)
        .thenComparingInt(candidate -> -count.applyAsInt(candidate))
        .thenComparing(SuggestionCandidate::mainText);
  }

  /** A countable condition that matches nothing in the filtered set is a dead end. */
  private static boolean isDeadEndUnderFilters(SuggestionCandidate candidate) {
    return candidate.kind() == Kind.CONDITION
        && candidate.counting() != Counting.NONE
        && candidate.count().isEmpty();
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
