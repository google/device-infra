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
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import com.google.devtools.mobileharness.shared.util.testresult.loader.JunitXmlTestCase;
import com.google.devtools.mobileharness.shared.util.testresult.loader.JunitXmlTestSuite;
import java.time.Duration;
import java.util.List;

/**
 * Helper converter class for JUnit (Ant) XML test result formats.
 *
 * <p>Test cases are grouped into {@link TestSuiteOverview}s by the package component of the JUnit
 * {@code classname} attribute. For iOS XCTest results this component is the test target, e.g. both
 * test cases below belong to the {@code EarlGreyExampleSwiftTests} target:
 *
 * <pre>{@code
 * <testsuite name='' tests='2' ...>
 *   <testcase name='testLayout' classname='EarlGreyExampleSwiftTests.FooTest' time='4'/>
 *   <testcase name='testTap' classname='EarlGreyExampleSwiftTests.BarTest' time='1'/>
 * </testsuite>
 * }</pre>
 *
 * <p>Test cases whose {@code classname} has no package component fall back to the name of the
 * enclosing {@code <testsuite>} element.
 */
public final class JunitTestSuiteResultConverter {

  private JunitTestSuiteResultConverter() {}

  /** Converts parsed JUnit XML {@link JunitXmlTestSuite}s to a rollup {@link TestResult}. */
  public static TestResult toTestResult(List<JunitXmlTestSuite> testSuites) {
    ImmutableList.Builder<TestCase> testCasesBuilder = ImmutableList.builder();
    for (JunitXmlTestSuite testSuite : testSuites) {
      for (JunitXmlTestCase testCase : testSuite.testCases()) {
        testCasesBuilder.add(toTestCase(testCase, testSuite.name()));
      }
    }
    ImmutableList<TestCase> testCases = testCasesBuilder.build();

    ImmutableListMultimap<String, TestCase> testCasesBySuiteName =
        Multimaps.index(testCases, testCase -> testCase.testCaseReference().testSuiteName());
    ImmutableList<TestSuiteOverview> testSuiteOverviews =
        testCasesBySuiteName.asMap().entrySet().stream()
            .map(
                entry ->
                    TestSuiteOverview.builder()
                        .setName(entry.getKey())
                        .setElapsedTime(sumElapsedTime(entry.getValue()))
                        .build())
            .collect(toImmutableList());

    return TestResult.create(
        testCases, testSuiteOverviews, TestCase.convertToOutcome(testCases), State.COMPLETE);
  }

  private static Duration sumElapsedTime(Iterable<TestCase> testCases) {
    Duration total = Duration.ZERO;
    for (TestCase testCase : testCases) {
      total = total.plus(testCase.elapsedTime());
    }
    return total;
  }

  private static TestCase toTestCase(JunitXmlTestCase testCase, String enclosingTestSuiteName) {
    TestCase.Builder testCaseBuilder =
        TestCase.builder()
            .setTestCaseReference(
                TestCaseReference.builder()
                    .setName(testCase.name())
                    .setClassName(testCase.className())
                    .setTestSuiteName(
                        getTestSuiteName(testCase.className(), enclosingTestSuiteName))
                    .build())
            .setElapsedTime(testCase.elapsedTime())
            .setStatus(getTestCaseStatus(testCase.status()));
    testCase.stackTrace().map(StackTrace::create).ifPresent(testCaseBuilder::addStackTraces);
    return testCaseBuilder.build();
  }

  /**
   * Returns the test suite name of the given test case, which is the package component of its
   * {@code classname}, or {@code enclosingTestSuiteName} if the {@code classname} has none.
   */
  private static String getTestSuiteName(String className, String enclosingTestSuiteName) {
    int lastDotIndex = className.lastIndexOf('.');
    return lastDotIndex > 0 ? className.substring(0, lastDotIndex) : enclosingTestSuiteName;
  }

  private static TestCase.TestStatus getTestCaseStatus(JunitXmlTestCase.Status status) {
    return switch (status) {
      case PASSED -> TestCase.TestStatus.PASSED;
      case FAILED -> TestCase.TestStatus.FAILED;
      case SKIPPED -> TestCase.TestStatus.SKIPPED;
      case ERROR -> TestCase.TestStatus.ERROR;
    };
  }
}
