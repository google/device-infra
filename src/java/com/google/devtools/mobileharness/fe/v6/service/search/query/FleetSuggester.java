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

import static com.google.common.primitives.Booleans.falseFirst;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.SuggestEntityAdapter.normalize;

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
import com.google.devtools.mobileharness.fe.v6.service.search.query.SuggestEntityAdapter.KeyMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionCatalogStore;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Entity-agnostic suggestion engine that turns a partial search-bar query into a ranked list of
 * {@link FleetSuggestion}s for Device Search and Host Search.
 *
 * <h2>Why this exists</h2>
 *
 * <p>As users type in the OmniLab Console search bar, they may express a filter condition ({@code
 * status is idle}), a negated condition ({@code pool is not shared}), an empty/presence check
 * ({@code no owner}, {@code has pool}), a bare value ({@code pixel}), a key name ({@code status}),
 * a multi-value disjunction ({@code idle, busy}), or a grouping directive ({@code group by model}).
 * Evaluating these intents against live snapshot postings and active filter chips requires a
 * deterministic pipeline that classifies the input, generates valid candidates with exact BitSet
 * counts, suppresses contradictory chip modifications, and ranks results by scenario priority and
 * match quality. All entity-specific key schemas and alias catalogs are delegated to {@link
 * SuggestEntityAdapter} ({@link DeviceSuggestAdapter} or {@link HostSuggestAdapter}), keeping this
 * core engine completely independent of device or host key definitions.
 *
 * <h2>How to use it</h2>
 *
 * <ol>
 *   <li>Inject {@link FleetSuggester} via Guice (backed by {@link FleetFilterEngine}, the per-fleet
 *       {@link ScenarioCuration} map, and {@link DimensionCatalogStore}).
 *   <li>Invoke {@link #suggest(SearchCorpus, FleetSuggestionRequest)} with the snapshot {@link
 *       SearchCorpus} ({@link DeviceCorpus} or {@link HostCorpus}) and the client request.
 *   <li>Every returned {@link FleetSuggestion} carries structured display segments ({@link
 *       TextSegment}), an exact entity count (when applicable), and an explicit typed action
 *       ({@link FleetApplyFilter}, {@link FleetOpenPicker}, or {@link FleetAddGroupBy}) so the
 *       frontend executes suggestions without parsing display strings.
 * </ol>
 *
 * <h2>Suggestion semantics</h2>
 *
 * <ul>
 *   <li><b>First-match intent dispatch</b>: A non-empty query is matched against ordered patterns:
 *       {@code group by <key>}, empty/non-empty ({@code no <key>}, {@code has <key>}), negated
 *       key-value ({@code <key> is not <value>}), trailing operator ({@code <key> is}), positive
 *       key-value ({@code <key> is <value>}), bare negation ({@code not <value>}), comma-separated
 *       OR values, and single-token key + value search.
 *   <li><b>Chip-aware actions and polarity suppression</b>: When a candidate key already has an
 *       active chip with conflicting polarity or complex mode, value suggestions for that key are
 *       suppressed. When the active chip shares the candidate's polarity, the action stages the
 *       value in the existing picker ({@link FleetStagedModification}) and reports the OR delta
 *       ({@code +N}).
 *   <li><b>Two-phase ranking</b>: Candidates are deduplicated by {@code (label, mainText)} and
 *       sorted by scenario key priority, match-quality tier, personalization, count, and
 *       lexicographic text. When active filters are present, intersection counts against the
 *       filtered BitSet are computed before re-sorting and zero-match conditions are dropped.
 * </ul>
 */
public final class FleetSuggester {

  /** A grouping needs at least two buckets to be worth offering ({@code GROUP_SUGGEST_MIN}). */
  private static final int GROUP_SUGGEST_MIN = 2;

  /**
   * Above this bucket count a suggested grouping is flagged {@code over_max}: the accordion becomes
   * a wall. Ported from the prototype's {@code GROUP_SUGGEST_MAX}.
   */
  private static final int GROUP_SUGGEST_MAX = 50;

  // Longest first so "grouped by" is not consumed by the "group" prefix.
  private static final ImmutableList<String> GROUP_BY_PREFIXES =
      ImmutableList.of("grouped by", "group by", "groupby", "group");

  private static final int DEFAULT_LIMIT = 12;

  /** Number of strongest matched keys for which key-name matches offer ready-to-apply values. */
  private static final int TOP_VALUE_KEYS = 5;

  private static final int TOP_VALUES_PER_KEY = 3;
  private static final int KEY_VALUES_PER_KEY = 8;

  /** Cap on semantic prefix scan entries to keep PrefixAll bounded. */
  private static final int MAX_SEMANTIC_SCAN = 500;

  /**
   * Personalized (recent / frequent) key set. Personalization is deferred (backend plan and
   * adaptation guide section 11), so this is empty and no candidate is ever treated as
   * personalized.
   */
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
  private final Map<Fleet, ScenarioCuration> curations;
  private final DimensionCatalogStore dimensionCatalogStore;

  @Inject
  FleetSuggester(
      FleetFilterEngine filterEngine,
      Map<Fleet, ScenarioCuration> curations,
      DimensionCatalogStore dimensionCatalogStore) {
    this.filterEngine = filterEngine;
    this.curations = curations;
    this.dimensionCatalogStore = dimensionCatalogStore;
  }

  FleetSuggester(FleetFilterEngine filterEngine, Map<Fleet, ScenarioCuration> curations) {
    this(filterEngine, curations, new DimensionCatalogStore());
  }

  /** Returns ranked suggestions for the request against the given snapshot corpus. */
  public FleetSuggestionResponse suggest(SearchCorpus corpus, FleetSuggestionRequest request) {
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

    Fleet fleet = request.getFleet();
    ScenarioCuration curation =
        curations.get(fleet == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : fleet);

    SuggestEntityAdapter adapter;
    if (corpus instanceof DeviceCorpus deviceCorpus) {
      adapter =
          new DeviceSuggestAdapter(
              deviceCorpus, curation, dimensionCatalogStore.getDimensionNames(fleet));
    } else if (corpus instanceof HostCorpus hostCorpus) {
      adapter = new HostSuggestAdapter(hostCorpus, curation);
    } else {
      throw new IllegalArgumentException("Unsupported SearchCorpus: " + corpus.getClass());
    }

    Context context =
        new Context(
            corpus,
            adapter,
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

    // Pattern 0: the group-by prefix owns its input. A group-by term that matches no key returns
    // nothing rather than falling through to a value search for the literal word "group".
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
            // Key-name suggestions fire at any length (1-2 chars included). Value suggestions
            // only start at length >= 3 to avoid flooding very short inputs with value matches.
            List<Cand> both = new ArrayList<>(suggestKey(context, query));
            if (query.length() >= 3) {
              both.addAll(suggestValue(context, query, /* exclude= */ false, /* multi= */ null));
            }
            yield both;
          }
        };

    return rank(context, raw, limit);
  }

  // ---- Dispatch (spec section 1) ----

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

  // ---- Pattern 1 / 6: key-value (spec section 2, section 7) ----

  private List<Cand> suggestKv(Context context, String keyToken, String rawValue, boolean exclude) {
    List<Cand> out = new ArrayList<>();
    SuggestEntityAdapter adapter = context.adapter();
    ImmutableList<String> keyIds = adapter.resolveKey(keyToken, context.index());
    if (keyIds.isEmpty()) {
      keyIds = adapter.fallbackKvKeyIds(keyToken);
    }

    // Comma outside quotes: a multi-value OR under the resolved key.
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
          if (adapter.isColdUnindexedKey(keyId, context.index())
              && !context.activeKeys().contains(keyId)) {
            String display = adapter.titleDisplayName(keyId);
            String verb = adapter.isPlural(keyId) ? "are" : "is";
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
                    .setApplyFilter(applyFilter(adapter, context.index(), keyId, filter));
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
      if (!adapter.isKnownKey(keyId)) {
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
      // Cold long-tail fallback: if the key is an unindexed long-tail dimension/property and is
      // not already in active filters, emit a ready-to-apply filter condition with no count.
      if (!hadMatches
          && !value.isEmpty()
          && adapter.isColdUnindexedKey(keyId, context.index())
          && !context.activeKeys().contains(keyId)) {
        String display = adapter.titleDisplayName(keyId);
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
                .setApplyFilter(applyFilter(adapter, context.index(), keyId, filter));
        Cand cand = new Cand(Kind.CONDITION, keyId, 1.0, builder, mainTextString(mainText));
        cand.needsCount = false;
        cand.noCount = true;
        out.add(cand);
      }
    }
    return out;
  }

  // ---- Pattern 5: empty / non-empty (spec section 6) ----

  private List<Cand> suggestEmpty(Context context, String keyToken, boolean empty) {
    List<Cand> out = new ArrayList<>();
    SuggestEntityAdapter adapter = context.adapter();
    FleetIndex index = context.index();
    int globalTotal = context.corpus().recordCount();
    for (String keyId : adapter.resolveKey(keyToken, index)) {
      if (!index.keyIds().contains(keyId) || !adapter.isKnownKey(keyId)) {
        continue;
      }

      // 1. Global baseline check (unfiltered fleet properties):
      // A condition that has zero discrimination or zero hits globally across the entire fleet
      // has its priority demoted to 0 / completely omitted.
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

      // 2. Filtered context evaluation:
      int base = context.hasFilters() ? context.current().size() : globalTotal;
      int present = presenceCount(context, keyId);
      int count = empty ? (base - present) : present;
      if (count <= 0) {
        continue;
      }

      String display = adapter.titleDisplayName(keyId);
      boolean isPlural = adapter.isPlural(keyId);
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
              .setLabel(label(adapter, keyId, inChip))
              .addAllMainText(mainText);
      if (inChip) {
        builder.setOpenPicker(openPickerViewExisting(adapter, keyId));
      } else {
        builder.setApplyFilter(applyFilter(adapter, index, keyId, filter));
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

  // ---- Pattern 1 operator with no value yet (spec section 2.1a) ----

  private List<Cand> suggestKeyValues(Context context, String keyToken, boolean exclude) {
    List<Cand> out = new ArrayList<>();
    for (String keyId : context.adapter().resolveKey(keyToken, context.index())) {
      for (Match match : topValues(context, keyId, KEY_VALUES_PER_KEY)) {
        Cand cand = condition(context, keyId, match.value(), 3, exclude);
        if (cand != null) {
          out.add(cand);
        }
      }
    }
    return out;
  }

  // ---- Pattern 4 / 2: value over all keys (spec section 5, section 3) ----

  private List<Cand> suggestValue(
      Context context, String token, boolean exclude, ImmutableList<String> multi) {
    SuggestEntityAdapter adapter = context.adapter();
    FleetIndex index = context.index();

    if (multi != null) {
      List<Cand> out = new ArrayList<>();
      ImmutableList.Builder<String> lowered = ImmutableList.builder();
      for (String value : multi) {
        lowered.add(Ascii.toLowerCase(stripQuotes(value)));
      }
      ImmutableList<String> values = lowered.build();
      for (String keyId : index.keyIds()) {
        if (!adapter.isKnownKey(keyId)) {
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

    // 1. Exact matches (all keys, via globalExact) for O(1) lookup.
    for (KeyCount kc : index.globalExact().getOrDefault(value, ImmutableList.of())) {
      if (seen.add(kc.key() + '\u0000' + value)) {
        Cand cand = condition(context, kc.key(), value, 3, exclude);
        if (cand != null) {
          out.add(cand);
        }
      }
    }

    // 2. Prefix matches (semantic keys only, via semanticGlobalSorted bisect) for O(log D_s).
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

    // 3. Identifier collapse (one collapsed suggestion per identifier key in the index).
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
        String identDisplay = adapter.titleDisplayName(identKey);
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
                .setOpenPicker(openPickerNewChip(adapter, identKey));
        Cand cand = new Cand(Kind.KEY, identKey, 1, builder, mainTextString(mainText));
        cand.needsCount = false;
        cand.noCount = true;
        out.add(cand);
      }
    }

    return out;
  }

  // ---- Pattern 3: key name (spec section 4) ----

  private List<Cand> suggestKey(Context context, String token) {
    List<Cand> out = new ArrayList<>();
    SuggestEntityAdapter adapter = context.adapter();
    List<KeyMatch> matched = adapter.matchKeys(token, context.index());
    int rank = 0;
    for (KeyMatch keyMatch : matched) {
      String keyId = keyMatch.keyId();
      double tier = keyMatch.tier();
      // Ready-to-apply conditions for the strongest matched keys.
      if (rank < TOP_VALUE_KEYS) {
        for (Match value : topValues(context, keyId, TOP_VALUES_PER_KEY)) {
          Cand cand = condition(context, keyId, value.value(), tier, /* exclude= */ false);
          if (cand != null) {
            out.add(cand);
          }
        }
      }
      // The bare filter key: opens the value picker. Ranks just below a concrete condition.
      boolean inChip = context.activeKeys().contains(keyId);
      String display = adapter.titleDisplayName(keyId);
      ImmutableList<TextSegment> mainText = segments(display, null);
      FleetSuggestion.Builder builder =
          FleetSuggestion.newBuilder()
              .setLabel(label(adapter, keyId, inChip))
              .addAllMainText(mainText)
              .setOpenPicker(
                  inChip
                      ? openPickerViewExisting(adapter, keyId)
                      : openPickerNewChip(adapter, keyId));
      Cand cand = new Cand(Kind.KEY, keyId, tier - 0.4, builder, mainTextString(mainText));
      cand.needsCount = false;
      cand.noCount = true;
      out.add(cand);
      rank++;
    }
    return out;
  }

  // ---- Pattern 0: typed group-by ----

  private FleetSuggestionResponse suggestGroupBy(
      Context context, String term, List<String> applied, int limit) {
    SuggestEntityAdapter adapter = context.adapter();
    FleetIndex index = context.index();
    if (applied.size() >= 3) {
      return FleetSuggestionResponse.getDefaultInstance();
    }
    Set<String> appliedSet = new HashSet<>(applied);

    List<String> candidates = new ArrayList<>();
    Map<String, Integer> matchRank = new HashMap<>();
    if (term.isEmpty()) {
      candidates.addAll(adapter.defaultGroupByCandidates());
    } else {
      String normTerm = normalize(term);
      for (String keyId : adapter.resolveKey(term, index)) {
        matchRank.put(keyId, 0);
      }
      for (String keyId : index.keyIds()) {
        if (matchRank.containsKey(keyId) || !adapter.isKnownKey(keyId)) {
          continue;
        }
        String display = normalize(adapter.titleDisplayName(keyId));
        String bare = normalize(adapter.bareName(keyId));
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
          || !adapter.isKnownKey(keyId)) {
        continue;
      }
      int groups = groupCount(context, keyId);
      if (groups < GROUP_SUGGEST_MIN) {
        continue;
      }
      boolean overMax = groups > GROUP_SUGGEST_MAX;
      String display = adapter.titleDisplayName(keyId);
      ImmutableList<TextSegment> mainText = segments("", display);
      FleetSuggestion.Builder builder =
          FleetSuggestion.newBuilder()
              .setLabel("Group by")
              .addAllMainText(mainText)
              .setCount(groups)
              .setCountUnit("groups")
              .setOverMax(overMax)
              .setAddGroupBy(addGroupBy(adapter, keyId));
      Cand cand = new Cand(Kind.GROUP_BY, keyId, 0, builder, mainTextString(mainText));
      cand.groupRank = matchRank.getOrDefault(keyId, 0);
      cand.overMax = overMax;
      cand.count = groups;
      out.add(cand);
    }

    out.sort(
        Comparator.<Cand>comparingInt(c -> -adapter.keyPriority(c.keyId))
            .thenComparingInt(c -> c.groupRank)
            .thenComparing(c -> c.overMax, falseFirst())
            .thenComparingInt(c -> c.count == null ? 0 : c.count));

    FleetSuggestionResponse.Builder response = FleetSuggestionResponse.newBuilder();
    for (Cand cand : out.subList(0, Math.min(out.size(), limit))) {
      response.addItems(cand.builder.build());
    }
    return response.build();
  }

  // ---- Condition builder (spec section 10.2, section 11.1 eligibility) ----

  @Nullable
  private Cand condition(
      Context context, String keyId, String valueLower, double tier, boolean exclude) {
    SuggestEntityAdapter adapter = context.adapter();
    FleetIndex index = context.index();
    if (!index.keyIds().contains(keyId) || !adapter.isKnownKey(keyId)) {
      return null;
    }
    // Eligibility gate (spec section 11.1): the value must exist somewhere in the fleet.
    int global = index.valueCount(keyId, valueLower);
    if (global == 0) {
      return null;
    }
    boolean inChip = context.activeKeys().contains(keyId);

    // Polarity-conflict and complex-chip rules (spec section 7): when the key already carries a
    // chip whose polarity conflicts, or a complex chip, suppress the value suggestion.
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

    String display = adapter.titleDisplayName(keyId);
    String shown = displayValue(index, keyId, valueLower);
    FleetSuggestion.Builder builder = FleetSuggestion.newBuilder();
    ImmutableList<TextSegment> mainText;
    if (inChip) {
      String verb = exclude ? "exclude " : "add ";
      mainText = segments(verb, shown);
      builder.setOpenPicker(openPickerStaged(adapter, keyId, ImmutableList.of(shown)));
    } else {
      String op =
          adapter.isPlural(keyId) ? (exclude ? "are not" : "are") : (exclude ? "is not" : "is");
      mainText = segments(display + " " + op + " ", shown);
      Filter filter = valueFilter(keyId, ImmutableList.of(shown), exclude);
      builder.setApplyFilter(applyFilter(adapter, index, keyId, filter));
    }
    builder.setLabel(label(adapter, keyId, inChip)).addAllMainText(mainText);

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
    SuggestEntityAdapter adapter = context.adapter();
    FleetIndex index = context.index();
    if (!index.keyIds().contains(keyId) || !adapter.isKnownKey(keyId)) {
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
    String display = adapter.titleDisplayName(keyId);
    String verb = adapter.isPlural(keyId) ? "are" : "is";
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
            .setLabel(label(adapter, keyId, inChip))
            .addAllMainText(mainText);
    if (inChip) {
      builder.setOpenPicker(openPickerStaged(adapter, keyId, shown));
    } else {
      builder.setApplyFilter(
          applyFilter(adapter, index, keyId, valueFilter(keyId, shown, exclude)));
    }
    Cand cand = new Cand(Kind.CONDITION, keyId, 3, builder, mainTextString(mainText));
    cand.rankCount = orCount;
    cand.count = orCount > 0 ? orCount : null;
    cand.needsCount = false;
    out.add(cand);
  }

  // ---- Ranking (spec section 11.2, section 11.3, section 10.3) ----

  private FleetSuggestionResponse rank(Context context, List<Cand> raw, int limit) {
    SuggestEntityAdapter adapter = context.adapter();
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
        Comparator.<Cand>comparingInt(c -> -adapter.keyPriority(c.keyId))
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
        Comparator.<Cand>comparingInt(c -> -adapter.keyPriority(c.keyId))
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
      for (int recordIndex : context.postings().get(cand.keyId, cand.value)) {
        if (baseK.get(recordIndex) && !context.currentBits().get(recordIndex)) {
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
      for (int recordIndex : context.postings().get(keyId, value)) {
        union.set(recordIndex);
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

  // ---- Value matching (spec section 2.3) ----

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

  /** Top values of a key by global count, used to offer ready-to-apply conditions. */
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

  // ---- Group-by counting ----

  private static int groupCount(Context context, String keyId) {
    Set<String> combos = new HashSet<>();
    boolean hasMissing = false;
    for (int recordIndex : context.current()) {
      ImmutableSet<String> values = context.corpus().valuesForKey(recordIndex, keyId);
      if (values.isEmpty()) {
        hasMissing = true;
      } else {
        combos.add(String.join("\u0000", new TreeSet<>(values)));
      }
    }
    return combos.size() + (hasMissing ? 1 : 0);
  }

  // ---- Action builders ----

  private static FleetApplyFilter applyFilter(
      SuggestEntityAdapter adapter, FleetIndex index, String keyId, Filter filter) {
    return FleetApplyFilter.newBuilder()
        .setResultingFilter(filter)
        .setPillKey(adapter.pillKey(keyId))
        .setPillCondition(pillCondition(index, filter))
        .setMetadata(metadata(adapter, keyId))
        .build();
  }

  private static FleetOpenPicker openPickerNewChip(SuggestEntityAdapter adapter, String keyId) {
    return FleetOpenPicker.newBuilder()
        .setKey(keyId)
        .setMetadata(metadata(adapter, keyId))
        .setNewChip(FleetNewChip.getDefaultInstance())
        .build();
  }

  private static FleetOpenPicker openPickerViewExisting(
      SuggestEntityAdapter adapter, String keyId) {
    return FleetOpenPicker.newBuilder()
        .setKey(keyId)
        .setMetadata(metadata(adapter, keyId))
        .setViewExisting(FleetViewExisting.getDefaultInstance())
        .build();
  }

  private static FleetOpenPicker openPickerStaged(
      SuggestEntityAdapter adapter, String keyId, ImmutableList<String> values) {
    return FleetOpenPicker.newBuilder()
        .setKey(keyId)
        .setMetadata(metadata(adapter, keyId))
        .setStagedModify(FleetStagedModification.newBuilder().addAllValues(values))
        .build();
  }

  private static FleetAddGroupBy addGroupBy(SuggestEntityAdapter adapter, String keyId) {
    return FleetAddGroupBy.newBuilder().setKey(keyId).setPillKey(adapter.pillKey(keyId)).build();
  }

  private static FleetFilterChipMetadata metadata(SuggestEntityAdapter adapter, String keyId) {
    return FleetFilterChipMetadata.newBuilder()
        .setKeyDisplayName(adapter.titleDisplayName(keyId))
        .setCanUseAdvanced(true)
        .setIsPlural(adapter.isPlural(keyId))
        .build();
  }

  // ---- Filter builders ----

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

  // ---- Display helpers ----

  private static String label(SuggestEntityAdapter adapter, String keyId, boolean inChip) {
    return inChip ? "Modify " + adapter.titleDisplayName(keyId) : "Add filter";
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

  // ---- Small utilities ----

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
      for (int recordIndex : posting) {
        withKey.set(recordIndex);
      }
    }
    return withKey;
  }

  private static int intersectionCount(int[] posting, BitSet filteredSet) {
    int count = 0;
    for (int recordIndex : posting) {
      if (filteredSet.get(recordIndex)) {
        count++;
      }
    }
    return count;
  }

  private static BitSet toBitSet(ImmutableList<Integer> indices) {
    BitSet set = new BitSet();
    for (int recordIndex : indices) {
      set.set(recordIndex);
    }
    return set;
  }

  /** Lower bound binary search over the semantic global value index. */
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

  // ---- Internal types ----

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

  /** A dispatched intent: the pattern plus its extracted operands. */
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

  /** A matched value with its match-quality tier (3 full, 2 prefix, 1 contains). */
  private record Match(String value, int tier) {}

  /** Per-request query context, so helpers avoid threading many parameters. */
  private record Context(
      SearchCorpus corpus,
      SuggestEntityAdapter adapter,
      FleetIndex index,
      List<Filter> filters,
      boolean hasFilters,
      Set<String> activeKeys,
      ImmutableList<Integer> current,
      BitSet currentBits,
      Postings postings) {}

  /**
   * A candidate suggestion before ranking. Holds the partially built proto (label, main text, and
   * action are set at generation) plus bookkeeping the ranker needs, and the count fields set
   * during ranking.
   */
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
