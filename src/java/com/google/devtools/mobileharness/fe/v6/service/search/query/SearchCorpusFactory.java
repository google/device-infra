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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DimensionOverlay;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.OverlayView;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionCatalogStore;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.FleetSnapshotStore;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyRegistry;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Factory for constructing entity-specific {@link SearchCorpus} projections and accessing scenario
 * curations.
 *
 * <p>A corpus is built per request, so anything that depends only on the fleet is built here, once,
 * and handed to every corpus of that fleet: the key registries and the alias tables derived from
 * their built-in keys. A device corpus also receives the fleet's discovered dimension names from
 * the {@link DimensionCatalogStore}, so the suggestion path learns about undiscovered dimensions
 * through the corpus vocabulary rather than by reading the catalog store itself.
 */
@Singleton
public final class SearchCorpusFactory {

  /** The fleet-scoped key collaborators of both entities, built once per fleet. */
  private record KeyTables(
      DeviceKeyRegistry deviceRegistry,
      KeyAliasTable deviceAliases,
      HostKeyRegistry hostRegistry,
      KeyAliasTable hostAliases) {

    static KeyTables forCuration(@Nullable ScenarioCuration curation) {
      DeviceKeyRegistry deviceRegistry = DeviceCorpus.registryFor(curation);
      HostKeyRegistry hostRegistry = HostCorpus.registryFor(curation);
      return new KeyTables(
          deviceRegistry,
          KeyAliasTable.of(deviceRegistry.builtInKeys()),
          hostRegistry,
          KeyAliasTable.of(hostRegistry.builtInKeys()));
    }
  }

  private final FleetSnapshotStore store;
  private final Map<Fleet, ScenarioCuration> curations;
  private final DimensionCatalogStore dimensionCatalogStore;
  private final ImmutableMap<Fleet, KeyTables> keyTables;

  /** Tables for a fleet without an installed curation: the standalone ATS registries. */
  private final KeyTables defaultKeyTables = KeyTables.forCuration(null);

  @Inject
  SearchCorpusFactory(
      FleetSnapshotStore store,
      Map<Fleet, ScenarioCuration> curations,
      DimensionCatalogStore dimensionCatalogStore) {
    this.store = checkNotNull(store);
    this.curations = checkNotNull(curations);
    this.dimensionCatalogStore = checkNotNull(dimensionCatalogStore);
    this.keyTables =
        curations.entrySet().stream()
            .collect(
                toImmutableMap(
                    Map.Entry::getKey, entry -> KeyTables.forCuration(entry.getValue())));
  }

  /**
   * Constructs a {@link SearchCorpus} for the specified fleet and search entity, bound to the
   * loaded overlays.
   */
  public SearchCorpus getCorpus(
      Fleet fleet, SearchEntity entity, ImmutableMap<String, DimensionOverlay> overlays) {
    ScenarioCuration curation = curations.get(fleet);
    KeyTables tables = keyTables.getOrDefault(fleet, defaultKeyTables);
    if (entity == SearchEntity.SEARCH_ENTITY_HOST) {
      return new HostCorpus(
          store.get(fleet),
          store.hostPostings(fleet),
          curation,
          tables.hostRegistry(),
          tables.hostAliases());
    }
    FleetSnapshot snapshot = store.get(fleet);
    OverlayView overlayView = OverlayView.bind(snapshot, overlays);
    return new DeviceCorpus(
        snapshot,
        store.postings(fleet),
        curation,
        tables.deviceRegistry(),
        tables.deviceAliases(),
        overlayView,
        dimensionCatalogStore.getDimensionNames(fleet));
  }

  /** Constructs a {@link SearchCorpus} without overlay data (e.g. for suggestions or catalogs). */
  public SearchCorpus getCorpus(Fleet fleet, SearchEntity entity) {
    return getCorpus(fleet, entity, ImmutableMap.of());
  }

  /** Returns the {@link ScenarioCuration} for the specified fleet, or null if uninstalled. */
  @Nullable
  public ScenarioCuration getCuration(Fleet fleet) {
    return curations.get(fleet);
  }

  /** Returns the {@link DeviceKeyRegistry} for the specified fleet, or null if uninstalled. */
  @Nullable
  public DeviceKeyRegistry getDeviceKeyRegistry(Fleet fleet) {
    ScenarioCuration curation = curations.get(fleet);
    return curation != null ? curation.deviceKeyRegistry() : null;
  }

  /** Returns the serving {@link FleetSnapshot} for the specified fleet. */
  public FleetSnapshot getSnapshot(Fleet fleet) {
    return store.get(fleet);
  }
}
