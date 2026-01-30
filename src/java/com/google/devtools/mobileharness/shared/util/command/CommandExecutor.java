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

package com.google.devtools.mobileharness.shared.util.command;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.test.TestContext.TestContextRunnable;
import com.google.devtools.mobileharness.shared.util.command.LineCallback.Response;
import com.google.devtools.mobileharness.shared.util.command.backend.OutputSink;
import com.google.devtools.mobileharness.shared.util.command.history.CommandRecord;
import com.google.devtools.mobileharness.shared.util.command.history.CommandRecorder;
import com.google.devtools.mobileharness.shared.util.command.io.LineCollector;
import com.google.devtools.mobileharness.shared.util.command.io.LineReader;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Command executor for executing {@link Command}s.
 *
 * <p>Command executor has command base environment, command default, command default work directory
 * and command default redirect stderr which you can specify.
 *
 * @see Command
 */
public class CommandExecutor {

  /** Builder for building {@link CommandExecutor}. */
  public static class Builder {

    private ListeningExecutorService threadPool;
    private ListeningScheduledExecutorService timer;
    private com.google.devtools.mobileharness.shared.util.command.backend.CommandExecutor backend;

    /**
     * Sets the thread pool used by the command executor. By default, it is a shared thread pool for
     * {@link CommandExecutor}s which always propagates trace context.
     */
    @CanIgnoreReturnValue
    public Builder setThreadPool(ListeningExecutorService threadPool) {
      this.threadPool = threadPool;
      return this;
    }

    /** Sets the thread pool used by the command executor with a shared default one. */
    @CanIgnoreReturnValue
    public Builder useDefaultThreadPool(boolean propagateTraceContext) {
      return setThreadPool(
          propagateTraceContext
              ? LazyLoader.DEFAULT_THREAD_POOL
              : LazyLoader.DEFAULT_NON_PROPAGATING_THREAD_POOL);
    }

    /**
     * Sets the timer used by the command executor. By default, it is a shared timer for {@link
     * CommandExecutor}s.
     */
    @CanIgnoreReturnValue
    public Builder setTimer(ListeningScheduledExecutorService timer) {
      this.timer = timer;
      return this;
    }

    /**
     * Sets the command execution backend used by the command executor. By default, it is {@link
     * com.google.devtools.mobileharness.shared.util.command.backend.Command#NATIVE_EXECUTOR}.
     */
    @CanIgnoreReturnValue
    public Builder setBackend(
        com.google.devtools.mobileharness.shared.util.command.backend.CommandExecutor backend) {
      this.backend = backend;
      return this;
    }

    public CommandExecutor build() {
      return new CommandExecutor(
          checkNotNull(threadPool), checkNotNull(timer), checkNotNull(backend));
    }

    private Builder() {
      setThreadPool(LazyLoader.DEFAULT_THREAD_POOL);
      setTimer(LazyLoader.DEFAULT_TIMER);
      setBackend(
          com.google.devtools.mobileharness.shared.util.command.backend.Command.NATIVE_EXECUTOR);
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** System environment of the current process. */
  private static final ImmutableMap<String, String> SYSTEM_ENVIRONMENT =
      ImmutableMap.copyOf(System.getenv());

  /**
   * Default command timeout which will be used if you don't specify a timeout for a command and
   * {@link CommandExecutor#setDefaultTimeout(Timeout)} has not been invoked.
   */
  private static final Timeout DEFAULT_COMMAND_TIMEOUT = Timeout.fixed(Duration.ofMinutes(5));

  /**
   * Default command stderr redirect which will be used if you don't specify a stderr redirect for a
   * command and {@link CommandExecutor#setDefaultRedirectStderr(boolean)} has not been invoked.
   */
  private static final boolean DEFAULT_REDIRECT_STDERR = true;

  private final ListeningExecutorService threadPool;
  private final ListeningScheduledExecutorService timer;
  private final com.google.devtools.mobileharness.shared.util.command.backend.CommandExecutor
      backend;
  private final CommandBugChecker bugChecker = new CommandBugChecker();

  private final Lock baseEnvironmentLock = new ReentrantLock();

  @GuardedBy("baseEnvironmentLock")
  private final Map<String, String> baseEnvironment = new HashMap<>();

  private volatile Timeout defaultTimeout = DEFAULT_COMMAND_TIMEOUT;

  @Nullable private volatile Path defaultWorkDirectory;

  private volatile boolean defaultRedirectStderr = DEFAULT_REDIRECT_STDERR;

  /**
   * Creates a new command executor which uses default shared thread pool, default shared timer,
   * default command default timeout (5 minutes) and default command default stderr redirect (true).
   */
  public CommandExecutor() {
    this(
        LazyLoader.DEFAULT_THREAD_POOL,
        LazyLoader.DEFAULT_TIMER,
        com.google.devtools.mobileharness.shared.util.command.backend.Command.NATIVE_EXECUTOR);
  }

  private CommandExecutor(
      ListeningExecutorService threadPool,
      ListeningScheduledExecutorService timer,
      com.google.devtools.mobileharness.shared.util.command.backend.CommandExecutor backend) {
    this.threadPool = threadPool;
    this.timer = timer;
    this.backend = backend;
  }

  /**
   * Executes a command and returns its stdout when it completes and all stdout/stderr are handled
   * and if its exit code is in the command's success exit codes ({@code 0} by default), and throws
   * {@link CommandFailureException} otherwise.
   *
   * <p>Short for {@code exec(command).stdout()}.
   *
   * <p>Note that the trailing "\n" in stdout will <b>NOT</b> be omitted if any. For example, <code>
   * run(Command.of("echo", "Hello"))</code> will return "Hello\n" rather than "Hello", which is
   * different from the behavior of MH legacy command executor. If you want the same behavior as the
   * legacy command executor, please use <code>exec(Command).stdoutWithoutTrailingLineTerminator()
   * </code> instead.
   *
   * @throws CommandStartException if an error occurs when starting the command
   * @throws CommandFailureException if the result fails the command's success exit codes (by
   *     default zero)
   * @throws CommandTimeoutException if the command timeouts
   * @throws InterruptedException if the execution is interrupted. The method will kill the command
   *     then.
   * @see Command
   * @see #exec(Command)
   */
  @CanIgnoreReturnValue
  public String run(Command command) throws CommandException, InterruptedException {
    return exec(command).stdout();
  }

  /**
   * Starts executing a command and returns a future of its result.
   *
   * <p>The future will complete when the command completes and all stdout/stderr are handled.
   *
   * <p>If the future is cancelled by {@code cancel(true)}, the command will be killed. Its
   * interrupted {@code get()} or {@code cancel(false)} will not cause the command to be killed.
   *
   * @see Command
   */
  @CheckReturnValue
  public ListenableFuture<String> asyncRun(Command command) {
    return threadPool.submit(() -> run(command));
  }

  /**
   * Executes a command and returns its result when it completes and all stdout/stderr are handled
   * and if its exit code is in the command's success exit codes ({@code 0} by default), and throws
   * {@link CommandFailureException} otherwise.
   *
   * @throws CommandStartException if an error occurs when starting the command
   * @throws CommandFailureException if the result fails the command's success exit codes (by
   *     default zero)
   * @throws CommandTimeoutException if the command timeouts
   * @throws InterruptedException if the execution is interrupted. The method will kill the command
   *     then.
   * @see Command
   * @see CommandResult
   */
  @CanIgnoreReturnValue
  public CommandResult exec(Command command) throws CommandException, InterruptedException {
    CommandProcess commandProcess = start(command);
    try {
      return commandProcess.await();
    } catch (InterruptedException e) {
      // Kills the command if this method is interrupted.
      logger.atFine().log("Interrupted while awaiting result of command [%s], kill it", command);
      commandProcess.kill();
      throw e;
    }
  }

  /**
   * Starts executing a command and returns a future of its result.
   *
   * <p>The future will complete when the command completes and all stdout/stderr are handled.
   *
   * <p>If the future is cancelled by {@code cancel(true)}, the command will be killed. Its
   * interrupted {@code get()} or {@code cancel(false)} will not cause the command to be killed.
   *
   * @see Command
   * @see CommandResult
   */
  @CheckReturnValue
  public ListenableFuture<CommandResult> asyncExec(Command command) {
    return threadPool.submit(() -> exec(command));
  }

  /**
   * Starts executing a command and returns its process.
   *
   * @throws CommandStartException if an error occurs when starting the command
   * @see Command
   * @see CommandProcess
   */
  @CheckReturnValue
  public CommandProcess start(Command command) throws CommandStartException {
    CommandRecord commandRecord = CommandRecorder.getInstance().addCommand(command.getCommand());

    // Checks potential bugs of the command.
    bugChecker.checkCommand(command);

    // Calculates remaining time.
    Duration remainingTime =
        getRemainingTime(command, command.getTimeout().orElse(getDefaultTimeout()));
    Optional<Timeout> startTimeout = command.getStartTimeout();
    Optional<Duration> startRemainingTime =
        startTimeout.isPresent()
            ? Optional.of(getRemainingTime(command, startTimeout.get()))
            : Optional.empty();

    // Unless explicitly set by the caller the default is always true
    boolean redirectStderr = command.getRedirectStderr().orElse(getDefaultRedirectStderr());

    OutputSink stdoutSink;
    LineCollector stdoutCollector;
    if (command.getStdoutPath().isPresent()) {
      // Output to file
      stdoutCollector = null;
      stdoutSink = OutputSink.toFile(command.getStdoutPath().get());
    } else {
      // Set up line reader and collector
      LineReader stdoutReader = new LineReader();
      stdoutSink = OutputSink.toStream(stdoutReader);
      stdoutCollector =
          new LineCollector(
              /* numSource= */ redirectStderr ? 2 : 1, command.getNeedStdoutInResult());
    }

    OutputSink stderrSink;
    LineCollector stderrCollector;
    if (command.getStderrPath().isPresent()) {
      // Output to file
      stderrCollector = null;
      stderrSink = OutputSink.toFile(command.getStderrPath().get());
    } else {
      // Set up line reader for the backend command
      LineReader stderrReader = new LineReader();
      stderrSink = OutputSink.toStream(stderrReader);
      // Set up collector.
      stderrCollector =
          new LineCollector(
              /* numSource= */ redirectStderr ? 0 : 1, command.getNeedStderrInResult());
    }

    // Creates backend command.
    com.google.devtools.mobileharness.shared.util.command.backend.Command backendCommand =
        getBackendCommand(command, stdoutSink, stderrSink);

    // Starts backend process.
    com.google.devtools.mobileharness.shared.util.command.backend.CommandProcess backendProcess;
    try {
      backendProcess = backend.start(backendCommand);
    } catch (
        com.google.devtools.mobileharness.shared.util.command.backend.CommandStartException e) {
      throw new CommandStartException("Failed to start command", e, command);
    }

    // Creates command process.
    CommandProcess commandProcess =
        new CommandProcess(
            command,
            backendProcess,
            stdoutCollector,
            stderrCollector,
            remainingTime,
            startRemainingTime.orElse(null));

    // Schedules timeout tasks.
    Runnable timeoutTask = new TestContextRunnable(new TimeoutTask(commandProcess));
    Function<Duration, ListenableFuture<?>> timeoutTaskScheduler =
        timeout -> timer.schedule(timeoutTask, timeout.toMillis(), MILLISECONDS);
    ListenableFuture<?> timeoutTaskFuture = timeoutTaskScheduler.apply(remainingTime);
    @Nullable
    ListenableFuture<?> startTimeoutTaskFuture =
        startRemainingTime.map(timeoutTaskScheduler).orElse(null);

    // Writes initial input.
    writeToStdin(commandProcess, command.getInput().orElse(null));

    if (command.getShouldCloseStdinAfterInput()) {
      try {
        commandProcess.stdinWriter().close();
      } catch (IOException e) {
        logger.atWarning().withCause(e).log(
            "Failed to close stdin of command [%s]", commandProcess.command());
      }
    }

    // Sets line consumers and starts reading stdout.
    if (stdoutCollector != null) {
      stdoutCollector.setLineConsumer(
          new LineConsumer(
              commandProcess,
              command.getStdoutLineCallback().orElse(null),
              startTimeoutTaskFuture));
      threadPool.execute(
          new TestContextRunnable(
              () ->
                  readOutput(
                      (LineReader) stdoutSink.streamSupplier(),
                      stdoutCollector,
                      commandProcess.command())));
    }

    // Sets line consumers and starts reading stderr only if necessary.
    if (stderrCollector != null) {
      stderrCollector.setLineConsumer(
          new LineConsumer(
              commandProcess,
              command.getStderrLineCallback().orElse(null),
              startTimeoutTaskFuture));
      // If output is already redirected to stderr and is being written to a file, do not launch
      // thread to scan stderr lines.
      if (!(redirectStderr && stdoutCollector == null)) {
        threadPool.execute(
            new TestContextRunnable(
                () ->
                    readOutput(
                        (LineReader) stderrSink.streamSupplier(),
                        redirectStderr ? stdoutCollector : stderrCollector,
                        commandProcess.command())));
      }
    }

    // Schedules post run task.
    threadPool.execute(
        new TestContextRunnable(
            () ->
                postRun(commandProcess, timeoutTaskFuture, startTimeoutTaskFuture, commandRecord)));

    return commandProcess;
  }

  @CanIgnoreReturnValue
  public CommandExecutor setBaseEnvironment(Map<String, String> baseEnvironment) {
    checkNotNull(baseEnvironment);
    baseEnvironmentLock.lock();
    try {
      this.baseEnvironment.clear();
      this.baseEnvironment.putAll(baseEnvironment);
    } finally {
      baseEnvironmentLock.unlock();
    }
    return this;
  }

  @CanIgnoreReturnValue
  public CommandExecutor updateBaseEnvironment(String key, String value) {
    baseEnvironmentLock.lock();
    try {
      baseEnvironment.put(key, value);
    } finally {
      baseEnvironmentLock.unlock();
    }
    return this;
  }

  public Map<String, String> getBaseEnvironment() {
    baseEnvironmentLock.lock();
    try {
      return ImmutableMap.copyOf(baseEnvironment);
    } finally {
      baseEnvironmentLock.unlock();
    }
  }

  @CanIgnoreReturnValue
  public CommandExecutor setDefaultTimeout(Timeout defaultTimeout) {
    this.defaultTimeout = checkNotNull(defaultTimeout);
    return this;
  }

  public Timeout getDefaultTimeout() {
    return defaultTimeout;
  }

  @CanIgnoreReturnValue
  public CommandExecutor setDefaultWorkDirectory(@Nullable Path defaultWorkDirectory) {
    this.defaultWorkDirectory = defaultWorkDirectory;
    return this;
  }

  public Optional<Path> getDefaultWorkDirectory() {
    return Optional.ofNullable(defaultWorkDirectory);
  }

  @CanIgnoreReturnValue
  public CommandExecutor setDefaultRedirectStderr(boolean defaultRedirectStderr) {
    this.defaultRedirectStderr = defaultRedirectStderr;
    return this;
  }

  public boolean getDefaultRedirectStderr() {
    return defaultRedirectStderr;
  }

  // This lambda implements @Immutable interface 'SuccessCondition', but the declaration of type
  // 'com.google.devtools.mobileharness.shared.util.command.Command' is not annotated with
  // @com.google.errorprone.annotations.Immutable
  @SuppressWarnings("Immutable")
  private com.google.devtools.mobileharness.shared.util.command.backend.Command getBackendCommand(
      Command command, OutputSink stdoutSink, OutputSink stderrSink) {
    return com.google.devtools.mobileharness.shared.util.command.backend.Command.command(
            command.getExecutable(), command.getArguments().toArray(new String[0]))
        .withSuccessCondition(result -> command.getSuccessExitCodes().contains(result.exitCode()))
        .withEnvironment(SYSTEM_ENVIRONMENT)
        .withEnvironmentUpdated(getBaseEnvironment())
        .withEnvironmentUpdated(command.getExtraEnvironment())
        .withWorkingDirectory(getCommandWorkDirectory(command))
        .withStdoutTo(stdoutSink)
        .withStderrTo(stderrSink);
  }

  private Optional<Path> getCommandWorkDirectory(Command command) {
    return command.getWorkDirectory().or(this::getDefaultWorkDirectory);
  }

  private static Duration getRemainingTime(Command command, Timeout timeout)
      throws CommandStartException {
    try {
      return timeout.getRemainingTime();
    } catch (MobileHarnessException e) {
      throw new CommandStartException("Invalid command timeout", e, command);
    }
  }

  private static void writeToStdin(CommandProcess commandProcess, @Nullable String input) {
    if (input != null) {
      try {
        commandProcess.stdinWriter().write(input);
        commandProcess.stdinWriter().flush();
      } catch (IOException e) {
        logger.atWarning().withCause(e).log(
            "Failed to write [%s] to stdin of command [%s], kill it",
            input, commandProcess.command());
        commandProcess.kill();
      }
    }
  }

  private static void readOutput(
      LineReader lineReader, LineCollector lineCollector, Command command) {
    try {
      lineReader.start(lineCollector);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to read from command [%s]", command);
    }
  }

  private static void postRun(
      CommandProcess commandProcess,
      ListenableFuture<?> timeoutTaskFuture,
      @Nullable ListenableFuture<?> startTimeoutTaskFuture,
      CommandRecord commandRecord) {
    try {
      CommandResult result;
      try {
        result = commandProcess.await();
      } catch (CommandExecutionException e) {
        result = e.result();
      } finally {
        timeoutTaskFuture.cancel(/* mayInterruptIfRunning= */ false);
        if (startTimeoutTaskFuture != null) {
          startTimeoutTaskFuture.cancel(/* mayInterruptIfRunning= */ false);
        }
        commandProcess.setSuccessfulStart(/* successfulStart= */ false);
      }
      CommandRecorder.getInstance().addCommandResult(commandRecord, result);
      final CommandResult commandResult = result;
      commandProcess
          .command()
          .getExitCallback()
          .ifPresent(callback -> callback.accept(commandResult));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private class TimeoutTask implements Runnable {

    private final AtomicBoolean isStarted = new AtomicBoolean();
    private final CommandProcess commandProcess;

    private TimeoutTask(CommandProcess commandProcess) {
      this.commandProcess = commandProcess;
    }

    @Override
    public void run() {
      if (!isStarted.getAndSet(true)) {
        threadPool.execute(this::onTimeout);
      }
    }

    private void onTimeout() {
      logger.atInfo().log("Kill timeout command: %s", commandProcess.command());
      commandProcess.setTimeout();
      commandProcess.kill();
      commandProcess.command().getTimeoutCallback().ifPresent(Runnable::run);
    }
  }

  /** Returning {@code true} indicates stopping consuming lines. */
  @NotThreadSafe
  private static class LineConsumer implements Predicate<String> {

    private final CommandProcess commandProcess;
    @Nullable private final ListenableFuture<?> startTimeoutTaskFuture;
    @Nullable private LineCallback lineCallback;

    private LineConsumer(
        CommandProcess commandProcess,
        @Nullable LineCallback lineCallback,
        @Nullable ListenableFuture<?> startTimeoutTaskFuture) {
      this.commandProcess = commandProcess;
      this.lineCallback = lineCallback;
      this.startTimeoutTaskFuture = startTimeoutTaskFuture;
    }

    @Override
    public boolean test(String line) {
      // Tests successful start.
      if (testSuccessfulStart(line)) {
        if (startTimeoutTaskFuture != null) {
          startTimeoutTaskFuture.cancel(/* mayInterruptIfRunning= */ false);
        }
        commandProcess.setSuccessfulStart(true);
      }

      // Calls line callback.
      if (lineCallback != null) {
        Response response = null;
        try {
          response = lineCallback.onLine(line);
        } catch (LineCallbackException e) {
          logger.atInfo().withCause(e).log(
              "Line callback error of command [%s], line=[%s]", commandProcess.command(), line);
          if (e.getKillCommand()) {
            logger.atFine().log(
                "Kill command [%s] by its callback error, line=[%s]",
                commandProcess.command(), line);
            commandProcess.kill();
          }
          if (e.getStopReadingOutput()) {
            lineCallback = null;
          }
        } catch (RuntimeException e) {
          logger.atWarning().withCause(e).log(
              "Line callback runtime exception, command=[%s], line=[%s]",
              commandProcess.command(), line);
        }
        if (response != null) {
          writeToStdin(commandProcess, response.getAnswer().orElse(null));
          if (response.getStop()) {
            logger.atFine().log(
                "Stop command [%s] by its callback, line=[%s]", commandProcess.command(), line);
            commandProcess.stop();
          }
          if (response.getStopReadingOutput()) {
            lineCallback = null;
          }
        }
      }
      return lineCallback == null
          && commandProcess.successfulStartFuture().isDone()
          && Boolean.TRUE.equals(getUnchecked(commandProcess.successfulStartFuture()));
    }

    private boolean testSuccessfulStart(String line) {
      try {
        return commandProcess.command().getSuccessfulStartCondition().test(line);
      } catch (RuntimeException e) {
        logger.atWarning().withCause(e).log(
            "Error when testing successful start condition of command [%s] with line [%s]",
            commandProcess.command(), line);
        return false;
      }
    }
  }

  private static class LazyLoader {

    private static final ListeningExecutorService DEFAULT_NON_PROPAGATING_THREAD_POOL =
        ThreadPools.createStandardThreadPool("default-mh-command-executor");

    private static final ListeningExecutorService DEFAULT_THREAD_POOL =
        decorateWithLocalTraceSpan(
            DEFAULT_NON_PROPAGATING_THREAD_POOL, ListeningExecutorService.class);

    private static final ListeningScheduledExecutorService DEFAULT_TIMER =
        decorateWithLocalTraceSpan(
            ThreadPools.createStandardScheduledThreadPool(
                "default-mh-command-executor-timer", /* corePoolSize= */ 30),
            ListeningScheduledExecutorService.class);
  }

  @SuppressWarnings("unused")
  private static <T extends Executor> T decorateWithLocalTraceSpan(
      T executor, Class<T> interfaceName) {
    return executor;
  }
}
