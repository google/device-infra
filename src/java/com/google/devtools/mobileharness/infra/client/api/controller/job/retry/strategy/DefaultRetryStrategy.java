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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy.RetryEvaluator.AttemptSnapshot;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy.RetryEvaluator.JobRetryContext;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy.RetryEvaluator.StrategyDecision;
import com.google.devtools.mobileharness.infra.container.proto.ModeSettingProto.ContainerModePreference;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Default strategy deciding whether a finished test should be retried. */
public class DefaultRetryStrategy implements RetryStrategy {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String PROPERTY_REPEAT_INDEX = Ascii.toLowerCase(Test.REPEAT_INDEX.name());

  public DefaultRetryStrategy() {}

  /** Decide whether the test should be retried. */
  @Override
  public List<RetryInfo> decideRetryOnTestEnd(TestInfo currentTestInfo)
      throws MobileHarnessException, InterruptedException {
    JobInfo jobInfo = currentTestInfo.jobInfo();
    Retry retrySetting = jobInfo.setting().getRetry();
    boolean retryOnTimeout = jobInfo.params().getBool(RetryEvaluator.PARAM_RETRY_ON_TIMEOUT, true);
    String containerModePreference =
        jobInfo
            .params()
            .get(
                JobInfo.PARAM_CONTAINER_MODE_PREFERENCE,
                ContainerModePreference.CONTAINER_MODE_PREFERENCE_UNSPECIFIED.name());

    ImmutableList<TestInfo> allAttempts = getAllAttempts(jobInfo, currentTestInfo);
    int validAttemptNum =
        (int)
            allAttempts.stream()
                .filter(attempt ->
                    RetryEvaluator.isValidAttempt(
                        containerModePreference, toAttemptSnapshot(attempt)))
                .count();

    JobRetryContext jobContext =
        new JobRetryContext(
            retrySetting,
            jobInfo.type().getDriver(),
            jobInfo.jobUser().getRunAs(),
            /* flakyTestAttempts= */ -1,
            /* flakyTestParallelRetry= */ false,
            retryOnTimeout,
            containerModePreference);
    AttemptSnapshot currentSnapshot = toAttemptSnapshot(currentTestInfo);
    StrategyDecision decision =
        RetryEvaluator.decideDefaultRetry(
            jobContext,
            currentSnapshot,
            validAttemptNum,
            allAttempts.size(),
            () -> jobInfo.timer().remainingTimeJava(),
            Instant.now());

    decision
        .testLogMessage()
        .ifPresent(message -> currentTestInfo.log().atInfo().log("%s", message));

    decision
        .skipMessage()
        .ifPresent(message -> {
          jobInfo.log().atInfo().alsoTo(logger).log("%s", message);
          currentTestInfo.log().atInfo().log("%s", message);
        });

    if (!decision.retries().isEmpty()
        && validAttemptNum > 0
        && !currentTestInfo.properties().has(Test.RETRY_INDEX)) {
      currentTestInfo.properties().add(Test.RETRY_INDEX, Long.toString(validAttemptNum - 1));
    }
    return decision.retries();
  }

  private static AttemptSnapshot toAttemptSnapshot(TestInfo testInfo) {
    return new AttemptSnapshot(
        testInfo.locator().getId(),
        testInfo.resultWithCause().get().type(),
        testInfo.resultWithCause().get().causeProto(),
        testInfo.properties().getAll(),
        Optional.ofNullable(testInfo.timing().getStartTime()));
  }

  private ImmutableList<TestInfo> getAllAttempts(JobInfo jobInfo, TestInfo currentTestInfo) {
    String testName = currentTestInfo.locator().getName();
    Integer repeatIndex = null;
    if (currentTestInfo.properties().has(PROPERTY_REPEAT_INDEX)) {
      try {
        repeatIndex = Integer.parseInt(currentTestInfo.properties().get(PROPERTY_REPEAT_INDEX));
      } catch (NumberFormatException e) {
        // do nothing
      }
    }
    ImmutableList.Builder<TestInfo> allAttempts = ImmutableList.builder();
    for (TestInfo testInfo : jobInfo.tests().getByName(testName)) {
      if (repeatIndex != null
          && !testInfo.properties().get(PROPERTY_REPEAT_INDEX).equals(repeatIndex.toString())) {
        // The attempt is not for the same repeatIndex tests
        continue;
      }
      allAttempts.add(testInfo);
    }
    return allAttempts.build();
  }
}
