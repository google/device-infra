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
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.DIM_PREFIX;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_OWNER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_UUID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_CONNECTIVITY;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_DEVICE_COUNT;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_LAB_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_NAME;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_RELEASE_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.IDENTIFIER_KEYS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.MULTI_VALUE_KEYS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.PLAIN_VALUE_KEYS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.PLURAL_DISPLAY_KEYS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.PROP_PREFIX;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.VALUE_DISPLAY_KEYS;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TextSegment;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.KeyCount;
import com.google.devtools.mobileharness.fe.v6.service.search.index.Postings;
import com.google.devtools.mobileharness.fe.v6.service.search.index.ValueKeyPair;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Turns a partial search-bar string into a ranked list of {@link FleetSuggestion}s. This is the
 * Java port of the search prototype's {@code suggest}, {@code _dispatch}, and {@code _rank}
 * (suggest_engine.py at depot HEAD, CL 959511642), following the algorithm spec
 * (filter_condition_suggestion_algorithm.md).
 *
 * <p>The engine classifies the input into exactly one pattern, produces raw candidate suggestions
 * for that pattern, then ranks them. Every suggestion carries structured display text ({@link
 * TextSegment}s with the matched value emphasized), an optional device count, and a typed action:
 * apply a new filter chip, open the value picker for an existing key, or add a group-by key. The
 * frontend never infers behavior from the text.
 *
 * <p>Intent patterns ported here (dispatch order matches the prototype's first-match-wins order):
 *
 * <ul>
 *   <li>group-by prefix ({@code group by <key>}) to an add-group-by suggestion;
 *   <li>empty / non-empty ({@code no <key>} / {@code has <key>});
 *   <li>key-value negate ({@code <key> is not <value>});
 *   <li>key with a trailing operator and no value yet ({@code <key> is}) to that key's top values;
 *   <li>key-value ({@code <key> is <value>});
 *   <li>bare negation ({@code not <value>});
 *   <li>comma-separated values (multi-value OR, key undetermined);
 *   <li>a single token, tried as both a key name and a value.
 * </ul>
 *
 * <p>An identifier typed on its own resolves through the single-token value path: a device UUID
 * matches {@code field::uuid} and a user name matches {@code field::owner} via the value index, so
 * those intents fall out of ordinary value matching rather than a dedicated regex detector.
 *
 * <p>Ranking is by match quality, then personalization, then device count, then scenario key
 * priority, then a stable text tie-break. Personalization (recent and frequent suggestions) is a
 * ranking input in the spec but is deferred, so this port emits no personalized items and treats
 * every candidate as non-personalized. See the TODO on {@link #PERSONALIZED_KEYS}.
 */
public final class FleetSuggester {

  // --- Key namespaces and identity ---

  private static final String DIM_DISPLAY_PREFIX = "Dimension ";
  private static final String PROP_DISPLAY_PREFIX = "Host Property ";

  // --- Group-by ---

  /**
   * Group-by candidates for a bare {@code group by} prefix. Ported from the prototype's {@code
   * GROUP_BY_CANDIDATES}.
   */
  private static final ImmutableList<String> GROUP_BY_CANDIDATES =
      ImmutableList.of(
          "host::host_name",
          "host::lab_type",
          "host::ats_controller",
          "dim::lab_location",
          FIELD_STATUS,
          "dim::model",
          "dim::pool",
          FIELD_TYPE);

  /**
   * Group-by candidates for a bare {@code group by} prefix in the host entity. Ported from the
   * prototype's {@code HOST_GROUP_BY_ROW} (the 1p host branch), the richest host group-by candidate
   * pool; scenario awareness comes from the entity-aware key priority that reorders these and from
   * dropping any candidate absent from the fleet, exactly as the device pool does.
   */
  private static final ImmutableList<String> HOST_GROUP_BY_CANDIDATES =
      ImmutableList.of(HOST_LAB_TYPE, HOST_RELEASE_STATUS);

  /** A grouping needs at least two buckets to be worth offering ({@code GROUP_SUGGEST_MIN}). */
  private static final int GROUP_SUGGEST_MIN = 2;

  /**
   * Above this bucket count a suggested grouping is flagged {@code over_max}: the accordion becomes
   * a wall. Ported from the prototype's {@code GROUP_SUGGEST_MAX}. This is the "usable group count"
   * threshold the proto's {@code over_max} documents as a backend decision.
   */
  private static final int GROUP_SUGGEST_MAX = 50;

  // Longest first so "grouped by" is not consumed by the "group" prefix.
  private static final ImmutableList<String> GROUP_BY_PREFIXES =
      ImmutableList.of("grouped by", "group by", "groupby", "group");

  /**
   * Curated starter keys offered for an empty query. Ported from the prototype's {@code
   * _empty_state}.
   */
  private static final ImmutableList<String> EMPTY_STATE_KEYS =
      ImmutableList.of(
          FIELD_STATUS,
          "dim::model",
          FIELD_TYPE,
          FIELD_OWNER,
          "dim::pool",
          "dim::os",
          "dim::quarantined");

  /**
   * Curated starter keys offered for an empty query in the host entity. Ported from the prototype's
   * host empty state, which offers the host {@code HOST_FILTER_BY_ROW} keys (the 1p host branch);
   * keys absent from the fleet are skipped, exactly as the device empty state skips absent keys.
   */
  private static final ImmutableList<String> HOST_EMPTY_STATE_KEYS =
      ImmutableList.of(
          HOST_NAME, HOST_LAB_TYPE, HOST_RELEASE_STATUS, HOST_CONNECTIVITY, HOST_DEVICE_COUNT);

  private static final int DEFAULT_LIMIT = 12;

  /** Number of strongest matched keys for which key-name matches offer ready-to-apply values. */
  private static final int TOP_VALUE_KEYS = 5;

  private static final int TOP_VALUES_PER_KEY = 3;
  private static final int KEY_VALUES_PER_KEY = 8;

  /** Cap on semantic prefix scan entries to keep PrefixAll bounded. */
  private static final int MAX_SEMANTIC_SCAN = 500;

  /**
   * Personalized (recent / frequent) key set. Personalization is deferred (backend plan and
   * adaptation guide §11), so this is empty and no candidate is ever treated as personalized.
   *
   * <p>TODO: when personalization ships, populate this per request from the user's query and view
   * history, and add recent-condition and recent-key candidates as the prototype's {@code
   * recent_conditions} / {@code recent_kids} do. Until then the ranking's personalization tier is a
   * no-op.
   */
  private static final ImmutableSet<String> PERSONALIZED_KEYS = ImmutableSet.of();

  // --- Static alias catalog (prototype BUILTIN_DEVICE + BUILTIN_HOST). ---

  private static final ImmutableMap<String, ImmutableList<String>> ALIAS_TO_KEYS = buildAliasMap();

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern NAMESPACE_DIM =
      Pattern.compile("^(?:device[ _])?dimension[ _:]+(.+)$");
  private static final Pattern NAMESPACE_PROP = Pattern.compile("^host[ _]?property[ _:]+(.+)$");
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

  /**
   * The per-fleet curations, keyed by {@link Fleet}. The scenario key ranking used by {@link #rank}
   * and {@link #suggestGroupBy} comes from {@link ScenarioCuration#keyPriority} of the entry for
   * the request's fleet. Empty until activation installs the {@link ScenarioCurationModule}
   * MapBinder, in which case ranking falls back to a neutral priority (see {@link #suggest}).
   */
  private final Map<Fleet, ScenarioCuration> curations;

  @Inject
  FleetSuggester(FleetFilterEngine filterEngine, Map<Fleet, ScenarioCuration> curations) {
    this.filterEngine = filterEngine;
    this.curations = curations;
  }

  /** Returns ranked suggestions for the request against the given snapshot. */
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

    // FLEET_UNSPECIFIED defaults to FLEET_SELF (see the Fleet proto). Resolve the curation for the
    // request's fleet and derive its scenario key ranking. If no curation is installed (the
    // MapBinder is wired at activation), fall back to a neutral priority that treats every key as
    // the lowest tier, so suggestions still return in a stable order rather than failing.
    Fleet fleet = request.getFleet();
    ScenarioCuration curation =
        curations.get(fleet == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : fleet);
    // The key ranking is entity aware: host keys rank by the host tier table, devices by the device
    // one. For the device entity the entity-aware curation call resolves to the same device
    // ranking,
    // so device suggestions are unchanged.
    SearchEntity entity = corpus.entity();
    ToIntFunction<String> keyPriority =
        keyId -> (curation == null || keyId == null) ? 0 : curation.keyPriority(keyId, entity);

    Context context =
        new Context(
            corpus,
            index,
            filters,
            hasFilters,
            activeKeys,
            current,
            currentBits,
            keyPriority,
            corpus.postings());

    if (query.isEmpty()) {
      return emptyState(context, limit);
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
    ImmutableList<String> keyIds = resolveKey(context.index(), keyToken);

    // Comma outside quotes: a multi-value OR under the resolved key.
    if (rawValue.contains(",") && !rawValue.contains("\"") && !rawValue.contains("'")) {
      ImmutableList<String> parts = splitCommaLower(rawValue);
      for (String keyId : keyIds) {
        addMultiValueOr(context, out, keyId, parts, exclude);
      }
      return out;
    }

    String value = Ascii.toLowerCase(stripQuotes(rawValue.trim()));
    for (String keyId : keyIds) {
      for (Match match : matchValues(context.index(), keyId, value, /* allowContains= */ true)) {
        Cand cand = condition(context, keyId, match.value(), match.tier(), exclude);
        if (cand != null) {
          out.add(cand);
        }
      }
    }
    return out;
  }

  // ---- Pattern 5: empty / non-empty (spec section 6) ----

  private List<Cand> suggestEmpty(Context context, String keyToken, boolean empty) {
    List<Cand> out = new ArrayList<>();
    FleetIndex index = context.index();
    int base = context.hasFilters() ? context.current().size() : context.corpus().recordCount();
    for (String keyId : resolveKey(index, keyToken)) {
      if (!MULTI_VALUE_KEYS.contains(keyId) || !index.keyIds().contains(keyId)) {
        continue;
      }
      String display = displayName(index, keyId);
      int present = presenceCount(context, keyId);
      String verb = PLURAL_DISPLAY_KEYS.contains(keyId) ? "are" : "is";
      Filter filter;
      ImmutableList<TextSegment> mainText;
      int count;
      if (empty) {
        count = base - present;
        mainText = segments(display + " " + verb + " ", "empty");
        filter = noValueFilter(keyId, /* negated= */ false);
      } else {
        count = present;
        mainText = segments(display + " " + verb + " ", "not empty");
        filter = noValueFilter(keyId, /* negated= */ true);
      }
      boolean inChip = context.activeKeys().contains(keyId);
      FleetSuggestion.Builder builder =
          FleetSuggestion.newBuilder()
              .setLabel(label(index, keyId, inChip))
              .addAllMainText(mainText);
      if (inChip) {
        builder.setOpenPicker(openPickerViewExisting(index, keyId));
      } else {
        builder.setApplyFilter(applyFilter(index, keyId, filter));
      }
      Cand cand = new Cand(Kind.CONDITION, keyId, 3, builder, mainTextString(mainText));
      cand.count = count > 0 ? count : null;
      cand.needsCount = false;
      out.add(cand);
    }
    return out;
  }

  // ---- Pattern 1 operator with no value yet (spec section 2.1a) ----

  private List<Cand> suggestKeyValues(Context context, String keyToken, boolean exclude) {
    List<Cand> out = new ArrayList<>();
    for (String keyId : resolveKey(context.index(), keyToken)) {
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
    FleetIndex index = context.index();

    if (multi != null) {
      List<Cand> out = new ArrayList<>();
      ImmutableList.Builder<String> lowered = ImmutableList.builder();
      for (String value : multi) {
        lowered.add(Ascii.toLowerCase(stripQuotes(value)));
      }
      ImmutableList<String> values = lowered.build();
      for (String keyId : index.keyIds()) {
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
    // TODO: Consider extracting a composite(keyId, value) helper for
    // null-separated composite key lookups.
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

    // 3. Identifier collapse (PLAIN_VALUE_KEYS, one collapsed suggestion per key).
    for (String identKey : PLAIN_VALUE_KEYS) {
      ImmutableList<String> keyValues = index.sortedValues(identKey);
      if (keyValues.isEmpty()) {
        continue;
      }
      int ilo = FleetFilterEngine.lowerBound(keyValues, value);
      int ihi = FleetFilterEngine.lowerBound(keyValues, value + '\uffff');
      int matchCount = ihi - ilo;
      if (matchCount > 0) {
        String identDisplay = displayName(index, identKey);
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
                .setOpenPicker(openPickerNewChip(index, identKey));
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
    FleetIndex index = context.index();
    List<KeyMatch> matched = matchKeyIds(index, token);
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
      String display = displayName(index, keyId);
      ImmutableList<TextSegment> mainText = segments(display, null);
      FleetSuggestion.Builder builder =
          FleetSuggestion.newBuilder()
              .setLabel(label(index, keyId, inChip))
              .addAllMainText(mainText)
              .setOpenPicker(
                  inChip ? openPickerViewExisting(index, keyId) : openPickerNewChip(index, keyId));
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
    FleetIndex index = context.index();
    if (applied.size() >= 3) {
      return FleetSuggestionResponse.getDefaultInstance();
    }
    Set<String> appliedSet = new HashSet<>(applied);

    // A bare prefix offers the curated candidates; a term widens to any key whose display name or
    // bare id matches, ranked by match quality (exact 0, prefix 1, contains 2).
    List<String> candidates = new ArrayList<>();
    Map<String, Integer> matchRank = new HashMap<>();
    if (term.isEmpty()) {
      candidates.addAll(groupByCandidates(context.corpus()));
    } else {
      String normTerm = normalize(term);
      for (String keyId : resolveKey(index, term)) {
        matchRank.put(keyId, 0);
      }
      for (String keyId : index.keyIds()) {
        if (matchRank.containsKey(keyId)) {
          continue;
        }
        String display = normalize(displayName(index, keyId));
        String bare = normalize(bareName(keyId));
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
      if (!index.keyIds().contains(keyId) || appliedSet.contains(keyId)) {
        continue;
      }
      int groups = groupCount(context, keyId);
      if (groups < GROUP_SUGGEST_MIN) {
        continue;
      }
      boolean overMax = groups > GROUP_SUGGEST_MAX;
      String display = displayName(index, keyId);
      ImmutableList<TextSegment> mainText = segments("group by ", display);
      FleetSuggestion.Builder builder =
          FleetSuggestion.newBuilder()
              .setLabel("Group by")
              .addAllMainText(mainText)
              .setCount(groups)
              .setCountUnit("groups")
              .setOverMax(overMax)
              .setAddGroupBy(addGroupBy(index, keyId));
      Cand cand = new Cand(Kind.GROUP_BY, keyId, 0, builder, mainTextString(mainText));
      cand.groupRank = matchRank.getOrDefault(keyId, 0);
      cand.overMax = overMax;
      cand.count = groups;
      out.add(cand);
    }

    out.sort(
        Comparator.<Cand>comparingInt(c -> -context.keyPriority().applyAsInt(c.keyId))
            .thenComparingInt(c -> c.groupRank)
            .thenComparing(c -> c.overMax, falseFirst())
            .thenComparingInt(c -> c.count == null ? 0 : c.count));

    FleetSuggestionResponse.Builder response = FleetSuggestionResponse.newBuilder();
    for (Cand cand : out.subList(0, Math.min(out.size(), limit))) {
      response.addItems(cand.builder.build());
    }
    return response.build();
  }

  // ---- Empty query (spec section 11 empty state) ----

  private FleetSuggestionResponse emptyState(Context context, int limit) {
    FleetIndex index = context.index();
    FleetSuggestionResponse.Builder response = FleetSuggestionResponse.newBuilder();
    int emitted = 0;
    // Personalization is deferred, so no recent conditions are offered; only curated starter keys.
    for (String keyId : emptyStateKeys(context.corpus())) {
      if (emitted >= limit) {
        break;
      }
      if (!index.keyIds().contains(keyId)) {
        continue;
      }
      boolean inChip = context.activeKeys().contains(keyId);
      response.addItems(
          FleetSuggestion.newBuilder()
              .setLabel(label(index, keyId, inChip))
              .addAllMainText(segments(displayName(index, keyId), null))
              .setOpenPicker(
                  inChip ? openPickerViewExisting(index, keyId) : openPickerNewChip(index, keyId))
              .build());
      emitted++;
    }
    return response.build();
  }

  // ---- Condition builder (spec section 10.2, section 11.1 eligibility) ----

  @Nullable
  private Cand condition(
      Context context, String keyId, String valueLower, double tier, boolean exclude) {
    FleetIndex index = context.index();
    if (!index.keyIds().contains(keyId)) {
      return null;
    }
    // Eligibility gate (spec section 11.1): the value must exist somewhere in the fleet.
    int global = index.valueCount(keyId, valueLower);
    if (global == 0) {
      return null;
    }
    boolean inChip = context.activeKeys().contains(keyId);

    // Polarity-conflict and complex-chip rules (spec section 7): when the key already carries a
    // chip whose polarity conflicts, or a complex chip, suppress the value suggestion. Only the
    // key-level "Modify <Key>" affordance remains (emitted separately by the key path).
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

    String display = displayName(index, keyId);
    String shown = displayValue(index, keyId, valueLower);
    FleetSuggestion.Builder builder = FleetSuggestion.newBuilder();
    ImmutableList<TextSegment> mainText;
    if (inChip) {
      // Editing an existing simple, same-polarity chip: stage the value in the picker. One-click
      // apply is reserved for brand-new chips.
      String verb = exclude ? "exclude " : "add ";
      mainText = segments(verb, shown);
      builder.setOpenPicker(openPickerStaged(index, keyId, ImmutableList.of(shown)));
    } else {
      String op =
          PLURAL_DISPLAY_KEYS.contains(keyId)
              ? (exclude ? "are not" : "are")
              : (exclude ? "is not" : "is");
      mainText = segments(display + " " + op + " ", shown);
      Filter filter = valueFilter(keyId, ImmutableList.of(shown), exclude);
      builder.setApplyFilter(applyFilter(index, keyId, filter));
    }
    builder.setLabel(label(index, keyId, inChip)).addAllMainText(mainText);

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
    if (!index.keyIds().contains(keyId)) {
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
    String display = displayName(index, keyId);
    String verb = PLURAL_DISPLAY_KEYS.contains(keyId) ? "are" : "is";
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
        FleetSuggestion.newBuilder().setLabel(label(index, keyId, inChip)).addAllMainText(mainText);
    if (inChip) {
      builder.setOpenPicker(openPickerStaged(index, keyId, shown));
    } else {
      builder.setApplyFilter(applyFilter(index, keyId, valueFilter(keyId, shown, exclude)));
    }
    Cand cand = new Cand(Kind.CONDITION, keyId, 3, builder, mainTextString(mainText));
    cand.rankCount = orCount;
    cand.count = orCount > 0 ? orCount : null;
    cand.needsCount = false;
    out.add(cand);
  }

  // ---- Ranking (spec section 11.2, section 11.3, section 10.3) ----

  private FleetSuggestionResponse rank(Context context, List<Cand> raw, int limit) {
    // Dedupe by (label, main text).
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

    // Ranking order (matches prototype suggest_engine.py _rank sort_key): key priority is the
    // PRIMARY sort so core keys (e.g. dim::model) outrank raw dims (e.g. dim::supported_model)
    // regardless of match count, then match quality (tier), personalization, count, text.
    uniq.sort(
        Comparator.<Cand>comparingInt(c -> -context.keyPriority().applyAsInt(c.keyId))
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
    // Under active filters, compute filtered count for all candidates, then re-sort by filtered
    // count and drop zero-count conditions.
    for (Cand cand : uniq) {
      applyCount(context, cand);
    }
    uniq.sort(
        Comparator.<Cand>comparingInt(c -> -context.keyPriority().applyAsInt(c.keyId))
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

  /**
   * Sets the displayed count (spec section 10.3) for a shown candidate. A modify-include suggestion
   * carries the OR-broadening delta with a "+" prefix; every other value suggestion carries the
   * resulting absolute total.
   */
  private void applyCount(Context context, Cand cand) {
    if (!cand.needsCount) {
      return;
    }
    int base = context.hasFilters() ? context.current().size() : context.corpus().recordCount();

    if (cand.inChip && !cand.exclude) {
      // Modify include: the true delta is how many devices matching the OTHER chips and this value
      // are not already in the result.
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

  // ---- Value matching (spec section 2.3) ----

  private static ImmutableList<Match> matchValues(
      FleetIndex index, String keyId, String query, boolean allowContains) {
    ImmutableList<String> sorted = index.sortedValues(keyId);
    if (sorted.isEmpty() || query.isEmpty()) {
      return ImmutableList.of();
    }
    ImmutableMap<String, Integer> counts = index.valueCounts(keyId);
    List<Match> out = new ArrayList<>();
    Set<String> exactHits = new HashSet<>();
    // Full match, normalizing space and underscore both ways (spec section 2.3).
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
    if (allowContains && !IDENTIFIER_KEYS.contains(keyId) && out.size() < KEY_VALUES_PER_KEY) {
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

  // ---- Key resolution (spec section 2.2) ----

  private static ImmutableList<String> resolveKey(FleetIndex index, String token) {
    String raw = token.trim();
    String low = Ascii.toLowerCase(raw);

    Matcher dim = NAMESPACE_DIM.matcher(low);
    if (dim.matches()) {
      String keyId = DIM_PREFIX + normalize(dim.group(1));
      return index.keyIds().contains(keyId) ? ImmutableList.of(keyId) : ImmutableList.of();
    }
    Matcher prop = NAMESPACE_PROP.matcher(low);
    if (prop.matches()) {
      String keyId = PROP_PREFIX + normalize(prop.group(1));
      return index.keyIds().contains(keyId) ? ImmutableList.of(keyId) : ImmutableList.of();
    }
    ImmutableList<String> aliased = ALIAS_TO_KEYS.get(normalize(raw));
    if (aliased != null) {
      return aliased;
    }
    // Case 3: a bare token equal to a dimension or host-property key present in the fleet.
    String bareDim = DIM_PREFIX + normalize(raw);
    if (index.keyIds().contains(bareDim)) {
      return ImmutableList.of(bareDim);
    }
    String bareProp = PROP_PREFIX + normalize(raw);
    if (index.keyIds().contains(bareProp)) {
      return ImmutableList.of(bareProp);
    }
    return ImmutableList.of();
  }

  /**
   * Resolves a key token to matched keys with a match-quality tier, looser than {@link
   * #resolveKey}: beyond exact alias hits it also offers prefix and contains matches over key
   * display names.
   */
  private static List<KeyMatch> matchKeyIds(FleetIndex index, String token) {
    List<KeyMatch> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String keyId : resolveKey(index, token)) {
      if (index.keyIds().contains(keyId) && seen.add(keyId)) {
        out.add(new KeyMatch(keyId, 3));
      }
    }
    String normTerm = normalize(token);
    if (normTerm.isEmpty()) {
      return out;
    }
    for (String keyId : index.keyIds()) {
      if (seen.contains(keyId)) {
        continue;
      }
      String display = normalize(displayName(index, keyId));
      String bare = normalize(bareName(keyId));
      if (display.startsWith(normTerm) || bare.startsWith(normTerm)) {
        out.add(new KeyMatch(keyId, 2));
        seen.add(keyId);
      }
    }
    for (String keyId : index.keyIds()) {
      if (seen.contains(keyId)) {
        continue;
      }
      String display = normalize(displayName(index, keyId));
      String bare = normalize(bareName(keyId));
      if (display.contains(normTerm) || bare.contains(normTerm)) {
        out.add(new KeyMatch(keyId, 1));
        seen.add(keyId);
      }
    }
    return out;
  }

  // ---- Group-by counting (mirrors FleetPromotedKeysProvider) ----

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

  // ---- Action builders ----

  private static FleetApplyFilter applyFilter(FleetIndex index, String keyId, Filter filter) {
    return FleetApplyFilter.newBuilder()
        .setResultingFilter(filter)
        .setPillKey(pillKey(index, keyId))
        .setPillCondition(pillCondition(index, filter))
        .setMetadata(metadata(index, keyId))
        .build();
  }

  private static FleetOpenPicker openPickerNewChip(FleetIndex index, String keyId) {
    return FleetOpenPicker.newBuilder()
        .setKey(keyId)
        .setMetadata(metadata(index, keyId))
        .setNewChip(FleetNewChip.getDefaultInstance())
        .build();
  }

  private static FleetOpenPicker openPickerViewExisting(FleetIndex index, String keyId) {
    return FleetOpenPicker.newBuilder()
        .setKey(keyId)
        .setMetadata(metadata(index, keyId))
        .setViewExisting(FleetViewExisting.getDefaultInstance())
        .build();
  }

  private static FleetOpenPicker openPickerStaged(
      FleetIndex index, String keyId, ImmutableList<String> values) {
    return FleetOpenPicker.newBuilder()
        .setKey(keyId)
        .setMetadata(metadata(index, keyId))
        .setStagedModify(FleetStagedModification.newBuilder().addAllValues(values))
        .build();
  }

  private static FleetAddGroupBy addGroupBy(FleetIndex index, String keyId) {
    return FleetAddGroupBy.newBuilder().setKey(keyId).setPillKey(pillKey(index, keyId)).build();
  }

  private static FleetFilterChipMetadata metadata(FleetIndex index, String keyId) {
    return FleetFilterChipMetadata.newBuilder()
        .setKeyDisplayName(displayName(index, keyId))
        .setCanUseAdvanced(!VALUE_DISPLAY_KEYS.contains(keyId))
        .setIsPlural(PLURAL_DISPLAY_KEYS.contains(keyId))
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

  private static String label(FleetIndex index, String keyId, boolean inChip) {
    return inChip ? "Modify " + displayName(index, keyId) : "Add filter";
  }

  private static String displayName(FleetIndex index, String keyId) {
    return index.displayName(keyId);
  }

  private static String pillKey(FleetIndex index, String keyId) {
    String label = displayName(index, keyId);
    if (keyId.startsWith(DIM_PREFIX) && label.startsWith(DIM_DISPLAY_PREFIX)) {
      return label.substring(DIM_DISPLAY_PREFIX.length());
    }
    if (keyId.startsWith(PROP_PREFIX) && label.startsWith(PROP_DISPLAY_PREFIX)) {
      return "Host " + label.substring(PROP_DISPLAY_PREFIX.length());
    }
    return label;
  }

  private static String bareName(String keyId) {
    int separator = keyId.indexOf("::");
    return separator >= 0 ? keyId.substring(separator + 2) : keyId;
  }

  private static String displayValue(FleetIndex index, String keyId, String valueLower) {
    return index.valueDisplays(keyId).getOrDefault(valueLower, valueLower);
  }

  /**
   * Compact condition text for the resulting chip pill, mirroring {@link FleetChipResolver}. A
   * single value shows itself, several collapse to their count, an exclude is prefixed with the
   * not-equal sign, and a lone no-value entry reads as "empty" or "not empty".
   */
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

  /**
   * The bare {@code group by} candidate pool for the corpus's entity: the host pool for host
   * search, the device pool otherwise. The device branch returns the same {@link
   * #GROUP_BY_CANDIDATES} constant, so device group-by suggestions are unchanged.
   */
  private static ImmutableList<String> groupByCandidates(SearchCorpus corpus) {
    return corpus.entity() == SearchEntity.SEARCH_ENTITY_HOST
        ? HOST_GROUP_BY_CANDIDATES
        : GROUP_BY_CANDIDATES;
  }

  /**
   * The empty-query starter keys for the corpus's entity: the host keys for host search, the device
   * keys otherwise. The device branch returns the same {@link #EMPTY_STATE_KEYS} constant, so the
   * device empty state is unchanged.
   */
  private static ImmutableList<String> emptyStateKeys(SearchCorpus corpus) {
    return corpus.entity() == SearchEntity.SEARCH_ENTITY_HOST
        ? HOST_EMPTY_STATE_KEYS
        : EMPTY_STATE_KEYS;
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

  private static String normalize(String value) {
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

  private static ImmutableMap<String, ImmutableList<String>> buildAliasMap() {
    SetMultimap<String, String> map =
        MultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
    addAliases(map, FIELD_UUID, "uuid", "id", "device id", "device uuid");
    addAliases(map, FIELD_TYPE, "type(s)", "device type(s)");
    addAliases(map, FIELD_STATUS, "status", "device status");
    addAliases(map, FIELD_OWNER, "owner(s)", "device owner(s)");
    addAliases(
        map, "field::driver", "driver(s)", "supported driver(s)", "device supported driver(s)");
    addAliases(
        map,
        "field::decorator",
        "decorator(s)",
        "supported decorator(s)",
        "device supported decorator(s)");
    addAliases(map, "field::executor", "executor(s)", "device executor(s)");
    addAliases(map, "dim::os", "os", "device os");
    addAliases(map, "dim::model", "model", "device model");
    addAliases(map, "dim::sdk_version", "sdk version", "version");
    addAliases(map, "dim::software_version", "software version", "version");
    addAliases(map, "dim::device_form", "form", "device form");
    addAliases(map, "dim::quarantined", "quarantine", "quarantined");
    addAliases(map, "dim::device_class_name", "device class", "class", "device class name");
    addAliases(map, "dim::manufacturer", "manufacturer", "make", "brand");
    addAliases(
        map, "config::wifi_ssid", "wifi", "wi-fi", "ssid", "wifi ssid", "wi-fi ssid", "network");
    addAliases(map, "host::host_name", "host name", "hostname", "host");
    addAliases(map, "host::host_ip", "host ip", "ip");
    addAliases(map, "host::host_os", "host os");
    addAliases(map, "host::lab_type", "lab type");
    addAliases(map, "host::connectivity", "connectivity", "lab server connectivity");
    addAliases(map, "host::lab_server_activity", "activity", "lab server activity");
    addAliases(map, "host::daemon_status", "daemon", "daemon status", "daemon server status");
    addAliases(map, "host::release_status", "release status", "release");
    addAliases(map, "host::lab_server_version", "lab server version");
    addAliases(map, "host::release_type", "release type", "host release type");
    addAliases(map, "host::ats_controller", "controller", "ats controller", "ats lab");
    // Host device-count aliases. host::device_count is a host-only key, absent from the device
    // index, so these resolve to nothing under device search and only take effect for host search.
    addAliases(map, HOST_DEVICE_COUNT, "device count", "device_count", "devices");

    ImmutableMap.Builder<String, ImmutableList<String>> built = ImmutableMap.builder();
    for (Map.Entry<String, Collection<String>> entry : map.asMap().entrySet()) {
      built.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
    }
    return built.buildOrThrow();
  }

  private static void addAliases(SetMultimap<String, String> map, String keyId, String... aliases) {
    for (String alias : aliases) {
      for (String expanded : expandPlural(alias)) {
        map.put(normalize(expanded), keyId);
      }
    }
  }

  private static ImmutableList<String> expandPlural(String alias) {
    if (alias.contains("(s)")) {
      String base = alias.replace("(s)", "");
      return ImmutableList.of(base, base + "s");
    }
    return ImmutableList.of(alias);
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

  /** A matched key with its match-quality tier (3 exact, 2 prefix, 1 contains). */
  private record KeyMatch(String keyId, double tier) {}

  /** Per-request query context, so helpers avoid threading many parameters. */
  private record Context(
      SearchCorpus corpus,
      FleetIndex index,
      List<Filter> filters,
      boolean hasFilters,
      Set<String> activeKeys,
      ImmutableList<Integer> current,
      BitSet currentBits,
      ToIntFunction<String> keyPriority,
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
