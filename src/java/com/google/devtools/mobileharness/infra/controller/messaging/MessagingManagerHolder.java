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
import static com.google.common.base.Preconditions.checkState;

import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import java.util.concurrent.atomic.AtomicReference;

/** A holder for holding a singleton {@link MessagingManager} for messaging utils. */
public class MessagingManagerHolder {

  private static final AtomicReference<MessagingManager> messagingManager = new AtomicReference<>();

  /** This method needs to be called once and only once in a process to enable messaging utils. */
  public static void initialize(MessageSenderFinder messageSenderFinder) {
    initialize(
        new MessagingManager(
            checkNotNull(messageSenderFinder),
            ThreadPools.createStandardThreadPool("default-messaging-manager-thread-pool")));
  }

  /** This method needs to be called once and only once in a process to enable messaging utils. */
  public static void initialize(MessagingManager messagingManager) {
    checkState(
        MessagingManagerHolder.messagingManager.compareAndSet(
            /* expectedValue= */ null, checkNotNull(messagingManager)),
        "MessagingManager has already been initialized");
  }

  public static MessagingManager getMessagingManager() {
    MessagingManager messagingManager = MessagingManagerHolder.messagingManager.get();
    checkState(messagingManager != null, "MessagingManager has not been initialized");
    return messagingManager;
  }

  private MessagingManagerHolder() {}
}
