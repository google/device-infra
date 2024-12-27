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

import com.google.common.flogger.FluentLogger;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * Prints the test name in logs before/after a test.
 *
 * <p>It makes the start/end of a test more obvious in logs, which can help debugging.
 */
public class PrintTestName extends TestWatcher {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  protected void starting(Description description) {
    logger.atInfo().log(
        "\n========================================\n"
            + "Test starting: %s\n"
            + "========================================\n",
        description.getDisplayName());
  }

  @Override
  protected void finished(Description description) {
    logger.atInfo().log(
        "\n========================================\n"
            + "Test ended: %s\n"
            + "========================================\n",
        description.getDisplayName());
  }
}
