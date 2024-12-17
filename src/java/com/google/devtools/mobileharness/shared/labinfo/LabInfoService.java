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

package com.google.devtools.mobileharness.shared.labinfo;

import static com.google.devtools.mobileharness.shared.labinfo.LabQueryUtils.createNewLabQueryResult;
import static com.google.devtools.mobileharness.shared.labinfo.LabQueryUtils.getPagedResult;
import static com.google.devtools.mobileharness.shared.labinfo.LabQueryUtils.normalizePage;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.Page;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceGrpc;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** An in-memory implementation of {@code LabInfoService}. */
@Singleton
public class LabInfoService extends LabInfoServiceGrpc.LabInfoServiceImplBase {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LabInfoProvider labInfoProvider;

  /**
   * Unpaged query result cache for sessions. Each session is associated with a unique query proto
   * from a specific client. The cache has a long access-based expiration time. The cache is
   * refreshed upon accessing the first page of a query.
   */
  private final Cache<QueryAndClient, LabQueryResult> queryResultCacheBySession =
      CacheBuilder.newBuilder().expireAfterAccess(Duration.ofMinutes(5L)).softValues().build();

  /**
   * Unpaged query result cache for query. The cache has a short write-based expiration time. The
   * cache is shared among clients executing the same query simultaneously.
   */
  private final Cache<LabQuery, LabQueryResult> queryResultCacheByQuery =
      CacheBuilder.newBuilder().expireAfterWrite(Duration.ofSeconds(5L)).softValues().build();

  @Inject
  LabInfoService(LabInfoProvider labInfoProvider) {
    this.labInfoProvider = labInfoProvider;
  }

  @Override
  public void getLabInfo(
      GetLabInfoRequest request, StreamObserver<GetLabInfoResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doGetLabInfo,
        LabInfoServiceGrpc.getServiceDescriptor(),
        LabInfoServiceGrpc.getGetLabInfoMethod());
  }

  @VisibleForTesting
  GetLabInfoResponse doGetLabInfo(GetLabInfoRequest request) {
    String clientId = getClientId();
    LabQuery query = request.getLabQuery();
    Page page = normalizePage(request.getPage());
    QueryAndClient session = QueryAndClient.of(query, clientId);
    logger.atInfo().log(
        "Get lab info, req=[%s], client_id=[%s]", shortDebugString(request), clientId);

    LabQueryResult result;

    // Gets cached result if any.
    Optional<LabQueryResult> cachedResult =
        request.getUseRealtimeData() ? Optional.empty() : getCachedResult(session, page);

    if (cachedResult.isPresent()) {
      result = cachedResult.get();
    } else {
      // Creates new result.
      result = createNewLabQueryResult(query, labInfoProvider);

      // Updates cache.
      queryResultCacheByQuery.put(query, result);
      queryResultCacheBySession.put(session, result);
    }

    // Returns the specified page of the query result.
    LabQueryResult pagedResult = getPagedResult(result, page);

    return GetLabInfoResponse.newBuilder().setLabQueryResult(pagedResult).build();
  }

  private Optional<LabQueryResult> getCachedResult(QueryAndClient session, Page page) {
    LabQueryResult cachedResult;

    // Only uses session cache for non-first page.
    if (page.getOffset() != 0) {
      cachedResult = queryResultCacheBySession.getIfPresent(session);
      if (cachedResult != null) {
        return Optional.of(cachedResult);
      }
    }

    // Checks if there is the same query recently.
    cachedResult = queryResultCacheByQuery.getIfPresent(session.labQuery());
    if (cachedResult != null) {
      // Also adds the result to session cache.
      queryResultCacheBySession.put(session, cachedResult);
      return Optional.of(cachedResult);
    } else {
      return Optional.empty();
    }
  }

  /**
   * Gets an ID for the current client (e.g., a browser), which will be used for caching query
   * results.
   */
  private String getClientId() {
    return ""; // TODO: Gets client ID. Now all clients uses the same ID.
  }

  @AutoValue
  abstract static class QueryAndClient {

    abstract LabQuery labQuery();

    @SuppressWarnings("unused")
    abstract String clientId();

    private static QueryAndClient of(LabQuery labQuery, String clientId) {
      return new AutoValue_LabInfoService_QueryAndClient(labQuery, clientId);
    }
  }
}
