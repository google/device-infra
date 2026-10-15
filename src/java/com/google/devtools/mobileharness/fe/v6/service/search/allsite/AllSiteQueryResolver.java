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

package com.google.devtools.mobileharness.fe.v6.service.search.allsite;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ResolveAllSiteQueryRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ResolveAllSiteQueryResponse;

/**
 * Resolver for {@code SearchService.ResolveAllSiteQuery}.
 *
 * <p>Resolves an identifier pasted into the jump bar by exact match across OmniLab entities (device
 * ID, host name, test ID, job ID, session ID). The response carries zero matches (not found), one
 * match (navigate to its detail page), or several matches (disambiguate).
 *
 * <p>A request whose {@code scope} is unset, or whose {@code id_type} is {@code
 * ID_TYPE_UNSPECIFIED}, is resolved as {@code any_id}.
 */
public interface AllSiteQueryResolver {

  /** Resolves {@code request} into zero, one, or multiple {@code AllSiteMatch} entries. */
  ListenableFuture<ResolveAllSiteQueryResponse> resolveAllSiteQuery(
      ResolveAllSiteQueryRequest request);
}
