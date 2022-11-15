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

/**
 * Utilities for constructing {@link CommandResult}s.
 *
 * <p><b>NOTE</b>: This class should only be used by the implementation of the command API. If you
 * need a {@link CommandResult} in your tests, please create a mocked one directly.
 */
public class CommandResults {

  public static CommandResult of(
      String stdout, String stderr, int exitCode, boolean isTimeout, boolean isStopped) {
    return new CommandResult(stdout, stderr, exitCode, isTimeout, isStopped);
  }

  public static CommandResult withoutOutput(CommandResult commandResult) {
    return new CommandResult(
        /* stdout= */ "",
        /* stderr= */ "",
        commandResult.exitCode(),
        commandResult.isTimeout(),
        commandResult.isStopped());
  }

  private CommandResults() {}
}
