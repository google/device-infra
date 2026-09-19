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

package com.google.devtools.mobileharness.fe.v6.service.search.schema;

import com.google.common.collect.ImmutableSet;

/**
 * What every search key descriptor exposes regardless of entity.
 *
 * <p>{@link DeviceKeyDescriptor} and {@link HostKeyDescriptor} carry entity-specific extraction
 * metadata, but code that merely names, displays, or ranks a key (the suggester, chip resolution,
 * column catalogs) needs only the facts below. Such code holds a {@code KeyDescriptor} and asks the
 * entity's {@code KeyVocabulary} for anything that depends on which entity is being searched, so it
 * never handles a bare key id string and never branches on the descriptor's concrete type.
 */
public interface KeyDescriptor {

  /** The unique namespaced identifier, for example {@code "dimension::pool"}. */
  String id();

  /** The display name and plural grammar for the entity this descriptor belongs to. */
  KeyDisplay display();

  /**
   * The spellings, besides the display name, that a user may type to name this key in the search
   * bar of the entity this descriptor belongs to. Declared where the key is declared, so a key that
   * exists only in one deployment carries its aliases only there, and a host key projected into
   * device search may answer to different words than it does in host search. An alias containing
   * {@code "(s)"} stands for both its singular and plural forms. Comparison is by {@code
   * KeyTokens.normalize}, so case, whitespace and underscores do not matter.
   */
  ImmutableSet<String> aliases();

  /** Whether the descriptor was minted for a discovered key rather than declared in a catalog. */
  boolean isLongTail();

  /**
   * Whether the key is a host property (long-tail or curated), as opposed to a field or dimension.
   */
  boolean isHostProperty();

  /** The id without its namespace prefix, for example {@code "pool"}. */
  String bareName();
}
