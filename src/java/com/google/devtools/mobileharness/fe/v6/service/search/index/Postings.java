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

package com.google.devtools.mobileharness.fe.v6.service.search.index;

import com.google.common.collect.ImmutableMap;

/**
 * Inverted index provider for fleet search: maps (keyId, value) pairs to record index arrays.
 *
 * <p>Posting lists hold record indices into the corpus (devices or hosts) in ascending order.
 * Implementations provide lazy on-demand index construction over the forward store ({@link
 * LazyPostings}) or composite overlay views.
 */
public interface Postings {

  /** Returns the posting list for (keyId, value), or an empty array if absent. */
  int[] get(String keyId, String value);

  /** Builds or returns cached posting lists for all values of a key. */
  ImmutableMap<String, int[]> forKey(String keyId);
}
