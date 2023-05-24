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

package com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.initializer;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil.KillSignal;
import java.time.Duration;
import java.util.Set;

/** Helper util class for adb initialization. */
public final class AdbInitializerHelper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Kills adb server. */
  public static void killAdbServer(
      String adbPath, CommandExecutor commandExecutor, SystemUtil systemUtil) {
    boolean adbKillServerCommandFailed = false;
    try {
      Set<Integer> adbProcessIds = systemUtil.getProcessIds("adb", "fork-server");
      if (adbProcessIds.isEmpty()) {
        logger.atInfo().log("No adb processes found, skip killing adb server.");
        return;
      }
      CommandResult result =
          commandExecutor.exec(Command.of(adbPath, "kill-server").timeout(Duration.ofMinutes(4)));
      logger.atInfo().log("The result of adb kill-server: %s", result);

      Set<Integer> adbProcessIdsAfterKill = systemUtil.getProcessIds("adb", "fork-server");
      if (!adbProcessIdsAfterKill.isEmpty()) {
        adbKillServerCommandFailed = true;
      }
    } catch (MobileHarnessException e) {
      adbKillServerCommandFailed = true;
      logger.atWarning().log("%s", e.getMessage());
    } catch (InterruptedException e) {
      adbKillServerCommandFailed = true;
      Thread.currentThread().interrupt();
      logger.atWarning().withCause(e).log("Interrupted when killing adb server");
    }

    if (!adbKillServerCommandFailed) {
      return;
    }

    try {
      if (systemUtil.killAllProcesses("adb", KillSignal.SIGKILL)) {
        logger.atInfo().log("Killed adb process with killall.");
      } else {
        logger.atWarning().log(
            "Failed to kill adb process with killall command as the processes don't exist.");
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().log("%s", e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.atWarning().withCause(e).log("Interrupted when killall adb process");
    }
    try {
      String processes = systemUtil.getProcessesByKeywords("adb");
      logger.atInfo().log("All processes that has the keyword adb:\n%s", processes);
    } catch (MobileHarnessException e) {
      logger.atWarning().log("%s", e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.atWarning().withCause(e).log("Interrupted when get adb processes");
    }
  }

  /**
   * Manages adb server in the adb initialization process.
   *
   * @param adbPath adb binary path
   * @param commandExecutor command executor used to execute "adb kill-server" command
   * @param localFileUtil local file util
   * @param systemUtil system util
   * @param ifKillAdbServer whether kill adb server
   */
  public static void manageAdbServer(
      String adbPath,
      CommandExecutor commandExecutor,
      LocalFileUtil localFileUtil,
      SystemUtil systemUtil,
      boolean ifKillAdbServer) {

    boolean validAdbPath = localFileUtil.isFileExistInPath(adbPath);
    if (!validAdbPath) {
      logger.atInfo().log("Skip managing ADB server because ADB path [%s] is invalid", adbPath);
      return;
    }

    boolean managedAdbServer = false;
    try {
      if (systemUtil.getProcessIds("adb", "fork-server").isEmpty()) {
        managedAdbServer = true;
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("%s", e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.atWarning().withCause(e).log("Interrupted");
    }

    if (ifKillAdbServer) {
      // Stops the old adb server and touch the adb binary. This can help to recover some
      // offline devices.
      try {
        killAdbServer(adbPath, commandExecutor, systemUtil);
        logger.atInfo().log("Old adb server killed if any");
        // Since we killed the old server, now we will be the parent of the server.
        managedAdbServer = true;
        localFileUtil.touchFileOrDir(adbPath, false);
        logger.atInfo().log("adb binary touched");
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log("%s", e.getMessage());
      }
    }

    if (managedAdbServer && ifKillAdbServer) {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      killAdbServer(adbPath, commandExecutor, systemUtil);
                    } catch (Throwable e) {
                      logger.atInfo().withCause(e).log("Failed to stop ADB server");
                    }
                  }));
    }
  }

  private AdbInitializerHelper() {}
}
