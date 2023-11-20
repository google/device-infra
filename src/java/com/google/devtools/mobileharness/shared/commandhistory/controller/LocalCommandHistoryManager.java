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

package com.google.devtools.mobileharness.shared.commandhistory.controller;

import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toProtoDuration;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toProtoTimestamp;

import com.google.devtools.mobileharness.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecord;
import com.google.devtools.mobileharness.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecords;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Manager for managing command history of the current process. */
public class LocalCommandHistoryManager {

  private static final LocalCommandHistoryManager INSTANCE = new LocalCommandHistoryManager();

  public static LocalCommandHistoryManager getInstance() {
    return INSTANCE;
  }

  private final Object lock = new Object();

  @GuardedBy("lock")
  private final List<LocalCommandRecord> records = new ArrayList<>();

  @GuardedBy("lock")
  private Duration startElapsedTime = Duration.ZERO;

  @GuardedBy("lock")
  private Instant startTimestamp = Instant.EPOCH;

  private LocalCommandHistoryManager() {}

  /** Do NOT make it public. */
  void add(LocalCommandRecord record) {
    synchronized (lock) {
      records.add(record);
    }
  }

  /** Do NOT make it public. */
  void start(Duration startElapsedTime, Instant startTimestamp) {
    synchronized (lock) {
      this.startElapsedTime = startElapsedTime;
      this.startTimestamp = startTimestamp;
    }
  }

  public LocalCommandRecords getAll() {
    synchronized (lock) {
      return LocalCommandRecords.newBuilder()
          .addAllRecord(records)
          .setLocalStartElapsedTime(toProtoDuration(startElapsedTime))
          .setLocalStartTimestamp(toProtoTimestamp(startTimestamp))
          .build();
    }
  }
}
