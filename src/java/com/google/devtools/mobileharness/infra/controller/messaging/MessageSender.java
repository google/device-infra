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

import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.getUnchecked;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.ComponentMessageReceivingEnd;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.GlobalMessageReceivingEnd;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReception;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceptions;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageSend;
import com.google.devtools.mobileharness.infra.controller.messaging.MessageSubscriberBackend.MessageSubscribers;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import java.util.concurrent.CancellationException;

/** Message sender for sending messages of OmniLab messaging system. */
public class MessageSender implements NonThrowingAutoCloseable {

  private final SettableFuture<ImmutableList<MessageSubscribers>> localSubscribers =
      SettableFuture.create();

  /**
   * Sends a message synchronously and returns after finishing sending the message and handling all
   * the receptions, or after {@link #close()} is called.
   *
   * @apiNote this method will wait until {@link #initializeLocalSubscribers} is called (then start
   *     to send the message) or {@link #close} is called (then return immediately)
   */
  void sendMessage(MessageSend messageSend, MessageReceptionsHandler messageReceptionsHandler)
      throws InterruptedException {
    // Waits until local subscribers are initialized.
    ImmutableList<MessageSubscribers> localSubscribers;
    try {
      localSubscribers = getUnchecked(this.localSubscribers);
    } catch (CancellationException e) {
      // Returns if the sender is closed.
      return;
    }

    // Sends message to local subscribers.
    for (MessageSubscribers subscribers : localSubscribers) {
      subscribers.receiveMessage(messageSend, messageReceptionsHandler);
    }

    // Generates ComponentMessageReceivingEnd and GlobalMessageReceivingEnd.
    messageReceptionsHandler.handleMessageReceptions(
        MessageReceptions.newBuilder()
            .addReceptions(
                MessageReception.newBuilder()
                    .setComponentMessageReceivingEnd(
                        ComponentMessageReceivingEnd.getDefaultInstance()))
            .addReceptions(
                MessageReception.newBuilder()
                    .setGlobalMessageReceivingEnd(GlobalMessageReceivingEnd.getDefaultInstance()))
            .build());
  }

  public void initializeLocalSubscribers(ImmutableList<MessageSubscribers> localSubscribers) {
    this.localSubscribers.set(localSubscribers);
  }

  @Override
  public void close() {
    localSubscribers.cancel(/* mayInterruptIfRunning= */ false);
  }
}
