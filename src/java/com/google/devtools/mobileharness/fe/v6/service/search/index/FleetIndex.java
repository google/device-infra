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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Value index over one fleet's devices.
 *
 * <p>Built by {@link FleetIndexBuilder} from a {@code FleetRawData} and held by {@link
 * FleetSnapshot}. Serves value resolution and facet counts from memory. Posting lists (device index
 * arrays per key-value pair) are built lazily by {@link LazyPostings} on first access rather than
 * at index build time, which keeps the build under 2 seconds for 152K devices.
 *
 * <p>All structures key values by a normalized (lowercased) form so lookups are case-insensitive;
 * {@link #valueDisplays()} keeps the original casing for presentation.
 *
 * <p>Keys are identified by a namespaced key id, matching the search prototype: {@code
 * field::<name>} for built-in device fields (uuid, status, owner, type, driver, decorator,
 * executor), {@code dim::<name>} for composite dimensions, {@code prop::<name>} for host
 * properties, {@code host::<name>} for cross-entity host attributes joined onto each device, and
 * {@code config::<name>} for config-service fields such as wifi_ssid.
 */
@AutoValue
public abstract class FleetIndex {

  /**
   * Key id to (normalized value to device count). The count is a distinct-device count: a device
   * that lists the same value twice contributes one.
   */
  public abstract ImmutableMap<String, ImmutableMap<String, Integer>> valueCounts();

  /** Key id to its sorted distinct normalized values. Used for prefix matching. */
  public abstract ImmutableMap<String, ImmutableList<String>> sortedValues();

  /** Key id to (normalized value to first-seen original display value). */
  public abstract ImmutableMap<String, ImmutableMap<String, String>> valueDisplays();

  /** All key ids present in this fleet. */
  public abstract ImmutableSet<String> keyIds();

  /** Key id to human-readable display name. */
  public abstract ImmutableMap<String, String> displayNames();

  /**
   * All (value, key) pairs from non-{@link FleetSearchKeys#PLAIN_VALUE_KEYS} keys, sorted by value
   * then key. The suggestion engine bisects into this list for O(log D_s) prefix matching across
   * all semantic keys simultaneously.
   */
  public abstract ImmutableList<ValueKeyPair> semanticGlobalSorted();

  /**
   * Normalized value to the list of (key, count) pairs that carry that value. Covers all keys (not
   * just semantic), enabling O(1) exact-match lookup for the suggestion engine.
   */
  public abstract ImmutableMap<String, ImmutableList<KeyCount>> globalExact();

  /** Returns the device count for a value, or 0 if the key or value is absent. */
  public int valueCount(String keyId, String value) {
    ImmutableMap<String, Integer> values = valueCounts().get(keyId);
    return values == null ? 0 : values.getOrDefault(value, 0);
  }

  /** Creates a new builder. */
  public static Builder builder() {
    return new AutoValue_FleetIndex.Builder();
  }

  /** An empty index, used by an empty {@link FleetSnapshot}. */
  public static FleetIndex empty() {
    return builder()
        .setValueCounts(ImmutableMap.of())
        .setSortedValues(ImmutableMap.of())
        .setValueDisplays(ImmutableMap.of())
        .setKeyIds(ImmutableSet.of())
        .setDisplayNames(ImmutableMap.of())
        .setSemanticGlobalSorted(ImmutableList.of())
        .setGlobalExact(ImmutableMap.of())
        .build();
  }

  /** Builder for {@link FleetIndex}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setValueCounts(
        ImmutableMap<String, ImmutableMap<String, Integer>> valueCounts);

    public abstract Builder setSortedValues(
        ImmutableMap<String, ImmutableList<String>> sortedValues);

    public abstract Builder setValueDisplays(
        ImmutableMap<String, ImmutableMap<String, String>> valueDisplays);

    public abstract Builder setKeyIds(ImmutableSet<String> keyIds);

    public abstract Builder setDisplayNames(ImmutableMap<String, String> displayNames);

    public abstract Builder setSemanticGlobalSorted(
        ImmutableList<ValueKeyPair> semanticGlobalSorted);

    public abstract Builder setGlobalExact(
        ImmutableMap<String, ImmutableList<KeyCount>> globalExact);

    public abstract FleetIndex build();
  }
}
