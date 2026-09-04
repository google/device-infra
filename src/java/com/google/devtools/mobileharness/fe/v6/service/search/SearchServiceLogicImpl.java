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

package com.google.devtools.mobileharness.fe.v6.service.search;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetChipResolverRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetChipResolverResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFlatResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFlatView;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupExpandView;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupHeaderView;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupedResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedKeysRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedKeysResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetValueListRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetValueListResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DimensionOverlay;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.OverlayView;
import com.google.devtools.mobileharness.fe.v6.service.search.query.DeviceCorpus;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetChipResolver;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetColumnCataloger;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetFlatSearcher;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetGroupSearcher;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetPromotedKeysProvider;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetSearchConfigProvider;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetSuggester;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetValueLister;
import com.google.devtools.mobileharness.fe.v6.service.search.query.HostCorpus;
import com.google.devtools.mobileharness.fe.v6.service.search.query.ScenarioCuration;
import com.google.devtools.mobileharness.fe.v6.service.search.query.SearchCorpus;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionOverlayStore;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.FleetSnapshotStore;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * In-memory implementation of {@link SearchServiceLogic}.
 *
 * <p>Every Fleet RPC reads the current serving {@link FleetSnapshot} for the request's fleet from
 * the {@link FleetSnapshotStore} and hands it to the matching query class. The query classes are
 * synchronous and scan the whole in-memory snapshot, which is large enough that the scan must not
 * run on the serving thread. Each heavy method therefore submits its snapshot fetch and query call
 * to the injected {@link ListeningExecutorService} and returns the resulting future immediately, so
 * the scan runs on the pool. {@link #getFleetSearchConfig} builds a small config rather than
 * scanning, so it returns an already completed future without an executor hop.
 *
 * <p>The request's fleet is normalized so {@code FLEET_UNSPECIFIED} reads as {@code FLEET_SELF},
 * matching the {@code Fleet} proto's default. {@link FleetSuggester} and {@link
 * FleetPromotedKeysProvider} inject the curation map themselves and resolve the curation from the
 * request's fleet, so they take only the snapshot and request. {@link FleetSearchConfigProvider}
 * takes the curation as a parameter, so the config method looks it up from the injected map here.
 */
@Singleton
public final class SearchServiceLogicImpl implements SearchServiceLogic {

  private final ListeningExecutorService executor;
  private final FleetSnapshotStore store;
  private final DimensionOverlayStore overlayStore;
  private final Map<Fleet, ScenarioCuration> curations;
  private final FleetSearchConfigProvider searchConfigProvider;
  private final FleetFlatSearcher flatSearcher;
  private final FleetGroupSearcher groupSearcher;
  private final FleetSuggester suggester;
  private final FleetChipResolver chipResolver;
  private final FleetValueLister valueLister;
  private final FleetPromotedKeysProvider promotedKeysProvider;
  private final FleetColumnCataloger columnCataloger;

  @Inject
  SearchServiceLogicImpl(
      ListeningExecutorService executor,
      FleetSnapshotStore store,
      DimensionOverlayStore overlayStore,
      Map<Fleet, ScenarioCuration> curations,
      FleetSearchConfigProvider searchConfigProvider,
      FleetFlatSearcher flatSearcher,
      FleetGroupSearcher groupSearcher,
      FleetSuggester suggester,
      FleetChipResolver chipResolver,
      FleetValueLister valueLister,
      FleetPromotedKeysProvider promotedKeysProvider,
      FleetColumnCataloger columnCataloger) {
    this.executor = executor;
    this.store = store;
    this.overlayStore = overlayStore;
    this.curations = curations;
    this.searchConfigProvider = searchConfigProvider;
    this.flatSearcher = flatSearcher;
    this.groupSearcher = groupSearcher;
    this.suggester = suggester;
    this.chipResolver = chipResolver;
    this.valueLister = valueLister;
    this.promotedKeysProvider = promotedKeysProvider;
    this.columnCataloger = columnCataloger;
  }

  @Override
  public ListenableFuture<FleetSearchConfig> getFleetSearchConfig(
      FleetSearchConfigRequest request) {
    Fleet fleet = normalize(request.getFleet());
    FleetSnapshot snapshot = store.get(fleet);
    ScenarioCuration curation = curations.get(fleet);
    if (curation == null) {
      // The curation map is wired at activation. Until it is installed, return an empty config
      // rather than failing, so a page load before wiring degrades gracefully.
      return immediateFuture(FleetSearchConfig.getDefaultInstance());
    }
    return immediateFuture(searchConfigProvider.getConfig(snapshot, request, curation));
  }

  @Override
  public ListenableFuture<FleetSearchResults> searchFleet(FleetSearchRequest request) {
    Fleet fleet = normalize(request.getFleet());
    Set<String> referencedKeys = extractReferencedKeys(request);
    return Futures.transformAsync(
        overlayStore.loadOverlaysAsync(fleet, referencedKeys, executor),
        overlays -> Futures.submit(() -> searchFleetSync(request, overlays), executor),
        executor);
  }

  private FleetSearchResults searchFleetSync(
      FleetSearchRequest request, ImmutableMap<String, DimensionOverlay> overlays) {
    Fleet fleet = normalize(request.getFleet());
    SearchCorpus corpus = corpus(fleet, request.getEntity(), overlays);
    return switch (request.getViewCase()) {
      case FLAT -> {
        FleetFlatView flat = request.getFlat();
        FleetFlatResults results =
            flatSearcher.searchFlat(
                corpus,
                request.getFiltersList(),
                flat.getColumnsList(),
                flat.getSort(),
                flat.getPage());
        yield FleetSearchResults.newBuilder().setFlat(results).build();
      }
      case GROUP_HEADER -> {
        FleetGroupHeaderView header = request.getGroupHeader();
        FleetGroupedResults results =
            groupSearcher.searchGrouped(
                corpus,
                request.getFiltersList(),
                header.getGroupByList(),
                header.getSort(),
                header.getPage());
        yield FleetSearchResults.newBuilder().setGrouped(results).build();
      }
      case GROUP_EXPAND -> {
        FleetGroupExpandView expand = request.getGroupExpand();
        FleetFlatResults results =
            groupSearcher.expandGroup(
                corpus,
                request.getFiltersList(),
                expand.getGroupId(),
                expand.getColumnsList(),
                expand.getPageToken());
        yield FleetSearchResults.newBuilder().setFlat(results).build();
      }
      case VIEW_NOT_SET -> FleetSearchResults.getDefaultInstance();
    };
  }

  @Override
  public ListenableFuture<FleetSuggestionResponse> getFleetSuggestions(
      FleetSuggestionRequest request) {
    return Futures.submit(
        () -> {
          Fleet fleet = normalize(request.getFleet());
          return suggester.suggest(corpus(fleet, request.getEntity()), request);
        },
        executor);
  }

  @Override
  public ListenableFuture<FleetChipResolverResponse> resolveFleetChips(
      FleetChipResolverRequest request) {
    return Futures.submit(() -> chipResolver.resolve(request), executor);
  }

  @Override
  public ListenableFuture<FleetValueListResponse> getFleetValueList(FleetValueListRequest request) {
    Fleet fleet = normalize(request.getFleet());
    Set<String> keys = extractReferencedKeys(request);
    return Futures.transformAsync(
        overlayStore.loadOverlaysAsync(fleet, keys, executor),
        overlays ->
            Futures.submit(
                () ->
                    valueLister.listValues(
                        corpus(fleet, request.getEntity(), overlays),
                        request.getKey(),
                        request.getFiltersList()),
                executor),
        executor);
  }

  @Override
  public ListenableFuture<FleetPromotedKeysResponse> getFleetPromotedKeys(
      FleetPromotedKeysRequest request) {
    return Futures.submit(
        () -> {
          Fleet fleet = normalize(request.getFleet());
          return promotedKeysProvider.getPromotedKeys(corpus(fleet, request.getEntity()), request);
        },
        executor);
  }

  @Override
  public ListenableFuture<FleetColumnCatalogResponse> getFleetColumnCatalog(
      FleetColumnCatalogRequest request) {
    return Futures.submit(
        () -> {
          Fleet fleet = normalize(request.getFleet());
          return columnCataloger.getColumnCatalog(corpus(fleet, request.getEntity()), request);
        },
        executor);
  }

  private SearchCorpus corpus(
      Fleet fleet, SearchEntity entity, ImmutableMap<String, DimensionOverlay> overlays) {
    if (entity == SearchEntity.SEARCH_ENTITY_HOST) {
      return new HostCorpus(store.get(fleet), store.hostPostings(fleet), curations.get(fleet));
    }
    FleetSnapshot snapshot = store.get(fleet);
    OverlayView overlayView = OverlayView.bind(snapshot, overlays);
    return new DeviceCorpus(snapshot, store.postings(fleet), curations.get(fleet), overlayView);
  }

  private SearchCorpus corpus(Fleet fleet, SearchEntity entity) {
    return corpus(fleet, entity, ImmutableMap.of());
  }

  private static Set<String> extractReferencedKeys(FleetSearchRequest request) {
    Set<String> keys = new HashSet<>();
    for (Filter filter : request.getFiltersList()) {
      if (isOverlayKey(filter.getKey())) {
        keys.add(filter.getKey());
      }
    }
    switch (request.getViewCase()) {
      case FLAT -> {
        FleetFlatView flat = request.getFlat();
        for (String col : flat.getColumnsList()) {
          if (isOverlayKey(col)) {
            keys.add(col);
          }
        }
        if (flat.hasSort() && isOverlayKey(flat.getSort().getKey())) {
          keys.add(flat.getSort().getKey());
        }
      }
      case GROUP_HEADER -> {
        FleetGroupHeaderView header = request.getGroupHeader();
        for (String gb : header.getGroupByList()) {
          if (isOverlayKey(gb)) {
            keys.add(gb);
          }
        }
        if (header.hasSort() && isOverlayKey(header.getSort().getField().getGroupKey())) {
          keys.add(header.getSort().getField().getGroupKey());
        }
      }
      case GROUP_EXPAND -> {
        FleetGroupExpandView expand = request.getGroupExpand();
        for (String col : expand.getColumnsList()) {
          if (isOverlayKey(col)) {
            keys.add(col);
          }
        }
      }
      case VIEW_NOT_SET -> {}
    }
    return keys;
  }

  private static Set<String> extractReferencedKeys(FleetValueListRequest request) {
    Set<String> keys = new HashSet<>();
    if (isOverlayKey(request.getKey())) {
      keys.add(request.getKey());
    }
    for (Filter filter : request.getFiltersList()) {
      if (isOverlayKey(filter.getKey())) {
        keys.add(filter.getKey());
      }
    }
    return keys;
  }

  private static boolean isOverlayKey(String keyId) {
    return keyId != null && (keyId.startsWith("dimension::") || keyId.startsWith("dim::"));
  }

  private static Fleet normalize(Fleet fleet) {
    return fleet == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : fleet;
  }
}
