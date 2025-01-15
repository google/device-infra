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

package com.google.devtools.mobileharness.shared.util.junit.rule;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.shared.util.junit.rule.util.FinishWithFailureTestWatcher;
import com.google.devtools.mobileharness.shared.util.junit.rule.util.StringsDebugger;
import com.google.devtools.mobileharness.shared.util.logging.MobileHarnessLogFormatter;
import java.io.ByteArrayOutputStream;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import javax.annotation.Nullable;
import org.junit.runner.Description;

/**
 * Capture logs from a {@link Logger} during a test. This allows the logs to be used for
 * verification and to be automatically appended to the failure details of a failed test, thereby
 * simplifying the debugging process.
 */
public class CaptureLogs extends FinishWithFailureTestWatcher {

  private final Logger capturedLogger;
  private final boolean printFailedLogs;
  private final ByteArrayOutputStream logOutputStream = new ByteArrayOutputStream();
  private Handler logHandler;

  /**
   * The constructor.
   *
   * @param loggerName the name of the {@link Logger} whose logs will be captured during a test. For
   *     example, an empty string for the root logger.
   * @param printFailedLogs whether to append the captured logs to the failure details of a test (if
   *     the test fails)
   */
  public CaptureLogs(String loggerName, boolean printFailedLogs) {
    this.printFailedLogs = printFailedLogs;
    this.capturedLogger = Logger.getLogger(loggerName);
  }

  /** Returns the captured logs. */
  public String getLogs() {
    return logOutputStream.toString(UTF_8);
  }

  @Override
  protected void starting(Description description) {
    logHandler =
        new StreamHandler(logOutputStream, MobileHarnessLogFormatter.getDefaultFormatter());
    capturedLogger.addHandler(logHandler);
  }

  @Override
  protected void onFinished(@Nullable Throwable testFailure, Description description) {
    capturedLogger.removeHandler(logHandler);

    StringsDebugger.onTestFinished(
        printFailedLogs ? testFailure : null,
        () ->
            ImmutableMap.of(
                String.format("logs from logger\"%s\"", capturedLogger.getName()), getLogs()));
  }
}
