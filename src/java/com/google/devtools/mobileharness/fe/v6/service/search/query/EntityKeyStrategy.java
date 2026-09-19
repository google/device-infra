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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import java.util.Optional;

/**
 * Encapsulates all entity-specific key semantics, aliases, and display formatting for search
 * suggestions.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Search entities (devices versus hosts) operate in fundamentally different key spaces:
 *
 * <ul>
 *   <li><b>Device Search</b> uses {@code device_field::}, {@code dimension::}, {@code
 *       device_config::}, and projected host attributes. It supports discovered long-tail
 *       dimensions from a global catalog store and {@code dimension:<name>} syntax.
 *   <li><b>Host Search</b> uses only {@code host_field::} and {@code host_property::}. Hosts have
 *       no dimensions, and device dimensions must never leak into host search suggestions.
 * </ul>
 *
 * <p>By encapsulating entity-specific key rules behind this interface, the common suggestion engine
 * ({@link SuggesterEngine}) operates on pure, entity-agnostic query parsing, posting counts, and
 * ranking algorithms without scattering {@code instanceof} checks across the codebase.
 *
 * <h2>How to use it</h2>
 *
 * <p>Implementations are provided by {@link DeviceSuggester} for device search and {@link
 * HostSuggester} for host search. The engine queries this strategy during syntax dispatch and
 * candidate generation:
 *
 * <ul>
 *   <li>{@link #resolveKey}: Resolves aliases, explicit namespaces, or bare names into canonical
 *       key IDs for this entity.
 *   <li>{@link #matchDiscoveredPrefixKeys} and {@link #matchDiscoveredContainsKeys}: Matches
 *       unindexed long-tail keys (such as device dimensions) against an external catalog store.
 *       Returns empty for entities without dynamic catalogs.
 *   <li>{@link #isColdLongTailKey}: Decides whether an unindexed key qualifies for the uncounted
 *       long-tail fallback suggestion.
 *   <li>{@link #displayName}, {@link #pillKey}, {@link #bareName}: Formats key labels using
 *       entity-specific display rules.
 * </ul>
 */
public interface EntityKeyStrategy {

  /**
   * Resolves a key token (from aliases, explicit namespace prefixes, or bare names) to canonical
   * key IDs recognized by this entity. Every returned key ID is guaranteed to be known to the
   * corpus registry.
   */
  ImmutableList<String> resolveKey(SearchCorpus corpus, String token);

  /**
   * Synthesizes an arbitrary dynamic key ID for this entity from an unknown bare token when no
   * registered keys matched (e.g. {@code dimension::<name>} for devices, {@code
   * host_property::<name>} for hosts). Returns empty if the token is invalid or explicitly
   * forbidden for this entity.
   */
  Optional<String> synthesizeDynamicKeyId(String bareToken);

  /**
   * Discovers matching unindexed keys from an external catalog store for prefix queries. For
   * devices this queries discovered dimensions; for hosts this returns empty.
   */
  ImmutableList<String> matchDiscoveredPrefixKeys(Fleet fleet, String normalizedTerm);

  /**
   * Discovers matching unindexed keys from an external catalog store for substring and contains
   * queries. For devices this queries discovered dimensions; for hosts this returns empty.
   */
  ImmutableList<String> matchDiscoveredContainsKeys(Fleet fleet, String normalizedTerm);

  /**
   * Checks whether the given key is an unindexed dynamic dimension or property that qualifies for
   * the uncounted cold long-tail fallback suggestion. Built-in fields and already-indexed keys
   * return false.
   */
  boolean isColdLongTailKey(SearchCorpus corpus, String keyId);

  /** Checks whether the key is known and valid for this entity. */
  boolean isKeyKnown(SearchCorpus corpus, String keyId);

  /** Checks whether the key represents a multi-valued attribute that uses plural grammar. */
  boolean isPlural(SearchCorpus corpus, String keyId);

  /** Formats the full display name of a key for suggestion labels and titles. */
  String displayName(SearchCorpus corpus, String keyId);

  /** Returns the bare attribute name without its namespace prefix. */
  String bareName(SearchCorpus corpus, String keyId);

  /** Returns the compact chip pill label (namespace prefix stripped). */
  String pillKey(SearchCorpus corpus, String keyId);

  /** Returns scenario key priority for ranking: higher values sort earlier. */
  int keyPriority(SearchCorpus corpus, Fleet fleet, String keyId);

  /** Returns the curated group-by candidate keys for this entity when typing "group by". */
  ImmutableList<String> groupByCandidates(SearchCorpus corpus, Fleet fleet);
}
