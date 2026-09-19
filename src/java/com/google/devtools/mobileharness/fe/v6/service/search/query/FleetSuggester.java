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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionCatalogStore;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Facade dispatcher for fleet search-bar suggestions across search entities (devices versus hosts).
 *
 * <h2>Why this exists</h2>
 *
 * <p>Device search and host search operate on fundamentally disjoint key spaces with distinct
 * grammar rules and catalog sources:
 *
 * <ul>
 *   <li>{@link DeviceSuggester} handles device search queries over {@link DeviceCorpus},
 *       integrating {@code dimension:<name>} syntax, device aliases, and dynamic {@link
 *       DimensionCatalogStore} dimensions.
 *   <li>{@link HostSuggester} handles host search queries over {@link HostCorpus}, integrating
 *       {@code host property:<name>} syntax, host aliases, and host properties, while strictly
 *       excluding device dimensions.
 * </ul>
 *
 * <p>This facade provides a unified, backwards-compatible entry point for {@link
 * com.google.devtools.mobileharness.fe.v6.service.search.SearchServiceLogic} while enforcing
 * compile-time entity isolation by delegating to dedicated, strongly-typed entity suggesters.
 *
 * <h2>How to use it</h2>
 *
 * <p>Injected as a singleton and invoked by the search service logic layer:
 *
 * <pre>{@code
 * FleetSuggestionResponse response = fleetSuggester.suggest(corpus, request);
 * }</pre>
 *
 * <p>The dispatcher inspects the runtime type of {@link SearchCorpus} and routes {@link HostCorpus}
 * directly to {@link HostSuggester#suggest} and {@link DeviceCorpus} to {@link
 * DeviceSuggester#suggest}.
 */
@Singleton
public final class FleetSuggester {

  private final DeviceSuggester deviceSuggester;
  private final HostSuggester hostSuggester;

  @Inject
  FleetSuggester(DeviceSuggester deviceSuggester, HostSuggester hostSuggester) {
    this.deviceSuggester = checkNotNull(deviceSuggester);
    this.hostSuggester = checkNotNull(hostSuggester);
  }

  /** Testing and wiring constructor with custom filter engine and curation map. */
  public FleetSuggester(
      FleetFilterEngine filterEngine,
      Map<Fleet, ScenarioCuration> curations,
      DimensionCatalogStore dimensionCatalogStore) {
    SuggesterEngine engine = new SuggesterEngine(filterEngine);
    this.deviceSuggester = new DeviceSuggester(engine, curations, dimensionCatalogStore);
    this.hostSuggester = new HostSuggester(engine, curations);
  }

  public FleetSuggester(FleetFilterEngine filterEngine, Map<Fleet, ScenarioCuration> curations) {
    this(filterEngine, curations, new DimensionCatalogStore());
  }

  public FleetSuggester(DimensionCatalogStore dimensionCatalogStore) {
    this(new FleetFilterEngine(), ImmutableMap.of(), dimensionCatalogStore);
  }

  public FleetSuggester() {
    this(new FleetFilterEngine(), ImmutableMap.of(), new DimensionCatalogStore());
  }

  /** Returns ranked suggestions for the request against the given search corpus. */
  public FleetSuggestionResponse suggest(SearchCorpus corpus, FleetSuggestionRequest request) {
    if (corpus instanceof HostCorpus hostCorpus) {
      return hostSuggester.suggest(hostCorpus, request);
    }
    return deviceSuggester.suggest((DeviceCorpus) corpus, request);
  }
}
