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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.util.Objects.requireNonNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.truth.Correspondence;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionSummary;
import com.google.devtools.mobileharness.api.messaging.MessageEvent;
import com.google.devtools.mobileharness.api.messaging.SubscribeMessage;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceivingEnd;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceivingError;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceivingResult;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceivingStart;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceivingTimingInfo;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReception;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceptions;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageSend;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageSubscriberInfo;
import com.google.devtools.mobileharness.infra.controller.messaging.MessageSubscriberBackend.InvalidMessageSubscriber;
import com.google.devtools.mobileharness.infra.controller.messaging.MessageSubscriberBackend.MessageSubscriber;
import com.google.devtools.mobileharness.infra.controller.messaging.MessageSubscriberBackend.MessageSubscribers;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.protobuf.Any;
import com.google.protobuf.Duration;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import java.util.function.Consumer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class MessageSubscriberBackendTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @SuppressWarnings("unused")
  private static class Foo {

    /** Not a message subscriber: not annotated with @SubscribeMessage. */
    private Duration m0(MessageEvent<Duration> event) {
      return Duration.getDefaultInstance();
    }

    /** A valid message subscriber. */
    @SubscribeMessage
    private Duration m1(MessageEvent<Duration> event) {
      return add(event.getMessage(), java.time.Duration.ofSeconds(1L));
    }

    /** A valid message subscriber: static method. */
    @SubscribeMessage
    private static Duration m2(MessageEvent<Duration> event) {
      throw new IllegalArgumentException("Error message");
    }

    /** A invalid message subscriber: not a specific message. */
    @SubscribeMessage
    private Duration m3(MessageEvent<Message> event) {
      return Duration.getDefaultInstance();
    }

    /** An invalid message subscriber: zero parameter. */
    @SubscribeMessage
    private Duration m4() {
      return Duration.getDefaultInstance();
    }

    /** An invalid message subscriber: two parameters. */
    @SubscribeMessage
    private Duration m5(MessageEvent<Duration> event, String string) {
      return Duration.getDefaultInstance();
    }

    /** An invalid message subscriber: wrong parameter type. */
    @SubscribeMessage
    private Duration m6(String string) {
      return Duration.getDefaultInstance();
    }

    /** An invalid message subscriber: no generic type. */
    @SubscribeMessage
    private Duration m7(@SuppressWarnings("rawtypes") MessageEvent event) {
      return Duration.getDefaultInstance();
    }

    /** An invalid message subscriber: generic type variable. */
    @SubscribeMessage
    private <T extends Duration> Duration m8(MessageEvent<T> event) {
      return Duration.getDefaultInstance();
    }

    /** An invalid message subscriber: wildcard type. */
    @SubscribeMessage
    private Duration m9(MessageEvent<? extends Duration> event) {
      return Duration.getDefaultInstance();
    }

    /** An invalid message subscriber: wrong return type. */
    @SubscribeMessage
    private void m10(MessageEvent<Message> event) {}

    /** An invalid message subscriber: wrong return type. */
    @SubscribeMessage
    private String m11(MessageEvent<Message> event) {
      return "";
    }
  }

  private static Duration add(Duration duration, java.time.Duration delta) {
    return TimeUtils.toProtoDuration(TimeUtils.toJavaDuration(duration).plus(delta));
  }

  @Mock private Consumer<MessageReceptions> messageReceptionsHandler;

  @Captor private ArgumentCaptor<MessageReceptions> messageReceptionsCaptor;

  @Test
  public void searchMessageSubscribers() {
    MessageSubscribers messageSubscribers =
        MessageSubscriberBackend.searchMessageSubscribers(new Foo());

    // m1 ~ m3 are valid message subscribers.
    assertThat(messageSubscribers.messageSubscribers())
        .comparingElementsUsing(
            Correspondence.<MessageSubscriber, String>transforming(
                subscriber -> requireNonNull(subscriber).method().getName(),
                "has a method name of"))
        .containsExactly("m1", "m2");

    // m4 ~ m11 are invalid message subscribers.
    assertThat(messageSubscribers.invalidMessageSubscribers())
        .comparingElementsUsing(
            Correspondence.<InvalidMessageSubscriber, String>transforming(
                subscriber -> requireNonNull(subscriber).method().getName(),
                "has a method name of"))
        .containsExactly("m3", "m4", "m5", "m6", "m7", "m8", "m9", "m10", "m11");

    // Verifies message type and result type.
    MessageSubscriber messageSubscriber =
        messageSubscribers.messageSubscribers().stream()
            .filter(subscriber -> subscriber.method().getName().equals("m1"))
            .findFirst()
            .orElseThrow();
    assertThat(messageSubscriber.messageType()).isEqualTo(Duration.class);
    assertThat(messageSubscriber.resultType()).isEqualTo(Duration.class);
  }

  @Test
  public void receiveMessage() {
    MessageSubscribers messageSubscribers =
        MessageSubscriberBackend.searchMessageSubscribers(new Foo());

    // Sends a message without subscriber.
    messageSubscribers.receiveMessage(
        MessageSend.newBuilder().setMessage(Any.pack(Timestamp.getDefaultInstance())).build(),
        messageReceptionsHandler);
    verify(messageReceptionsHandler, never()).accept(messageReceptionsCaptor.capture());

    // Sends a message with 2 subscribers.
    messageSubscribers.receiveMessage(
        MessageSend.newBuilder()
            .setMessage(Any.pack(TimeUtils.toProtoDuration(java.time.Duration.ofSeconds(123L))))
            .build(),
        messageReceptionsHandler);
    verify(messageReceptionsHandler, atLeastOnce()).accept(messageReceptionsCaptor.capture());

    assertThat(messageReceptionsCaptor.getAllValues())
        .comparingExpectedFieldsOnly()
        .containsExactly(
            MessageReceptions.newBuilder()
                .addReceptions(
                    MessageReception.newBuilder()
                        .setSubscriberInfo(MessageSubscriberInfo.newBuilder().setMethodName("m1"))
                        .setReceivingStart(
                            MessageReceivingStart.newBuilder()
                                .setReceivingTimingInfo(
                                    MessageReceivingTimingInfo.getDefaultInstance())))
                .build(),
            MessageReceptions.newBuilder()
                .addReceptions(
                    MessageReception.newBuilder()
                        .setSubscriberInfo(MessageSubscriberInfo.newBuilder().setMethodName("m1"))
                        .setReceivingEnd(
                            MessageReceivingEnd.newBuilder()
                                .setReceivingTimingInfo(
                                    MessageReceivingTimingInfo.getDefaultInstance())
                                .setSuccess(
                                    MessageReceivingResult.newBuilder()
                                        .setSubscriberReceivingResult(
                                            Any.pack(
                                                TimeUtils.toProtoDuration(
                                                    java.time.Duration.ofSeconds(124L)))))))
                .build(),
            MessageReceptions.newBuilder()
                .addReceptions(
                    MessageReception.newBuilder()
                        .setSubscriberInfo(MessageSubscriberInfo.newBuilder().setMethodName("m2"))
                        .setReceivingStart(
                            MessageReceivingStart.newBuilder()
                                .setReceivingTimingInfo(
                                    MessageReceivingTimingInfo.getDefaultInstance())))
                .build(),
            MessageReceptions.newBuilder()
                .addReceptions(
                    MessageReception.newBuilder()
                        .setSubscriberInfo(MessageSubscriberInfo.newBuilder().setMethodName("m2"))
                        .setReceivingEnd(
                            MessageReceivingEnd.newBuilder()
                                .setReceivingTimingInfo(
                                    MessageReceivingTimingInfo.getDefaultInstance())
                                .setFailure(
                                    MessageReceivingError.newBuilder()
                                        .setSubscriberMethodInvocationError(
                                            MessagingProto.Exception.newBuilder()
                                                .setException(
                                                    ExceptionDetail.newBuilder()
                                                        .setSummary(
                                                            ExceptionSummary.newBuilder()
                                                                .setMessage("Error message")))))))
                .build());
  }
}
