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

package com.google.devtools.mobileharness.platform.android.instrumentation.result;

import static com.google.protobuf.util.Durations.toMillis;
import static com.google.protobuf.util.Timestamps.between;
import static java.lang.Long.max;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestCase;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.devtools.mobileharness.shared.util.junit.xmlwriter.model.TestCaseNode;
import com.google.devtools.mobileharness.shared.util.junit.xmlwriter.model.TestResult;
import com.google.devtools.mobileharness.shared.util.junit.xmlwriter.model.TestSuiteNode;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import javax.inject.Inject;

/** Converter to convert {@link TestSuiteResult} to {@link TestResult}. */
public class TestSuiteResultConverter {

  @VisibleForTesting static final ZoneId ZONE_ID = ZoneId.of("America/Los_Angeles");

  @Inject
  TestSuiteResultConverter() {}

  public static TestResult toJunitTestResult(
      TestSuiteResult testSuiteResult, Timestamp testStartTime) {
    TestSuiteNode allTestSuites = new TestSuiteNode("All targets");

    String testSuiteName = testSuiteResult.getTestSuiteMetaData().getTestSuiteName();

    LocalDateTime timestamp =
        Instant.ofEpochSecond(testStartTime.getSeconds()).atZone(ZONE_ID).toLocalDateTime();
    TestSuiteNode testSuiteNode = new TestSuiteNode(testSuiteName);
    testSuiteNode.setTimestamp(timestamp);

    for (com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestResult
        result : testSuiteResult.getTestResultList()) {
      TestCaseNode testCaseNode = toTestCaseNode(result);
      testSuiteNode.addTestCase(testCaseNode);
    }

    allTestSuites.addTestSuite(testSuiteNode);
    return allTestSuites.buildResult();
  }

  private static TestCaseNode toTestCaseNode(
      com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestResult
          result) {
    TestCase testCase = result.getTestCase();
    String className =
        testCase.getTestPackage().isEmpty()
            ? testCase.getTestClass()
            : Joiner.on(".").join(testCase.getTestPackage(), testCase.getTestClass());
    TestCaseNode testCaseNode = new TestCaseNode(className, testCase.getTestMethod());
    testCaseNode.setRunTime(
        max(toMillis(between(testCase.getStartTime(), testCase.getEndTime())), 0));

    switch (result.getTestStatus()) {
      case PASSED -> {}
      case SKIPPED, IGNORED -> {
        // Pass through
        testCaseNode.testSkipped();
        if (!result.getError().getErrorMessage().isEmpty()) {
          testCaseNode.testSkipped(result.getError().getErrorMessage());
        }
      }
      default -> {
        // All other cases as failure
        if (!result.getError().getErrorMessage().isEmpty()) {
          testCaseNode.testFailed(result.getError().getErrorMessage());
        } else {
          testCaseNode.testFailed(result.getTestStatus().name());
        }
      }
    }

    return testCaseNode;
  }
}
