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

import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.devtools.mobileharness.api.messaging.MessageEvent;
import com.google.protobuf.Message;

/** Implementation of {@link MessageEvent}. */
@AutoValue
abstract class MessageEventImpl implements MessageEvent<Message> {

  @Override
  public abstract Message getMessage();

  @Memoized
  @Override
  public String toString() {
    return shortDebugString(getMessage());
  }

  static MessageEventImpl of(Message message) {
    return new AutoValue_MessageEventImpl(message);
  }
}
