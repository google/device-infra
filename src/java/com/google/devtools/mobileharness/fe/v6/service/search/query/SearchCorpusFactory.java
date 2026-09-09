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

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DimensionOverlay;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.OverlayView;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.FleetSnapshotStore;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyRegistry;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Factory for constructing entity-specific {@link SearchCorpus} projections and accessing scenario
 * curations.
 */
@Singleton
public final class SearchCorpusFactory {

  private final FleetSnapshotStore store;
  private final Map<Fleet, ScenarioCuration> curations;

  @Inject
  SearchCorpusFactory(FleetSnapshotStore store, Map<Fleet, ScenarioCuration> curations) {
    this.store = checkNotNull(store);
    this.curations = checkNotNull(curations);
  }

  /**
   * Constructs a {@link SearchCorpus} for the specified fleet and search entity, bound to the
   * loaded overlays.
   */
  public SearchCorpus getCorpus(
      Fleet fleet, SearchEntity entity, ImmutableMap<String, DimensionOverlay> overlays) {
    if (entity == SearchEntity.SEARCH_ENTITY_HOST) {
      return new HostCorpus(store.get(fleet), store.hostPostings(fleet), curations.get(fleet));
    }
    FleetSnapshot snapshot = store.get(fleet);
    OverlayView overlayView = OverlayView.bind(snapshot, overlays);
    return new DeviceCorpus(snapshot, store.postings(fleet), curations.get(fleet), overlayView);
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
