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

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsResolveChipsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsResolveChipsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSuggestionResponse;

/**
 * No-op implementation of {@link TjsSearchLogic} used in OSS / ATS environments where no MOSS
 * backend is reachable.
 *
 * <p>Returns {@link FeServiceException#unimplemented} so that the gRPC and Envoy REST layers map
 * the failure cleanly to {@code UNIMPLEMENTED} (HTTP 501).
 */
public final class NoOpTjsSearchLogic implements TjsSearchLogic {

  private static final String TJS_UNIMPLEMENTED_MESSAGE = "TJS search is not implemented";

  @Override
  public ListenableFuture<TjsSearchConfig> getTjsSearchConfig(TjsSearchConfigRequest request) {
    return immediateFailedFuture(FeServiceException.unimplemented(TJS_UNIMPLEMENTED_MESSAGE));
  }

  @Override
  public ListenableFuture<TjsSearchResponse> searchTjs(TjsSearchRequest request) {
    return immediateFailedFuture(FeServiceException.unimplemented(TJS_UNIMPLEMENTED_MESSAGE));
  }

  @Override
  public ListenableFuture<TjsSuggestionResponse> getTjsSuggestions(TjsSuggestionRequest request) {
    return immediateFailedFuture(FeServiceException.unimplemented(TJS_UNIMPLEMENTED_MESSAGE));
  }

  @Override
  public ListenableFuture<TjsResolveChipsResponse> resolveTjsChips(TjsResolveChipsRequest request) {
    return immediateFailedFuture(FeServiceException.unimplemented(TJS_UNIMPLEMENTED_MESSAGE));
  }
}
