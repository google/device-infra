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
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.TeardownConduitRequest;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.TeardownConduitResponse;
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
    EstablishConduitRequest request =
        EstablishConduitRequest.newBuilder()
            .setType(ConduitType.CONDUIT_TYPE_REVERSE)
            .setProtocol(Protocol.PROTOCOL_GRPC)
            .setAutoReconnect(true)
            .setServerName(serverName)
            .setInstanceId(instanceId)
            .setDestinationEndpoint(destinationEndpoint)
            .build();
    return blockingStub.establishConduit(request);
  }

  /** Establishes a gRPC reverse conduit asynchronously. */
  public ListenableFuture<EstablishConduitResponse> establishReverseGrpcConduitAsync(
      String serverName, String instanceId, String destinationEndpoint) {
    EstablishConduitRequest request =
        EstablishConduitRequest.newBuilder()
            .setType(ConduitType.CONDUIT_TYPE_REVERSE)
            .setProtocol(Protocol.PROTOCOL_GRPC)
            .setAutoReconnect(true)
            .setServerName(serverName)
            .setInstanceId(instanceId)
            .setDestinationEndpoint(destinationEndpoint)
            .build();
    return futureStub.establishConduit(request);
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
}
