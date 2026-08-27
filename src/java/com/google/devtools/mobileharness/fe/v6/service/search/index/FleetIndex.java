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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Value index interface over a fleet's devices or hosts.
 *
 * <p>Serves value resolution, counts, sorted values, and facet counts from memory. Keys are
 * identified by canonical namespaced key IDs (e.g. {@code device_field::status}, {@code
 * dimension::model}, {@code host_property::rack_id}, {@code host_field::host_name}, {@code
 * device_config::wifi_ssid}).
 *
 * <p>Values are lowercased for case-insensitive lookup.
 */
public interface FleetIndex {

  /** Returns the sorted distinct normalized values for a key, or an empty list if absent. */
  ImmutableList<String> sortedValues(String keyId);

  /**
   * Returns the (normalized value -> distinct-device count) map for a key, or an empty map if
   * absent.
   */
  ImmutableMap<String, Integer> valueCounts(String keyId);

  /**
   * Returns the (normalized value -> original display value) map for a key, or an empty map if
   * absent.
   */
  ImmutableMap<String, String> valueDisplays(String keyId);

  /** Returns the device/record count for a value, or 0 if the key or value is absent. */
  int valueCount(String keyId, String value);

  /** Returns all key ids available in this index. */
  ImmutableSet<String> keyIds();

  /**
   * All (value, key) pairs from non-identifier semantic keys, sorted by value then key. Used for
   * O(log D_s) global value prefix matching in Pattern 4.
   */
  ImmutableList<ValueKeyPair> semanticGlobalSorted();

  /**
   * Normalized value to the list of (key, count) pairs that carry that value. Used for O(1) exact
   * global value lookup in Pattern 4.
   */
  ImmutableMap<String, ImmutableList<KeyCount>> globalExact();
}
