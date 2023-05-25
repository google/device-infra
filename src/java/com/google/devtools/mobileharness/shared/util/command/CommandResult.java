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

import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import java.util.Objects;
import java.util.function.Supplier;

/** Result of a command. */
public class CommandResult {

  /** Maximum length of the output for purposes of exception messages. */
  private static final int MAX_OUTPUT_LENGTH_IN_MSG = 2000;

  /**
   * Returns tail-truncated <code>output</code> with the last one and only one line terminator at
   * the end removed, or <code>output</code> if no line terminator found at the end. Line terminator
   * could be <code>\r</code>, <code>\n</code>, <code>\r\n</code>.
   */
  public static String removeTrailingLineTerminator(String output) {
    if (Strings.isNullOrEmpty(output)) {
      return output;
    }
    int len = output.length();
    if (output.endsWith("\r\n")) {
      return output.substring(0, len - 2);
    } else if (output.endsWith("\r") || output.endsWith("\n")) {
      return output.substring(0, len - 1);
    } else {
      return output;
    }
  }

  private final String stdout;
  private final String stderr;
  private final int exitCode;
  private final boolean isTimeout;
  private final boolean isStopped;

  private final Supplier<String> toStringSupplier;
  private final Supplier<String> toStringWithoutTruncationSupplier;
  private final Supplier<String> stdoutWithoutTrailingLineTerminatorSupplier;
  private final Supplier<String> stderrWithoutTrailingLineTerminatorSupplier;

  /** Do NOT make it public. In unit tests, please use {@code FakeCommandResult} instead. */
  CommandResult(String stdout, String stderr, int exitCode, boolean isTimeout, boolean isStopped) {
    this.stdout = stdout;
    this.stderr = stderr;
    this.exitCode = exitCode;
    this.isTimeout = isTimeout;
    this.isStopped = isStopped;

    toStringSupplier =
        Suppliers.memoize(
            () ->
                String.format(
                    "code=%d, out=[%s], err=[%s]",
                    exitCode(), truncateOutput(stdout()), truncateOutput(stderr())));
    toStringWithoutTruncationSupplier =
        Suppliers.memoize(
            () -> String.format("code=%d, out=[%s], err=[%s]", exitCode(), stdout(), stderr()));
    stdoutWithoutTrailingLineTerminatorSupplier =
        Suppliers.memoize(() -> removeTrailingLineTerminator(stdout()));
    stderrWithoutTrailingLineTerminatorSupplier =
        Suppliers.memoize(() -> removeTrailingLineTerminator(stderr()));
  }

  /**
   * The stdout of the command.
   *
   * <p>Note that trailing "\n" in stdout will <b>NOT</b> be omitted if any. For example, <code>
   * exec(Command.of("echo", "Hello")).stdout()</code> will return "Hello\n" rather than "Hello". If
   * you want a result without trailing line terminator, please use {@link
   * #stdoutWithoutTrailingLineTerminator()} instead.
   */
  public String stdout() {
    return stdout;
  }

  /**
   * The stderr of the command.
   *
   * <p>Note that the trailing "\n" in stderr will <b>NOT</b> be omitted if any. If * you want a
   * result without trailing line terminator, please use {@link
   * #stderrWithoutTrailingLineTerminator()} instead.
   */
  public String stderr() {
    return stderr;
  }

  public int exitCode() {
    return exitCode;
  }

  /** Returns whether the command is timeout */
  public boolean isTimeout() {
    return isTimeout;
  }

  /**
   * Returns whether {@link CommandProcess#stop()} has been invoked or there is any {@linkplain
   * LineCallback.Response response} of {@link LineCallback} in {@link
   * Command#onStdout(LineCallback)} or {@link Command#onStderr(LineCallback)} which has stopped the
   * command.
   *
   * @see CommandProcess#stop()
   */
  public boolean isStopped() {
    return isStopped;
  }

  @Override
  public String toString() {
    return toStringSupplier.get();
  }

  public String toStringWithoutTruncation() {
    return toStringWithoutTruncationSupplier.get();
  }

  /**
   * Stdout of the command without the trailing line terminator ('\n' or '\r' or "\r\n").
   *
   * <p>For example, if {@link #stdout()} returns "a\nb\n\n", this method will return "a\nb\n".
   *
   * @see #removeTrailingLineTerminator
   */
  public String stdoutWithoutTrailingLineTerminator() {
    return stdoutWithoutTrailingLineTerminatorSupplier.get();
  }

  /**
   * Stderr of the command without the trailing line terminator ('\n' or '\r' or "\r\n").
   *
   * <p>For example, if {@link #stderr()} returns "a\nb\n\n", this method will return "a\nb\n".
   *
   * @see #removeTrailingLineTerminator
   */
  public String stderrWithoutTrailingLineTerminator() {
    return stderrWithoutTrailingLineTerminatorSupplier.get();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CommandResult)) {
      return false;
    }
    CommandResult that = (CommandResult) o;
    return exitCode == that.exitCode && stdout.equals(that.stdout) && stderr.equals(that.stderr);
  }

  @Override
  public int hashCode() {
    return Objects.hash(stdout, stderr, exitCode);
  }

  /**
   * Returns a potentially truncated version of the provided command output.
   *
   * <p>If the output is already shorter than the provided maximum length, then the output is
   * returned as-is. Otherwise, it is truncated to that length. The truncated string contains text
   * from the beginning and the end of the original.
   *
   * @param output the output to be truncated
   * @return the truncated output
   */
  private static String truncateOutput(String output) {
    if (output.length() < MAX_OUTPUT_LENGTH_IN_MSG) {
      return output;
    }

    int startLength = MAX_OUTPUT_LENGTH_IN_MSG / 2;
    String start = output.substring(0, startLength);

    // the extra 5 is taken off for the eclipses added below.
    int endLength = MAX_OUTPUT_LENGTH_IN_MSG / 2 - 5;
    String end = output.substring(output.length() - endLength);

    return start + System.lineSeparator() + "..." + System.lineSeparator() + end;
  }
}
