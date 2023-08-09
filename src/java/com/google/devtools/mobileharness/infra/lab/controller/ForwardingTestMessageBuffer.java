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

package com.google.devtools.mobileharness.infra.lab.controller;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.test.TestInfoManager;
import com.google.devtools.mobileharness.shared.util.comm.messaging.message.TestMessageInfo;
import com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent;
import java.time.Duration;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The forwarding test message buffer for buffering test messages to forward from the lab side to
 * the client side.
 */
public class ForwardingTestMessageBuffer {

  /** The logger. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The interval of cleaning up. */
  @VisibleForTesting static final Duration CLEAN_UP_INTERVAL = Duration.ofMinutes(11L);

  /** The cleaner task. */
  @VisibleForTesting
  final TimerTask cleanerTask =
      new TimerTask() {

        @Override
        public void run() {
          synchronized (bufferedMessages) {
            // For each test:
            Iterator<Entry<String, Collection<TestMessageInfo>>> iterator =
                bufferedMessages.asMap().entrySet().iterator();
            while (iterator.hasNext()) {
              Entry<String, Collection<TestMessageInfo>> entry = iterator.next();
              String rootTestId = entry.getKey();
              Collection<TestMessageInfo> messages = entry.getValue();

              // If the job manager can not find the test info, the messages of the test can be
              // removed.
              try {
                testInfoManager.getTestInfo(rootTestId);
              } catch (MobileHarnessException e) {
                logger.atInfo().log(
                    "Remove test messages of expired root test %s: %s", rootTestId, messages);
                iterator.remove();
              }
            }
          }
        }
      };

  /** The forwarding messages {@code <rootTestId, message>}. */
  @VisibleForTesting final ListMultimap<String, TestMessageInfo> bufferedMessages;

  /** The job manager. */
  private final TestInfoManager testInfoManager;

  /** The constructor. */
  public ForwardingTestMessageBuffer(TestInfoManager testInfoManager) {
    this(testInfoManager, new Timer("ForwardingTestMessageBufferCleaner", /* isDaemon= */ true));
  }

  /** The constructor for testing. */
  @VisibleForTesting
  ForwardingTestMessageBuffer(TestInfoManager testInfoManager, Timer cleanerTimer) {
    this.testInfoManager = testInfoManager;
    bufferedMessages = ArrayListMultimap.create();
    cleanerTimer.schedule(cleanerTask, CLEAN_UP_INTERVAL.toMillis(), CLEAN_UP_INTERVAL.toMillis());
  }

  /** Polls the buffered forwarding test messages of a root test. */
  public List<TestMessageInfo> pollForwardingTestMessages(String rootTestId) {
    synchronized (bufferedMessages) {
      return bufferedMessages.removeAll(rootTestId);
    }
  }

  /** Adds a forwarding test message. */
  @Subscribe
  @VisibleForTesting
  void addForwardingTestMessage(TestMessageEvent testMessageEvent) {
    if (!testMessageEvent.getTestMessageInfo().isRemote()) {
      TestMessageInfo testMessageInfo = testMessageEvent.getTestMessageInfo();
      logger.atFine().log("Forward test message to client: %s", testMessageInfo);
      synchronized (bufferedMessages) {
        bufferedMessages.put(testMessageInfo.rootTestId(), testMessageInfo);
      }
    }
  }
}
