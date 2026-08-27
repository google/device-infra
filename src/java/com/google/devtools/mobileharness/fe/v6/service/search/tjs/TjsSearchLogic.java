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

package com.google.devtools.mobileharness.fe.v6.service.search.tjs;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsResolveChipsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsResolveChipsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSuggestionResponse;

/**
 * Core logic for Test / Job / Session (TJS) search.
 *
 * <p>Covers the four TJS RPCs of {@code SearchService}. The internal (1P) implementation queries
 * MOSS (Mobile Harness Object Storage Service); the OSS implementation is a no-op that returns
 * {@code UNIMPLEMENTED} (since MOSS is not reachable from OSS).
 */
public interface TjsSearchLogic {

  /** Returns page-load configuration (entity label, default chips, promoted filter keys). */
  ListenableFuture<TjsSearchConfig> getTjsSearchConfig(TjsSearchConfigRequest request);

  /** Executes a TJS search query (filters + page_token); returns columns, rows, and next token. */
  ListenableFuture<TjsSearchResponse> searchTjs(TjsSearchRequest request);

  /** Returns rule-based typed search-bar suggestions. */
  ListenableFuture<TjsSuggestionResponse> getTjsSuggestions(TjsSuggestionRequest request);

  /** Resolves TjsFilter structures into display pill text. */
  ListenableFuture<TjsResolveChipsResponse> resolveTjsChips(TjsResolveChipsRequest request);
}
