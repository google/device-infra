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

package com.google.devtools.mobileharness.infra.monitoring;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.monitoring.CloudPubsubMonitorModule.CloudPubsubTopic;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PublishResponse;
import com.google.pubsub.v1.PublisherGrpc;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Publishes data to Cloud PubSub. */
public class PubsubClientImpl extends DataPusher {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String pubsubTopic;
  private final Provider<PublisherGrpc.PublisherBlockingStub> publisherStubProvider;

  @Inject
  PubsubClientImpl(
      @CloudPubsubTopic String pubsubTopic,
      Provider<PublisherGrpc.PublisherBlockingStub> publisherStubProvider) {
    this.pubsubTopic = pubsubTopic;
    this.publisherStubProvider = publisherStubProvider;
  }

  @Override
  public void setUp() {}

  @Override
  public void tearDown() {}

  @Override
  public <T extends Message> void push(
      List<T> messageData, Consumer<String> successCallback, Consumer<Throwable> failureCallback) {
    PublishRequest.Builder request = PublishRequest.newBuilder().setTopic(pubsubTopic);

    for (Message data : messageData) {
      if (data != null) {
        Optional<ByteString> serializedData = serialize(data);
        if (serializedData.isEmpty()) {
          continue;
        }
        request.addMessagesBuilder().setData(serializedData.get());
      }
    }

    if (request.getMessagesCount() == 0) {
      logger.atWarning().log("No messages to publish.");
      return;
    }

    PublishResponse response;
    try {
      response =
          GrpcStubUtil.invoke(
              publisherStubProvider.get()::publish,
              request.build(),
              InfraErrorId.FAIL_TO_PUBLISH_MESSAGE_TO_CLOUD_PUB_SUB,
              "Failed to publish message to Cloud PubSub");
      successCallback.accept(response.getMessageIdsList().toString());
    } catch (GrpcExceptionWithErrorId e) {
      failureCallback.accept(e);
    }
  }

  private static Optional<ByteString> serialize(Message data) {
    try {
      return Optional.of(
          ByteString.copyFromUtf8(JsonFormat.printer().preservingProtoFieldNames().print(data)));
    } catch (InvalidProtocolBufferException e) {
      logger.atWarning().withCause(e).log(
          "Failed to convert proto message %s to byte string.", data);
      return Optional.empty();
    }
  }
}
