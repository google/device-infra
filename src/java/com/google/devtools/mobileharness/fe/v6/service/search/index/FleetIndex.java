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
 * Inverted index and value index over one fleet's devices.
 *
 * <p>Built by {@link FleetIndexBuilder} from a {@code FleetRawData} and held by {@link
 * FleetSnapshot}. Serves value resolution, facet counts, and posting-list set operations from
 * memory. All structures key values by a normalized (lowercased) form so lookups are
 * case-insensitive; {@link #valueDisplays()} keeps the original casing for presentation.
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

  /**
   * Key id to (normalized value to posting list). Each posting list is the device indices (into
   * {@code FleetSnapshot.devices()}) that carry the value, in ascending order of insertion.
   */
  public abstract ImmutableMap<String, ImmutableMap<String, ImmutableList<Integer>>> postings();

  /** Key id to its sorted distinct normalized values. Used for prefix matching. */
  public abstract ImmutableMap<String, ImmutableList<String>> sortedValues();

  /** Key id to (normalized value to first-seen original display value). */
  public abstract ImmutableMap<String, ImmutableMap<String, String>> valueDisplays();

  /** All key ids present in this fleet. */
  public abstract ImmutableSet<String> keyIds();

  /** Key id to human-readable display name. */
  public abstract ImmutableMap<String, String> displayNames();

  /** Returns the device count for a value, or 0 if the key or value is absent. */
  public int valueCount(String keyId, String value) {
    ImmutableMap<String, Integer> values = valueCounts().get(keyId);
    return values == null ? 0 : values.getOrDefault(value, 0);
  }

  /** Returns the posting list for a value, or an empty list if the key or value is absent. */
  public ImmutableList<Integer> postingList(String keyId, String value) {
    ImmutableMap<String, ImmutableList<Integer>> values = postings().get(keyId);
    return values == null ? ImmutableList.of() : values.getOrDefault(value, ImmutableList.of());
  }

  /** Creates a new builder. */
  public static Builder builder() {
    return new AutoValue_FleetIndex.Builder();
  }

  /** An empty index, used by an empty {@link FleetSnapshot}. */
  public static FleetIndex empty() {
    return builder()
        .setValueCounts(ImmutableMap.of())
        .setPostings(ImmutableMap.of())
        .setSortedValues(ImmutableMap.of())
        .setValueDisplays(ImmutableMap.of())
        .setKeyIds(ImmutableSet.of())
        .setDisplayNames(ImmutableMap.of())
        .build();
  }

  /** Builder for {@link FleetIndex}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setValueCounts(
        ImmutableMap<String, ImmutableMap<String, Integer>> valueCounts);

    public abstract Builder setPostings(
        ImmutableMap<String, ImmutableMap<String, ImmutableList<Integer>>> postings);

    public abstract Builder setSortedValues(
        ImmutableMap<String, ImmutableList<String>> sortedValues);

    public abstract Builder setValueDisplays(
        ImmutableMap<String, ImmutableMap<String, String>> valueDisplays);

    public abstract Builder setKeyIds(ImmutableSet<String> keyIds);

    public abstract Builder setDisplayNames(ImmutableMap<String, String> displayNames);

    public abstract FleetIndex build();
  }
}
