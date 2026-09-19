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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.primitives.Booleans.falseFirst;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetAddGroupBy;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetApplyFilter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterChipMetadata;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetNewChip;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetOpenPicker;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetStagedModification;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestion;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetViewExisting;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.NoValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TextSegment;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.KeyCount;
import com.google.devtools.mobileharness.fe.v6.service.search.index.Postings;
import com.google.devtools.mobileharness.fe.v6.service.search.index.ValueKeyPair;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Pure, entity-agnostic query parsing, syntax dispatch, and candidate ranking engine for fleet
 * search suggestions.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Search suggestion generation consists of two distinct layers:
 *
 * <ol>
 *   <li><b>Query syntax and candidate ranking</b>: Intent parsing (KV, negation, empty-checks,
 *       multi-value OR, group-by), posting intersection, match-quality scoring, and protobuf
 *       response assembly. These algorithms are identical for all search entities.
 *   <li><b>Entity key domain</b>: Alias dictionaries, explicit namespace prefixes, dynamic key
 *       minting, and dimension catalog discovery. These rules differ completely between devices and
 *       hosts.
 * </ol>
 *
 * <p>This engine isolates layer 1. It contains zero entity-specific knowledge, zero static alias
 * maps, and zero {@code instanceof} checks, delegating all key semantics strictly to an injected
 * {@link EntityKeyStrategy}.
 *
 * <h2>How to use it</h2>
 *
 * <p>Injected as a singleton into {@link DeviceSuggester} and {@link HostSuggester}. Each entity
 * suggester passes its corresponding {@link EntityKeyStrategy} along with the search corpus and
 * incoming request:
 *
 * <pre>{@code
 * engine.suggest(corpus, request, deviceKeyStrategy);
 * }</pre>
 *
 * <h2>Pipeline lifecycle</h2>
 *
 * <ol>
 *   <li><b>Phase 1 (Intent Dispatch)</b>: Classifies input text into an {@link IntentPattern} via
 *       regular expression precedence matching.
 *   <li><b>Phase 2 (Candidate Generation)</b>: Generates candidate suggestions using the strategy's
 *       key resolution, index posting lookups, and dynamic long-tail fallbacks.
 *   <li><b>Phase 3 (Filtered Estimation)</b>: Under active filter chips, calculates exact matching
 *       record counts by intersecting postings with the filtered subset bitset.
 *   <li><b>Phase 4 (Ranking and Deduplication)</b>: Sorts candidates by key priority, match-quality
 *       tier, personalization, and matched record count, deduplicating by label and text.
 * </ol>
 */
@Singleton
public final class SuggesterEngine {

  private static final int GROUP_SUGGEST_MIN = 2;
  private static final int GROUP_SUGGEST_MAX = 50;

  private static final ImmutableList<String> GROUP_BY_PREFIXES =
      ImmutableList.of("grouped by", "group by", "groupby", "group");

  private static final int DEFAULT_LIMIT = 12;
  private static final int TOP_VALUE_KEYS = 5;
  private static final int TOP_VALUES_PER_KEY = 3;
  private static final int KEY_VALUES_PER_KEY = 8;
  private static final int MAX_SEMANTIC_SCAN = 500;

  private static final ImmutableSet<String> PERSONALIZED_KEYS = ImmutableSet.of();

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern EMPTY_NO = Pattern.compile("^no[ _]+(.+)$");
  private static final Pattern EMPTY_HAS = Pattern.compile("^has[ _]+(.+)$");
  private static final Pattern NON_EMPTY =
      Pattern.compile("^(.+?)[ _]+(?:is|are)[ _]+not[ _]+empty$");
  private static final Pattern NON_EMPTY_CONTRACTED =
      Pattern.compile("^(.+?)[ _]+(?:isn't|aren't)[ _]+empty$");
  private static final Pattern NON_EMPTY_BARE = Pattern.compile("^(.+?)[ _]+not[ _]+empty$");
  private static final Pattern IS_EMPTY = Pattern.compile("^(.+?)[ _]+(?:is|are)[ _]+empty$");
  private static final Pattern IS_EMPTY_BARE = Pattern.compile("^(.+?)[ _]+empty$");
  private static final Pattern KV_NEGATE =
      Pattern.compile(
          "^(.+?)[ _]+(?:is[ _]+not|isn't|are[ _]+not|aren't"
              + "|do(?:es)?[ _]+not[ _]+includes?|do(?:es)?n't[ _]+includes?)[ _]+(.+)$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern KV_NEGATE_SYMBOL = Pattern.compile("^(.+?)[ _]*!=[ _]*(.+)$");
  private static final Pattern KEY_NEGATE_ONLY =
      Pattern.compile(
          "^(.+?)[ _]+(?:is[ _]+not|isn't|are[ _]+not|aren't"
              + "|do(?:es)?[ _]+not[ _]+includes?|do(?:es)?n't[ _]+includes?)$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern KEY_NEGATE_ONLY_SYMBOL = Pattern.compile("^(.+?)[ _]*!=\\s*$");
  private static final Pattern KEY_ONLY =
      Pattern.compile("^(.+?)[ _]+(?:is|are|includes?)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern KEY_ONLY_SYMBOL = Pattern.compile("^(.+?)[ _]*[:=]\\s*$");
  private static final Pattern KV =
      Pattern.compile("^(.+?)[ _]+(?:is|are|includes?)[ _]+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern KV_SYMBOL = Pattern.compile("^(.+?)[ _]*[:=][ _]*(.+)$");
  private static final Pattern BARE_NEGATE = Pattern.compile("^not\\s+(.+)$");

  private final FleetFilterEngine filterEngine;

  @Inject
  SuggesterEngine(FleetFilterEngine filterEngine) {
    this.filterEngine = checkNotNull(filterEngine);
  }

  /** Generates ranked suggestions for the request using the supplied entity key strategy. */
  public FleetSuggestionResponse suggest(
      SearchCorpus corpus, FleetSuggestionRequest request, EntityKeyStrategy strategy) {
    int limit = request.getLimit() > 0 ? request.getLimit() : DEFAULT_LIMIT;
    String query = WHITESPACE.matcher(request.getInput()).replaceAll(" ").trim();

    FleetIndex index = corpus.index();
    List<Filter> filters = request.getFiltersList();
    boolean hasFilters = !filters.isEmpty();
    Set<String> activeKeys = new HashSet<>();
    for (Filter filter : filters) {
      activeKeys.add(filter.getKey());
    }
    ImmutableList<Integer> current = filterEngine.match(corpus, filters);
    BitSet currentBits = toBitSet(current);

    Fleet fleet =
        request.getFleet() == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : request.getFleet();
    Context context =
        new Context(
            fleet,
            corpus,
            strategy,
            index,
            filters,
            hasFilters,
            activeKeys,
            current,
            currentBits,
            corpus.postings());

    if (query.isEmpty()) {
      return FleetSuggestionResponse.getDefaultInstance();
    }

    Optional<String> groupByTerm = groupByPrefix(query);
    if (groupByTerm.isPresent()) {
      return suggestGroupBy(context, groupByTerm.get(), request.getGroupByList(), limit);
    }

    Intent intent = dispatch(query);
    List<Cand> raw =
        switch (intent.pattern()) {
          case KV -> suggestKv(context, intent.key(), intent.value(), /* exclude= */ false);
          case KV_NEGATE -> suggestKv(context, intent.key(), intent.value(), /* exclude= */ true);
          case EMPTY -> suggestEmpty(context, intent.key(), intent.empty());
          case KEY_VALUES -> suggestKeyValues(context, intent.key(), intent.exclude());
          case BARE_NEGATE ->
              suggestValue(context, intent.value(), /* exclude= */ true, /* multi= */ null);
          case COMMA ->
              suggestValue(context, intent.value(), /* exclude= */ false, intent.values());
          case TOKEN -> {
            List<Cand> both = new ArrayList<>(suggestKey(context, query));
            if (query.length() >= 3) {
              both.addAll(suggestValue(context, query, /* exclude= */ false, /* multi= */ null));
            }
            yield both;
          }
        };

    return rank(context, raw, limit);
  }

  private static Intent dispatch(String query) {
    String low = Ascii.toLowerCase(query);

    Matcher m = EMPTY_NO.matcher(low);
    if (m.matches()) {
      return Intent.empty(m.group(1), /* empty= */ true);
    }
    m = EMPTY_HAS.matcher(low);
    if (m.matches()) {
      return Intent.empty(m.group(1), /* empty= */ false);
    }
    if ((m = firstMatch(low, NON_EMPTY, NON_EMPTY_CONTRACTED, NON_EMPTY_BARE)) != null) {
      return Intent.empty(m.group(1), /* empty= */ false);
    }
    if ((m = firstMatch(low, IS_EMPTY, IS_EMPTY_BARE)) != null) {
      return Intent.empty(m.group(1), /* empty= */ true);
    }
    m = KV_NEGATE.matcher(query);
    if (m.matches()) {
      return Intent.kv(IntentPattern.KV_NEGATE, m.group(1), m.group(2));
    }
    m = KV_NEGATE_SYMBOL.matcher(query);
    if (m.matches()) {
      return Intent.kv(IntentPattern.KV_NEGATE, m.group(1), m.group(2));
    }
    m = KEY_NEGATE_ONLY.matcher(low);
    if (m.matches()) {
      return Intent.keyValues(m.group(1), /* exclude= */ true);
    }
    m = KEY_NEGATE_ONLY_SYMBOL.matcher(query);
    if (m.matches()) {
      return Intent.keyValues(m.group(1), /* exclude= */ true);
    }
    m = KEY_ONLY.matcher(low);
    if (m.matches()) {
      return Intent.keyValues(m.group(1), /* exclude= */ false);
    }
    m = KEY_ONLY_SYMBOL.matcher(query);
    if (m.matches()) {
      return Intent.keyValues(m.group(1), /* exclude= */ false);
    }
    m = KV.matcher(query);
    if (m.matches()) {
      return Intent.kv(IntentPattern.KV, m.group(1), m.group(2));
    }
    m = KV_SYMBOL.matcher(query);
    if (m.matches()) {
      return Intent.kv(IntentPattern.KV, m.group(1), m.group(2));
    }
    m = BARE_NEGATE.matcher(low);
    if (m.matches()) {
      return Intent.bareNegate(m.group(1));
    }
    if (query.contains(",")) {
      ImmutableList.Builder<String> parts = ImmutableList.builder();
      for (String part : query.split(",", -1)) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) {
          parts.add(trimmed);
        }
      }
      return Intent.comma(query, parts.build());
    }
    return Intent.token();
  }

  @Nullable
  private static Matcher firstMatch(String input, Pattern... patterns) {
    for (Pattern pattern : patterns) {
      Matcher m = pattern.matcher(input);
      if (m.matches()) {
        return m;
      }
    }
    return null;
  }

  private List<Cand> suggestKv(Context context, String keyToken, String rawValue, boolean exclude) {
    List<Cand> out = new ArrayList<>();
    ImmutableList<String> keyIds = context.strategy().resolveKey(context.corpus(), keyToken);
    if (keyIds.isEmpty()) {
      Optional<String> dynamicKey = context.strategy().synthesizeDynamicKeyId(keyToken);
      if (dynamicKey.isPresent()) {
        keyIds = ImmutableList.of(dynamicKey.get());
      }
    }

    if (rawValue.contains(",") && !rawValue.contains("\"") && !rawValue.contains("'")) {
      ImmutableList<String> parts = splitCommaLower(rawValue);
      if (parts.isEmpty()) {
        return out;
      }
      for (String keyId : keyIds) {
        addMultiValueOr(context, out, keyId, parts, exclude);
      }
      if (out.isEmpty() && !keyIds.isEmpty() && !parts.isEmpty()) {
        for (String keyId : keyIds) {
          if (context.strategy().isColdLongTailKey(context.corpus(), keyId)
              && !context.activeKeys().contains(keyId)) {
            String display = context.strategy().displayName(context.corpus(), keyId);
            String verb = context.strategy().isPlural(context.corpus(), keyId) ? "are" : "is";
            if (exclude) {
              verb += " not";
            }
            String joined = String.join(", ", parts);
            ImmutableList<TextSegment> mainText = segments(display + " " + verb + " ", joined);
            SimpleMatch.Builder simpleBuilder = SimpleMatch.newBuilder().setNegated(exclude);
            for (String part : parts) {
              simpleBuilder.addValues(FilterValue.newBuilder().setValue(part));
            }
            Filter filter = Filter.newBuilder().setKey(keyId).setSimple(simpleBuilder).build();
            FleetSuggestion.Builder builder =
                FleetSuggestion.newBuilder()
                    .setLabel("Add filter")
                    .addAllMainText(mainText)
                    .setApplyFilter(
                        applyFilter(
                            context.corpus(), context.strategy(), context.index(), keyId, filter));
            Cand cand = new Cand(Kind.CONDITION, keyId, 1.0, builder, mainTextString(mainText));
            cand.needsCount = false;
            cand.noCount = true;
            out.add(cand);
          }
        }
      }
      return out;
    }

    String value = Ascii.toLowerCase(stripQuotes(rawValue.trim()));
    for (String keyId : keyIds) {
      if (!context.strategy().isKeyKnown(context.corpus(), keyId)) {
        continue;
      }
      boolean hadMatches = false;
      for (Match match :
          matchValues(context.corpus(), context.index(), keyId, value, /* allowContains= */ true)) {
        Cand cand = condition(context, keyId, match.value(), match.tier(), exclude);
        if (cand != null) {
          out.add(cand);
          hadMatches = true;
        }
      }
      if (!hadMatches
          && !value.isEmpty()
          && context.strategy().isColdLongTailKey(context.corpus(), keyId)
          && !context.activeKeys().contains(keyId)) {
        String display = context.strategy().displayName(context.corpus(), keyId);
        String op = exclude ? "is not" : "is";
        ImmutableList<TextSegment> mainText = segments(display + " " + op + " ", rawValue.trim());
        Filter filter =
            Filter.newBuilder()
                .setKey(keyId)
                .setSimple(
                    SimpleMatch.newBuilder()
                        .addValues(FilterValue.newBuilder().setValue(rawValue.trim()))
                        .setNegated(exclude))
                .build();
        FleetSuggestion.Builder builder =
            FleetSuggestion.newBuilder()
                .setLabel("Add filter")
                .addAllMainText(mainText)
                .setApplyFilter(
                    applyFilter(
                        context.corpus(), context.strategy(), context.index(), keyId, filter));
        Cand cand = new Cand(Kind.CONDITION, keyId, 1.0, builder, mainTextString(mainText));
        cand.needsCount = false;
        cand.noCount = true;
        out.add(cand);
      }
    }
    return out;
  }

  private List<Cand> suggestEmpty(Context context, String keyToken, boolean empty) {
    List<Cand> out = new ArrayList<>();
    FleetIndex index = context.index();
    int globalTotal = context.corpus().recordCount();
    for (String keyId : context.strategy().resolveKey(context.corpus(), keyToken)) {
      if (!index.keyIds().contains(keyId)
          || !context.strategy().isKeyKnown(context.corpus(), keyId)) {
        continue;
      }

      int globalPresent = devicesWithKey(context.postings(), keyId).cardinality();
      if (empty) {
        if (globalTotal - globalPresent <= 0) {
          continue;
        }
      } else {
        if (globalPresent <= 0 || globalPresent == globalTotal) {
          continue;
        }
      }

      int base = context.hasFilters() ? context.current().size() : globalTotal;
      int present = presenceCount(context, keyId);
      int count = empty ? (base - present) : present;
      if (count <= 0) {
        continue;
      }

      String display = context.strategy().displayName(context.corpus(), keyId);
      boolean isPlural = context.strategy().isPlural(context.corpus(), keyId);
      String verb = isPlural ? "are" : "is";
      Filter filter;
      ImmutableList<TextSegment> mainText;
      if (empty) {
        mainText = segments(display + " " + verb + " ", "empty");
        filter = noValueFilter(keyId, /* negated= */ false);
      } else {
        mainText = segments(display + " " + verb + " ", "not empty");
        filter = noValueFilter(keyId, /* negated= */ true);
      }
      boolean inChip = context.activeKeys().contains(keyId);
      FleetSuggestion.Builder builder =
          FleetSuggestion.newBuilder()
              .setLabel(label(context.corpus(), context.strategy(), keyId, inChip))
              .addAllMainText(mainText);
      if (inChip) {
        builder.setOpenPicker(openPickerViewExisting(context.corpus(), context.strategy(), keyId));
      } else {
        builder.setApplyFilter(
            applyFilter(context.corpus(), context.strategy(), index, keyId, filter));
      }

      double tier = 3.0;
      int rankCount = count;
      if (context.hasFilters() && count == base) {
        tier = 2.0;
        rankCount = 1;
      }

      Cand cand = new Cand(Kind.CONDITION, keyId, tier, builder, mainTextString(mainText));
      cand.rankCount = rankCount;
      cand.count = count;
      cand.needsCount = false;
      out.add(cand);
    }
    return out;
  }

  private List<Cand> suggestKeyValues(Context context, String keyToken, boolean exclude) {
    List<Cand> out = new ArrayList<>();
    for (String keyId : context.strategy().resolveKey(context.corpus(), keyToken)) {
      for (Match match : topValues(context, keyId, KEY_VALUES_PER_KEY)) {
        Cand cand = condition(context, keyId, match.value(), 3, exclude);
        if (cand != null) {
          out.add(cand);
        }
      }
    }
    return out;
  }

  private List<Cand> suggestValue(
      Context context, String token, boolean exclude, ImmutableList<String> multi) {
    FleetIndex index = context.index();

    if (multi != null) {
      List<Cand> out = new ArrayList<>();
      ImmutableList.Builder<String> lowered = ImmutableList.builder();
      for (String value : multi) {
        lowered.add(Ascii.toLowerCase(stripQuotes(value)));
      }
      ImmutableList<String> values = lowered.build();
      for (String keyId : index.keyIds()) {
        if (!context.strategy().isKeyKnown(context.corpus(), keyId)) {
          continue;
        }
        if (hasAllValues(index, keyId, values)) {
          addMultiValueOr(context, out, keyId, values, exclude);
        }
      }
      return out;
    }

    String value = Ascii.toLowerCase(stripQuotes(token));
    List<Cand> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    for (KeyCount kc : index.globalExact().getOrDefault(value, ImmutableList.of())) {
      if (seen.add(kc.key() + '\u0000' + value)) {
        Cand cand = condition(context, kc.key(), value, 3, exclude);
        if (cand != null) {
          out.add(cand);
        }
      }
    }

    ImmutableList<ValueKeyPair> sorted = index.semanticGlobalSorted();
    int lo = lowerBoundVkp(sorted, value);
    int hi = lowerBoundVkp(sorted, value + '\uffff');
    int scanned = 0;
    for (int i = lo; i < hi && scanned < MAX_SEMANTIC_SCAN; i++) {
      ValueKeyPair pair = sorted.get(i);
      if (!pair.value().equals(value) && seen.add(pair.key() + '\u0000' + pair.value())) {
        Cand cand = condition(context, pair.key(), pair.value(), 2, exclude);
        if (cand != null) {
          out.add(cand);
          scanned++;
        }
      }
    }

    for (String identKey : index.keyIds()) {
      if (!context.corpus().isIdentifierKey(identKey)) {
        continue;
      }
      ImmutableList<String> keyValues = index.sortedValues(identKey);
      if (keyValues.isEmpty()) {
        continue;
      }
      int ilo = FleetFilterEngine.lowerBound(keyValues, value);
      int ihi = FleetFilterEngine.lowerBound(keyValues, value + '\uffff');
      int matchCount = ihi - ilo;
      if (matchCount > 0) {
        String identDisplay = context.strategy().displayName(context.corpus(), identKey);
        ImmutableList<TextSegment> mainText =
            ImmutableList.of(
                text(identDisplay + " starts with ", false),
                text(token, true),
                text(" (" + matchCount + ")", false));
        FleetSuggestion.Builder builder =
            FleetSuggestion.newBuilder()
                .setLabel("Add filter")
                .addAllMainText(mainText)
                .setCount(matchCount)
                .setOpenPicker(openPickerNewChip(context.corpus(), context.strategy(), identKey));
        Cand cand = new Cand(Kind.KEY, identKey, 1, builder, mainTextString(mainText));
        cand.needsCount = false;
        cand.noCount = true;
        out.add(cand);
      }
    }

    return out;
  }

  private List<Cand> suggestKey(Context context, String token) {
    List<Cand> out = new ArrayList<>();
    List<KeyMatch> matched = matchKeyIds(context, token);
    int rank = 0;
    for (KeyMatch keyMatch : matched) {
      String keyId = keyMatch.keyId();
      double tier = keyMatch.tier();
      if (rank < TOP_VALUE_KEYS) {
        for (Match value : topValues(context, keyId, TOP_VALUES_PER_KEY)) {
          Cand cand = condition(context, keyId, value.value(), tier, /* exclude= */ false);
          if (cand != null) {
            out.add(cand);
          }
        }
      }
      boolean inChip = context.activeKeys().contains(keyId);
      String display = context.strategy().displayName(context.corpus(), keyId);
      ImmutableList<TextSegment> mainText = segments(display, null);
      FleetSuggestion.Builder builder =
          FleetSuggestion.newBuilder()
              .setLabel(label(context.corpus(), context.strategy(), keyId, inChip))
              .addAllMainText(mainText)
              .setOpenPicker(
                  inChip
                      ? openPickerViewExisting(context.corpus(), context.strategy(), keyId)
                      : openPickerNewChip(context.corpus(), context.strategy(), keyId));
      Cand cand = new Cand(Kind.KEY, keyId, tier - 0.4, builder, mainTextString(mainText));
      cand.needsCount = false;
      cand.noCount = true;
      out.add(cand);
      rank++;
    }
    return out;
  }

  private FleetSuggestionResponse suggestGroupBy(
      Context context, String term, List<String> applied, int limit) {
    FleetIndex index = context.index();
    if (applied.size() >= 3) {
      return FleetSuggestionResponse.getDefaultInstance();
    }
    Set<String> appliedSet = new HashSet<>(applied);

    List<String> candidates = new ArrayList<>();
    Map<String, Integer> matchRank = new HashMap<>();
    if (term.isEmpty()) {
      candidates.addAll(context.strategy().groupByCandidates(context.corpus(), context.fleet()));
    } else {
      String normTerm = normalize(term);
      for (String keyId : context.strategy().resolveKey(context.corpus(), term)) {
        matchRank.put(keyId, 0);
      }
      for (String keyId : index.keyIds()) {
        if (matchRank.containsKey(keyId)
            || !context.strategy().isKeyKnown(context.corpus(), keyId)) {
          continue;
        }
        String display = normalize(context.strategy().displayName(context.corpus(), keyId));
        String bare = normalize(context.strategy().bareName(context.corpus(), keyId));
        if (display.startsWith(normTerm) || bare.startsWith(normTerm)) {
          matchRank.put(keyId, 1);
        } else if (display.contains(normTerm) || bare.contains(normTerm)) {
          matchRank.put(keyId, 2);
        }
      }
      candidates.addAll(matchRank.keySet());
    }

    List<Cand> out = new ArrayList<>();
    for (String keyId : candidates) {
      if (!index.keyIds().contains(keyId)
          || appliedSet.contains(keyId)
          || !context.strategy().isKeyKnown(context.corpus(), keyId)) {
        continue;
      }
      int groups = groupCount(context, keyId);
      if (groups < GROUP_SUGGEST_MIN) {
        continue;
      }
      boolean overMax = groups > GROUP_SUGGEST_MAX;
      String display = context.strategy().displayName(context.corpus(), keyId);
      ImmutableList<TextSegment> mainText = segments("", display);
      FleetSuggestion.Builder builder =
          FleetSuggestion.newBuilder()
              .setLabel("Group by")
              .addAllMainText(mainText)
              .setCount(groups)
              .setCountUnit("groups")
              .setOverMax(overMax)
              .setAddGroupBy(addGroupBy(context.corpus(), context.strategy(), keyId));
      Cand cand = new Cand(Kind.GROUP_BY, keyId, 0, builder, mainTextString(mainText));
      cand.groupRank = matchRank.getOrDefault(keyId, 0);
      cand.overMax = overMax;
      cand.count = groups;
      out.add(cand);
    }

    out.sort(
        Comparator.<Cand>comparingInt(
                c -> -context.strategy().keyPriority(context.corpus(), context.fleet(), c.keyId))
            .thenComparingInt(c -> c.groupRank)
            .thenComparing(c -> c.overMax, falseFirst())
            .thenComparingInt(c -> c.count == null ? 0 : c.count));

    FleetSuggestionResponse.Builder response = FleetSuggestionResponse.newBuilder();
    for (Cand cand : out.subList(0, Math.min(out.size(), limit))) {
      response.addItems(cand.builder.build());
    }
    return response.build();
  }

  @Nullable
  private Cand condition(
      Context context, String keyId, String valueLower, double tier, boolean exclude) {
    FleetIndex index = context.index();
    if (!index.keyIds().contains(keyId)
        || !context.strategy().isKeyKnown(context.corpus(), keyId)) {
      return null;
    }
    int global = index.valueCount(keyId, valueLower);
    if (global == 0) {
      return null;
    }
    boolean inChip = context.activeKeys().contains(keyId);

    if (inChip) {
      Filter chip = chipFor(context.filters(), keyId);
      if (chip != null) {
        if (chip.getModeCase() == Filter.ModeCase.COMPLEX) {
          return null;
        }
        if (chip.getModeCase() == Filter.ModeCase.SIMPLE) {
          boolean chipNegated = chip.getSimple().getNegated();
          if (exclude != chipNegated) {
            return null;
          }
        }
      }
    }

    String display = context.strategy().displayName(context.corpus(), keyId);
    String shown = displayValue(index, keyId, valueLower);
    FleetSuggestion.Builder builder = FleetSuggestion.newBuilder();
    ImmutableList<TextSegment> mainText;
    if (inChip) {
      String verb = exclude ? "exclude " : "add ";
      mainText = segments(verb, shown);
      builder.setOpenPicker(
          openPickerStaged(context.corpus(), context.strategy(), keyId, ImmutableList.of(shown)));
    } else {
      String op =
          context.strategy().isPlural(context.corpus(), keyId)
              ? (exclude ? "are not" : "are")
              : (exclude ? "is not" : "is");
      mainText = segments(display + " " + op + " ", shown);
      Filter filter = valueFilter(keyId, ImmutableList.of(shown), exclude);
      builder.setApplyFilter(
          applyFilter(context.corpus(), context.strategy(), index, keyId, filter));
    }
    builder
        .setLabel(label(context.corpus(), context.strategy(), keyId, inChip))
        .addAllMainText(mainText);

    Cand cand = new Cand(Kind.CONDITION, keyId, tier, builder, mainTextString(mainText));
    cand.value = valueLower;
    cand.exclude = exclude;
    cand.inChip = inChip;
    cand.rankCount = global;
    cand.needsCount = true;
    return cand;
  }

  private void addMultiValueOr(
      Context context,
      List<Cand> out,
      String keyId,
      ImmutableList<String> valuesLower,
      boolean exclude) {
    FleetIndex index = context.index();
    if (!index.keyIds().contains(keyId)
        || !context.strategy().isKeyKnown(context.corpus(), keyId)) {
      return;
    }
    ImmutableList.Builder<String> presentBuilder = ImmutableList.builder();
    for (String value : valuesLower) {
      if (index.valueCount(keyId, value) > 0) {
        presentBuilder.add(value);
      }
    }
    ImmutableList<String> present = presentBuilder.build();
    if (present.isEmpty()) {
      return;
    }
    ImmutableList.Builder<String> shownBuilder = ImmutableList.builder();
    for (String value : present) {
      shownBuilder.add(displayValue(index, keyId, value));
    }
    ImmutableList<String> shown = shownBuilder.build();

    int orCount = unionCount(context, keyId, present);
    boolean inChip = context.activeKeys().contains(keyId);
    String display = context.strategy().displayName(context.corpus(), keyId);
    String verb = context.strategy().isPlural(context.corpus(), keyId) ? "are" : "is";
    if (exclude) {
      verb += " not";
    }
    List<TextSegment> segmentList = new ArrayList<>();
    if (inChip) {
      segmentList.add(text("add ", false));
    } else {
      segmentList.add(text(display + " " + verb + " ", false));
    }
    for (int i = 0; i < shown.size(); i++) {
      if (i > 0) {
        segmentList.add(text(" or ", false));
      }
      segmentList.add(text(shown.get(i), true));
    }
    ImmutableList<TextSegment> mainText = ImmutableList.copyOf(segmentList);
    FleetSuggestion.Builder builder =
        FleetSuggestion.newBuilder()
            .setLabel(label(context.corpus(), context.strategy(), keyId, inChip))
            .addAllMainText(mainText);
    if (inChip) {
      builder.setOpenPicker(openPickerStaged(context.corpus(), context.strategy(), keyId, shown));
    } else {
      builder.setApplyFilter(
          applyFilter(
              context.corpus(),
              context.strategy(),
              index,
              keyId,
              valueFilter(keyId, shown, exclude)));
    }
    Cand cand = new Cand(Kind.CONDITION, keyId, 3, builder, mainTextString(mainText));
    cand.rankCount = orCount;
    cand.count = orCount > 0 ? orCount : null;
    cand.needsCount = false;
    out.add(cand);
  }

  private FleetSuggestionResponse rank(Context context, List<Cand> raw, int limit) {
    Set<String> seen = new HashSet<>();
    List<Cand> uniq = new ArrayList<>();
    for (Cand cand : raw) {
      if (cand == null) {
        continue;
      }
      String sig = cand.builder.getLabel() + '\u0000' + cand.mainString;
      if (seen.add(sig)) {
        uniq.add(cand);
      }
    }

    uniq.sort(
        Comparator.<Cand>comparingInt(
                c -> -context.strategy().keyPriority(context.corpus(), context.fleet(), c.keyId))
            .thenComparingInt(c -> -(int) Math.round(c.tier))
            .thenComparingInt(c -> -(PERSONALIZED_KEYS.contains(c.keyId) ? 1 : 0))
            .thenComparingInt(c -> -c.rankCount)
            .thenComparing(c -> c.mainString));

    FleetSuggestionResponse.Builder response = FleetSuggestionResponse.newBuilder();
    if (!context.hasFilters()) {
      for (Cand cand : uniq.subList(0, Math.min(uniq.size(), limit))) {
        applyCount(context, cand);
        response.addItems(cand.build());
      }
      return response.build();
    }
    for (Cand cand : uniq) {
      applyCount(context, cand);
    }
    uniq.sort(
        Comparator.<Cand>comparingInt(
                c -> -context.strategy().keyPriority(context.corpus(), context.fleet(), c.keyId))
            .thenComparingInt(c -> -(int) Math.round(c.tier))
            .thenComparingInt(c -> -(PERSONALIZED_KEYS.contains(c.keyId) ? 1 : 0))
            .thenComparingInt(c -> -(c.count != null ? c.count : 0))
            .thenComparing(c -> c.mainString));
    int kept = 0;
    for (Cand cand : uniq) {
      if (cand.kind == Kind.CONDITION && !cand.noCount && cand.count == null) {
        continue;
      }
      response.addItems(cand.build());
      if (++kept >= limit) {
        break;
      }
    }
    return response.build();
  }

  private void applyCount(Context context, Cand cand) {
    if (!cand.needsCount) {
      return;
    }
    int base = context.hasFilters() ? context.current().size() : context.corpus().recordCount();

    if (cand.inChip && !cand.exclude) {
      BitSet baseK =
          toBitSet(
              filterEngine.match(context.corpus(), otherFilters(context.filters(), cand.keyId)));
      int added = 0;
      for (int deviceIndex : context.postings().get(cand.keyId, cand.value)) {
        if (baseK.get(deviceIndex) && !context.currentBits().get(deviceIndex)) {
          added++;
        }
      }
      cand.count = added > 0 ? added : null;
      cand.countPrefix = "+";
      return;
    }

    int matches = resultingCount(context, cand.keyId, cand.value);
    if (cand.exclude) {
      int resulting = base - matches;
      cand.count = resulting > 0 ? resulting : null;
    } else {
      cand.count = matches > 0 ? matches : null;
    }
  }

  private static int resultingCount(Context context, String keyId, String valueLower) {
    if (!context.hasFilters()) {
      return context.index().valueCount(keyId, valueLower);
    }
    return intersectionCount(context.postings().get(keyId, valueLower), context.currentBits());
  }

  private static int unionCount(Context context, String keyId, ImmutableList<String> valuesLower) {
    BitSet union = new BitSet();
    for (String value : valuesLower) {
      for (int deviceIndex : context.postings().get(keyId, value)) {
        union.set(deviceIndex);
      }
    }
    if (context.hasFilters()) {
      union.and(context.currentBits());
    }
    return union.cardinality();
  }

  private static int presenceCount(Context context, String keyId) {
    BitSet withKey = devicesWithKey(context.postings(), keyId);
    if (context.hasFilters()) {
      withKey.and(context.currentBits());
    }
    return withKey.cardinality();
  }

  private static List<KeyMatch> matchKeyIds(Context context, String token) {
    FleetIndex index = context.index();
    List<KeyMatch> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String keyId : context.strategy().resolveKey(context.corpus(), token)) {
      if (context.strategy().isKeyKnown(context.corpus(), keyId) && seen.add(keyId)) {
        out.add(new KeyMatch(keyId, 3));
      }
    }
    String normTerm = normalize(token);
    if (normTerm.isEmpty()) {
      return out;
    }
    for (String keyId : index.keyIds()) {
      if (seen.contains(keyId) || !context.strategy().isKeyKnown(context.corpus(), keyId)) {
        continue;
      }
      String display = normalize(context.strategy().displayName(context.corpus(), keyId));
      String bare = normalize(context.strategy().bareName(context.corpus(), keyId));
      if (display.startsWith(normTerm) || bare.startsWith(normTerm)) {
        out.add(new KeyMatch(keyId, 2));
        seen.add(keyId);
      }
    }
    for (String keyId : context.strategy().matchDiscoveredPrefixKeys(context.fleet(), normTerm)) {
      if (seen.add(keyId)) {
        out.add(new KeyMatch(keyId, 2));
      }
    }
    for (String keyId : index.keyIds()) {
      if (seen.contains(keyId) || !context.strategy().isKeyKnown(context.corpus(), keyId)) {
        continue;
      }
      String display = normalize(context.strategy().displayName(context.corpus(), keyId));
      String bare = normalize(context.strategy().bareName(context.corpus(), keyId));
      if (display.contains(normTerm) || bare.contains(normTerm)) {
        out.add(new KeyMatch(keyId, 1));
        seen.add(keyId);
      }
    }
    for (String keyId : context.strategy().matchDiscoveredContainsKeys(context.fleet(), normTerm)) {
      if (seen.add(keyId)) {
        out.add(new KeyMatch(keyId, 1));
      }
    }
    return out;
  }

  private static int groupCount(Context context, String keyId) {
    Set<String> combos = new HashSet<>();
    boolean hasMissing = false;
    for (int deviceIndex : context.current()) {
      ImmutableSet<String> values = context.corpus().valuesForKey(deviceIndex, keyId);
      if (values.isEmpty()) {
        hasMissing = true;
      } else {
        combos.add(String.join("\u0000", new TreeSet<>(values)));
      }
    }
    return combos.size() + (hasMissing ? 1 : 0);
  }

  private static ImmutableList<Match> matchValues(
      SearchCorpus corpus, FleetIndex index, String keyId, String query, boolean allowContains) {
    ImmutableList<String> sorted = index.sortedValues(keyId);
    if (sorted.isEmpty() || query.isEmpty()) {
      return ImmutableList.of();
    }
    ImmutableMap<String, Integer> counts = index.valueCounts(keyId);
    List<Match> out = new ArrayList<>();
    Set<String> exactHits = new HashSet<>();
    for (String variant : new String[] {query, query.replace(' ', '_'), query.replace('_', ' ')}) {
      if (counts.containsKey(variant) && exactHits.add(variant)) {
        out.add(new Match(variant, 3));
      }
    }
    int lo = FleetFilterEngine.lowerBound(sorted, query);
    int hi = FleetFilterEngine.lowerBound(sorted, query + '\uffff');
    for (int i = lo; i < hi; i++) {
      String value = sorted.get(i);
      if (!exactHits.contains(value)) {
        out.add(new Match(value, 2));
      }
    }
    if (allowContains && !corpus.isIdentifierKey(keyId) && out.size() < KEY_VALUES_PER_KEY) {
      for (String value : sorted) {
        if (value.contains(query) && !value.startsWith(query) && !value.equals(query)) {
          out.add(new Match(value, 1));
          if (out.size() >= KEY_VALUES_PER_KEY * 3) {
            break;
          }
        }
      }
    }
    return ImmutableList.copyOf(out);
  }

  private static List<Match> topValues(Context context, String keyId, int n) {
    ImmutableMap<String, Integer> counts = context.index().valueCounts(keyId);
    List<Match> all = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
      if (entry.getValue() > 0) {
        all.add(new Match(entry.getKey(), entry.getValue()));
      }
    }
    all.sort(Comparator.<Match>comparingInt(Match::tier).reversed().thenComparing(Match::value));
    return all.subList(0, Math.min(all.size(), n));
  }

  private static FleetApplyFilter applyFilter(
      SearchCorpus corpus,
      EntityKeyStrategy strategy,
      FleetIndex index,
      String keyId,
      Filter filter) {
    return FleetApplyFilter.newBuilder()
        .setResultingFilter(filter)
        .setPillKey(strategy.pillKey(corpus, keyId))
        .setPillCondition(pillCondition(index, filter))
        .setMetadata(metadata(corpus, strategy, keyId))
        .build();
  }

  private static FleetOpenPicker openPickerNewChip(
      SearchCorpus corpus, EntityKeyStrategy strategy, String keyId) {
    return FleetOpenPicker.newBuilder()
        .setKey(keyId)
        .setMetadata(metadata(corpus, strategy, keyId))
        .setNewChip(FleetNewChip.getDefaultInstance())
        .build();
  }

  private static FleetOpenPicker openPickerViewExisting(
      SearchCorpus corpus, EntityKeyStrategy strategy, String keyId) {
    return FleetOpenPicker.newBuilder()
        .setKey(keyId)
        .setMetadata(metadata(corpus, strategy, keyId))
        .setViewExisting(FleetViewExisting.getDefaultInstance())
        .build();
  }

  private static FleetOpenPicker openPickerStaged(
      SearchCorpus corpus, EntityKeyStrategy strategy, String keyId, ImmutableList<String> values) {
    return FleetOpenPicker.newBuilder()
        .setKey(keyId)
        .setMetadata(metadata(corpus, strategy, keyId))
        .setStagedModify(FleetStagedModification.newBuilder().addAllValues(values))
        .build();
  }

  private static FleetAddGroupBy addGroupBy(
      SearchCorpus corpus, EntityKeyStrategy strategy, String keyId) {
    return FleetAddGroupBy.newBuilder()
        .setKey(keyId)
        .setPillKey(strategy.pillKey(corpus, keyId))
        .build();
  }

  private static FleetFilterChipMetadata metadata(
      SearchCorpus corpus, EntityKeyStrategy strategy, String keyId) {
    return FleetFilterChipMetadata.newBuilder()
        .setKeyDisplayName(strategy.displayName(corpus, keyId))
        .setCanUseAdvanced(true)
        .setIsPlural(strategy.isPlural(corpus, keyId))
        .build();
  }

  private static String label(
      SearchCorpus corpus, EntityKeyStrategy strategy, String keyId, boolean inChip) {
    return inChip ? "Modify " + strategy.displayName(corpus, keyId) : "Add filter";
  }

  private static Filter valueFilter(String keyId, ImmutableList<String> values, boolean negated) {
    SimpleMatch.Builder simple = SimpleMatch.newBuilder().setNegated(negated);
    for (String value : values) {
      simple.addValues(FilterValue.newBuilder().setValue(value));
    }
    return Filter.newBuilder().setKey(keyId).setSimple(simple).build();
  }

  private static Filter noValueFilter(String keyId, boolean negated) {
    return Filter.newBuilder()
        .setKey(keyId)
        .setSimple(
            SimpleMatch.newBuilder()
                .setNegated(negated)
                .addValues(FilterValue.newBuilder().setNoValue(NoValue.getDefaultInstance())))
        .build();
  }

  private static String displayValue(FleetIndex index, String keyId, String valueLower) {
    return index.valueDisplays(keyId).getOrDefault(valueLower, valueLower);
  }

  private static String pillCondition(FleetIndex index, Filter filter) {
    if (filter.getModeCase() != Filter.ModeCase.SIMPLE) {
      return "\u2026";
    }
    SimpleMatch simple = filter.getSimple();
    int count = 0;
    String lastDisplay = "";
    boolean lastIsNoValue = false;
    for (FilterValue value : simple.getValuesList()) {
      switch (value.getKindCase()) {
        case VALUE -> {
          count++;
          lastDisplay = displayValue(index, filter.getKey(), Ascii.toLowerCase(value.getValue()));
          lastIsNoValue = false;
        }
        case NO_VALUE -> {
          count++;
          lastIsNoValue = true;
        }
        case KIND_NOT_SET -> {}
      }
    }
    boolean negated = simple.getNegated();
    if (count == 0) {
      return "\u2026";
    }
    if (count == 1) {
      if (lastIsNoValue) {
        return negated ? "not empty" : "empty";
      }
      return negated ? "\u2260 " + lastDisplay : lastDisplay;
    }
    return negated ? "\u2260 " + count : Integer.toString(count);
  }

  private static ImmutableList<TextSegment> segments(String plain, String emphasized) {
    ImmutableList.Builder<TextSegment> out = ImmutableList.builder();
    if (!plain.isEmpty()) {
      out.add(text(plain, false));
    }
    if (emphasized != null) {
      out.add(text(emphasized, true));
    }
    return out.build();
  }

  private static TextSegment text(String value, boolean emphasized) {
    return TextSegment.newBuilder().setText(value).setEmphasized(emphasized).build();
  }

  private static String mainTextString(ImmutableList<TextSegment> segments) {
    StringBuilder builder = new StringBuilder();
    for (TextSegment segment : segments) {
      builder.append(segment.getText());
    }
    return builder.toString();
  }

  private static Optional<String> groupByPrefix(String query) {
    String low = Ascii.toLowerCase(query);
    for (String prefix : GROUP_BY_PREFIXES) {
      if (low.equals(prefix)) {
        return Optional.of("");
      }
      if (low.startsWith(prefix + " ")) {
        return Optional.of(query.substring(prefix.length()).trim());
      }
    }
    return Optional.empty();
  }

  public static String normalize(String value) {
    return value.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "_");
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
    ImmutableList.Builder<String> parts = ImmutableList.builder();
    for (String part : raw.split(",", -1)) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        parts.add(Ascii.toLowerCase(trimmed));
      }
    }
    return parts.build();
  }

  private static boolean hasAllValues(
      FleetIndex index, String keyId, ImmutableList<String> valuesLower) {
    for (String value : valuesLower) {
      if (index.valueCount(keyId, value) == 0) {
        return false;
      }
    }
    return !valuesLower.isEmpty();
  }

  @Nullable
  private static Filter chipFor(List<Filter> filters, String keyId) {
    for (Filter filter : filters) {
      if (filter.getKey().equals(keyId)) {
        return filter;
      }
    }
    return null;
  }

  private static ImmutableList<Filter> otherFilters(List<Filter> filters, String keyId) {
    ImmutableList.Builder<Filter> others = ImmutableList.builder();
    for (Filter filter : filters) {
      if (!filter.getKey().equals(keyId)) {
        others.add(filter);
      }
    }
    return others.build();
  }

  private static BitSet devicesWithKey(Postings postings, String keyId) {
    BitSet withKey = new BitSet();
    for (int[] posting : postings.forKey(keyId).values()) {
      for (int deviceIndex : posting) {
        withKey.set(deviceIndex);
      }
    }
    return withKey;
  }

  private static int intersectionCount(int[] posting, BitSet filteredSet) {
    int count = 0;
    for (int deviceIndex : posting) {
      if (filteredSet.get(deviceIndex)) {
        count++;
      }
    }
    return count;
  }

  private static BitSet toBitSet(ImmutableList<Integer> indices) {
    BitSet set = new BitSet();
    for (int deviceIndex : indices) {
      set.set(deviceIndex);
    }
    return set;
  }

  private static int lowerBoundVkp(List<ValueKeyPair> sorted, String valuePrefix) {
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

  private enum Kind {
    CONDITION,
    KEY,
    GROUP_BY
  }

  private enum IntentPattern {
    KV,
    KV_NEGATE,
    EMPTY,
    KEY_VALUES,
    BARE_NEGATE,
    COMMA,
    TOKEN
  }

  private record Intent(
      IntentPattern pattern,
      String key,
      String value,
      boolean empty,
      boolean exclude,
      ImmutableList<String> values) {

    static Intent kv(IntentPattern pattern, String key, String value) {
      return new Intent(pattern, key.trim(), value.trim(), false, false, ImmutableList.of());
    }

    static Intent empty(String key, boolean empty) {
      return new Intent(IntentPattern.EMPTY, key.trim(), "", empty, false, ImmutableList.of());
    }

    static Intent keyValues(String key, boolean exclude) {
      return new Intent(
          IntentPattern.KEY_VALUES, key.trim(), "", false, exclude, ImmutableList.of());
    }

    static Intent bareNegate(String value) {
      return new Intent(
          IntentPattern.BARE_NEGATE, "", value.trim(), false, true, ImmutableList.of());
    }

    static Intent comma(String value, ImmutableList<String> values) {
      return new Intent(IntentPattern.COMMA, "", value, false, false, values);
    }

    static Intent token() {
      return new Intent(IntentPattern.TOKEN, "", "", false, false, ImmutableList.of());
    }
  }

  private record Match(String value, int tier) {}

  private record KeyMatch(String keyId, double tier) {}

  private record Context(
      Fleet fleet,
      SearchCorpus corpus,
      EntityKeyStrategy strategy,
      FleetIndex index,
      List<Filter> filters,
      boolean hasFilters,
      Set<String> activeKeys,
      ImmutableList<Integer> current,
      BitSet currentBits,
      Postings postings) {}

  private static final class Cand {
    final Kind kind;
    final String keyId;
    final double tier;
    final FleetSuggestion.Builder builder;
    final String mainString;

    String value;
    boolean exclude;
    boolean inChip;
    int rankCount;
    boolean needsCount;
    boolean noCount;
    int groupRank;
    boolean overMax;
    Integer count;
    String countPrefix = "";

    Cand(Kind kind, String keyId, double tier, FleetSuggestion.Builder builder, String mainString) {
      this.kind = kind;
      this.keyId = keyId;
      this.tier = tier;
      this.builder = builder;
      this.mainString = mainString;
    }

    FleetSuggestion build() {
      if (count != null) {
        builder.setCount(count);
      }
      if (!countPrefix.isEmpty()) {
        builder.setCountPrefix(countPrefix);
      }
      return builder.build();
    }
  }
}
