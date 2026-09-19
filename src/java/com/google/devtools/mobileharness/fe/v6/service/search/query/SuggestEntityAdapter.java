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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.SetMultimap;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import java.util.List;
import java.util.Locale;

/**
 * Domain boundary contract between the entity-agnostic suggestion engine ({@link FleetSuggester})
 * and the entity-specific key schemas for Device Search ({@link DeviceSuggestAdapter}) and Host
 * Search ({@link HostSuggestAdapter}).
 *
 * <h2>Why this exists</h2>
 *
 * <p>The suggestion algorithm (intent dispatch, value matching, eligibility gating, BitSet
 * intersection counting, and two-phase ranking) is identical across Device Search and Host Search,
 * whereas key resolution, alias catalogs, namespace syntax, long-tail key discovery, and display
 * formatting differ fundamentally between devices and hosts. Encapsulating every key-domain
 * operation behind this interface keeps the suggestion engine free of entity type checks while
 * ensuring at compile time that Device dimensions never leak into Host Search and Host-only keys
 * never pollute Device Search.
 *
 * <h2>How to use it</h2>
 *
 * <ol>
 *   <li>At the entry point of {@link FleetSuggester#suggest}, bind the concrete adapter matching
 *       the request's {@link SearchCorpus}: {@link DeviceSuggestAdapter} for a {@link DeviceCorpus}
 *       or {@link HostSuggestAdapter} for a {@link HostCorpus}.
 *   <li>Pass the bound adapter through the per-request context to every pattern handler, condition
 *       builder, action builder, and ranking comparator.
 *   <li>Query the adapter for all key-domain operations rather than inspecting the corpus type or
 *       referencing entity key registries directly.
 * </ol>
 *
 * <h2>Adapter semantics</h2>
 *
 * <ul>
 *   <li><b>Domain isolation</b>: {@link #isKnownKey(String)} returns {@code true} only for key IDs
 *       belonging to the active search entity. Any candidate key outside that schema is rejected
 *       before condition construction or ranking.
 *   <li><b>Strict key resolution</b>: {@link #resolveKey(String, FleetIndex)} resolves an explicit
 *       key operand (from patterns such as {@code <key> is <value>} or {@code has <key>}) against
 *       entity-specific namespace prefixes, built-in alias tables, indexed dynamic keys, and
 *       discovered catalog keys.
 *   <li><b>Cold long-tail fallback</b>: When {@link #resolveKey(String, FleetIndex)} returns no
 *       matches in a key-value pattern, {@link #fallbackKvKeyIds(String)} synthesizes the
 *       entity-appropriate dynamic key ID ({@code dimension::<name>} for devices or {@code
 *       host_property::<name>} for hosts) unless the syntax is invalid for that entity.
 *   <li><b>Broad key-name matching</b>: {@link #matchKeys(String, FleetIndex)} returns exact (tier
 *       3), prefix (tier 2), and substring (tier 1) matches across all known and discovered keys
 *       for single-token key suggestions.
 * </ul>
 */
interface SuggestEntityAdapter {

  /** A matched key ID paired with its match-quality tier (3 exact, 2 prefix, 1 substring). */
  record KeyMatch(String keyId, double tier) {}

  /** Returns whether {@code keyId} is a valid, recognized key in this entity's search schema. */
  boolean isKnownKey(String keyId);

  /**
   * Resolves a user-typed key token into canonical key IDs for strict key-operand patterns ({@code
   * <key> is <value>}, {@code no <key>}, {@code has <key>}). Returns empty when the token matches
   * no known alias, namespace syntax, or indexed key for this entity.
   */
  ImmutableList<String> resolveKey(String token, FleetIndex index);

  /**
   * Returns fallback dynamic key IDs for key-value patterns when {@link #resolveKey(String,
   * FleetIndex)} returns empty, enabling users to filter on cold long-tail dimensions or host
   * properties before their overlays are loaded. Returns empty if {@code keyToken} uses a namespace
   * prefix forbidden in this entity.
   */
  ImmutableList<String> fallbackKvKeyIds(String keyToken);

  /**
   * Returns whether {@code keyId} is a valid dynamic key (dimension or host property) that is not
   * currently populated in {@code index}, qualifying it for count-free cold fallback suggestions.
   */
  boolean isColdUnindexedKey(String keyId, FleetIndex index);

  /**
   * Matches {@code token} against all keys available to this entity (exact aliases at tier 3,
   * prefix matches at tier 2, and substring matches at tier 1).
   */
  List<KeyMatch> matchKeys(String token, FleetIndex index);

  /** Returns the human-readable title display name for {@code keyId} (e.g. {@code "Status"}). */
  String titleDisplayName(String keyId);

  /** Returns the unprefixed bare name of {@code keyId} (e.g. {@code "sdk_version"}). */
  String bareName(String keyId);

  /** Returns the compact chip pill label for {@code keyId}. */
  String pillKey(String keyId);

  /** Returns whether {@code keyId} takes plural verbs ({@code "are"} / {@code "are not"}). */
  boolean isPlural(String keyId);

  /** Returns the scenario key priority tier for {@code keyId} used as the primary sort key. */
  int keyPriority(String keyId);

  /** Returns the curated default group-by key IDs when the user types bare {@code "group by"}. */
  ImmutableList<String> defaultGroupByCandidates();

  /** Normalizes a key or alias token by lowercasing and collapsing whitespace and underscores. */
  static String normalize(String value) {
    return value.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "_");
  }

  /** Registers one or more aliases (expanding {@code "(s)"} plurals) for {@code keyId}. */
  static void addAliases(SetMultimap<String, String> map, String keyId, String... aliases) {
    for (String alias : aliases) {
      if (alias.contains("(s)")) {
        String base = alias.replace("(s)", "");
        map.put(normalize(base), keyId);
        map.put(normalize(base + "s"), keyId);
      } else {
        map.put(normalize(alias), keyId);
      }
    }
  }
}
