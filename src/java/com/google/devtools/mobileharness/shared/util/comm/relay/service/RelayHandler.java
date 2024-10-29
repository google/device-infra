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

import com.google.devtools.mobileharness.shared.util.comm.relay.DestinationUtils;
import com.google.devtools.mobileharness.shared.util.comm.relay.proto.DestinationProto.Destination;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import java.util.function.Function;

/** Relays the server call to a client call. */
public final class RelayHandler<ReqT, RespT, RelayReqT, RelayRespT>
    implements ServerCallHandler<ReqT, RespT> {
  private final ConnectionManager connectionManager;
  private final Function<MethodDescriptor<ReqT, RespT>, MethodDescriptor<RelayReqT, RelayRespT>>
      methodDescriptorTransform;
  private final Function<ReqT, RelayReqT> requestTransform;
  private final Function<RelayRespT, RespT> responseTransform;

  /**
   * Creates a new {@link RelayHandler}.
   *
   * @param connectionManager manages the background connections
   * @param methodDescriptorTransform transforms the server call method descriptor to the client
   *     call method descriptor
   * @param requestTransform transforms the server call request to the client call request
   * @param responseTransform transforms the client call response to the server call response
   */
  public RelayHandler(
      ConnectionManager connectionManager,
      Function<MethodDescriptor<ReqT, RespT>, MethodDescriptor<RelayReqT, RelayRespT>>
          methodDescriptorTransform,
      Function<ReqT, RelayReqT> requestTransform,
      Function<RelayRespT, RespT> responseTransform) {
    this.connectionManager = connectionManager;
    this.methodDescriptorTransform = methodDescriptorTransform;
    this.requestTransform = requestTransform;
    this.responseTransform = responseTransform;
  }

  @Override
  public ServerCall.Listener<ReqT> startCall(ServerCall<ReqT, RespT> serverCall, Metadata headers) {
    Destination destination = DestinationUtils.getDestination(headers);
    if (destination == null) {
      return failRelay(
          serverCall,
          String.format(
              "Method not found: %s in the registered service. No relay destination found in"
                  + " the headers: %s",
              serverCall.getMethodDescriptor().getFullMethodName(), headers),
          headers);
    }
    try {
      ClientCall<RelayReqT, RelayRespT> clientCall =
          connectionManager.connectToTarget(
              destination,
              c ->
                  c.newCall(
                      methodDescriptorTransform.apply(serverCall.getMethodDescriptor()),
                      CallOptions.DEFAULT));
      return startRelay(clientCall, serverCall, headers);
    } catch (RuntimeException e) {
      return failRelay(
          serverCall,
          String.format(
              "Failed to look up the destination %s in the channel locator: %s", destination, e),
          headers);
    }
  }

  private ServerCall.Listener<ReqT> startRelay(
      ClientCall<RelayReqT, RelayRespT> clientCall,
      ServerCall<ReqT, RespT> serverCall,
      Metadata headers) {
    CallSwitcher<ReqT, RespT, RelayReqT, RelayRespT> callSwitcher =
        new CallSwitcher<>(serverCall, clientCall, requestTransform, responseTransform);
    clientCall.start(callSwitcher.clientCallListener, headers);
    serverCall.request(1);
    clientCall.request(1);
    return callSwitcher.serverCallListener;
  }

  private ServerCall.Listener<ReqT> failRelay(
      ServerCall<ReqT, RespT> serverCall, String errorMessage, Metadata headers) {
    serverCall.close(Status.INVALID_ARGUMENT.withDescription(errorMessage), headers);
    return new ServerCall.Listener<ReqT>() {};
  }
}
