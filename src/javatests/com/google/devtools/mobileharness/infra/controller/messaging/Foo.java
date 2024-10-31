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

import com.google.devtools.mobileharness.api.messaging.MessageEvent;
import com.google.devtools.mobileharness.api.messaging.SubscribeMessage;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.protobuf.Duration;
import com.google.protobuf.Message;

@SuppressWarnings("unused")
class Foo {

  /** Not a message subscriber: not annotated with @SubscribeMessage. */
  private Duration m0(MessageEvent<Duration> event) {
    return Duration.getDefaultInstance();
  }

  /** A valid message subscriber. */
  @SubscribeMessage
  private Duration m1(MessageEvent<Duration> event) {
    return TimeUtils.toProtoDuration(TimeUtils.toJavaDuration(event.getMessage()).plusSeconds(1L));
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
