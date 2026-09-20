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
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionActions.applyFilter;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionActions.noValueFilter;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionActions.openPicker;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionActions.openPickerNewChip;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionActions.openPickerStaged;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionActions.valueFilter;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionText.conditionText;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionText.displayValue;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionText.label;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionText.segments;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionText.text;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionText.verb;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestion;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TextSegment;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.KeyCount;
import com.google.devtools.mobileharness.fe.v6.service.search.index.ValueKeyPair;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetFilterEngine;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyTokens;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyVocabulary;
import com.google.devtools.mobileharness.fe.v6.service.search.query.SearchCorpus;
import com.google.devtools.mobileharness.fe.v6.service.search.query.suggest.SuggestionIntentParser.Intent;
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
import java.util.TreeSet;
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
 *   <li>a single token as a key name (3): {@link #suggestKeyNames}.
 * </ul>
 *
 * <p>An identifier typed on its own resolves through the value path: a device UUID matches the UUID
 * key via the value index, so no dedicated identifier detector exists.
 */
public final class FleetSuggester {

  /** A grouping needs at least two buckets to be worth offering. */
  private static final int GROUP_SUGGEST_MIN = 2;

  /** Above this bucket count a suggested grouping is flagged {@code over_max}. */
  private static final int GROUP_SUGGEST_MAX = 50;

  /** At most this many group-by keys may already be applied for more to be suggested. */
  private static final int MAX_APPLIED_GROUP_BYS = 3;

  private static final int DEFAULT_LIMIT = 12;

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

  /** Match quality tiers shared by value and key-name matching. */
  private static final int TIER_FULL = 3;

  private static final int TIER_PREFIX = 2;
  private static final int TIER_CONTAINS = 1;

  private final FleetFilterEngine filterEngine;
  private final SuggestionRanker ranker;

  @Inject
  FleetSuggester(FleetFilterEngine filterEngine) {
    this.filterEngine = filterEngine;
    this.ranker = new SuggestionRanker(filterEngine);
  }

  /** Returns ranked suggestions for the request against the given corpus. */
  public FleetSuggestionResponse suggest(SearchCorpus corpus, FleetSuggestionRequest request) {
    int limit = request.getLimit() > 0 ? request.getLimit() : DEFAULT_LIMIT;
    String query = WHITESPACE.matcher(request.getInput()).replaceAll(" ").trim();
    if (query.isEmpty()) {
      return FleetSuggestionResponse.getDefaultInstance();
    }
    SuggestionContext context =
        SuggestionContext.create(corpus, request.getFiltersList(), filterEngine);

    // The group-by prefix owns its input: a term matching no key yields nothing rather than falling
    // through to a value search for the literal word "group".
    Optional<String> groupByTerm = SuggestionIntentParser.groupByTerm(query);
    if (groupByTerm.isPresent()) {
      return ranker.rankGroupBys(
          context, groupByCandidates(context, groupByTerm.get(), request.getGroupByList()), limit);
    }

    Intent intent = SuggestionIntentParser.parse(query);
    ImmutableList<SuggestionCandidate> raw =
        switch (intent.pattern()) {
          case KEY_VALUE ->
              suggestKeyValue(context, intent.key(), intent.value(), /* exclude= */ false);
          case KEY_VALUE_NEGATED ->
              suggestKeyValue(context, intent.key(), intent.value(), /* exclude= */ true);
          case EMPTINESS -> suggestEmptiness(context, intent.key(), intent.isEmpty());
          case KEY_WITH_OPERATOR -> suggestTopValuesOfKey(context, intent.key(), intent.exclude());
          case NEGATED_VALUE -> suggestValues(context, intent.value(), /* exclude= */ true);
          case VALUE_LIST -> suggestValueList(context, intent.values(), /* exclude= */ false);
          case SINGLE_TOKEN -> {
            // Key names match at any length; values only from MIN_VALUE_SEARCH_LENGTH, so very
            // short inputs are not flooded with value matches.
            ImmutableList.Builder<SuggestionCandidate> both = ImmutableList.builder();
            both.addAll(suggestKeyNames(context, query));
            if (query.length() >= MIN_VALUE_SEARCH_LENGTH) {
              both.addAll(suggestValues(context, query, /* exclude= */ false));
            }
            yield both.build();
          }
        };
    return ranker.rank(context, raw, limit);
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
   * it, or none does) is skipped, and so is a condition that matches nothing in the current scope.
   */
  private ImmutableList<SuggestionCandidate> suggestEmptiness(
      SuggestionContext context, String keyToken, boolean isEmpty) {
    KeyVocabulary vocabulary = context.vocabulary();
    ImmutableList.Builder<SuggestionCandidate> out = ImmutableList.builder();
    int globalTotal = context.corpus().recordCount();
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
      int scope = context.scopeSize();
      int present = context.scopePresenceCount(keyId);
      int count = isEmpty ? scope - present : present;
      if (count <= 0) {
        continue;
      }

      boolean inChip = context.isActiveKey(keyId);
      ImmutableList<TextSegment> mainText =
          conditionText(vocabulary, key, /* exclude= */ false, isEmpty ? "empty" : "not empty");
      FleetSuggestion.Builder proto =
          FleetSuggestion.newBuilder()
              .setLabel(label(vocabulary, key, inChip))
              .addAllMainText(mainText);
      if (inChip) {
        proto.setOpenPicker(SuggestionActions.openPickerViewExisting(vocabulary, key));
      } else {
        Filter filter = noValueFilter(keyId, /* negated= */ !isEmpty);
        proto.setApplyFilter(applyFilter(vocabulary, context.index(), key, filter));
      }

      // A condition covering the whole filtered set is valid but temporarily uninformative, so it
      // ranks one tier lower and as if it matched a single record.
      boolean coversScope = context.hasFilters() && count == scope;
      double tier = coversScope ? TIER_PREFIX : TIER_FULL;
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
                        match ->
                            condition(context, key, match.value(), TIER_FULL, exclude).stream()))
        .collect(toImmutableList());
  }

  // ---- <value1>, <value2> ----

  /** One OR condition per indexed key that carries every listed value. */
  private ImmutableList<SuggestionCandidate> suggestValueList(
      SuggestionContext context, ImmutableList<String> values, boolean exclude) {
    ImmutableList<String> lowered =
        values.stream().map(v -> Ascii.toLowerCase(stripQuotes(v))).collect(toImmutableList());
    KeyVocabulary vocabulary = context.vocabulary();
    return context.index().keyIds().stream()
        .filter(keyId -> hasAllValues(context.index(), keyId, lowered))
        .flatMap(keyId -> vocabulary.describe(keyId).stream())
        .flatMap(key -> multiValueOr(context, key, lowered, exclude).stream())
        .collect(toImmutableList());
  }

  // ---- a bare value ----

  /**
   * Conditions for a value with the key undetermined, searched across all keys: exact matches via
   * the global reverse index, prefix matches via the sorted global value list, and one collapsed
   * "starts with" row per identifier key so a UUID prefix does not produce thousands of rows.
   */
  private ImmutableList<SuggestionCandidate> suggestValues(
      SuggestionContext context, String token, boolean exclude) {
    FleetIndex index = context.index();
    KeyVocabulary vocabulary = context.vocabulary();
    String value = Ascii.toLowerCase(stripQuotes(token));
    ImmutableList.Builder<SuggestionCandidate> out = ImmutableList.builder();
    Set<String> seen = new HashSet<>();

    for (KeyCount keyCount : index.globalExact().getOrDefault(value, ImmutableList.of())) {
      if (seen.add(keyCount.key() + '\u0000' + value)) {
        conditionFor(context, keyCount.key(), value, TIER_FULL, exclude).ifPresent(out::add);
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
            conditionFor(context, pair.key(), pair.value(), TIER_PREFIX, exclude);
        if (candidate.isPresent()) {
          out.add(candidate.get());
          scanned++;
        }
      }
    }

    for (String identifierKeyId : index.keyIds()) {
      if (!context.corpus().isIdentifierKey(identifierKeyId)) {
        continue;
      }
      Optional<KeyDescriptor> key = vocabulary.describe(identifierKeyId);
      ImmutableList<String> keyValues = index.sortedValues(identifierKeyId);
      if (key.isEmpty() || keyValues.isEmpty()) {
        continue;
      }
      int matchCount =
          FleetFilterEngine.lowerBound(keyValues, value + '\uffff')
              - FleetFilterEngine.lowerBound(keyValues, value);
      if (matchCount == 0) {
        continue;
      }
      ImmutableList<TextSegment> mainText =
          ImmutableList.of(
              text(vocabulary.titleDisplayName(key.get()) + " starts with ", false),
              text(token, true),
              text(" (" + matchCount + ")", false));
      FleetSuggestion.Builder proto =
          FleetSuggestion.newBuilder()
              .setLabel("Add filter")
              .addAllMainText(mainText)
              .setCount(matchCount)
              .setOpenPicker(openPickerNewChip(vocabulary, key.get()));
      out.add(SuggestionCandidate.keyOnly(key.get(), TIER_CONTAINS, proto));
    }
    return out.build();
  }

  // ---- a key name ----

  /**
   * For each key whose name matches the token: ready-to-apply conditions on its top values (for the
   * strongest few keys) and the bare key, which opens the picker and ranks just below a condition.
   */
  private ImmutableList<SuggestionCandidate> suggestKeyNames(
      SuggestionContext context, String token) {
    KeyVocabulary vocabulary = context.vocabulary();
    ImmutableList.Builder<SuggestionCandidate> out = ImmutableList.builder();
    int rank = 0;
    for (KeyMatch keyMatch : matchKeyNames(context, token)) {
      KeyDescriptor key = keyMatch.key();
      double tier = keyMatch.tier();
      if (rank < TOP_VALUE_KEYS) {
        for (ValueMatch match : topValues(context, key.id(), TOP_VALUES_PER_KEY)) {
          condition(context, key, match.value(), tier, /* exclude= */ false).ifPresent(out::add);
        }
      }
      boolean inChip = context.isActiveKey(key.id());
      FleetSuggestion.Builder proto =
          FleetSuggestion.newBuilder()
              .setLabel(label(vocabulary, key, inChip))
              .addAllMainText(segments(vocabulary.titleDisplayName(key), null))
              .setOpenPicker(openPicker(vocabulary, key, inChip));
      out.add(SuggestionCandidate.keyOnly(key, tier - 0.4, proto));
      rank++;
    }
    return out.build();
  }

  /**
   * Keys whose name matches the token, best first: an exact resolution (full tier), then keys whose
   * display or bare name starts with the token (prefix tier), then keys whose name contains it
   * (contains tier). Discoverable keys are those in the index plus those the entity has seen but
   * not indexed; an exactly resolved key also qualifies if it is cold long-tail capable.
   */
  private static ImmutableList<KeyMatch> matchKeyNames(SuggestionContext context, String token) {
    KeyVocabulary vocabulary = context.vocabulary();
    ImmutableSet<KeyDescriptor> discoverable = vocabulary.discoverableKeys();
    Map<KeyDescriptor, KeyMatch> out = new LinkedHashMap<>();

    for (KeyDescriptor key : vocabulary.resolve(token)) {
      if (context.isIndexed(key.id())
          || discoverable.contains(key)
          || vocabulary.isColdCapable(key)) {
        out.putIfAbsent(key, new KeyMatch(key, TIER_FULL));
      }
    }
    String term = KeyTokens.normalize(token);
    for (KeyDescriptor key : discoverable) {
      if (!out.containsKey(key) && nameStartsWith(vocabulary, key, term)) {
        out.put(key, new KeyMatch(key, TIER_PREFIX));
      }
    }
    for (KeyDescriptor key : discoverable) {
      if (!out.containsKey(key) && nameContains(vocabulary, key, term)) {
        out.put(key, new KeyMatch(key, TIER_CONTAINS));
      }
    }
    return ImmutableList.copyOf(out.values());
  }

  // ---- group by <key> ----

  /**
   * Group-by rows. A bare prefix offers the curated candidates; a term widens to any indexed key
   * whose name matches, ranked by match quality. Keys already grouped by, keys producing fewer than
   * two buckets, and requests that already carry the maximum number of group-bys yield nothing.
   */
  private ImmutableList<SuggestionCandidate> groupByCandidates(
      SuggestionContext context, String term, List<String> applied) {
    if (applied.size() >= MAX_APPLIED_GROUP_BYS) {
      return ImmutableList.of();
    }
    KeyVocabulary vocabulary = context.vocabulary();
    Set<String> appliedKeys = new HashSet<>(applied);

    // Match quality per key: 0 exact resolution or curated candidate, 1 name prefix, 2 contains.
    Map<KeyDescriptor, Integer> matchRank = new LinkedHashMap<>();
    if (term.isEmpty()) {
      vocabulary.groupByCandidates().forEach(key -> matchRank.put(key, 0));
    } else {
      String normalized = KeyTokens.normalize(term);
      vocabulary.resolve(term).forEach(key -> matchRank.put(key, 0));
      for (String keyId : context.index().keyIds()) {
        Optional<KeyDescriptor> indexed = vocabulary.describe(keyId);
        if (indexed.isEmpty() || matchRank.containsKey(indexed.get())) {
          continue;
        }
        if (nameStartsWith(vocabulary, indexed.get(), normalized)) {
          matchRank.put(indexed.get(), 1);
        } else if (nameContains(vocabulary, indexed.get(), normalized)) {
          matchRank.put(indexed.get(), 2);
        }
      }
    }

    ImmutableList.Builder<SuggestionCandidate> out = ImmutableList.builder();
    for (Map.Entry<KeyDescriptor, Integer> entry : matchRank.entrySet()) {
      KeyDescriptor key = entry.getKey();
      String keyId = key.id();
      if (!context.isIndexed(keyId) || appliedKeys.contains(keyId)) {
        continue;
      }
      int groups = groupCount(context, keyId);
      if (groups < GROUP_SUGGEST_MIN) {
        continue;
      }
      boolean overMax = groups > GROUP_SUGGEST_MAX;
      FleetSuggestion.Builder proto =
          FleetSuggestion.newBuilder()
              .setLabel("Group by")
              .addAllMainText(segments("", vocabulary.titleDisplayName(key)))
              .setCount(groups)
              .setCountUnit("groups")
              .setOverMax(overMax)
              .setAddGroupBy(SuggestionActions.addGroupBy(vocabulary, key));
      out.add(SuggestionCandidate.groupBy(key, proto, groups, entry.getValue(), overMax));
    }
    return out.build();
  }

  /**
   * Number of distinct value combinations of the key across the current scope, plus one if any
   * record lacks the key.
   */
  private static int groupCount(SuggestionContext context, String keyId) {
    Set<String> combos = new HashSet<>();
    boolean hasMissing = false;
    for (int position : context.current()) {
      ImmutableSet<String> values = context.corpus().valuesForKey(position, keyId);
      if (values.isEmpty()) {
        hasMissing = true;
      } else {
        combos.add(String.join("\u0000", new TreeSet<>(values)));
      }
    }
    return combos.size() + (hasMissing ? 1 : 0);
  }

  // ---- Candidate builders shared by several shapes ----

  /** {@link #condition} for a key id taken from the index, which the vocabulary may not know. */
  private static Optional<SuggestionCandidate> conditionFor(
      SuggestionContext context, String keyId, String valueLower, double tier, boolean exclude) {
    return context
        .vocabulary()
        .describe(keyId)
        .flatMap(key -> condition(context, key, valueLower, tier, exclude));
  }

  /**
   * A {@code <key> is <value>} row, or empty when it fails the eligibility gate: the value must
   * exist somewhere in the fleet, and when the key already carries a chip, that chip must be simple
   * and of the same polarity (a conflicting or complex chip leaves only the key-level row).
   */
  private static Optional<SuggestionCandidate> condition(
      SuggestionContext context,
      KeyDescriptor key,
      String valueLower,
      double tier,
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
    boolean inChip = context.isActiveKey(keyId);
    if (inChip && conflictsWithChip(context.chipFor(keyId), exclude)) {
      return Optional.empty();
    }

    String shown = displayValue(index, keyId, valueLower);
    FleetSuggestion.Builder proto =
        FleetSuggestion.newBuilder().setLabel(label(vocabulary, key, inChip));
    if (inChip) {
      // Editing an existing same-polarity chip stages the value in the picker; one-click apply is
      // reserved for brand-new chips.
      proto
          .addAllMainText(segments(exclude ? "exclude " : "add ", shown))
          .setOpenPicker(openPickerStaged(vocabulary, key, ImmutableList.of(shown)));
    } else {
      Filter filter = valueFilter(keyId, ImmutableList.of(shown), exclude);
      proto
          .addAllMainText(conditionText(vocabulary, key, exclude, shown))
          .setApplyFilter(applyFilter(vocabulary, index, key, filter));
    }
    return Optional.of(
        SuggestionCandidate.valueCondition(key, tier, proto, valueLower, exclude, inChip, global));
  }

  private static boolean conflictsWithChip(Optional<Filter> chip, boolean exclude) {
    if (chip.isEmpty()) {
      return false;
    }
    return switch (chip.get().getModeCase()) {
      case COMPLEX -> true;
      case SIMPLE -> chip.get().getSimple().getNegated() != exclude;
      case MODE_NOT_SET -> false;
    };
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
            .collect(toImmutableList());
    if (present.isEmpty()) {
      return Optional.empty();
    }
    ImmutableList<String> shown =
        present.stream().map(value -> displayValue(index, keyId, value)).collect(toImmutableList());
    int orCount = context.scopeUnionCount(keyId, present);
    if (orCount == 0) {
      return Optional.empty();
    }
    boolean inChip = context.isActiveKey(keyId);

    List<TextSegment> mainText = new ArrayList<>();
    mainText.add(
        text(
            inChip ? "add " : vocabulary.titleDisplayName(key) + " " + verb(key, exclude) + " ",
            false));
    for (int i = 0; i < shown.size(); i++) {
      if (i > 0) {
        mainText.add(text(" or ", false));
      }
      mainText.add(text(shown.get(i), true));
    }
    FleetSuggestion.Builder proto =
        FleetSuggestion.newBuilder()
            .setLabel(label(vocabulary, key, inChip))
            .addAllMainText(mainText);
    if (inChip) {
      proto.setOpenPicker(openPickerStaged(vocabulary, key, shown));
    } else {
      proto.setApplyFilter(applyFilter(vocabulary, index, key, valueFilter(keyId, shown, exclude)));
    }
    return Optional.of(
        SuggestionCandidate.presetCondition(key, TIER_FULL, proto, orCount, orCount));
  }

  /** A ready-to-apply, uncounted row on a cold long-tail key with the values exactly as typed. */
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
    return SuggestionCandidate.uncountedCondition(key, TIER_CONTAINS, proto);
  }

  /** A long-tail key with nothing indexed yet and no chip already applied. */
  private static boolean isColdLongTail(SuggestionContext context, KeyDescriptor key) {
    return context.vocabulary().isColdCapable(key)
        && !context.isIndexed(key.id())
        && !context.isActiveKey(key.id());
  }

  // ---- Value matching ----

  /**
   * Values of the key matching the query, full matches first (space and underscore
   * interchangeable), then prefix matches, then, if allowed and the key is not an identifier,
   * contains matches until enough are found.
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
        out.add(new ValueMatch(variant, TIER_FULL));
      }
    }
    int lo = FleetFilterEngine.lowerBound(sorted, query);
    int hi = FleetFilterEngine.lowerBound(sorted, query + '\uffff');
    for (int i = lo; i < hi; i++) {
      String value = sorted.get(i);
      if (!fullHits.contains(value)) {
        out.add(new ValueMatch(value, TIER_PREFIX));
      }
    }
    if (allowContains
        && !context.corpus().isIdentifierKey(keyId)
        && out.size() < KEY_VALUES_PER_KEY) {
      for (String value : sorted) {
        if (value.contains(query) && !value.startsWith(query) && !value.equals(query)) {
          out.add(new ValueMatch(value, TIER_CONTAINS));
          if (out.size() >= KEY_VALUES_PER_KEY * 3) {
            break;
          }
        }
      }
    }
    return ImmutableList.copyOf(out);
  }

  /** The key's most common values, most common first, ties alphabetical. */
  private static ImmutableList<ValueMatch> topValues(
      SuggestionContext context, String keyId, int n) {
    return context.index().valueCounts(keyId).entrySet().stream()
        .filter(entry -> entry.getValue() > 0)
        .map(entry -> new ValueMatch(entry.getKey(), entry.getValue()))
        .sorted(
            Comparator.comparingInt(ValueMatch::tier).reversed().thenComparing(ValueMatch::value))
        .limit(n)
        .collect(toImmutableList());
  }

  // ---- Small helpers ----

  /** Whether the key's display name or bare name starts with the normalized term. */
  private static boolean nameStartsWith(
      KeyVocabulary vocabulary, KeyDescriptor key, String normalizedTerm) {
    return KeyTokens.normalize(vocabulary.titleDisplayName(key)).startsWith(normalizedTerm)
        || KeyTokens.normalize(key.bareName()).startsWith(normalizedTerm);
  }

  /** Whether the key's display name or bare name contains the normalized term. */
  private static boolean nameContains(
      KeyVocabulary vocabulary, KeyDescriptor key, String normalizedTerm) {
    return KeyTokens.normalize(vocabulary.titleDisplayName(key)).contains(normalizedTerm)
        || KeyTokens.normalize(key.bareName()).contains(normalizedTerm);
  }

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
    return Arrays.stream(raw.split(",", -1))
        .map(String::trim)
        .filter(part -> !part.isEmpty())
        .map(Ascii::toLowerCase)
        .collect(toImmutableList());
  }

  private static boolean hasAllValues(
      FleetIndex index, String keyId, ImmutableList<String> valuesLower) {
    return !valuesLower.isEmpty()
        && valuesLower.stream().allMatch(value -> index.valueCount(keyId, value) > 0);
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

  /** A matched value with its match-quality tier; for top values the tier carries the count. */
  private record ValueMatch(String value, int tier) {}

  /** A matched key with its match-quality tier. */
  private record KeyMatch(KeyDescriptor key, double tier) {}
}
