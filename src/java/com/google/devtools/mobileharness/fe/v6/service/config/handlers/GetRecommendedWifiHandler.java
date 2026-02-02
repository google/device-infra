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

package com.google.devtools.mobileharness.fe.v6.service.config.handlers;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetRecommendedWifiRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetRecommendedWifiResponse;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetRecommendedWifi RPC. */
@Singleton
public final class GetRecommendedWifiHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  GetRecommendedWifiHandler() {}

  public ListenableFuture<GetRecommendedWifiResponse> getRecommendedWifi(
      GetRecommendedWifiRequest request) {
    logger.atInfo().log("GetRecommendedWifiRequest: %s", request);
    // TODO: Implement real logic.
    return immediateFuture(GetRecommendedWifiResponse.getDefaultInstance());
  }
}
