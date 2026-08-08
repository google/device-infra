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

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.grpc.FeGrpcInvoker;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchServiceGrpc;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsResolveChipsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsResolveChipsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSuggestionResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

/**
 * gRPC binding for {@code SearchService}.
 *
 * <p>The seven Fleet RPCs delegate to {@link SearchServiceLogic} through {@link FeGrpcInvoker},
 * mirroring the DeviceService gRPC binding. The four TJS RPCs have no backend and fail with {@code
 * UNIMPLEMENTED}.
 */
public final class SearchServiceGrpcImpl extends SearchServiceGrpc.SearchServiceImplBase {

  private static final String TJS_UNIMPLEMENTED_MESSAGE = "TJS search is not implemented";

  private final SearchServiceLogic logic;
  private final ListeningExecutorService executor;

  @Inject
  SearchServiceGrpcImpl(SearchServiceLogic logic, ListeningExecutorService executor) {
    this.logic = logic;
    this.executor = executor;
  }

  // ===========================================================================
  // Fleet (device/host) search
  // ===========================================================================

  @Override
  public void getFleetSearchConfig(
      FleetSearchConfigRequest request, StreamObserver<FleetSearchConfig> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::getFleetSearchConfig,
        executor,
        SearchServiceGrpc.getServiceDescriptor(),
        SearchServiceGrpc.getGetFleetSearchConfigMethod());
  }

  @Override
  public void searchFleet(
      FleetSearchRequest request, StreamObserver<FleetSearchResults> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::searchFleet,
        executor,
        SearchServiceGrpc.getServiceDescriptor(),
        SearchServiceGrpc.getSearchFleetMethod());
  }

  @Override
  public void getFleetSuggestions(
      FleetSuggestionRequest request, StreamObserver<FleetSuggestionResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::getFleetSuggestions,
        executor,
        SearchServiceGrpc.getServiceDescriptor(),
        SearchServiceGrpc.getGetFleetSuggestionsMethod());
  }

  @Override
  public void resolveFleetChips(
      FleetChipResolverRequest request,
      StreamObserver<FleetChipResolverResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::resolveFleetChips,
        executor,
        SearchServiceGrpc.getServiceDescriptor(),
        SearchServiceGrpc.getResolveFleetChipsMethod());
  }

  @Override
  public void getFleetValueList(
      FleetValueListRequest request, StreamObserver<FleetValueListResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::getFleetValueList,
        executor,
        SearchServiceGrpc.getServiceDescriptor(),
        SearchServiceGrpc.getGetFleetValueListMethod());
  }

  @Override
  public void getFleetPromotedKeys(
      FleetPromotedKeysRequest request,
      StreamObserver<FleetPromotedKeysResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::getFleetPromotedKeys,
        executor,
        SearchServiceGrpc.getServiceDescriptor(),
        SearchServiceGrpc.getGetFleetPromotedKeysMethod());
  }

  @Override
  public void getFleetColumnCatalog(
      FleetColumnCatalogRequest request,
      StreamObserver<FleetColumnCatalogResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::getFleetColumnCatalog,
        executor,
        SearchServiceGrpc.getServiceDescriptor(),
        SearchServiceGrpc.getGetFleetColumnCatalogMethod());
  }

  // ===========================================================================
  // TJS (test/job/session) search: no backend
  // ===========================================================================

  @Override
  public void getTjsSearchConfig(
      TjsSearchConfigRequest request, StreamObserver<TjsSearchConfig> responseObserver) {
    responseObserver.onError(tjsUnimplemented());
  }

  @Override
  public void searchTjs(
      TjsSearchRequest request, StreamObserver<TjsSearchResponse> responseObserver) {
    responseObserver.onError(tjsUnimplemented());
  }

  @Override
  public void getTjsSuggestions(
      TjsSuggestionRequest request, StreamObserver<TjsSuggestionResponse> responseObserver) {
    responseObserver.onError(tjsUnimplemented());
  }

  @Override
  public void resolveTjsChips(
      TjsResolveChipsRequest request, StreamObserver<TjsResolveChipsResponse> responseObserver) {
    responseObserver.onError(tjsUnimplemented());
  }

  private static StatusRuntimeException tjsUnimplemented() {
    return Status.UNIMPLEMENTED.withDescription(TJS_UNIMPLEMENTED_MESSAGE).asRuntimeException();
  }
}
