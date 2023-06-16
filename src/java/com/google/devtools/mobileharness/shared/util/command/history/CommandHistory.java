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

package com.google.devtools.mobileharness.shared.util.command.history;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.CommandResults;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.concurrent.GuardedBy;

/**
 * History of {@linkplain com.google.devtools.mobileharness.shared.util.command.CommandExecutor
 * executing} {@linkplain com.google.devtools.mobileharness.shared.util.command.Command commands}.
 */
public class CommandHistory {

  public static CommandHistory getInstance() {
    return INSTANCE;
  }

  private static final int DEFAULT_CAPACITY = 100_000;

  private static final CommandHistory INSTANCE = new CommandHistory(DEFAULT_CAPACITY);

  private final Lock recordLock = new ReentrantLock();

  @GuardedBy("recordLock")
  private final Map<String, CommandRecord> records;

  @VisibleForTesting
  CommandHistory(int capacity) {
    this.records = new RecordCache(capacity);
  }

  /** Returns all commands which are still saved. */
  public List<CommandRecord> getAllCommands() {
    recordLock.lock();
    try {
      return ImmutableList.copyOf(records.values());
    } finally {
      recordLock.unlock();
    }
  }

  /** Returns all commands which are still saved and meet the requirements of the filter. */
  public List<CommandRecord> searchCommands(Predicate<CommandRecord> commandRecordFilter) {
    return getAllCommands().stream().filter(commandRecordFilter).collect(Collectors.toList());
  }

  /**
   * Adds a new command.
   *
   * @return the command record
   */
  @CanIgnoreReturnValue
  CommandRecord addCommand(List<String> command) {
    CommandRecord record = CommandRecord.create(command);
    recordLock.lock();
    try {
      records.put(record.id(), record);
    } finally {
      recordLock.unlock();
    }
    return record;
  }

  /** Adds result to a command. */
  void addCommandResult(CommandRecord commandRecord, CommandResult result) {
    recordLock.lock();
    try {
      records.computeIfPresent(
          commandRecord.id(),
          (recordId, record) -> record.withResult(CommandResults.withoutOutput(result)));
    } finally {
      recordLock.unlock();
    }
  }

  private static class RecordCache extends LinkedHashMap<String, CommandRecord> {

    private final int capacity;

    private RecordCache(int capacity) {
      this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Entry<String, CommandRecord> eldest) {
      return size() > capacity;
    }
  }
}
