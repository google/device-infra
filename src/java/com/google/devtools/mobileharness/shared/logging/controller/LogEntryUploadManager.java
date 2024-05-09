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

package com.google.devtools.mobileharness.shared.logging.controller;

import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.logging.controller.queue.LogEntryQueue;
import com.google.devtools.mobileharness.shared.logging.controller.uploader.LogUploader;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.logging.v2.LogEntry;
import com.google.protobuf.util.Timestamps;
import java.time.Duration;
import java.util.List;

/**
 * The upload manager which will poll the {@link LogEntry} from {@link LogEntryQueue} and send it to
 * remote.
 */
@Singleton
public final class LogEntryUploadManager extends AbstractScheduledService {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The maximum elements to be polled from the queue in a round. */
  private static final int MAX_FLUSH_ELEMENTS = 2000;

  /** The maximum number of retries to upload logs to remote. */
  private static final int MAX_ATTEMPTS = 3;

  private final LogEntryQueue logEntryQueue;
  private final LogUploader logUploader;

  @Inject
  public LogEntryUploadManager(LogEntryQueue logEntryQueue, LogUploader logUploader) {
    this.logEntryQueue = logEntryQueue;
    this.logUploader = logUploader;
  }

  /**
   * Whether the log uploader is enabled.
   *
   * @return true if enabled.
   */
  public boolean isEnabled() {
    return logUploader.isEnabled();
  }

  @Override
  protected void startUp() throws MobileHarnessException {
    logUploader.init();
  }

  @Override
  protected void runOneIteration() {
    List<LogEntry> logEntries = logEntryQueue.poll(MAX_FLUSH_ELEMENTS);
    logger.atFine().log(
        "Attempting to write a batch of %s log(s) to cloud logging.", logEntries.size());
    if (!logEntries.isEmpty()) {
      for (int i = 0; i < MAX_ATTEMPTS; i++) {
        try {
          logUploader.uploadLogs(logEntries);
          return;
        } catch (MobileHarnessException e) {
          if (i + 1 == MAX_ATTEMPTS) {
            logger.atWarning().withCause(e).log(
                "Failed to send log entries which were generated in [%s, %s] to remote.",
                Timestamps.toMillis(logEntries.get(0).getTimestamp()),
                Timestamps.toMillis(Iterables.getLast(logEntries).getTimestamp()));
          }
        }
      }
    }
  }

  @Override
  protected void shutDown() {
    runOneIteration();
  }

  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedDelaySchedule(
        Duration.ofSeconds(1), Flags.instance().logUploadDelay.getNonNull());
  }
}
