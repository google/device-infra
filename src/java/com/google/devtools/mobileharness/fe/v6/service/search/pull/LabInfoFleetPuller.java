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

package com.google.devtools.mobileharness.fe.v6.service.search.pull;

import com.google.common.util.concurrent.Uninterruptibles;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.Page;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;

/**
 * Pulls the full fleet from {@code LabInfoService} for the search index refresh cycle.
 *
 * <p>Unlike the per-entity detail-page reads, this asks for the whole fleet in one call: no filter
 * (every lab and device) and no page limit. It accepts the master's cached data, which is enough
 * for a periodically refreshed index that does not need the per-query realtime path (the {@code
 * use_realtime_data} opt-in stays at its default). The result comes back as a lab to device tree
 * ({@code lab_view_request}), which is the shape {@code FleetIndexBuilder} consumes.
 *
 * <p>{@link #pull()} blocks until the master responds. The refresh scheduler runs it off the
 * serving path, so blocking here is intentional and keeps the caller simple.
 */
public final class LabInfoFleetPuller {

  /**
   * The full fleet request: lab view, no filter, no page limit. Reused across pulls since it never
   * varies.
   */
  private static final GetLabInfoRequest FULL_FLEET_REQUEST =
      GetLabInfoRequest.newBuilder()
          .setLabQuery(
              LabQuery.newBuilder().setLabViewRequest(LabQuery.LabViewRequest.getDefaultInstance()))
          .setPage(Page.newBuilder().setLimit(0))
          .build();

  private final LabInfoProvider labInfoProvider;

  @Inject
  LabInfoFleetPuller(LabInfoProvider labInfoProvider) {
    this.labInfoProvider = labInfoProvider;
  }

  /**
   * Fetches the full fleet from the local (self) universe and returns its {@link LabQueryResult}.
   *
   * @throws ExecutionException if the underlying RPC fails
   */
  public LabQueryResult pull() throws ExecutionException {
    GetLabInfoResponse response =
        Uninterruptibles.getUninterruptibly(
            labInfoProvider.getLabInfoAsync(FULL_FLEET_REQUEST, UniverseScope.SELF));
    return response.getLabQueryResult();
  }
}
