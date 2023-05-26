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

package com.google.devtools.mobileharness.shared.util.command.backend;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.common.base.Verify;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * A running process, generally started via {@link Command#start}. This class allows you to interact
 * with the process as it executes asynchronously. Use {@link #await} or {@link Command#execute} to
 * cause the current thread to block until the process terminates.
 *
 * <p><b>Note:</b> native processes write output to fixed-size buffers and will block after either
 * buffer is full. Therefore starting a subprocess without reading stdout and stderr risks causing
 * the subprocess to stall. Calling {@code .await()} will flush these buffers for you.
 */
public abstract class CommandProcess {
  private final Command command;
  private final AtomicResult result;
  private final Opener<InputStream> stdinSourceOpener;
  private final Opener<OutputStream> stdoutSinkOpener;
  private final Opener<OutputStream> stderrSinkOpener;
  private final CapturingOutputStream stdoutStream = new CapturingOutputStream();
  private final CapturingOutputStream stderrStream = new CapturingOutputStream();

  protected CommandProcess(Command command) {
    this.command = checkNotNull(command);
    result = new AtomicResult(command, this::killHook);
    stdinSourceOpener = new Opener<>(command.stdinSource()::openStream);
    stdoutSinkOpener =
        new Opener<>(
            () -> {
              switch (command.stdoutSink().kind()) {
                case PROCESS_OUT:
                  return stdoutStream;
                case PROCESS_ERR:
                  return stderrStream;
                default:
                  return command.stdoutSink().openStream();
              }
            });
    stderrSinkOpener =
        new Opener<>(
            () -> {
              switch (command.stderrSink().kind()) {
                case PROCESS_OUT:
                  return stdoutStream;
                case PROCESS_ERR:
                  return stderrStream;
                default:
                  return command.stderrSink().openStream();
              }
            });
  }

  /** The command that started this process. */
  public final Command command() {
    return command;
  }

  /**
   * Creates a view of a {@link CommandProcess} as a {@link ListenableFuture}.
   *
   * <p>The returned future remains pending until the process it manages has terminated. At that
   * point the future succeeds if the process terminated successfully (e.g. returned an exit code of
   * 0) and fails if the process failed - see {@link Command#withSuccessCondition}. Canceling the
   * future with {@code mayInterruptIfRunning == true} before the process has finished will
   * {@linkplain #kill} the process.
   *
   * <p>The current implementation spins up a single background thread that polls all running
   * processes for which a future has been created.
   */
  public ListenableFuture<CommandResult> asFuture() {
    return result.future();
  }

  /** Returns whether the process has not yet terminated. */
  public final boolean isAlive() {
    return !result.isComplete();
  }

  /**
   * Blocks until the command completes. It returns the command result if the result satisfies the
   * command's success condition (by default a zero exit code), and throws {@link
   * CommandFailureException} otherwise.
   *
   * <p>If this thread is interrupted, an {@link InterruptedException} is thrown, but the <i>process
   * will continue running</i>. If you wish the process to be killed in this case, catch this
   * exception and call {@link CommandProcess#kill} on the running process.
   *
   * @throws CommandFailureException - if the result fails the command's success condition
   * @throws InterruptedException - if the awaiting thread is interrupted
   */
  @CanIgnoreReturnValue
  public final CommandResult await() throws CommandFailureException, InterruptedException {
    return result.await();
  }

  /**
   * Blocks until the command completes or the timeout is reached. If the timeout is reached before
   * the command finishes, {@link TimeoutException} is thrown. It returns the command result if the
   * result satisfies the command's success condition (by default a zero exit code), and throws
   * {@link CommandFailureException} otherwise.
   *
   * <p>If this thread is interrupted, an {@link InterruptedException} is thrown, but the <i>process
   * will continue running</i>. If you wish the process to be killed in this case, catch this
   * exception and call {@link CommandProcess#kill} on the running process.
   *
   * @throws CommandFailureException - if the result fails the command's success condition
   * @throws InterruptedException - if the awaiting thread is interrupted
   * @throws TimeoutException - if the process is still alive after the timeout has elapsed
   */
  @CanIgnoreReturnValue
  public final CommandResult await(Duration timeout)
      throws CommandFailureException, InterruptedException, TimeoutException {
    return result.await(timeout);
  }

  /**
   * Invokes {@link await} {@link com.google.common.util.concurrent.Uninterruptibles
   * uninterruptibly}. This is a convenience method for cases where you <i>do not want to respect
   * interruption</i>, and should be used sparingly. Consider {@link #awaitChecked} if you want to
   * respect interruption but will simply propagate a different exception if interrupted.
   *
   * @throws CommandFailureException - if the result fails the command's success condition
   */
  @CanIgnoreReturnValue
  public final CommandResult awaitUninterruptibly() throws CommandFailureException {
    boolean interrupted = false;
    try {
      while (true) {
        try {
          return await();
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Blocks until the command completes. It returns the command result if the result satisfies the
   * command's success condition (by default a zero exit code), and throws {@code E} otherwise. If
   * this thread is interrupted, this method kills the process and throws {@link E}, preserving the
   * thread's interrupted status.
   *
   * @throws E - if there was an error starting the command, if the result fails the command's
   *     success condition, or if the execution is interrupted
   */
  @CanIgnoreReturnValue
  public final <E extends Exception> CommandResult awaitChecked(Function<Exception, E> factory)
      throws E {
    try {
      return await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw factory.apply(e);
    } catch (CommandFailureException e) {
      throw factory.apply(e);
    }
  }

  /**
   * For a process that has already completed, returns the command result if the result satisfies
   * the command's success condition (by default a zero exit code), and throws {@link
   * CommandFailureException} otherwise. This is the non-blocking counterpart to {@link #await}, and
   * will fail if the process is still running.
   *
   * @throws CommandFailureException - if the result fails the command's success condition
   * @throws IllegalStateException - if the process is still alive
   */
  public final CommandResult getDone() throws CommandFailureException {
    Optional<CommandResult> res = result.get();
    checkState(
        res.isPresent(),
        "Process %s is still alive - use await() to wait for the process to complete.",
        this);
    return res.get();
  }

  /**
   * Sends a signal to terminate the process and returns immediately. Killing a process that has
   * already exited has no effect. To wait for the process to be killed, use {@code kill().await()}.
   *
   * <p>This method makes a best-effort attempt to read in any data already written to stdout or
   * stderr before the process is terminated. Reading output from a process as you kill it is
   * inherently racy, so if there's any output you expect to see it should be read explicitly via
   * {@link #stdoutStream}/{@link #stderrStream} before calling this method.
   *
   * <p><b>Implementation detail:</b> manual benchmarking suggests {@link Process} will buffer up to
   * 60KB internally, which this method should be able to retrieve.
   */
  @CanIgnoreReturnValue
  public final CommandProcess kill() {
    killHook();
    return this;
  }

  /**
   * Sends a signal to forcibly terminate the process and returns immediately. If forcible
   * termination is not supported, this method is equivalent to {@link #kill}. Killing a process
   * that has already exited has no effect. To wait for the process to be killed, use {@code
   * killForcibly().await()}.
   *
   * <p>This method makes a best-effort attempt to read in any data already written to stdout or
   * stderr before the process is terminated. Reading output from a process as you kill it is
   * inherently racy, so if there's any output you expect to see it should be read explicitly via
   * {@link #stdoutStream}/{@link #stderrStream} before calling this method.
   *
   * <p><b>Implementation detail:</b> manual benchmarking suggests {@link Process} will buffer up to
   * 60KB internally, which this method should be able to retrieve.
   */
  @CanIgnoreReturnValue
  public final CommandProcess killForcibly() {
    killForciblyHook();
    return this;
  }

  /**
   * Returns a new {@link OutputStream} connected to the standard input of the process. If the
   * process has exited, writing to it is a noop.
   *
   * @throws IllegalStateException if the input source of the command is not the running process
   */
  public final OutputStream stdinStream() {
    // Wrap it in a ForwardingOutputStream to meet the contract of returning a new instance.
    return new ForwardingOutputStream(maybeGetStdinStream()) {};
  }

  /**
   * Returns a new {@link Writer} connected to the standard input of the process, using the
   * specified encoding. If the process has exited, writing to it is a noop.
   *
   * @throws IllegalStateException if the input source of the command is not the running process
   */
  public final Writer stdinWriter(Charset cs) {
    return new OutputStreamWriter(maybeGetStdinStream(), cs);
  }

  /**
   * Returns a new {@link Writer} connected to the standard input of the process, using UTF-8
   * encoding. If the process has exited, writing to it is a noop.
   *
   * @throws IllegalStateException if the input source of the command is not the running process
   */
  public final Writer stdinWriterUtf8() {
    return stdinWriter(UTF_8);
  }

  /** Returns a new {@link InputStream} connected to the standard output of the process. */
  public final InputStream stdoutStream() {
    return maybeGetStdoutStream().openInputStream();
  }

  /** Returns a new {@link InputStream} connected to the standard error of the process. */
  public final InputStream stderrStream() {
    return maybeGetStderrStream().openInputStream();
  }

  /** Returns a new {@link Reader} connected to the standard output of the process. */
  public final Reader stdoutReader(Charset cs) {
    return new InputStreamReader(stdoutStream(), cs);
  }

  /** Returns a new {@link Reader} connected to the standard error of the process. */
  public final Reader stderrReader(Charset cs) {
    return new InputStreamReader(stderrStream(), cs);
  }

  /**
   * Returns a new {@link Reader} connected to the standard output of the process, assuming the
   * output uses UTF-8 encoding.
   */
  public final Reader stdoutReaderUtf8() {
    return stdoutReader(UTF_8);
  }

  /**
   * Returns a new {@link Reader} connected to the standard error of the process, assuming the
   * output uses UTF-8 encoding.
   */
  public final Reader stderrReaderUtf8() {
    return stderrReader(UTF_8);
  }

  private OutputStream maybeGetStdinStream() {
    checkState(
        command.stdinSource().kind().equals(InputSource.Kind.PROCESS),
        "The process is reading stdin from %s, there is no stdin stream",
        command.stdinSource());
    return stdinStreamHook();
  }

  private CapturingOutputStream maybeGetStdoutStream() {
    checkState(
        command.stdoutSink().kind().equals(OutputSink.Kind.PROCESS_OUT)
            || command.stderrSink().kind().equals(OutputSink.Kind.PROCESS_OUT),
        "The process is writing stdout to %s and stderr to %s, there is no stdout stream",
        command.stdoutSink(),
        command.stderrSink());
    return stdoutStream;
  }

  private CapturingOutputStream maybeGetStderrStream() {
    checkState(
        command.stdoutSink().kind().equals(OutputSink.Kind.PROCESS_ERR)
            || command.stderrSink().kind().equals(OutputSink.Kind.PROCESS_ERR),
        "The process is writing stdout to %s and stderr to %s, there is no stderr stream",
        command.stdoutSink(),
        command.stderrSink());
    return stderrStream;
  }

  /**
   * Returns the native process ID of the process. The native process ID is an identification number
   * that the operating system assigns to the process. The operating system may reuse the process ID
   * after a process terminates.
   *
   * @throws UnsupportedOperationException if the platform does not support retrieving the pid.
   */
  public final long processId() {
    return processIdHook();
  }

  @Override
  public final int hashCode() {
    // All implementations must use reference equality.
    return super.hashCode();
  }

  @Override
  public final boolean equals(Object o) {
    // All implementations must use reference equality.
    return super.equals(o);
  }

  @Override
  public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("command", command)
        .add("alive", isAlive())
        .toString();
  }

  /** Notifies the command process of the exit code when the command has finished. */
  protected final void notifyComplete(int exitCode) {
    result.complete(new CommandResult(exitCode, stdoutStream, stderrStream));
  }

  /**
   * Opens an input stream to read from the input source of the process. Subclass implementations
   * <i>may<i> call this if they do not have their own logic for reading from the input source, but
   * <i>must not</i> call this when the input source kind is <code>PROCESS</code>.
   */
  protected final InputStream openStdinSourceStream() throws IOException {
    Verify.verify(
        !command.stdinSource().kind().equals(InputSource.Kind.PROCESS),
        "openStdinSourceStream() must never be called by a subclass implementation when the input"
            + "source is the process itself.");
    return stdinSourceOpener.open();
  }

  /**
   * Opens an output stream to write to the output sink of the process. Subclass implementations
   * <i>may<i> call this if they do not have their own logic for writing to the output sink, but
   * <i>must</i> call this when the output sink kind is <code>PROCESS</code>.
   */
  protected final OutputStream openStdoutSinkStream() throws IOException {
    return stdoutSinkOpener.open();
  }

  /**
   * Opens an output stream to write to the error sink of the process. Subclass implementations
   * <i>may<i> call this if they do not have their own logic for writing to the error sink, but
   * <i>must</i> call this when the error sink kind is <code>PROCESS</code>.
   */
  protected final OutputStream openStderrSinkStream() throws IOException {
    return stderrSinkOpener.open();
  }

  // Hook methods for subclasses to implement.

  /** Sends a signal to terminate the process and returns immediately. */
  protected abstract void killHook();

  /**
   * Sends a signal to forcibly terminate the process and returns immediately. If forcible
   * termination is not supported, this method is equivalent to {@link #kill}.
   */
  protected abstract void killForciblyHook();

  /**
   * Returns the native process ID of the process. The native process ID is an identification number
   * that the operating system assigns to the process. The operating system may reuse the process ID
   * after a process terminates.
   *
   * @throws UnsupportedOperationException if the platform does not support retrieving the pid.
   */
  protected abstract long processIdHook();

  /** Returns an output stream that writes to the standard input of the process. */
  protected abstract OutputStream stdinStreamHook();
}
