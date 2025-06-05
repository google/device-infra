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

package com.google.devtools.mobileharness.infra.ats.console.command;

import com.google.devtools.mobileharness.infra.ats.console.util.command.ExitUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.console.InterruptibleLineReader;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

/** Command to exit the ATS console. */
@Command(
    name = "exit",
    aliases = {"quit", "q"},
    description = "Exit the console.")
final class ExitCommand implements Callable<Integer> {

  @Option(
      names = {"--wait-for-command", "-c"},
      required = false,
      paramLabel = "<wait_for_command>",
      description = "Whether to exit only after all commands have executed.")
  private boolean waitForCommand = false;

  private final ConsoleUtil consoleUtil;
  private final InterruptibleLineReader interruptibleLineReader;
  private final ExitUtil exitUtil;

  @Inject
  ExitCommand(
      ConsoleUtil consoleUtil, InterruptibleLineReader interruptibleLineReader, ExitUtil exitUtil) {
    this.consoleUtil = consoleUtil;
    this.interruptibleLineReader = interruptibleLineReader;
    this.exitUtil = exitUtil;
  }

  @Override
  public Integer call() {
    try {
      if (!waitForCommand) {
        exitUtil.cancelUnfinishedSessions("User triggered Exit Command.", /* aggressive= */ false);
      }
      // Wait until no running sessions.
      exitUtil.waitUntilNoRunningSessions();
    } finally {
      consoleUtil.printlnStdout("Exiting...");
      // Makes the console exit, and the shutdown hook will kill the OLC server.
      interruptibleLineReader.interrupt();
    }

    return ExitCode.OK;
  }
}
