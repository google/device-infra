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

package com.google.wireless.qa.mobileharness.shared.android;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.Timeout;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.util.ArrayUtil;
import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import javax.annotation.Nullable;

/** Util methods for using perfetto tool. */
public class Perfetto {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Path of the Perfetto command line tool. */
  @Nullable private final String perfettoPath;

  /** System command executor. */
  private final CommandExecutor cmdExecutor;

  /** Lazy initializer for deploying the perfetto tool. */
  private static class LazyInitializer {
    @Nullable private static final String FULL_PERFETTO_PATH;

    static {
      String perfettoPath;
      LocalFileUtil fileUtil = new LocalFileUtil();
      SystemUtil systemUtil = new SystemUtil();

      String perfettoPathFromFlag = Flags.instance().perfettoScriptPath.get();

      if (!isNullOrEmpty(perfettoPathFromFlag)) {
        // Use the Perfetto specified by the flag.
        perfettoPath = perfettoPathFromFlag;
        try {
          fileUtil.checkFile(perfettoPath);
        } catch (MobileHarnessException e) {
          logger.atSevere().withCause(e).log(
              "Perfetto script does not exist at %s.", perfettoPathFromFlag);
        }
      } else {
        perfettoPath = getBuiltinPerfettoPathOss();
      }

      if (perfettoPath != null) {
        logger.atInfo().log("Found perfetto at: %s", perfettoPath);
      } else {
        logger.atSevere().log("Perfetto not found.");
      }
      FULL_PERFETTO_PATH = perfettoPath;
    }
  }

  /** Perfetto default constructor. The hardcoded path are used. */
  public Perfetto() {
    this(LazyInitializer.FULL_PERFETTO_PATH, new CommandExecutor());
  }

  @VisibleForTesting
  Perfetto(String perfettoPath, CommandExecutor cmdExecutor) {
    this.perfettoPath = perfettoPath;
    this.cmdExecutor = Preconditions.checkNotNull(cmdExecutor);
  }

  /** Returns array of complete command line to launch perfetto. */
  String[] getPerfettoCommandArgs(
      String outputPath, String serial, Optional<String> tags, Optional<String> packageList)
      throws MobileHarnessException {
    if (this.perfettoPath == null) {
      throw new MobileHarnessException(
          AndroidErrorId.ADNROID_PERFETTO_SCRIPT_NOT_FOUND, "Perfetto not found.");
    }

    String[] commandArgs =
        ArrayUtil.join(
            new String[] {this.perfettoPath, "-s", serial, "-o", outputPath, "--no-open"},
            tags.orElse("").split("\\s+"));

    if (packageList.isPresent()) {
      return ArrayUtil.join(commandArgs, new String[] {"-a", packageList.get()});
    }

    return commandArgs;
  }

  /**
   * Returns array of complete command line to launch perfetto, with additional running time input.
   */
  String[] getPerfettoCommandArgs(
      String outputPath,
      String serial,
      Optional<String> tags,
      Duration time,
      Optional<String> packageList)
      throws MobileHarnessException {
    String[] commandArgs = getPerfettoCommandArgs(outputPath, serial, tags, packageList);

    return !time.isNegative() && !time.isZero()
        ? ArrayUtil.join(commandArgs, new String[] {"-t", time.toSeconds() + "s"})
        : commandArgs;
  }

  /**
   * Returns array of complete command line to launch perfetto, with additional buffer size input.
   */
  String[] getPerfettoCommandArgs(
      String outputPath,
      String serial,
      Optional<String> tags,
      Duration time,
      int bufferSizeKb,
      Optional<String> packageList)
      throws MobileHarnessException {
    String[] commandArgs = getPerfettoCommandArgs(outputPath, serial, tags, time, packageList);

    String[] ret =
        (bufferSizeKb > 0)
            ? ArrayUtil.join(
                commandArgs,
                // Round-up the buffer size to an integer in MB.
                new String[] {"-b", ((bufferSizeKb + 1023) / 1024) + "mb"})
            : commandArgs;
    return ret;
  }

  /**
   * Returns array of complete command line to launch perfetto, with additional config file input.
   */
  String[] getPerfettoCommandArgs(
      String outputPath,
      String serial,
      Optional<String> configFile,
      Optional<String> tags,
      Duration time,
      int bufferSizeKb,
      Optional<String> packageList)
      throws MobileHarnessException {
    if (configFile.isPresent()) {
      String[] commandArgs =
          getPerfettoCommandArgs(outputPath, serial, Optional.empty(), packageList);
      return ArrayUtil.join(commandArgs, new String[] {"-c", configFile.get()});
    } else {
      return getPerfettoCommandArgs(outputPath, serial, tags, time, bufferSizeKb, packageList);
    }
  }

  /**
   * Runs perfetto command asynchronously. This method will start perfetto and return directly.
   * exitCallback will be called when there's a available perfetto result.
   *
   * @param args all the args this function needs
   * @return CommandProcess of the perfetto process
   */
  public CommandProcess runAsyncPerfetto(RunPerfettoArgs args) throws MobileHarnessException {
    checkPerfetto();
    String pathEnv = getEnvPathWithMhAdb(args.adbPath());
    final String[] command =
        getPerfettoCommandArgs(
            args.outputPath(),
            args.serial(),
            args.configFile(),
            args.tags(),
            Duration.ZERO,
            args.bufferSizeKb(),
            args.packageList());
    logger.atInfo().log("runAsyncPerfetto command: %s", Arrays.toString(command));
    logger.atInfo().log("runAsyncPerfetto PATH: %s", pathEnv);
    CommandProcess process =
        cmdExecutor.start(
            Command.of(command)
                .timeout(Timeout.fixed(args.traceTimeout()))
                .extraEnv(ImmutableMap.of("PATH", pathEnv))
                .onExit(args.exitCallback().get())
                .onTimeout(args.timeoutCallback())
                .onStdout(args.outputCallback()));
    logger.atInfo().log("Started (future) perfetto: %s", Arrays.toString(command));
    return process;
  }

  /**
   * Runs perfetto command synchronously. This method will start perfetto and wait until perfetto
   * ends. You need to guarantee "-t" is used in tags to limit perfetto running time.
   *
   * @param args all the args we need to run sync perfetto
   * @return the console output
   */
  public String runSyncPerfetto(RunPerfettoArgs args)
      throws MobileHarnessException, InterruptedException {
    checkPerfetto();
    String pathEnv = getEnvPathWithMhAdb(args.adbPath());
    final String[] command =
        getPerfettoCommandArgs(
            args.outputPath(),
            args.serial(),
            args.configFile(),
            args.tags(),
            args.time(),
            args.bufferSizeKb(),
            args.packageList());
    logger.atInfo().log("runSyncPerfetto command: %s", Arrays.toString(command));
    logger.atInfo().log("runSyncPerfetto PATH: %s", pathEnv);
    return cmdExecutor
        .exec(
            Command.of(command)
                .timeout(Timeout.fixed(args.traceTimeout()))
                .extraEnv(ImmutableMap.of("PATH", pathEnv))
                .onTimeout(args.timeoutCallback())
                .onStdout(args.outputCallback()))
        .toString();
  }

  /**
   * Find Adb path. perfetto cannot find `adb` on its own, and instead relies on $PATH. Help it to
   * find the correct adb by altering the $PATH that it sees. It should always use the adb that MH
   * is (already) using to run the instrumentation.
   *
   * @return the adb path with prepend from the adb parent dir to existing system env PATH
   */
  private static String getEnvPathWithMhAdb(Optional<String> adbPath) {
    String pathEnv = System.getenv().get("PATH");
    if (adbPath.isEmpty()) {
      return pathEnv;
    }
    File adbFile = new File(adbPath.get());
    return adbFile.getParentFile().getAbsolutePath() + File.pathSeparator + pathEnv;
  }

  private void checkPerfetto() throws MobileHarnessException {
    MobileHarnessExceptions.checkNotNull(
        this.perfettoPath,
        AndroidErrorId.ANDROID_PERFETTO_DECORATOR_SCRIPT_PATH_IS_NULL,
        "getPerfettoPath was null");
  }

  @Nullable
  private static String getBuiltinPerfettoPathOss() {
    logger.atSevere().log("Should specify perfetto script path using --perfetto_script_path.");
    return null;
  }
}
