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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Recorder for recording {@link CommandHistory}.
 *
 * @see CommandHistory
 */
public class CommandRecorder {

  public static CommandRecorder getInstance() {
    return INSTANCE;
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final CommandRecorder INSTANCE = new CommandRecorder(CommandHistory.getInstance());

  private final CommandHistory commandHistory;

  private final CopyOnWriteArrayList<CommandRecorderListener> listeners =
      new CopyOnWriteArrayList<>();

  private CommandRecorder(CommandHistory commandHistory) {
    this.commandHistory = commandHistory;
  }

  /**
   * Hooks a listener to the recorder.
   *
   * <p>Do not add unnecessary listeners to CommandRecorder. Listeners will reduce the performance.
   */
  public synchronized void addListener(CommandRecorderListener listener) {
    listeners.add(listener);
  }

  /**
   * Adds a new command.
   *
   * @return the command record
   * @see CommandHistory#addCommand(List)
   */
  public CommandRecord addCommand(List<String> command) {
    return commandHistory.addCommand(command);
  }

  /**
   * Adds result to a command.
   *
   * @see CommandHistory#addCommandResult(CommandRecord, CommandResult)
   */
  public void addCommandResult(CommandRecord commandRecord, CommandResult result) {
    commandHistory.addCommandResult(commandRecord, result);
    for (CommandRecorderListener listener : listeners) {
      try {
        listener.onAddCommandResult(commandRecord, result);
      } catch (RuntimeException e) {
        logger.atWarning().withCause(e).log(
            "Failed to execute command recorder listener %s", listener.getClass().getName());
      }
    }
  }
}
