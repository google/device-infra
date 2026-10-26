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
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionActions.appendNoValue;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionActions.appendValues;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionActions.applyFilter;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionActions.noValueFilter;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionActions.openPicker;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionActions.startsWithFilter;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionActions.valueFilter;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionText.conditionText;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionText.displayValue;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionText.label;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionText.modifyConditionText;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionText.modifyPrefix;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionText.segments;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionText.text;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionText.verb;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestion;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TextSegment;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.KeyCount;
import com.google.devtools.mobileharness.fe.v6.service.search.index.ValueKeyPair;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetFilterEngine;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCounts;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyTokens;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyVocabulary;
import com.google.devtools.mobileharness.fe.v6.service.search.query.SearchCorpus;
import com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionIntentParser.Intent;
import com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionIntentParser.IntentPattern;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * Turns a partial search-bar string into a ranked list of {@link FleetSuggestion}s for one search
 * entity.
 *
 * <p>The pipeline is: {@link SuggestionIntentParser} classifies the text into one shape; the
 * matching {@code suggest...} method here turns that shape into raw {@link SuggestionCandidate}s;
 * {@link SuggestionRanker} orders them, fills in counts and cuts to the limit. This class owns only
 * the middle step: given a shape and a corpus, which conditions, keys or groupings are worth
 * offering.
 *
 * <p><b>Entity separation.</b> Every question about keys (does this token name a key, which keys
 * exist, how is a key displayed, what is its curated rank) goes through the corpus's {@link
 * KeyVocabulary}, and keys travel as {@link KeyDescriptor}s the vocabulary produced. This class
 * never inspects a key id prefix and never branches on the corpus type, so device and host search
 * share one algorithm while the vocabulary guarantees that a device dimension can never appear in a
 * host suggestion. A key id string appears only where the index or a proto is addressed.
 *
 * <p>Shapes and their generators (spec pattern numbers in parentheses):
 *
 * <ul>
 *   <li>group-by prefix (0): {@link #groupByCandidates};
 *   <li>emptiness {@code no <key>}, {@code <key> is not empty} (5): {@link #suggestEmptiness};
 *   <li>{@code <key> is <value>} and its negation (1, 6): {@link #suggestKeyValue};
 *   <li>{@code <key> is} with no value yet (1): {@link #suggestTopValuesOfKey};
 *   <li>{@code not <value>} (7) and a bare value (4): {@link #suggestValues};
 *   <li>comma-separated values (2): {@link #suggestValueList};
 *   <li>a single token as a key name (3) and, from three characters, as a value (4): {@link
 *       #suggestSingleToken}, which also closes the list with a group-by row for a matched key.
 * </ul>
 *
 * <p>An identifier typed on its own resolves through the value path: a device UUID matches the UUID
 * key via the value index, so no dedicated identifier detector exists.
 */
public final class FleetSuggester {

  /** Above this bucket count a suggested grouping is flagged {@code over_max}. */
  private static final int GROUP_SUGGEST_MAX = 50;

  /** At most this many group-by keys may already be applied for more to be suggested. */
  private static final int MAX_APPLIED_GROUP_BYS = 3;

  private static final int DEFAULT_LIMIT = 12;

  /**
   * Largest number of suggestions one request may ask for. The search bar shows about a dozen rows;
   * the cap keeps a client-supplied limit from sizing ranking buffers or evaluation budgets.
   */
  private static final int MAX_LIMIT = 50;

  /**
   * Value search over all keys starts at this input length; shorter inputs only match key names.
   */
  private static final int MIN_VALUE_SEARCH_LENGTH = 3;

  /** Number of strongest matched keys for which key-name matches offer ready-to-apply values. */
  private static final int TOP_VALUE_KEYS = 5;

  private static final int TOP_VALUES_PER_KEY = 3;
  private static final int KEY_VALUES_PER_KEY = 8;

  /** Cap on prefix-scan entries of the global value index, to keep short inputs bounded. */
  private static final int MAX_SEMANTIC_SCAN = 500;

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private final FleetFilterEngine filterEngine;
  private final SuggestionRanker ranker;

  @Inject
  FleetSuggester(FleetFilterEngine filterEngine) {
    this.filterEngine = filterEngine;
    this.ranker = new SuggestionRanker();
  }

  /** Returns ranked suggestions for the request against the given corpus. */
  public FleetSuggestionResponse suggest(SearchCorpus corpus, FleetSuggestionRequest request) {
    int limit = request.getLimit() > 0 ? Math.min(request.getLimit(), MAX_LIMIT) : DEFAULT_LIMIT;
    String query = WHITESPACE.matcher(request.getInput()).replaceAll(" ").trim();
    SuggestionContext context =
        SuggestionContext.create(corpus, request.getFiltersList(), filterEngine);

    if (query.isEmpty()) {
      return suggestSingleToken(context, "", request.getGroupByList(), limit);
    }

    // The group-by prefix owns its input: a term matching no key yields nothing rather than falling
    // through to a value search for the literal word "group".
    Optional<String> groupByTerm = SuggestionIntentParser.groupByTerm(query);
    if (groupByTerm.isPresent()) {
      return ranker.rankGroupBys(
          context,
          groupByCandidates(context, groupByTerm.get(), request.getGroupByList(), limit),
          limit);
    }

    Intent intent = SuggestionIntentParser.parse(query);
    if (intent.pattern() == IntentPattern.SINGLE_TOKEN) {
      return suggestSingleToken(context, query, request.getGroupByList(), limit);
    }
    return ranker.rank(context, conditionCandidates(context, intent, limit), limit);
  }

  /**
   * Raw candidates for every shape in which the user spelled out a key, a value, or an operator.
   */
  private ImmutableList<SuggestionCandidate> conditionCandidates(
      SuggestionContext context, Intent intent, int limit) {
    return switch (intent.pattern()) {
      case KEY_VALUE ->
          suggestKeyValue(context, intent.key(), intent.value(), /* exclude= */ false);
      case KEY_VALUE_NEGATED ->
          suggestKeyValue(context, intent.key(), intent.value(), /* exclude= */ true);
      case EMPTINESS -> suggestEmptiness(context, intent.key(), intent.isEmpty());
      case KEY_WITH_OPERATOR -> suggestTopValuesOfKey(context, intent.key(), intent.exclude());
      case NEGATED_VALUE -> suggestValues(context, intent.value(), /* exclude= */ true);
      case VALUE_LIST -> suggestValueList(context, intent.values(), /* exclude= */ false, limit);
      case SINGLE_TOKEN ->
          throw new IllegalArgumentException("a single token is handled by suggestSingleToken");
    };
  }

  // ---- a single token ----

  /**
   * A single token is tried as a key name at any length and, from {@link #MIN_VALUE_SEARCH_LENGTH}
   * characters, as a value, so very short inputs are not flooded with value matches. When the token
   * names a key, the list closes with one {@code Group by <Key>} row for the best matched key that
   * can be grouped, so a user who typed a key can pivot to grouping without spelling out {@code
   * group by}. That row takes one slot of the limit and is always last; it is subject to the same
   * eligibility as the {@code group by} prefix.
   */
  private FleetSuggestionResponse suggestSingleToken(
      SuggestionContext context, String token, List<String> appliedGroupBys, int limit) {
    ImmutableList<KeyMatch> keyMatches = matchKeyNames(context, token);
    ImmutableList.Builder<SuggestionCandidate> raw = ImmutableList.builder();
    raw.addAll(suggestKeyNames(context, keyMatches));
    if (token.length() >= MIN_VALUE_SEARCH_LENGTH) {
      raw.addAll(suggestValues(context, token, /* exclude= */ false));
    }

    FleetSuggestionResponse groupByRow =
        ranker.rankGroupBys(
            context, groupByForMatchedKey(context, keyMatches, appliedGroupBys), /* limit= */ 1);
    if (groupByRow.getItemsCount() == 0) {
      return ranker.rank(context, raw.build(), limit);
    }
    return ranker.rank(context, raw.build(), Math.max(limit - 1, 0)).toBuilder()
        .addAllItems(groupByRow.getItemsList())
        .build();
  }

  /**
   * The group-by row for the best matched key that can be grouped in this request, as a list of at
   * most one candidate. Matches are tried best first, so an exact key wins over a prefix match.
   */
  private static ImmutableList<SuggestionCandidate> groupByForMatchedKey(
      SuggestionContext context, ImmutableList<KeyMatch> keyMatches, List<String> appliedGroupBys) {
    if (appliedGroupBys.size() >= MAX_APPLIED_GROUP_BYS
        || (context.hasFilters() && context.current().isEmpty())) {
      return ImmutableList.of();
    }
    Set<String> appliedKeys = new HashSet<>(appliedGroupBys);
    return keyMatches.stream()
        .flatMap(
            match ->
                groupByCandidate(context, match.key(), match.tier(), appliedKeys, /* count= */ true)
                    .stream())
        .limit(1)
        .collect(toImmutableList());
  }

  // ---- <key> is <value> ----

  /**
   * Conditions for a key the user named and a value they typed. The key resolves exactly through
   * the vocabulary; if it resolves to nothing, the entity's long-tail family mints a key so a
   * filter on a not-yet-discovered dimension or property is still possible. A comma outside quotes
   * makes the value a multi-value OR.
   */
  private ImmutableList<SuggestionCandidate> suggestKeyValue(
      SuggestionContext context, String keyToken, String rawValue, boolean exclude) {
    KeyVocabulary vocabulary = context.vocabulary();
    ImmutableList<KeyDescriptor> keys = vocabulary.resolve(keyToken);
    if (keys.isEmpty()) {
      keys = vocabulary.mintLongTailKey(keyToken).map(ImmutableList::of).orElse(ImmutableList.of());
    }

    if (isUnquotedList(rawValue)) {
      ImmutableList<String> parts = splitCommaLower(rawValue);
      if (parts.isEmpty()) {
        return ImmutableList.of();
      }
      ImmutableList<SuggestionCandidate> out =
          keys.stream()
              .flatMap(key -> multiValueOr(context, key, parts, exclude).stream())
              .collect(toImmutableList());
      if (!out.isEmpty()) {
        return out;
      }
      return keys.stream()
          .filter(key -> isColdLongTail(context, key))
          .map(key -> coldCondition(context, key, parts, exclude))
          .collect(toImmutableList());
    }

    String value = Ascii.toLowerCase(stripQuotes(rawValue.trim()));
    ImmutableList.Builder<SuggestionCandidate> out = ImmutableList.builder();
    for (KeyDescriptor key : keys) {
      ImmutableList<SuggestionCandidate> matched =
          matchValues(context, key.id(), value, /* allowContains= */ true).stream()
              .flatMap(
                  match -> condition(context, key, match.value(), match.tier(), exclude).stream())
              .collect(toImmutableList());
      out.addAll(matched);
      // A cold long-tail key has no index to match against; offer the typed value as is, uncounted.
      if (matched.isEmpty() && !value.isEmpty() && isColdLongTail(context, key)) {
        out.add(coldCondition(context, key, ImmutableList.of(rawValue.trim()), exclude));
      }
    }
    return out.build();
  }

  // ---- no <key> / <key> is not empty ----

  /**
   * Emptiness conditions for a named key. A key that is globally undiscriminating (every record has
   * it, or none does) is skipped, and so is a condition that matches nothing in the target scope.
   * When the key already carries a compatible simple chip without {@code (no value)}, the row
   * merges {@code (no value)} directly via {@code apply_filter}; when the existing chip conflicts
   * or already includes {@code (no value)}, only the bare-key picker opener is offered.
   */
  private ImmutableList<SuggestionCandidate> suggestEmptiness(
      SuggestionContext context, String keyToken, boolean isEmpty) {
    KeyVocabulary vocabulary = context.vocabulary();
    ImmutableList.Builder<SuggestionCandidate> out = ImmutableList.builder();
    int globalTotal = context.corpus().recordCount();
    boolean exclude = !isEmpty;
    for (KeyDescriptor key : vocabulary.resolve(keyToken)) {
      String keyId = key.id();
      if (!context.isIndexed(keyId)) {
        continue;
      }
      int globalPresent = context.globalPresenceCount(keyId);
      boolean globallyUseless =
          isEmpty
              ? globalTotal - globalPresent <= 0
              : globalPresent <= 0 || globalPresent == globalTotal;
      if (globallyUseless) {
        continue;
      }

      Optional<Filter> chip = context.chipFor(keyId);
      if (chip.isPresent()) {
        if (conflictsWithChip(chip.get(), exclude) || chipContainsNoValue(chip.get())) {
          FleetSuggestion.Builder opener =
              FleetSuggestion.newBuilder()
                  .setLabel(label(/* inChip= */ true))
                  .addAllMainText(segments(vocabulary.titleDisplayName(key), null))
                  .setOpenPicker(openPicker(vocabulary, key, /* inChip= */ true));
          out.add(SuggestionCandidate.keyOnly(key, MatchTier.EXACT, opener));
          continue;
        }
        Filter merged = appendNoValue(chip.get());
        if (!exclude) {
          int added = context.modifyIncludeEmptyDelta(keyId);
          if (added <= 0) {
            continue;
          }
          FleetSuggestion.Builder proto =
              FleetSuggestion.newBuilder()
                  .setLabel(label(/* inChip= */ true))
                  .setCountPrefix("+")
                  .addAllMainText(modifyConditionText(vocabulary, key, false, "(no value)"))
                  .setApplyFilter(applyFilter(vocabulary, context.index(), key, merged));
          out.add(SuggestionCandidate.presetCondition(key, MatchTier.EXACT, proto, added, added));
          continue;
        }
        int scope = context.scopeSize();
        int present = context.scopePresenceCount(keyId);
        if (present <= 0) {
          continue;
        }
        FleetSuggestion.Builder proto =
            FleetSuggestion.newBuilder()
                .setLabel(label(/* inChip= */ true))
                .addAllMainText(modifyConditionText(vocabulary, key, true, "(no value)"))
                .setApplyFilter(applyFilter(vocabulary, context.index(), key, merged));
        boolean coversScope = present == scope;
        MatchTier tier = coversScope ? MatchTier.EXACT.demoted() : MatchTier.EXACT;
        int rankCount = coversScope ? 1 : present;
        out.add(SuggestionCandidate.presetCondition(key, tier, proto, present, rankCount));
        continue;
      }

      int scope = context.scopeSize();
      int present = context.scopePresenceCount(keyId);
      int count = isEmpty ? scope - present : present;
      if (count <= 0) {
        continue;
      }
      Filter filter = noValueFilter(keyId, /* negated= */ exclude);
      FleetSuggestion.Builder proto =
          FleetSuggestion.newBuilder()
              .setLabel(label(/* inChip= */ false))
              .addAllMainText(
                  conditionText(
                      vocabulary, key, /* exclude= */ false, isEmpty ? "empty" : "not empty"))
              .setApplyFilter(applyFilter(vocabulary, context.index(), key, filter));
      // A condition covering the whole filtered set is valid but temporarily uninformative, so it
      // ranks one tier lower and as if it matched a single record.
      boolean coversScope = context.hasFilters() && count == scope;
      MatchTier tier = coversScope ? MatchTier.EXACT.demoted() : MatchTier.EXACT;
      int rankCount = coversScope ? 1 : count;
      out.add(SuggestionCandidate.presetCondition(key, tier, proto, count, rankCount));
    }
    return out.build();
  }

  // ---- <key> is (value pending) ----

  /** The key's most common values as ready-to-apply conditions, the text form of the picker. */
  private ImmutableList<SuggestionCandidate> suggestTopValuesOfKey(
      SuggestionContext context, String keyToken, boolean exclude) {
    return context.vocabulary().resolve(keyToken).stream()
        .flatMap(
            key ->
                topValues(context, key.id(), KEY_VALUES_PER_KEY).stream()
                    .flatMap(
                        value -> condition(context, key, value, MatchTier.EXACT, exclude).stream()))
        .collect(toImmutableList());
  }

  // ---- <value1>, <value2> ----

  /**
   * One OR condition per indexed key that carries every listed value. Keys that need a record pass
   * (filtered requests, or multi-valued keys when unfiltered) are evaluated strongest priority
   * first, then by descending upper-bound OR count and key id, up to {@code limit *
   * FILTERED_KEY_BUDGET_MULTIPLIER}.
   */
  private ImmutableList<SuggestionCandidate> suggestValueList(
      SuggestionContext context, ImmutableList<String> values, boolean exclude, int limit) {
    ImmutableList<String> lowered =
        values.stream().map(v -> Ascii.toLowerCase(stripQuotes(v))).collect(toImmutableList());
    ImmutableSet<String> distinctValues = ImmutableSet.copyOf(lowered);
    FleetIndex index = context.index();
    KeyVocabulary vocabulary = context.vocabulary();
    ImmutableList<KeyDescriptor> matchingKeys =
        index.keyIds().stream()
            .filter(keyId -> hasAllValues(index, keyId, lowered))
            .flatMap(keyId -> vocabulary.describe(keyId).stream())
            .sorted(
                Comparator.comparingInt(vocabulary::priority)
                    .reversed()
                    .thenComparing(
                        Comparator.comparingInt(
                                (KeyDescriptor key) ->
                                    distinctValues.stream()
                                        .mapToInt(v -> index.valueCount(key.id(), v))
                                        .sum())
                            .reversed())
                    .thenComparing(KeyDescriptor::id))
            .collect(toImmutableList());
    int scanBudget = limit * SuggestionRanker.FILTERED_KEY_BUDGET_MULTIPLIER;
    int scanned = 0;
    ImmutableList.Builder<SuggestionCandidate> out = ImmutableList.builder();
    for (KeyDescriptor key : matchingKeys) {
      boolean scansRecords = context.hasFilters() || index.keyStats(key.id()).multiValued();
      if (scansRecords) {
        if (scanned >= scanBudget) {
          continue;
        }
        scanned++;
      }
      multiValueOr(context, key, lowered, exclude).ifPresent(out::add);
    }
    return out.build();
  }

  // ---- a bare value ----

  /**
   * Conditions for a value with the key undetermined, searched across all keys: exact matches via
   * the global reverse index, prefix matches via the sorted global value list, and, for non-negated
   * inputs on identifier keys that do not already carry a chip, one collapsed "starts with" row per
   * identifier key so a UUID prefix does not produce thousands of rows.
   *
   * <p>Identifier keys are left out of the sorted global value list for that reason, but they are
   * in the global reverse index, so a complete ID always gets its exact {@code <Key> is <value>}
   * row. The collapsed row shows the number of matching records in {@link
   * FleetSuggestion#getCount}, which the frontend renders right-aligned like every other count, so
   * its main text does not repeat the number.
   */
  private ImmutableList<SuggestionCandidate> suggestValues(
      SuggestionContext context, String token, boolean exclude) {
    FleetIndex index = context.index();
    KeyVocabulary vocabulary = context.vocabulary();
    String prefix = stripQuotes(token);
    String value = Ascii.toLowerCase(prefix);
    ImmutableList.Builder<SuggestionCandidate> out = ImmutableList.builder();
    Set<String> seen = new HashSet<>();

    for (KeyCount keyCount : index.globalExact().getOrDefault(value, ImmutableList.of())) {
      if (seen.add(keyCount.key() + '\u0000' + value)) {
        conditionFor(context, keyCount.key(), value, MatchTier.EXACT, exclude).ifPresent(out::add);
      }
    }

    ImmutableList<ValueKeyPair> sorted = index.semanticGlobalSorted();
    int lo = lowerBound(sorted, value);
    int hi = lowerBound(sorted, value + '\uffff');
    int scanned = 0;
    for (int i = lo; i < hi && scanned < MAX_SEMANTIC_SCAN; i++) {
      ValueKeyPair pair = sorted.get(i);
      if (!pair.value().equals(value) && seen.add(pair.key() + '\u0000' + pair.value())) {
        Optional<SuggestionCandidate> candidate =
            conditionFor(context, pair.key(), pair.value(), MatchTier.PREFIX, exclude);
        if (candidate.isPresent()) {
          out.add(candidate.get());
          scanned++;
        }
      }
    }

    for (String identifierKeyId : index.keyIds()) {
      // StartsWith is not negatable, and applying a new filter cannot overwrite an existing chip on
      // the same key.
      if (exclude
          || !context.corpus().isIdentifierKey(identifierKeyId)
          || context.isActiveKey(identifierKeyId)) {
        continue;
      }
      Optional<KeyDescriptor> key = vocabulary.describe(identifierKeyId);
      ImmutableList<String> keyValues = index.sortedValues(identifierKeyId);
      if (key.isEmpty() || keyValues.isEmpty()) {
        continue;
      }
      int loIndex = FleetFilterEngine.lowerBound(keyValues, value);
      int hiIndex = FleetFilterEngine.lowerBound(keyValues, value + '\uffff');
      // A complete identifier value that no other value extends (globally or in the active scope)
      // already has its exact row, so a "starts with" row would only repeat those records. When
      // other values share the prefix (typing "device-1" with "device-10" present), the row covers
      // records the exact row does not, and it stays.
      if (loIndex == hiIndex || (hiIndex - loIndex == 1 && keyValues.get(loIndex).equals(value))) {
        continue;
      }
      int matchCount =
          context.scopePrefixCount(identifierKeyId, value, keyValues.subList(loIndex, hiIndex));
      if (matchCount == 0
          || (keyValues.get(loIndex).equals(value)
              && matchCount == context.scopeCount(identifierKeyId, value))) {
        continue;
      }
      Filter filter = startsWithFilter(identifierKeyId, prefix);
      FleetSuggestion.Builder proto =
          FleetSuggestion.newBuilder()
              .setLabel("Add filter")
              .addAllMainText(
                  segments(vocabulary.titleDisplayName(key.get()) + " starts with ", prefix))
              .setApplyFilter(applyFilter(vocabulary, index, key.get(), filter));
      // The row applies a prefix filter rather than a single value, so MatchTier.WEAK places it
      // below every concrete value match of the same key priority while still ordering it by
      // matchCount ahead of uncounted rows.
      out.add(
          SuggestionCandidate.presetCondition(
              key.get(), MatchTier.WEAK, proto, matchCount, matchCount));
    }
    return out.build();
  }

  // ---- a key name ----

  /**
   * For each matched key, best first: ready-to-apply conditions on its top values (for the
   * strongest few keys) and the bare key, which opens the picker. Both share the key's match tier;
   * the bare key carries no count, so among rows of the same key priority and tier it ranks below
   * every counted condition.
   */
  private ImmutableList<SuggestionCandidate> suggestKeyNames(
      SuggestionContext context, ImmutableList<KeyMatch> keyMatches) {
    KeyVocabulary vocabulary = context.vocabulary();
    ImmutableList.Builder<SuggestionCandidate> out = ImmutableList.builder();
    int rank = 0;
    for (KeyMatch keyMatch : keyMatches) {
      KeyDescriptor key = keyMatch.key();
      MatchTier tier = keyMatch.tier();
      if (rank < TOP_VALUE_KEYS) {
        for (String value : topValues(context, key.id(), TOP_VALUES_PER_KEY)) {
          condition(context, key, value, tier, /* exclude= */ false).ifPresent(out::add);
        }
      }
      boolean inChip = context.isActiveKey(key.id());
      FleetSuggestion.Builder proto =
          FleetSuggestion.newBuilder()
              .setLabel(label(inChip))
              .addAllMainText(segments(vocabulary.titleDisplayName(key), null))
              .setOpenPicker(openPicker(vocabulary, key, inChip));
      out.add(SuggestionCandidate.keyOnly(key, tier, proto));
      rank++;
    }
    return out.build();
  }

  /**
   * Keys whose name matches the token, best first: an exact resolution ({@link MatchTier#EXACT}),
   * then keys whose display or bare name starts with the token ({@link MatchTier#PREFIX}), then
   * keys whose name contains it ({@link MatchTier#WEAK}). Discoverable keys are those in the index
   * plus those the entity has seen but not indexed; an exactly resolved key also qualifies if it is
   * cold long-tail capable.
   */
  private static ImmutableList<KeyMatch> matchKeyNames(SuggestionContext context, String token) {
    KeyVocabulary vocabulary = context.vocabulary();
    ImmutableSet<KeyDescriptor> discoverable = vocabulary.discoverableKeys();
    Map<KeyDescriptor, KeyMatch> out = new LinkedHashMap<>();

    for (KeyDescriptor key : vocabulary.resolve(token)) {
      if (context.isIndexed(key.id())
          || discoverable.contains(key)
          || vocabulary.isColdCapable(key)) {
        out.putIfAbsent(key, new KeyMatch(key, MatchTier.EXACT));
      }
    }
    String term = KeyTokens.normalize(token);
    // One pass normalizes each key name once. Prefix matches go in as they are found and contains
    // matches are appended after them, both in discoverable order.
    List<KeyDescriptor> containsMatches = new ArrayList<>();
    for (KeyDescriptor key : discoverable) {
      if (out.containsKey(key)) {
        continue;
      }
      String normTitle = KeyTokens.normalize(vocabulary.titleDisplayName(key));
      String normBare = KeyTokens.normalize(key.bareName());
      if (normTitle.startsWith(term) || normBare.startsWith(term)) {
        out.put(key, new KeyMatch(key, MatchTier.PREFIX));
      } else if (normTitle.contains(term) || normBare.contains(term)) {
        containsMatches.add(key);
      }
    }
    for (KeyDescriptor key : containsMatches) {
      out.put(key, new KeyMatch(key, MatchTier.WEAK));
    }
    return ImmutableList.copyOf(out.values());
  }

  // ---- group by <key> ----

  /**
   * Group-by rows for the {@code group by} prefix. A bare prefix offers the curated candidates; a
   * term widens to any discoverable key whose name matches (or a minted cold long-tail key when
   * nothing discoverable matches). Requests that already carry the maximum number of group-bys, or
   * whose filters leave no record, yield nothing.
   *
   * <p>Without filters every group count comes from the per-snapshot key statistics. With filters
   * each count is one pass over the filtered records, so only the strongest {@code limit *
   * FILTERED_KEY_BUDGET_MULTIPLIER} indexed keys, by match quality and then key priority, are
   * counted; the remaining matches are still offered, without a group count. This bound is best
   * effort, like the filtered evaluation budget of {@link SuggestionRanker#rank}.
   */
  private static ImmutableList<SuggestionCandidate> groupByCandidates(
      SuggestionContext context, String term, List<String> applied, int limit) {
    if (applied.size() >= MAX_APPLIED_GROUP_BYS
        || (context.hasFilters() && context.current().isEmpty())) {
      return ImmutableList.of();
    }
    KeyVocabulary vocabulary = context.vocabulary();
    Set<String> appliedKeys = new HashSet<>(applied);
    ImmutableList<KeyMatch> matches;
    if (term.isEmpty()) {
      matches =
          vocabulary.groupByCandidates().stream()
              .map(key -> new KeyMatch(key, MatchTier.EXACT))
              .collect(toImmutableList());
    } else {
      matches = matchKeyNames(context, term);
      if (matches.isEmpty()) {
        matches =
            vocabulary
                .mintLongTailKey(term)
                .map(key -> ImmutableList.of(new KeyMatch(key, MatchTier.EXACT)))
                .orElse(ImmutableList.of());
      }
    }

    ImmutableList<KeyMatch> strongestFirst =
        ImmutableList.sortedCopyOf(
            Comparator.comparing(KeyMatch::tier)
                .thenComparing(
                    Comparator.comparingInt((KeyMatch match) -> vocabulary.priority(match.key()))
                        .reversed()),
            matches);
    int countBudget =
        context.hasFilters()
            ? limit * SuggestionRanker.FILTERED_KEY_BUDGET_MULTIPLIER
            : Integer.MAX_VALUE;
    int counted = 0;
    ImmutableList.Builder<SuggestionCandidate> out = ImmutableList.builder();
    for (KeyMatch match : strongestFirst) {
      String keyId = match.key().id();
      boolean needsCount = context.isIndexed(keyId) && !appliedKeys.contains(keyId);
      boolean count = !needsCount || counted < countBudget;
      if (needsCount && count) {
        counted++;
      }
      groupByCandidate(context, match.key(), match.tier(), appliedKeys, count).ifPresent(out::add);
    }
    return out.build();
  }

  /**
   * The group-by row for one key, or empty when the key cannot be grouped in this request: it is
   * already grouped by, it is an unindexed built-in key, or it is counted and the active filters
   * leave it no group. Single-group keys are still offered (with {@code 1 group}) so the user can
   * add or adjust group-by keys freely while editing filters. A row is offered without a group
   * count for a cold long-tail key (from the dimension catalog or minted from the term), which has
   * no overlay loaded during typeahead and is pulled when the grouping is executed, and for an
   * indexed key when {@code count} is false. {@code tier} is how well the key name matches the
   * typed term, used for ordering.
   */
  private static Optional<SuggestionCandidate> groupByCandidate(
      SuggestionContext context,
      KeyDescriptor key,
      MatchTier tier,
      Set<String> appliedKeys,
      boolean count) {
    String keyId = key.id();
    if (appliedKeys.contains(keyId)) {
      return Optional.empty();
    }
    KeyVocabulary vocabulary = context.vocabulary();
    boolean indexed = context.isIndexed(keyId);
    if (!indexed && !key.isLongTail()) {
      return Optional.empty();
    }
    if (!indexed || !count) {
      FleetSuggestion.Builder proto =
          FleetSuggestion.newBuilder()
              .setLabel("Group by")
              .addAllMainText(segments("", vocabulary.titleDisplayName(key)))
              .setAddGroupBy(SuggestionActions.addGroupBy(vocabulary, key));
      return Optional.of(SuggestionCandidate.uncountedGroupBy(key, proto, tier));
    }
    int groups = groupCount(context, keyId);
    if (groups == 0) {
      return Optional.empty();
    }
    boolean overMax = groups > GROUP_SUGGEST_MAX;
    FleetSuggestion.Builder proto =
        FleetSuggestion.newBuilder()
            .setLabel("Group by")
            .addAllMainText(segments("", vocabulary.titleDisplayName(key)))
            .setCount(groups)
            .setCountUnit(groups == 1 ? "group" : "groups")
            .setOverMax(overMax)
            .setAddGroupBy(SuggestionActions.addGroupBy(vocabulary, key));
    return Optional.of(SuggestionCandidate.groupBy(key, proto, groups, tier, overMax));
  }

  /**
   * Number of groups a group-by on the key would produce over the current scope, shared with the
   * group-by key catalog through {@link KeyCounts#groupCount}: from the per-snapshot key statistics
   * without filters, or one pass over the filtered records with them.
   */
  private static int groupCount(SuggestionContext context, String keyId) {
    return KeyCounts.groupCount(
        context.corpus(),
        context.hasFilters() ? Optional.of(context.current()) : Optional.empty(),
        keyId);
  }

  // ---- Candidate builders shared by several shapes ----

  /** {@link #condition} for a key id taken from the index, which the vocabulary may not know. */
  private static Optional<SuggestionCandidate> conditionFor(
      SuggestionContext context, String keyId, String valueLower, MatchTier tier, boolean exclude) {
    return context
        .vocabulary()
        .describe(keyId)
        .flatMap(key -> condition(context, key, valueLower, tier, exclude));
  }

  /**
   * A concrete single-value row, or empty when it fails the eligibility gate: the value must exist
   * somewhere in the fleet, and when the key already carries a chip, that chip must be simple, have
   * the same polarity, and not yet contain the value (a conflicting or complex chip leaves only the
   * bare-key picker row).
   */
  private static Optional<SuggestionCandidate> condition(
      SuggestionContext context,
      KeyDescriptor key,
      String valueLower,
      MatchTier tier,
      boolean exclude) {
    FleetIndex index = context.index();
    KeyVocabulary vocabulary = context.vocabulary();
    String keyId = key.id();
    if (!context.isIndexed(keyId)) {
      return Optional.empty();
    }
    int global = index.valueCount(keyId, valueLower);
    if (global == 0) {
      return Optional.empty();
    }
    Optional<Filter> chip = context.chipFor(keyId);
    boolean inChip = chip.isPresent();
    if (inChip
        && (conflictsWithChip(chip.get(), exclude) || chipContainsValue(chip.get(), valueLower))) {
      return Optional.empty();
    }

    String shown = displayValue(index, keyId, valueLower);
    Filter filter =
        inChip
            ? appendValues(chip.get(), ImmutableList.of(shown))
            : valueFilter(keyId, ImmutableList.of(shown), exclude);
    ImmutableList<TextSegment> mainText =
        inChip
            ? modifyConditionText(vocabulary, key, exclude, shown)
            : conditionText(vocabulary, key, exclude, shown);
    FleetSuggestion.Builder proto =
        FleetSuggestion.newBuilder()
            .setLabel(label(inChip))
            .addAllMainText(mainText)
            .setApplyFilter(applyFilter(vocabulary, index, key, filter));
    return Optional.of(
        SuggestionCandidate.valueCondition(key, tier, proto, valueLower, exclude, inChip, global));
  }

  private static boolean conflictsWithChip(Filter chip, boolean exclude) {
    return !chip.hasSimple() || chip.getSimple().getNegated() != exclude;
  }

  /**
   * Whether {@code simpleChip} already lists {@code valueLower} (which is a non-empty indexed
   * value, so {@code no_value} entries with {@code getValue() == ""} never match).
   */
  private static boolean chipContainsValue(Filter simpleChip, String valueLower) {
    return simpleChip.getSimple().getValuesList().stream()
        .anyMatch(v -> Ascii.equalsIgnoreCase(v.getValue(), valueLower));
  }

  private static boolean chipContainsNoValue(Filter simpleChip) {
    return simpleChip.getSimple().getValuesList().stream()
        .anyMatch(v -> v.getKindCase() == FilterValue.KindCase.NO_VALUE);
  }

  /** One OR row over the listed values that exist under the key, counted as a set. */
  private static Optional<SuggestionCandidate> multiValueOr(
      SuggestionContext context,
      KeyDescriptor key,
      ImmutableList<String> valuesLower,
      boolean exclude) {
    FleetIndex index = context.index();
    KeyVocabulary vocabulary = context.vocabulary();
    String keyId = key.id();
    ImmutableList<String> present =
        valuesLower.stream()
            .filter(value -> index.valueCount(keyId, value) > 0)
            .distinct()
            .collect(toImmutableList());
    if (present.isEmpty()) {
      return Optional.empty();
    }

    Optional<Filter> chip = context.chipFor(keyId);
    if (chip.isPresent()) {
      Filter existing = chip.get();
      if (conflictsWithChip(existing, exclude)) {
        return Optional.empty();
      }
      ImmutableList<String> addedLower =
          present.stream()
              .filter(value -> !chipContainsValue(existing, value))
              .collect(toImmutableList());
      if (addedLower.isEmpty()) {
        return Optional.empty();
      }
      int count =
          exclude
              ? context.scopeSize() - context.scopeUnionCount(keyId, addedLower)
              : context.modifyIncludeUnionDelta(keyId, addedLower);
      if (count <= 0) {
        return Optional.empty();
      }
      ImmutableList<String> addedShown =
          addedLower.stream()
              .map(value -> displayValue(index, keyId, value))
              .collect(toImmutableList());
      Filter merged = appendValues(chip.get(), addedShown);
      FleetSuggestion.Builder proto =
          FleetSuggestion.newBuilder()
              .setLabel(label(/* inChip= */ true))
              .addAllMainText(valueListMainText(modifyPrefix(vocabulary, key, exclude), addedShown))
              .setApplyFilter(applyFilter(vocabulary, index, key, merged));
      if (!exclude) {
        proto.setCountPrefix("+");
      }
      return Optional.of(
          SuggestionCandidate.presetCondition(key, MatchTier.EXACT, proto, count, count));
    }

    int unionMatches = context.scopeUnionCount(keyId, present);
    int orCount = exclude ? context.scopeSize() - unionMatches : unionMatches;
    if (orCount <= 0) {
      return Optional.empty();
    }
    ImmutableList<String> shown =
        present.stream().map(value -> displayValue(index, keyId, value)).collect(toImmutableList());
    String prefix = vocabulary.titleDisplayName(key) + " " + verb(key, exclude) + " ";
    FleetSuggestion.Builder proto =
        FleetSuggestion.newBuilder()
            .setLabel(label(/* inChip= */ false))
            .addAllMainText(valueListMainText(prefix, shown))
            .setApplyFilter(
                applyFilter(vocabulary, index, key, valueFilter(keyId, shown, exclude)));
    return Optional.of(
        SuggestionCandidate.presetCondition(key, MatchTier.EXACT, proto, orCount, orCount));
  }

  private static List<TextSegment> valueListMainText(String prefix, ImmutableList<String> shown) {
    List<TextSegment> mainText = new ArrayList<>();
    mainText.add(text(prefix, false));
    for (int i = 0; i < shown.size(); i++) {
      if (i > 0) {
        mainText.add(text(" or ", false));
      }
      mainText.add(text(shown.get(i), true));
    }
    return mainText;
  }

  /**
   * A ready-to-apply, uncounted row on a cold long-tail key with the values exactly as typed. The
   * index cannot confirm the values exist, so the row ranks in {@link MatchTier#WEAK}.
   */
  private static SuggestionCandidate coldCondition(
      SuggestionContext context,
      KeyDescriptor key,
      ImmutableList<String> shownValues,
      boolean exclude) {
    KeyVocabulary vocabulary = context.vocabulary();
    Filter filter = valueFilter(key.id(), shownValues, exclude);
    FleetSuggestion.Builder proto =
        FleetSuggestion.newBuilder()
            .setLabel("Add filter")
            .addAllMainText(conditionText(vocabulary, key, exclude, String.join(", ", shownValues)))
            .setApplyFilter(applyFilter(vocabulary, context.index(), key, filter));
    return SuggestionCandidate.uncountedCondition(key, MatchTier.WEAK, proto);
  }

  /** A long-tail key with nothing indexed yet and no chip already applied. */
  private static boolean isColdLongTail(SuggestionContext context, KeyDescriptor key) {
    return context.vocabulary().isColdCapable(key)
        && !context.isIndexed(key.id())
        && !context.isActiveKey(key.id());
  }

  // ---- Value matching ----

  /**
   * Values of the key matching the query: whole-value matches first (space and underscore
   * interchangeable) in {@link MatchTier#EXACT}, then prefix matches in {@link MatchTier#PREFIX},
   * then, if allowed and the key is not an identifier, substring matches in {@link MatchTier#WEAK}
   * until enough are found.
   */
  private static ImmutableList<ValueMatch> matchValues(
      SuggestionContext context, String keyId, String query, boolean allowContains) {
    FleetIndex index = context.index();
    ImmutableList<String> sorted = index.sortedValues(keyId);
    if (sorted.isEmpty() || query.isEmpty()) {
      return ImmutableList.of();
    }
    ImmutableMap<String, Integer> counts = index.valueCounts(keyId);
    List<ValueMatch> out = new ArrayList<>();
    Set<String> fullHits = new HashSet<>();
    for (String variant : new String[] {query, query.replace(' ', '_'), query.replace('_', ' ')}) {
      if (counts.containsKey(variant) && fullHits.add(variant)) {
        out.add(new ValueMatch(variant, MatchTier.EXACT));
      }
    }
    int lo = FleetFilterEngine.lowerBound(sorted, query);
    int hi = FleetFilterEngine.lowerBound(sorted, query + '\uffff');
    for (int i = lo; i < hi; i++) {
      String value = sorted.get(i);
      if (!fullHits.contains(value)) {
        out.add(new ValueMatch(value, MatchTier.PREFIX));
      }
    }
    if (allowContains
        && !context.corpus().isIdentifierKey(keyId)
        && out.size() < KEY_VALUES_PER_KEY) {
      for (String value : sorted) {
        if (value.contains(query) && !value.startsWith(query)) {
          out.add(new ValueMatch(value, MatchTier.WEAK));
          if (out.size() >= KEY_VALUES_PER_KEY * 3) {
            break;
          }
        }
      }
    }
    return ImmutableList.copyOf(out);
  }

  /** The key's {@code n} most common values, most common first, ties alphabetical. */
  private static ImmutableList<String> topValues(SuggestionContext context, String keyId, int n) {
    return context.index().valueCounts(keyId).entrySet().stream()
        .filter(entry -> entry.getValue() > 0)
        .sorted(
            Map.Entry.<String, Integer>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(n)
        .map(Map.Entry::getKey)
        .collect(toImmutableList());
  }

  // ---- Small helpers ----

  /** A comma outside quotes means several values; quotes make the whole text one literal value. */
  private static boolean isUnquotedList(String rawValue) {
    return rawValue.contains(",") && !rawValue.contains("\"") && !rawValue.contains("'");
  }

  private static String stripQuotes(String value) {
    String trimmed = value.trim();
    if (trimmed.length() >= 2
        && trimmed.charAt(0) == trimmed.charAt(trimmed.length() - 1)
        && (trimmed.charAt(0) == '"' || trimmed.charAt(0) == '\'')) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }

  private static ImmutableList<String> splitCommaLower(String raw) {
    return Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(part -> !part.isEmpty())
        .map(Ascii::toLowerCase)
        .collect(toImmutableList());
  }

  private static boolean hasAllValues(
      FleetIndex index, String keyId, ImmutableList<String> valuesLower) {
    return valuesLower.stream().allMatch(value -> index.valueCount(keyId, value) > 0);
  }

  /** Lower bound binary search over the sorted global value index. */
  private static int lowerBound(List<ValueKeyPair> sorted, String valuePrefix) {
    int lo = 0;
    int hi = sorted.size();
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (sorted.get(mid).value().compareTo(valuePrefix) < 0) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    return lo;
  }

  /** A value of a key that matches the typed text, with how strongly it matches. */
  private record ValueMatch(String value, MatchTier tier) {}

  /** A key whose name matches the typed text, with how strongly it matches. */
  private record KeyMatch(KeyDescriptor key, MatchTier tier) {}
}
