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
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyVocabulary;
import com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionCandidate.Counting;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Orders candidate suggestions, fills in their displayed counts, and cuts the list to the result
 * cap.
 *
 * <p>This class answers "of everything the patterns produced, what does the user see and in which
 * order?" It knows nothing about how candidates were generated. Filter suggestions are ordered by
 * curated key priority first, so a core key such as Model outranks a raw dimension whatever the
 * counts, then match quality, count, and finally main text as a stable tie-break; group-by
 * suggestions are ordered by {@link #rankGroupBys}.
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

  /**
   * At most {@code limit * FILTERED_KEY_BUDGET_MULTIPLIER} distinct keys are scanned per request.
   * In {@link #rank} under active filters, counting an unapplied key costs one pass over the
   * filtered set, after which every value of that key is a cached lookup; {@code
   * FleetSuggester.suggestValueList} and {@code FleetSuggester.groupByCandidates} apply the same
   * multiplier when bounding keys that scan records. The budget is best effort: keys beyond it are
   * skipped (or offered uncounted in {@code group by}).
   */
  static final int FILTERED_KEY_BUDGET_MULTIPLIER = 3;

  /**
   * Ranks filter suggestions (conditions and key affordances) and returns at most {@code limit}.
   *
   * <p>Counting every admitted candidate when only {@code limit} rows are shown would waste passes
   * over the filtered scope across dozens of candidate keys. Instead, candidates are ordered best
   * first by an {@link EvaluationKey} that combines curated key priority, match tier, {@link
   * #upperBoundCount}, and the row's full-corpus count (so two candidates whose upper bounds are
   * both clamped to a small filtered scope size still keep their relative fleet-wide frequency
   * order). The best {@code limit} counted candidates are kept in a heap, and the walk stops as
   * soon as the next candidate's upper bound cannot beat the worst kept candidate. Under active
   * filters, where many global values may have zero matches in the filtered set and therefore not
   * fill the heap early, at most {@code limit * FILTERED_KEY_BUDGET_MULTIPLIER} distinct keys are
   * counted.
   */
  FleetSuggestionResponse rank(
      SuggestionContext context, List<SuggestionCandidate> raw, int limit) {
    if (limit <= 0) {
      // No row is requested, and PriorityQueue rejects a zero initial capacity.
      return FleetSuggestionResponse.getDefaultInstance();
    }
    KeyVocabulary vocabulary = context.vocabulary();
    Comparator<SuggestionCandidate> finalOrder =
        Comparator.comparing(candidate -> finalKey(vocabulary, candidate), RankKey.BEST_FIRST);
    ImmutableList<SuggestionCandidate> byUpperBound =
        ImmutableList.sortedCopyOf(
            Comparator.comparing(
                candidate -> evaluationKey(context, candidate), EvaluationKey.BEST_FIRST),
            admit(context, raw));

    // Reversed order, so the worst kept candidate sits at peek().
    PriorityQueue<SuggestionCandidate> top = new PriorityQueue<>(limit, finalOrder.reversed());
    int maxFilteredKeys = limit * FILTERED_KEY_BUDGET_MULTIPLIER;
    Set<String> countedKeys = new HashSet<>();
    for (SuggestionCandidate candidate : byUpperBound) {
      if (top.size() == limit && cannotBeat(context, candidate, requireNonNull(top.peek()))) {
        break;
      }
      if (candidate.counting() == Counting.DEFERRED) {
        String keyId = candidate.key().id();
        if (context.hasFilters()
            && !countedKeys.contains(keyId)
            && countedKeys.size() >= maxFilteredKeys) {
          continue;
        }
        countedKeys.add(keyId);
        assignCount(context, candidate);
        if (isDeadEndUnderFilters(candidate)) {
          continue;
        }
      }
      if (top.size() < limit) {
        top.offer(candidate);
      } else if (finalOrder.compare(candidate, requireNonNull(top.peek())) < 0) {
        top.poll();
        top.offer(candidate);
      }
    }

    FleetSuggestionResponse.Builder response = FleetSuggestionResponse.newBuilder();
    ImmutableList.sortedCopyOf(finalOrder, top)
        .forEach(candidate -> response.addItems(candidate.build()));
    return response.build();
  }

  /**
   * Ranks group-by suggestions: how well the key name matches the typed term first, so the key the
   * user named leads even when it makes one group; then how useful the grouping is ({@link
   * GroupingUse}), so a grouping that splits the scope is never outranked by one that does not;
   * then curated key priority, fewer groups, and main text.
   */
  FleetSuggestionResponse rankGroupBys(
      SuggestionContext context, List<SuggestionCandidate> raw, int limit) {
    KeyVocabulary vocabulary = context.vocabulary();
    List<SuggestionCandidate> ordered =
        admit(context, raw).stream()
            .sorted(
                Comparator.comparing(SuggestionCandidate::tier)
                    .thenComparing(GroupingUse::of)
                    .thenComparing(
                        Comparator.<SuggestionCandidate>comparingInt(
                                candidate -> vocabulary.priority(candidate.key()))
                            .reversed())
                    .thenComparingInt(SuggestionCandidate::rankCount)
                    .thenComparing(SuggestionCandidate::mainText))
            .limit(limit)
            .toList();
    FleetSuggestionResponse.Builder response = FleetSuggestionResponse.newBuilder();
    ordered.forEach(candidate -> response.addItems(candidate.build()));
    return response.build();
  }

  /** How useful a group-by row is for splitting the current scope, most useful first. */
  private enum GroupingUse {
    /** Splits the scope into at least two groups without exceeding the over-max threshold. */
    USABLE,
    /** Not counted in this request, so whether it splits the scope is unknown. */
    UNCOUNTED,
    /** Splits the scope into more groups than a grouped view can usefully show. */
    OVER_MAX,
    /** Produces a single group, which only repeats the ungrouped list. */
    SINGLE_GROUP;

    static GroupingUse of(SuggestionCandidate candidate) {
      if (candidate.counting() == Counting.NONE) {
        return UNCOUNTED;
      }
      if (candidate.overMax()) {
        return OVER_MAX;
      }
      return candidate.rankCount() > 1 ? USABLE : SINGLE_GROUP;
    }
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

  /**
   * The fields a filter row is ordered by, best first: curated key priority (higher first), match
   * tier (stronger first), count (higher first), and main text as a stable tie-break.
   */
  private record RankKey(int priority, MatchTier tier, int count, String mainText) {
    static final Comparator<RankKey> BEST_FIRST =
        Comparator.comparingInt(RankKey::priority)
            .reversed()
            .thenComparing(RankKey::tier)
            .thenComparing(Comparator.comparingInt(RankKey::count).reversed())
            .thenComparing(RankKey::mainText);
  }

  /**
   * Ordering key for visiting candidates before their filtered counts are computed: curated key
   * priority, match tier, {@link #upperBoundCount}, {@link #fullCorpusCount} (preserving relative
   * fleet-wide frequency when {@link #upperBoundCount} is clamped to a small filtered scope size),
   * and main text.
   */
  private record EvaluationKey(
      int priority, MatchTier tier, int upperBoundCount, int fullCorpusCount, String mainText) {
    static final Comparator<EvaluationKey> BEST_FIRST =
        Comparator.comparingInt(EvaluationKey::priority)
            .reversed()
            .thenComparing(EvaluationKey::tier)
            .thenComparing(Comparator.comparingInt(EvaluationKey::upperBoundCount).reversed())
            .thenComparing(Comparator.comparingInt(EvaluationKey::fullCorpusCount).reversed())
            .thenComparing(EvaluationKey::mainText);
  }

  /** The candidate's rank key with its current count; final once the candidate is counted. */
  private static RankKey finalKey(KeyVocabulary vocabulary, SuggestionCandidate candidate) {
    return new RankKey(
        vocabulary.priority(candidate.key()),
        candidate.tier(),
        candidate.rankCount(),
        candidate.mainText());
  }

  /** The candidate's pre-evaluation ordering key. */
  private static EvaluationKey evaluationKey(
      SuggestionContext context, SuggestionCandidate candidate) {
    return new EvaluationKey(
        context.vocabulary().priority(candidate.key()),
        candidate.tier(),
        upperBoundCount(context, candidate),
        fullCorpusCount(context, candidate),
        candidate.mainText());
  }

  /**
   * Whether neither {@code candidate} nor any candidate ordered after it by {@link EvaluationKey}
   * can beat {@code worstKept}.
   *
   * <p>Every kept candidate was visited before {@code candidate}, so {@code candidate}'s key
   * priority is never higher than {@code worstKept}'s and, at the same priority, its tier is never
   * stronger; a different priority or tier is therefore a worse one. At the same priority and tier,
   * {@code upperBoundCount} is non-increasing along {@link EvaluationKey#BEST_FIRST} and a final
   * count never exceeds its upper bound, so an upper bound below {@code worstKept}'s count ends the
   * walk. An upper bound equal to it does not: a row with that count wins when its main text sorts
   * first.
   */
  private static boolean cannotBeat(
      SuggestionContext context, SuggestionCandidate candidate, SuggestionCandidate worstKept) {
    KeyVocabulary vocabulary = context.vocabulary();
    if (vocabulary.priority(candidate.key()) != vocabulary.priority(worstKept.key())
        || candidate.tier() != worstKept.tier()) {
      return true;
    }
    return upperBoundCount(context, candidate) < worstKept.rankCount();
  }

  /**
   * The count the row would show over the whole corpus, known without touching posting lists. Valid
   * only before the candidate is counted, while a value row's {@link
   * SuggestionCandidate#rankCount()} is the value's fleet-wide count: an exclude row shows the
   * records without the value, every other row its {@link SuggestionCandidate#rankCount()}.
   */
  private static int fullCorpusCount(SuggestionContext context, SuggestionCandidate candidate) {
    return candidate.exclude()
        ? context.corpus().recordCount() - candidate.rankCount()
        : candidate.rankCount();
  }

  /**
   * An upper bound of the count {@link #assignCount} can give the candidate, known without touching
   * posting lists. Valid only before the candidate is counted.
   *
   * <ul>
   *   <li>A modify-include row (an include value added to an existing include chip) shows records
   *       carrying the value that are outside the scope, at most the value's fleet-wide count,
   *       which may exceed the scope size.
   *   <li>Every other row is bounded by both its {@link #fullCorpusCount} and the scope size: an
   *       add-include row shows records in scope carrying the value, an exclude row shows records
   *       in scope without it, and a row that is not deferred already carries its final count,
   *       which never exceeds the scope.
   * </ul>
   *
   * <p>Without filters the scope is the whole corpus, so every bound except a modify-include row's
   * (which needs a chip, and so a filter) is the exact count.
   */
  private static int upperBoundCount(SuggestionContext context, SuggestionCandidate candidate) {
    if (candidate.inChip() && !candidate.exclude()) {
      return candidate.rankCount();
    }
    return Math.min(fullCorpusCount(context, candidate), context.scopeSize());
  }

  /** A deferred condition that matches nothing in the filtered set is a dead end. */
  private static boolean isDeadEndUnderFilters(SuggestionCandidate candidate) {
    return candidate.counting() == Counting.DEFERRED && candidate.count().isEmpty();
  }

  /**
   * Computes the displayed count of a deferred candidate; other candidates already carry theirs.
   */
  private static void assignCount(SuggestionContext context, SuggestionCandidate candidate) {
    if (candidate.counting() != Counting.DEFERRED) {
      return;
    }
    String keyId = candidate.key().id();
    String value = candidate.valueLower();

    if (candidate.inChip() && !candidate.exclude()) {
      // Modify-include shows the delta the value would add to the result, not a total.
      int added = context.modifyIncludeDelta(keyId, value);
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
