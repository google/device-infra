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

package com.google.devtools.mobileharness.platform.android.nativebin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;
import java.util.Optional;

/**
 * Util class for running native bin on Android devices/emulators.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 *
 * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
 * separator on SDK>23. It's callers' responsibility to parse it correctly.
 */
public class NativeBinUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Adb adb;

  private final AndroidFileUtil androidFileUtil;

  public NativeBinUtil() {
    this(new Adb(), new AndroidFileUtil());
  }

  @VisibleForTesting
  NativeBinUtil(Adb adb, AndroidFileUtil androidFileUtil) {
    this.adb = adb;
    this.androidFileUtil = androidFileUtil;
  }

  /**
   * Runs native binary test.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of the device
   * @param nativeBinArgs arguments wrapper for running native binary test
   * @return command stdout output
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  @CanIgnoreReturnValue
  public CommandResult runNativeBin(String serial, NativeBinArgs nativeBinArgs)
      throws MobileHarnessException, InterruptedException {
    String runDir = nativeBinArgs.runDirectory();
    String bin = nativeBinArgs.binary();
    String runAs = nativeBinArgs.runAs().orElse("").trim();
    String runEnvironment = nativeBinArgs.runEnvironment().orElse("").trim();
    String options = nativeBinArgs.options().orElse("").trim();
    String cpuAffinity = nativeBinArgs.cpuAffinity().orElse("").trim();
    boolean echoCommandExitCode = nativeBinArgs.echoCommandExitCode().orElse(false);

    androidFileUtil.makeFileExecutable(serial, PathUtil.join(runDir, bin));

    StringBuilder cmdBuilder = new StringBuilder(String.format("cd %s && ", runDir));
    if (!runAs.isEmpty()) {
      cmdBuilder.append(String.format("su %s ", runAs));
    }
    if (!runEnvironment.isEmpty()) {
      cmdBuilder.append(String.format("%s ", runEnvironment));
    }
    if (!cpuAffinity.isEmpty()) {
      cmdBuilder.append(String.format("taskset %s ", cpuAffinity));
    }
    cmdBuilder.append(String.format("./%s", bin));

    if (!options.isEmpty()) {
      cmdBuilder.append(" ").append(options);
    }

    if (echoCommandExitCode) {
      cmdBuilder.append("; echo $?");
    }

    Command cmd =
        adb.getAdbCommand()
            .args("-s", serial, "shell", cmdBuilder.toString())
            .timeout(nativeBinArgs.commandTimeout())
            .redirectStderr(nativeBinArgs.redirectStderr().orElse(true));
    if (nativeBinArgs.stdoutLineCallback().isPresent()) {
      cmd = cmd.onStdout(nativeBinArgs.stdoutLineCallback().get());
    }
    if (nativeBinArgs.stderrLineCallback().isPresent()) {
      cmd = cmd.onStderr(nativeBinArgs.stderrLineCallback().get());
    }

    logger.atInfo().log(
        "Running native bin on device %s with command %s", serial, cmd.getCommand());
    CommandResult commandResult = null;
    try {
      commandResult = adb.run(cmd);
    } catch (MobileHarnessException e) {
      if (AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_TIMEOUT.equals(e.getErrorId())) {
        throw new MobileHarnessException(
            AndroidErrorId.NATIVE_BIN_UTIL_RUN_NATIVE_BIN_TIMEOUT, e.getMessage(), e);
      } else if (AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE.equals(e.getErrorId())) {
        throw new MobileHarnessException(
            AndroidErrorId.NATIVE_BIN_UTIL_RUN_NATIVE_BIN_FAILURE, e.getMessage(), e);
      }
      throw e;
    }

    if (commandResult == null) {
      throw new MobileHarnessException(
          AndroidErrorId.NATIVE_BIN_UTIL_RUN_NATIVE_BIN_NULL_COMMAND_RESULT_ERROR,
          "Command result should not be null.");
    }
    if (echoCommandExitCode) {
      // The above adb command execution will throw MobileHarnessException when its command exit
      // code is non-zero. Now check what is the exit code from 'echo $?' for the shell command
      // (b/137390305).
      String commandStdout = commandResult.stdoutWithoutTrailingLineTerminator();
      Optional<Integer> exitCodeFromEcho = getExitCodeFromCommandOutput(commandStdout);
      if (exitCodeFromEcho.isPresent() && exitCodeFromEcho.get() != 0) {
        throw new MobileHarnessException(
            AndroidErrorId.NATIVE_BIN_UTIL_RUN_NATIVE_BIN_NON_ZERO_EXIT_CODE_FROM_ECHO,
            String.format(
                "Exit code from adb shell command is %s but exit code from 'echo $?' for the shell"
                    + " command is %s.%nCommand output:%n%s%n",
                commandResult.exitCode(), exitCodeFromEcho.get(), commandStdout));
      }
    }

    return commandResult;
  }

  @VisibleForTesting
  static Optional<Integer> getExitCodeFromCommandOutput(String output) {
    List<String> outputLines =
        Splitters.LINE_SPLITTER.trimResults().omitEmptyStrings().splitToList(output);
    // The exit code from echo should appear at the end or at the beginning of logs
    if (outputLines.isEmpty()) {
      return Optional.empty();
    }
    Optional<Integer> exitCodeFromEnd = parseInt(Iterables.getLast(outputLines));
    if (exitCodeFromEnd.isPresent()) {
      return exitCodeFromEnd;
    }
    return parseInt(outputLines.get(0));
  }

  private static Optional<Integer> parseInt(String input) {
    try {
      return Optional.of(Integer.parseInt(input));
    } catch (NumberFormatException nfe) {
      logger.atWarning().withCause(nfe).log("Failed to parse to int: %s", input);
    }
    return Optional.empty();
  }
}
