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

import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.AtsSessionStub;
import com.google.devtools.mobileharness.infra.ats.console.util.command.ExitUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.console.InterruptibleLineReader;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
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
  private final AtsSessionStub atsSessionStub;
  private final Sleeper sleeper;
  private final InterruptibleLineReader interruptibleLineReader;

  @Inject
  ExitCommand(
      ConsoleUtil consoleUtil,
      AtsSessionStub atsSessionStub,
      Sleeper sleeper,
      InterruptibleLineReader interruptibleLineReader) {
    this.consoleUtil = consoleUtil;
    this.atsSessionStub = atsSessionStub;
    this.sleeper = sleeper;
    this.interruptibleLineReader = interruptibleLineReader;
  }

  @Override
  public Integer call() {
    try {
      if (!waitForCommand) {
        ExitUtil.cancelUnfinishedSessions(
            atsSessionStub, consoleUtil, "User triggered Exit Command.", /* aggressive= */ false);
      }
      // Wait until no running sessions.
      ExitUtil.waitUntilNoRunningSessions(consoleUtil, sleeper);
    } finally {
      consoleUtil.printlnStdout("Exiting...");
      // Makes the console exit, and the shutdown hook will kill the OLC server.
      interruptibleLineReader.interrupt();
    }

    return ExitCode.OK;
  }
}
