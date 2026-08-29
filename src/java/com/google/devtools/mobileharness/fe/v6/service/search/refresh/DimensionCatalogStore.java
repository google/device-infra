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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Thread-safe cache storing the complete set of device dimension names discovered across fleets.
 *
 * <p>Updated periodically (every 1 hour) by {@link DimensionCatalogRefresher}. Provides O(1)
 * membership checks and full name sets to query-layer consumers (such as {@code FleetSuggester} and
 * {@code FleetColumnCataloger}) so long-tail dimensions can be suggested and added as columns
 * without pulling full dimension values fleet-wide upfront.
 */
@Singleton
public final class DimensionCatalogStore {

  private final ConcurrentMap<Fleet, ImmutableSet<String>> dimensionNames =
      new ConcurrentHashMap<>();

  @Inject
  @VisibleForTesting
  public DimensionCatalogStore() {}

  /** Returns all discovered dimension names for the specified fleet. */
  public ImmutableSet<String> getDimensionNames(Fleet fleet) {
    Fleet normalized = fleet == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : fleet;
    return dimensionNames.getOrDefault(normalized, ImmutableSet.of());
  }

  /** Checks whether the given dimension name exists in the specified fleet. */
  public boolean hasDimension(Fleet fleet, String dimensionName) {
    return getDimensionNames(fleet).contains(dimensionName);
  }

  /** Updates the discovered dimension names for the specified fleet. */
  public void setDimensionNames(Fleet fleet, Collection<String> names) {
    Fleet normalized = fleet == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : fleet;
    dimensionNames.put(normalized, ImmutableSet.copyOf(names));
  }
}
