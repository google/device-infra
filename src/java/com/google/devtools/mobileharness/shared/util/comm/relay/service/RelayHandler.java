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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.comm.relay.DestinationUtils;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import java.util.Optional;
import java.util.function.Function;

/** Relays the server call to a client call. */
public class RelayHandler<ReqT, RespT, RelayReqT, RelayRespT>
    implements ServerCallHandler<ReqT, RespT> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ChannelManager channelManager;
  private final Function<MethodDescriptor<ReqT, RespT>, MethodDescriptor<RelayReqT, RelayRespT>>
      methodDescriptorTransform;
  private final Function<ReqT, RelayReqT> requestTransform;
  private final Function<RelayRespT, RespT> responseTransform;

  /**
   * Creates a new {@link RelayHandler}.
   *
   * @param channelManager the channel manager to look up the channel
   * @param methodDescriptorTransform the function to transform the server call method descriptor to
   *     the client call method descriptor
   * @param requestTransform the function to transform the server call request to the client call
   *     request
   * @param responseTransform the function to transform the client call response to the server call
   *     response
   */
  public RelayHandler(
      ChannelManager channelManager,
      Function<MethodDescriptor<ReqT, RespT>, MethodDescriptor<RelayReqT, RelayRespT>>
          methodDescriptorTransform,
      Function<ReqT, RelayReqT> requestTransform,
      Function<RelayRespT, RespT> responseTransform) {
    this.channelManager = channelManager;
    this.methodDescriptorTransform = methodDescriptorTransform;
    this.requestTransform = requestTransform;
    this.responseTransform = responseTransform;
  }

  @Override
  public ServerCall.Listener<ReqT> startCall(ServerCall<ReqT, RespT> serverCall, Metadata headers) {
    logger.atInfo().log("Start call headers: %s", headers);
    String destination = DestinationUtils.getDestination(headers);
    Optional<Channel> channelOp;
    if (destination == null || (channelOp = channelManager.lookupChannel(destination)).isEmpty()) {
      String message =
          (destination == null)
              ? String.format(
                  "Method not found: %s in the registered service. No relay destination found in"
                      + " the headers: %s",
                  serverCall.getMethodDescriptor().getFullMethodName(), headers)
              : String.format(
                  "Failed to look up the destination %s in the channel manager", destination);
      serverCall.close(Status.INVALID_ARGUMENT.withDescription(message), headers);
      return new ServerCall.Listener<ReqT>() {};
    }
    Channel channel = channelOp.get();
    ClientCall<RelayReqT, RelayRespT> clientCall =
        channel.newCall(
            methodDescriptorTransform.apply(serverCall.getMethodDescriptor()), CallOptions.DEFAULT);
    CallRelay<ReqT, RespT, RelayReqT, RelayRespT> callRelay =
        new CallRelay<>(serverCall, clientCall, requestTransform, responseTransform);
    clientCall.start(callRelay.clientCallListener, headers);
    serverCall.request(1);
    clientCall.request(1);
    return callRelay.serverCallListener;
  }
}
