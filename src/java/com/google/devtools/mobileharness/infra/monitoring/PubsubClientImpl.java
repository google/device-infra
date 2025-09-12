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

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.auto.value.AutoValue;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** Publishes data to Cloud PubSub. */
public class PubsubClientImpl extends DataPusher {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // The maximum batch size in bytes of a single Cloud PubSub message is 10000000 and we only send
  // 9MB in each message to reserver some buffer space for overhead.
  private static final int MAX_BATCH_SIZE_BYTES = 9 * 1024 * 1024; // 9MB
  // The maximum number of messages in a single Cloud PubSub message.
  private static final int MAX_MESSAGES_PER_REQUEST = 1000;
  private static final int PUBLISH_DEADLINE_SECONDS = 10;

  private final String pubsubTopic;
  private final Provider<PublisherGrpc.PublisherFutureStub> publisherStubProvider;

  @Inject
  PubsubClientImpl(
      @CloudPubsubTopic String pubsubTopic,
      Provider<PublisherGrpc.PublisherFutureStub> publisherStubProvider) {
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
    List<MessageWithSerializedData<T>> currentBatch = new ArrayList<>();
    int currentBatchSizeBytes = 0;

    for (T message : messageData) {
      if (message == null) {
        continue;
      }
      Optional<ByteString> serializedDataOptional = serialize(message);
      if (serializedDataOptional.isEmpty()) {
        continue;
      }
      ByteString serializedData = serializedDataOptional.get();
      int messageSize = serializedData.size();

      if (messageSize > MAX_BATCH_SIZE_BYTES) {
        logger.atWarning().log(
            "Skipping message because it exceeds the maximum batch size (%d bytes): %s.",
            MAX_BATCH_SIZE_BYTES, message);
        continue;
      }

      if (currentBatchSizeBytes + messageSize > MAX_BATCH_SIZE_BYTES
          || currentBatch.size() >= MAX_MESSAGES_PER_REQUEST) {
        // Current batch is full, add it to the list of batches and start a new one.
        pushAsync(currentBatch, successCallback, failureCallback);
        currentBatch = new ArrayList<>();
        currentBatchSizeBytes = 0;
      }

      currentBatch.add(MessageWithSerializedData.create(message, serializedData));
      currentBatchSizeBytes += messageSize;
    }

    // Add the last batch if it's not empty.
    if (!currentBatch.isEmpty()) {
      pushAsync(currentBatch, successCallback, failureCallback);
    }
  }

  private <T extends Message> void pushAsync(
      List<MessageWithSerializedData<T>> serializedBatch,
      Consumer<String> successCallback,
      Consumer<Throwable> failureCallback) {
    PublishRequest.Builder request = PublishRequest.newBuilder().setTopic(pubsubTopic);

    for (MessageWithSerializedData<T> messageWithSerializedData : serializedBatch) {
      if (messageWithSerializedData.serializedData() != null) {
        request.addMessagesBuilder().setData(messageWithSerializedData.serializedData());
      }
    }

    if (request.getMessagesCount() == 0) {
      logger.atWarning().log("No messages to publish in the current batch.");
      return;
    }

    logger.atInfo().log(
        "Publishing %d messages to topic %s", request.getMessagesCount(), pubsubTopic);

    ListenableFuture<PublishResponse> responseFuture =
        publisherStubProvider
            .get()
            .withDeadlineAfter(PUBLISH_DEADLINE_SECONDS, SECONDS)
            .publish(request.build());
    Futures.addCallback(
        responseFuture,
        new FutureCallback<PublishResponse>() {
          @Override
          public void onSuccess(@Nullable PublishResponse result) {
            successCallback.accept(result.getMessageIdsList().toString());
          }

          @Override
          public void onFailure(Throwable t) {
            failureCallback.accept(t);
          }
        },
        directExecutor());
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

  @AutoValue
  abstract static class MessageWithSerializedData<T extends Message> {
    abstract T message();

    abstract ByteString serializedData();

    static <T extends Message> MessageWithSerializedData<T> create(
        T message, ByteString serializedData) {
      return new AutoValue_PubsubClientImpl_MessageWithSerializedData<>(message, serializedData);
    }
  }
}
