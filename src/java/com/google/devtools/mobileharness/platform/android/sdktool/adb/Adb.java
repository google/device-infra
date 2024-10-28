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

package com.google.devtools.mobileharness.platform.android.sdktool.adb;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Executor for invoking ADB command line tools from Android SDK.
 *
 * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
 * separator on SDK>23. It's callers' responsibility to parse it correctly.
 */
// LINT.IfChange
public class Adb {

  private final com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb
      newAdb;
  private final Supplier<Command> adbCommandSupplier;

  /** Creates a executor for running ADB commands using Android SDK tools. */
  public Adb() {
    this(
        new com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb(),
        new SystemUtil());
  }

  @VisibleForTesting
  Adb(
      com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb newAdb,
      SystemUtil systemUtil) {
    this.newAdb = newAdb;
    this.adbCommandSupplier = Suppliers.memoize(newAdb::getAdbCommand);
  }

  public String getAdbPath() {
    return newAdb.getAdbPath();
  }

  public String getAdbKeyPath() {
    return newAdb.getAdbKeyPath();
  }

  public int getAdbServerPort() {
    return newAdb.getAdbServerPort();
  }

  public String getAdbServerHost() {
    return newAdb.getAdbServerHost();
  }

  public Command getAdbCommand() {
    return adbCommandSupplier.get();
  }

  /** StockAdbPath is the native Adb binary path from {@code AdbParam}. */
  public String getStockAdbPath() {
    return newAdb.getStockAdbPath();
  }

  /**
   * Enables adb command output to be logged to the class logger. Note that this is done via a
   * {@link LineCallback}, so if a callback is passed explicitly into a run() or runWithRetry()
   * method, this logging will not take effect
   *
   * <p>WARNING: This will logging ALL command output for this instance of Adb. Take caution to make
   * sure this won't unintentionally spam your log.
   */
  public void enableCommandOutputLogging() {
    newAdb.enableCommandOutputLogging();
  }

  /**
   * Runs ADB command with default timeout.
   *
   * @param args adb command line arguments
   * @return command adb command std output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  @CanIgnoreReturnValue
  public String run(String[] args) throws MobileHarnessException, InterruptedException {
    return newAdb.run(args);
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
  @CanIgnoreReturnValue
  public String run(String[] args, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return newAdb.run(args, timeout);
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
    return newAdb.run(args, timeout, lineCallback);
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
  @CanIgnoreReturnValue
  public String run(String serial, String[] args)
      throws MobileHarnessException, InterruptedException {
    return newAdb.run(serial, args);
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
  @CanIgnoreReturnValue
  public String run(String serial, String[] args, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return newAdb.run(serial, args, timeout);
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
  @CanIgnoreReturnValue
  public String run(
      String serial, String[] args, @Nullable Duration timeout, @Nullable LineCallback lineCallback)
      throws MobileHarnessException, InterruptedException {
    return newAdb.run(serial, args, timeout, lineCallback);
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
    return newAdb.run(command);
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
    return newAdb.runWithRetry(args);
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
    return newAdb.runWithRetry(args, timeout);
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
  @CanIgnoreReturnValue
  public String runWithRetry(String serial, String[] args)
      throws MobileHarnessException, InterruptedException {
    return newAdb.runWithRetry(serial, args);
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
  @CanIgnoreReturnValue
  public String runWithRetry(String serial, String[] args, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return newAdb.runWithRetry(serial, args, timeout);
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
    return newAdb.runWithRetry(serial, args, timeout, lineCallback);
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
  @CanIgnoreReturnValue
  public String runShell(String serial, String command)
      throws MobileHarnessException, InterruptedException {
    return newAdb.runShell(serial, command);
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
  @CanIgnoreReturnValue
  public String runShell(String serial, String command, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return newAdb.runShell(serial, command, timeout);
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
    return newAdb.runShell(serial, command, timeout, lineCallback);
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
    return newAdb.runShell(serial, command, disableRemoteExitCode, timeout, lineCallback);
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
    return newAdb.runShell(serial, command, timeout, lineCallback);
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
  @CanIgnoreReturnValue
  public CommandProcess runShellAsync(String serial, String command, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return newAdb.runShellAsync(serial, command, timeout);
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
    return newAdb.runShellAsync(serial, command, timeout);
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
  @CanIgnoreReturnValue
  public String runShellWithRetry(String serial, String command)
      throws MobileHarnessException, InterruptedException {
    return newAdb.runShellWithRetry(serial, command);
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
  @CanIgnoreReturnValue
  public String runShellWithRetry(String serial, String command, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return newAdb.runShellWithRetry(serial, command, timeout);
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
    return newAdb.runShellWithRetry(serial, command, timeout, lineCallback);
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
    return newAdb.runShellWithRetry(serial, command, timeout, lineCallback);
  }

  /**
   * Run {@link Command} in async way with executable as {@link #getAdbPath()}. This method is for
   * advance usage of Adb.
   *
   * @param command the {@link Command} to be executed
   * @throws MobileHarnessException if fails to execute the commands or timeout
   */
  @CanIgnoreReturnValue
  public CommandProcess runAsync(Command command) throws MobileHarnessException {
    return newAdb.runAsync(command);
  }
}
// LINT.ThenChange(//depot/google3/third_party/deviceinfra/src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb/Adb.java)
