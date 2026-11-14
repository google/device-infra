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
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.processor.TestRetryProcessor;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy.RetryEvaluator.AttemptSnapshot;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy.RetryEvaluator.FlakyDecision;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.List;
import java.util.Optional;

/** Strategy deciding whether a finished test should be retried based on flakiness. */
public class FlakyTestRetryStrategy implements RetryStrategy {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
    int errorAttempts =
        TEST_RESULTS_OF_ERROR_ATTEMPTS.contains(testResult)
            ? getErrorAttemptCountForCurrentFlakyAttemptIndex(
                currentTestInfo, currentFlakyAttemptIndex)
            : 0;
    AttemptSnapshot snapshot =
        new AttemptSnapshot(
            currentTestInfo.locator().getId(),
            testResult,
            currentTestInfo.resultWithCause().get().causeProto(),
            currentTestInfo.properties().getAll(),
            Optional.ofNullable(currentTestInfo.timing().getStartTime()));
    FlakyDecision decision =
        RetryEvaluator.decideFlakyRetry(
            flakyTestAttempts,
            isParallelRetry,
            snapshot,
            currentFlakyAttemptIndex,
            errorAttempts,
            () -> testRetryProcessor.generateRetryTestTargetsProperty(currentTestInfo));
    decision
        .jobLogMessage()
        .ifPresent(message -> jobInfo.log().atInfo().alsoTo(logger).log("%s", message));
    return decision.retries();
  }

  private int getErrorAttemptCountForCurrentFlakyAttemptIndex(
      TestInfo currentTestInfo, int currentFlakyAttemptsIndex) {
    return (int)
        currentTestInfo.jobInfo().tests().getByName(currentTestInfo.locator().getName()).stream()
            .filter(testInfo ->
                TEST_RESULTS_OF_ERROR_ATTEMPTS.contains(testInfo.resultWithCause().get().type()))
            .filter(testInfo ->
                testInfo
                        .properties()
                        .getInt(FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX)
                        .orElse(-1)
                    == currentFlakyAttemptsIndex)
            .count();
  }
}
