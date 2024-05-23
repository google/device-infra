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

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.DEBUG;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.AtsSessionStub;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.time.Duration;
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
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Option(
      names = {"--wait-for-command", "-c"},
      required = false,
      paramLabel = "<wait_for_command>",
      description = "Whether to exit only after all commands have executed.")
  private boolean waitForCommand = false;

  private final ConsoleInfo consoleInfo;
  private final ConsoleUtil consoleUtil;
  private final AtsSessionStub atsSessionStub;
  private final Sleeper sleeper;

  @Inject
  ExitCommand(
      ConsoleInfo consoleInfo,
      ConsoleUtil consoleUtil,
      AtsSessionStub atsSessionStub,
      Sleeper sleeper) {
    this.consoleInfo = consoleInfo;
    this.consoleUtil = consoleUtil;
    this.atsSessionStub = atsSessionStub;
    this.sleeper = sleeper;
  }

  @Override
  public Integer call() {
    try {
      if (waitForCommand) {
        waitUntilNoRunningSessions();
      }
    } finally {
      // Exits the console directly. Its shutdown hook will kill olc server.
      consoleInfo.setShouldExitConsole(true);
    }

    return ExitCode.OK;
  }

  private void waitUntilNoRunningSessions() {
    consoleUtil.printlnStdout("Will exit the console after all commands have executed.");
    try {
      ImmutableList<String> unfinishedNotAbortedSessions;
      do {
        unfinishedNotAbortedSessions =
            atsSessionStub.getAllUnfinishedNotAbortedSessions(/* fromCurrentClient= */ true);

        if (!unfinishedNotAbortedSessions.isEmpty()) {
          logger
              .atInfo()
              .with(IMPORTANCE, DEBUG)
              .atMostEvery(1, MINUTES)
              .log(
                  "Still need to wait as sessions - %s are still running.",
                  unfinishedNotAbortedSessions);
          sleeper.sleep(Duration.ofSeconds(30));
        }
      } while (!unfinishedNotAbortedSessions.isEmpty());
    } catch (MobileHarnessException e) {
      consoleUtil.printlnStderr(
          String.format(
              "Failed to wait until no running sessions with error. Going to exit the"
                  + " console directly. Error=[%s]",
              MoreThrowables.shortDebugString(e)));
    } catch (InterruptedException e) {
      consoleUtil.printlnStderr(
          "Interrupted while waiting until no running sessions. Going to exit the console"
              + " directly.");
      Thread.currentThread().interrupt();
    }
  }
}
