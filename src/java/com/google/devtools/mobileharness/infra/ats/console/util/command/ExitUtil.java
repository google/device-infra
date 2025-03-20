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

package com.google.devtools.mobileharness.infra.ats.console.util.command;

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.DEBUG;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.command.RunCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.AtsSessionStub;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionCancellation;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginNotification;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.time.Duration;

/** A util class to perform exit/kill/quit operations. */
public final class ExitUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration SHORT_SLEEP_INTERVAL = Duration.ofSeconds(3L);
  private static final Duration LONG_SLEEP_INTERVAL = Duration.ofSeconds(30L);

  /** Cancels all unfinished sessions from the current client. */
  public static void cancelUnfinishedSessions(
      AtsSessionStub atsSessionStub, ConsoleUtil consoleUtil, String reason, boolean aggressive) {
    try {
      atsSessionStub.cancelUnfinishedNotAbortedSessions(
          /* fromCurrentClient= */ true,
          AtsSessionPluginNotification.newBuilder()
              .setSessionCancellation(
                  AtsSessionCancellation.newBuilder().setReason(reason).setAggressive(aggressive))
              .build());
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to cancel unfinished sessions with error. Error=[%s]",
          MoreThrowables.shortDebugString(e));
    }
  }

  public static void waitUntilNoRunningSessions(ConsoleUtil consoleUtil, Sleeper sleeper) {
    consoleUtil.printlnStdout("Will exit the console after all commands have executed.");
    try {
      int sleepCount = 0;
      int runningRunCommandCount;
      do {
        // Sessions of RunCommand are the only async sessions in ATS console.
        runningRunCommandCount = RunCommand.getRunningRunCommandCount();
        if (runningRunCommandCount > 0) {
          logger
              .atInfo()
              .with(IMPORTANCE, DEBUG)
              .atMostEvery(1, MINUTES)
              .log(
                  "Still need to wait as %s RunCommands are still running.",
                  runningRunCommandCount);
          sleeper.sleep(sleepCount < 10 ? SHORT_SLEEP_INTERVAL : LONG_SLEEP_INTERVAL);
          sleepCount++;
        }
      } while (runningRunCommandCount > 0);
    } catch (InterruptedException e) {
      consoleUtil.printlnStderr(
          "Interrupted while waiting until no running sessions. Going to exit the console"
              + " directly.");
      Thread.currentThread().interrupt();
    }
  }

  private ExitUtil() {}
}
