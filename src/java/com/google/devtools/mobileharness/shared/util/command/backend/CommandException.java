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

/** An exception indicating a failure to execute a command. */
public abstract class CommandException extends Exception {
  private final Command command;

  CommandException(Command command, String message) {
    super(checkNotNull(command, message) + ": " + message);
    this.command = command;
  }

  CommandException(Command command, Exception cause) {
    super(checkNotNull(command, cause).toString(), cause);
    this.command = command;
  }

  /** The command that failed to execute. */
  public final Command command() {
    return command;
  }
}
