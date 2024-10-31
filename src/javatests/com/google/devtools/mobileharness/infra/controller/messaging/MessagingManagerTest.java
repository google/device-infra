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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionSummary;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceivingEnd;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceivingError;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceivingResult;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceivingTimingInfo;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReception;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageReceptions;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageSend;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageSubscriberInfo;
import com.google.devtools.mobileharness.infra.controller.messaging.MessageSubscriberBackend.MessageSubscribers;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.Any;
import java.time.Duration;
import java.util.function.Consumer;
import javax.inject.Inject;
import org.junit.Before;
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
public class MessagingManagerTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Consumer<MessageReceptions> messageReceptionsHandler;

  @Bind @Mock private MessageSenderFinder messageSenderFinder;

  @Bind private final ListeningExecutorService threadPool = newDirectExecutorService();

  @Inject private MessagingManager messagingManager;
  @Inject private MessageSender messageSender;

  @Captor private ArgumentCaptor<MessageReceptions> messageReceptionsCaptor;

  @Before
  public void setUp() throws Exception {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

    when(messageSenderFinder.findMessageSender(any(MessageSend.class))).thenReturn(messageSender);
  }

  @Test
  public void sendMessage() throws Exception {
    MessageSubscribers messageSubscribers =
        MessageSubscriberBackend.searchMessageSubscribers(new Foo());
    messageSender.initializeLocalSubscribers(ImmutableList.of(messageSubscribers));

    messagingManager.sendMessage(
        MessageSend.newBuilder()
            .setMessage(Any.pack(TimeUtils.toProtoDuration(Duration.ofSeconds(123L))))
            .build(),
        messageReceptionsHandler);

    verify(messageReceptionsHandler, atLeastOnce()).accept(messageReceptionsCaptor.capture());
    assertThat(
            messageReceptionsCaptor.getAllValues().stream()
                .flatMap(receptions -> receptions.getReceptionsList().stream())
                .collect(toImmutableList()))
        .comparingExpectedFieldsOnly()
        .containsAtLeast(
            MessageReception.newBuilder()
                .setReceivingEnd(
                    MessageReceivingEnd.newBuilder()
                        .setSubscriberInfo(MessageSubscriberInfo.newBuilder().setMethodName("m1"))
                        .setReceivingTimingInfo(MessageReceivingTimingInfo.getDefaultInstance())
                        .setSuccess(
                            MessageReceivingResult.newBuilder()
                                .setSubscriberReceivingResult(
                                    Any.pack(TimeUtils.toProtoDuration(Duration.ofSeconds(124L))))))
                .build(),
            MessageReception.newBuilder()
                .setReceivingEnd(
                    MessageReceivingEnd.newBuilder()
                        .setSubscriberInfo(MessageSubscriberInfo.newBuilder().setMethodName("m2"))
                        .setReceivingTimingInfo(MessageReceivingTimingInfo.getDefaultInstance())
                        .setFailure(
                            MessageReceivingError.newBuilder()
                                .setSubscriberMethodInvocationError(
                                    MessagingProto.Exception.newBuilder()
                                        .setException(
                                            ExceptionDetail.newBuilder()
                                                .setSummary(
                                                    ExceptionSummary.newBuilder()
                                                        .setMessage("Error message"))))))
                .build());
  }
}
