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

import com.google.common.collect.Lists;
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
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** Publishes data to Cloud PubSub. */
public class PubsubClientImpl extends DataPusher {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // TODO: Make this depend on the size of each message to publish.
  private static final int MAX_DATASETS_IN_ONE_QUERY = 20;
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
    List<List<T>> batches = Lists.partition(messageData, MAX_DATASETS_IN_ONE_QUERY);

    for (List<T> batch : batches) {
      pushAsync(batch, successCallback, failureCallback);
    }
  }

  private <T extends Message> void pushAsync(
      List<T> batch, Consumer<String> successCallback, Consumer<Throwable> failureCallback) {
    PublishRequest.Builder request = PublishRequest.newBuilder().setTopic(pubsubTopic);

    for (Message data : batch) {
      if (data != null) {
        Optional<ByteString> serializedData = serialize(data);
        if (serializedData.isEmpty()) {
          continue;
        }
        request.addMessagesBuilder().setData(serializedData.get());
      }
    }

    if (request.getMessagesCount() == 0) {
      logger.atWarning().log("No messages to publish in the current batch.");
      return;
    }

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
      return Optional.of(ByteString.copyFromUtf8(JsonFormat.printer().print(data)));
    } catch (InvalidProtocolBufferException e) {
      logger.atWarning().withCause(e).log(
          "Failed to convert proto message %s to byte string.", data);
      return Optional.empty();
    }
  }
}
