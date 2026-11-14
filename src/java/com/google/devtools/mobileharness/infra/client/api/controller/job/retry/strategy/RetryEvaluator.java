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
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy.RetryStrategy.RetryInfo;
import com.google.devtools.mobileharness.infra.client.api.util.result.ClientAllocErrorUtil;
import com.google.devtools.mobileharness.infra.container.proto.ModeSettingProto.ContainerModePreference;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Pure, side-effect-free retry evaluator shared by V1 {@link RetryStrategy} implementations and
 * Gateway V2 worker stages.
 */
public final class RetryEvaluator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String PARAM_RETRY_ON_TIMEOUT = "retry_on_timeout";

  /** Default container mode preference name when the job param is unset. */
  public static final String DEFAULT_CONTAINER_MODE_PREFERENCE =
      ContainerModePreference.CONTAINER_MODE_PREFERENCE_UNSPECIFIED.name();

  private static final String PROPERTY_HALT_RETRY = Ascii.toLowerCase(Test.HALT_RETRY.name());
  private static final String PROPERTY_RETRY_REASON = Ascii.toLowerCase(Test.RETRY_REASON.name());
  private static final String PROPERTY_FOREGOING_TEST_ID =
      Ascii.toLowerCase(Test.FOREGOING_TEST_ID.name());
  private static final String PROPERTY_FOREGOING_TEST_RESULT =
      Ascii.toLowerCase(Test.FOREGOING_TEST_RESULT.name());
  private static final String PROPERTY_RETRY_INDEX = Ascii.toLowerCase(Test.RETRY_INDEX.name());
  private static final String PROPERTY_CONTAINER_MODE =
      Ascii.toLowerCase(Test.CONTAINER_MODE.name());
  private static final String PROPERTY_RETRY_AFTER_CONTAINER_FAILS =
      Ascii.toLowerCase(Test.RETRY_AFTER_CONTAINER_FAILS.name());
  private static final String PROPERTY_RETRY_AFTER_NO_VALID_UID_ASSIGNED =
      Ascii.toLowerCase(Test.RETRY_AFTER_NO_VALID_UID_ASSIGNED.name());
  private static final String PROPERTY_DRAIN_TIMEOUT_RETRY_ATTEMPTS =
      Ascii.toLowerCase(Test._DRAIN_TIMEOUT_RETRY_ATTEMPTS.name());

  private static final ImmutableSet<String> INHERITED_TEST_PROPERTIES =
      ImmutableSet.of(
          Ascii.toLowerCase(Test.SHARD_COUNT.name()),
          Ascii.toLowerCase(Test.SHARD_INDEX.name()),
          Ascii.toLowerCase(
              Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_SMART_SHARD_TEST_NAMES.name()),
          PROPERTY_DRAIN_TIMEOUT_RETRY_ATTEMPTS,
          Ascii.toLowerCase(Test.REPEAT_INDEX.name()));

  /** Blocklist for disabling the extra retry for infra errors by driver. */
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
      ImmutableSet.of("ytlr-lab-users", "pte-sensor-lab-jobs", "fusion-guitar-jobs");

  /** Minimal job remaining time needed for triggering the extra retry for infra errors. */
  private static final Duration MIN_JOB_REMAINING_TIME_FOR_INFRA_ERROR_EXTRA_RETRY =
      Duration.ofMinutes(5);

  /**
   * Maximum test duration for which an extra retry on infra error is allowed (exclusive upper
   * bound).
   */
  private static final Duration MAX_TEST_DURATION_FOR_INFRA_ERROR_EXTRA_RETRY = Duration.ofHours(2);

  private static final long MAX_RETRY_ATTEMPTS_FOR_DRAIN_TIMEOUT = 5;

  private static final ImmutableSet<TestResult> FLAKY_NON_RETRYABLE_RESULTS =
      ImmutableSet.of(TestResult.PASS, TestResult.SKIP, TestResult.ABORT);

  private static final ImmutableSet<TestResult> FLAKY_ERROR_RESULTS =
      ImmutableSet.of(TestResult.ERROR, TestResult.TIMEOUT, TestResult.UNKNOWN);

  /** Lazy supplier of remaining job time, invoked only when extra-infra-retry time checks run. */
  @FunctionalInterface
  interface RemainingTimeSupplier {
    Duration get() throws MobileHarnessException;
  }

  /**
   * Job-level retry configuration and metadata.
   *
   * @param retrySetting the job's {@link Retry} setting
   * @param driver the job driver name
   * @param runAsSupplier supplier of the effective run-as user for the job
   * @param flakyTestAttempts value of {@code flaky_test_attempts}, or non-positive if unset
   * @param flakyTestParallelRetry whether {@code flaky_test_parallel_retry} is enabled
   * @param retryOnTimeout whether {@code retry_on_timeout} is enabled (default {@code true})
   * @param containerModePreference value of {@code container_mode_preference}
   */
  public record JobRetryContext(
      Retry retrySetting,
      String driver,
      Supplier<String> runAsSupplier,
      int flakyTestAttempts,
      boolean flakyTestParallelRetry,
      boolean retryOnTimeout,
      String containerModePreference) {

    public JobRetryContext(
        Retry retrySetting,
        String driver,
        String runAs,
        int flakyTestAttempts,
        boolean flakyTestParallelRetry,
        boolean retryOnTimeout,
        String containerModePreference) {
      this(
          retrySetting,
          driver,
          () -> runAs,
          flakyTestAttempts,
          flakyTestParallelRetry,
          retryOnTimeout,
          containerModePreference);
    }

    JobRetryContext withoutContainerFallback() {
      return new JobRetryContext(
          retrySetting,
          driver,
          runAsSupplier,
          flakyTestAttempts,
          flakyTestParallelRetry,
          retryOnTimeout,
          ContainerModePreference.MANDATORY_CONTAINER.name());
    }
  }

  /**
   * Immutable snapshot of a finished test attempt.
   *
   * @param testId the attempt's test ID
   * @param result the attempt's {@link TestResult}
   * @param cause optional failure cause proto
   * @param properties the attempt's test properties (without internal worker bookkeeping entries)
   * @param startTime when the attempt started executing, if recorded
   * @param endTime when the attempt finished executing, if recorded; falls back to evaluation
   *     {@code now} when empty
   */
  public record AttemptSnapshot(
      String testId,
      TestResult result,
      Optional<ExceptionDetail> cause,
      ImmutableMap<String, String> properties,
      Optional<Instant> startTime,
      Optional<Instant> endTime) {

    public AttemptSnapshot(
        String testId,
        TestResult result,
        Optional<ExceptionDetail> cause,
        ImmutableMap<String, String> properties,
        Optional<Instant> startTime) {
      this(testId, result, cause, properties, startTime, Optional.empty());
    }
  }

  /**
   * Result of {@link #decideDefaultRetry}.
   *
   * @param retries planned retry decisions (empty when no retry should be triggered)
   * @param skipMessage optional diagnostic log message for job/test log when an extra retry was
   *     skipped
   * @param testLogMessage optional diagnostic log message for {@code currentTestInfo.log()} on
   *     normal retry evaluation
   */
  record StrategyDecision(
      ImmutableList<RetryInfo> retries,
      Optional<String> skipMessage,
      Optional<String> testLogMessage) {}

  /**
   * Result of {@link #decideFlakyRetry}.
   *
   * @param retries planned retry decisions (empty when no retry should be triggered)
   * @param jobLogMessage optional diagnostic log message for {@code jobInfo.log()}
   */
  record FlakyDecision(ImmutableList<RetryInfo> retries, Optional<String> jobLogMessage) {}

  /** Returns the property names carried over from a retried attempt to its retry. */
  public static ImmutableSet<String> inheritedTestProperties() {
    return INHERITED_TEST_PROPERTIES;
  }

  /** Returns whether the job uses flaky test retry semantics instead of {@link Retry}. */
  public static boolean usesFlakyRetry(Retry.Level retryLevel, int flakyTestAttempts) {
    return retryLevel != Retry.Level.ALL && flakyTestAttempts > 0;
  }

  /**
   * Returns whether the attempt timed out as {@code DefaultRetryStrategy} counts it for {@code
   * retry_on_timeout}: with a {@code TIMEOUT} result, or with a customer test execution timeout as
   * the critical error of its cause.
   */
  public static boolean isTimeout(TestResult result, Optional<ExceptionDetail> cause) {
    return result == TestResult.TIMEOUT
        || cause.map(RetryEvaluator::isCustomerTestExecutionTimeout).orElse(false);
  }

  /**
   * Returns whether the critical error of {@code detail} is a customer execution timeout wrapper.
   */
  private static boolean isCustomerTestExecutionTimeout(ExceptionDetail detail) {
    return ErrorModelConverter.getCriticalErrorId(detail)
        .getName()
        .equals(BasicErrorId.CUSTOMER_TEST_EXECUTION_TIMEOUT_EXCEPTION_WRAPPER.name());
  }

  /**
   * Returns whether an attempt is eligible for a free retry due to lab drain timeout, given its
   * failure cause and the number of drain-timeout retries already used.
   */
  private static boolean isRetryableForDrainTimeout(
      Optional<ExceptionDetail> cause, long priorDrainRetries) {
    return cause
            .map(ErrorModelConverter::getCriticalErrorId)
            .map(errorId ->
                Ascii.equalsIgnoreCase(
                    errorId.getName(),
                    InfraErrorId.TR_TEST_DRAIN_TIMEOUT_AND_FORCE_CLEAN_UP.name()))
            .orElse(false)
        && priorDrainRetries < MAX_RETRY_ATTEMPTS_FOR_DRAIN_TIMEOUT;
  }

  /**
   * Returns whether an attempt failed with a potential container issue and may fall back to
   * non-container execution without consuming {@code test_attempts}.
   */
  private static boolean isPotentialContainerError(
      String containerModePreference, AttemptSnapshot attempt) {
    TestResult testResult = attempt.result();
    return !Ascii.equalsIgnoreCase(
            ContainerModePreference.MANDATORY_CONTAINER.name(), containerModePreference)
        && Ascii.equalsIgnoreCase(
            attempt.properties().getOrDefault(PROPERTY_CONTAINER_MODE, ""), "true")
        && testResult != TestResult.PASS
        && testResult != TestResult.FAIL
        && !(testResult == TestResult.ERROR
            && attempt
                    .cause()
                    .orElse(ExceptionDetail.getDefaultInstance())
                    .getSummary()
                    .getErrorId()
                    .getType()
                == ErrorType.CUSTOMER_ISSUE);
  }

  /**
   * Returns whether {@code attempt} counts against {@code Retry.test_attempts} (neither a potential
   * container failure nor a retryable drain timeout).
   */
  static boolean isValidAttempt(String containerModePreference, AttemptSnapshot attempt) {
    return !isPotentialContainerError(containerModePreference, attempt)
        && !isRetryableForDrainTimeout(
            attempt.cause(), getDrainTimeoutRetryAttempts(attempt.properties()));
  }

  /**
   * Evaluates {@code DefaultRetryStrategy} rules for a finished attempt.
   *
   * @param job job-level retry config and metadata
   * @param current snapshot of the finished attempt
   * @param validAttemptNum number of valid attempts completed so far (including {@code current} if
   *     valid)
   * @param totalAttemptNum total attempts completed so far (including {@code current})
   * @param jobRemainingTime supplier for remaining job time
   * @param now current UTC instant
   */
  static StrategyDecision decideDefaultRetry(
      JobRetryContext job,
      AttemptSnapshot current,
      int validAttemptNum,
      int totalAttemptNum,
      RemainingTimeSupplier jobRemainingTime,
      Instant now)
      throws MobileHarnessException {
    Retry retrySetting = job.retrySetting();
    if (validAttemptNum > retrySetting.getTestAttempts()) {
      return new StrategyDecision(ImmutableList.of(), Optional.empty(), Optional.empty());
    }

    TestResult testResult = current.result();
    Optional<ExceptionDetail> cause = current.cause();
    ErrorId criticalErrorId =
        cause.isPresent() ? ErrorModelConverter.getCriticalErrorId(cause.get()) : null;
    String retryReason = null;
    String skipMessage = null;
    String testLogMessage = null;

    if (validAttemptNum < retrySetting.getTestAttempts()) {
      if (isPotentialContainerError(job.containerModePreference(), current)) {
        retryReason = "POTENTIAL_CONTAINER_ISSUE";
      } else if (isRetryableForDrainTimeout(
          cause, getDrainTimeoutRetryAttempts(current.properties()))) {
        retryReason = "DRAIN_TIMEOUT_ERROR";
      } else if (isRetryableForLevel(testResult, retrySetting.getRetryLevel())) {
        boolean isTimeout = isTimeout(testResult, cause);
        if (job.retryOnTimeout() || !isTimeout) {
          testLogMessage =
              String.format(
                  "testResult: %s, retryOnTimeout: %s, isTimeout: %s, cause: %s",
                  testResult,
                  job.retryOnTimeout(),
                  isTimeout,
                  cause.map(ExceptionDetail::toString).orElse("N/A"));
          retryReason = "TEST_" + testResult.name();
        }
      }
    } else if (validAttemptNum == retrySetting.getTestAttempts()
        && validAttemptNum == totalAttemptNum) {
      retryReason =
          infraExtraRetryReason(
                  job.driver(), job.runAsSupplier(), testResult, cause, criticalErrorId)
              .orElse(null);
      if (retryReason != null) {
        Duration remainingTime = jobRemainingTime.get();
        if (remainingTime.compareTo(MIN_JOB_REMAINING_TIME_FOR_INFRA_ERROR_EXTRA_RETRY) < 0) {
          skipMessage =
              String.format(
                  "Skip the extra retry for INFRA_ISSUE because the job remaining time(%s) < %s",
                  remainingTime, MIN_JOB_REMAINING_TIME_FOR_INFRA_ERROR_EXTRA_RETRY);
          retryReason = null;
        } else if (current.startTime().isPresent()) {
          Duration testDuration =
              Duration.between(current.startTime().get(), current.endTime().orElse(now));
          if (testDuration.compareTo(MAX_TEST_DURATION_FOR_INFRA_ERROR_EXTRA_RETRY) >= 0) {
            skipMessage =
                String.format(
                    "Skip the extra retry for INFRA_ISSUE because the current test"
                        + " attempt(test_id=%s) duration(%s) >= %s",
                    current.testId(), testDuration, MAX_TEST_DURATION_FOR_INFRA_ERROR_EXTRA_RETRY);
            retryReason = null;
          }
          if (testDuration.compareTo(remainingTime) > 0) {
            skipMessage =
                String.format(
                    "Skip the extra retry for INFRA_ISSUE because the job remaining time(%s) <"
                        + " current test attempt(test_id=%s) duration(%s)",
                    remainingTime, current.testId(), testDuration);
            retryReason = null;
          }
        }
      }
    }

    if (retryReason == null) {
      return new StrategyDecision(
          ImmutableList.of(),
          Optional.ofNullable(skipMessage),
          Optional.ofNullable(testLogMessage));
    }

    ImmutableMap.Builder<String, String> newTestProperties = ImmutableMap.builder();
    if (validAttemptNum > 0) {
      newTestProperties.put(PROPERTY_RETRY_INDEX, Long.toString(validAttemptNum));
    }
    if (criticalErrorId != null
        && Ascii.equalsIgnoreCase(
            criticalErrorId.getName(),
            AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_NO_VALID_UID_ASSIGNED
                .name())) {
      newTestProperties.put(PROPERTY_RETRY_AFTER_NO_VALID_UID_ASSIGNED, "true");
    }
    long priorDrainRetries = getDrainTimeoutRetryAttempts(current.properties());
    if (isRetryableForDrainTimeout(cause, priorDrainRetries)) {
      newTestProperties.put(
          PROPERTY_DRAIN_TIMEOUT_RETRY_ATTEMPTS, Long.toString(priorDrainRetries + 1L));
    }
    boolean isCurrentContainerMode =
        Ascii.equalsIgnoreCase(
            current.properties().getOrDefault(PROPERTY_CONTAINER_MODE, ""), "true");
    if (isCurrentContainerMode) {
      logger.atInfo().log("Retry container-mode test [%s] after it got error.", current.testId());
      newTestProperties.put(PROPERTY_RETRY_AFTER_CONTAINER_FAILS, "true");
    } else {
      newTestProperties.put(PROPERTY_CONTAINER_MODE, "false");
    }

    return new StrategyDecision(
        ImmutableList.of(new RetryInfo(retryReason, newTestProperties.buildOrThrow())),
        Optional.ofNullable(skipMessage),
        Optional.ofNullable(testLogMessage));
  }

  /**
   * Evaluates {@code FlakyTestRetryStrategy} rules for a finished attempt.
   *
   * @param flakyTestAttempts configured flaky attempt limit
   * @param isParallelRetry whether {@code flaky_test_parallel_retry} is enabled
   * @param current snapshot of the finished attempt
   * @param currentFlakyAttemptIndex 0-based flaky attempt index of {@code current}
   * @param errorAttemptsForCurrentFlakyIndex count of error attempts recorded for {@code
   *     currentFlakyAttemptIndex} (including {@code current})
   * @param retryTestTargetsSupplier lazy supplier for driver-specific retry target properties
   */
  static FlakyDecision decideFlakyRetry(
      int flakyTestAttempts,
      boolean isParallelRetry,
      AttemptSnapshot current,
      int currentFlakyAttemptIndex,
      int errorAttemptsForCurrentFlakyIndex,
      Supplier<Map<String, String>> retryTestTargetsSupplier) {
    if (flakyTestAttempts <= 0) {
      return new FlakyDecision(ImmutableList.of(), Optional.empty());
    }
    TestResult testResult = current.result();
    if (FLAKY_NON_RETRYABLE_RESULTS.contains(testResult)) {
      return new FlakyDecision(ImmutableList.of(), Optional.empty());
    }
    if (testResult == TestResult.FAIL) {
      if (flakyTestAttempts == 1) {
        return new FlakyDecision(
            ImmutableList.of(),
            Optional.of(
                String.format(
                    "No retry for test fail. Flaky test attempts is 1. Current attempt:"
                        + " flaky_attempt_idx=%d, test_id=%s",
                    currentFlakyAttemptIndex, current.testId())));
      }
      if (isParallelRetry) {
        if (currentFlakyAttemptIndex >= 1) {
          return new FlakyDecision(
              ImmutableList.of(),
              Optional.of(
                  String.format(
                      "No more retry for test fail. All parallel flaky test attempts have been"
                          + " triggered. Current attempt: flaky_attempt_idx=%d,"
                          + " max_flaky_attempts=%d, test_id=%s",
                      currentFlakyAttemptIndex, flakyTestAttempts, current.testId())));
        }
        Map<String, String> retryTestTargetsProperties = retryTestTargetsSupplier.get();
        List<RetryInfo> retryInfos = new ArrayList<>();
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
        return new FlakyDecision(
            ImmutableList.copyOf(retryInfos),
            Optional.of(
                String.format(
                    "Trigger %d parallel flaky test attempts for the first failed attempt."
                        + " Current attempt: flaky_attempt_idx=%d, test_id=%s",
                    retryInfos.size(), currentFlakyAttemptIndex, current.testId())));
      }
      if (currentFlakyAttemptIndex >= flakyTestAttempts - 1) {
        return new FlakyDecision(
            ImmutableList.of(),
            Optional.of(
                String.format(
                    "No more retry for test fail. Reached the flaky test attempt limit: limit=%d."
                        + " Current attempt: flaky_attempt_idx=%d, test_id=%s",
                    flakyTestAttempts, currentFlakyAttemptIndex, current.testId())));
      }
      return new FlakyDecision(
          ImmutableList.of(
              new RetryInfo(
                  "TEST_FAIL",
                  ImmutableMap.<String, String>builder()
                      .put(FlakyTestRetryConstants.TEST_PROP_ERROR_ATTEMPT_INDEX, "0")
                      .put(
                          FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX,
                          Integer.toString(currentFlakyAttemptIndex + 1))
                      .put(FlakyTestRetryConstants.TEST_PROP_FLAKY_TEST_PARALLEL_RETRY, "false")
                      .putAll(retryTestTargetsSupplier.get())
                      .buildOrThrow())),
          Optional.of(
              String.format(
                  "Trigger a new sequential retry attempt. Current attempt: flaky_attempt_idx=%d,"
                      + " max_flaky_attempts=%d, test_id=%s",
                  currentFlakyAttemptIndex, flakyTestAttempts, current.testId())));
    }
    if (FLAKY_ERROR_RESULTS.contains(testResult)) {
      if (errorAttemptsForCurrentFlakyIndex < FlakyTestRetryConstants.MAX_ERROR_ATTEMPTS) {
        return new FlakyDecision(
            ImmutableList.of(
                new RetryInfo(
                    "TEST_" + testResult.name(),
                    ImmutableMap.<String, String>builder()
                        .put(
                            FlakyTestRetryConstants.TEST_PROP_ERROR_ATTEMPT_INDEX,
                            Integer.toString(errorAttemptsForCurrentFlakyIndex))
                        .put(
                            FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX,
                            Integer.toString(currentFlakyAttemptIndex))
                        .putAll(retryTestTargetsSupplier.get())
                        .buildOrThrow())),
            Optional.of(
                String.format(
                    "Internal retry on test result [%s]. Current attempts: flaky_attempt_idx=%d,"
                        + " max_flaky_attempts=%d, error_attempt_idx=%d, max_error_attempts=%d,"
                        + " test_id=%s",
                    testResult,
                    currentFlakyAttemptIndex,
                    flakyTestAttempts,
                    errorAttemptsForCurrentFlakyIndex - 1,
                    FlakyTestRetryConstants.MAX_ERROR_ATTEMPTS,
                    current.testId())));
      }
      return new FlakyDecision(
          ImmutableList.of(),
          Optional.of(
              String.format(
                  "No more retry for test result [%s]. Reached the error attempt limit. Current"
                      + " attempts: flaky_attempt_idx=%d, max_flaky_attempts=%d,"
                      + " error_attempt_idx=%d, max_error_attempts=%d, test_id=%s",
                  testResult,
                  currentFlakyAttemptIndex,
                  flakyTestAttempts,
                  errorAttemptsForCurrentFlakyIndex - 1,
                  FlakyTestRetryConstants.MAX_ERROR_ATTEMPTS,
                  current.testId())));
    }
    return new FlakyDecision(
        ImmutableList.of(),
        Optional.of(
            String.format(
                "SHOULD NOT HAPPEN: Test result [%s] is not supported by flaky test retry"
                    + " strategy.",
                testResult)));
  }

  /**
   * Evaluates whether Gateway V2 should retry {@code current}, returning the full non-worker
   * property map for each planned retry row (empty when no retry should be created).
   *
   * <p>Combines V1's {@code TestRetryHandler} guards, strategy selection, property inheritance, and
   * strategy decision. Unlike V1, V2 leaves {@code retry_index} out of the returned map because
   * {@code WorkerTestInfos} and {@code JobPostProcessing} derive {@code retry_index} from {@code
   * attemptCount} so Sponge/AnTS attempt numbers stay distinct after free drain-timeout retries. V2
   * also omits container-mode properties because it does not run in container mode.
   */
  public static ImmutableList<ImmutableMap<String, String>> evaluateWorkerAttempt(
      JobRetryContext job,
      Instant jobExpireTime,
      Instant now,
      AttemptSnapshot current,
      long attemptCount,
      Supplier<Map<String, String>> nextAttemptPropertiesSupplier) {
    if (isRetryPreempted(job, current) || now.isAfter(jobExpireTime)) {
      return ImmutableList.of();
    }
    if (usesFlakyRetry(job.retrySetting().getRetryLevel(), job.flakyTestAttempts())) {
      int flakyAttemptIndex =
          getNonNegativeIntProperty(
              current.properties(), FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX);
      int errorAttemptIndex =
          getNonNegativeIntProperty(
              current.properties(), FlakyTestRetryConstants.TEST_PROP_ERROR_ATTEMPT_INDEX);
      ImmutableList<RetryInfo> retries =
          decideFlakyRetry(
                  job.flakyTestAttempts(),
                  job.flakyTestParallelRetry(),
                  current,
                  flakyAttemptIndex,
                  errorAttemptIndex + 1,
                  nextAttemptPropertiesSupplier)
              .retries();
      ImmutableList.Builder<ImmutableMap<String, String>> result = ImmutableList.builder();
      for (RetryInfo retry : retries) {
        Map<String, String> props = baseRetryProperties(current, retry.retryReason());
        props.putAll(retry.newTestProperties());
        props.putIfAbsent(
            FlakyTestRetryConstants.TEST_PROP_FLAKY_TEST_PARALLEL_RETRY,
            Boolean.toString(job.flakyTestParallelRetry()));
        result.add(ImmutableMap.copyOf(props));
      }
      return result.build();
    }

    long priorDrainRetries = Math.max(0L, getDrainTimeoutRetryAttempts(current.properties()));
    boolean currentValid = !isRetryableForDrainTimeout(current.cause(), priorDrainRetries);
    int totalAttemptNum = Math.toIntExact(attemptCount + 1);
    int validAttemptNum =
        Math.toIntExact(Math.max(0L, attemptCount - priorDrainRetries)) + (currentValid ? 1 : 0);

    StrategyDecision decision;
    try {
      decision =
          decideDefaultRetry(
              job.withoutContainerFallback(),
              current,
              validAttemptNum,
              totalAttemptNum,
              () -> Duration.between(now, jobExpireTime),
              now);
    } catch (MobileHarnessException e) {
      throw new IllegalStateException("Unexpected failure computing remaining job time", e);
    }
    decision.skipMessage().ifPresent(msg -> logger.atInfo().log("%s", msg));

    ImmutableList.Builder<ImmutableMap<String, String>> result = ImmutableList.builder();
    for (RetryInfo retry : decision.retries()) {
      Map<String, String> props = baseRetryProperties(current, retry.retryReason());
      retry
          .newTestProperties()
          .forEach((key, value) -> {
            if (!key.equals(PROPERTY_RETRY_INDEX)
                && !key.equals(PROPERTY_CONTAINER_MODE)
                && !key.equals(PROPERTY_RETRY_AFTER_CONTAINER_FAILS)) {
              props.put(key, value);
            }
          });
      result.add(ImmutableMap.copyOf(props));
    }
    return result.build();
  }

  /**
   * Returns whether {@link #evaluateWorkerAttempt} can ever schedule a retry for {@code current},
   * ignoring only time-based limits (job expiry and extra-retry remaining/duration thresholds).
   * Used by {@code TestExecution} before MOSS upsert so attempts that will never retry carry {@code
   * is_final_attempt=true}.
   */
  public static boolean canWorkerEverRetry(
      JobRetryContext job, AttemptSnapshot current, long attemptCount) {
    if (isRetryPreempted(job, current)) {
      return false;
    }
    Retry retrySetting = job.retrySetting();
    if (usesFlakyRetry(retrySetting.getRetryLevel(), job.flakyTestAttempts())) {
      int flakyAttemptIndex =
          getNonNegativeIntProperty(
              current.properties(), FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX);
      int errorAttemptIndex =
          getNonNegativeIntProperty(
              current.properties(), FlakyTestRetryConstants.TEST_PROP_ERROR_ATTEMPT_INDEX);
      return !decideFlakyRetry(
              job.flakyTestAttempts(),
              job.flakyTestParallelRetry(),
              current,
              flakyAttemptIndex,
              errorAttemptIndex + 1,
              ImmutableMap::of)
          .retries()
          .isEmpty();
    }
    long priorDrainRetries = Math.max(0L, getDrainTimeoutRetryAttempts(current.properties()));
    boolean currentValid = !isRetryableForDrainTimeout(current.cause(), priorDrainRetries);
    long totalAttemptNum = attemptCount + 1;
    long validAttemptNum =
        Math.max(0L, attemptCount - priorDrainRetries) + (currentValid ? 1L : 0L);

    if (validAttemptNum < retrySetting.getTestAttempts()) {
      return isRetryableForDrainTimeout(current.cause(), priorDrainRetries)
          || (isRetryableForLevel(current.result(), retrySetting.getRetryLevel())
              && (job.retryOnTimeout() || !isTimeout(current.result(), current.cause())));
    }
    if (validAttemptNum == retrySetting.getTestAttempts() && validAttemptNum == totalAttemptNum) {
      ErrorId criticalErrorId =
          current.cause().isPresent()
              ? ErrorModelConverter.getCriticalErrorId(current.cause().get())
              : null;
      return infraExtraRetryReason(
              job.driver(), job.runAsSupplier(), current.result(), current.cause(), criticalErrorId)
          .isPresent();
    }
    return false;
  }

  private static boolean isRetryPreempted(JobRetryContext job, AttemptSnapshot current) {
    TestResult result = current.result();
    return job.retrySetting().getRetryLevel() == Retry.Level.ALL
        || result == TestResult.PASS
        || result == TestResult.SKIP
        || result == TestResult.UNKNOWN
        || result == TestResult.UNRECOGNIZED
        || ClientAllocErrorUtil.isTestAllocError(result, current.cause())
        || ClientAllocErrorUtil.isTestAllocFail(result, current.cause())
        || Ascii.equalsIgnoreCase(
            current.properties().getOrDefault(PROPERTY_HALT_RETRY, ""), "true");
  }

  private static boolean isRetryableForLevel(TestResult result, Retry.Level level) {
    if (result == TestResult.PASS || result == TestResult.SKIP) {
      return false;
    }
    return switch (level) {
      case ERROR -> result != TestResult.FAIL;
      case FAIL -> true;
      case ALL, UNRECOGNIZED -> false;
    };
  }

  private static Optional<String> infraExtraRetryReason(
      String driver,
      Supplier<String> runAsSupplier,
      TestResult testResult,
      Optional<ExceptionDetail> cause,
      ErrorId criticalErrorId) {
    if (DRIVER_BLOCK_LIST_FOR_INFRA_ERROR_EXTRA_RETRY.contains(driver)
        || testResult == TestResult.PASS
        || testResult == TestResult.SKIP
        || cause.isEmpty()) {
      return Optional.empty();
    }
    String reason = null;
    if (criticalErrorId != null
        && criticalErrorId.getType() == ErrorType.INFRA_ISSUE
        && criticalErrorId.getNamespace().equals(Namespace.MH)) {
      reason = "EXTRA_RETRY_FOR_INFRA_ISSUE_AS_CRITICAL_ERROR";
    } else if (ErrorModelConverter.hasMhInfraIssue(cause.get())) {
      reason = "EXTRA_RETRY_FOR_INFRA_ISSUE_AS_CAUSE_OR_SUPPRESSED_ERROR";
    }
    if (reason == null
        || USER_BLOCK_LIST_FOR_INFRA_ERROR_EXTRA_RETRY.contains(runAsSupplier.get())) {
      return Optional.empty();
    }
    return Optional.of(reason);
  }

  private static Map<String, String> baseRetryProperties(
      AttemptSnapshot current, String retryReason) {
    Map<String, String> props = new LinkedHashMap<>();
    current
        .properties()
        .forEach((key, value) -> {
          if (INHERITED_TEST_PROPERTIES.contains(key)) {
            props.put(key, value);
          }
        });
    props.put(PROPERTY_FOREGOING_TEST_ID, current.testId());
    props.put(PROPERTY_FOREGOING_TEST_RESULT, current.result().name());
    props.put(PROPERTY_RETRY_REASON, retryReason);
    return props;
  }

  private static long getDrainTimeoutRetryAttempts(Map<String, String> properties) {
    String raw = properties.get(PROPERTY_DRAIN_TIMEOUT_RETRY_ATTEMPTS);
    if (raw == null) {
      return 0L;
    }
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  /**
   * Returns the non-negative integer stored under {@code key}, or {@code 0} when absent or invalid.
   */
  public static int getNonNegativeIntProperty(Map<String, String> properties, String key) {
    String value = properties.get(key);
    if (value == null) {
      return 0;
    }
    try {
      return Math.max(0, Integer.parseInt(value));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private RetryEvaluator() {}
}
