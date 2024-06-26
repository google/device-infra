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

import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.base.Supplier;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.google.pubsub.v1.PubsubMessage;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Publishes data to Cloud PubSub. */
public class CloudPubSubPublisher extends DataPusher {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final Supplier<Publisher> publisherSupplier;
  private Publisher publisher;

  CloudPubSubPublisher(Supplier<Publisher> publisherSupplier) {
    this.publisherSupplier = publisherSupplier;
  }

  @Override
  public void setUp() throws MobileHarnessException {
    // Lazy initialize resources.
    publisher = publisherSupplier.get();
    if (publisher == null) {
      throw new MobileHarnessException(
          InfraErrorId.CLOUD_PUB_SUB_PUBLISHER_CREATE_PUBLISHER_ERROR, "Publisher not initialized");
    }
  }

  @Override
  public void tearDown() {
    if (publisher != null) {
      try {
        publisher.shutdown();
        publisher.awaitTermination(1, MINUTES);
        publisher = null; // Be idempotent.
      } catch (InterruptedException e) {
        logger.atWarning().withCause(e).log("Interrupted!");
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public <T extends Message> void push(
      List<T> messageData, Consumer<String> successCallback, Consumer<Throwable> failureCallback) {
    for (Message data : messageData) {
      if (data != null) {
        publish(data, successCallback, failureCallback);
      }
    }
  }

  private void publish(
      Message data, Consumer<String> successCallback, Consumer<Throwable> failureCallback) {
    Optional<ApiFuture<String>> futureOp =
        serialize(data)
            .map(bs -> publisher.publish(PubsubMessage.newBuilder().setData(bs).build()));
    if (futureOp.isPresent()) {
      ApiFuture<String> future = futureOp.get();
      ApiFutures.addCallback(
          future,
          new ApiFutureCallback<String>() {
            @Override
            public void onSuccess(String messageId) {
              successCallback.accept(messageId);
            }

            @Override
            public void onFailure(Throwable t) {
              failureCallback.accept(t);
            }
          },
          MoreExecutors.directExecutor());
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
