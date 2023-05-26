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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** An implementation of {@link CommandProcess} that runs natively on the OS. */
final class NativeProcess extends CommandProcess {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final ExecutorService EXECUTOR_SERVICE =
      Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setDaemon(true).setNameFormat("native-process-%d").build());

  static final CommandExecutor EXECUTOR =
      new CommandExecutor() {
        @Override
        public CommandProcess start(Command command) throws CommandStartException {
          try {
            return new NativeProcess(command);
          } catch (IOException e) {
            throw new CommandStartException(command, e);
          }
        }
      };

  private static Redirect sourceToRedirect(InputSource source) {
    InputSource.Kind kind = source.kind();
    if (kind.equals(InputSource.Kind.JVM)) {
      return Redirect.INHERIT;
    } else if (kind.equals(InputSource.Kind.FILE)) {
      File file;
      try {
        file = source.file().toFile();
      } catch (UnsupportedOperationException e) {
        // There is no native redirect for paths not on the default filesystem.
        return Redirect.PIPE;
      }
      return Redirect.from(file);
    }
    return Redirect.PIPE;
  }

  private static Redirect sinkToRedirect(OutputSink sink, OutputSink.Kind inheritKind) {
    OutputSink.Kind kind = sink.kind();
    if (kind.equals(inheritKind)) {
      return Redirect.INHERIT;
    } else if (kind.equals(OutputSink.Kind.FILE) || kind.equals(OutputSink.Kind.FILE_APPEND)) {
      File file;
      try {
        file = sink.file().toFile();
      } catch (UnsupportedOperationException e) {
        // There is no native redirect for paths not on the default filesystem.
        return Redirect.PIPE;
      }
      return kind.equals(OutputSink.Kind.FILE) ? Redirect.to(file) : Redirect.appendTo(file);
    }
    return Redirect.PIPE;
  }

  private final Process process;
  private volatile boolean killed = false;
  private final Optional<AsyncCopier> stdinPump;
  private final Optional<AsyncCopier> stdoutPump;
  private final Optional<AsyncCopier> stderrPump;
  private final Supplier<Long> pid = Suppliers.memoize(this::fetchPid);

  private NativeProcess(Command command) throws IOException {
    super(command);

    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.command().add(command.executable());
    processBuilder.command().addAll(command.arguments());
    processBuilder.environment().clear();
    processBuilder.environment().putAll(command.environment());
    if (command.workingDirectory().isPresent()) {
      processBuilder.directory(command.workingDirectory().get().toFile());
    }

    // Special cases for I/O redirection with a native process.
    processBuilder.redirectInput(sourceToRedirect(command.stdinSource()));
    processBuilder.redirectOutput(sinkToRedirect(command.stdoutSink(), OutputSink.Kind.JVM_OUT));
    if (command.stderrSink().equals(command.stdoutSink())) {
      processBuilder.redirectErrorStream(true);
    } else {
      processBuilder.redirectError(sinkToRedirect(command.stderrSink(), OutputSink.Kind.JVM_ERR));
    }

    // Start the process and I/O pumps
    process = processBuilder.start();
    stdinPump =
        maybeStartAsyncCopy(
            processBuilder.redirectInput().type().equals(Redirect.Type.PIPE)
                && !command.stdinSource().kind().equals(InputSource.Kind.PROCESS),
            this::openStdinSourceStream,
            process::getOutputStream,
            "to stdin");
    stdoutPump =
        maybeStartAsyncCopy(
            processBuilder.redirectOutput().type().equals(Redirect.Type.PIPE),
            process::getInputStream,
            this::openStdoutSinkStream,
            "from stdout");
    stderrPump =
        maybeStartAsyncCopy(
            processBuilder.redirectError().type().equals(Redirect.Type.PIPE)
                && !processBuilder.redirectErrorStream(),
            process::getErrorStream,
            this::openStderrSinkStream,
            "from stderr");
    EXECUTOR_SERVICE.execute(this::awaitAndNotify);
  }

  @Override
  protected void killHook() {
    killed = true;
    process.destroy();
  }

  @Override
  protected void killForciblyHook() {
    killed = true;
    process.destroyForcibly();
  }

  @Override
  protected OutputStream stdinStreamHook() {
    return process.getOutputStream();
  }

  @Override
  protected long processIdHook() {
    long pid = this.pid.get();
    if (pid == -1) {
      throw new UnsupportedOperationException("Process ID not available on this platform");
    }
    return pid;
  }

  private long fetchPid() {
    try {
      Field pidField = process.getClass().getDeclaredField("pid");
      pidField.setAccessible(true);
      return pidField.getInt(process);
    } catch (ReflectiveOperationException e) {
      return -1;
    }
  }

  private Optional<AsyncCopier> maybeStartAsyncCopy(
      boolean condition,
      Opener.StreamSupplier<InputStream> in,
      Opener.StreamSupplier<OutputStream> out,
      String desc)
      throws IOException {
    return condition
        ? Optional.of(AsyncCopier.start(in.get(), out.get(), new IOExceptionLogger(desc)))
        : Optional.empty();
  }

  /**
   * This method suppresses some interruptions (similar to {@link
   * com.google.common.util.concurrent.Uninterruptibles}) to ensure all threads properly terminate.
   */
  private void awaitAndNotify() {
    try {
      int exitCode = process.waitFor();
      // The process has ended, so there is no need to wait indefinitely for the stdin pump,
      // which may in theory be blocked or simply unbounded; just force stop it now.
      if (stdinPump.isPresent()) {
        stdinPump.get().stop();
      }
      // However, wait indefinitely for the stdout and stderr pumps before returning. The
      // default case of writing to a CapturingOutputStream will never block forever; if the
      // user provides a custom output stream, the burden is on them that it doesn't either.
      if (stdoutPump.isPresent()) {
        stdoutPump.get().await();
      }
      if (stderrPump.isPresent()) {
        stderrPump.get().await();
      }
      notifyComplete(exitCode);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      // Safe to ignore since this is a daemon
      logger.atSevere().withCause(e).log("Process waiting thread interrupted");
      return;
    }
  }

  private final class IOExceptionLogger implements Consumer<IOException> {
    private final String desc;

    private IOExceptionLogger(String desc) {
      this.desc = desc;
    }

    @Override
    public void accept(IOException e) {
      // java.lang.Process closes its streams on destroy(), causing copy to raise an IO error.
      if (process.isAlive() && !killed) {
        logger.atWarning().withCause(e).log(
            "Unexpected IO error copying %s of a running process", desc);
      } else {
        logger.atFine().withCause(e).log(
            "Benign IO error copying %s of a terminated process", desc);
      }
    }
  }
}
