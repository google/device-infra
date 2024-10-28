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

package com.google.devtools.mobileharness.platform.android.nativebin;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import java.time.Duration;
import java.util.Optional;

/** Arguments wrapper for running native binary on Android device for {@link NativeBinUtil}. */
@AutoValue
public abstract class NativeBinArgs {

  /** Directory of the binary, also where the binary is run. */
  public abstract String runDirectory();

  /** File name of the binary. */
  public abstract String binary();

  /** Max timeout for running native bin. */
  public abstract Duration commandTimeout();

  /** Optional: The user to run the test as, defaults to root if not provided. */
  public abstract Optional<String> runAs();

  /*
   * Optional: The environment variable assignments to make during the run.
   *
   * This should normally be a space-separated list of environment variable settings of the form
   *
   *   var='value'
   *
   * For example, this may be used to set PATH and LD_LIBRARY_PATH environment variables by using
   * the value
   *
   *   PATH='/some/path/to/bin' LD_LIBRARY_PATH='/some/path/to/lib'
   *
   * If set, this setting will be prefixed to the shell command line that is used to
   * invoke the binary. As such, it will be subject to the shell's usual expansions
   * (i.e. brace expansion, tilde expansion, shell parameter and variable expansion,
   * command substitution, arithmetic expansion, word splitting, and pathname expansion).
   * In the typical case where such expansions are NOT desired, values that might contain
   * shell metacharacters should be quoted to prevent such expansions, e.g. using single
   * quotes as shown in the example above.
   */
  public abstract Optional<String> runEnvironment();

  /** Optional: The flags for the binary. */
  public abstract Optional<String> options();

  /** Optional: The flags for taskset. */
  public abstract Optional<String> cpuAffinity();

  /** Optional: Redirect stderr into stdout. */
  public abstract Optional<Boolean> redirectStderr();

  /** Optional: Callback for each line of stdout output. */
  public abstract Optional<LineCallback> stdoutLineCallback();

  /** Optional: Callback for each line of stderr output. */
  public abstract Optional<LineCallback> stderrLineCallback();

  /**
   * Optional: Calls '{@code echo $?}' after shell command to get shell command exit code in command
   * output. For device sdk <= 23, adb shell doesn't return exit code of program (b/137390305).
   */
  public abstract Optional<Boolean> echoCommandExitCode();

  public static Builder builder() {
    return new AutoValue_NativeBinArgs.Builder();
  }

  /** Auto value builder for {@link NativeBinArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract NativeBinArgs.Builder setRunDirectory(String runDirectory);

    public abstract NativeBinArgs.Builder setBinary(String binary);

    public abstract NativeBinArgs.Builder setCommandTimeout(Duration commandTimeout);

    public abstract NativeBinArgs.Builder setRunAs(String runAs);

    public abstract NativeBinArgs.Builder setRunEnvironment(String runEnvironment);

    public abstract NativeBinArgs.Builder setOptions(String options);

    public abstract NativeBinArgs.Builder setCpuAffinity(String cpuAffinity);

    public abstract NativeBinArgs.Builder setRedirectStderr(boolean redirectStderr);

    public abstract NativeBinArgs.Builder setStdoutLineCallback(LineCallback stdoutLineCallback);

    public abstract NativeBinArgs.Builder setStderrLineCallback(LineCallback stderrLineCallback);

    public abstract NativeBinArgs.Builder setEchoCommandExitCode(boolean echoCommandExitCode);

    public abstract NativeBinArgs build();
  }
}
