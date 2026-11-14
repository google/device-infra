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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy.RetryEvaluator.AttemptSnapshot;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy.RetryEvaluator.FlakyDecision;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy.RetryEvaluator.JobRetryContext;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy.RetryEvaluator.StrategyDecision;
import com.google.devtools.mobileharness.infra.container.proto.ModeSettingProto.ContainerModePreference;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class RetryEvaluatorTest {

  private static final Instant BASE_TIME = Instant.parse("2026-11-13T12:00:00Z");
  private static final Instant JOB_EXPIRE_TIME = BASE_TIME.plus(Duration.ofHours(1));
  private static final String TEST_ID = "test-0";

  private static JobRetryContext defaultJob(int testAttempts) {
    return new JobRetryContext(
        Retry.newBuilder().setRetryLevel(Retry.Level.ERROR).setTestAttempts(testAttempts).build(),
        "MoblyTest",
        "test-user",
        /* flakyTestAttempts= */ -1,
        /* flakyTestParallelRetry= */ false,
        /* retryOnTimeout= */ true,
        ContainerModePreference.CONTAINER_MODE_PREFERENCE_UNSPECIFIED.name());
  }

  private static AttemptSnapshot snapshot(
      TestResult result, Optional<ExceptionDetail> cause, ImmutableMap<String, String> props) {
    return new AttemptSnapshot(TEST_ID, result, cause, props, Optional.of(BASE_TIME));
  }

  private static ExceptionDetail mhInfraCause() {
    return ErrorModelConverter.toExceptionDetail(
        new MobileHarnessException(
            InfraErrorId.TE_RESOLVE_FILE_FAILURE_WHEN_CLIENT_WAITING_FILE_RESOLVED,
            "resolve failed"));
  }

  private static ExceptionDetail nestedMhInfraCause() {
    MobileHarnessException inner =
        new MobileHarnessException(InfraErrorId.LAB_JM_JOB_NOT_FOUND, "missing");
    MobileHarnessException outer =
        new MobileHarnessException(
            BasicErrorId.CUSTOMER_TEST_EXECUTION_TIMEOUT_EXCEPTION_WRAPPER, "wrapped", inner);
    return ErrorModelConverter.toExceptionDetail(outer);
  }

  private static ExceptionDetail customerCause() {
    return ErrorModelConverter.toExceptionDetail(
        new MobileHarnessException(
            BasicErrorId.CUSTOMER_TEST_EXECUTION_TIMEOUT_EXCEPTION_WRAPPER, "user timeout"));
  }

  private static ExceptionDetail drainTimeoutCause() {
    return ErrorModelConverter.toExceptionDetail(
        new MobileHarnessException(
            InfraErrorId.TR_TEST_DRAIN_TIMEOUT_AND_FORCE_CLEAN_UP, "drain timeout"));
  }

  @Test
  public void evaluateWorkerAttempt_extraInfraRetry_triggersWhenAttemptsExhausted() {
    JobRetryContext job = defaultJob(/* testAttempts= */ 1);

    ImmutableList<ImmutableMap<String, String>> retries =
        RetryEvaluator.evaluateWorkerAttempt(
            job,
            JOB_EXPIRE_TIME,
            BASE_TIME.plusSeconds(60),
            snapshot(TestResult.ERROR, Optional.of(mhInfraCause()), ImmutableMap.of()),
            /* attemptCount= */ 0L,
            ImmutableMap::of);

    assertThat(retries)
        .containsExactly(
            ImmutableMap.of(
                "foregoing_test_id",
                TEST_ID,
                "foregoing_test_result",
                "ERROR",
                "retry_reason",
                "EXTRA_RETRY_FOR_INFRA_ISSUE_AS_CRITICAL_ERROR"));
  }

  @Test
  public void evaluateWorkerAttempt_extraInfraRetry_nestedCauseUsesCauseReason() {
    JobRetryContext job = defaultJob(/* testAttempts= */ 2);

    ImmutableList<ImmutableMap<String, String>> retries =
        RetryEvaluator.evaluateWorkerAttempt(
            job,
            JOB_EXPIRE_TIME,
            BASE_TIME.plusSeconds(60),
            snapshot(TestResult.ERROR, Optional.of(nestedMhInfraCause()), ImmutableMap.of()),
            /* attemptCount= */ 1L,
            ImmutableMap::of);

    assertThat(retries)
        .containsExactly(
            ImmutableMap.of(
                "foregoing_test_id",
                TEST_ID,
                "foregoing_test_result",
                "ERROR",
                "retry_reason",
                "EXTRA_RETRY_FOR_INFRA_ISSUE_AS_CAUSE_OR_SUPPRESSED_ERROR"));
  }

  @Test
  public void evaluateWorkerAttempt_extraInfraRetry_doesNotRepeatOnExtraAttempt() {
    JobRetryContext job = defaultJob(/* testAttempts= */ 1);

    ImmutableList<ImmutableMap<String, String>> retries =
        RetryEvaluator.evaluateWorkerAttempt(
            job,
            JOB_EXPIRE_TIME,
            BASE_TIME.plusSeconds(60),
            snapshot(TestResult.ERROR, Optional.of(mhInfraCause()), ImmutableMap.of()),
            /* attemptCount= */ 1L,
            ImmutableMap::of);

    assertThat(retries).isEmpty();
  }

  @Test
  public void evaluateWorkerAttempt_extraInfraRetry_respectsDriverBlockList(
      @TestParameter({
            "AndroidTradefedTest",
            "AndroidCopycatRemoteControlledMoblySnippetTest",
            "IosCopycatRemoteControlledMoblySnippetTest",
            "IosNativeXcTest",
            "ManekiTest",
            "MoblyAospTest",
            "TradefedTest",
            "VegaTest",
            "YtsTest"
          })
          String driver) {
    AtomicInteger runAsCalls = new AtomicInteger();
    JobRetryContext job =
        new JobRetryContext(
            Retry.newBuilder().setRetryLevel(Retry.Level.ERROR).setTestAttempts(1).build(),
            driver,
            () -> {
              runAsCalls.incrementAndGet();
              return "test-user";
            },
            /* flakyTestAttempts= */ -1,
            /* flakyTestParallelRetry= */ false,
            /* retryOnTimeout= */ true,
            ContainerModePreference.CONTAINER_MODE_PREFERENCE_UNSPECIFIED.name());

    assertThat(
            RetryEvaluator.evaluateWorkerAttempt(
                job,
                JOB_EXPIRE_TIME,
                BASE_TIME.plusSeconds(60),
                snapshot(TestResult.ERROR, Optional.of(mhInfraCause()), ImmutableMap.of()),
                /* attemptCount= */ 0L,
                ImmutableMap::of))
        .isEmpty();
    assertThat(runAsCalls.get()).isEqualTo(0);
  }

  @Test
  public void evaluateWorkerAttempt_extraInfraRetry_respectsUserBlockList(
      @TestParameter({"ytlr-lab-users", "pte-sensor-lab-jobs", "fusion-guitar-jobs"}) String user) {
    JobRetryContext job =
        new JobRetryContext(
            Retry.newBuilder().setRetryLevel(Retry.Level.ERROR).setTestAttempts(1).build(),
            "MoblyTest",
            user,
            /* flakyTestAttempts= */ -1,
            /* flakyTestParallelRetry= */ false,
            /* retryOnTimeout= */ true,
            ContainerModePreference.CONTAINER_MODE_PREFERENCE_UNSPECIFIED.name());

    assertThat(
            RetryEvaluator.evaluateWorkerAttempt(
                job,
                JOB_EXPIRE_TIME,
                BASE_TIME.plusSeconds(60),
                snapshot(TestResult.ERROR, Optional.of(mhInfraCause()), ImmutableMap.of()),
                /* attemptCount= */ 0L,
                ImmutableMap::of))
        .isEmpty();
  }

  @Test
  public void evaluateWorkerAttempt_extraInfraRetry_skipsWhenRemainingTimeTooShort() {
    JobRetryContext job = defaultJob(/* testAttempts= */ 1);
    AttemptSnapshot attempt =
        snapshot(TestResult.ERROR, Optional.of(mhInfraCause()), ImmutableMap.of());

    assertThat(
            RetryEvaluator.evaluateWorkerAttempt(
                job,
                JOB_EXPIRE_TIME,
                JOB_EXPIRE_TIME.minus(Duration.ofMinutes(4)),
                attempt,
                /* attemptCount= */ 0L,
                ImmutableMap::of))
        .isEmpty();
  }

  @Test
  public void evaluateWorkerAttempt_extraInfraRetry_skipsWhenTestDurationTooLong() {
    JobRetryContext job = defaultJob(/* testAttempts= */ 1);
    AttemptSnapshot attempt =
        snapshot(TestResult.ERROR, Optional.of(mhInfraCause()), ImmutableMap.of());
    Instant longExpire = BASE_TIME.plus(Duration.ofHours(10));

    assertThat(
            RetryEvaluator.evaluateWorkerAttempt(
                job,
                longExpire,
                BASE_TIME.plus(Duration.ofHours(2)),
                attempt,
                /* attemptCount= */ 0L,
                ImmutableMap::of))
        .isEmpty();
  }

  @Test
  public void evaluateWorkerAttempt_extraInfraRetry_skipsWhenTestDurationExceedsRemainingTime() {
    JobRetryContext job = defaultJob(/* testAttempts= */ 1);
    AttemptSnapshot attempt =
        snapshot(TestResult.ERROR, Optional.of(mhInfraCause()), ImmutableMap.of());

    assertThat(
            RetryEvaluator.evaluateWorkerAttempt(
                job,
                JOB_EXPIRE_TIME,
                BASE_TIME.plus(Duration.ofMinutes(35)),
                attempt,
                /* attemptCount= */ 0L,
                ImmutableMap::of))
        .isEmpty();
  }

  @Test
  public void evaluateWorkerAttempt_extraInfraRetry_ignoresRetryOnTimeoutFalse() {
    JobRetryContext job =
        new JobRetryContext(
            Retry.newBuilder().setRetryLevel(Retry.Level.ERROR).setTestAttempts(1).build(),
            "MoblyTest",
            "test-user",
            /* flakyTestAttempts= */ -1,
            /* flakyTestParallelRetry= */ false,
            /* retryOnTimeout= */ false,
            ContainerModePreference.CONTAINER_MODE_PREFERENCE_UNSPECIFIED.name());

    ImmutableList<ImmutableMap<String, String>> retries =
        RetryEvaluator.evaluateWorkerAttempt(
            job,
            JOB_EXPIRE_TIME,
            BASE_TIME.plusSeconds(60),
            snapshot(TestResult.TIMEOUT, Optional.of(mhInfraCause()), ImmutableMap.of()),
            /* attemptCount= */ 0L,
            ImmutableMap::of);

    assertThat(retries).hasSize(1);
    assertThat(retries.get(0))
        .containsEntry("retry_reason", "EXTRA_RETRY_FOR_INFRA_ISSUE_AS_CRITICAL_ERROR");
  }

  @Test
  public void evaluateWorkerAttempt_drainTimeout_retriesFreeAndIncrementsProperty() {
    JobRetryContext job = defaultJob(/* testAttempts= */ 1);
    AttemptSnapshot firstDrain =
        snapshot(TestResult.ERROR, Optional.of(drainTimeoutCause()), ImmutableMap.of());

    ImmutableList<ImmutableMap<String, String>> retry1 =
        RetryEvaluator.evaluateWorkerAttempt(
            job, JOB_EXPIRE_TIME, BASE_TIME.plusSeconds(60), firstDrain, 0L, ImmutableMap::of);

    assertThat(retry1)
        .containsExactly(
            ImmutableMap.of(
                "foregoing_test_id",
                TEST_ID,
                "foregoing_test_result",
                "ERROR",
                "retry_reason",
                "DRAIN_TIMEOUT_ERROR",
                "_drain_timeout_retry_attempts",
                "1"));
  }

  @Test
  public void evaluateWorkerAttempt_drainTimeout_priorDrainBlocksExtraInfraRetry() {
    JobRetryContext job = defaultJob(/* testAttempts= */ 1);
    AttemptSnapshot postDrainInfra =
        snapshot(
            TestResult.ERROR,
            Optional.of(mhInfraCause()),
            ImmutableMap.of("_drain_timeout_retry_attempts", "1"));

    assertThat(
            RetryEvaluator.evaluateWorkerAttempt(
                job,
                JOB_EXPIRE_TIME,
                BASE_TIME.plusSeconds(120),
                postDrainInfra,
                1L,
                ImmutableMap::of))
        .isEmpty();
  }

  @Test
  public void evaluateWorkerAttempt_drainTimeout_capsAtFive() {
    JobRetryContext job = defaultJob(/* testAttempts= */ 1);
    AttemptSnapshot exhaustedDrain =
        snapshot(
            TestResult.ERROR,
            Optional.of(drainTimeoutCause()),
            ImmutableMap.of("_drain_timeout_retry_attempts", "5"));

    assertThat(
            RetryEvaluator.evaluateWorkerAttempt(
                job,
                JOB_EXPIRE_TIME,
                BASE_TIME.plusSeconds(120),
                exhaustedDrain,
                5L,
                ImmutableMap::of))
        .isEmpty();
  }

  @Test
  public void evaluateWorkerAttempt_noValidUidAssigned_setsPropertyOnRetry() {
    JobRetryContext job = defaultJob(/* testAttempts= */ 2);
    ExceptionDetail noUidCause =
        ErrorModelConverter.toExceptionDetail(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_NO_VALID_UID_ASSIGNED,
                "no uid"));

    ImmutableList<ImmutableMap<String, String>> retries =
        RetryEvaluator.evaluateWorkerAttempt(
            job,
            JOB_EXPIRE_TIME,
            BASE_TIME.plusSeconds(60),
            snapshot(TestResult.ERROR, Optional.of(noUidCause), ImmutableMap.of()),
            /* attemptCount= */ 0L,
            ImmutableMap::of);

    assertThat(retries)
        .containsExactly(
            ImmutableMap.of(
                "foregoing_test_id",
                TEST_ID,
                "foregoing_test_result",
                "ERROR",
                "retry_reason",
                "TEST_ERROR",
                "retry_after_no_valid_uid_assigned",
                "true"));
  }

  @Test
  @TestParameters({
    "{result: 'PASS', expected: false}",
    "{result: 'FAIL', expected: false}",
    "{result: 'SKIP', expected: false}",
    "{result: 'UNKNOWN', expected: false}",
    "{result: 'ERROR', expected: true}",
    "{result: 'TIMEOUT', expected: true}",
    "{result: 'ABORT', expected: true}",
  })
  public void canWorkerEverRetry_errorLevel_matchesExpectedByResult(
      TestResult result, boolean expected) {
    JobRetryContext attempts2Error = defaultJob(/* testAttempts= */ 2);

    assertThat(
            RetryEvaluator.canWorkerEverRetry(
                attempts2Error, snapshot(result, Optional.empty(), ImmutableMap.of()), 0L))
        .isEqualTo(expected);
  }

  @Test
  public void canWorkerEverRetry_whenNormalAttemptsExhausted_allowsMhInfraAndDrainTimeoutOnly() {
    JobRetryContext attempts1 = defaultJob(/* testAttempts= */ 1);

    assertThat(
            RetryEvaluator.canWorkerEverRetry(
                attempts1,
                snapshot(TestResult.ERROR, Optional.of(mhInfraCause()), ImmutableMap.of()),
                0L))
        .isTrue();
    assertThat(
            RetryEvaluator.canWorkerEverRetry(
                attempts1,
                snapshot(TestResult.ERROR, Optional.of(customerCause()), ImmutableMap.of()),
                0L))
        .isFalse();
    assertThat(
            RetryEvaluator.canWorkerEverRetry(
                attempts1,
                snapshot(TestResult.ERROR, Optional.of(drainTimeoutCause()), ImmutableMap.of()),
                0L))
        .isTrue();
  }

  @Test
  public void canWorkerEverRetry_flakyRetry_returnsFalseWhenFlakyOrErrorBudgetExhausted() {
    JobRetryContext flakyJob =
        new JobRetryContext(
            Retry.newBuilder().setRetryLevel(Retry.Level.ERROR).setTestAttempts(1).build(),
            "MoblyTest",
            "test-user",
            /* flakyTestAttempts= */ 2,
            /* flakyTestParallelRetry= */ false,
            /* retryOnTimeout= */ true,
            ContainerModePreference.CONTAINER_MODE_PREFERENCE_UNSPECIFIED.name());

    assertThat(
            RetryEvaluator.canWorkerEverRetry(
                flakyJob,
                snapshot(
                    TestResult.FAIL, Optional.empty(), ImmutableMap.of("flaky_attempt_index", "0")),
                0L))
        .isTrue();
    assertThat(
            RetryEvaluator.canWorkerEverRetry(
                flakyJob,
                snapshot(
                    TestResult.FAIL, Optional.empty(), ImmutableMap.of("flaky_attempt_index", "1")),
                1L))
        .isFalse();
    assertThat(
            RetryEvaluator.canWorkerEverRetry(
                flakyJob,
                snapshot(
                    TestResult.ERROR,
                    Optional.empty(),
                    ImmutableMap.of("flaky_attempt_index", "0", "error_attempt_index", "2")),
                2L))
        .isFalse();
  }

  @Test
  public void decideDefaultRetry_unknownResult_retriedForV1AndPopulatesTestLogMessage()
      throws Exception {
    JobRetryContext job = defaultJob(/* testAttempts= */ 2);
    AttemptSnapshot unknownAttempt =
        snapshot(TestResult.UNKNOWN, Optional.empty(), ImmutableMap.of());

    StrategyDecision v1Decision =
        RetryEvaluator.decideDefaultRetry(
            job,
            unknownAttempt,
            /* validAttemptNum= */ 1,
            /* totalAttemptNum= */ 1,
            () -> Duration.ofMinutes(30),
            BASE_TIME);

    assertThat(v1Decision.retries()).hasSize(1);
    assertThat(v1Decision.retries().get(0).retryReason()).isEqualTo("TEST_UNKNOWN");
    assertThat(v1Decision.testLogMessage()).isPresent();
    assertThat(v1Decision.testLogMessage().get()).contains("testResult: UNKNOWN");

    // Gateway V2 still suppresses UNKNOWN in isRetryPreempted for both evaluation and finality.
    assertThat(
            RetryEvaluator.evaluateWorkerAttempt(
                job,
                JOB_EXPIRE_TIME,
                BASE_TIME.plusSeconds(60),
                unknownAttempt,
                0L,
                ImmutableMap::of))
        .isEmpty();
    assertThat(RetryEvaluator.canWorkerEverRetry(job, unknownAttempt, 0L)).isFalse();
  }

  @Test
  public void decideDefaultRetry_unrecognizedResult_retriedForV1() throws Exception {
    AttemptSnapshot unrecognizedAttempt =
        snapshot(TestResult.UNRECOGNIZED, Optional.empty(), ImmutableMap.of());

    StrategyDecision v1Decision =
        RetryEvaluator.decideDefaultRetry(
            defaultJob(/* testAttempts= */ 2),
            unrecognizedAttempt,
            /* validAttemptNum= */ 1,
            /* totalAttemptNum= */ 1,
            () -> Duration.ofMinutes(30),
            BASE_TIME);

    assertThat(v1Decision.retries()).hasSize(1);
    assertThat(v1Decision.retries().get(0).retryReason()).isEqualTo("TEST_UNRECOGNIZED");
  }

  @Test
  public void decideFlakyRetry_emitsExactJobLogMessageOnRetry() {
    AttemptSnapshot failAttempt = snapshot(TestResult.FAIL, Optional.empty(), ImmutableMap.of());

    FlakyDecision retryDecision =
        RetryEvaluator.decideFlakyRetry(
            /* flakyTestAttempts= */ 2,
            /* isParallelRetry= */ false,
            failAttempt,
            /* currentFlakyAttemptIndex= */ 0,
            /* errorAttemptsForCurrentFlakyIndex= */ 0,
            ImmutableMap::of);

    assertThat(retryDecision.retries()).hasSize(1);
    assertThat(retryDecision.jobLogMessage()).isPresent();
    assertThat(retryDecision.jobLogMessage().get())
        .contains("Trigger a new sequential retry attempt");
  }

  @Test
  public void decideFlakyRetry_emitsExactJobLogMessageOnLimitReached() {
    AttemptSnapshot failAttempt = snapshot(TestResult.FAIL, Optional.empty(), ImmutableMap.of());

    FlakyDecision stopDecision =
        RetryEvaluator.decideFlakyRetry(
            /* flakyTestAttempts= */ 2,
            /* isParallelRetry= */ false,
            failAttempt,
            /* currentFlakyAttemptIndex= */ 1,
            /* errorAttemptsForCurrentFlakyIndex= */ 0,
            ImmutableMap::of);

    assertThat(stopDecision.retries()).isEmpty();
    assertThat(stopDecision.jobLogMessage()).isPresent();
    assertThat(stopDecision.jobLogMessage().get())
        .contains("No more retry for test fail. Reached the flaky test attempt limit");
  }

  @Test
  public void evaluateWorkerAttempt_containerModeProperty_doesNotTriggerContainerFallbackInV2() {
    JobRetryContext attempts1Job = defaultJob(/* testAttempts= */ 1);
    AttemptSnapshot errorContainer =
        snapshot(TestResult.ERROR, Optional.empty(), ImmutableMap.of("container_mode", "true"));

    // Counts as a valid attempt (validAttemptNum=1 == test_attempts=1) rather than a free container
    // failure.
    assertThat(
            RetryEvaluator.evaluateWorkerAttempt(
                attempts1Job,
                JOB_EXPIRE_TIME,
                BASE_TIME.plusSeconds(60),
                errorContainer,
                /* attemptCount= */ 0L,
                ImmutableMap::of))
        .isEmpty();
    assertThat(RetryEvaluator.canWorkerEverRetry(attempts1Job, errorContainer, 0L)).isFalse();

    JobRetryContext noTimeoutRetryJob =
        new JobRetryContext(
            Retry.newBuilder().setRetryLevel(Retry.Level.ERROR).setTestAttempts(2).build(),
            "MoblyTest",
            "test-user",
            /* flakyTestAttempts= */ -1,
            /* flakyTestParallelRetry= */ false,
            /* retryOnTimeout= */ false,
            ContainerModePreference.CONTAINER_MODE_PREFERENCE_UNSPECIFIED.name());
    AttemptSnapshot timeoutContainer =
        snapshot(TestResult.TIMEOUT, Optional.empty(), ImmutableMap.of("container_mode", "true"));

    // With validAttemptNum=1 < test_attempts=2, withoutContainerFallback() prevents
    // isPotentialContainerError from bypassing retryOnTimeout=false.
    assertThat(
            RetryEvaluator.evaluateWorkerAttempt(
                noTimeoutRetryJob,
                JOB_EXPIRE_TIME,
                BASE_TIME.plusSeconds(60),
                timeoutContainer,
                /* attemptCount= */ 0L,
                ImmutableMap::of))
        .isEmpty();
    assertThat(RetryEvaluator.canWorkerEverRetry(noTimeoutRetryJob, timeoutContainer, 0L))
        .isFalse();

    AttemptSnapshot skipContainer =
        snapshot(TestResult.SKIP, Optional.empty(), ImmutableMap.of("container_mode", "true"));
    assertThat(
            RetryEvaluator.evaluateWorkerAttempt(
                noTimeoutRetryJob,
                JOB_EXPIRE_TIME,
                BASE_TIME.plusSeconds(60),
                skipContainer,
                /* attemptCount= */ 0L,
                ImmutableMap::of))
        .isEmpty();
    assertThat(RetryEvaluator.canWorkerEverRetry(noTimeoutRetryJob, skipContainer, 0L)).isFalse();
  }

  @Test
  public void evaluateWorkerAttempt_errorLevel_retriesErrorTimeoutAndAbortOnly() {
    Retry retry = Retry.newBuilder().setRetryLevel(Retry.Level.ERROR).setTestAttempts(2).build();

    assertThat(retriedResults(retry, /* flakyTestAttempts= */ -1))
        .containsExactly(TestResult.ERROR, TestResult.TIMEOUT, TestResult.ABORT);
  }

  @Test
  public void evaluateWorkerAttempt_failLevel_retriesFailErrorTimeoutAndAbortOnly() {
    Retry retry = Retry.newBuilder().setRetryLevel(Retry.Level.FAIL).setTestAttempts(2).build();

    assertThat(retriedResults(retry, /* flakyTestAttempts= */ -1))
        .containsExactly(TestResult.FAIL, TestResult.ERROR, TestResult.TIMEOUT, TestResult.ABORT);
  }

  @Test
  public void evaluateWorkerAttempt_singleAttemptWithoutFlakyRetry_retriesNoResult() {
    Retry retry = Retry.newBuilder().setRetryLevel(Retry.Level.FAIL).setTestAttempts(1).build();

    assertThat(retriedResults(retry, /* flakyTestAttempts= */ -1)).isEmpty();
  }

  @Test
  public void evaluateWorkerAttempt_flakyRetry_retriesFailOnlyWithSeveralFlakyAttempts() {
    Retry retry = Retry.newBuilder().setRetryLevel(Retry.Level.ERROR).setTestAttempts(1).build();

    assertThat(retriedResults(retry, /* flakyTestAttempts= */ 2))
        .containsExactly(TestResult.FAIL, TestResult.ERROR, TestResult.TIMEOUT);
    assertThat(retriedResults(retry, /* flakyTestAttempts= */ 1))
        .containsExactly(TestResult.ERROR, TestResult.TIMEOUT);
  }

  @Test
  public void evaluateWorkerAttempt_allLevel_retriesNoResultEvenWithFlakyAttempts() {
    Retry retry = Retry.newBuilder().setRetryLevel(Retry.Level.ALL).setTestAttempts(3).build();

    assertThat(RetryEvaluator.usesFlakyRetry(Retry.Level.ALL, /* flakyTestAttempts= */ 3))
        .isFalse();
    assertThat(retriedResults(retry, /* flakyTestAttempts= */ -1)).isEmpty();
    assertThat(retriedResults(retry, /* flakyTestAttempts= */ 3)).isEmpty();
  }

  @Test
  public void evaluateWorkerAttempt_unrecognizedLevel_retriesOnlyUnderFlakyRetry() {
    Retry retry = Retry.newBuilder().setRetryLevelValue(999).setTestAttempts(3).build();

    assertThat(retriedResults(retry, /* flakyTestAttempts= */ -1)).isEmpty();
    assertThat(retriedResults(retry, /* flakyTestAttempts= */ 3))
        .containsExactly(TestResult.FAIL, TestResult.ERROR, TestResult.TIMEOUT);
  }

  @Test
  public void evaluateWorkerAttempt_allocErrorOrFail_notRetried(
      @TestParameter({"CLIENT_JR_ALLOC_INFRA_ERROR", "CLIENT_JR_ALLOC_USER_CONFIG_ERROR"})
          InfraErrorId errorId) {
    JobRetryContext job = defaultJob(/* testAttempts= */ 2);
    AttemptSnapshot allocAttempt =
        snapshot(
            TestResult.ERROR,
            Optional.of(
                ErrorModelConverter.toExceptionDetail(
                    new MobileHarnessException(errorId, "allocation failed"))),
            ImmutableMap.of());

    assertThat(
            RetryEvaluator.evaluateWorkerAttempt(
                job,
                JOB_EXPIRE_TIME,
                BASE_TIME.plusSeconds(60),
                allocAttempt,
                /* attemptCount= */ 0L,
                ImmutableMap::of))
        .isEmpty();
    assertThat(RetryEvaluator.canWorkerEverRetry(job, allocAttempt, 0L)).isFalse();
  }

  @Test
  public void canWorkerEverRetry_agreesWithEvaluateWorkerAttemptWhenNoTimeLimitApplies(
      @TestParameter({"ERROR", "FAIL", "ALL"}) Retry.Level level,
      @TestParameter({"-1", "2"}) int flakyTestAttempts,
      @TestParameter boolean retryOnTimeout,
      @TestParameter TestResult result,
      @TestParameter Cause cause) {
    JobRetryContext job =
        new JobRetryContext(
            Retry.newBuilder().setRetryLevel(level).setTestAttempts(2).build(),
            "MoblyTest",
            "test-user",
            flakyTestAttempts,
            /* flakyTestParallelRetry= */ false,
            retryOnTimeout,
            ContainerModePreference.CONTAINER_MODE_PREFERENCE_UNSPECIFIED.name());
    AttemptSnapshot attempt = snapshot(result, cause.detail, ImmutableMap.of());

    // With test_attempts=2: an attempt within the budget, the one using it up, and one past it.
    for (long attemptCount = 0; attemptCount <= 2; attemptCount++) {
      assertWithMessage("attemptCount=%s", attemptCount)
          .that(RetryEvaluator.canWorkerEverRetry(job, attempt, attemptCount))
          .isEqualTo(
              !RetryEvaluator.evaluateWorkerAttempt(
                      job,
                      JOB_EXPIRE_TIME,
                      BASE_TIME.plusSeconds(60),
                      attempt,
                      attemptCount,
                      ImmutableMap::of)
                  .isEmpty());
    }
  }

  enum Cause {
    NONE(Optional.empty()),
    MH_INFRA(Optional.of(mhInfraCause())),
    CUSTOMER(Optional.of(customerCause())),
    DRAIN_TIMEOUT(Optional.of(drainTimeoutCause()));

    final Optional<ExceptionDetail> detail;

    Cause(Optional<ExceptionDetail> detail) {
      this.detail = detail;
    }
  }

  /** Returns the results a first attempt with no cause is retried for. */
  private static EnumSet<TestResult> retriedResults(Retry retry, int flakyTestAttempts) {
    JobRetryContext job =
        new JobRetryContext(
            retry,
            "MoblyTest",
            "test-user",
            flakyTestAttempts,
            /* flakyTestParallelRetry= */ false,
            /* retryOnTimeout= */ true,
            ContainerModePreference.CONTAINER_MODE_PREFERENCE_UNSPECIFIED.name());
    EnumSet<TestResult> results = EnumSet.allOf(TestResult.class);
    results.removeIf(result ->
        RetryEvaluator.evaluateWorkerAttempt(
                job,
                JOB_EXPIRE_TIME,
                BASE_TIME.plusSeconds(60),
                snapshot(result, Optional.empty(), ImmutableMap.of()),
                /* attemptCount= */ 0L,
                ImmutableMap::of)
            .isEmpty());
    return results;
  }
}
