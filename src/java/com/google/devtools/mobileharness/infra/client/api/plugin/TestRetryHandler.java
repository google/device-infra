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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.proto.ErrorIdProto.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.util.result.ClientAllocErrorUtil;
import com.google.devtools.mobileharness.infra.container.proto.ModeSettingProto.ContainerModePreference;
import com.google.devtools.mobileharness.infra.container.proto.ModeSettingProto.SandboxModePreference;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.common.UtpMode;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.proto.IncompatibleReasonProto.InfraIncompatibleReason;
import com.google.wireless.qa.mobileharness.client.api.event.JobStartEvent;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndedEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Result;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** The test event handler for generating multiple attempts or retrying tests. */
public class TestRetryHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String PROPERTY_REPEAT_INDEX = Ascii.toLowerCase(Test.REPEAT_INDEX.name());

  private static final ImmutableSet<String> INHERITED_TEST_PROPERTIES =
      ImmutableSet.of(
          Ascii.toLowerCase(Test._DRAIN_TIMEOUT_RETRY_ATTEMPTS.name()), PROPERTY_REPEAT_INDEX);

  /** Blocklist for disabling the extra retry for infra errors. */
  private static final ImmutableSet<String> DRIVER_BLOCK_LIST_FOR_INFRA_ERROR_EXTRA_RETRY =
      ImmutableSet.of(
          "AndroidTradefedTest",
          "AndroidCopycatRemoteControlledMoblySnippetTest",
          "IosCopycatRemoteControlledMoblySnippetTest",
          "MoblyAospPackageTest",
          "XtsTradefedTest");

  /** Minimal job remaining time needed for triggering the extra retry for infra errors. */
  private static final Duration MIN_JOB_REMAINING_TIME_FOR_INFRA_ERROR_EXTRA_RETRY =
      Duration.ofMinutes(5);

  /**
   * If the error attempt runs longer than this threshold, DOES NOT trigger the extra retry for
   * infra errors.
   */
  private static final Duration MAX_TEST_DURATION_FOR_INFRA_ERROR_EXTRA_RETRY = Duration.ofHours(2);

  private static final long MAX_RETRY_ATTEMPTS_FOR_DRAIN_TIMEOUT = 5;

  private final DeviceAllocator deviceAllocator;

  public TestRetryHandler(DeviceAllocator deviceAllocator) {
    this.deviceAllocator = deviceAllocator;
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
    for (TestInfo testInfo : jobInfo.tests().getAll().values()) {
      String testName = testInfo.locator().getName();
      testInfo.properties().add(PROPERTY_REPEAT_INDEX, "1");
      for (int i = 2; i <= repeatRuns; i++) {
        jobInfo.tests().add(testName).properties().add(PROPERTY_REPEAT_INDEX, Integer.toString(i));
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
      currentTestInfo.properties().add(Ascii.toLowerCase(Test.IS_FINAL_ATTEMPT.name()), "true");
      // Already handled in JobStartEvent. Ignore.
      return;
    }

    Optional<TestInfo> foregoingTest = getForegoingTest(currentTestInfo);
    Optional<UtpMode> currentUtpMode = getUtpMode(currentTestInfo);

    TestResult currentTestResult = currentTestInfo.result().get();
    Optional<TestResult> foregoingTestResult = foregoingTest.map(TestInfo::result).map(Result::get);

    boolean isPassAfterRetry =
        foregoingTestResult.isPresent()
            && TestResult.PASS != foregoingTestResult.get()
            && TestResult.PASS == currentTestResult;
    if (isPassAfterRetry) {
      // Use cases:
      // 1) Show the tests failed because of UTP:
      //    NONPASSING_BEFORE_RETRY_PASS=true && UTP_MODE=UMTS_UTP/MH_HYBRID_UTP
      // 2) Show the tests failed because of Sandbox:
      //    NONPASSING_BEFORE_RETRY_PASS=true & SANDBOX_MODE/CONTAINER_MODE=true
      // 3) Show the failed/error tests that are saved by a general retry:
      //    NONPASSING_BEFORE_RETRY_PASS=true & SANDBOX_MODE&CONTAINER_MODE=false & UTP_MODE=null
      foregoingTest
          .get()
          .properties()
          .add(Test.NONPASSING_BEFORE_RETRY_PASS, Boolean.TRUE.toString());
      // See b/184734364. Since the foregoing test has ended and nowadays MossUploader will not
      // update test after it ends, we need to explicitly tell MossUploader to update the foregoing
      // test when the job ends.
      foregoingTest
          .get()
          .properties()
          .add(Test.VOLATILE_TEST_INFO_AFTER_TEST_ENDS, Boolean.TRUE.toString());

      // Use cases:
      // 1) Show the pass retry attempts after a UTP fail/error run:
      //    PASS_AFTER_RETRY=true && RETRY_REASON=POTENTIAL_{UMTS_UTP/MH_HYBRID_UTP}_ISSUE
      // 2) Show the pass retry attempts after a container fail/error runs:
      //    PASS_AFTER_RETRY=true && RETRY_REASON=POTENTIAL_CONTAINER_ISSUE
      // 3) Show the failed/error tests that are saved by a general retry:
      //    NONPASSING_BEFORE_RETRY_PASS=true & SANDBOX_MODE&CONTAINER_MODE=false & UTP_MODE=null
      // 4) Show the INFRA_ISSUE tests that are saved by an extra retry:
      //    PASS_AFTER_RETRY=true && RETRY_REASON=EXTRA_RETRY_FOR_INFRA_ISSUE
      currentTestInfo.properties().add(Test.PASS_AFTER_RETRY, Boolean.TRUE.toString());

      currentTestInfo.properties().add(Ascii.toLowerCase(Test.IS_FINAL_ATTEMPT.name()), "true");
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

      currentTestInfo.properties().add(Ascii.toLowerCase(Test.IS_FINAL_ATTEMPT.name()), "true");
      return;
    }

    long validAttemptNum =
        getAllAttempts(jobInfo, currentTestInfo).stream()
            .filter(TestRetryHandler::isValidAttempt)
            .count();
    String testName = currentTestInfo.locator().getName();

    // Makes sure the check logic below is the same as the one in {@link
    // GoogleAnalyticsUploader.isFinalTestResult()}.
    if (!jobInfo.timer().isExpired() && validAttemptNum <= retrySetting.getTestAttempts()) {
      TestResult testResult = currentTestInfo.result().get();
      Optional<ExceptionDetail> cause = currentTestInfo.resultWithCause().get().causeProto();
      ErrorId criticalErrorId =
          cause.isPresent() ? ErrorModelConverter.getCriticalErrorId(cause.get()) : null;
      String retryReason = null;
      if (validAttemptNum < retrySetting.getTestAttempts()) {
        if (isPotentialContainerError(currentTestInfo)) {
          retryReason = "POTENTIAL_CONTAINER_ISSUE";
        } else if (isPotentialUtpError(currentTestInfo)) {
          retryReason = "POTENTIAL_" + currentUtpMode.get().name() + "_ISSUE";
        } else if (isRetryableForDrainTimeout(currentTestInfo)) {
          retryReason = "DRAIN_TIMEOUT_ERROR";
        } else if ((retryLevel == Retry.Level.ERROR
                && testResult != TestResult.PASS
                && testResult != TestResult.FAIL
                && testResult != TestResult.SKIP)
            || (retryLevel == Retry.Level.FAIL
                && testResult != TestResult.PASS
                && testResult != TestResult.SKIP)) {
          retryReason = "TEST_" + testResult;
        }
      } else if (validAttemptNum == retrySetting.getTestAttempts()
          && !DRIVER_BLOCK_LIST_FOR_INFRA_ERROR_EXTRA_RETRY.contains(jobInfo.type().getDriver())
          && validAttemptNum
              == getAllAttempts(jobInfo, currentTestInfo)
                  .size() /* no auto retry for UTP/sandbox */) {
        // Have another retry for the INFRA_ISSUE even when the max attempt number is reached.
        if (testResult != TestResult.PASS && testResult != TestResult.SKIP && cause.isPresent()) {
          if (criticalErrorId.getType() == ErrorType.INFRA_ISSUE) {
            retryReason = "EXTRA_RETRY_FOR_INFRA_ISSUE_AS_CRITICAL_ERROR";
          } else if (ErrorModelConverter.hasInfraIssue(cause.get())) {
            retryReason = "EXTRA_RETRY_FOR_INFRA_ISSUE_AS_CAUSE_OR_SUPPRESSED_ERROR";
          }
          if (retryReason != null) {
            // Double check whether the time is enough for the extra retry. If not, cancel the
            // retry.
            String message = null;
            if (jobInfo
                    .timer()
                    .remainingTimeJava()
                    .compareTo(MIN_JOB_REMAINING_TIME_FOR_INFRA_ERROR_EXTRA_RETRY)
                < 0) {
              message =
                  String.format(
                      "Skip the extra retry for INFRA_ISSUE because the job remaining time(%s) <"
                          + " %s",
                      jobInfo.timer().remainingTimeJava(),
                      MIN_JOB_REMAINING_TIME_FOR_INFRA_ERROR_EXTRA_RETRY);
              retryReason = null; // No retry when job remaining time is less than 5 minutes.
            } else if (currentTestInfo.timing().getStartTime() != null) {
              Duration testDuration =
                  Duration.between(
                      currentTestInfo.timing().getStartTime(), Clock.systemUTC().instant());
              if (testDuration.compareTo(MAX_TEST_DURATION_FOR_INFRA_ERROR_EXTRA_RETRY) >= 0) {
                message =
                    String.format(
                        "Skip the extra retry for INFRA_ISSUE because the current test"
                            + " attempt(test_id=%s) duration(%s) >= %s",
                        currentTestInfo.locator().getId(),
                        testDuration,
                        MAX_TEST_DURATION_FOR_INFRA_ERROR_EXTRA_RETRY);
                retryReason = null; // No retry for tests running longer than 2 hours.
              }
              if (testDuration.compareTo(jobInfo.timer().remainingTimeJava()) > 0) {
                message =
                    String.format(
                        "Skip the extra retry for INFRA_ISSUE because the job remaining time(%s) <"
                            + " current test attempt(test_id=%s) duration(%s)",
                        jobInfo.timer().remainingTimeJava(),
                        currentTestInfo.locator().getId(),
                        testDuration);
                retryReason =
                    null; // No retry for tests running longer than the remaining job time.
              }
            }
            if (message != null) {
              jobInfo.log().atInfo().alsoTo(logger).log("%s", message);
              currentTestInfo.log().atInfo().log("%s", message);
            }
          }
        }
      }
      if (retryReason != null) {
        TestInfo newTest = addNewTest(jobInfo, currentTestInfo, validAttemptNum);
        newTest.properties().add(Test.RETRY_REASON, retryReason);

        if (criticalErrorId != null
            && Ascii.equalsIgnoreCase(
                criticalErrorId.getName(),
                AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_NO_VALID_UID_ASSIGNED
                    .name())) {
          newTest.properties().add(Test.RETRY_AFTER_NO_VALID_UID_ASSIGNED, "true");
        }

        // Update left retry times for drain timeout.
        if (isRetryableForDrainTimeout(currentTestInfo)) {
          long unused = newTest.properties().plusLong(Test._DRAIN_TIMEOUT_RETRY_ATTEMPTS, 1L);
        }

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
        return;
      }
    }

    currentTestInfo.properties().add(Ascii.toLowerCase(Test.IS_FINAL_ATTEMPT.name()), "true");
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

  private static TestInfo addNewTest(
      JobInfo jobInfo, TestInfo currentTestInfo, long validAttemptNum)
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
    newTestInfo.properties().add(Test.FOREGOING_TEST_RESULT, currentTestInfo.result().get().name());
    if (validAttemptNum > 0) {
      // If there have been valid attempts, set the index for the retry
      newTestInfo.properties().add(Test.RETRY_INDEX, Long.toString(validAttemptNum));
    }
    currentTestInfo.properties().add(Test.RETRY_TEST_ID, newTestInfo.locator().getId());

    // Disable hybrid UTP mode for all retries, excepts the following cases:
    // 1) The first attempts can run with hybrid UTP
    // 2) If "enable_mh_hybrid_utp_mode" is set to true, all attempts should run with hybrid mode.
    // 3) If the test has user specified UTP configs, all attempts should run with hybrid mode.
    if (!isForcedHybridUtpMode(currentTestInfo)) {
      newTestInfo
          .properties()
          .add(
              Test.HYBRID_UTP_FORCIBLY_DISABLE,
              Ascii.toLowerCase(InfraIncompatibleReason.TEST_RETRY.name()));
    }

    // RETRY_AFTER_SANDBOX_FAILS will fallback to container-mode + non-sandbox-mode test.
    // RETRY_AFTER_CONTAINER_FAILS will fallback to non-container-mode test.
    boolean isCurrentContainerMode =
        currentTestInfo.properties().getBoolean(Test.CONTAINER_MODE).orElse(false);
    boolean isCurrentSandboxMode =
        currentTestInfo.properties().getBoolean(Test.SANDBOX_MODE).orElse(false);
    if (isCurrentSandboxMode) {
      logger.atInfo().log("Retry sandbox-mode test [%s] after it got error.", currentTestId);
      newTestInfo.properties().add(Test.RETRY_AFTER_SANDBOX_FAILS, "true");
    } else if (isCurrentContainerMode) {
      logger.atInfo().log("Retry container-mode test [%s] after it got error.", currentTestId);
      newTestInfo.properties().add(Test.RETRY_AFTER_CONTAINER_FAILS, "true");
    }

    // If the foregoing test is non-sandbox/non-container, make the next test
    // non-sandbox/non-container.
    if (!isCurrentSandboxMode) {
      newTestInfo.properties().add(Test.SANDBOX_MODE, "false");
    }
    if (!isCurrentContainerMode) {
      newTestInfo.properties().add(Test.CONTAINER_MODE, "false");
    }

    return newTestInfo;
  }

  /** Returns the test which generated the given retry test. */
  private static Optional<TestInfo> getForegoingTest(TestInfo testInfo) {
    return testInfo
        .properties()
        .getOptional(Test.FOREGOING_TEST_ID)
        .flatMap(testId -> Optional.ofNullable(testInfo.jobInfo().tests().getById(testId)));
  }

  // We want to make retry after container/sandbox/UTP failure user-invisible. Therefore, we only
  // consider tests which are not potentially container/UTP error as valid attempts.
  private static boolean isValidAttempt(TestInfo testInfo) {
    return !isPotentialContainerError(testInfo)
        && !isPotentialUtpError(testInfo)
        && !isRetryableForDrainTimeout(testInfo);
  }

  /**
   * We consider tests satisfying the following requirement a potential container error:
   *
   * <ol>
   *   <li>It runs in container-mode.
   *   <li>Its result is not PASS or FAIL.
   *   <li>It has error but not customer issue error.
   * </ol>
   */
  private static boolean isPotentialContainerError(TestInfo testInfo) {
    TestResult testResult = testInfo.result().get();
    return !isMandatorySandboxOrContainerMode(testInfo.jobInfo())
        && testInfo.properties().getBoolean(Test.CONTAINER_MODE).orElse(false)
        && testResult != TestResult.PASS
        && testResult != TestResult.FAIL
        && !(testResult == TestResult.ERROR
            && testInfo.resultWithCause().get().causeNonEmpty().getSummary().getErrorType()
                == ErrorType.CUSTOMER_ISSUE);
  }

  /**
   * We consider tests satisfying the following requirement a potential UTP TestRunner error:
   *
   * <ol>
   *   <li>It runs in any UTP mode.
   *   <li>Its result is not PASS or Skipped.
   *   <li>The parameter "enable_mh_hybrid_utp_mode" is not set to true.
   *   <li>The test does not have user specified UTP configs.
   * </ol>
   */
  private static boolean isPotentialUtpError(TestInfo currentTestInfo) {
    TestResult currentTestResult = currentTestInfo.result().get();
    Optional<UtpMode> currentUtpMode = getUtpMode(currentTestInfo);
    return !isForcedHybridUtpMode(currentTestInfo)
        && currentUtpMode.isPresent()
        && currentTestResult != TestResult.PASS
        && currentTestResult != TestResult.SKIP;
  }

  /** Check if the test is retryable for drain timeout. */
  private static boolean isRetryableForDrainTimeout(TestInfo testInfo) {
    Optional<ExceptionDetail> cause = testInfo.resultWithCause().get().causeProto();
    return cause
            .map(ErrorModelConverter::getCriticalErrorId)
            .map(
                errorId ->
                    Ascii.equalsIgnoreCase(
                        errorId.getName(),
                        InfraErrorId.TR_TEST_DRAIN_TIMEOUT_AND_FORCE_CLEAN_UP.name()))
            .orElse(false)
        && testInfo.properties().getLong(Test._DRAIN_TIMEOUT_RETRY_ATTEMPTS).orElse(0L)
            < MAX_RETRY_ATTEMPTS_FOR_DRAIN_TIMEOUT;
  }

  /** Retrieves the UtpMode type from the test property if the given test is running in UTP Mode. */
  private static Optional<UtpMode> getUtpMode(TestInfo testInfo) {
    String utpModeStr = testInfo.properties().get(Test.UTP_MODE);
    if (utpModeStr != null) {
      try {
        return Optional.of(UtpMode.valueOf(utpModeStr));
      } catch (IllegalArgumentException e) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Unknown UtpMode in test property: %s", utpModeStr);
        // Add warning to TestInfo.warnings().
      }
    }
    return Optional.empty();
  }

  /**
   * Checks whether the user specifies the parameter "enable_mh_hybrid_utp_mode" is set to true or
   * the test has user specified UTP configs.
   *
   * <p>If yes, we consider the user only wants to execute the test in UTP hybrid mode. Therefore,
   * we won't retry the test in MH stacks.
   */
  private static boolean isForcedHybridUtpMode(TestInfo testInfo) {
    return testInfo.jobInfo().params().isTrue("enable_mh_hybrid_utp_mode")
        || testInfo.properties().getBoolean(Test.HAS_USER_UTP_CONFIG).orElse(false);
  }

  /**
   * Checks whether the user specifies the parameter "sandbox_mode_preference" as
   * "mandatory_sandbox" or the parameter "container_mode_preference" as "mandatory_conteiner".
   *
   * <p>If yes, we consider the user only wants to execute the test in container/sandbox. Therefore,
   * we won't retry the test in MH stacks.
   */
  private static boolean isMandatorySandboxOrContainerMode(JobInfo jobInfo) {
    return Ascii.equalsIgnoreCase(
            SandboxModePreference.MANDATORY_SANDBOX.name(),
            jobInfo
                .params()
                .get(
                    JobInfo.PARAM_SANDBOX_MODE_PREFERENCE,
                    SandboxModePreference.SANDBOX_MODE_PREFERENCE_UNSPECIFIED.name()))
        || Ascii.equalsIgnoreCase(
            ContainerModePreference.MANDATORY_CONTAINER.name(),
            jobInfo
                .params()
                .get(
                    JobInfo.PARAM_CONTAINER_MODE_PREFERENCE,
                    ContainerModePreference.CONTAINER_MODE_PREFERENCE_UNSPECIFIED.name()));
  }
}
