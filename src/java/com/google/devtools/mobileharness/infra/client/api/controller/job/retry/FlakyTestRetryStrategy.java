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

package com.google.devtools.mobileharness.infra.client.api.controller.job.retry;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;

/** Strategy deciding whether a finished test should be retried based on flakiness. */
public class FlakyTestRetryStrategy implements RetryStrategy {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String PARAM_FLAKY_TEST_ATTEMPTS = "flaky_test_attempts";

  static final String TEST_PROP_FLAKY_RETRY_INDEX = "flaky_retry_index";

  static final String TEST_PROP_ERROR_RETRY_INDEX = "error_retry_index";

  static final int MAX_ERROR_RETRY_ATTEMPTS = 2;

  @Override
  public RetryInfo decideRetryOnTestEnd(TestInfo currentTestInfo)
      throws MobileHarnessException, InterruptedException {
    // Add the retry index properties for the first attempt.
    int currentFlakyRetryIndex =
        currentTestInfo
            .properties()
            .getOptional(TEST_PROP_FLAKY_RETRY_INDEX)
            .map(Integer::parseInt)
            .orElse(-1);
    if (currentFlakyRetryIndex < 0) {
      // This is the first attempt. Add the flaky_test_index property.
      currentTestInfo.properties().add(TEST_PROP_FLAKY_RETRY_INDEX, "0");
      currentTestInfo.properties().add(TEST_PROP_ERROR_RETRY_INDEX, "0");
      currentFlakyRetryIndex = 0;
    }

    if (currentTestInfo.resultWithCause().get().type() == TestResult.PASS
        || currentTestInfo.resultWithCause().get().type() == TestResult.SKIP
        || currentTestInfo.resultWithCause().get().type() == TestResult.ABORT) {
      return NO_RETRY;
    }

    // Check if flaky_test_attempts is set and valid.
    String flakyTestAttemptStr = currentTestInfo.jobInfo().params().get(PARAM_FLAKY_TEST_ATTEMPTS);
    if (flakyTestAttemptStr == null) {
      logger.atInfo().log("No flaky_test_attempts set, skipping flaky test retry strategy.");
      return NO_RETRY;
    }
    int flakyTestAttempts = 0;
    try {
      flakyTestAttempts = Integer.parseInt(flakyTestAttemptStr);
    } catch (NumberFormatException e) {
      logger.atInfo().log("Invalid flaky_test_attempts set, skipping flaky test retry strategy.");
      return NO_RETRY;
    }
    if (flakyTestAttempts <= 0) {
      logger.atInfo().log(
          "flaky_test_attempts is not positive, skipping flaky test retry strategy.");
      return NO_RETRY;
    }

    TestResult testResult = currentTestInfo.resultWithCause().get().type();
    if (testResult == TestResult.FAIL) {
      if (currentFlakyRetryIndex + 1 >= flakyTestAttempts) {
        currentTestInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Test has reached the flaky test attempt limit.");
        return NO_RETRY;
      } else {
        return new RetryInfo(
            true,
            Optional.of("TEST_FAIL"),
            ImmutableMap.of(
                TEST_PROP_ERROR_RETRY_INDEX,
                "0",
                TEST_PROP_FLAKY_RETRY_INDEX,
                Integer.toString(currentFlakyRetryIndex + 1)));
      }
    } else {
      // Retry on other test results, including ERROR, TIMEOUT, UNKNOWN.
      int errorAttempts =
          getErrorAttemptsForCurrentFlakyTestIdx(currentTestInfo, currentFlakyRetryIndex);
      if (errorAttempts < MAX_ERROR_RETRY_ATTEMPTS) {
        return new RetryInfo(
            true,
            Optional.of("TEST_" + testResult.name()),
            ImmutableMap.of(
                TEST_PROP_ERROR_RETRY_INDEX, Integer.toString(errorAttempts),
                TEST_PROP_FLAKY_RETRY_INDEX, Integer.toString(currentFlakyRetryIndex)));
      }
    }

    return NO_RETRY;
  }

  private int getErrorAttemptsForCurrentFlakyTestIdx(
      TestInfo currentTestInfo, int currentFlakyTestIndex) {
    return (int)
        currentTestInfo.jobInfo().tests().getByName(currentTestInfo.locator().getName()).stream()
            .filter(testInfo -> testInfo.resultWithCause().get().type() == TestResult.ERROR)
            .filter(
                testInfo ->
                    testInfo
                            .properties()
                            .getOptional(TEST_PROP_FLAKY_RETRY_INDEX)
                            .map(Integer::parseInt)
                            .orElse(-1)
                        == currentFlakyTestIndex)
            .count();
  }
}
