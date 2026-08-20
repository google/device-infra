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
 * Immutable value index over one fleet's core snapshot records.
 *
 * <p>Built by {@link FleetIndexBuilder} and held by {@link FleetSnapshot}. Implements the {@link
 * FleetIndex} interface over in-memory ImmutableMaps.
 */
@AutoValue
public abstract class CoreFleetIndex implements FleetIndex {

  /** Internal map: key id to (normalized value to device count). */
  public abstract ImmutableMap<String, ImmutableMap<String, Integer>> valueCountsMap();

  /** Internal map: key id to its sorted distinct normalized values. */
  public abstract ImmutableMap<String, ImmutableList<String>> sortedValuesMap();

  /** Internal map: key id to (normalized value to first-seen original display value). */
  public abstract ImmutableMap<String, ImmutableMap<String, String>> valueDisplaysMap();

  /** Internal map: key id to human-readable display name. */
  public abstract ImmutableMap<String, String> displayNamesMap();

  @Override
  public ImmutableList<String> sortedValues(String keyId) {
    ImmutableList<String> values = sortedValuesMap().get(keyId);
    return values != null ? values : ImmutableList.of();
  }

  @Override
  public ImmutableMap<String, Integer> valueCounts(String keyId) {
    ImmutableMap<String, Integer> counts = valueCountsMap().get(keyId);
    return counts != null ? counts : ImmutableMap.of();
  }

  @Override
  public ImmutableMap<String, String> valueDisplays(String keyId) {
    ImmutableMap<String, String> displays = valueDisplaysMap().get(keyId);
    return displays != null ? displays : ImmutableMap.of();
  }

  @Override
  public String displayName(String keyId) {
    String name = displayNamesMap().get(keyId);
    return name != null ? name : FleetIndex.deriveDisplayName(keyId);
  }

  @Override
  public int valueCount(String keyId, String value) {
    ImmutableMap<String, Integer> values = valueCountsMap().get(keyId);
    return values == null ? 0 : values.getOrDefault(value, 0);
  }

  @Override
  public abstract ImmutableSet<String> keyIds();

  @Override
  public abstract ImmutableList<ValueKeyPair> semanticGlobalSorted();

  @Override
  public abstract ImmutableMap<String, ImmutableList<KeyCount>> globalExact();

  /** Creates a new builder. */
  public static Builder builder() {
    return new AutoValue_CoreFleetIndex.Builder();
  }

  /** An empty index, used by an empty {@link FleetSnapshot}. */
  public static CoreFleetIndex empty() {
    return builder()
        .setValueCountsMap(ImmutableMap.of())
        .setSortedValuesMap(ImmutableMap.of())
        .setValueDisplaysMap(ImmutableMap.of())
        .setDisplayNamesMap(ImmutableMap.of())
        .setKeyIds(ImmutableSet.of())
        .setSemanticGlobalSorted(ImmutableList.of())
        .setGlobalExact(ImmutableMap.of())
        .build();
  }

  /** Builder for {@link CoreFleetIndex}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setValueCountsMap(
        ImmutableMap<String, ImmutableMap<String, Integer>> valueCounts);

    public abstract Builder setSortedValuesMap(
        ImmutableMap<String, ImmutableList<String>> sortedValues);

    public abstract Builder setValueDisplaysMap(
        ImmutableMap<String, ImmutableMap<String, String>> valueDisplays);

    public abstract Builder setDisplayNamesMap(ImmutableMap<String, String> displayNames);

    public abstract Builder setKeyIds(ImmutableSet<String> keyIds);

    public abstract Builder setSemanticGlobalSorted(
        ImmutableList<ValueKeyPair> semanticGlobalSorted);

    public abstract Builder setGlobalExact(
        ImmutableMap<String, ImmutableList<KeyCount>> globalExact);

    public abstract CoreFleetIndex build();
  }
}
