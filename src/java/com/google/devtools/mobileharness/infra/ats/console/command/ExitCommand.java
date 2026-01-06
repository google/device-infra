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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.infra.ats.console.util.command.ExitUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
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
      description =
          """
          Whether to exit only after all commands have executed. If false, will stop running\
           commands, wait until they finish and then exit. If true, will directly wait until\
           running commands finish and then exit. Default is false.\
          """)
  private boolean waitForCommand = false;

  @Option(
      names = {"--stop-reading-input", "-s"},
      required = false,
      paramLabel = "<stop_reading_input>",
      description =
          """
          Whether to stop reading input when waiting until running commands finish. If true,\
           the console will become not responsive immediately. Default is false.\
          """)
  private boolean stopReadingInput = false;

  private final ExitUtil exitUtil;
  private final ConsoleUtil consoleUtil;

  @Inject
  ExitCommand(ExitUtil exitUtil, ConsoleUtil consoleUtil) {
    this.exitUtil = exitUtil;
    this.consoleUtil = consoleUtil;
  }

  @Override
  public Integer call() {
    if (!waitForCommand) {
      exitUtil.cancelUnfinishedSessions("User triggered Exit Command.", /* aggressive= */ false);
    }

    // Wait until no running sessions.
    ListenableFuture<?> noRunningSessionsFuture =
        exitUtil.waitUntilNoRunningSessions(/* interruptLineReader= */ true);

    // Stops reading input if necessary.
    if (stopReadingInput) {
      consoleUtil.printlnStdout("Stop reading input");
      Futures.getUnchecked(noRunningSessionsFuture);
    }

    return ExitCode.OK;
  }
}
