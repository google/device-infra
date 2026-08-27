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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsResolveChipsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsResolveChipsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSearchResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TjsSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.search.tjs.TjsSearchLogic;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SearchServiceGrpcImplTest {

  private SearchServiceLogic fleetLogic;
  private TjsSearchLogic tjsLogic;
  private ListeningExecutorService executor;
  private SearchServiceGrpcImpl service;

  @Before
  public void setUp() {
    fleetLogic = mock(SearchServiceLogic.class);
    tjsLogic = mock(TjsSearchLogic.class);
    executor = newDirectExecutorService();
    service = new SearchServiceGrpcImpl(fleetLogic, tjsLogic, executor);
  }

  // ===========================================================================
  // Fleet search delegation
  // ===========================================================================

  @Test
  public void getFleetSearchConfig_callsFleetLogic() {
    FleetSearchConfigRequest request = FleetSearchConfigRequest.getDefaultInstance();
    when(fleetLogic.getFleetSearchConfig(any()))
        .thenReturn(immediateFuture(FleetSearchConfig.getDefaultInstance()));

    service.getFleetSearchConfig(request, noOpObserver());

    verify(fleetLogic).getFleetSearchConfig(eq(request));
  }

  @Test
  public void searchFleet_callsFleetLogic() {
    FleetSearchRequest request = FleetSearchRequest.getDefaultInstance();
    when(fleetLogic.searchFleet(any()))
        .thenReturn(immediateFuture(FleetSearchResults.getDefaultInstance()));

    service.searchFleet(request, noOpObserver());

    verify(fleetLogic).searchFleet(eq(request));
  }

  @Test
  public void getFleetSuggestions_callsFleetLogic() {
    FleetSuggestionRequest request = FleetSuggestionRequest.getDefaultInstance();
    when(fleetLogic.getFleetSuggestions(any()))
        .thenReturn(immediateFuture(FleetSuggestionResponse.getDefaultInstance()));

    service.getFleetSuggestions(request, noOpObserver());

    verify(fleetLogic).getFleetSuggestions(eq(request));
  }

  @Test
  public void resolveFleetChips_callsFleetLogic() {
    FleetChipResolverRequest request = FleetChipResolverRequest.getDefaultInstance();
    when(fleetLogic.resolveFleetChips(any()))
        .thenReturn(immediateFuture(FleetChipResolverResponse.getDefaultInstance()));

    service.resolveFleetChips(request, noOpObserver());

    verify(fleetLogic).resolveFleetChips(eq(request));
  }

  @Test
  public void getFleetValueList_callsFleetLogic() {
    FleetValueListRequest request = FleetValueListRequest.getDefaultInstance();
    when(fleetLogic.getFleetValueList(any()))
        .thenReturn(immediateFuture(FleetValueListResponse.getDefaultInstance()));

    service.getFleetValueList(request, noOpObserver());

    verify(fleetLogic).getFleetValueList(eq(request));
  }

  @Test
  public void getFleetPromotedKeys_callsFleetLogic() {
    FleetPromotedKeysRequest request = FleetPromotedKeysRequest.getDefaultInstance();
    when(fleetLogic.getFleetPromotedKeys(any()))
        .thenReturn(immediateFuture(FleetPromotedKeysResponse.getDefaultInstance()));

    service.getFleetPromotedKeys(request, noOpObserver());

    verify(fleetLogic).getFleetPromotedKeys(eq(request));
  }

  @Test
  public void getFleetColumnCatalog_callsFleetLogic() {
    FleetColumnCatalogRequest request = FleetColumnCatalogRequest.getDefaultInstance();
    when(fleetLogic.getFleetColumnCatalog(any()))
        .thenReturn(immediateFuture(FleetColumnCatalogResponse.getDefaultInstance()));

    service.getFleetColumnCatalog(request, noOpObserver());

    verify(fleetLogic).getFleetColumnCatalog(eq(request));
  }

  // ===========================================================================
  // TJS search delegation
  // ===========================================================================

  @Test
  public void getTjsSearchConfig_callsTjsLogic() {
    TjsSearchConfigRequest request = TjsSearchConfigRequest.getDefaultInstance();
    when(tjsLogic.getTjsSearchConfig(any()))
        .thenReturn(immediateFuture(TjsSearchConfig.getDefaultInstance()));

    service.getTjsSearchConfig(request, noOpObserver());

    verify(tjsLogic).getTjsSearchConfig(eq(request));
  }

  @Test
  public void searchTjs_callsTjsLogic() {
    TjsSearchRequest request = TjsSearchRequest.getDefaultInstance();
    when(tjsLogic.searchTjs(any()))
        .thenReturn(immediateFuture(TjsSearchResponse.getDefaultInstance()));

    service.searchTjs(request, noOpObserver());

    verify(tjsLogic).searchTjs(eq(request));
  }

  @Test
  public void getTjsSuggestions_callsTjsLogic() {
    TjsSuggestionRequest request = TjsSuggestionRequest.getDefaultInstance();
    when(tjsLogic.getTjsSuggestions(any()))
        .thenReturn(immediateFuture(TjsSuggestionResponse.getDefaultInstance()));

    service.getTjsSuggestions(request, noOpObserver());

    verify(tjsLogic).getTjsSuggestions(eq(request));
  }

  @Test
  public void resolveTjsChips_callsTjsLogic() {
    TjsResolveChipsRequest request = TjsResolveChipsRequest.getDefaultInstance();
    when(tjsLogic.resolveTjsChips(any()))
        .thenReturn(immediateFuture(TjsResolveChipsResponse.getDefaultInstance()));

    service.resolveTjsChips(request, noOpObserver());

    verify(tjsLogic).resolveTjsChips(eq(request));
  }

  @Test
  public void searchTjs_tjsLogicFailsWithUnimplemented_mapsToGrpcUnimplemented() {
    TjsSearchRequest request = TjsSearchRequest.getDefaultInstance();
    when(tjsLogic.searchTjs(any()))
        .thenReturn(
            immediateFailedFuture(
                FeServiceException.unimplemented("TJS search is not implemented")));

    AtomicReference<Throwable> errorRef = new AtomicReference<>();
    StreamObserver<TjsSearchResponse> observer =
        new StreamObserver<TjsSearchResponse>() {
          @Override
          public void onNext(TjsSearchResponse response) {}

          @Override
          public void onError(Throwable t) {
            errorRef.set(t);
          }

          @Override
          public void onCompleted() {}
        };

    service.searchTjs(request, observer);

    assertThat(errorRef.get()).isInstanceOf(StatusRuntimeException.class);
    StatusRuntimeException sre = (StatusRuntimeException) errorRef.get();
    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
    assertThat(sre).hasMessageThat().contains("TJS search is not implemented");
  }

  private static <T> StreamObserver<T> noOpObserver() {
    return new StreamObserver<T>() {
      @Override
      public void onNext(T response) {}

      @Override
      public void onError(Throwable t) {}

      @Override
      public void onCompleted() {}
    };
  }
}
