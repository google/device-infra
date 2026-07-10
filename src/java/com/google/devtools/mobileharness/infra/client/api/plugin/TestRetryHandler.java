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

package com.google.devtools.mobileharness.infra.client.api.plugin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.DefaultRetryStrategy;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.FlakyTestRetryConstants;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.FlakyTestRetryStrategy;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.RetryStrategy;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.RetryStrategy.RetryInfo;
import com.google.devtools.mobileharness.infra.client.api.util.result.ClientAllocErrorUtil;
import com.google.wireless.qa.mobileharness.client.api.event.JobStartEvent;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Job;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndedEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Map;
import java.util.Optional;

/** The test event handler for generating multiple attempts or retrying tests. */
public class TestRetryHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String PROPERTY_REPEAT_INDEX = Ascii.toLowerCase(Test.REPEAT_INDEX.name());

  private static final ImmutableSet<String> INHERITED_TEST_PROPERTIES =
      ImmutableSet.of(
          Ascii.toLowerCase(Test.SHARD_COUNT.name()),
          Ascii.toLowerCase(Test.SHARD_INDEX.name()),
          Ascii.toLowerCase(Test._DRAIN_TIMEOUT_RETRY_ATTEMPTS.name()),
          PROPERTY_REPEAT_INDEX);

  private final DeviceAllocator deviceAllocator;
  private final RetryStrategy defaultRetryStrategy;
  private final RetryStrategy flakyTestRetryStrategy;

  public TestRetryHandler(DeviceAllocator deviceAllocator) {
    this(deviceAllocator, new DefaultRetryStrategy(), new FlakyTestRetryStrategy());
  }

  @VisibleForTesting
  TestRetryHandler(DeviceAllocator deviceAllocator, RetryStrategy retryStrategy) {
    this(deviceAllocator, retryStrategy, new FlakyTestRetryStrategy());
  }

  @VisibleForTesting
  TestRetryHandler(
      DeviceAllocator deviceAllocator,
      RetryStrategy defaultRetryStrategy,
      RetryStrategy flakyTestRetryStrategy) {
    this.deviceAllocator = deviceAllocator;
    this.defaultRetryStrategy = defaultRetryStrategy;
    this.flakyTestRetryStrategy = flakyTestRetryStrategy;
  }

  @Subscribe
  public void onJobStart(JobStartEvent event) throws MobileHarnessException {
    JobInfo jobInfo = event.getJob();
    int repeatRuns = jobInfo.setting().getRepeat().getRepeatRuns();
    if (repeatRuns <= 0) { // Make sure it is compatible with previous retry_level=ALL solution.
      repeatRuns = getRepeatRunsFromRetry(jobInfo.setting().getRetry());
    }
    if (repeatRuns <= 1) {
      return;
    }
    jobInfo.log().atInfo().alsoTo(logger).log("Create %d repeat runs for every tests", repeatRuns);
    jobInfo.properties().add(Job.REPEAT_RUNS, Integer.toString(repeatRuns));
    for (TestInfo testInfo : jobInfo.tests().getAll().values()) {
      String testName = testInfo.locator().getName();
      testInfo.properties().add(PROPERTY_REPEAT_INDEX, "0");
      addFinalAttemptProperty(testInfo);
      for (int i = 1; i < repeatRuns; i++) {
        TestInfo repeatRunTesInfo = jobInfo.tests().add(testName);
        repeatRunTesInfo.properties().add(PROPERTY_REPEAT_INDEX, Integer.toString(i));
        addFinalAttemptProperty(repeatRunTesInfo);
      }
    }
  }

  // To be compatible with the old retry_level=ALL.
  private int getRepeatRunsFromRetry(Retry retrySetting) {
    if (retrySetting.getRetryLevel() == Retry.Level.ALL) {
      logger.atWarning().log(
          "Retry level ALL is deprecated. Please specify --repeat_runs if using mobile_test"
              + " target, or set repeatRuns in JobSetting if using MobileHarness API.");
      return retrySetting.getTestAttempts();
    }
    return 1;
  }

  @Subscribe
  public void onTestEnded(TestEndedEvent event)
      throws MobileHarnessException, InterruptedException {
    TestInfo currentTestInfo = event.getTest();
    JobInfo jobInfo = currentTestInfo.jobInfo();

    Retry retrySetting = jobInfo.setting().getRetry();
    Retry.Level retryLevel = retrySetting.getRetryLevel();
    if (retryLevel == Retry.Level.ALL) {
      // Already handled in JobStartEvent. Ignore.
      return;
    }

    Optional<TestInfo> foregoingTest = getForegoingTest(currentTestInfo);

    TestResult currentTestResult = currentTestInfo.resultWithCause().get().type();
    Optional<TestResult> foregoingTestResult =
        foregoingTest
            .map(TestInfo::resultWithCause)
            .map(Result::get)
            .map(ResultTypeWithCause::type);

    boolean isPassAfterRetry =
        foregoingTestResult.isPresent()
            && foregoingTestResult.get() != TestResult.PASS
            && currentTestResult == TestResult.PASS;
    if (isPassAfterRetry) {
      // Use cases:
      // 1) Show the tests failed because of Container:
      //    NONPASSING_BEFORE_RETRY_PASS=true & CONTAINER_MODE=true
      // 2) Show the failed/error tests that are saved by a general retry:
      //    NONPASSING_BEFORE_RETRY_PASS=true & CONTAINER_MODE=false
      foregoingTest.get().properties().add(Test.NONPASSING_BEFORE_RETRY_PASS, "true");
      // See b/184734364. Since the foregoing test has ended and nowadays MossUploader will not
      // update test after it ends, we need to explicitly tell MossUploader to update the foregoing
      // test when the job ends.
      foregoingTest.get().properties().add(Test.VOLATILE_TEST_INFO_AFTER_TEST_ENDS, "true");

      // Use cases:
      // 1) Show the pass retry attempts after a container fail/error runs:
      //    PASS_AFTER_RETRY=true && RETRY_REASON=POTENTIAL_CONTAINER_ISSUE
      // 2) Show the failed/error tests that are saved by a general retry:
      //    NONPASSING_BEFORE_RETRY_PASS=true & CONTAINER_MODE=false
      // 3) Show the INFRA_ISSUE tests that are saved by an extra retry:
      //    PASS_AFTER_RETRY=true && RETRY_REASON=EXTRA_RETRY_FOR_INFRA_ISSUE
      currentTestInfo.properties().add(Test.PASS_AFTER_RETRY, "true");
      addFinalAttemptProperty(currentTestInfo);
      return;
    }

    // Don't retry the test if fails to allocate device because the start timeout already expires.
    if (ClientAllocErrorUtil.isTestAllocError(currentTestInfo)
        || ClientAllocErrorUtil.isTestAllocFail(currentTestInfo)) {
      currentTestInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Do not retry test for allocation failure [%s]", currentTestResult);
      addFinalAttemptProperty(currentTestInfo);
      return;
    }

    // Do not retry if the job is timeout
    if (jobInfo.timer().isExpired()) {
      addFinalAttemptProperty(currentTestInfo);
      return;
    }
    // Do not retry if the job is manually aborted
    if (jobInfo.properties().getBoolean(Job.MANUALLY_ABORTED).orElse(false)) {
      addFinalAttemptProperty(currentTestInfo);
      return;
    }
    // Do not retry if the current test halts the future retry.
    if (currentTestInfo.properties().getBoolean(Test.HALT_RETRY).orElse(false)) {
      addFinalAttemptProperty(currentTestInfo);
      return;
    }

    RetryInfo retryInfo = getRetryStrategy(jobInfo).decideRetryOnTestEnd(currentTestInfo);

    if (retryInfo.retryReason().isEmpty()) {
      addFinalAttemptProperty(currentTestInfo);
    } else {
      String retryReason = retryInfo.retryReason().orElse("");
      String testName = currentTestInfo.locator().getName();
      TestInfo newTest = addNewTest(jobInfo, currentTestInfo);

      newTest.properties().add(Test.RETRY_REASON, retryReason);
      newTest.properties().addAll(retryInfo.newTestProperties());

      String message =
          String.format(
              "Retry MH test [%s], reason=%s, old_id=%s, new_id=%s, job_remaining_time=%s",
              testName,
              retryReason,
              currentTestInfo.locator().getId(),
              newTest.locator().getId(),
              jobInfo.timer().remainingTimeJava());
      jobInfo.log().atInfo().alsoTo(logger).log("%s", message);
      currentTestInfo.log().atInfo().log("%s", message);
      newTest.log().atInfo().log("%s", message);

      try {
        deviceAllocator.extraAllocation(newTest);
      } catch (MobileHarnessException e) {
        // Does nothing here. Even if it failed to add the new test to master here, it has
        // been added to job. So the MasterDeviceAllocator will check and reopen it to master
        // again.
        currentTestInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .withCause(e)
            .log("Failed to add allocation for retry test [%s]", newTest.locator().getId());
      }
      currentTestInfo.properties().add(Ascii.toLowerCase(Test.IS_FINAL_ATTEMPT.name()), "false");
    }
  }

  private static TestInfo addNewTest(JobInfo jobInfo, TestInfo currentTestInfo)
      throws MobileHarnessException {
    String testName = currentTestInfo.locator().getName();
    TestInfo newTestInfo = jobInfo.tests().add(testName);
    for (Map.Entry<String, String> property : currentTestInfo.properties().getAll().entrySet()) {
      if (INHERITED_TEST_PROPERTIES.contains(property.getKey())) {
        newTestInfo.properties().add(property.getKey(), property.getValue());
      }
    }

    String currentTestId = currentTestInfo.locator().getId();
    newTestInfo.properties().add(Test.FOREGOING_TEST_ID, currentTestId);
    newTestInfo
        .properties()
        .add(Test.FOREGOING_TEST_RESULT, currentTestInfo.resultWithCause().get().type().name());

    currentTestInfo.properties().add(Test.RETRY_TEST_ID, newTestInfo.locator().getId());

    return newTestInfo;
  }

  private RetryStrategy getRetryStrategy(JobInfo jobInfo) {
    int flakyTestAttempts =
        jobInfo.params().getInt(FlakyTestRetryConstants.PARAM_FLAKY_TEST_ATTEMPTS, -1);
    if (flakyTestAttempts >= 1) {
      jobInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "%s is set to %d. Using FlakyTestRetryStrategy. All retry_level/test_attempts args"
                  + " will be ignored.",
              FlakyTestRetryConstants.PARAM_FLAKY_TEST_ATTEMPTS, flakyTestAttempts);
      return flakyTestRetryStrategy;
    }
    jobInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Using DefaultRetryStrategy. Will respect retry_level/test_attempts args.");
    return defaultRetryStrategy;
  }

  /** Returns the test which generated the given retry test. */
  private static Optional<TestInfo> getForegoingTest(TestInfo testInfo) {
    return testInfo
        .properties()
        .getOptional(Test.FOREGOING_TEST_ID)
        .flatMap(testId -> Optional.ofNullable(testInfo.jobInfo().tests().getById(testId)));
  }

  private static void addFinalAttemptProperty(TestInfo currentTestInfo) {
    currentTestInfo.properties().add(Test.IS_FINAL_ATTEMPT, "true");
  }
}
