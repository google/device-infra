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

package com.google.devtools.mobileharness.shared.util.command;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;

/**
 * Checked exception thrown when a command completes but its exit code is not in it
 * success-exit-codes ({@code 0} by default or specified by {@link Command#successExitCodes(int,
 * int...)}).
 *
 * @see Command
 * @see Command#successExitCodes(int, int...)
 */
public class CommandFailureException extends CommandExecutionException {

  CommandFailureException(
      com.google.devtools.mobileharness.shared.util.command.backend.CommandFailureException
          backendFailureException,
      Command command,
      CommandResult commandResult) {
    super(
        BasicErrorId.COMMAND_EXEC_FAIL,
        "Failed command with exit_code="
            + commandResult.exitCode()
            + " and success_exit_codes="
            + command.getSuccessExitCodes(),
        backendFailureException,
        command,
        commandResult);
  }
}
