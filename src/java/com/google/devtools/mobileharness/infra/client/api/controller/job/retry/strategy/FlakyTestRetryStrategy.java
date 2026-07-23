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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.processor.TestRetryProcessor;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.ArrayList;
import java.util.List;

/** Strategy deciding whether a finished test should be retried based on flakiness. */
public class FlakyTestRetryStrategy implements RetryStrategy {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
  public List<RetryInfo> decideRetryOnTestEnd(TestInfo currentTestInfo)
      throws MobileHarnessException, InterruptedException {

    JobInfo jobInfo = currentTestInfo.jobInfo();

    // Check if flaky_test_attempts is set and valid.
    int flakyTestAttempts =
        jobInfo.params().getInt(FlakyTestRetryConstants.PARAM_FLAKY_TEST_ATTEMPTS, -1);
    if (flakyTestAttempts <= 0) {
      logger.atInfo().log(
          "%s is not set or not positive, skipping flaky test retry strategy.",
          FlakyTestRetryConstants.PARAM_FLAKY_TEST_ATTEMPTS);
      return ImmutableList.of();
    }

    boolean isParallelRetry =
        jobInfo.params().getBool(FlakyTestRetryConstants.PARAM_FLAKY_TEST_PARALLEL_RETRY, false);

    // Add the retry index properties for the first attempt.
    int currentFlakyAttemptIndex =
        currentTestInfo
            .properties()
            .getInt(FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX)
            .orElse(-1);
    if (currentFlakyAttemptIndex < 0) {
      // This is the first attempt. Add the flaky_test_index property.
      currentTestInfo.properties().add(FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX, "0");
      currentTestInfo.properties().add(FlakyTestRetryConstants.TEST_PROP_ERROR_ATTEMPT_INDEX, "0");
      currentTestInfo
          .properties()
          .add(
              FlakyTestRetryConstants.TEST_PROP_FLAKY_TEST_PARALLEL_RETRY,
              String.valueOf(isParallelRetry));
      currentFlakyAttemptIndex = 0;
    }

    TestResult testResult = currentTestInfo.resultWithCause().get().type();
    if (TEST_RESULTS_NON_RETRYABLE.contains(testResult)) {
      return ImmutableList.of();
    }

    if (testResult == TestResult.FAIL) {
      if (isParallelRetry) {
        if (currentFlakyAttemptIndex >= 1) {
          jobInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "No more retry for test fail. All parallel flaky test attempts have been"
                      + " triggered. Current attempt: flaky_attempt_idx=%d, max_flaky_attempts=%d,"
                      + " test_id=%s",
                  currentFlakyAttemptIndex, flakyTestAttempts, currentTestInfo.locator().getId());
          return ImmutableList.of();
        } else {
          List<RetryInfo> retryInfos = new ArrayList<>();
          ImmutableMap<String, String> retryTestTargetsProperties =
              testRetryProcessor.generateRetryTestTargetsProperty(currentTestInfo);
          for (int i = 1; i < flakyTestAttempts; i++) {
            retryInfos.add(
                new RetryInfo(
                    "PARALLEL_FLAKY_TEST_RETRY_FOR_TEST_FAIL",
                    ImmutableMap.<String, String>builder()
                        .put(FlakyTestRetryConstants.TEST_PROP_ERROR_ATTEMPT_INDEX, "0")
                        .put(
                            FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX,
                            Integer.toString(i))
                        .put(FlakyTestRetryConstants.TEST_PROP_FLAKY_TEST_PARALLEL_RETRY, "true")
                        .putAll(retryTestTargetsProperties)
                        .buildOrThrow()));
          }
          jobInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Trigger %d parallel flaky test attempts for the first failed attempt. "
                      + "Current attempt: flaky_attempt_idx=%d, test_id=%s",
                  retryInfos.size(), currentFlakyAttemptIndex, currentTestInfo.locator().getId());
          return ImmutableList.copyOf(retryInfos);
        }
      } else {
        if (currentFlakyAttemptIndex >= flakyTestAttempts - 1) {
          jobInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "No more retry for test fail. Reached the flaky test attempt limit: limit=%d. "
                      + "Current attempt: flaky_attempt_idx=%d, test_id=%s",
                  flakyTestAttempts, currentFlakyAttemptIndex, currentTestInfo.locator().getId());
          return ImmutableList.of();
        } else {
          jobInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Trigger a new sequential retry attempt. "
                      + "Current attempt: flaky_attempt_idx=%d, max_flaky_attempts=%d, test_id=%s",
                  currentFlakyAttemptIndex, flakyTestAttempts, currentTestInfo.locator().getId());
          return ImmutableList.of(
              new RetryInfo(
                  "TEST_FAIL",
                  ImmutableMap.<String, String>builder()
                      .put(FlakyTestRetryConstants.TEST_PROP_ERROR_ATTEMPT_INDEX, "0")
                      .put(
                          FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX,
                          Integer.toString(currentFlakyAttemptIndex + 1))
                      .put(FlakyTestRetryConstants.TEST_PROP_FLAKY_TEST_PARALLEL_RETRY, "false")
                      .putAll(testRetryProcessor.generateRetryTestTargetsProperty(currentTestInfo))
                      .buildOrThrow()));
        }
      }
    }

    if (TEST_RESULTS_OF_ERROR_ATTEMPTS.contains(testResult)) {
      // Retry on other test results, including ERROR, TIMEOUT, UNKNOWN.
      int errorAttempts =
          getErrorAttemptCountForCurrentFlakyAttemptIndex(
              currentTestInfo, currentFlakyAttemptIndex);

      if (errorAttempts < FlakyTestRetryConstants.MAX_ERROR_ATTEMPTS) {
        jobInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Internal retry on test result [%s]. Current attempts: flaky_attempt_idx=%d,"
                    + " max_flaky_attempts=%d, error_attempt_idx=%d, max_error_attempts=%d,"
                    + " test_id=%s",
                testResult,
                currentFlakyAttemptIndex,
                flakyTestAttempts,
                errorAttempts - 1,
                FlakyTestRetryConstants.MAX_ERROR_ATTEMPTS,
                currentTestInfo.locator().getId());
        return ImmutableList.of(
            new RetryInfo(
                "TEST_" + testResult.name(),
                ImmutableMap.<String, String>builder()
                    .put(
                        FlakyTestRetryConstants.TEST_PROP_ERROR_ATTEMPT_INDEX,
                        Integer.toString(errorAttempts))
                    .put(
                        FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX,
                        Integer.toString(currentFlakyAttemptIndex))
                    .putAll(testRetryProcessor.generateRetryTestTargetsProperty(currentTestInfo))
                    .buildOrThrow()));
      } else {
        jobInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "No more retry for test result [%s]. Reached the error attempt limit. Current"
                    + " attempts: flaky_attempt_idx=%d, max_flaky_attempts=%d,"
                    + " error_attempt_idx=%d, max_error_attempts=%d, test_id=%s",
                testResult,
                currentFlakyAttemptIndex,
                flakyTestAttempts,
                errorAttempts - 1,
                FlakyTestRetryConstants.MAX_ERROR_ATTEMPTS,
                currentTestInfo.locator().getId());
        return ImmutableList.of();
      }
    }

    // Should not happen. All test results should be covered in the above conditions.
    jobInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "SHOULD NOT HAPPEN: Test result [%s] is not supported by flaky test retry strategy.",
            testResult);
    return ImmutableList.of();
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
                    testInfo
                            .properties()
                            .getInt(FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX)
                            .orElse(-1)
                        == currentFlakyAttemptsIndex)
            .count();
  }
}
