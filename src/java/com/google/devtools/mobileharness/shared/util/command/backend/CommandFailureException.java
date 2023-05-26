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

package com.google.devtools.mobileharness.shared.util.command.backend;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;

/** An exception thrown when a command result fails the command's success condition. */
public final class CommandFailureException extends CommandException {
  private final CommandResult result;

  CommandFailureException(Command command, CommandResult result) {
    super(command, checkNotNull(result, command).toString());
    if (command().successCondition().test(result)) {
      throw new AssertionError(
          Strings.lenientFormat(
              "%s incorrectly passed a result which passes the command's success condition.\n"
                  + "Command: %s\nResult: %s",
              getClass().getName(), command(), result));
    }
    this.result = result;
  }

  public CommandResult result() {
    return result;
  }
}
