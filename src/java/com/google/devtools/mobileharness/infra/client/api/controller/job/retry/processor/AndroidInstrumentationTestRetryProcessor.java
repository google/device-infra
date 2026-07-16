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

package com.google.devtools.mobileharness.infra.client.api.controller.job.retry.processor;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.TestSuiteResultLoader;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestCase;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestResult;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestStatus;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;

/** Processor to process Android instrumentation test retry. */
class AndroidInstrumentationTestRetryProcessor {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final TestSuiteResultLoader testSuiteResultLoader;

  public AndroidInstrumentationTestRetryProcessor() {
    this(new TestSuiteResultLoader());
  }

  @VisibleForTesting
  AndroidInstrumentationTestRetryProcessor(TestSuiteResultLoader testSuiteResultLoader) {
    this.testSuiteResultLoader = testSuiteResultLoader;
  }

  /**
   * Generates properties (like failed test classes/methods to rerun) for retrying an Android
   * instrumentation test based on the execution result of the current test.
   */
  public ImmutableMap<String, String> generateRetryTestTargetsProperty(TestInfo currentTestInfo) {
    com.google.devtools.mobileharness.api.model.proto.Test.TestResult currentTestResult =
        currentTestInfo.resultWithCause().get().type();
    if (currentTestResult
        != com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL) {
      // If test result of current test is NOT fail but caller still wants to retry it, test
      // property ANDROID_INSTRUMENTATION_RETRY_TEST_TARGETS will be inherited from the current test
      // if any.
      Optional<String> retryTestTargets =
          currentTestInfo
              .properties()
              .getOptional(Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_RETRY_TEST_TARGETS);
      if (retryTestTargets.isPresent() && !retryTestTargets.get().isEmpty()) {
        return ImmutableMap.of(
            Ascii.toLowerCase(
                Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_RETRY_TEST_TARGETS.name()),
            retryTestTargets.get());
      }
      return ImmutableMap.of();
    }

    Optional<TestSuiteResult> testSuiteResult =
        testSuiteResultLoader.loadTestResult(currentTestInfo);
    if (testSuiteResult.isEmpty()) {
      logger.atInfo().log(
          "No Android instrumentation test result found for test %s.",
          currentTestInfo.locator().getId());
      return ImmutableMap.of();
    }
    ImmutableList<TestCase> failedOrErrorTestCases =
        testSuiteResult.get().getTestResultList().stream()
            .filter(
                testResult ->
                    testResult.getTestStatus() == TestStatus.FAILED
                        || testResult.getTestStatus() == TestStatus.ERROR)
            .map(TestResult::getTestCase)
            .collect(toImmutableList());
    if (failedOrErrorTestCases.isEmpty()) {
      logger.atInfo().log(
          "No failed or error test cases found for test %s.", currentTestInfo.locator().getId());
      return ImmutableMap.of();
    }
    String failedOrErrorTestTargets =
        failedOrErrorTestCases.stream()
            .map(
                testCase ->
                    String.format(
                        "%s.%s#%s",
                        testCase.getTestPackage(),
                        testCase.getTestClass(),
                        testCase.getTestMethod()))
            .collect(joining(","));
    return ImmutableMap.of(
        Ascii.toLowerCase(
            Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_RETRY_TEST_TARGETS.name()),
        failedOrErrorTestTargets);
  }
}
