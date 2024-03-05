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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.comm.messagerelay.proto.MessageRelayServiceGrpc;
import com.google.devtools.mobileharness.shared.util.comm.messagerelay.proto.MessageRelayServiceProto.Locator;
import com.google.devtools.mobileharness.shared.util.comm.messagerelay.proto.MessageRelayServiceProto.RelayMessage;
import com.google.devtools.mobileharness.shared.util.comm.messagerelay.proto.MessageRelayServiceProto.StreamInfo;
import com.google.devtools.mobileharness.shared.util.comm.messagerelay.proto.MessageRelayServiceProto.UnaryMessage;
import com.google.devtools.mobileharness.shared.util.comm.messagerelay.service.StreamManager.RelayStream;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

/** Message relay service for exchanging gRPC message. */
public final class MessageRelayService extends MessageRelayServiceGrpc.MessageRelayServiceImplBase {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final StreamManager streamManager;

  @Inject
  MessageRelayService(StreamManager streamManager) {
    this.streamManager = streamManager;
  }

  @Override
  public StreamObserver<RelayMessage> relay(StreamObserver<RelayMessage> responseObserver) {
    return new RelayRequestObserver(responseObserver);
  }

  private void handleUnaryMessage(UnaryMessage message) throws MobileHarnessException {
    Locator locator = message.getLocator();
    String source = locator.getSource();
    String destination = locator.getDestination();
    String messageId = message.getId();

    RelayStream stream = streamManager.pickUpStream(destination);
    StreamObserver<RelayMessage> obs = stream.observer();
    logger.atInfo().log(
        "Forwarding message %s from host %s to host %s using stream %s",
        messageId, source, destination, stream.streamId());
    obs.onNext(RelayMessage.newBuilder().setUnaryMessage(message).build());
    logger.atInfo().log("Forwarded message %s", messageId);
  }

  private final class RelayRequestObserver implements StreamObserver<RelayMessage> {

    private final StreamObserver<RelayMessage> responseObserver;
    private RelayStream stream = null;

    RelayRequestObserver(StreamObserver<RelayMessage> responseObserver) {
      this.responseObserver = responseObserver;
    }

    @Override
    public void onNext(RelayMessage request) {
      if (request.hasStreamInfo()) {
        StreamInfo streamInfo = request.getStreamInfo();
        stream =
            RelayStream.create(
                streamInfo.getHostname(), streamInfo.getStreamId(), responseObserver);
        streamManager.addStream(stream);
        logger.atInfo().log(
            "Connected: %s, %s", streamInfo.getHostname(), streamInfo.getStreamId());
      } else if (request.hasUnaryMessage()) {
        if (stream == null) {
          MobileHarnessException err =
              new MobileHarnessException(
                  InfraErrorId.MESSAGE_RELAY_NO_STREAM_INFO,
                  "Unknown stream information. The first message on the relay stream must be"
                      + " StreamInfo.");
          // TODO: Respond to the client with an error.
          logger.atWarning().withCause(err).log(
              "Error to process message %s due to missing StreamInfo",
              request.getUnaryMessage().getId());
          logger.atWarning().log("Closing the stream due to missing StreamInfo");
          responseObserver.onError(GrpcExceptionUtil.toStatusRuntimeException(err));
        }
        try {
          handleUnaryMessage(request.getUnaryMessage());
        } catch (MobileHarnessException e) {
          // TODO: Retry after a certain period of time, or respond to the client
          // with an error.
          logger.atWarning().withCause(e).log(
              "Error to process message %s.", request.getUnaryMessage().getId());
        }
      }
    }

    @Override
    public void onError(Throwable t) {
      if (stream != null) {
        streamManager.removeStream(stream);
        logger.atInfo().withCause(t).log(
            "Error on stream %s of host %s, remaining available streams of the host is %d",
            stream.streamId(), stream.hostname(), streamManager.getStreamSize(stream.hostname()));
      } else {
        logger.atWarning().withCause(t).log("Error on unknown stream");
      }
      responseObserver.onError(t);
    }

    @Override
    public void onCompleted() {
      if (stream != null) {
        streamManager.removeStream(stream);
        logger.atInfo().log(
            "Completion on stream %s of host %s, remaining available streams of the host is %d",
            stream.streamId(), stream.hostname(), streamManager.getStreamSize(stream.hostname()));
      } else {
        logger.atWarning().log("Completion on unknown stream");
      }
      responseObserver.onCompleted();
    }
  }
}
