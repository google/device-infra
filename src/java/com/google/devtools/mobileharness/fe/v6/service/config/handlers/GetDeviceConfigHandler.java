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
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigResponse;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetDeviceConfig RPC. */
@Singleton
public final class GetDeviceConfigHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  GetDeviceConfigHandler() {}

  public ListenableFuture<GetDeviceConfigResponse> getDeviceConfig(GetDeviceConfigRequest request) {
    logger.atInfo().log("Getting device config for %s", request.getDeviceId());
    // TODO: Implement real logic.
    return immediateFuture(GetDeviceConfigResponse.getDefaultInstance());
  }
}
