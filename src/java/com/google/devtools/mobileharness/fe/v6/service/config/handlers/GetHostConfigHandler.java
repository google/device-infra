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
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostConfigResponse;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetHostConfig RPC. */
@Singleton
public final class GetHostConfigHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  GetHostConfigHandler() {}

  public ListenableFuture<GetHostConfigResponse> getHostConfig(GetHostConfigRequest request) {
    logger.atInfo().log("Getting host config for %s", request.getHostName());
    // TODO: Implement real logic.
    return immediateFuture(GetHostConfigResponse.getDefaultInstance());
  }
}
