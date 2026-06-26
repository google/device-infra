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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.proto.ErrorIdProto.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.NamespaceProto.Namespace;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.container.proto.ModeSettingProto.ContainerModePreference;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/** Default strategy deciding whether a finished test should be retried. */
public class DefaultRetryStrategy {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String PARAM_RETRY_ON_TIMEOUT = "retry_on_timeout";
  private static final String PROPERTY_REPEAT_INDEX = Ascii.toLowerCase(Test.REPEAT_INDEX.name());

  /** Blocklist for disabling the extra retry for infra errors. */
  private static final ImmutableSet<String> DRIVER_BLOCK_LIST_FOR_INFRA_ERROR_EXTRA_RETRY =
      ImmutableSet.of(
          "AndroidTradefedTest",
          "AndroidCopycatRemoteControlledMoblySnippetTest",
          "IosCopycatRemoteControlledMoblySnippetTest",
          "IosNativeXcTest",
          "ManekiTest",
          "MoblyAospTest",
          "TradefedTest",
          "VegaTest",
          "YtsTest");

  /** Blocklist for disabling the extra retry for infra errors by user. */
  private static final ImmutableSet<String> USER_BLOCK_LIST_FOR_INFRA_ERROR_EXTRA_RETRY =
      ImmutableSet.of("ytlr-lab-users");

  /** Minimal job remaining time needed for triggering the extra retry for infra errors. */
  private static final Duration MIN_JOB_REMAINING_TIME_FOR_INFRA_ERROR_EXTRA_RETRY =
      Duration.ofMinutes(5);

  /**
   * If the error attempt runs longer than this threshold, DOES NOT trigger the extra retry for
   * infra errors.
   */
  private static final Duration MAX_TEST_DURATION_FOR_INFRA_ERROR_EXTRA_RETRY = Duration.ofHours(2);

  private static final long MAX_RETRY_ATTEMPTS_FOR_DRAIN_TIMEOUT = 5;

  /**
   * Struct containing details of a retry decision.
   *
   * @param shouldRetry Whether the test should be retried.
   * @param retryReason The reason for the retry. If null, the test will not be retried. If not
   *     null, the framework will create a new test attempt with the given reason.
   * @param validAttemptNum The number of valid attempts until the current test, not including the
   *     retry.
   * @param newTestProperties Properties to be added to the new test attempt if a retry is made.
   */
  public record RetryInfo(
      boolean shouldRetry,
      @Nullable String retryReason,
      int validAttemptNum,
      Map<String, String> newTestProperties) {}

  public DefaultRetryStrategy() {}

  /** Decide whether the test should be retried. */
  public RetryInfo decideRetryOnTestEnd(TestInfo currentTestInfo)
      throws MobileHarnessException, InterruptedException {
    JobInfo jobInfo = currentTestInfo.jobInfo();
    Retry retrySetting = jobInfo.setting().getRetry();
    Retry.Level retryLevel = retrySetting.getRetryLevel();

    // Do not retry if already retry enough times.
    int validAttemptNum =
        (int)
            getAllAttempts(jobInfo, currentTestInfo).stream()
                .filter(DefaultRetryStrategy::isValidAttempt)
                .count();
    if (validAttemptNum > retrySetting.getTestAttempts()) {
      return new RetryInfo(false, null, validAttemptNum, ImmutableMap.of());
    }

    TestResult testResult = currentTestInfo.resultWithCause().get().type();
    Optional<ExceptionDetail> cause = currentTestInfo.resultWithCause().get().causeProto();
    ErrorId criticalErrorId =
        cause.isPresent() ? ErrorModelConverter.getCriticalErrorId(cause.get()) : null;
    String retryReason = null;
    if (validAttemptNum < retrySetting.getTestAttempts()) {
      if (isPotentialContainerError(currentTestInfo)) {
        retryReason = "POTENTIAL_CONTAINER_ISSUE";
      } else if (isRetryableForDrainTimeout(currentTestInfo)) {
        retryReason = "DRAIN_TIMEOUT_ERROR";
      } else if ((retryLevel == Retry.Level.ERROR
              && testResult != TestResult.PASS
              && testResult != TestResult.FAIL
              && testResult != TestResult.SKIP)
          || (retryLevel == Retry.Level.FAIL
              && testResult != TestResult.PASS
              && testResult != TestResult.SKIP)) {
        boolean retryOnTimeout = jobInfo.params().getBool(PARAM_RETRY_ON_TIMEOUT, true);
        boolean isTimeout =
            testResult == TestResult.TIMEOUT
                || cause.map(DefaultRetryStrategy::isCustomerTestExecutionTimeout).orElse(false);
        if (retryOnTimeout || !isTimeout) {
          currentTestInfo.log().atInfo().log(
              "testResult: %s, retryOnTimeout: %s, isTimeout: %s, cause: %s",
              testResult,
              retryOnTimeout,
              isTimeout,
              cause.map(ExceptionDetail::toString).orElse("N/A"));
          retryReason = "TEST_" + testResult;
        }
      }
    } else if (validAttemptNum == retrySetting.getTestAttempts()
        && !DRIVER_BLOCK_LIST_FOR_INFRA_ERROR_EXTRA_RETRY.contains(jobInfo.type().getDriver())
        && !USER_BLOCK_LIST_FOR_INFRA_ERROR_EXTRA_RETRY.contains(jobInfo.jobUser().getRunAs())
        && validAttemptNum
            == getAllAttempts(jobInfo, currentTestInfo)
                .size() /* no auto retry for container / drain */) {
      // Have another retry for the INFRA_ISSUE even when the max attempt number is reached.
      if (testResult != TestResult.PASS && testResult != TestResult.SKIP && cause.isPresent()) {
        if (criticalErrorId.getType() == ErrorType.INFRA_ISSUE
            && criticalErrorId.getNamespace().equals(Namespace.MH)) {
          retryReason = "EXTRA_RETRY_FOR_INFRA_ISSUE_AS_CRITICAL_ERROR";
        } else if (ErrorModelConverter.hasMhInfraIssue(cause.get())) {
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
              retryReason = null; // No retry for tests running longer than the remaining job time.
            }
          }
          if (message != null) {
            jobInfo.log().atInfo().alsoTo(logger).log("%s", message);
            currentTestInfo.log().atInfo().log("%s", message);
          }
        }
      }
    }
    ImmutableMap.Builder<String, String> newTestProperties = ImmutableMap.builder();
    if (retryReason != null) {
      if (criticalErrorId != null
          && Ascii.equalsIgnoreCase(
              criticalErrorId.getName(),
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_NO_VALID_UID_ASSIGNED
                  .name())) {
        newTestProperties.put(
            Ascii.toLowerCase(Test.RETRY_AFTER_NO_VALID_UID_ASSIGNED.name()), "true");
      }
      if (isRetryableForDrainTimeout(currentTestInfo)) {
        newTestProperties.put(
            Ascii.toLowerCase(Test._DRAIN_TIMEOUT_RETRY_ATTEMPTS.name()),
            Long.toString(
                currentTestInfo.properties().getLong(Test._DRAIN_TIMEOUT_RETRY_ATTEMPTS).orElse(0L)
                    + 1L));
      }

      // RETRY_AFTER_CONTAINER_FAILS will fallback to non-container-mode test.
      String currentTestId = currentTestInfo.locator().getId();
      boolean isCurrentContainerMode =
          currentTestInfo.properties().getBoolean(Test.CONTAINER_MODE).orElse(false);
      if (isCurrentContainerMode) {
        logger.atInfo().log("Retry container-mode test [%s] after it got error.", currentTestId);
        newTestProperties.put(Ascii.toLowerCase(Test.RETRY_AFTER_CONTAINER_FAILS.name()), "true");
      }
      if (!isCurrentContainerMode) {
        newTestProperties.put(Ascii.toLowerCase(Test.CONTAINER_MODE.name()), "false");
      }
    }
    return new RetryInfo(
        retryReason != null, retryReason, validAttemptNum, newTestProperties.buildOrThrow());
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

  // We want to make retry after container failure / drain timeout user-invisible. Therefore, we
  // only consider tests which are not potentially container error or drain timeout as valid
  // attempts.
  private static boolean isValidAttempt(TestInfo testInfo) {
    return !isPotentialContainerError(testInfo) && !isRetryableForDrainTimeout(testInfo);
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
    TestResult testResult = testInfo.resultWithCause().get().type();
    return !isMandatoryContainerMode(testInfo.jobInfo())
        && testInfo.properties().getBoolean(Test.CONTAINER_MODE).orElse(false)
        && testResult != TestResult.PASS
        && testResult != TestResult.FAIL
        && !(testResult == TestResult.ERROR
            && testInfo
                    .resultWithCause()
                    .get()
                    .causeProtoNonEmpty()
                    .getSummary()
                    .getErrorId()
                    .getType()
                == ErrorType.CUSTOMER_ISSUE);
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

  private static boolean isCustomerTestExecutionTimeout(ExceptionDetail detail) {
    return ErrorModelConverter.getCriticalErrorId(detail)
        .getName()
        .equals(BasicErrorId.CUSTOMER_TEST_EXECUTION_TIMEOUT_EXCEPTION_WRAPPER.name());
  }

  /**
   * Checks whether the user specifies the parameter "container_mode_preference" as
   * "mandatory_container".
   *
   * <p>If yes, we consider the user only wants to execute the test in container. Therefore, we
   * won't retry the test in MH stacks.
   */
  private static boolean isMandatoryContainerMode(JobInfo jobInfo) {
    return Ascii.equalsIgnoreCase(
        ContainerModePreference.MANDATORY_CONTAINER.name(),
        jobInfo
            .params()
            .get(
                JobInfo.PARAM_CONTAINER_MODE_PREFERENCE,
                ContainerModePreference.CONTAINER_MODE_PREFERENCE_UNSPECIFIED.name()));
  }
}
