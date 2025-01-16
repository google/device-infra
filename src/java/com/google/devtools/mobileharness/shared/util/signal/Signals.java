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

package com.google.devtools.mobileharness.shared.util.signal;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import sun.misc.Signal;
import sun.misc.SignalHandler;

/** Utility for handling signals of the process. */
public class Signals {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableList<String> KNOWN_SIGNAL_NAMES =
      ImmutableList.of("INT", "TERM", "TSTP");

  private static final int EXIT_CODE_BASE = 128;
  private static final LoggingSignalHandler LOGGING_SIGNAL_HANDLER = new LoggingSignalHandler();

  /**
   * Captures known signals of the current process.
   *
   * <p>When capturing a known signal, logs it and terminates the current process with 128 plus the
   * signal number.
   */
  @SuppressWarnings("SunApi")
  public static void monitorKnownSignals() {
    for (String knownSignalName : KNOWN_SIGNAL_NAMES) {
      try {
        Signal.handle(new Signal(knownSignalName), LOGGING_SIGNAL_HANDLER);
      } catch (RuntimeException | Error e) {
        logger.atWarning().withCause(e).log("Failed to monitor signal [%s]", knownSignalName);
      }
    }
  }

  @SuppressWarnings("SunApi")
  private static class LoggingSignalHandler implements SignalHandler {

    @Override
    @SuppressWarnings("SystemExitOutsideMain")
    public void handle(Signal signal) {
      logger.atInfo().log("Receive signal [%s], terminating...", signal);
      System.exit(EXIT_CODE_BASE + signal.getNumber());
    }
  }

  private Signals() {}
}
