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

package com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.processor.TestRetryProcessor;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;

/** Strategy deciding whether a finished test should be retried based on flakiness. */
public class FlakyTestRetryStrategy implements RetryStrategy {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String TEST_PROP_FLAKY_ATTEMPT_INDEX =
      FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX;

  static final String TEST_PROP_ERROR_ATTEMPT_INDEX =
      FlakyTestRetryConstants.TEST_PROP_ERROR_ATTEMPT_INDEX;

  static final int MAX_ERROR_ATTEMPTS = 2;

  static final ImmutableSet<TestResult> TEST_RESULTS_NON_RETRYABLE =
      ImmutableSet.of(TestResult.PASS, TestResult.SKIP, TestResult.ABORT);

  static final ImmutableSet<TestResult> TEST_RESULTS_OF_ERROR_ATTEMPTS =
      ImmutableSet.of(TestResult.ERROR, TestResult.TIMEOUT, TestResult.UNKNOWN);

  private final TestRetryProcessor testRetryProcessor;

  public FlakyTestRetryStrategy() {
    this(new TestRetryProcessor());
  }

  @VisibleForTesting
  FlakyTestRetryStrategy(TestRetryProcessor testRetryProcessor) {
    this.testRetryProcessor = testRetryProcessor;
  }

  @Override
  public RetryInfo decideRetryOnTestEnd(TestInfo currentTestInfo)
      throws MobileHarnessException, InterruptedException {

    // Check if flaky_test_attempts is set and valid.
    int flakyTestAttempts =
        currentTestInfo
            .jobInfo()
            .params()
            .getInt(FlakyTestRetryConstants.PARAM_FLAKY_TEST_ATTEMPTS, -1);
    if (flakyTestAttempts <= 0) {
      logger.atInfo().log(
          "%s is not set or not positive, skipping flaky test retry strategy.",
          FlakyTestRetryConstants.PARAM_FLAKY_TEST_ATTEMPTS);
      return NO_RETRY;
    }

    // Add the retry index properties for the first attempt.
    int currentFlakyAttemptIndex =
        currentTestInfo.properties().getInt(TEST_PROP_FLAKY_ATTEMPT_INDEX).orElse(-1);
    if (currentFlakyAttemptIndex < 0) {
      // This is the first attempt. Add the flaky_test_index property.
      currentTestInfo.properties().add(TEST_PROP_FLAKY_ATTEMPT_INDEX, "0");
      currentTestInfo.properties().add(TEST_PROP_ERROR_ATTEMPT_INDEX, "0");
      currentFlakyAttemptIndex = 0;
    }

    TestResult testResult = currentTestInfo.resultWithCause().get().type();
    if (TEST_RESULTS_NON_RETRYABLE.contains(testResult)) {
      return NO_RETRY;
    }

    if (testResult == TestResult.FAIL) {
      if (currentFlakyAttemptIndex + 1 >= flakyTestAttempts) {
        currentTestInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Test has reached the flaky test attempt limit.");
        return NO_RETRY;
      } else {
        return new RetryInfo(
            Optional.of("TEST_FAIL"),
            ImmutableMap.<String, String>builder()
                .put(TEST_PROP_ERROR_ATTEMPT_INDEX, "0")
                .put(TEST_PROP_FLAKY_ATTEMPT_INDEX, Integer.toString(currentFlakyAttemptIndex + 1))
                .putAll(testRetryProcessor.generateRetryTestTargetsProperty(currentTestInfo))
                .buildOrThrow());
      }
    }

    if (TEST_RESULTS_OF_ERROR_ATTEMPTS.contains(testResult)) {
      // Retry on other test results, including ERROR, TIMEOUT, UNKNOWN.
      int errorAttempts =
          getErrorAttemptCountForCurrentFlakyAttemptIndex(
              currentTestInfo, currentFlakyAttemptIndex);
      if (errorAttempts < MAX_ERROR_ATTEMPTS) {
        return new RetryInfo(
            Optional.of("TEST_" + testResult.name()),
            ImmutableMap.<String, String>builder()
                .put(TEST_PROP_ERROR_ATTEMPT_INDEX, Integer.toString(errorAttempts))
                .put(TEST_PROP_FLAKY_ATTEMPT_INDEX, Integer.toString(currentFlakyAttemptIndex))
                .putAll(testRetryProcessor.generateRetryTestTargetsProperty(currentTestInfo))
                .buildOrThrow());
      }
    }

    // Should not happen. All test results should be covered in the above conditions.
    currentTestInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Test result [%s] is not supported by flaky test retry strategy.", testResult);
    return NO_RETRY;
  }

  private int getErrorAttemptCountForCurrentFlakyAttemptIndex(
      TestInfo currentTestInfo, int currentFlakyAttemptsIndex) {
    return (int)
        currentTestInfo.jobInfo().tests().getByName(currentTestInfo.locator().getName()).stream()
            .filter(
                testInfo ->
                    TEST_RESULTS_OF_ERROR_ATTEMPTS.contains(
                        testInfo.resultWithCause().get().type()))
            .filter(
                testInfo ->
                    testInfo.properties().getInt(TEST_PROP_FLAKY_ATTEMPT_INDEX).orElse(-1)
                        == currentFlakyAttemptsIndex)
            .count();
  }
}
