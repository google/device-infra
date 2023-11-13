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

package com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Annotations.AdbCommandExecutorSupplier;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Annotations.AdbParamSupplier;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.initializer.AdbInitializer;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.proto.Adb.AdbParam;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.command.CommandTimeoutException;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.command.Timeout;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Executor for invoking ADB command line tools from Android SDK.
 *
 * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
 * separator on SDK>23. It's callers' responsibility to parse it correctly.
 */
public class Adb {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Default timeout of running commands. */
  private static final Duration DEFAULT_ADB_COMMAND_TIMEOUT = Constants.DEFAULT_ADB_COMMAND_TIMEOUT;

  /** Default success exit code for adb command. */
  @VisibleForTesting static final int DEFAULT_ADB_SUCCESS_EXIT_CODE = 0;

  /** Default max attempt times if requiring retry. */
  @VisibleForTesting
  static final int DEFAULT_RETRY_ATTEMPTS = Flags.instance().adbCommandRetryAttempts.getNonNull();

  @VisibleForTesting
  static final Duration RETRY_INTERVAL = Flags.instance().adbCommandRetryInterval.getNonNull();

  @VisibleForTesting final Sleeper sleeper = Sleeper.defaultSleeper();

  private final Supplier<AdbParam> adbParamSupplier;

  private final Supplier<CommandExecutor> commandExecutorSupplier;

  /**
   * The default {@link LineCallback} to use in {@link Adb}'s various run() methods, when one is not
   * provided.
   */
  private volatile OutputCallbackImpl defaultOutputCallback;

  public Adb() {
    this(
        Suppliers.memoize(() -> new AdbInitializer().initializeAdbEnvironment()),
        new CommandExecutor());
  }

  private Adb(Supplier<AdbParam> adbParamSupplier, CommandExecutor commandExecutor) {
    this(
        adbParamSupplier,
        Suppliers.memoize(
            () -> {
              commandExecutor.setBaseEnvironment(adbParamSupplier.get().getCmdBaseEnvVarsMap());
              return commandExecutor;
            }));
  }

  @VisibleForTesting
  Adb(
      @AdbParamSupplier Supplier<AdbParam> adbParamSupplier,
      @AdbCommandExecutorSupplier Supplier<CommandExecutor> commandExecutorSupplier) {
    this.adbParamSupplier = adbParamSupplier;
    this.commandExecutorSupplier = commandExecutorSupplier;
  }

  public String getAdbPath() {
    return adbParamSupplier.get().getAdbPath();
  }

  public String getAdbKeyPath() {
    return adbParamSupplier.get().getAdbKeyPath();
  }

  public int getAdbServerPort() {
    return adbParamSupplier.get().getAdbServerPort();
  }

  public String getAdbServerHost() {
    return adbParamSupplier.get().getAdbServerHost();
  }

  public Command getAdbCommand() {
    return Command.of(getAdbPath())
        .timeout(DEFAULT_ADB_COMMAND_TIMEOUT)
        .redirectStderr(Flags.instance().defaultAdbCommandRedirectStderr.getNonNull())
        .successExitCodes(ImmutableSet.of(DEFAULT_ADB_SUCCESS_EXIT_CODE));
  }

  /** StockAdbPath is the native Adb binary path from {@code AdbParam}. */
  public String getStockAdbPath() {
    return adbParamSupplier.get().getStockAdbPath();
  }

  /**
   * Enables adb command output to be logged to the class logger. Note that this is done via a
   * {@link LineCallback}, so if a callback is passed explicitly into a run() or runWithRetry()
   * method, this logging will not take effect
   *
   * <p>WARNING: This will log ALL command output for this instance of Adb. Take caution to make
   * sure this won't unintentionally spam your log.
   */
  public void enableCommandOutputLogging() {
    defaultOutputCallback = new OutputCallbackImpl();
  }

  /**
   * Runs ADB command with default timeout.
   *
   * @param args adb command line arguments
   * @return command adb command std output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String run(String[] args) throws MobileHarnessException, InterruptedException {
    return run(args, /* timeout= */ null, /* lineCallback= */ null);
  }

  /**
   * Runs ADB command with a given timeout.
   *
   * @param args adb command line arguments
   * @param timeout time to wait for the command to complete, or null for default timeout
   * @return command adb command std output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String run(String[] args, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return run(args, timeout, /* lineCallback= */ null);
  }

  /**
   * Runs ADB command with a given timeout and {@link LineCallback}.
   *
   * @param args adb command line arguments
   * @param timeout time to wait for the command to complete, or null for default timeout
   * @param lineCallback callback for each line of std/err output
   * @return command adb command std output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String run(String[] args, @Nullable Duration timeout, @Nullable LineCallback lineCallback)
      throws MobileHarnessException, InterruptedException {
    Command command = getAdbCommand().args(args);
    if (timeout != null) {
      command = command.timeout(timeout);
    }

    if (lineCallback != null) {
      command = command.onStdout(lineCallback);
    }

    return syncCommand(command).stdoutWithoutTrailingLineTerminator();
  }

  /**
   * Runs ADB command line tools against a specific device.
   *
   * @param serial device serial number
   * @param args ADB command line arguments
   * @return command std output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String run(String serial, String[] args)
      throws MobileHarnessException, InterruptedException {
    return run(serial, args, /* timeout= */ null, /* lineCallback= */ null);
  }

  /**
   * Runs ADB command line tools against a specific device within the specified time.
   *
   * @param serial device serial number
   * @param args ADB command line arguments
   * @param timeout max execution time
   * @return command std output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String run(String serial, String[] args, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return run(serial, args, timeout, /* lineCallback= */ null);
  }

  /**
   * Runs ADB command line tools against a specific device within the specified time.
   *
   * @param serial device serial number
   * @param args ADB command line arguments
   * @param timeout max execution time
   * @param lineCallback callback for each line of std/err output
   * @return command std output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String run(
      String serial, String[] args, @Nullable Duration timeout, @Nullable LineCallback lineCallback)
      throws MobileHarnessException, InterruptedException {
    return run(ArrayUtils.addAll(new String[] {"-s", serial}, args), timeout, lineCallback);
  }

  /**
   * Run {@link Command} with executable as {@link #getAdbPath()}. This method is for advance usage
   * of Adb.
   *
   * @param command the {@link Command} to be executed
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public CommandResult run(Command command) throws MobileHarnessException, InterruptedException {
    // Override the executable to default adb.
    command = command.executable(getAdbPath());
    return syncCommand(command);
  }

  /**
   * Runs ADB command with retry.
   *
   * @param args adb command line arguments
   * @return command std output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String runWithRetry(String[] args) throws MobileHarnessException, InterruptedException {
    return runWithRetry(args, /* timeout= */ null);
  }

  /**
   * Runs ADB command with retry.
   *
   * @param args adb command line arguments
   * @param timeout max execution time for each attempt
   * @return command std output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String runWithRetry(String[] args, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    MobileHarnessException error = null;
    for (int i = 0; i < DEFAULT_RETRY_ATTEMPTS; i++) {
      try {
        String output = run(args, timeout);
        if (error != null) {
          logger.atWarning().log(
              "%s",
              String.format(
                  "adb command succeed after retry %s times, last error:%n%s",
                  i, error.getMessage()));
        }
        return output;
      } catch (MobileHarnessException e) {
        error = e;
      }
      sleeper.sleep(RETRY_INTERVAL);
    }
    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_ADB_CMD_RETRY_ERROR,
        String.format(
            "Abort adb command after attempting %d times:%n%s",
            DEFAULT_RETRY_ATTEMPTS, error.getMessage()),
        error);
  }

  /**
   * Runs ADB command with retry against a specific device.
   *
   * @param serial device serial number
   * @param args adb command line arguments
   * @return command std output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String runWithRetry(String serial, String[] args)
      throws MobileHarnessException, InterruptedException {
    return runWithRetry(serial, args, /* timeout= */ null, /* lineCallback= */ null);
  }

  /**
   * Runs ADB command with retry against a specific device.
   *
   * @param serial device serial number
   * @param args adb command line arguments
   * @param timeout max execution time for each attempt
   * @return command std output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String runWithRetry(String serial, String[] args, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return runWithRetry(serial, args, timeout, /* lineCallback= */ null);
  }

  /**
   * Runs ADB command with retry against a specific device.
   *
   * @param serial device serial number
   * @param args adb command line arguments
   * @param timeout max execution time for each attempt
   * @param lineCallback callback for each line of std/err output
   * @return command std output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public String runWithRetry(
      String serial, String[] args, @Nullable Duration timeout, @Nullable LineCallback lineCallback)
      throws MobileHarnessException, InterruptedException {
    MobileHarnessException error = null;
    for (int i = 0; i < DEFAULT_RETRY_ATTEMPTS; i++) {
      try {
        String output = run(serial, args, timeout, lineCallback);
        if (error != null) {
          logger.atWarning().log(
              "%s",
              String.format(
                  "adb command succeed after retry %s times, last error:%n%s",
                  i, error.getMessage()));
        }
        return output;
      } catch (MobileHarnessException e) {
        error = e;
      }
      sleeper.sleep(RETRY_INTERVAL);
    }
    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_ADB_CMD_RETRY_ERROR,
        String.format(
            "Abort adb command after attempting %d times:%n%s",
            DEFAULT_RETRY_ATTEMPTS, error.getMessage()),
        error);
  }

  /**
   * Runs an adb shell command. The method will return empty string if you pass in a null or empty
   * command.
   *
   * @param serial device serial number
   * @param command the shell command
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String runShell(String serial, String command)
      throws MobileHarnessException, InterruptedException {
    return runShell(serial, command, /* timeout= */ null, /* lineCallback= */ null);
  }

  /**
   * Runs an adb shell command. The method will return empty string if you pass in a null or empty
   * command.
   *
   * @param serial device serial number
   * @param command the shell command
   * @param timeout max execution time
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String runShell(String serial, String command, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return runShell(serial, command, timeout, /* lineCallback= */ null);
  }

  /**
   * Runs an adb shell command. The method will return empty string if you pass in a null or empty
   * command.
   *
   * @param serial device serial number
   * @param command the shell command
   * @param timeout max execution time
   * @param lineCallback callback for each line of std/err output
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  @CanIgnoreReturnValue
  public String runShell(
      String serial,
      String command,
      @Nullable Duration timeout,
      @Nullable LineCallback lineCallback)
      throws MobileHarnessException, InterruptedException {
    if (StrUtil.isEmptyOrWhitespace(command)) {
      return "";
    }

    return runShell(serial, new String[] {command}, timeout, lineCallback);
  }

  /**
   * Runs an adb shell command. ADB shell remote exit code can be disabled thru this method as
   * historical behavior (Android API < 23).
   *
   * @param serial device serial number
   * @param command the shell command
   * @param disableRemoteExitCode disable shell remote exit codes and always return 0
   * @param timeout max execution time
   * @param lineCallback callback for each line of std/err output
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String runShell(
      String serial,
      String command,
      boolean disableRemoteExitCode,
      @Nullable Duration timeout,
      @Nullable LineCallback lineCallback)
      throws MobileHarnessException, InterruptedException {
    if (StrUtil.isEmptyOrWhitespace(command)) {
      return "";
    }

    return runShell(
        serial,
        disableRemoteExitCode ? new String[] {"-x", command} : new String[] {command},
        timeout,
        lineCallback);
  }

  /**
   * Runs an adb shell command. The method will return empty string if you pass in a null or empty
   * command.
   *
   * @param serial device serial number
   * @param command the shell command as a String array
   * @param timeout max execution time
   * @param lineCallback callback for each line of std/err output
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String runShell(
      String serial,
      String[] command,
      @Nullable Duration timeout,
      @Nullable LineCallback lineCallback)
      throws MobileHarnessException, InterruptedException {
    if (command == null || command.length == 0) {
      return "";
    }
    return run(serial, ArrayUtils.addAll(new String[] {"shell"}, command), timeout, lineCallback);
  }

  /**
   * Runs an adb shell command asynchronously.
   *
   * @param serial device serial number
   * @param command the shell command
   * @param timeout the shell command timeout
   * @return a {@link CommandProcess} representing pending completion of the command
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public CommandProcess runShellAsync(String serial, String command, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return runShellAsync(serial, new String[] {command}, timeout);
  }

  /**
   * Runs an adb shell command asynchronously.
   *
   * @param serial device serial number
   * @param command the shell command
   * @param timeout the shell command timeout
   * @return a {@link CommandProcess} representing pending completion of the command
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public CommandProcess runShellAsync(String serial, String[] command, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    ImmutableSet<Integer> successExitCodes = ImmutableSet.of(DEFAULT_ADB_SUCCESS_EXIT_CODE);
    String commandStr = String.join(" ", command);

    Consumer<CommandResult> exitCallback =
        (commandResult) -> {
          int exitCode = commandResult.exitCode();
          if (successExitCodes.contains(exitCode)) {
            logger.atInfo().log(
                "Background command [%s] on device %s finished.", commandStr, serial);
          } else if (exitCode == 143) {
            // The command is killed.
            logger.atInfo().log("Background command [%s] on device %s killed.", commandStr, serial);
          } else {
            logger.atWarning().log("Error from background command: %s", commandResult);
          }
        };

    Runnable timeoutCallback =
        () ->
            logger.atWarning().log(
                "Background command [%s] on device %s timeout.", commandStr, serial);

    OutputCallbackImpl outputCallback =
        new OutputCallbackImpl(String.format("Command [%s] output:", commandStr));

    Command newCommand =
        getAdbCommand()
            .args(ArrayUtils.addAll(new String[] {"-s", serial, "shell"}, command))
            .timeout(timeout)
            .onTimeout(timeoutCallback)
            .onStdout(outputCallback)
            .onExit(exitCallback);

    return asyncCommand(newCommand);
  }

  /**
   * Runs an adb shell command with retry. The method will return empty string if you pass in a null
   * or empty command.
   *
   * @param serial device serial number
   * @param command the shell command
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String runShellWithRetry(String serial, String command)
      throws MobileHarnessException, InterruptedException {
    return runShellWithRetry(serial, command, /* timeout= */ null, /* lineCallback= */ null);
  }

  /**
   * Runs an adb shell command with retry. The method will return empty string if you pass in a null
   * or empty command.
   *
   * @param serial device serial number
   * @param command the shell command
   * @param timeout max execution time for each attempt
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String runShellWithRetry(String serial, String command, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return runShellWithRetry(serial, command, timeout, /* lineCallback= */ null);
  }

  /**
   * Runs an adb shell command with retry. The method will return empty string if you pass in a null
   * or empty command.
   *
   * @param serial device serial number
   * @param command the shell command
   * @param timeout max execution time for each attempt
   * @param lineCallback callback for each line of std/err output
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String runShellWithRetry(
      String serial,
      String command,
      @Nullable Duration timeout,
      @Nullable LineCallback lineCallback)
      throws MobileHarnessException, InterruptedException {
    return runShellWithRetry(serial, new String[] {command}, timeout, lineCallback);
  }

  /**
   * Runs an adb shell command with retry. The method will return empty string if you pass in a null
   * or empty command.
   *
   * @param serial device serial number
   * @param command the shell command as a String array
   * @param timeout max execution time in milliseconds of each attempt
   * @param lineCallback callback for each line of std/err output
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String runShellWithRetry(
      String serial,
      String[] command,
      @Nullable Duration timeout,
      @Nullable LineCallback lineCallback)
      throws MobileHarnessException, InterruptedException {
    MobileHarnessException error = null;
    for (int i = 0; i < DEFAULT_RETRY_ATTEMPTS; i++) {
      try {
        String output = runShell(serial, command, timeout, lineCallback);
        if (error != null) {
          logger.atWarning().log(
              "%s",
              String.format(
                  "adb shell command succeed after retry %s times, last error:%n%s",
                  i, error.getMessage()));
        }
        return output;
      } catch (MobileHarnessException e) {
        error = e;
      }
      sleeper.sleep(RETRY_INTERVAL);
    }
    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_ADB_CMD_RETRY_ERROR,
        String.format(
            "Abort adb shell command after attempting %d times:%n%s",
            DEFAULT_RETRY_ATTEMPTS, error.getMessage()),
        error);
  }

  /**
   * Run {@link Command} in async way with executable as {@link #getAdbPath()}. This method is for
   * advance usage of Adb.
   *
   * @param command the {@link Command} to be executed
   * @throws MobileHarnessException if fails to execute the commands or timeout
   */
  public CommandProcess runAsync(Command command) throws MobileHarnessException {
    // Override the executable to default adb.
    command = command.executable(getAdbPath());
    return asyncCommand(command);
  }

  private CommandResult syncCommand(Command command)
      throws MobileHarnessException, InterruptedException {
    if (command.getStdoutLineCallback().isEmpty() && defaultOutputCallback != null) {
      command = command.onStdout(defaultOutputCallback);
    }

    // Adds extra ADB command timeout.
    command = command.timeout(addExtraAdbCommandTimeout(command.getTimeout().orElse(null)));

    try {
      return commandExecutorSupplier.get().exec(command);
    } catch (CommandException e) {
      if (e instanceof CommandStartException) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_ADB_SYNC_CMD_START_ERROR, e.getMessage(), e);
      }
      if (e instanceof CommandFailureException) {
        // Exit code 134 means the program received SIGABRT, as a result of a failed assertion.
        if (((CommandFailureException) e).result() != null
            && ((CommandFailureException) e).result().exitCode() == 134) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ASSERTION_FAILURE, e.getMessage(), e);
        } else {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, e.getMessage(), e);
        }
      }
      if (e instanceof CommandTimeoutException) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_TIMEOUT, e.getMessage(), e);
      } else {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, e.getMessage(), e);
      }
    }
  }

  private CommandProcess asyncCommand(Command command) throws MobileHarnessException {
    if (command.getStdoutLineCallback().isEmpty() && defaultOutputCallback != null) {
      command = command.onStdout(defaultOutputCallback);
    }

    // Adds extra ADB command timeout.
    command = command.timeout(addExtraAdbCommandTimeout(command.getTimeout().orElse(null)));

    try {
      return commandExecutorSupplier.get().start(command);
    } catch (CommandStartException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_ASYNC_CMD_START_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Adds {@linkplain Flags#extraAdbCommandTimeout extra ADB command timeout} to a command timeout.
   *
   * <p>If the original timeout is {@code null}, the {@linkplain CommandExecutor#getDefaultTimeout()
   * default timeout} of the command executor will be used as the original timeout.
   *
   * <p>If the {@linkplain Timeout#getPeriod() period} of the original timeout is empty, returns the
   * original timeout directly. Otherwise, returns a timeout whose period is the original period
   * <b>plus</b> the extra ADB command timeout.
   */
  private Timeout addExtraAdbCommandTimeout(@Nullable Timeout originalTimeout) {
    if (originalTimeout == null) {
      originalTimeout = commandExecutorSupplier.get().getDefaultTimeout();
    }
    Optional<Duration> originalPeriod = originalTimeout.getPeriod();
    if (originalPeriod.isEmpty()) {
      return originalTimeout;
    }
    Duration newPeriod =
        originalPeriod.get().plus(Flags.instance().extraAdbCommandTimeout.getNonNull());
    return originalTimeout.withFixed(newPeriod);
  }

  /** Output callback to log to {@link Adb}'s class logger. */
  private static class OutputCallbackImpl implements LineCallback {
    private final String tag;

    /** Creates a callback to print to the ADB logger. */
    public OutputCallbackImpl() {
      this("");
    }

    /** Creates a callback to print to the ADB logger with the given tag. */
    public OutputCallbackImpl(String tag) {
      this.tag = Strings.isNullOrEmpty(tag) ? "" : String.format("%s ", tag);
    }

    @Override
    public LineCallback.Response onLine(String line) {
      Adb.logger.atInfo().log("%s%s", tag, line);
      return LineCallback.Response.empty();
    }
  }
}
