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

package com.google.devtools.mobileharness.shared.util.comm.messagerelay.service;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.Correspondence;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.shared.util.comm.messagerelay.proto.MessageRelayServiceGrpc;
import com.google.devtools.mobileharness.shared.util.comm.messagerelay.proto.MessageRelayServiceProto.Locator;
import com.google.devtools.mobileharness.shared.util.comm.messagerelay.proto.MessageRelayServiceProto.MessageType;
import com.google.devtools.mobileharness.shared.util.comm.messagerelay.proto.MessageRelayServiceProto.RelayMessage;
import com.google.devtools.mobileharness.shared.util.comm.messagerelay.proto.MessageRelayServiceProto.StreamInfo;
import com.google.devtools.mobileharness.shared.util.comm.messagerelay.proto.MessageRelayServiceProto.UnaryMessage;
import com.google.protobuf.Any;
import com.google.protobuf.StringValue;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MessageRelayServiceTest {
  private static final String STREAM_ID = "stream_id";
  private static final String MESSAGE_ID = "message_id";

  private static final String SERVICE_NAME = "/package.MyService";

  private static final String METHOD_NAME = "MyMethod";
  private static final String HOST1 = "hostname1";
  private static final String HOST2 = "hostname2";

  private static final String UNARY_PAYLOAD = "test_message";

  private static final Correspondence<Throwable, InfraErrorId> GRPC_STATUS_ERROR_CONTAINS_ERROR_ID =
      Correspondence.from(
          (actual, expected) -> actual.getMessage().contains(expected.name()),
          "contains the MobileHarnessException error");

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  private final StreamManager streamManager = StreamManager.getInstance();
  private ManagedChannel channel;

  private MessageRelayServiceGrpc.MessageRelayServiceStub stub;

  @Before
  public void setUp() throws Exception {
    String serverName = InProcessServerBuilder.generateName();
    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new MessageRelayService(streamManager))
            .build()
            .start());
    channel =
        grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());
    stub = MessageRelayServiceGrpc.newStub(channel);
  }

  @Test
  public void relay_unaryMessage_success() {
    FakeObserver resObs1 = new FakeObserver();
    StreamObserver<RelayMessage> reqObs1 = stub.relay(resObs1);
    FakeObserver resObs2 = new FakeObserver();
    StreamObserver<RelayMessage> reqObs2 = stub.relay(resObs2);

    reqObs1.onNext(genWithStreamInfo(HOST1));
    reqObs2.onNext(genWithStreamInfo(HOST2));
    RelayMessage testMsg = genWithUnaryMessage(MessageType.REQUEST, HOST1, HOST2, UNARY_PAYLOAD);
    reqObs1.onNext(testMsg);
    reqObs1.onCompleted();
    reqObs2.onCompleted();

    assertThat(StreamManager.getInstance().getStreamSize(HOST1)).isEqualTo(0);

    assertThat(resObs2.receivedMessages).containsExactly(testMsg);
  }

  @Test
  public void relay_noStreamInfo_errorAndCloseStream() {
    FakeObserver resObs1 = new FakeObserver();
    StreamObserver<RelayMessage> reqObs1 = stub.relay(resObs1);

    RelayMessage testMsg = genWithUnaryMessage(MessageType.REQUEST, HOST1, HOST2, UNARY_PAYLOAD);
    reqObs1.onNext(testMsg);

    assertThat(resObs1.errors)
        .comparingElementsUsing(GRPC_STATUS_ERROR_CONTAINS_ERROR_ID)
        .containsExactly(InfraErrorId.MESSAGE_RELAY_NO_STREAM_INFO);
  }

  private RelayMessage genWithStreamInfo(String hostname) {
    return RelayMessage.newBuilder()
        .setStreamInfo(StreamInfo.newBuilder().setStreamId(STREAM_ID).setHostname(hostname))
        .build();
  }

  private RelayMessage genWithUnaryMessage(
      MessageType type, String src, String dst, String strPayload) {
    return RelayMessage.newBuilder()
        .setUnaryMessage(
            UnaryMessage.newBuilder()
                .setId(MESSAGE_ID)
                .setLocator(
                    Locator.newBuilder()
                        .setSource(src)
                        .setDestination(dst)
                        .setType(type)
                        .setFullServiceName(SERVICE_NAME)
                        .setMethodName(METHOD_NAME))
                .setPayload(Any.pack(StringValue.of(strPayload))))
        .build();
  }

  private static class FakeObserver implements StreamObserver<RelayMessage> {
    final List<RelayMessage> receivedMessages = new ArrayList<>();
    List<Throwable> errors = new ArrayList<>();

    @Override
    public void onNext(RelayMessage value) {
      receivedMessages.add(value);
    }

    @Override
    public void onError(Throwable t) {
      errors.add(t);
    }

    @Override
    public void onCompleted() {}
  }
}
