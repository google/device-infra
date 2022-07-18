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

package com.google.devtools.deviceinfra.shared.util.command;

import com.google.devtools.deviceinfra.shared.util.command.backend.CommandProcess;
import com.google.devtools.deviceinfra.shared.util.command.io.LineCollector;
import java.io.OutputStream;
import java.io.Writer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/** Implementation of {@code CommandProcess}. */
public class CommandProcessImpl {

  private final CommandProcess backendProcess;
  private final LineCollector stdoutCollector;
  private final LineCollector stderrCollector;
  private final Duration finalizedTimeout;
  @Nullable private final Duration finalizedStartTimeout;

  private final OutputStream stdinStream;
  private final Writer stdinWriter;

  private final AtomicBoolean isTimeout = new AtomicBoolean();
  private final AtomicBoolean isStopped = new AtomicBoolean();

  public CommandProcessImpl(
      CommandProcess backendProcess,
      LineCollector stdoutCollector,
      LineCollector stderrCollector,
      Duration finalizedTimeout,
      @Nullable Duration finalizedStartTimeout) {
    this.backendProcess = backendProcess;
    this.stdoutCollector = stdoutCollector;
    this.stderrCollector = stderrCollector;
    this.finalizedTimeout = finalizedTimeout;
    this.finalizedStartTimeout = finalizedStartTimeout;
    this.stdinStream = backendProcess.stdinStream();
    this.stdinWriter = backendProcess.stdinWriterUtf8();
  }

  public CommandProcess backendProcess() {
    return backendProcess;
  }

  public LineCollector stdoutCollector() {
    return stdoutCollector;
  }

  public LineCollector stderrCollector() {
    return stderrCollector;
  }

  public Duration finalizedTimeout() {
    return finalizedTimeout;
  }

  public Optional<Duration> finalizedStartTimeout() {
    return Optional.ofNullable(finalizedStartTimeout);
  }

  public OutputStream stdinStream() {
    return stdinStream;
  }

  public Writer stdinWriter() {
    return stdinWriter;
  }

  public AtomicBoolean isTimeout() {
    return isTimeout;
  }

  public AtomicBoolean isStopped() {
    return isStopped;
  }
}
