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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.TestSuiteResultLoader;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestCase;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestResult;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestStatus;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;
import javax.inject.Inject;

/** Processor to process Android instrumentation test retry. */
class AndroidInstrumentationTestRetryProcessor {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  // TestInfo properties are protobuf strings rather than HTML, so HTML escaping is unnecessary.
  // Disabling it avoids turning '=', '<', '>', and '\'' in parameterized test names (e.g.
  // "[alpha,count=10]") into "\\u003d"-style Unicode escapes in logs, test properties, and unit
  // test assertions.
  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
  private static final String RETRY_TEST_TARGETS_KEY =
      Ascii.toLowerCase(
          Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_RETRY_TEST_TARGETS.name());
  private static final String RETRY_TEST_TARGET_LIST_KEY =
      Ascii.toLowerCase(
          Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_RETRY_TEST_TARGET_LIST.name());

  private final TestSuiteResultLoader testSuiteResultLoader;

  public AndroidInstrumentationTestRetryProcessor() {
    this(new TestSuiteResultLoader());
  }

  @Inject
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
      // If test result of current test is NOT fail but caller still wants to retry it, retry
      // target properties are inherited from the current test if any.
      ImmutableMap.Builder<String, String> inherited = ImmutableMap.builder();
      currentTestInfo
          .properties()
          .getOptional(Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_RETRY_TEST_TARGET_LIST)
          .filter(value -> !value.isEmpty())
          .ifPresent(value -> inherited.put(RETRY_TEST_TARGET_LIST_KEY, value));
      // TODO: baisheng - Remove RETRY_TEST_TARGETS_KEY once all labs migrate to the new property
      // ANDROID_INSTRUMENTATION_RETRY_TEST_TARGET_LIST.
      currentTestInfo
          .properties()
          .getOptional(Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_RETRY_TEST_TARGETS)
          .filter(value -> !value.isEmpty())
          .ifPresent(value -> inherited.put(RETRY_TEST_TARGETS_KEY, value));
      return inherited.buildOrThrow();
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
            .filter(testResult ->
                testResult.getTestStatus() == TestStatus.FAILED
                    || testResult.getTestStatus() == TestStatus.ERROR)
            .map(TestResult::getTestCase)
            .collect(toImmutableList());
    if (failedOrErrorTestCases.isEmpty()) {
      logger.atInfo().log(
          "No failed or error test cases found for test %s.", currentTestInfo.locator().getId());
      return ImmutableMap.of();
    }
    ImmutableList<String> failedOrErrorTargets =
        failedOrErrorTestCases.stream()
            .map(testCase ->
                String.format(
                    "%s.%s#%s",
                    testCase.getTestPackage(), testCase.getTestClass(), testCase.getTestMethod()))
            .collect(toImmutableList());
    return ImmutableMap.of(
        RETRY_TEST_TARGET_LIST_KEY, GSON.toJson(failedOrErrorTargets),
        RETRY_TEST_TARGETS_KEY, String.join(",", failedOrErrorTargets));
  }
}
