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
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishSessionRequest;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishSessionResponse;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.ServiceLocator;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.TeardownConduitRequest;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.TeardownConduitResponse;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.TeardownSessionRequest;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.TeardownSessionResponse;
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

    EstablishConduitRequest recordedRequest = service.getRecordedConduitRequest();
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
    TeardownConduitRequest recordedRequest = service.getRecordedTeardownConduitRequest();
    assertThat(recordedRequest.getConduitId()).isEqualTo("fake-conduit-id");
  }

  @Test
  public void teardownConduitAsync_success() throws Exception {
    ListenableFuture<TeardownConduitResponse> future =
        client.teardownConduitAsync("fake-conduit-id");

    TeardownConduitResponse response = future.get();

    assertThat(response).isNotNull();
  }

  @Test
  public void establishReverseGrpcConduitSession_success() {
    EstablishSessionResponse response =
        client.establishReverseGrpcConduitSession("my-server", "localhost", "backend:50051");

    assertThat(response.getSessionId()).isEqualTo("fake-session-id");
    assertThat(response.getEstablishConduitResponsesCount()).isEqualTo(1);
    assertThat(response.getEstablishConduitResponses(0).getConduitId())
        .isEqualTo("fake-conduit-id");
    assertThat(response.getEstablishConduitResponses(0).getServiceLocator().getXdsAddress())
        .isEqualTo("xds:///my-server.dcon");

    EstablishSessionRequest recordedRequest = service.getRecordedSessionRequest();
    assertThat(recordedRequest.getAutoReconnect()).isTrue();
    EstablishConduitRequest conduitRequest = recordedRequest.getEstablishConduitRequest();
    assertThat(conduitRequest.getType()).isEqualTo(ConduitType.CONDUIT_TYPE_REVERSE);
    assertThat(conduitRequest.getProtocol()).isEqualTo(Protocol.PROTOCOL_GRPC);
    assertThat(conduitRequest.getServerName()).isEqualTo("my-server");
    assertThat(conduitRequest.getInstanceId()).isEqualTo("localhost");
    assertThat(conduitRequest.getDestinationEndpoint()).isEqualTo("backend:50051");
  }

  @Test
  public void establishReverseGrpcConduitSessionAsync_success() throws Exception {
    ListenableFuture<EstablishSessionResponse> future =
        client.establishReverseGrpcConduitSessionAsync("my-server", "localhost", "backend:50051");

    EstablishSessionResponse response = future.get();

    assertThat(response.getSessionId()).isEqualTo("fake-session-id");
    assertThat(response.getEstablishConduitResponsesCount()).isEqualTo(1);
    assertThat(response.getEstablishConduitResponses(0).getConduitId())
        .isEqualTo("fake-conduit-id");
  }

  @Test
  public void teardownSession_success() {
    TeardownSessionResponse response = client.teardownSession("fake-session-id");

    assertThat(response).isNotNull();
    TeardownSessionRequest recordedRequest = service.getRecordedTeardownSessionRequest();
    assertThat(recordedRequest.getSessionId()).isEqualTo("fake-session-id");
  }

  @Test
  public void teardownSessionAsync_success() throws Exception {
    ListenableFuture<TeardownSessionResponse> future =
        client.teardownSessionAsync("fake-session-id");

    TeardownSessionResponse response = future.get();

    assertThat(response).isNotNull();
  }

  private static class FakeDualConduitService
      extends DualConduitServiceGrpc.DualConduitServiceImplBase {
    private EstablishConduitRequest recordedConduitRequest;
    private TeardownConduitRequest recordedTeardownConduitRequest;
    private EstablishSessionRequest recordedSessionRequest;
    private TeardownSessionRequest recordedTeardownSessionRequest;

    @Override
    public void establishConduit(
        EstablishConduitRequest request,
        StreamObserver<EstablishConduitResponse> responseObserver) {
      this.recordedConduitRequest = request;
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
      this.recordedTeardownConduitRequest = request;
      TeardownConduitResponse response = TeardownConduitResponse.getDefaultInstance();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void establishSession(
        EstablishSessionRequest request,
        StreamObserver<EstablishSessionResponse> responseObserver) {
      this.recordedSessionRequest = request;
      EstablishSessionResponse response =
          EstablishSessionResponse.newBuilder()
              .setSessionId("fake-session-id")
              .addEstablishConduitResponses(
                  EstablishConduitResponse.newBuilder()
                      .setConduitId("fake-conduit-id")
                      .setServiceLocator(
                          ServiceLocator.newBuilder()
                              .setXdsAddress("xds:///my-server.dcon")
                              .build())
                      .build())
              .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void teardownSession(
        TeardownSessionRequest request, StreamObserver<TeardownSessionResponse> responseObserver) {
      this.recordedTeardownSessionRequest = request;
      TeardownSessionResponse response = TeardownSessionResponse.getDefaultInstance();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    EstablishConduitRequest getRecordedConduitRequest() {
      return recordedConduitRequest;
    }

    TeardownConduitRequest getRecordedTeardownConduitRequest() {
      return recordedTeardownConduitRequest;
    }

    EstablishSessionRequest getRecordedSessionRequest() {
      return recordedSessionRequest;
    }

    TeardownSessionRequest getRecordedTeardownSessionRequest() {
      return recordedTeardownSessionRequest;
    }
  }
}
