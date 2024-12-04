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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.messaging.MessageDestinationNotFoundException;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceptions;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageSend;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.inject.Inject;

/** Messaging manager for sending messages. */
public class MessagingManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final MessageSenderFinder messageSenderFinder;
  private final ListeningExecutorService threadPool;

  @Inject
  MessagingManager(MessageSenderFinder messageSenderFinder, ListeningExecutorService threadPool) {
    this.messageSenderFinder = messageSenderFinder;
    this.threadPool = threadPool;
  }

  /**
   * Sends a message asynchronously.
   *
   * @implSpec local message receiving and receptions handling will be in different threads
   * @throws MessageDestinationNotFoundException if the message destination cannot be found
   */
  public void sendMessage(
      MessageSend messageSend, Consumer<MessageReceptions> messageReceptionsHandler)
      throws MessageDestinationNotFoundException {
    checkNotNull(messageSend);
    checkNotNull(messageReceptionsHandler);

    // Finds message sender.
    MessageSender messageSender = messageSenderFinder.findMessageSender(messageSend);

    // Creates message receptions producer and consumer.
    String messageId = UUID.randomUUID().toString();
    BlockingQueue<Optional<MessageReceptions>> queue = new LinkedBlockingDeque<>();
    MessageReceptionsProducer receptionsProducer =
        new MessageReceptionsProducer(messageId, messageSend, messageSender, queue);
    MessageReceptionsConsumer receptionsConsumer =
        new MessageReceptionsConsumer(messageId, messageReceptionsHandler, queue);

    // Starts to send message asynchronously.
    logger.atInfo().log(
        "Send message, message_id=[%s], message_send=[%s]",
        messageId, shortDebugString(messageSend));
    logFailure(
        threadPool.submit(threadRenaming(receptionsProducer, () -> "message-sender-" + messageId)),
        Level.WARNING,
        "Fatal error in message sender [%s]",
        messageId);
    logFailure(
        threadPool.submit(
            threadRenaming(receptionsConsumer, () -> "message-receptions-consumer-" + messageId)),
        Level.WARNING,
        "Fatal error in message receptions consumer [%s]",
        messageId);
  }

  private static class MessageReceptionsProducer implements Callable<Void> {

    private final String messageId;
    private final MessageSend messageSend;
    private final MessageSender messageSender;

    /** Empty indicates the end of the queue. */
    private final BlockingQueue<Optional<MessageReceptions>> queue;

    private MessageReceptionsProducer(
        String messageId,
        MessageSend messageSend,
        MessageSender messageSender,
        BlockingQueue<Optional<MessageReceptions>> queue) {
      this.messageId = messageId;
      this.messageSend = messageSend;
      this.messageSender = messageSender;
      this.queue = queue;
    }

    @Override
    public Void call() throws InterruptedException {
      try {
        messageSender.sendMessage(messageSend, receptions -> queue.put(Optional.of(receptions)));
        logger.atInfo().log("Finished sending message, message_id=[%s]", messageId);
        return null;
      } finally {
        // Puts empty to the queue to let consumer exit.
        boolean interrupted = Thread.interrupted();
        queue.put(Optional.empty());
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  private static class MessageReceptionsConsumer implements Callable<Void> {

    private final String messageId;
    private final Consumer<MessageReceptions> messageReceptionsHandler;

    /** Empty indicates the end of the queue. */
    private final BlockingQueue<Optional<MessageReceptions>> queue;

    private MessageReceptionsConsumer(
        String messageId,
        Consumer<MessageReceptions> messageReceptionsHandler,
        BlockingQueue<Optional<MessageReceptions>> queue) {
      this.messageId = messageId;
      this.messageReceptionsHandler = messageReceptionsHandler;
      this.queue = queue;
    }

    @SuppressWarnings("OptionalAssignedToNull")
    @Override
    public Void call() throws InterruptedException {
      while (true) {
        // Blocks until retrieving the first MessageReceptions.
        Optional<MessageReceptions> headOptional = queue.take();
        if (headOptional.isEmpty()) {
          break;
        }
        MessageReceptions head = headOptional.get();

        // Gets all the rest MessageReceptions.
        boolean finished = false;
        List<MessageReceptions> rest = null;
        while (true) {
          Optional<MessageReceptions> next = queue.poll();
          if (next == null) {
            break;
          }
          if (next.isEmpty()) {
            finished = true;
          } else {
            if (rest == null) {
              rest = new ArrayList<>();
            }
            rest.add(next.get());
          }
        }

        // Merges MessageReceptions if necessary.
        MessageReceptions receptions;
        if (rest == null) {
          receptions = head;
        } else {
          MessagingProto.MessageReceptions.Builder receptionsBuilder =
              MessagingProto.MessageReceptions.newBuilder()
                  .addAllReceptions(head.getReceptionsList());
          for (MessageReceptions restReceptions : rest) {
            receptionsBuilder.addAllReceptions(restReceptions.getReceptionsList());
          }
          receptions = receptionsBuilder.build();
        }

        // Calls the handler.
        try {
          messageReceptionsHandler.accept(receptions);
        } catch (RuntimeException | Error e) {
          logger.atInfo().withCause(e).log(
              "Error when handling message receptions, message_id=[%s], message_receptions=[%s]",
              messageId, shortDebugString(receptions));
        }

        if (finished) {
          break;
        }
      }
      logger.atInfo().log("Finished consuming message receptions, message_id=[%s]", messageId);
      return null;
    }
  }
}
