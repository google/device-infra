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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.errorprone.annotations.CheckReturnValue;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * An executable command.
 *
 * <p>Execute a command by {@link CommandExecutor}. Example:
 *
 * <pre class="code"><code class="java">
 * String result = commandExecutor.run(Command.of("echo", "Hello, Command!"));
 * </code></pre>
 *
 * <h3 id="timeout">Timeout</h3>
 *
 * You can specify a timeout for a command. Command executor will kill a command when it timeouts. A
 * timeout can be a fixed duration (e.g., 1 minute), a "deadline" (e.g., a command timeouts when a
 * Mobile Harness test ends, or at a fixed moment like "now + 1 hour") or their mixture. Example:
 *
 * <pre class="code"><code class="java">
 * // A command with fixed 15s timeout.
 * Command.of("ls").timeout(Duration.ofSeconds(15));
 *
 * // A command timeouts at a fixed moment.
 * Command.of("ls").timeout(instant);
 *
 * // A command timeouts when the test ends.
 * Command.of("ls").timeout(testInfo.timer());
 *
 * // A command timeouts when the test ends or 2 min has passed.
 * Command.of("ls").timeout(fixed(Duration.ofMinutes(2)).withDeadline(testInfo.timer()));
 * </code></pre>
 *
 * <p>If you don't specify a timeout for a command, the default timeout of a command executor will
 * be used. You can also set the default timeout of a command executor. By default, it is 5 min.
 *
 * <p><b>NOTE</b>: If a command timeouts, getting its result by {@link
 * CommandExecutor#run(Command)}, {@link CommandExecutor#exec(Command)} and {@link
 * CommandProcess#await()} will always throw {@link CommandTimeoutException} no matter if the
 * command successes.
 *
 * <h4 id="start-timeout">Start Timeout</h4>
 *
 * If you specify a start timeout for a command, the command will timeout additionally if it does
 * not "start successfully" for the specified duration since the command starts. By default, the
 * condition of a successfully starting is there is any line from stdout/stderr. Example:
 *
 * <pre class="code"><code class="java">
 * // A command with fixed 700ms start timeout.
 * Command.of("ls").startTimeout(Duration.ofMillis(700));
 * </code></pre>
 *
 * <h5 id="success-start-condition">Success Start Condition</h5>
 *
 * You can change the default condition of successfully starting a command. Example:
 *
 * <pre class="code"><code class="java">{@code
 * // A command with fixed 700ms start timeout and specified success start condition.
 * Command.of("ls").startTimeout(Duration.ofMillis(700))
 *     .successStartCondition(line -> line.contains("Started"));
 * }</code></pre>
 *
 * <h3>Callback</h3>
 *
 * <h4 id="line-callback">1. Stdout/stderr line callback</h4>
 *
 * You can specify a stdout/stderr line callback for a command. It will be invoked when there comes
 * a new line in stdout/stderr. Example:
 *
 * <pre class="code"><code class="java">{@code
 * // Logs each line of the command.
 * Command.of("ls").onStdout(does(logger.atInfo()::log));
 *
 * // Stops the command when there comes a new line in stdout which contains substring "SUCCESS".
 * Command.of("ls").onStdout(stopWhen(line -> line.contains("SUCCESS")));
 *
 * // Each time the command generates a new line in stderr, writes upper case of the line to stdin.
 * Command.of("ls").onStderr(answerLn(line -> Optional.of(line.toUpperCase())));
 * }</code></pre>
 *
 * <h4 id="exit-callback">2. Exit callback</h4>
 *
 * The exit callback of a command will be invoked when the command ends no matter if it succeeds or
 * fails. It is useful in asynchronous mode. Example:
 *
 * <pre class="code"><code class="java">{@code
 * // Prints the exit code of the command when it ends.
 * Command.of("ls").onExit(result -> System.out.println(result.exitCode()));
 * }</code></pre>
 *
 * <h4 id="timeout-callback">3. Timeout callback</h4>
 *
 * The exit callback of a command will be invoked when the command timeouts. Example:
 *
 * <pre class="code"><code class="java">{@code
 * // Prints a message when the command timeouts.
 * Command.of("ls").onTimeout(() -> System.out.println("Command timeouts"));
 * }</code></pre>
 *
 * <h3 id="success-exit-codes">Success Exit Codes</h3>
 *
 * You can specify success exit codes for a command. Only if they contain the exit code of the
 * command process, the command succeeds, and it fails otherwise. By default, they are {0}. Example:
 *
 * <pre class="code"><code class="java">
 * // A command with success exit codes {0, 1, 2, 10}.
 * Command.of("ls").successExitCodes(0, 1, 2, 10);
 * </code></pre>
 *
 * <h3 id="input">Input</h3>
 *
 * You can write an initial input string to stdin of the command. Example:
 *
 * <pre class="code"><code class="java">
 * // A command with initial input "Y\n".
 * Command.of("ls").inputLn("Y");
 * </code></pre>
 *
 * You can also write to stdin of the command while it is running. Example:
 *
 * <pre class="code"><code class="java">{@code
 * // Uses answer() in stdout/stderr line callback.
 * commandExecutor.exec(Command.of("ls").onStdout(answerLn(line -> "Y")));
 *
 * // Writes to stdin of the command process directly while the command is running.
 * CommandProcess process = commandExecutor.start(Command.of("ls"));
 * Writer stdin = process.stdin();
 * stdin.write("Y\n");
 * stdin.flush();
 * }</code></pre>
 *
 * <h3 id="work-directory">Work Directory</h3>
 *
 * You can specify work directory of a command. If it is not specified and the command executor has
 * a default work directory, the default directory will be used. Example:
 *
 * <pre class="code"><code class="java">
 * Command.of("ls").workDir(path);
 * </code></pre>
 *
 * <h3 id="environment">Environment</h3>
 *
 * You can set environment variables for a command. The final environment of a command is the union
 * of 3 sets and the following one will override the previous one:
 *
 * <ol>
 *   <li>Environment of the current process.
 *   <li>Base environment defined in command executor.
 *   <li>Extra environment defined in command.
 * </ol>
 *
 * For example, if system environment is {a=1, b=2}, the following command will have environment
 * {a=1, b=3, c=5, d=6}:
 *
 * <pre class="code"><code class="java">
 * commandExecutor.setBaseEnvironment(ImmutableMap.of("b", "3", "c", "4"));
 *
 * commandExecutor.exec(Command.of("ls").extraEnv("c", "5", "d", "6"));
 * </code></pre>
 *
 * <h3 id="redirect-stderr">Redirect Stderr to Stdout</h3>
 *
 * You can redirect stderr of a command to its stdout. Example:
 *
 * <pre class="code"><code class="java">
 * // Redirects stderr of the command to its stdout.
 * Command.of("ls").redirectStderr(true);
 * </code></pre>
 *
 * If this option is not specified in a command, the default value of a command executor will be
 * used. You can also set the default value of a command executor. By default, it is <b>true</b>.
 *
 * <h3>Command Template</h3>
 *
 * This is an immutable class, so a Command can be used as a command template. Example:
 *
 * <pre class="code"><code class="java">
 * Command template = Command.of("adb", "-s", "device_id").successExitCodes(0, 1, 4);
 *
 * // Same as Command.of("adb", "-s", "device_id", "shell", "pwd").successExitCodes(0, 1, 4).
 * Command command1 = template.argsAppended("shell", "pwd");
 *
 * // Same as Command.of("adb", "-s", "device_id", "logcat").successExitCodes(0, 1, 4).
 * Command command2 = template.argsAppended("logcat");
 * </code></pre>
 *
 * <h3>Equivalence</h3>
 *
 * Two command equals if and only if they have same executable and arguments.
 *
 * <h3 id="memory">Memory Usage</h3>
 *
 * If you only need stdout/stderr in callbacks rather than in result, you can use {@link
 * #needStdoutInResult(boolean)} and {@link #needStderrInResult(boolean)} to tell the command
 * executor not to save stdout/stderr while executing to save memory when the command result size is
 * extremely large.
 *
 * @see CommandExecutor
 * @see LineCallback
 * @see Timeout
 */
@AutoValue
public abstract class Command {

  public static final int DEFAULT_SUCCESS_EXIT_CODE = 0;

  public static final Predicate<String> DEFAULT_SUCCESS_START_CONDITION = line -> true;

  public static final boolean DEFAULT_NEED_STDOUT_IN_RESULT = true;

  public static final boolean DEFAULT_NEED_STDERR_IN_RESULT = true;

  public static final boolean DEFAULT_SHOW_FULL_RESULT_IN_EXCEPTION = false;

  /**
   * Constructs a command. The first element is the executable and the rest are the arguments.
   *
   * <p>The length of the array should be at least 1.
   */
  public static Command of(String... command) {
    return of(command[0], Arrays.stream(command).skip(1L).collect(Collectors.toList()));
  }

  /** Constructs a command. */
  public static Command of(String executable, List<String> arguments) {
    return newBuilder().executable(executable).arguments(arguments).build();
  }

  /**
   * Constructs a command. The first element is the executable and the rest are the arguments.
   *
   * <p>The size of the list should be at least 1.
   */
  public static Command of(List<String> command) {
    return newBuilder()
        .executable(command.get(/* index= */ 0))
        .arguments(command.subList(/* fromIndex= */ 1, command.size()))
        .build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified executable
   * in place of the current executable.
   */
  @CheckReturnValue
  public Command executable(String executable) {
    return toBuilder().executable(executable).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified arguments
   * in place of the current arguments.
   */
  @CheckReturnValue
  public Command args(String... arguments) {
    return args(ImmutableList.copyOf(arguments));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified arguments
   * in place of the current arguments.
   */
  @CheckReturnValue
  public Command args(List<String> arguments) {
    return toBuilder().arguments(arguments).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified arguments
   * appended to the current arguments.
   */
  @CheckReturnValue
  public Command argsAppended(String... appendedArguments) {
    return argsAppended(ImmutableList.copyOf(appendedArguments));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified arguments
   * appended to the current arguments.
   */
  @CheckReturnValue
  public Command argsAppended(List<String> appendedArguments) {
    return args(
        ImmutableList.<String>builderWithExpectedSize(
                getArguments().size() + appendedArguments.size())
            .addAll(getArguments())
            .addAll(appendedArguments)
            .build());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified timeout in
   * place of the current timeout.
   *
   * <p>The command will be killed if it <a href="#timeout">timeouts</a> while running.
   *
   * <p><b>WARNING</b>: When a command timeouts, the command executor kills it by calling {@link
   * Process#destroy()} of the process of the command, which sends {@code SIGTERM} to the process.
   * {@link CommandExecutor#run(Command)}, {@link CommandExecutor#exec(Command)} and {@link
   * CommandProcess#await()} of the command will return <b>AFTER</b> the process handles the {@code
   * SIGTERM} signal and <b>EXITS</b>. It means if the process does not handle the signal or does
   * not handle the signal in time, these methods will <b>NOT</b> return, even when the command
   * timeouts.
   *
   * <p><b>NOTE</b>: If a command timeouts, getting its result by {@link
   * CommandExecutor#run(Command)}, {@link CommandExecutor#exec(Command)} and {@link
   * CommandProcess#await()} will always throw {@link CommandTimeoutException} no matter if the
   * command successes.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">
   * command.timeout(fixed(Duration.ofSeconds(10)));
   * command.timeout(deadline(instant));
   * command.timeout(deadline(testInfo.timer()));
   * command.timeout(fixed(Duration.ofSeconds(10)).withDeadline(testInfo.timer()));
   * </code></pre>
   *
   * <p>If it is not specified, the default value in the command executor will be used.
   *
   * @see <a href="#timeout">Command Timeout</a>
   * @see Timeout
   */
  @CheckReturnValue
  public Command timeout(Timeout timeout) {
    return toBuilder().timeout(timeout).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified timeout in
   * place of the current timeout.
   *
   * <p>The command will be killed after the deadline. (See {@link #timeout(Timeout)} for the
   * <b>WARNING</b> of the kill behavior.)
   *
   * <p>Short for {@linkplain #timeout(Timeout) timeout}{@code (Timeout.deadline(deadline))}.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">
   * command.timeout(testInfo.timer());
   * </code></pre>
   *
   * <p>If it is not specified, the default value in the command executor will be used.
   *
   * @see <a href="#timeout">Command Timeout</a>
   * @see Timeout
   */
  @CheckReturnValue
  public Command timeout(CountDownTimer deadline) {
    return timeout(Timeout.deadline(deadline));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified timeout in
   * place of the current timeout.
   *
   * <p>The command will be killed after the deadline. (See {@link #timeout(Timeout)} for the
   * <b>WARNING</b> of the kill behavior.)
   *
   * <p>Short for {@linkplain #timeout(Timeout) timeout}{@code (Timeout.deadline(deadline))}.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">
   * command.timeout(Instant.now().plus(Duration.ofMillis(900)));
   * </code></pre>
   *
   * <p>If it is not specified, the default value in the command executor will be used.
   *
   * @see <a href="#timeout">Command Timeout</a>
   * @see Timeout
   */
  @CheckReturnValue
  public Command timeout(Instant deadline) {
    return timeout(Timeout.deadline(deadline));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified timeout in
   * place of the current timeout.
   *
   * <p>The command will be killed after it has been running for the specified duration. (See {@link
   * #timeout(Timeout)} for the <b>WARNING</b> of the kill behavior.)
   *
   * <p>Short for {@linkplain #timeout(Timeout) timeout}{@code (Timeout.fixed(timeout))}.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">
   * command.timeout(Duration.ofMillis(500));
   * </code></pre>
   *
   * <p>If it is not specified, the default value in the command executor will be used.
   *
   * @see <a href="#timeout">Command Timeout</a>
   * @see Timeout
   */
  @CheckReturnValue
  public Command timeout(Duration timeout) {
    return timeout(Timeout.fixed(timeout));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified start
   * timeout in place of the current start timeout.
   *
   * <p>The command will be killed if it does not "start successfully" for the specified duration
   * since the command starts. By default, the condition of a successfully starting is there is any
   * line from stdout/stderr. (See {@link #timeout(Timeout)} for the <b>WARNING</b> of the kill
   * behavior.)
   *
   * <p><b>NOTE</b>: If a command timeouts, getting its result by {@link
   * CommandExecutor#run(Command)}, {@link CommandExecutor#exec(Command)} and {@link
   * CommandProcess#await()} will always throw {@link CommandTimeoutException} no matter if the
   * command successes.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">
   * command.startTimeout(fixed(Duration.ofSeconds(10)));
   * command.startTimeout(deadline(instant));
   * command.startTimeout(deadline(testInfo.timer()));
   * command.startTimeout(fixed(Duration.ofMinutes(10)).withDeadline(testInfo.timer()));
   * </code></pre>
   *
   * <p>You can specify the success start condition by {@link #successStartCondition(Predicate)}.
   *
   * @see <a href="#start-timeout">Command Start Timeout</a>
   * @see <a href="#success-start-condition">Command Success Start Condition</a>
   * @see Timeout
   * @see #successStartCondition(Predicate)
   */
  @CheckReturnValue
  public Command startTimeout(Timeout startTimeout) {
    return toBuilder().startTimeout(startTimeout).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified start
   * timeout in place of the current start timeout.
   *
   * <p>The command will be killed if it does not "start successfully" before the specified
   * deadline. By default, the condition of a successfully starting is there is any line from
   * stdout/stderr. (See {@link #timeout(Timeout)} for the <b>WARNING</b> of the kill behavior.)
   *
   * <p>Short for {@linkplain #startTimeout(Timeout) startTimeout}{@code
   * (Timeout.deadline(startDeadline))}.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">
   * command.startTimeout(testInfo.timer());
   * </code></pre>
   *
   * <p>You can specify the success start condition by {@link #successStartCondition(Predicate)}.
   *
   * @see <a href="#start-timeout">Command Start Timeout</a>
   * @see <a href="#success-start-condition">Command Success Start Condition</a>
   * @see Timeout
   * @see #successStartCondition(Predicate)
   */
  @CheckReturnValue
  public Command startTimeout(CountDownTimer startDeadline) {
    return startTimeout(Timeout.deadline(startDeadline));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified start
   * timeout in place of the current start timeout.
   *
   * <p>The command will be killed if it does not "start successfully" before the specified
   * deadline. By default, the condition of a successfully starting is there is any line from
   * stdout/stderr. (See {@link #timeout(Timeout)} for the <b>WARNING</b> of the kill behavior.)
   *
   * <p>Short for {@linkplain #startTimeout(Timeout) startTimeout}{@code
   * (Timeout.deadline(startDeadline))}.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">
   * command.startTimeout(Instant.now().plus(Duration.ofMillis(900)));
   * </code></pre>
   *
   * <p>You can specify the success start condition by {@link #successStartCondition(Predicate)}.
   *
   * @see <a href="#start-timeout">Command Start Timeout</a>
   * @see <a href="#success-start-condition">Command Success Start Condition</a>
   * @see Timeout
   * @see #successStartCondition(Predicate)
   */
  @CheckReturnValue
  public Command startTimeout(Instant startDeadline) {
    return startTimeout(Timeout.deadline(startDeadline));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified start
   * timeout in place of the current start timeout.
   *
   * <p>The command will be killed if it does not "start successfully" for the specified duration
   * since the command starts. By default, the condition of a successfully starting is there is any
   * line from stdout/stderr. (See {@link #timeout(Timeout)} for the <b>WARNING</b> of the kill
   * behavior.)
   *
   * <p>Short for {@linkplain #startTimeout(Timeout) startTimeout}{@code
   * (Timeout.fixed(startTimeout))}.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">
   * command.startTimeout(Duration.ofMillis(900));
   * </code></pre>
   *
   * <p>You can specify the success start condition by {@link #successStartCondition(Predicate)}.
   *
   * @see <a href="#start-timeout">Command Start Timeout</a>
   * @see <a href="#success-start-condition">Command Success Start Condition</a>
   * @see Timeout
   * @see #successStartCondition(Predicate)
   */
  @CheckReturnValue
  public Command startTimeout(Duration startTimeout) {
    return startTimeout(Timeout.fixed(startTimeout));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified success
   * start condition in place of the current success start condition.
   *
   * <p>It is only meaningful when {@linkplain #startTimeout(Timeout) start timeout} is specified.
   *
   * <p>Be default, it is {@link Command#DEFAULT_SUCCESS_START_CONDITION}.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">{@code
   * command.startTimeout(Duration.ofMillis(700))
   *     .successStartCondition(line -> line.contains("Started"));
   * }</code></pre>
   *
   * <p><b>*WARNING*</b>: This is intended for lightweight check work, for example, specified
   * substring detection. A long-lasting invocation may cause stdout/stderr callbacks and the
   * command process to be blocked. If your task does anything heavier consider, please manually
   * implement your own start timeout logic with {@link CommandExecutor#start(Command)} and {@link
   * CommandProcess#kill()}.
   *
   * @param successStartCondition the success start condition whose {@linkplain
   *     Predicate#test(Object) test(String)} will be invoked when there comes a new line from
   *     stdout/stderr and the command starts successfully if its return value is {@code true}
   * @see <a href="#start-timeout">Command Start Timeout</a>
   * @see <a href="#success-start-condition">Command Success Start Condition</a>
   * @see #startTimeout(Timeout)
   */
  @CheckReturnValue
  public Command successStartCondition(Predicate<String> successStartCondition) {
    return toBuilder().successStartCondition(successStartCondition).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified timeout
   * callback in place of the current timeout callback.
   *
   * <p>The callback will be invoked when the command {@linkplain #timeout(Timeout) timeouts} or
   * {@linkplain #startTimeout(Timeout) start-timeouts}.
   *
   * <p>Note that the callback will NOT block {@link CommandExecutor#exec(Command)} and {@link
   * CommandProcess#await()}.
   *
   * @see <a href="#timeout-callback">Command Timeout Callback</a>
   * @see <a href="#timeout">Command Timeout</a>
   * @see <a href="#start-timeout">Command Start Timeout</a>
   * @see #timeout(Timeout)
   * @see #startTimeout(Timeout)
   */
  @CheckReturnValue
  public Command onTimeout(Runnable timeoutCallback) {
    return toBuilder().timeoutCallback(timeoutCallback).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stdout line
   * callback in place of the current stdout line callback.
   *
   * <p>The callback will be invoked when there comes a new line from stdout.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">{@code
   * command.onStdout(does(line -> System.out.println("Line: " + line)));
   * command.onStdout(writeTo(fileWriter));
   * command.onStdout(stopWhen(line -> line.contains("Successful")));
   * command.onStdout(
   *     answerLn(line -> line.contains("Confirm") ? Optional.of("Y") : Optional.empty()));
   * }</code></pre>
   *
   * <p><b>*WARNING*</b>: This is intended for lightweight callback work like logging. A
   * long-lasting invocation may cause the command process or its start timeout detection to be
   * blocked. If your task does anything heavier consider, please use {@link
   * java.util.concurrent.Executor} or {@link java.util.concurrent.BlockingQueue} to asynchronously
   * execute your callback logic and use {@link CommandExecutor#start(Command)}, {@link
   * CommandProcess#stdinWriter()} and {@link CommandProcess#kill()} if necessary.
   *
   * @see <a href="#line-callback">Command Line Callback</a>
   * @see LineCallback
   */
  @CheckReturnValue
  public Command onStdout(LineCallback stdoutLineCallback) {
    return toBuilder().stdoutLineCallback(stdoutLineCallback).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified stderr line
   * callback in place of the current stderr line callback.
   *
   * <p>The callback will be invoked when there comes a new line from stderr.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">{@code
   * command.onStderr(does(line -> System.out.println("Line: " + line)));
   * command.onStderr(writeTo(fileWriter));
   * command.onStderr(stopWhen(line -> line.contains("Successful")));
   * command.onStderr(
   *     answerLn(line -> line.contains("Confirm") ? Optional.of("Y") : Optional.empty()));
   * }</code></pre>
   *
   * <p><b>*WARNING*</b>: This is intended for lightweight callback work like logging. A
   * long-lasting invocation may cause the command process or its start timeout detection to be
   * blocked. If your task does anything heavier consider, please use {@link
   * java.util.concurrent.Executor} or {@link java.util.concurrent.BlockingQueue} to asynchronously
   * execute your callback logic and use {@link CommandExecutor#start(Command)}, {@link
   * CommandProcess#stdinWriter()} and {@link CommandProcess#kill()} if necessary.
   *
   * @see <a href="#line-callback">Command Line Callback</a>
   * @see LineCallback
   */
  @CheckReturnValue
  public Command onStderr(LineCallback stderrLineCallback) {
    return toBuilder().stderrLineCallback(stderrLineCallback).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified exit
   * callback in place of the current exit callback.
   *
   * <p>The callback will be invoked when the command exits.
   *
   * <p>Note that the callback will NOT block {@link CommandExecutor#exec(Command)} and {@link
   * CommandProcess#await()}.
   *
   * @see <a href="#exit-callback">Command Exit Callback</a>
   * @see CommandResult
   */
  @CheckReturnValue
  public Command onExit(Consumer<CommandResult> resultConsumer) {
    return toBuilder().exitCallback(resultConsumer).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified success
   * exit codes in place of the current success exit codes.
   *
   * <p>Only if they contain the exit code of the command process, the command succeeds, and it
   * fails otherwise.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">
   * command.successExitCodes(0, 1, 2, 10);
   * </code></pre>
   *
   * <p>By default, it is {@linkplain Command#DEFAULT_SUCCESS_EXIT_CODE 0}.
   *
   * @see <a href="#success-exit-codes">Command Success Exit Codes</a>
   */
  @CheckReturnValue
  public Command successExitCodes(int first, int... rest) {
    return successExitCodes(
        ImmutableSet.<Integer>builder().add(first).addAll(Ints.asList(rest)).build());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified success
   * exit codes in place of the current success exit codes.
   *
   * <p>Only if they contain the exit code of the command process, the command succeeds, and it
   * fails otherwise.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">
   * command.successExitCodes(ImmutableSet.of(0, 1, 2, 10));
   * </code></pre>
   *
   * <p>By default, it is {@linkplain Command#DEFAULT_SUCCESS_EXIT_CODE 0}.
   *
   * @see <a href="#success-exit-codes">Command Success Exit Codes</a>
   */
  @CheckReturnValue
  public Command successExitCodes(Set<Integer> successExitCodes) {
    return toBuilder().successExitCodes(successExitCodes).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified initial
   * input in place of the current initial input.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">
   * command.input("Y\n");
   * </code></pre>
   *
   * @see <a href="#input">Command Input</a>
   */
  @CheckReturnValue
  public Command input(String input) {
    return toBuilder().input(input).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified initial
   * input in place of the current initial input.
   *
   * <p>Short for {@linkplain #input(String) input}{@code (input + "\n")}.
   *
   * <p>Example:
   *
   * <pre class="code"><code class="java">
   * command.inputLn("Y");
   * </code></pre>
   *
   * @see <a href="#input">Command Input</a>
   */
  @CheckReturnValue
  public Command inputLn(String input) {
    return input(input + "\n");
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified work
   * directory in place of the current work directory.
   *
   * <p>If it is not specified, the default value in the command executor will be used if it exists.
   *
   * @see <a href="#work-directory">Command Work Directory</a>
   */
  @CheckReturnValue
  public Command workDir(Path workDirectory) {
    return toBuilder().workDirectory(workDirectory).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified work
   * directory in place of the current work directory.
   *
   * <p>If it is not specified, the default value in the command executor will be used if it exists.
   *
   * <p>Short for {@linkplain #workDir(Path) workDir}{@code (Paths.get(workDirectory))}.
   *
   * @see <a href="#work-directory">Command Work Directory</a>
   */
  @CheckReturnValue
  public Command workDir(String workDirectory) {
    return workDir(Paths.get(workDirectory));
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified extra
   * environment in place of the current extra environment.
   *
   * <p>The environment of this command is the union of 3 sets and the following one will override
   * the previous one:
   *
   * <ol>
   *   <li>Environment of the current process.
   *   <li>Base environment defined in command executor.
   *   <li>Extra environment defined in command.
   * </ol>
   *
   * @see <a href="#environment">Command Environment</a>
   */
  @CheckReturnValue
  public Command extraEnv(String k1, String v1, String... rest) {
    checkArgument(
        rest.length % 2 == 0, "Odd number of key/value arguments: %s", Arrays.toString(rest));
    ImmutableMap.Builder<String, String> extraEnvironment =
        ImmutableMap.<String, String>builder().put(k1, v1);
    for (int i = 0; i < rest.length; i += 2) {
      extraEnvironment.put(rest[i], rest[i + 1]);
    }
    return extraEnv(extraEnvironment.buildOrThrow());
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified extra
   * environment in place of the current extra environment.
   *
   * <p>The environment of this command is the union of 3 sets and the following one will override
   * the previous one:
   *
   * <ol>
   *   <li>Environment of the current process.
   *   <li>Base environment defined in command executor.
   *   <li>Extra environment defined in command.
   * </ol>
   *
   * @see <a href="#environment">Command Environment</a>
   */
  @CheckReturnValue
  public Command extraEnv(Map<String, String> extraEnvironment) {
    return toBuilder().extraEnvironment(extraEnvironment).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified redirect
   * stderr in place of the current redirect stderr.
   *
   * <p>If it is {@code true}, stderr will be merged to stdout.
   *
   * <p>If it is not specified, the default value in the command executor will be used. By default,
   * it is {@code true}.
   *
   * @see <a href="#redirect-stderr">Command Stderr Redirect</a>
   */
  @CheckReturnValue
  public Command redirectStderr(boolean redirectStderr) {
    return toBuilder().redirectStderr(redirectStderr).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified
   * needStdoutInResult in place of the current needStdoutInResult.
   *
   * <p>If it is {@code false}, {@link CommandResult#stdout()} of this command will always be empty.
   * It is useful when you don't need stdout in the result and its size may be extremely large.
   *
   * <p>By default, it is {@linkplain Command#DEFAULT_NEED_STDOUT_IN_RESULT true}.
   *
   * @see <a href="#memory">Command Execution Memory Usage</a>
   */
  @CheckReturnValue
  public Command needStdoutInResult(boolean needStdoutInResult) {
    return toBuilder().needStdoutInResult(needStdoutInResult).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified
   * needStderrInResult in place of the current needStderrInResult.
   *
   * <p>If it is {@code false}, {@link CommandResult#stderr()} of this command will always be empty.
   * It is useful when you don't need stderr in the result and its size may be extremely large.
   *
   * <p>By default, it is {@linkplain Command#DEFAULT_NEED_STDERR_IN_RESULT true}.
   *
   * @see <a href="#memory">Command Execution Memory Usage</a>
   */
  @CheckReturnValue
  public Command needStderrInResult(boolean needStderrInResult) {
    return toBuilder().needStderrInResult(needStderrInResult).build();
  }

  /**
   * Returns a command that behaves equivalently to this command, but with the specified
   * showFullResultInException in place of the current showFullResultInException.
   *
   * <p>If it is {@code true}, the message of {@link CommandExecutionException} (e.g., {@link
   * CommandFailureException} and {@link CommandTimeoutException}) of this command (if any) will
   * contain full command result. Otherwise, it will only contain a truncated command result
   * summary.
   *
   * <p>By default, it is {@linkplain Command#DEFAULT_SHOW_FULL_RESULT_IN_EXCEPTION false}.
   *
   * <p>Note that you still need to ensure {@link #needStdoutInResult(boolean)} and {@link
   * #needStderrInResult(boolean)} are {@code true} (by default they are {@code true}) if you need
   * command result in exceptions.
   */
  @CheckReturnValue
  public Command showFullResultInException(boolean showFullResultInException) {
    return toBuilder().showFullResultInException(showFullResultInException).build();
  }

  /** See {@link #of(String...)} and {@link #executable(String)}. */
  public abstract String getExecutable();

  /**
   * See {@link #of(String...)} and {@link #args(String...)} and {@link #argsAppended(String...)}.
   */
  public abstract ImmutableList<String> getArguments();

  /** See {@link #timeout(Timeout)}. */
  public abstract Optional<Timeout> getTimeout();

  /** See {@link #startTimeout(Timeout)}. */
  public abstract Optional<Timeout> getStartTimeout();

  /** See {@link #successStartCondition(Predicate)}. */
  public abstract Predicate<String> getSuccessStartCondition();

  /** See {@link #onTimeout(Runnable)}. */
  public abstract Optional<Runnable> getTimeoutCallback();

  /** See {@link #onStdout(LineCallback)}. */
  public abstract Optional<LineCallback> getStdoutLineCallback();

  /** See {@link #onStderr(LineCallback)}. */
  public abstract Optional<LineCallback> getStderrLineCallback();

  /** See {@link #onExit(Consumer)}. */
  public abstract Optional<Consumer<CommandResult>> getExitCallback();

  /** See {@link #successExitCodes(int, int...)}. */
  public abstract ImmutableSet<Integer> getSuccessExitCodes();

  /** See {@link #input(String)} and {@link #inputLn(String)}. */
  public abstract Optional<String> getInput();

  /** See {@link #workDir(Path)}. */
  public abstract Optional<Path> getWorkDirectory();

  /** See {@link #extraEnv(String, String, String...)}. */
  public abstract ImmutableMap<String, String> getExtraEnvironment();

  /** See {@link #redirectStderr(boolean)}. */
  public abstract Optional<Boolean> getRedirectStderr();

  /** See {@link #needStdoutInResult(boolean)}. */
  public abstract boolean getNeedStdoutInResult();

  /** See {@link #needStderrInResult(boolean)}. */
  public abstract boolean getNeedStderrInResult();

  /** See {@link #showFullResultInException(boolean)}. */
  public abstract boolean getShowFullResultInException();

  /** Returns the command including its executable and arguments. */
  @Memoized
  public ImmutableList<String> getCommand() {
    return ImmutableList.<String>builder().add(getExecutable()).addAll(getArguments()).build();
  }

  /**
   * Prints this command including its executable and arguments.
   *
   * <p>{@inheritDoc}
   */
  @Memoized
  @Override
  public String toString() {
    return String.join(" ", getCommand());
  }

  /**
   * Two command equals if and only if they have same executable and arguments.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public final boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Command)) {
      return false;
    }
    Command other = (Command) o;
    return getExecutable().equals(other.getExecutable())
        && getArguments().equals(other.getArguments());
  }

  @Memoized
  @Override
  public int hashCode() {
    return Objects.hash(getExecutable(), getArguments());
  }

  abstract Builder toBuilder();

  @AutoValue.Builder
  abstract static class Builder {

    abstract Builder executable(String executable);

    abstract Builder arguments(List<String> arguments);

    abstract Builder timeout(Timeout timeout);

    abstract Builder startTimeout(Timeout startTimeout);

    abstract Builder successStartCondition(Predicate<String> successStartCondition);

    abstract Builder timeoutCallback(Runnable timeoutCallback);

    abstract Builder stdoutLineCallback(LineCallback stdoutLineCallback);

    abstract Builder stderrLineCallback(LineCallback stderrLineCallback);

    abstract Builder exitCallback(Consumer<CommandResult> exitCallback);

    abstract Builder successExitCodes(Set<Integer> getSuccessExitCodes);

    abstract Builder input(String input);

    abstract Builder workDirectory(Path workDirectory);

    abstract Builder extraEnvironment(Map<String, String> extraEnvironment);

    abstract Builder redirectStderr(boolean redirectStderr);

    abstract Builder needStdoutInResult(boolean needStdoutInResult);

    abstract Builder needStderrInResult(boolean needStderrInResult);

    abstract Builder showFullResultInException(boolean showFullResultInException);

    abstract Command build();
  }

  private static Builder newBuilder() {
    return new AutoValue_Command.Builder()
        .arguments(ImmutableList.of())
        .successStartCondition(DEFAULT_SUCCESS_START_CONDITION)
        .successExitCodes(ImmutableSet.of(DEFAULT_SUCCESS_EXIT_CODE))
        .extraEnvironment(ImmutableMap.of())
        .needStdoutInResult(DEFAULT_NEED_STDOUT_IN_RESULT)
        .needStderrInResult(DEFAULT_NEED_STDERR_IN_RESULT)
        .showFullResultInException(DEFAULT_SHOW_FULL_RESULT_IN_EXCEPTION);
  }
}
