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

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetChipResolverRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetChipResolverResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogResponse;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.GetGlobalSummaryRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.GlobalSummary;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetChipResolver;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetColumnCataloger;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetPromotedKeysProvider;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetSearchConfigProvider;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetSearcher;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetSuggester;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetValueLister;
import com.google.devtools.mobileharness.fe.v6.service.search.query.ScenarioCuration;
import com.google.devtools.mobileharness.fe.v6.service.search.query.SearchCorpusFactory;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionOverlayStore;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.summary.GlobalSummaryProvider;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * In-memory implementation of {@link SearchServiceLogic}.
 *
 * <p>Every Fleet RPC coordinates snapshot acquisition, on-demand overlay fetching, and query
 * execution across the thread pool via an injected {@link ListeningExecutorService}.
 */
@Singleton
public final class SearchServiceLogicImpl implements SearchServiceLogic {

  private final ListeningExecutorService executor;
  private final DimensionOverlayStore overlayStore;
  private final SearchCorpusFactory corpusFactory;
  private final FleetSearcher fleetSearcher;
  private final FleetSearchConfigProvider searchConfigProvider;
  private final FleetSuggester suggester;
  private final FleetChipResolver chipResolver;
  private final FleetValueLister valueLister;
  private final FleetPromotedKeysProvider promotedKeysProvider;
  private final FleetColumnCataloger columnCataloger;
  private final GlobalSummaryProvider globalSummaryProvider;

  @Inject
  SearchServiceLogicImpl(
      ListeningExecutorService executor,
      DimensionOverlayStore overlayStore,
      SearchCorpusFactory corpusFactory,
      FleetSearcher fleetSearcher,
      FleetSearchConfigProvider searchConfigProvider,
      FleetSuggester suggester,
      FleetChipResolver chipResolver,
      FleetValueLister valueLister,
      FleetPromotedKeysProvider promotedKeysProvider,
      FleetColumnCataloger columnCataloger,
      GlobalSummaryProvider globalSummaryProvider) {
    this.executor = executor;
    this.overlayStore = overlayStore;
    this.corpusFactory = corpusFactory;
    this.fleetSearcher = fleetSearcher;
    this.searchConfigProvider = searchConfigProvider;
    this.suggester = suggester;
    this.chipResolver = chipResolver;
    this.valueLister = valueLister;
    this.promotedKeysProvider = promotedKeysProvider;
    this.columnCataloger = columnCataloger;
    this.globalSummaryProvider = globalSummaryProvider;
  }

  @Override
  public ListenableFuture<FleetSearchConfig> getFleetSearchConfig(
      FleetSearchConfigRequest request) {
    Fleet fleet = normalize(request.getFleet());
    FleetSnapshot snapshot = corpusFactory.getSnapshot(fleet);
    ScenarioCuration curation = corpusFactory.getCuration(fleet);
    if (curation == null) {
      return immediateFuture(FleetSearchConfig.getDefaultInstance());
    }
    return immediateFuture(searchConfigProvider.getConfig(snapshot, request, curation));
  }

  @Override
  public ListenableFuture<FleetSearchResults> searchFleet(FleetSearchRequest request) {
    Fleet fleet = normalize(request.getFleet());
    DeviceKeyRegistry registry = corpusFactory.getDeviceKeyRegistry(fleet);
    ImmutableSet<DeviceKeyDescriptor> overlayKeys =
        FleetSearcher.extractOverlayKeys(registry, request);
    return Futures.transformAsync(
        overlayStore.loadOverlaysAsync(fleet, overlayKeys, executor),
        overlays ->
            Futures.submit(
                () ->
                    fleetSearcher.search(
                        corpusFactory.getCorpus(fleet, request.getEntity(), overlays), request),
                executor),
        executor);
  }

  @Override
  public ListenableFuture<FleetSuggestionResponse> getFleetSuggestions(
      FleetSuggestionRequest request) {
    return Futures.submit(
        () -> {
          Fleet fleet = normalize(request.getFleet());
          return suggester.suggest(corpusFactory.getCorpus(fleet, request.getEntity()), request);
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
    DeviceKeyRegistry registry = corpusFactory.getDeviceKeyRegistry(fleet);
    ImmutableSet<DeviceKeyDescriptor> overlayKeys =
        FleetValueLister.extractOverlayKeys(registry, request);
    return Futures.transformAsync(
        overlayStore.loadOverlaysAsync(fleet, overlayKeys, executor),
        overlays ->
            Futures.submit(
                () ->
                    valueLister.listValues(
                        corpusFactory.getCorpus(fleet, request.getEntity(), overlays),
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
          return promotedKeysProvider.getPromotedKeys(
              corpusFactory.getCorpus(fleet, request.getEntity()), request);
        },
        executor);
  }

  @Override
  public ListenableFuture<FleetColumnCatalogResponse> getFleetColumnCatalog(
      FleetColumnCatalogRequest request) {
    return Futures.submit(
        () -> {
          Fleet fleet = normalize(request.getFleet());
          return columnCataloger.getColumnCatalog(
              corpusFactory.getCorpus(fleet, request.getEntity()), request);
        },
        executor);
  }

  @Override
  public ListenableFuture<GlobalSummary> getGlobalSummary(GetGlobalSummaryRequest request) {
    return globalSummaryProvider.getGlobalSummary(request);
  }

  private static Fleet normalize(Fleet fleet) {
    return fleet == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : fleet;
  }
}
