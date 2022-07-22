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

package com.google.devtools.deviceinfra.shared.util.command;

import com.google.devtools.deviceinfra.api.error.id.DeviceInfraErrorId;
import javax.annotation.Nullable;

/**
 * Checked exception thrown when a command completes but some error has occurred during execution.
 *
 * @see CommandFailureException
 * @see CommandTimeoutException
 */
public class CommandExecutionException extends CommandException {

  private final CommandResult commandResult;

  CommandExecutionException(
      DeviceInfraErrorId errorId,
      String errorMessage,
      @Nullable Throwable cause,
      Command command,
      CommandResult commandResult) {
    super(
        errorId,
        String.format("%s, result=[%s]", errorMessage, formatCommandResult(commandResult, command)),
        cause,
        command);
    this.commandResult = commandResult;
  }

  /** The result of the command. */
  public CommandResult result() {
    return commandResult;
  }

  private static String formatCommandResult(CommandResult commandResult, Command command) {
    return command.getShowFullResultInException()
        ? commandResult.toStringWithoutTruncation()
        : commandResult.toString();
  }
}
