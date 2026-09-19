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
import com.google.common.collect.ImmutableSetMultimap;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDescriptor;
import java.util.Collection;

/**
 * An index from what a user might type for a key to the keys it may mean, derived from the keys
 * themselves.
 *
 * <p>This class answers one question: for a typed key token, which of the given keys declare that
 * spelling, in declaration order? It holds no data of its own. Every entry comes from a
 * descriptor's {@link KeyDescriptor#display() display name} or {@link KeyDescriptor#aliases()
 * aliases}, so the table for a deployment is exactly the union of what that deployment's registry
 * declares: a key that a build does not register has no aliases in that build, and an entity's
 * table can only ever name that entity's keys. Nothing here is entity or deployment specific; each
 * {@link KeyVocabulary} builds its own table from its own registry.
 *
 * <p>Matching conventions: tokens and declared spellings are compared after {@link
 * KeyTokens#normalize}, and a declared alias containing {@code "(s)"} stands for both its singular
 * and plural forms, so {@code "owner(s)"} matches {@code owner} and {@code owners}. Two spellings
 * that normalize identically ({@code "device count"} and {@code "device_count"}) are one entry, and
 * a key is listed at most once under one spelling.
 *
 * <p>One spelling may map to several keys when the word is genuinely shared (for example {@code
 * version} for both SDK and software version); the vocabulary returns all of them and the suggester
 * ranks them. Iteration order follows the order of the keys given, so a registry's declaration
 * order decides which key is preferred.
 */
final class KeyAliasTable {

  private final ImmutableSetMultimap<String, KeyDescriptor> spellingToKeys;

  private KeyAliasTable(ImmutableSetMultimap<String, KeyDescriptor> spellingToKeys) {
    this.spellingToKeys = spellingToKeys;
  }

  /** Indexes the display name and aliases of every given key, in the given order. */
  static KeyAliasTable of(Collection<? extends KeyDescriptor> keys) {
    ImmutableSetMultimap.Builder<String, KeyDescriptor> spellingToKeys =
        ImmutableSetMultimap.builder();
    for (KeyDescriptor key : keys) {
      spellingToKeys.put(KeyTokens.normalize(key.display().name()), key);
      for (String alias : key.aliases()) {
        for (String form : singularAndPlural(alias)) {
          spellingToKeys.put(KeyTokens.normalize(form), key);
        }
      }
    }
    return new KeyAliasTable(spellingToKeys.build());
  }

  /**
   * The keys that declare {@code token} as a spelling (normalized before lookup), first key first.
   */
  ImmutableList<KeyDescriptor> lookup(String token) {
    return spellingToKeys.get(KeyTokens.normalize(token)).asList();
  }

  /** Every normalized spelling in the table. Exposed for tests. */
  ImmutableSet<String> spellings() {
    return spellingToKeys.keySet();
  }

  private static ImmutableList<String> singularAndPlural(String alias) {
    if (!alias.contains("(s)")) {
      return ImmutableList.of(alias);
    }
    String singular = alias.replace("(s)", "");
    return ImmutableList.of(singular, singular + "s");
  }
}
