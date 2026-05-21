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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishConduitRequest;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishConduitRequest.ConduitType;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishConduitRequest.Protocol;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishConduitResponse;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.ServiceLocator;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.TeardownConduitRequest;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.TeardownConduitResponse;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitServiceGrpc;
import io.grpc.Channel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DualConduitClientTest {

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  private DualConduitClient client;
  private final FakeDualConduitService service = new FakeDualConduitService();

  @Before
  public void setUp() throws Exception {
    String serverName = InProcessServerBuilder.generateName();
    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(service)
            .build()
            .start());
    Channel channel =
        grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());
    client = new DualConduitClient(channel);
  }

  @Test
  public void establishReverseGrpcConduit_success() {
    EstablishConduitResponse response =
        client.establishReverseGrpcConduit("my-server", "localhost", "backend:50051");

    assertThat(response.getConduitId()).isEqualTo("fake-conduit-id");
    assertThat(response.getServiceLocator().getXdsAddress()).isEqualTo("xds:///my-server.dcon");

    EstablishConduitRequest recordedRequest = service.getRecordedRequest();
    assertThat(recordedRequest.getType()).isEqualTo(ConduitType.CONDUIT_TYPE_REVERSE);
    assertThat(recordedRequest.getProtocol()).isEqualTo(Protocol.PROTOCOL_GRPC);
    assertThat(recordedRequest.getServerName()).isEqualTo("my-server");
    assertThat(recordedRequest.getInstanceId()).isEqualTo("localhost");
    assertThat(recordedRequest.getDestinationEndpoint()).isEqualTo("backend:50051");
  }

  @Test
  public void establishReverseGrpcConduitAsync_success() throws Exception {
    ListenableFuture<EstablishConduitResponse> future =
        client.establishReverseGrpcConduitAsync("my-server", "localhost", "backend:50051");

    EstablishConduitResponse response = future.get();

    assertThat(response.getConduitId()).isEqualTo("fake-conduit-id");
    assertThat(response.getServiceLocator().getXdsAddress()).isEqualTo("xds:///my-server.dcon");
  }

  @Test
  public void teardownConduit_success() {
    TeardownConduitResponse response = client.teardownConduit("fake-conduit-id");

    assertThat(response).isNotNull();
    TeardownConduitRequest recordedRequest = service.getRecordedTeardownRequest();
    assertThat(recordedRequest.getConduitId()).isEqualTo("fake-conduit-id");
  }

  @Test
  public void teardownConduitAsync_success() throws Exception {
    ListenableFuture<TeardownConduitResponse> future =
        client.teardownConduitAsync("fake-conduit-id");

    TeardownConduitResponse response = future.get();

    assertThat(response).isNotNull();
  }

  private static class FakeDualConduitService
      extends DualConduitServiceGrpc.DualConduitServiceImplBase {
    private EstablishConduitRequest recordedRequest;
    private TeardownConduitRequest recordedTeardownRequest;

    @Override
    public void establishConduit(
        EstablishConduitRequest request,
        StreamObserver<EstablishConduitResponse> responseObserver) {
      this.recordedRequest = request;
      EstablishConduitResponse response =
          EstablishConduitResponse.newBuilder()
              .setConduitId("fake-conduit-id")
              .setServiceLocator(
                  ServiceLocator.newBuilder().setXdsAddress("xds:///my-server.dcon").build())
              .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void teardownConduit(
        TeardownConduitRequest request, StreamObserver<TeardownConduitResponse> responseObserver) {
      this.recordedTeardownRequest = request;
      TeardownConduitResponse response = TeardownConduitResponse.getDefaultInstance();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    EstablishConduitRequest getRecordedRequest() {
      return recordedRequest;
    }

    TeardownConduitRequest getRecordedTeardownRequest() {
      return recordedTeardownRequest;
    }
  }
}
