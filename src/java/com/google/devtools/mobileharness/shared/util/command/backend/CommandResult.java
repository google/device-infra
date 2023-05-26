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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.Immutable;
import java.nio.charset.Charset;

/** The result of executing a {@link Command}. */
@Immutable
public final class CommandResult {
  private final int exitCode;

  @SuppressWarnings("Immutable") // never mutated after entering this class
  private final CapturingOutputStream stdout;

  @SuppressWarnings("Immutable") // never mutated after entering this class
  private final CapturingOutputStream stderr;

  CommandResult(int exitCode, CapturingOutputStream stdout, CapturingOutputStream stderr) {
    this.exitCode = exitCode;
    this.stdout = stdout;
    this.stderr = stderr;
  }

  /** Returns the exit code of the command execution. */
  public int exitCode() {
    return exitCode;
  }

  /** Returns the standard output of the command execution as a new array of bytes. */
  public byte[] stdoutBytes() {
    return stdout.toByteArray();
  }

  /** Returns the standard error of the command execution as a new array of bytes. */
  public byte[] stderrBytes() {
    return stderr.toByteArray();
  }

  /** Returns the standard output of the command execution as a string. */
  public String stdoutString(Charset cs) {
    return stdout.toString(cs);
  }

  /** Returns the standard error of the command execution as a string. */
  public String stderrString(Charset cs) {
    return stderr.toString(cs);
  }

  /** Returns the standard output of the command execution as a UTF-8 string. */
  public String stdoutStringUtf8() {
    return stdoutString(UTF_8);
  }

  /** Returns the standard error of the command execution as a UTF-8 string. */
  public String stderrStringUtf8() {
    return stderrString(UTF_8);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("exit code", exitCode)
        .add("stdout", stdoutStringUtf8())
        .add("stderr", stderrStringUtf8())
        .toString();
  }
}
