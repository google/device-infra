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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.devtools.mobileharness.shared.util.testresult.Outcome;
import com.google.devtools.mobileharness.shared.util.testresult.OutcomeSummary;
import com.google.devtools.mobileharness.shared.util.testresult.TestCase;
import com.google.devtools.mobileharness.shared.util.testresult.TestStatus;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Helper utility class that rolls up attempts of a single test case across runs. */
public final class TestCaseRollupHelper {

  public static final Ordering<TestStatus> WORST_STATUS_FIRST_ORDERING =
      Ordering.explicit(
          TestStatus.FAILED,
          TestStatus.FLAKY,
          TestStatus.PASSED,
          TestStatus.SKIPPED,
          TestStatus.ERROR);

  private TestCaseRollupHelper() {}

  /** Computes {@link Outcome} based on rolled up test cases. */
  public static Outcome convertTestCaseResultsToOutcome(List<TestCase> rolledUpTestCases) {
    if (rolledUpTestCases.isEmpty()) {
      return Outcome.create(OutcomeSummary.INCONCLUSIVE);
    }
    TestStatus rolledUpTestStatus =
        rolledUpTestCases.stream()
            .map(TestCase::status)
            .min(WORST_STATUS_FIRST_ORDERING)
            .orElse(TestStatus.ERROR);

    return switch (rolledUpTestStatus) {
      case ERROR -> Outcome.create(OutcomeSummary.INCONCLUSIVE);
      case FAILED, SKIPPED -> Outcome.create(OutcomeSummary.FAILURE);
      case FLAKY -> Outcome.create(OutcomeSummary.FLAKY);
      case PASSED -> Outcome.create(OutcomeSummary.SUCCESS);
    };
  }

  /** Rolls up the same test cases from different runs/attempts to a single {@link TestCase}. */
  public static Optional<TestCase> rollupTestCasesFromDifferentRuns(
      List<TestCase> testCasesFromDifferentRuns) {
    if (testCasesFromDifferentRuns.isEmpty()) {
      return Optional.empty();
    }
    TestCase first = testCasesFromDifferentRuns.get(0);
    TestCase.Builder builder = TestCase.builder().setTestCaseReference(first.testCaseReference());

    ImmutableList<TestStatus> statuses =
        testCasesFromDifferentRuns.stream().map(TestCase::status).collect(toImmutableList());

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
