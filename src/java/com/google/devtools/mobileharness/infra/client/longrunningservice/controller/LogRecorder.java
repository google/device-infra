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

package com.google.devtools.mobileharness.infra.client.longrunningservice.controller;

import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto;

/** Recorder for recording logs from non-logger sources (e.g., from subprocesses). */
public class LogRecorder {

  private static final LogRecorder INSTANCE = new LogRecorder();

  public static LogRecorder getInstance() {
    return INSTANCE;
  }

  private volatile LogManager<?> logManager;

  public void initialize(LogManager<?> logManager) {
    this.logManager = logManager;
  }

  /** Adds a log record proto. */
  public void addLogRecord(LogProto.LogRecord logRecord) {
    LogManager<?> logManager = this.logManager;
    if (logManager != null) {
      logManager.addExternalLogRecord(logRecord);
    }
  }

  private LogRecorder() {}
}
