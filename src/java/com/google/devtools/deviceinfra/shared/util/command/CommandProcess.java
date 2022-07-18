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

package com.google.devtools.deviceinfra.shared.util.command;

import com.google.common.annotations.Beta;
import com.google.devtools.deviceinfra.api.error.id.defined.BasicErrorId;
import com.google.devtools.deviceinfra.shared.util.command.io.LineCollector;
import java.io.OutputStream;
import java.io.Writer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

/**
 * Process of a running command.
 *
 * <p>Use {@link #await()} to wait until the command ends.
 *
 * <p>Use {@link #kill()} to kill the command.
 *
 * <p>Use {@link #stdinStream()}/{@link #stdinWriter()} to write to stdin of the command.
 *
 * @see CommandExecutor#start(Command)
 */
public class CommandProcess {

  private static final CommandExecutor EXECUTOR = new CommandExecutor();

  private final Command command;
  private final CommandProcessImpl impl;

  /** Do NOT make it public. */
  CommandProcess(
      Command command,
      com.google.devtools.deviceinfra.shared.util.command.backend.CommandProcess backendProcess,
      LineCollector stdoutCollector,
      LineCollector stderrCollector,
      Duration finalizedTimeout,
      @Nullable Duration finalizedStartTimeout) {
    this.command = command;
    this.impl =
        new CommandProcessImpl(
            backendProcess,
            stdoutCollector,
            stderrCollector,
            finalizedTimeout,
            finalizedStartTimeout);
  }

  /**
   * Blocks until the command ends and all stdout/stderr are handled. It returns the command result
   * if its exit code is in the command's success exit codes ({@code 0} by default), and throws
   * {@link CommandFailureException} otherwise.
   *
   * <p>If this thread is interrupted, an {@link InterruptedException} is thrown, but the <i>process
   * will continue running</i>. If you wish the process to be killed in this case, catch this
   * exception and call {@link CommandProcess#kill} on the running process.
   *
   * @throws CommandFailureException if the result fails the command's success exit codes
   * @throws CommandTimeoutException if the command timeouts
   * @throws InterruptedException if the execution is interrupted
   */
  public CommandResult await()
      throws CommandFailureException, InterruptedException, CommandTimeoutException {
    try {
      int exitCode = impl.backendProcess().await().exitCode();
      String stdout = impl.stdoutCollector().waitForAllLines();
      String stderr = impl.stderrCollector().waitForAllLines();
      return getResult(stdout, stderr, exitCode, /*backendFailureException=*/ null);
    } catch (
        com.google.devtools.deviceinfra.shared.util.command.backend.CommandFailureException e) {
      int exitCode = e.result().exitCode();
      String stdout = impl.stdoutCollector().waitForAllLines();
      String stderr = impl.stderrCollector().waitForAllLines();
      return getResult(stdout, stderr, exitCode, e);
    }
  }

  /**
   * Blocks until the command ends and all stdout/stderr are handled or the timeout is reached. It
   * returns the command result if its exit code is in the command's success exit codes ({@code 0}
   * by default), and throws {@link CommandFailureException} otherwise.
   *
   * <p>If this thread is interrupted or {@link TimeoutException} is thrown, the <i>process will
   * continue running</i>. If you wish the process to be killed in this case, catch this exception
   * and call {@link CommandProcess#kill} on the running process.
   *
   * @throws CommandFailureException if the result fails the command's success exit codes
   * @throws CommandTimeoutException if the command timeouts
   * @throws InterruptedException if the execution is interrupted
   * @throws CommandException if the waiting time {@code timeout} elapsed before the command
   *     completed
   */
  public CommandResult await(Duration timeout) throws CommandException, InterruptedException {
    Instant deadline = Clock.systemUTC().instant().plus(timeout);
    try {
      int exitCode =
          impl.backendProcess()
              .await(Duration.between(Clock.systemUTC().instant(), deadline))
              .exitCode();
      String stdout =
          impl.stdoutCollector()
              .waitForAllLines(Duration.between(Clock.systemUTC().instant(), deadline));
      String stderr =
          impl.stderrCollector()
              .waitForAllLines(Duration.between(Clock.systemUTC().instant(), deadline));
      return getResult(stdout, stderr, exitCode, /*backendFailureException=*/ null);
    } catch (
        com.google.devtools.deviceinfra.shared.util.command.backend.CommandFailureException e) {
      int exitCode = e.result().exitCode();
      String stdout =
          impl.stdoutCollector()
              .waitForAllLines(Duration.between(Clock.systemUTC().instant(), deadline));
      String stderr =
          impl.stderrCollector()
              .waitForAllLines(Duration.between(Clock.systemUTC().instant(), deadline));
      return getResult(stdout, stderr, exitCode, e);
    } catch (TimeoutException e) {
      throw new CommandException(
          BasicErrorId.CMD_PROCESS_AWAIT_TIMEOUT,
          "Timeout when awaiting command process, timeout=" + timeout,
          e,
          command);
    }
  }

  /**
   * Kills the command, hides any {@link CommandFailureException} from its result.
   *
   * <p>If a command is stopped, getting its result by {@link CommandExecutor#run(Command)}, {@link
   * CommandExecutor#exec(Command)} and {@link CommandProcess#await()} will not throw {@link
   * CommandFailureException} no matter if the command successes.
   */
  public void stop() {
    impl.isStopped().set(true);
    kill();
  }

  /** Kills the command. Killing a process that has already exited has no effect. */
  public void kill() {
    impl.backendProcess().kill();
  }

  /**
   * Kills the command forcibly. Killing a process that has already exited has no effect.
   *
   * <p><b>NOTE</b>: If the feature is not supported, it will invoke {@link #kill()} instead.
   */
  public void killForcibly() {
    impl.backendProcess().killForcibly();
  }

  /**
   * Kills the command with the given signal.
   *
   * <p><b>*WARNING*</b>: This feature is experimental, and it is only supported on UNIX. Please use
   * {@link #kill()} and {@link #killForcibly()} instead if possible.
   *
   * @param signal the signal for killing the command (e.g., 2 for SIGINT and 3 for SIGQUIT)
   * @throws UnsupportedOperationException if the platform is not UNIX
   * @see <a href="https://ss64.com/bash/kill.html">kill Man Page</a>
   */
  @Beta
  public void killWithSignal(int signal) throws InterruptedException {
    try {
      int pid = getUnixPid();
      EXECUTOR.run(Command.of("kill", "-" + signal, Integer.toString(pid)));
    } catch (CommandException e) {
      throw new UnsupportedOperationException(e);
    }
  }

  /** Returns whether the command process is alive or handling stdout/stderr. */
  public boolean isAlive() {
    return impl.backendProcess().isAlive()
        || impl.stdoutCollector().notAllSourceClosed()
        || impl.stderrCollector().notAllSourceClosed();
  }

  /**
   * Returns a new {@link OutputStream} connected to the standard input of the process. If the
   * process has exited, writing to it is a noop.
   */
  public OutputStream stdinStream() {
    return impl.stdinStream();
  }

  /**
   * Returns a new {@link Writer} connected to the standard input of the process, using UTF-8
   * encoding. If the process has exited, writing to it is a noop.
   */
  public Writer stdinWriter() {
    return impl.stdinWriter();
  }

  /** Returns the command of the command process */
  public Command command() {
    return command;
  }

  /**
   * Marks the command is timeout.
   *
   * <p>If a command is timeout, {@link #await()} on its process will always throw {@link
   * CommandTimeoutException} no matter whether the command successes.
   */
  void timeout() {
    impl.isTimeout().set(true);
  }

  private CommandResult getResult(
      String stdout,
      String stderr,
      int exitCode,
      @Nullable
          com.google.devtools.deviceinfra.shared.util.command.backend.CommandFailureException
              backendFailureException)
      throws CommandFailureException, CommandTimeoutException {
    CommandResult result =
        CommandResults.of(stdout, stderr, exitCode, impl.isTimeout().get(), impl.isStopped().get());
    if (result.isTimeout()) {
      throw new CommandTimeoutException(
          command, impl.finalizedTimeout(), impl.finalizedStartTimeout().orElse(null), result);
    } else if (result.isStopped() || backendFailureException == null) {
      return result;
    } else {
      throw new CommandFailureException(backendFailureException, command, result);
    }
  }

  public int getUnixPid() throws CommandException {
    try {
      return (int) impl.backendProcess().processId();
    } catch (UnsupportedOperationException e) {
      throw new CommandException(
          BasicErrorId.CMD_PROCESS_GET_ID_FAILURE, "Failed to get UNIX PID", e, command);
    }
  }

  /** Do NOT make it public. */
  CommandProcessImpl implementation() {
    return impl;
  }
}
