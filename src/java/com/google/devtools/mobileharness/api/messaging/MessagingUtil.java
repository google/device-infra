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

package com.google.devtools.mobileharness.api.messaging;

import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceptions;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageSend;
import com.google.devtools.mobileharness.infra.controller.messaging.MessagingManagerHolder;
import java.util.function.Consumer;

/** Utility for sending messages using the OmniLab messaging system. */
public class MessagingUtil {

  /**
   * Sends a protobuf message to a destination (e.g., a running Mobile Harness test).
   *
   * <p>A subscriber method is a method annotated with {@link SubscribeMessage}, which specifies a
   * protobuf message type it can receive.
   *
   * <p>All subscriber methods of the destination, whose message type is equal to the type of the
   * given protobuf message, will receive the message.
   *
   * <p>If the destination is a running Mobile Harness test, its subscriber methods include all
   * subscriber methods in its Driver/Decorator/plugins (in the current process, for now).
   *
   * <p>This method is asynchronous. Subscriber methods of a component (the current process, for
   * now) will receive the message in another thread (a message receiving thread of this sending)
   * sequentially.
   *
   * <p>If this method returns successfully (which means the destination is found), a non-null
   * {@code messageReceptionsHandler} will be invoked one or multiple times in another thread (a
   * message receptions handling thread of this sending) sequentially. The sequence of {@linkplain
   * com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReception
   * MessageReception} in these invocations will end up with a {@linkplain
   * com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.GlobalMessageReceivingEnd
   * GlobalMessageReceivingEnd}.
   *
   * @throws MessageDestinationNotFoundException if the destination cannot be found (e.g., the
   *     Mobile Harness test is not in the current process or is not running)
   */
  public void sendMessage(
      MessageSend messageSend, Consumer<MessageReceptions> messageReceptionsHandler)
      throws MessageDestinationNotFoundException {
    MessagingManagerHolder.getMessagingManager().sendMessage(messageSend, messageReceptionsHandler);
  }
}
