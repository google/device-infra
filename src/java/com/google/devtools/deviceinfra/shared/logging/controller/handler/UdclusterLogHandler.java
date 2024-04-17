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

package com.google.devtools.deviceinfra.shared.logging.controller.handler;

import com.google.devtools.deviceinfra.shared.logging.controller.queue.LogEntryQueue;
import com.google.devtools.deviceinfra.shared.logging.util.LogEntryUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.logging.v2.LogEntry;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Handler of Udcluster logging, which parse the Java {@link LogRecord} to Stackdriver {@link
 * LogEntry} and send it to the local memory queue.
 */
@Singleton
public class UdclusterLogHandler extends Handler {

  private final LogEntryUtil logEntryUtil;
  private final LogEntryQueue logEntryQueue;

  @Inject
  UdclusterLogHandler(LogEntryQueue logEntryQueue, LogEntryUtil logEntryUtil) {
    this.logEntryQueue = logEntryQueue;
    this.logEntryUtil = logEntryUtil;
    if (!Flags.instance().enableStackdriverDebugMode.getNonNull()) {
      setLevel(Level.INFO);
    }
  }

  @Override
  public void publish(LogRecord logRecord) {
    if (isLoggable(logRecord)) {
      LogEntry logEntry = logEntryUtil.generateLogEntry(logRecord);
      logEntryQueue.add(logEntry);
    }
  }

  @Override
  public void flush() {
    // Does nothing.
  }

  @Override
  public void close() {
    // Does nothing.
  }
}
