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

/**
 * Query interface for the fleet search index.
 *
 * <p>All deployment cases (single-controller and multi-controller) share this interface. The
 * implementation builds an in-memory inverted index from periodically pulled data sources and
 * serves all queries from memory.
 */
public interface FleetSearchIndex {

  /** Searches devices or hosts with filters, returning paginated results. */
  ListenableFuture<FleetSearchResults> search(FleetSearchRequest request);

  /** Returns ranked search-bar suggestions for the given input. */
  ListenableFuture<FleetSuggestionResponse> suggest(FleetSuggestionRequest request);

  /** Returns enumerated values for a key with filtered/total facet counts. */
  ListenableFuture<FleetValueListResponse> getValueList(FleetValueListRequest request);

  /** Resolves filter and group-by chips to their display text. */
  ListenableFuture<FleetChipResolverResponse> resolveChips(FleetChipResolverRequest request);

  /** Returns dynamically promoted filter-by and group-by key rows. */
  ListenableFuture<FleetPromotedKeysResponse> getPromotedKeys(FleetPromotedKeysRequest request);

  /** Returns the browsable column catalog for the column selector dialog. */
  ListenableFuture<FleetColumnCatalogResponse> getColumnCatalog(FleetColumnCatalogRequest request);

  /** Returns page-load configuration (columns, landing page). */
  FleetSearchConfig getSearchConfig(FleetSearchConfigRequest request);
}
