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

package com.google.devtools.mobileharness.shared.logging.controller.queue;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Queues;
import com.google.common.flogger.FluentLogger;
import com.google.logging.v2.LogEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/** Implementation of {@link LogEntryQueue} */
public class LogEntryQueueImpl implements LogEntryQueue {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The maximum elements in the queue. */
  @VisibleForTesting static final int MAX_BACKLOG = 10000;

  private final BlockingQueue<LogEntry> queue = Queues.newArrayBlockingQueue(MAX_BACKLOG);

  @VisibleForTesting
  LogEntryQueueImpl() {}

  @Override
  public boolean add(LogEntry logEntry) {
    boolean success = queue.offer(logEntry);
    if (!success) {
      logger.atWarning().atMostEvery(10, SECONDS).log(
          "Overran logging queue with size %s, dropping messages until the next flush.",
          MAX_BACKLOG);
    }
    return success;
  }

  @Override
  public boolean addAll(List<LogEntry> logEntries) {
    try {
      queue.addAll(logEntries);
      return true;
    } catch (IllegalStateException e) {
      logger.atWarning().atMostEvery(10, SECONDS).log(
          "Overran logging queue with size %s, dropping messages until the next flush.",
          MAX_BACKLOG);
      return false;
    }
  }

  @Override
  public List<LogEntry> poll(int maxElements) {
    List<LogEntry> batch = new ArrayList<>();
    queue.drainTo(batch, maxElements);
    return batch;
  }
}
