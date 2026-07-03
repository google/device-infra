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

package com.google.devtools.mobileharness.shared.util.testresult.rollup;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Data class representing a test case (test method) execution result. */
@AutoValue
public abstract class TestCase {

  /** Status of an individual test case run. */
  public enum TestStatus {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED,
    FLAKY
  }

  public abstract TestCaseReference testCaseReference();

  public abstract TestStatus status();

  @Nullable
  public abstract Instant startTime();

  @Nullable
  public abstract Instant endTime();

  public abstract Duration elapsedTime();

  public abstract ImmutableList<StackTrace> stackTraces();

  public static TestCase create(
      TestCaseReference testCaseReference,
      TestStatus status,
      @Nullable Instant startTime,
      @Nullable Instant endTime,
      Duration elapsedTime,
      ImmutableList<StackTrace> stackTraces) {
    return builder()
        .setTestCaseReference(testCaseReference)
        .setStatus(status)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setElapsedTime(elapsedTime)
        .setStackTraces(stackTraces)
        .build();
  }

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_TestCase.Builder()
        .setStackTraces(ImmutableList.of())
        .setElapsedTime(Duration.ZERO);
  }

  /** Builder for {@link TestCase}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setTestCaseReference(TestCaseReference testCaseReference);

    public abstract Builder setStatus(TestStatus status);

    public abstract Builder setStartTime(@Nullable Instant startTime);

    public abstract Builder setEndTime(@Nullable Instant endTime);

    public abstract Builder setElapsedTime(Duration elapsedTime);

    public abstract Builder setStackTraces(ImmutableList<StackTrace> stackTraces);

    abstract ImmutableList.Builder<StackTrace> stackTracesBuilder();

    @CanIgnoreReturnValue
    public Builder addStackTraces(StackTrace stackTrace) {
      stackTracesBuilder().add(stackTrace);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAllStackTraces(Iterable<StackTrace> stackTraces) {
      stackTracesBuilder().addAll(stackTraces);
      return this;
    }

    public abstract TestCase build();
  }

  private static final Ordering<TestStatus> WORST_STATUS_FIRST_ORDERING =
      Ordering.explicit(
          TestStatus.FAILED,
          TestStatus.FLAKY,
          TestStatus.PASSED,
          TestStatus.SKIPPED,
          TestStatus.ERROR);

  /** Computes {@link Outcome} based on rolled up test cases. */
  public static Outcome convertToOutcome(List<TestCase> rolledUpTestCases) {
    if (rolledUpTestCases.isEmpty()) {
      return Outcome.create(Outcome.OutcomeSummary.INCONCLUSIVE);
    }
    TestStatus rolledUpTestStatus =
        rolledUpTestCases.stream()
            .map(TestCase::status)
            .min(WORST_STATUS_FIRST_ORDERING)
            .orElse(TestStatus.ERROR);

    return switch (rolledUpTestStatus) {
      case ERROR -> Outcome.create(Outcome.OutcomeSummary.INCONCLUSIVE);
      case FAILED, SKIPPED -> Outcome.create(Outcome.OutcomeSummary.FAILURE);
      case FLAKY -> Outcome.create(Outcome.OutcomeSummary.FLAKY);
      case PASSED -> Outcome.create(Outcome.OutcomeSummary.SUCCESS);
    };
  }

  /** Rolls up the same test case from different runs/attempts to a single {@link TestCase}. */
  public static Optional<TestCase> rollUp(List<TestCase> testCasesFromDifferentRuns) {
    if (testCasesFromDifferentRuns.isEmpty()) {
      return Optional.empty();
    }
    TestCase first = testCasesFromDifferentRuns.get(0);
    Builder builder = TestCase.builder().setTestCaseReference(first.testCaseReference());

    ImmutableSet<TestStatus> statuses =
        testCasesFromDifferentRuns.stream().map(TestCase::status).collect(toImmutableSet());

    TestStatus worstStatus =
        statuses.stream().min(WORST_STATUS_FIRST_ORDERING).orElse(TestStatus.ERROR);

    boolean hasPass = statuses.contains(TestStatus.PASSED);
    boolean hasFail = statuses.contains(TestStatus.FAILED);
    TestStatus finalStatus = (hasPass && hasFail) ? TestStatus.FLAKY : worstStatus;
    builder.setStatus(finalStatus);

    Duration maxElapsed =
        testCasesFromDifferentRuns.stream()
            .map(TestCase::elapsedTime)
            .max(Duration::compareTo)
            .orElse(Duration.ZERO);
    builder.setElapsedTime(maxElapsed);

    testCasesFromDifferentRuns.forEach(tc -> builder.addAllStackTraces(tc.stackTraces()));

    return Optional.of(builder.build());
  }
}
