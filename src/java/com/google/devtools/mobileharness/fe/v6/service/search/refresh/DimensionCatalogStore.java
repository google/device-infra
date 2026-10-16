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

package com.google.devtools.mobileharness.fe.v6.service.search.refresh;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Thread-safe cache storing the complete set of device dimension names and their device counts
 * discovered across fleets.
 *
 * <p>Updated periodically (every 1 hour) by {@link DimensionCatalogRefresher}. Provides O(1)
 * membership checks, full name sets, and device coverage counts to query-layer consumers (such as
 * {@code FleetSuggester}, {@code FleetColumnCataloger}, {@code FleetFilterKeyCataloger}, and {@code
 * FleetGroupByKeyCataloger}) so long-tail dimensions can be suggested and listed with accurate
 * coverage counts without pulling full dimension values fleet-wide upfront.
 */
@Singleton
public final class DimensionCatalogStore {

  private final ConcurrentMap<Fleet, ImmutableMap<String, Integer>> dimensionCounts =
      new ConcurrentHashMap<>();

  @Inject
  @VisibleForTesting
  public DimensionCatalogStore() {}

  /** Returns all discovered dimension names and their device counts for the specified fleet. */
  public ImmutableMap<String, Integer> getDimensionCounts(Fleet fleet) {
    return dimensionCounts.getOrDefault(normalize(fleet), ImmutableMap.of());
  }

  /** Returns all discovered dimension names for the specified fleet. */
  public ImmutableSet<String> getDimensionNames(Fleet fleet) {
    return getDimensionCounts(fleet).keySet();
  }

  /** Checks whether the given dimension name exists in the specified fleet. */
  public boolean hasDimension(Fleet fleet, String dimensionName) {
    return getDimensionCounts(fleet).containsKey(dimensionName);
  }

  /** Updates the discovered dimension names and their device counts for the specified fleet. */
  public void setDimensionCounts(Fleet fleet, Map<String, Integer> counts) {
    dimensionCounts.put(normalize(fleet), ImmutableMap.copyOf(counts));
  }

  /** Updates the discovered dimension names for the specified fleet with unknown (0) counts. */
  @VisibleForTesting
  public void setDimensionNames(Fleet fleet, Collection<String> names) {
    dimensionCounts.put(
        normalize(fleet), names.stream().distinct().collect(toImmutableMap(name -> name, n -> 0)));
  }

  private static Fleet normalize(Fleet fleet) {
    return fleet == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : fleet;
  }
}
