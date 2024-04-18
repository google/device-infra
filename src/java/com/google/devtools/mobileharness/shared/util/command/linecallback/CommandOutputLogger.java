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

import com.google.common.flogger.FluentLogger;

/**
 * Logger for logging stdout/stderr of a subprocess to the logs of the current process without
 * printing the common log prefix (e.g., date, level, logger name) of loggers of the current
 * process.
 *
 * <p>For example, if {@code stdoutLinePrefix} is "{@code FOO-abcd }", then a call to {@link
 * #logStdoutLine(String)} with an argument "{@code This is a stdout line from the subprocess
 * foo.}", the logger of the current process will receive a log record with the formatter message
 * "{@code FOO-abcd This is a stdout line from the subprocess foo.}".
 *
 * @implSpec the class name of this class should be added to {@code
 *     MobileHarnessLogFormatter#DIRECT_MODE_SOURCE_CLASS_NAMES}.
 */
public class CommandOutputLogger {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String stdoutLinePrefix;

  private final String stderrLinePrefix;

  public CommandOutputLogger(String stdoutLinePrefix, String stderrLinePrefix) {
    this.stdoutLinePrefix = stdoutLinePrefix;
    this.stderrLinePrefix = stderrLinePrefix;
  }

  public void logStdoutLine(String stdoutLine) {
    logger.atInfo().log("%s%s", stdoutLinePrefix, stdoutLine);
  }

  public void logStderrLine(String stderrLine) {
    logger.atInfo().log("%s%s", stderrLinePrefix, stderrLine);
  }
}
