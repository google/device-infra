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

package com.google.wireless.qa.mobileharness.shared.model.job.util;

import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException.DesiredTestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.ResultCounter;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for handling test results generated by MH or Moscar. Cannot support
 * FluentLogger/Logger.
 */
public final class ResultUtil {

  /** Adapter interface to adapt test like classes to be used in this utility class. */
  public interface TestAdapter<T> {
    // Gets name of a test.
    String getName(T t);

    // Gets test status of a test.
    TestStatus getStatus(T t);

    // Get test result of a test.
    TestResult getResult(T t);
  }

  /** Gets test result with the given exception. */
  public static TestResult getResultByException(MobileHarnessException e) {
    return TestResult.ERROR;
  }

  public static DesiredTestResult getDesiredTestResultByException(MobileHarnessException e) {
    ErrorId errorId = e.getErrorId();
    switch (errorId.type()) {
      case CUSTOMER_ISSUE:
        return DesiredTestResult.FAIL;
      case INFRA_ISSUE:
        return DesiredTestResult.ERROR;
      case DEPENDENCY_ISSUE:
      case UNCLASSIFIED:
      case UNDETERMINED:
      case UNRECOGNIZED:
        break;
    }
    return DesiredTestResult.ERROR;
  }

  /**
   * Sorts the given test list by test name. For tests with the same name, this method follows
   * {@code JobInfo#getFinalizedTests} to put the test with best result before any others. So test
   * with PASS result is before any other tests, and test with UNKNOWN/TIMEOUT result is after any
   * other tests, other tests in the middle are sorted in start time descending order.
   */
  public static <T> List<T> sortTest(List<T> tests, final TestAdapter<T> testAdapter) {
    List<T> sortedTests = new ArrayList<>(tests);
    Collections.sort(
        sortedTests,
        new Comparator<T>() {
          private final ResultComparator testResultComparator = new ResultComparator();

          @Override
          public int compare(T o1, T o2) {
            String testName1 = testAdapter.getName(o1);
            String testName2 = testAdapter.getName(o2);
            int result = testName1.compareTo(testName2);
            if (result == 0) {
              TestResult leftResult = testAdapter.getResult(o1);
              TestResult rightResult = testAdapter.getResult(o2);
              return testResultComparator.compare(leftResult, rightResult);
            }
            return result;
          }
        });
    return sortedTests;
  }

  /** Gets test count statistics for a sorted list of tests, return {@link ResultCounter}. */
  public static <T> ResultCounter getSortedTestResultCounter(
      List<T> tests, final TestAdapter<T> testAdapter) {
    List<T> unifiedTests = new ArrayList<>();
    String preName = "";
    for (T test : tests) {
      String testName = testAdapter.getName(test);
      if (!testName.equals(preName)) {
        unifiedTests.add(test);
      }
      preName = testName;
    }
    return getTestResultCounter(unifiedTests, testAdapter);
  }

  /** Gets test count statistics for a collection of tests, return {@link ResultCounter}. */
  public static <T> ResultCounter getTestResultCounter(
      Collection<T> tests, final TestAdapter<T> testAdapter) {
    Map<TestResult, Integer> resultCount = new EnumMap<>(TestResult.class);
    for (TestResult result : TestResult.values()) {
      resultCount.put(result, 0);
    }
    int pending = 0;
    for (T test : tests) {
      if (testAdapter.getStatus(test) != TestStatus.DONE) {
        pending++;
        continue;
      }
      TestResult result = testAdapter.getResult(test);
      resultCount.put(result, resultCount.get(result) + 1);
    }
    return ResultCounter.newBuilder()
        .setTotal(tests.size())
        .setPassed(resultCount.get(TestResult.PASS))
        .setFailed(resultCount.get(TestResult.FAIL))
        .setError(resultCount.get(TestResult.ERROR))
        .setInfraError(resultCount.get(TestResult.INFRA_ERROR))
        .setTimeout(resultCount.get(TestResult.TIMEOUT))
        .setUnknown(pending + resultCount.get(TestResult.UNKNOWN))
        .setAbort(resultCount.get(TestResult.ABORT))
        .setSkipped(resultCount.get(TestResult.SKIP))
        .build();
  }

  private ResultUtil() {}
}
