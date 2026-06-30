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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestResult;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestStatus;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.devtools.mobileharness.shared.util.testresult.Outcome;
import com.google.devtools.mobileharness.shared.util.testresult.OutcomeSummary;
import com.google.devtools.mobileharness.shared.util.testresult.StackTrace;
import com.google.devtools.mobileharness.shared.util.testresult.State;
import com.google.devtools.mobileharness.shared.util.testresult.TestCase;
import com.google.devtools.mobileharness.shared.util.testresult.TestCaseReference;
import com.google.devtools.mobileharness.shared.util.testresult.TestSuiteOverview;
import com.google.protobuf.Timestamp;
import java.time.Duration;
import java.time.Instant;

/** Helper converter class for Android Instrumentation test result formats. */
public final class AndroidInstrumentationTestSuiteResultConverter {

  private AndroidInstrumentationTestSuiteResultConverter() {}

  /**
   * Converts a {@link TestSuiteResult} to a rollup {@link
   * com.google.devtools.mobileharness.shared.util.testresult.rollup.TestResult}.
   */
  public static com.google.devtools.mobileharness.shared.util.testresult.rollup.TestResult
      toTestResult(TestSuiteResult testSuiteResult) {
    ImmutableList.Builder<TestCase> testCasesBuilder = ImmutableList.builder();
    String suiteName =
        testSuiteResult.hasTestSuiteMetaData()
            ? testSuiteResult.getTestSuiteMetaData().getTestSuiteName()
            : "";

    for (TestResult testResult : testSuiteResult.getTestResultList()) {
      testCasesBuilder.add(toTestCase(testResult, suiteName));
    }

    ImmutableList<TestCase> testCases = testCasesBuilder.build();

    ImmutableList<TestSuiteOverview> suiteOverviews = ImmutableList.of();
    if (!suiteName.isEmpty() || !testCases.isEmpty()) {
      Duration totalElapsed = Duration.ZERO;
      for (TestCase tc : testCases) {
        totalElapsed = totalElapsed.plus(tc.elapsedTime());
      }
      TestSuiteOverview rawOverview =
          TestSuiteOverview.builder().setName(suiteName).setElapsedTime(totalElapsed).build();
      suiteOverviews = ImmutableList.of(rawOverview);
    }

    OutcomeSummary outcomeSummary =
        getOutcomeSummaryFromTestStatus(testSuiteResult.getTestStatus());
    Outcome outcome = Outcome.create(outcomeSummary);
    State state = State.COMPLETE;

    return com.google.devtools.mobileharness.shared.util.testresult.rollup.TestResult.create(
        testCases, suiteOverviews, outcome, state);
  }

  /** Converts an Android Instrumentation test case {@link TestResult} to a {@link TestCase}. */
  public static TestCase toTestCase(TestResult testResult, String suiteName) {
    com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestCase
        instrumentationTestCase = testResult.getTestCase();

    String className =
        instrumentationTestCase.getTestPackage().isEmpty()
            ? instrumentationTestCase.getTestClass()
            : instrumentationTestCase.getTestPackage()
                + "."
                + instrumentationTestCase.getTestClass();

    TestCaseReference testCaseRef =
        TestCaseReference.builder()
            .setName(instrumentationTestCase.getTestMethod())
            .setClassName(className)
            .setTestSuiteName(suiteName)
            .build();

    TestCase.Builder testCaseBuilder = TestCase.builder().setTestCaseReference(testCaseRef);

    Instant startInstant = null;
    if (instrumentationTestCase.hasStartTime()) {
      Timestamp ts = instrumentationTestCase.getStartTime();
      startInstant = Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
      testCaseBuilder.setStartTime(startInstant);
    }
    Instant endInstant = null;
    if (instrumentationTestCase.hasEndTime()) {
      Timestamp ts = instrumentationTestCase.getEndTime();
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

  private static com.google.devtools.mobileharness.shared.util.testresult.TestStatus
      getTestCaseStatus(TestStatus status) {
    if (status == null) {
      return com.google.devtools.mobileharness.shared.util.testresult.TestStatus.ERROR;
    }
    return switch (status) {
      case PASSED -> com.google.devtools.mobileharness.shared.util.testresult.TestStatus.PASSED;
      case FAILED -> com.google.devtools.mobileharness.shared.util.testresult.TestStatus.FAILED;
      case IGNORED, SKIPPED ->
          com.google.devtools.mobileharness.shared.util.testresult.TestStatus.SKIPPED;
      case ERROR, ABORTED, CANCELLED, TEST_STATUS_UNSPECIFIED, UNRECOGNIZED ->
          com.google.devtools.mobileharness.shared.util.testresult.TestStatus.ERROR;
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
