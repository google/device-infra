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

import com.google.common.util.concurrent.ListenableFuture;
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

/**
 * Core logic of the fleet (device and host) search service.
 *
 * <p>Covers the seven Fleet RPCs of {@code SearchService} plus the Home page global summary RPC.
 * The four TJS (test, job, session) RPCs are served by {@link
 * com.google.devtools.mobileharness.fe.v6.service.search.tjs.TjsSearchLogic}.
 *
 * <p>// TODO: rename to FleetSearchLogic once the TJS backend lands. This interface covers only the
 * fleet (device/host) RPCs; TJS RPCs are served by TjsSearchLogic.
 *
 * <p>Every method returns a {@link ListenableFuture}. The current implementation searches an
 * in-memory snapshot and completes immediately, but the async contract keeps the interface stable
 * if a fleet ever needs a remote read on the serving path.
 */
public interface SearchServiceLogic {

  /** Returns the page-load configuration: column setup and landing page content. */
  ListenableFuture<FleetSearchConfig> getFleetSearchConfig(FleetSearchConfigRequest request);

  /** Executes a fleet search query in the flat, grouped-header, or grouped-expand view. */
  ListenableFuture<FleetSearchResults> searchFleet(FleetSearchRequest request);

  /** Returns ranked search-bar suggestions for the current query. */
  ListenableFuture<FleetSuggestionResponse> getFleetSuggestions(FleetSuggestionRequest request);

  /** Resolves filter and group-by chips to their display text and metadata. */
  ListenableFuture<FleetChipResolverResponse> resolveFleetChips(FleetChipResolverRequest request);

  /** Returns the enumerated values for a key's value picker, with filtered and total counts. */
  ListenableFuture<FleetValueListResponse> getFleetValueList(FleetValueListRequest request);

  /** Returns the dynamically promoted "Filter by:" and "Group by:" key rows. */
  ListenableFuture<FleetPromotedKeysResponse> getFleetPromotedKeys(
      FleetPromotedKeysRequest request);

  /** Returns the browsable column catalog for the column selector dialog. */
  ListenableFuture<FleetColumnCatalogResponse> getFleetColumnCatalog(
      FleetColumnCatalogRequest request);

  /** Returns the Home page global summary ("OmniLab Summary" card). */
  ListenableFuture<GlobalSummary> getGlobalSummary(GetGlobalSummaryRequest request);
}
