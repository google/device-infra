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

package com.google.devtools.mobileharness.infra.controller.messaging;

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReception.TypeCase;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceptions;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingServiceGrpc;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingServiceProto.SendMessageRequest;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingServiceProto.SendMessageResponse;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import io.grpc.stub.StreamObserver;
import java.util.function.Consumer;
import javax.inject.Inject;

/** Implementation of {@link MessagingServiceGrpc}. */
public class MessagingService extends MessagingServiceGrpc.MessagingServiceImplBase {

  private final MessagingManager messagingManager;

  @Inject
  MessagingService(MessagingManager messagingManager) {
    this.messagingManager = messagingManager;
  }

  @Override
  public void sendMessage(
      SendMessageRequest request, StreamObserver<SendMessageResponse> responseObserver) {
    try {
      messagingManager.sendMessage(
          request.getMessageSend(), new MessageReceptionsHandler(responseObserver));
    } catch (MobileHarnessException | RuntimeException | Error e) {
      GrpcServiceUtil.handleFailure(
          e,
          responseObserver,
          "method",
          MessagingServiceGrpc.getServiceDescriptor(),
          MessagingServiceGrpc.getSendMessageMethod());
    }
  }

  private static class MessageReceptionsHandler implements Consumer<MessageReceptions> {

    private final StreamObserver<SendMessageResponse> responseObserver;

    private MessageReceptionsHandler(StreamObserver<SendMessageResponse> responseObserver) {
      this.responseObserver = responseObserver;
    }

    @Override
    public void accept(MessageReceptions messageReceptions) {
      responseObserver.onNext(
          SendMessageResponse.newBuilder().setMessageReceptions(messageReceptions).build());

      // If the receptions contain GlobalMessageReceivingEnd, calls onCompleted().
      if (messageReceptions.getReceptionsList().stream()
          .anyMatch(
              reception -> reception.getTypeCase() == TypeCase.GLOBAL_MESSAGE_RECEIVING_END)) {
        responseObserver.onCompleted();
      }
    }
  }
}
