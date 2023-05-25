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

import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.io.Writer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Callback of a new stdout/stderr line of a running {@link Command}.
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
 * @see Command
 * @see Command#onStdout(LineCallback)
 * @see Command#onStderr(LineCallback)
 */
@FunctionalInterface
public interface LineCallback {

  /**
   * Handles a new output line.
   *
   * @return response that indicates whether to stop the command and what to be written to stdin
   * @throws LineCallbackException if fails to handle the line
   */
  Response onLine(String line) throws LineCallbackException;

  /**
   * Returns a callback that only does what {@code lineConsumer} does when invoked and does not stop
   * the command or write anything to stdin.
   */
  static LineCallback does(Consumer<String> lineConsumer) {
    return line -> {
      lineConsumer.accept(line);
      return Response.empty();
    };
  }

  /** Returns a callback that writes the new line with "\n" added to a writer when invoked. */
  static LineCallback writeTo(Writer writer) {
    return line -> {
      try {
        writer.write(line + "\n");
        return Response.empty();
      } catch (IOException e) {
        throw new LineCallbackException(
            "Failed to write", e, /* killCommand= */ false, /* stopReadingOutput= */ true);
      }
    };
  }

  /**
   * Returns a callback that writes what {@code answerFunction} answers (if presented) to stdin when
   * invoked and does not stop the command.
   */
  static LineCallback answer(Function<String, Optional<String>> answerFunction) {
    return line -> answerFunction.apply(line).map(Response::answer).orElseGet(Response::empty);
  }

  /**
   * Returns a callback that writes what {@code answerFunction} answers (if presented) and an
   * additional "\n" to stdin when invoked and does not stop the command.
   */
  static LineCallback answerLn(Function<String, Optional<String>> answerFunction) {
    return line -> answerFunction.apply(line).map(Response::answerLn).orElseGet(Response::empty);
  }

  /**
   * Returns a callback that stops the command if {@code stopPredicate} passes when invoked and does
   * not write anything to stdin.
   *
   * <p><b>NOTE</b>: If a command is stopped, getting its result by {@link
   * CommandExecutor#run(Command)}, {@link CommandExecutor#exec(Command)} and {@link
   * CommandProcess#await()} will not throw {@link CommandFailureException} no matter if the command
   * successes.
   */
  static LineCallback stopWhen(Predicate<String> stopPredicate) {
    return line -> Response.stop(stopPredicate.test(line));
  }

  /** Response of a line callback. */
  @AutoValue
  abstract class Response {

    /**
     * A response to tell command executor not to stop the command, not to write anything to stdin,
     * and not to stop reading output from stdout/stderr.
     */
    public static Response empty() {
      return notStop();
    }

    /** A response to tell command executor not to stop the command. */
    public static Response notStop() {
      return stop(false);
    }

    /**
     * A response to tell command executor to stop the command.
     *
     * <p>If a command is stopped, getting its result by {@link CommandExecutor#run(Command)},
     * {@link CommandExecutor#exec(Command)} and {@link CommandProcess#await()} will not throw
     * {@link CommandFailureException} no matter if the command successes.
     */
    public static Response stop() {
      return stop(true);
    }

    /**
     * A response to tell command executor to stop the command specified by {@code stop}.
     *
     * <p>If a command is stopped, getting its result by {@link CommandExecutor#run(Command)},
     * {@link CommandExecutor#exec(Command)} and {@link CommandProcess#await()} will not throw
     * {@link CommandFailureException} no matter if the command successes.
     */
    public static Response stop(boolean stop) {
      return builder().stop(stop).build();
    }

    /** A response to tell command executor to write {@code answer} to stdin. */
    public static Response answer(String answer) {
      return builder().answer(answer).build();
    }

    /** A response to tell command executor to write {@code answer} + "\n" to stdin. */
    public static Response answerLn(String answer) {
      return answer(answer + "\n");
    }

    /** A response to tell command executor to stop reading output from stdout/stderr. */
    public static Response stopReadingOutput() {
      return builder().stopReadingOutput(true).build();
    }

    /**
     * A response to tell command executor to stop the command specified by {@code stop}, write
     * {@code answer} to stdin, and stop reading output from stdout/stderr specified by {@code
     * stopReadingOutput}.
     *
     * <p>If a command is stopped, getting its result by {@link CommandExecutor#run(Command)},
     * {@link CommandExecutor#exec(Command)} and {@link CommandProcess#await()} will not throw
     * {@link CommandFailureException} no matter if the command successes.
     */
    public static Response of(boolean stop, @Nullable String answer, boolean stopReadingOutput) {
      return builder().stop(stop).answer(answer).stopReadingOutput(stopReadingOutput).build();
    }

    /**
     * A response to tell command executor to stop the command specified by {@code stop}, write
     * {@code answer} + "\n" to stdin, and stop reading output from stdout/stderr specified by
     * {@code stopReadingOutput}.
     *
     * <p>If a command is stopped, getting its result by {@link CommandExecutor#run(Command)},
     * {@link CommandExecutor#exec(Command)} and {@link CommandProcess#await()} will not throw
     * {@link CommandFailureException} no matter if the command successes.
     */
    public static Response ofLn(boolean stop, @Nullable String answer, boolean stopReadingOutput) {
      return of(stop, answer == null ? null : answer + "\n", stopReadingOutput);
    }

    /**
     * Returns a new response that behaves equivalently to this response, but with the specified
     * stop argument in place of the current stop argument.
     *
     * <p>The new response tells command executor to stop the command.
     *
     * <p>If a command is stopped, getting its result by {@link CommandExecutor#run(Command)},
     * {@link CommandExecutor#exec(Command)} and {@link CommandProcess#await()} will not throw
     * {@link CommandFailureException} no matter if the command successes.
     */
    public Response withStop() {
      return withStop(true);
    }

    /**
     * Returns a new response that behaves equivalently to this response, but with the specified
     * stop argument in place of the current stop argument.
     *
     * <p>The new response tells command executor to stop the command specified by {@code stop}.
     *
     * <p>If a command is stopped, getting its result by {@link CommandExecutor#run(Command)},
     * {@link CommandExecutor#exec(Command)} and {@link CommandProcess#await()} will not throw
     * {@link CommandFailureException} no matter if the command successes.
     */
    public Response withStop(boolean stop) {
      return toBuilder().stop(stop).build();
    }

    /**
     * Returns a new response that behaves equivalently to this response, but with the specified
     * stop argument in place of the current stop argument.
     *
     * <p>The new response tells command executor not to stop the command.
     */
    public Response withNotStop() {
      return withStop(false);
    }

    /**
     * Returns a new response that behaves equivalently to this response, but with the specified
     * answer argument in place of the current answer argument.
     *
     * <p>The new response tells command executor to write {@code answer} to stdin.
     */
    public Response withAnswer(String answer) {
      return toBuilder().answer(answer).build();
    }

    /**
     * Returns a new response that behaves equivalently to this response, but with the specified
     * answer argument in place of the current answer argument.
     *
     * <p>The new response tells command executor to write {@code answer} + "\n" to stdin.
     */
    public Response withAnswerLn(String answer) {
      return withAnswer(answer + "\n");
    }

    /**
     * Returns a new response that behaves equivalently to this response, but with the specified
     * stop_reading_output argument in place of the current stop_reading_output argument.
     *
     * <p>The new response tells command executor to stop reading output from stdout/stderr.
     */
    public Response withStopReadingOutput() {
      return toBuilder().stopReadingOutput(true).build();
    }

    /** Gets whether to stop the command. */
    public abstract boolean getStop();

    /** Gets the answer to write to stdin of the command. */
    public abstract Optional<String> getAnswer();

    /** Gets whether to stop reading the following output from stdout/stderr */
    public abstract boolean getStopReadingOutput();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder stop(boolean stop);

      abstract Builder answer(@Nullable String answer);

      abstract Builder stopReadingOutput(boolean stopReadingOutput);

      abstract Response build();
    }

    abstract Builder toBuilder();

    private static Builder builder() {
      return new AutoValue_LineCallback_Response.Builder().stop(false).stopReadingOutput(false);
    }
  }
}
