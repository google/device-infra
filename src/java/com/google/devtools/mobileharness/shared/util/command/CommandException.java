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

import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import javax.annotation.Nullable;

/**
 * Checked exception thrown when fails to execute a command.
 *
 * @see Command
 * @see CommandStartException
 * @see CommandFailureException
 * @see CommandTimeoutException
 */
public class CommandException extends MobileHarnessException {

  private final Command command;

  CommandException(
      ErrorId errorId, String errorMessage, @Nullable Throwable cause, Command command) {
    super(errorId, errorMessage + ", command=[" + command + "]", cause);
    this.command = command;
  }

  public Command command() {
    return command;
  }
}
