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

package com.google.devtools.mobileharness.shared.util.comm.dualconduit.client;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishConduitRequest;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishConduitRequest.ConduitType;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishConduitRequest.Protocol;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishConduitResponse;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishSessionRequest;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishSessionResponse;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.TeardownConduitRequest;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.TeardownConduitResponse;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.TeardownSessionRequest;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.TeardownSessionResponse;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitServiceGrpc;
import io.grpc.Channel;

/** Client for DualConduitService. */
public class DualConduitClient {

  private final DualConduitServiceGrpc.DualConduitServiceBlockingStub blockingStub;
  private final DualConduitServiceGrpc.DualConduitServiceFutureStub futureStub;

  public DualConduitClient(Channel channel) {
    this.blockingStub = DualConduitServiceGrpc.newBlockingStub(channel);
    this.futureStub = DualConduitServiceGrpc.newFutureStub(channel);
  }

  /** Establishes a gRPC reverse conduit. */
  public EstablishConduitResponse establishReverseGrpcConduit(
      String serverName, String instanceId, String destinationEndpoint) {
    return blockingStub.establishConduit(
        createReverseGrpcConduitRequest(
            serverName, instanceId, destinationEndpoint, /* autoReconnect= */ true));
  }

  /** Establishes a gRPC reverse conduit asynchronously. */
  public ListenableFuture<EstablishConduitResponse> establishReverseGrpcConduitAsync(
      String serverName, String instanceId, String destinationEndpoint) {
    return futureStub.establishConduit(
        createReverseGrpcConduitRequest(
            serverName, instanceId, destinationEndpoint, /* autoReconnect= */ true));
  }

  /** Tears down a conduit. */
  public TeardownConduitResponse teardownConduit(String conduitId) {
    TeardownConduitRequest request =
        TeardownConduitRequest.newBuilder().setConduitId(conduitId).build();
    return blockingStub.teardownConduit(request);
  }

  /** Tears down a conduit asynchronously. */
  public ListenableFuture<TeardownConduitResponse> teardownConduitAsync(String conduitId) {
    TeardownConduitRequest request =
        TeardownConduitRequest.newBuilder().setConduitId(conduitId).build();
    return futureStub.teardownConduit(request);
  }

  /** Establishes a gRPC reverse conduit session. */
  public EstablishSessionResponse establishReverseGrpcConduitSession(
      String serverName, String instanceId, String destinationEndpoint, int reverseConduitCount) {
    EstablishSessionRequest request =
        EstablishSessionRequest.newBuilder()
            .setEstablishConduitRequest(
                createReverseGrpcConduitRequest(
                    serverName, instanceId, destinationEndpoint, /* autoReconnect= */ false))
            .setReverseConduitCount(reverseConduitCount)
            .setAutoReconnect(true)
            .build();
    return blockingStub.establishSession(request);
  }

  /** Establishes a gRPC reverse conduit session asynchronously. */
  public ListenableFuture<EstablishSessionResponse> establishReverseGrpcConduitSessionAsync(
      String serverName, String instanceId, String destinationEndpoint, int reverseConduitCount) {
    EstablishSessionRequest request =
        EstablishSessionRequest.newBuilder()
            .setEstablishConduitRequest(
                createReverseGrpcConduitRequest(
                    serverName, instanceId, destinationEndpoint, /* autoReconnect= */ false))
            .setReverseConduitCount(reverseConduitCount)
            .setAutoReconnect(true)
            .build();
    return futureStub.establishSession(request);
  }

  private static EstablishConduitRequest createReverseGrpcConduitRequest(
      String serverName, String instanceId, String destinationEndpoint, boolean autoReconnect) {
    return EstablishConduitRequest.newBuilder()
        .setType(ConduitType.CONDUIT_TYPE_REVERSE)
        .setProtocol(Protocol.PROTOCOL_GRPC)
        .setAutoReconnect(autoReconnect)
        .setServerName(serverName)
        .setInstanceId(instanceId)
        .setDestinationEndpoint(destinationEndpoint)
        .build();
  }

  /** Tears down a session. */
  public TeardownSessionResponse teardownSession(String sessionId) {
    TeardownSessionRequest request =
        TeardownSessionRequest.newBuilder().setSessionId(sessionId).build();
    return blockingStub.teardownSession(request);
  }

  /** Tears down a session asynchronously. */
  public ListenableFuture<TeardownSessionResponse> teardownSessionAsync(String sessionId) {
    TeardownSessionRequest request =
        TeardownSessionRequest.newBuilder().setSessionId(sessionId).build();
    return futureStub.teardownSession(request);
  }
}
