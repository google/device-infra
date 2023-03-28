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

import static java.lang.Math.min;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Execution record of a {@link com.google.devtools.mobileharness.shared.util.command.Command}. */
@AutoValue
public abstract class CommandRecord {

  static CommandRecord create(List<String> command) {
    StackTraceElement[] stackTrace = new Throwable().getStackTrace();
    int startPosition = min(2, stackTrace.length);
    int endPosition = min(startPosition + 3, stackTrace.length);
    return new AutoValue_CommandRecord(
        UUID.randomUUID().toString(),
        ImmutableList.copyOf(command),
        ImmutableList.copyOf(Arrays.asList(stackTrace).subList(startPosition, endPosition)),
        Thread.currentThread().getName(),
        Instant.now(),
        Optional.empty(),
        Optional.empty());
  }

  CommandRecord withResult(CommandResult result) {
    return new AutoValue_CommandRecord(
        id(),
        command(),
        stackTrace(),
        threadName(),
        startTime(),
        Optional.of(result),
        Optional.of(Instant.now()));
  }

  /** Returns the record id */
  public abstract String id();

  public abstract ImmutableList<String> command();

  /** Returns the stack trace for executing the command */
  public abstract ImmutableList<StackTraceElement> stackTrace();

  /** Returns the name of the thread for executing the command */
  public abstract String threadName();

  public abstract Instant startTime();

  public abstract Optional<CommandResult> result();

  public abstract Optional<Instant> endTime();
}
