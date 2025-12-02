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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostOverview;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetHostOverview RPC. */
@Singleton
public final class GetHostOverviewHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  GetHostOverviewHandler() {}

  public ListenableFuture<HostOverview> getHostOverview(GetHostOverviewRequest request) {
    logger.atInfo().log("Getting host overview for %s", request.getHostName());

    // TODO: Implement real logic to fetch and build HostOverview.
    // 1. Fetch LabInfo from LabInfoProvider.
    // 2. Fetch additional host details.
    // 3. Fetch summaries of devices on the host.
    // 4. Combine all information into HostOverview proto.

    return immediateFuture(HostOverview.newBuilder().setHostName(request.getHostName()).build());
  }
}
