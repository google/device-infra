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

package com.google.devtools.mobileharness.shared.util.comm.relay.service;

import static java.util.function.Function.identity;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.comm.relay.DestinationUtils;
import com.google.devtools.mobileharness.shared.util.comm.relay.proto.DestinationProto.Destination;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * Controls if the call should be relayed or not.
 *
 * <p>If the metadata contains the destination header, the call will be relayed to the destination
 * channel. Otherwise, the call will be handled by the original registered service.
 */
public final class RelayInterceptor implements ServerInterceptor {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final ConnectionManager connectionManager;

  public RelayInterceptor(ConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    Destination destination = DestinationUtils.getDestination(headers);
    if (destination == null) {
      logger.atInfo().log("No relay. Call the registered service");
      return next.startCall(call, headers);
    } else {
      logger.atInfo().log("Relay to destination %s", destination);
      return new RelayHandler<ReqT, RespT, ReqT, RespT>(
              connectionManager, identity(), identity(), identity())
          .startCall(call, headers);
    }
  }
}
