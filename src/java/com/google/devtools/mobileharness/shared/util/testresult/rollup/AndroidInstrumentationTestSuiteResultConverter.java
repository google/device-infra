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
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestStatus;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.Outcome.OutcomeSummary;
import com.google.protobuf.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

/** Helper converter class for Android Instrumentation test result formats. */
public final class AndroidInstrumentationTestSuiteResultConverter {

  private AndroidInstrumentationTestSuiteResultConverter() {}

  /** Converts a {@link TestSuiteResult} to a rollup {@link TestResult}. */
  public static TestResult toTestResult(TestSuiteResult testSuiteResult) {
    String suiteName =
        testSuiteResult.hasTestSuiteMetaData()
            ? testSuiteResult.getTestSuiteMetaData().getTestSuiteName()
            : "";

    ImmutableList<TestCase> testCases =
        testSuiteResult.getTestResultList().stream()
            .map(testResult -> toTestCase(testResult, suiteName))
            .collect(toImmutableList());

    TestSuiteOverview suiteOverview =
        TestSuiteOverview.builder()
            .setName(suiteName)
            .setElapsedTime(getTotalElapsedTime(testCases))
            .build();

    Outcome outcome =
        Outcome.create(getOutcomeSummaryFromTestStatus(testSuiteResult.getTestStatus()));
    return TestResult.create(testCases, ImmutableList.of(suiteOverview), outcome, State.COMPLETE);
  }

  /**
   * Converts an Android Instrumentation test case {@link
   * com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestResult} to
   * a {@link TestCase}.
   */
  public static TestCase toTestCase(
      com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestResult
          testResult,
      String suiteName) {
    var testCase = testResult.getTestCase();

    String className =
        testCase.getTestPackage().isEmpty()
            ? testCase.getTestClass()
            : testCase.getTestPackage() + "." + testCase.getTestClass();

    TestCaseReference testCaseRef =
        TestCaseReference.builder()
            .setName(testCase.getTestMethod())
            .setClassName(className)
            .setTestSuiteName(suiteName)
            .build();

    TestCase.Builder testCaseBuilder = TestCase.builder().setTestCaseReference(testCaseRef);

    Instant startInstant = null;
    if (testCase.hasStartTime()) {
      Timestamp ts = testCase.getStartTime();
      startInstant = Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
      testCaseBuilder.setStartTime(startInstant);
    }
    Instant endInstant = null;
    if (testCase.hasEndTime()) {
      Timestamp ts = testCase.getEndTime();
      endInstant = Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
      testCaseBuilder.setEndTime(endInstant);
    }
    if (startInstant != null && endInstant != null) {
      testCaseBuilder.setElapsedTime(Duration.between(startInstant, endInstant));
    }

    testCaseBuilder.setStatus(getTestCaseStatus(testResult.getTestStatus()));

    if (testResult.hasError() && !testResult.getError().getErrorMessage().isEmpty()) {
      testCaseBuilder.addStackTraces(
          StackTrace.create(
              testResult.getError().getErrorMessage()
                  + "\n"
                  + testResult.getError().getStackTrace()));
    }

    return testCaseBuilder.build();
  }

  private static Duration getTotalElapsedTime(Collection<TestCase> testCases) {
    return testCases.stream().map(TestCase::elapsedTime).reduce(Duration.ZERO, Duration::plus);
  }

  private static TestCase.TestStatus getTestCaseStatus(TestStatus status) {
    if (status == null) {
      return TestCase.TestStatus.ERROR;
    }
    return switch (status) {
      case PASSED -> TestCase.TestStatus.PASSED;
      case FAILED -> TestCase.TestStatus.FAILED;
      case IGNORED, SKIPPED -> TestCase.TestStatus.SKIPPED;
      case ERROR, ABORTED, CANCELLED, TEST_STATUS_UNSPECIFIED, UNRECOGNIZED ->
          TestCase.TestStatus.ERROR;
    };
  }

  private static OutcomeSummary getOutcomeSummaryFromTestStatus(TestStatus status) {
    if (status == null) {
      return OutcomeSummary.INCONCLUSIVE;
    }
    return switch (status) {
      case PASSED -> OutcomeSummary.SUCCESS;
      case FAILED -> OutcomeSummary.FAILURE;
      case IGNORED, SKIPPED -> OutcomeSummary.SKIPPED;
      case ERROR, ABORTED, CANCELLED, TEST_STATUS_UNSPECIFIED, UNRECOGNIZED ->
          OutcomeSummary.INCONCLUSIVE;
    };
  }
}
