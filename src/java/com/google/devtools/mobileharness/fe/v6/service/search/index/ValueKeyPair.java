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

/**
 * A (value, key) pair in the global semantic value index, sorted by value then key.
 *
 * <p>Used by the suggestion engine's PrefixAll path to bisect into all non-identifier values across
 * every key in a single sorted list, so a prefix search is O(log D_s) regardless of how many keys
 * contain matching values.
 */
public record ValueKeyPair(String value, String key) implements Comparable<ValueKeyPair> {

  @Override
  public int compareTo(ValueKeyPair other) {
    int cmp = value.compareTo(other.value);
    return cmp != 0 ? cmp : key.compareTo(other.key);
  }
}
