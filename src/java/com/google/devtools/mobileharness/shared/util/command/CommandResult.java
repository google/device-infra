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

/** Result of a command. */
public class CommandResult
    extends com.google.devtools.deviceinfra.shared.util.command.CommandResult {

  /** Do NOT make it public. In unit tests, please use {@code FakeCommandResult} instead. */
  CommandResult(String stdout, String stderr, int exitCode, boolean isTimeout, boolean isStopped) {
    super(stdout, stderr, exitCode, isTimeout, isStopped);
  }

  public static CommandResult fromNewCommandResult(
      com.google.devtools.deviceinfra.shared.util.command.CommandResult newCommandResult) {
    return new CommandResult(
        newCommandResult.stdout(),
        newCommandResult.stderr(),
        newCommandResult.exitCode(),
        newCommandResult.isTimeout(),
        newCommandResult.isStopped());
  }
}
