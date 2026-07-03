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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.Outcome.OutcomeSummary;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TestCaseTest {

  private static final TestCaseReference TEST_CASE_REFERENCE =
      TestCaseReference.create("testMethod", "FakeTestClass", "TestSuiteName");

  @Test
  public void builder_fieldsAssertion() {
    Instant startTime = Instant.ofEpochMilli(1000);
    Instant endTime = Instant.ofEpochMilli(2000);
    TestCase testCase =
        TestCase.builder()
            .setTestCaseReference(TEST_CASE_REFERENCE)
            .setStatus(TestStatus.PASSED)
            .setStartTime(startTime)
            .setEndTime(endTime)
            .setElapsedTime(Duration.ofSeconds(1))
            .addStackTraces(StackTrace.create("Failed trace"))
            .build();

    assertThat(testCase.testCaseReference()).isEqualTo(TEST_CASE_REFERENCE);
    assertThat(testCase.status()).isEqualTo(TestStatus.PASSED);
    assertThat(testCase.startTime()).isEqualTo(startTime);
    assertThat(testCase.endTime()).isEqualTo(endTime);
    assertThat(testCase.elapsedTime()).isEqualTo(Duration.ofSeconds(1));
    assertThat(testCase.stackTraces()).containsExactly(StackTrace.create("Failed trace"));
  }

  @Test
  public void convertToOutcome_emptyList_returnsInconclusive() {
    Outcome outcome = TestCase.convertToOutcome(ImmutableList.of());
    assertThat(outcome.summary()).isEqualTo(OutcomeSummary.INCONCLUSIVE);
  }

  @Test
  public void convertToOutcome_errorMapsToInconclusive() {
    TestCase testCase = createTestCase(TestStatus.ERROR);
    assertThat(TestCase.convertToOutcome(ImmutableList.of(testCase)).summary())
        .isEqualTo(OutcomeSummary.INCONCLUSIVE);
  }

  @Test
  public void convertToOutcome_failedMapsToFailure() {
    TestCase testCase = createTestCase(TestStatus.FAILED);
    assertThat(TestCase.convertToOutcome(ImmutableList.of(testCase)).summary())
        .isEqualTo(OutcomeSummary.FAILURE);
  }

  @Test
  public void convertToOutcome_skippedMapsToFailure() {
    TestCase testCase = createTestCase(TestStatus.SKIPPED);
    assertThat(TestCase.convertToOutcome(ImmutableList.of(testCase)).summary())
        .isEqualTo(OutcomeSummary.FAILURE);
  }

  @Test
  public void convertToOutcome_flakyMapsToFlaky() {
    TestCase testCase = createTestCase(TestStatus.FLAKY);
    assertThat(TestCase.convertToOutcome(ImmutableList.of(testCase)).summary())
        .isEqualTo(OutcomeSummary.FLAKY);
  }

  @Test
  public void convertToOutcome_passedMapsToSuccess() {
    TestCase testCase = createTestCase(TestStatus.PASSED);
    assertThat(TestCase.convertToOutcome(ImmutableList.of(testCase)).summary())
        .isEqualTo(OutcomeSummary.SUCCESS);
  }

  @Test
  public void rollUp_emptyList_returnsEmptyOptional() {
    assertThat(TestCase.rollUp(ImmutableList.of())).isEmpty();
  }

  @Test
  public void rollUp_passedAndFailed_returnsFlaky() {
    TestCase testCase1 = createTestCase(TestStatus.FAILED);
    TestCase testCase2 = createTestCase(TestStatus.PASSED);

    Optional<TestCase> rolledUp = TestCase.rollUp(ImmutableList.of(testCase1, testCase2));
    assertThat(rolledUp).hasValue(createTestCase(TestStatus.FLAKY));
  }

  @Test
  public void rollUp_worseStatusTakesPriorityWhenNotFlaky() {
    TestCase testCase1 = createTestCase(TestStatus.PASSED);
    TestCase testCase2 = createTestCase(TestStatus.SKIPPED);

    Optional<TestCase> rolledUp = TestCase.rollUp(ImmutableList.of(testCase1, testCase2));
    // PASSED & SKIPPED doesn't produce FLAKY, it should get min(PASSED, SKIPPED) which is PASSED
    assertThat(rolledUp).hasValue(createTestCase(TestStatus.PASSED));
  }

  @Test
  public void rollUp_accumulatesLongestElapsedTimeAndStackTraces() {
    TestCase testCase1 =
        TestCase.builder()
            .setTestCaseReference(TEST_CASE_REFERENCE)
            .setStatus(TestStatus.FAILED)
            .setElapsedTime(Duration.ofSeconds(2))
            .addStackTraces(StackTrace.create("trace1"))
            .build();
    TestCase testCase2 =
        TestCase.builder()
            .setTestCaseReference(TEST_CASE_REFERENCE)
            .setStatus(TestStatus.PASSED)
            .setElapsedTime(Duration.ofSeconds(5))
            .addStackTraces(StackTrace.create("trace2"))
            .build();

    TestCase expected =
        TestCase.builder()
            .setTestCaseReference(TEST_CASE_REFERENCE)
            .setStatus(TestStatus.FLAKY)
            .setElapsedTime(Duration.ofSeconds(5))
            .addStackTraces(StackTrace.create("trace1"))
            .addStackTraces(StackTrace.create("trace2"))
            .build();

    Optional<TestCase> rolledUp = TestCase.rollUp(ImmutableList.of(testCase1, testCase2));
    assertThat(rolledUp).hasValue(expected);
  }

  private static TestCase createTestCase(TestStatus status) {
    return TestCase.builder().setTestCaseReference(TEST_CASE_REFERENCE).setStatus(status).build();
  }
}
