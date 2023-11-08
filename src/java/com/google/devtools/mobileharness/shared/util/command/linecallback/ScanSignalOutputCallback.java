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

package com.google.devtools.mobileharness.shared.util.command.linecallback;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * A {@link LineCallback} to search a signal string in the command output. It supports terminating
 * the command when the signal is found.
 */
public class ScanSignalOutputCallback implements LineCallback {
  /** Logger to print out the trace if timeout */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Interval time in milliseconds of waiting for the signal message. */
  public static final Duration WAIT_FOR_SIGNAL_INTERVAL_DURATION = Duration.ofMillis(200L);

  /** Limit the size of output buffer to 100 lines, with 80 characters per line */
  private static final int OUTPUT_MAX_SIZE = 8000;

  /** Whether the signal has been caught. */
  private volatile boolean signalCaught = false;

  /** Whether to terminate the command when the signal is caught. */
  private final boolean stopOnSignal;

  /** The signal to search in the command output. The signal should be in a single line. */
  private final String signal;

  private final Object handleOutputLock = new Object();

  /** The buffer to save the output from console which will be print out by timeout */
  @GuardedBy("handleOutputLock")
  private final StringBuilder output;

  /** {@code Clock} for getting current system time. */
  private final Clock clock;

  private final Sleeper sleeper;

  /**
   * Creates a output callback to catch the given signal in the command output.
   *
   * @param signal the signal to search in the command output, the signal should be in a single line
   * @param stopOnSignal whether to terminate the command when the signal is caught
   */
  public ScanSignalOutputCallback(String signal, boolean stopOnSignal) {
    this(signal, stopOnSignal, Sleeper.defaultSleeper(), Clock.systemUTC());
  }

  @VisibleForTesting
  ScanSignalOutputCallback(String signal, boolean stopOnSignal, Sleeper sleeper, Clock clock) {
    this.signal = signal;
    this.stopOnSignal = stopOnSignal;
    this.sleeper = sleeper;
    this.clock = clock;
    this.output = new StringBuilder();
  }

  /** Whether the signal is caught. */
  public boolean isSignalCaught() {
    return signalCaught;
  }

  @Override
  public Response onLine(String line) {
    if (!Strings.isNullOrEmpty(line)) {
      synchronized (handleOutputLock) {
        // Limit the max size of this buffer to avoid memory overflow
        int deleteSize = output.length() + line.length() + 1 - OUTPUT_MAX_SIZE;
        if (deleteSize > 0) {
          output.delete(0, deleteSize);
        }
        output.append(line).append("\n");
      }
    }

    if (!signalCaught) {
      if (line.contains(signal)) {
        signalCaught = true;
        if (stopOnSignal) {
          return Response.stop();
        }
      }
    }
    return Response.notStop();
  }

  /**
   * Waits until signal caught, or the async command is done, or timeout.
   *
   * @param timeout max wait time
   * @param commandProcess an object that can be used to check if the async command is terminated,
   *     or null if no command to check
   * @return whether the signal is caught
   * @throws InterruptedException if the current thread is interrupted
   */
  public boolean waitForSignal(Duration timeout, @Nullable CommandProcess commandProcess)
      throws InterruptedException {
    boolean signalFound = false;
    Instant expireTime = clock.instant().plus(timeout);
    while (clock.instant().isBefore(expireTime)) {
      if (isSignalCaught()) {
        break;
      }
      if (commandProcess != null && !commandProcess.isAlive()) {
        break;
      }
      sleeper.sleep(WAIT_FOR_SIGNAL_INTERVAL_DURATION);
    }
    synchronized (handleOutputLock) {
      if (isSignalCaught()) {
        signalFound = true;
      } else {
        // Timeout to catch signal, print out the trace for debug.
        logger.atInfo().log("Timeout to catch signal %s, with trace:\n%s", signal, output);
      }
      // Delete the content of output in case it is reused later.
      output.delete(0, output.length());
    }
    return signalFound;
  }
}
