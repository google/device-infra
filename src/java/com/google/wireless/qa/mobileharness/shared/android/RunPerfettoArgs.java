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

package com.google.wireless.qa.mobileharness.shared.android;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

/** Wrapper for arguments used for Running the Perfetto tracing tool. */
@AutoValue
public abstract class RunPerfettoArgs {
  // The path to adb binary that we want to run Perfetto decorator with.
  public abstract Optional<String> adbPath();

  // The device serial of the test device.
  public abstract String serial();

  // If running in sync mode, time specifies the time that Perfetto runs.
  // Time is meaningless for async mode.
  public abstract Duration time();

  // The time limit for the tracing session.
  public abstract Duration traceTimeout();

  // Where to put the tracing output.
  public abstract String outputPath();

  // space-separated tags to run perfetto with.
  public abstract Optional<String> tags();

  // The size of the buffer which determines the flush frequency.
  public abstract int bufferSizeKb();

  // The packages(apps) to monitor.
  public abstract Optional<String> packageList();

  // Called when the tracing is completed.
  public abstract Optional<Consumer<CommandResult>> exitCallback();

  // Called when timeout limit is hit.
  public abstract Runnable timeoutCallback();

  // Called when the Perfetto script dumps logs to the console.
  public abstract LineCallback outputCallback();

  // The perfetto config file path.
  public abstract Optional<String> configFile();

  public static Builder builder() {
    return new AutoValue_RunPerfettoArgs.Builder();
  }

  abstract Builder toBuilder();

  /** Auto value builder for {@link RunPerfettoArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setAdbPath(Optional<String> adbPath);

    public abstract Builder setSerial(String serial);

    public abstract Builder setTime(Duration time);

    public abstract Builder setTraceTimeout(Duration duration);

    public abstract Builder setOutputPath(String outputPath);

    public abstract Builder setTags(Optional<String> tags);

    public abstract Builder setBufferSizeKb(int bufferSizeKb);

    public abstract Builder setPackageList(Optional<String> packageList);

    public abstract Builder setExitCallback(Optional<Consumer<CommandResult>> exitCallback);

    public abstract Builder setTimeoutCallback(Runnable timeoutCallback);

    public abstract Builder setOutputCallback(LineCallback lineCallback);

    public abstract Builder setConfigFile(Optional<String> configFile);

    public abstract RunPerfettoArgs build();
  }
}
