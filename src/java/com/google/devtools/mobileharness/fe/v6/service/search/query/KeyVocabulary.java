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
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDescriptor;
import java.util.Optional;

/**
 * The complete set of keys one search entity understands, and every fact the suggester may ask
 * about them.
 *
 * <p>This interface is the single boundary between the entity-neutral suggestion algorithm and the
 * entity-specific key world. The algorithm resolves typed text, enumerates candidate keys, formats
 * display names, and ranks, exclusively through a vocabulary; it never inspects a key id prefix,
 * never consults an alias table, and never branches on the corpus type. As a result a key can only
 * reach a suggestion if the vocabulary for the searched entity produced it.
 *
 * <p>Keys travel as {@link KeyDescriptor}s. The only place a bare key id string enters is {@link
 * #describe(String)}, because the index and the wire protos are string-keyed; everything else
 * returns and accepts descriptors. Facts that depend on the entity as well as the key (the display
 * title, the pill key, whether the key may be cold) are methods here rather than descriptor fields,
 * because the same host attribute reads "Host Lab Type" in device search and "Lab Type" in host
 * search.
 *
 * <p><b>Invariant, guaranteed by every implementation:</b> no method ever returns a descriptor the
 * entity's key registry does not recognize. A device vocabulary never yields {@code
 * host_field::device_count}; a host vocabulary never yields {@code dimension::model}. Keys are also
 * filtered by deployment: an alias whose key the current registry lacks (for example {@code owner}
 * under a standalone ATS build) resolves to nothing rather than to an unknown key.
 *
 * <p>A vocabulary is bound to one corpus: it reads that corpus's index to decide which bare names
 * name a real key, and its curation to rank and to offer group-by candidates. Obtain it from {@link
 * SearchCorpus#vocabulary()}.
 */
public interface KeyVocabulary {

  /** The entity whose keys this vocabulary describes. */
  SearchEntity entity();

  /**
   * Resolves a typed key token to the keys it denotes, most preferred first, or empty if the token
   * names nothing in this entity.
   *
   * <p>Resolution order: an explicit namespace spelling ({@code dimension:x}, {@code host
   * property:x}), then the entity's alias table, then a bare token equal to a key present in the
   * index or discovered catalog. Namespaces the entity does not support are rejected outright, so
   * {@code dimension:battery_status} resolves to nothing in host search.
   */
  ImmutableList<KeyDescriptor> resolve(String token);

  /**
   * Every key eligible for prefix and substring matching by display name: keys present in the index
   * plus keys discovered from data but not yet indexed.
   */
  ImmutableSet<KeyDescriptor> discoverableKeys();

  /**
   * The descriptor for a key id taken from the index or a request, or empty if the key is not part
   * of this entity. This is the sole crossing from string ids into descriptors.
   */
  Optional<KeyDescriptor> describe(String keyId);

  /** Whether the key id belongs to this entity. Equivalent to {@code describe(keyId)} present. */
  default boolean knows(String keyId) {
    return describe(keyId).isPresent();
  }

  /**
   * The name shown for the key in this entity's suggestions and dialog titles. Long-tail keys carry
   * their category prefix ({@code "Dimension pool"}, {@code "Host Property rack"}).
   */
  String titleDisplayName(KeyDescriptor key);

  /** The short label shown on the key's filter chip pill in this entity. */
  String pillKey(KeyDescriptor key);

  /**
   * Whether the key may legitimately have no index entries yet because its values are pulled on
   * demand (a dimension or host property). Only such keys may receive an uncounted cold filter
   * suggestion.
   */
  boolean isColdCapable(KeyDescriptor key);

  /**
   * The curated ranking of the key for this entity; higher is offered earlier. Deployments without
   * a curation rank every key equally (zero).
   */
  int priority(KeyDescriptor key);

  /** The curated keys offered when the user types a bare {@code group by}, in display order. */
  ImmutableList<KeyDescriptor> groupByCandidates();

  /**
   * The key this entity denotes for an unresolved bare token, or empty if the token is blank or
   * uses a namespace spelling. Device search mints a dimension, host search mints a host property.
   * This is the last resort of key resolution and lets a user filter on a key the catalog has not
   * discovered yet. If the name is one the deployment declares, the built-in descriptor is returned
   * rather than a long-tail twin, so a filter typed as {@code model is pixel} never lands on a
   * second {@code model} key. A namespaced token that did not resolve names a family this entity
   * lacks, so nothing is minted for it.
   */
  Optional<KeyDescriptor> mintLongTailKey(String bareName);
}
