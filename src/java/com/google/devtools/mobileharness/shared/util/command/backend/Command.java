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

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An immutable representation of an executable command.
 *
 * <p>The newly created command has the following defaults:
 *
 * <ul>
 *   <li>{@code environment}: {@link #SYSTEM_ENVIRONMENT}
 *   <li>{@code workingDirectory}: absent, meaning use the Java process's working directory
 *   <li>{@code stdinSource}: {@link InputSource#fromProcess}
 *   <li>{@code stdoutSink}: {@link OutputSink#toProcessOut}
 *   <li>{@code stderrSink}: {@link OutputSink#toProcessErr}
 *   <li>{@code successCondition}: true if and only if the exit code is zero
 *   <li>{@code executor}: {@link #NATIVE_EXECUTOR}
 * </ul>
 *
 * <p>If the {@code stdoutSink} and {@code stderrSink} of a command are equal, a best effort is made
 * to ensure that their lines are cleanly interleaved, where no single line of output mixes stdout
 * and stderr, but clean interleaving in general cannot be guaranteed.
 */
@AutoValue
@Immutable
public abstract class Command {
  /** The default command environment: equal to {@link System#getenv()}. */
  public static final ImmutableMap<String, String> SYSTEM_ENVIRONMENT =
      ImmutableMap.copyOf(System.getenv());

  /** The default command executor: runs commands on the native OS. */
  public static final CommandExecutor NATIVE_EXECUTOR = NativeProcess.EXECUTOR;

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  Command() {}

  /** Returns a command with the specified executable and arguments. */
  public static Command command(String executable, String... args) {
    return new AutoValue_Command(
        executable,
        ImmutableList.copyOf(args),
        SYSTEM_ENVIRONMENT,
        Optional.empty(),
        InputSource.fromProcess(),
        OutputSink.toProcessOut(),
        OutputSink.toProcessErr(),
        ExitCodeSuccessCondition.create(0),
        NATIVE_EXECUTOR);
  }

  /** Returns a command with the specified executable path and arguments. */
  public static Command command(Path executable, String... args) {
    return command(sanitizePath(executable), args);
  }

  /**
   * Need to prefix paths that are just a filename with a ./, otherwise it will be treated as a
   * executable on the PATH, and fail.
   */
  private static String sanitizePath(Path executable) {
    return executable.equals(executable.getFileName())
        ? executable.getFileSystem().getPath(".").resolve(executable).toString()
        : executable.toString();
  }

  //
  // Accessor methods.
  //

  /** Returns the executable. */
  public abstract String executable();

  /** Returns the list of arguments to the executable. */
  public abstract ImmutableList<String> arguments();

  /** Returns the map of environment variables under which the executable will run. */
  public abstract ImmutableMap<String, String> environment();

  /**
   * Returns the working directory where to run the command, or absent for the working directory of
   * the current Java process.
   */
  public abstract Optional<Path> workingDirectory();

  /** Returns the source of input that will be piped to this command's standard input. */
  public abstract InputSource stdinSource();

  /** Returns the sink to which output from the command's standard output will be piped. */
  public abstract OutputSink stdoutSink();

  /** Returns the sink to which output from the command's standard error will be piped. */
  public abstract OutputSink stderrSink();

  /**
   * Returns the predicate on a command's result that determines whether a command's result is
   * successful.
   */
  public abstract SuccessCondition successCondition();

  /** Returns the executor to run the command. */
  public abstract CommandExecutor executor();

  //
  // Fluent methods.
  //

  /**
   * Returns a command that behaves equivalently to this command, but with the specified executable
   * in place of the current executable.
   */
  public final Command withExecutable(Path executable) {
    return withExecutable(sanitizePath(executable));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified executable
   * in place of the current executable.
   */
  public final Command withExecutable(String executable) {
    return new AutoValue_Command(
        executable,
        arguments(),
        environment(),
        workingDirectory(),
        stdinSource(),
        stdoutSink(),
        stderrSink(),
        successCondition(),
        executor());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified arguments
   * in place of the current arguments.
   */
  public final Command withArguments(List<String> arguments) {
    return new AutoValue_Command(
        executable(),
        ImmutableList.copyOf(arguments),
        environment(),
        workingDirectory(),
        stdinSource(),
        stdoutSink(),
        stderrSink(),
        successCondition(),
        executor());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified arguments
   * in place of the current arguments.
   */
  public final Command withArguments(String first, String... rest) {
    return withArguments(Lists.asList(first, rest));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified arguments
   * appended to the current arguments.
   */
  public final Command withArgumentsAppended(List<String> arguments) {
    return withArguments(
        ImmutableList.<String>builder().addAll(arguments()).addAll(arguments).build());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified arguments
   * appended to the current arguments.
   */
  public final Command withArgumentsAppended(String first, String... rest) {
    return withArgumentsAppended(Lists.asList(first, rest));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified environment
   * in place of the current environment.
   */
  public final Command withEnvironment(Map<String, String> environment) {
    return new AutoValue_Command(
        executable(),
        arguments(),
        ImmutableMap.copyOf(environment),
        workingDirectory(),
        stdinSource(),
        stdoutSink(),
        stderrSink(),
        successCondition(),
        executor());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified key-value
   * entries in place of the current environment.
   *
   * @throws IllegalArgumentException - if an odd number of key value arguments are given
   */
  public final Command withEnvironment(String firstKey, String firstValue, String... rest) {
    return withEnvironment(mapFromVarArgs(firstKey, firstValue, rest));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the current environment
   * updated by the specified environment.
   */
  public final Command withEnvironmentUpdated(Map<String, String> environment) {
    return withEnvironment(
        ImmutableMap.<String, String>builder()
            .putAll(environment())
            .putAll(environment)
            .buildKeepingLast());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the current environment
   * updated by the specified key-value entries.
   *
   * @throws IllegalArgumentException - if an odd number of key/value arguments are given
   */
  public final Command withEnvironmentUpdated(String firstKey, String firstValue, String... rest) {
    return withEnvironmentUpdated(mapFromVarArgs(firstKey, firstValue, rest));
  }

  private static Map<String, String> mapFromVarArgs(
      String firstKey, String firstValue, String... rest) {
    checkArgument(rest.length % 2 == 0, "odd number of key/value arguments");
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    builder.put(firstKey, firstValue);
    for (int i = 0; i < rest.length; i += 2) {
      builder.put(rest[i], rest[i + 1]);
    }
    return builder.buildOrThrow();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified working
   * directory. An absent value signifies the working directory of the current Java process.
   */
  public final Command withWorkingDirectory(Optional<Path> directory) {
    return new AutoValue_Command(
        executable(),
        arguments(),
        environment(),
        directory,
        stdinSource(),
        stdoutSink(),
        stderrSink(),
        successCondition(),
        executor());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified working
   * directory on the default FileSystem.
   */
  public final Command withWorkingDirectory(String directory) {
    return withWorkingDirectory(Paths.get(directory));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified working
   * directory.
   */
  public final Command withWorkingDirectory(Path directory) {
    return withWorkingDirectory(Optional.of(directory));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stdin
   * source.
   */
  public final Command withStdinFrom(Path file) {
    return withStdinFrom(InputSource.fromFile(file));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stdin
   * source.
   */
  public final Command withStdinFrom(String string, Charset charset) {
    return withStdinFrom(CharSource.wrap(string).asByteSource(charset));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stdin
   * source.
   */
  public final Command withStdinFrom(ByteSource byteSource) {
    return withStdinFrom(InputSource.fromStream(byteSource));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stdin
   * source.
   */
  public final Command withStdinFrom(InputSource stdinSource) {
    return new AutoValue_Command(
        executable(),
        arguments(),
        environment(),
        workingDirectory(),
        stdinSource,
        stdoutSink(),
        stderrSink(),
        successCondition(),
        executor());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stdin
   * source.
   */
  public final Command withStdinFromUtf8(String string) {
    return withStdinFrom(string, UTF_8);
  }

  /**
   * Returns a command that behaves equivalently to this command, but whose stdin source is the same
   * as the JVM's (effectively {@link System#in}).
   */
  public final Command withStdinFromJvm() {
    return withStdinFrom(InputSource.fromJvm());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stdout
   * sink.
   */
  public final Command withStdoutTo(Path file) {
    return withStdoutTo(OutputSink.toFile(file));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stdout
   * sink.
   */
  public final Command withStdoutTo(ByteSink byteSink) {
    return withStdoutTo(OutputSink.toStream(byteSink));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stdout
   * sink.
   */
  public final Command withStdoutTo(Consumer<String> lineConsumer, Charset charset) {
    return withStdoutTo(LineConsumingByteSink.create(lineConsumer, charset));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stdout
   * sink.
   */
  public final Command withStdoutTo(OutputSink outputSink) {
    return new AutoValue_Command(
        executable(),
        arguments(),
        environment(),
        workingDirectory(),
        stdinSource(),
        outputSink,
        stderrSink(),
        successCondition(),
        executor());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stdout
   * sink.
   */
  public final Command withStdoutToUtf8(Consumer<String> lineConsumer) {
    return withStdoutTo(lineConsumer, UTF_8);
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the same stdout sink as
   * the JVM's (effectively {@link System#out}).
   */
  public final Command withStdoutToJvm() {
    return withStdoutTo(OutputSink.toJvmOut());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stderr
   * sink.
   */
  public final Command withStderrTo(Path file) {
    return withStderrTo(OutputSink.toFile(file));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stderr
   * sink.
   */
  public final Command withStderrTo(ByteSink byteSink) {
    return withStderrTo(OutputSink.toStream(byteSink));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stderr
   * sink.
   */
  public final Command withStderrTo(Consumer<String> lineConsumer, Charset charset) {
    return withStderrTo(LineConsumingByteSink.create(lineConsumer, charset));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stderr
   * sink.
   */
  public final Command withStderrTo(OutputSink outputSink) {
    return new AutoValue_Command(
        executable(),
        arguments(),
        environment(),
        workingDirectory(),
        stdinSource(),
        stdoutSink(),
        outputSink,
        successCondition(),
        executor());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stderr
   * sink.
   */
  public final Command withStderrToUtf8(Consumer<String> lineConsumer) {
    return withStderrTo(lineConsumer, UTF_8);
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the same stderr sink as
   * the JVM's (effectively {@link System#err}).
   */
  public final Command withStderrToJvm() {
    return withStderrTo(OutputSink.toJvmErr());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified sink for
   * both stdout and stderr output. This method behaves identically to {@code
   * withStdoutTo(file).withStderrTo(file)}.
   */
  public final Command withStdoutAndStderrTo(Path file) {
    return withStdoutTo(file).withStderrTo(file);
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified sink for
   * both stdout and stderr output. This method behaves identically to {@code
   * withStdoutTo(byteSink).withStderrTo(byteSink)}.
   */
  public final Command withStdoutAndStderrTo(ByteSink byteSink) {
    return withStdoutTo(byteSink).withStderrTo(byteSink);
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified sink for
   * both stdout and stderr output. This method behaves identically to {@code
   * withStdoutTo(lineConsumer, charset).withStderrTo(lineConsumer, charset)}.
   */
  public final Command withStdoutAndStderrTo(Consumer<String> lineConsumer, Charset charset) {
    return withStdoutTo(lineConsumer, charset).withStderrTo(lineConsumer, charset);
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified sink for
   * both stdout and stderr output. This method behaves identically to {@code
   * withStdoutTo(outputSink).withStderrTo(outputSink)}.
   */
  public final Command withStdoutAndStderrTo(OutputSink outputSink) {
    return withStdoutTo(outputSink).withStderrTo(outputSink);
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified sink for
   * both stdout and stderr output. This method behaves identically to {@code
   * withStdoutToUtf8(lineConsumer).withStderrToUtf8(lineConsumer)}.
   */
  public final Command withStdoutAndStderrToUtf8(Consumer<String> lineConsumer) {
    return withStdoutAndStderrTo(lineConsumer, UTF_8);
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the same stdout and
   * stderr sinks as the JVM's (effectively {@link System#out} and {@link System#err}).
   */
  public final Command withStdoutAndStderrToJvm() {
    return withStdoutToJvm().withStderrToJvm();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with a success condition that
   * tests whether the result has one of the specified exit codes.
   */
  public final Command withSuccessExitCodes(int first, int... rest) {
    return withSuccessCondition(ExitCodeSuccessCondition.create(first, rest));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified success
   * condition.
   */
  public final Command withSuccessCondition(SuccessCondition condition) {
    return new AutoValue_Command(
        executable(),
        arguments(),
        environment(),
        workingDirectory(),
        stdinSource(),
        stdoutSink(),
        stderrSink(),
        condition,
        executor());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified executor in
   * place of the current executor.
   */
  public final Command withExecutor(CommandExecutor executor) {
    return new AutoValue_Command(
        executable(),
        arguments(),
        environment(),
        workingDirectory(),
        stdinSource(),
        stdoutSink(),
        stderrSink(),
        successCondition(),
        executor);
  }

  //
  // Execution methods.
  //

  /**
   * Starts the command running asynchronously.
   *
   * @throws CommandStartException - if there was an error starting the command
   */
  public final CommandProcess start() throws CommandStartException {
    logger.atFine().log("Started command: %s", this);
    return executor().start(this);
  }

  /**
   * Starts the command and blocks until it has completed.
   *
   * <p>Blocks until the command completes. It returns the command result if the result satisfies
   * the command's success condition (by default a zero exit code), and throws {@link
   * CommandFailureException} otherwise. If this thread is interrupted, this method kills the
   * process and throws {@link InterruptedException}.
   *
   * @throws CommandStartException - if there was an error starting the command
   * @throws CommandFailureException - if the result fails the command's success condition
   * @throws InterruptedException - if the execution is interrupted
   */
  @CanIgnoreReturnValue
  public final CommandResult execute()
      throws CommandStartException, CommandFailureException, InterruptedException {
    CommandProcess process = start();
    try {
      return process.await();
    } catch (InterruptedException e) {
      logger.atFine().log("Thread interrupted; killing command: %s", this);
      process.kill();
      throw e;
    }
  }

  /**
   * Invokes {@link #execute} {@link com.google.common.util.concurrent.Uninterruptibles
   * uninterruptibly}. This is a convenience method for cases where you <i>do not want to respect
   * interruption</i>, and should be used sparingly. Consider {@link #executeChecked} if you want to
   * respect interruption but will simply propagate a different exception if interrupted.
   *
   * @throws CommandStartException - if there was an error starting the command
   * @throws CommandFailureException - if the result fails the command's success condition
   */
  @CanIgnoreReturnValue
  public final CommandResult executeUninterruptibly()
      throws CommandStartException, CommandFailureException {
    return start().awaitUninterruptibly();
  }

  /**
   * Starts the command and blocks until it has completed, and uses the given factory function to
   * transform any failures into an exception type the caller prefers.
   *
   * <p>Blocks until the command completes. It returns the command result if the result satisfies
   * the command's success condition (by default a zero exit code), and throws {@code E} otherwise.
   * If this thread is interrupted, this method kills the process and throws {@link E}, preserving
   * the thread's interrupted status.
   *
   * @throws E - if there was an error starting the command, if the result fails the command's
   *     success condition, or if the execution is interrupted
   */
  @CanIgnoreReturnValue
  public final <E extends Exception> CommandResult executeChecked(Function<Exception, E> factory)
      throws E {
    try {
      return execute(); // don't delegate to awaitChecked() because interruption should still kill
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw factory.apply(e);
    } catch (CommandStartException | CommandFailureException e) {
      throw factory.apply(e);
    }
  }

  /**
   * A {@code Predicate<CommandResult>} that is annotated as {@code @Immutable}, so that {@code
   * Command} can similarly be immutable. See b/70353001.
   */
  @Immutable
  public static interface SuccessCondition extends Predicate<CommandResult> {}

  @AutoValue
  @Immutable
  abstract static class ExitCodeSuccessCondition implements SuccessCondition {
    // Hypothetically swapping to ImmutableIntArray and doing an O(n) search may be faster than
    // hashing and autoboxing, but without evidence this is cleaner and safer.
    // http://yaqs/4599585578287104
    abstract ImmutableSet<Integer> exitCodes();

    private static ExitCodeSuccessCondition create(int first, int... rest) {
      ImmutableSet.Builder<Integer> builder = ImmutableSet.<Integer>builder().add(first);
      for (int exitCode : rest) {
        builder.add(exitCode);
      }
      return new AutoValue_Command_ExitCodeSuccessCondition(builder.build());
    }

    @Override
    public boolean test(CommandResult result) {
      return exitCodes().contains(result.exitCode());
    }
  }
}
