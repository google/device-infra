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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidInstrumentationDriverSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/** Processor to process test retry. */
public class TestRetryProcessor {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AndroidInstrumentationTestRetryProcessor androidInstrumentationTestRetryProcessor;

  public TestRetryProcessor() {
    this(new AndroidInstrumentationTestRetryProcessor());
  }

  @VisibleForTesting
  TestRetryProcessor(
      AndroidInstrumentationTestRetryProcessor androidInstrumentationTestRetryProcessor) {
    this.androidInstrumentationTestRetryProcessor = androidInstrumentationTestRetryProcessor;
  }

  /**
   * Generates properties (like retry test targets) for a retry test based on the execution result
   * of the current test.
   */
  public ImmutableMap<String, String> generateRetryTestTargetsProperty(TestInfo currentTestInfo) {
    String driver = currentTestInfo.jobInfo().type().getDriver();
    // Currently only allow Android instrumentation failed-tests-only retry when the param is
    // explicitly set.
    boolean enableAndroidInstrumentationFailedTestsOnlyRetry =
        currentTestInfo
            .jobInfo()
            .params()
            .getBool(
                AndroidInstrumentationDriverSpec
                    .PARAM_ENABLE_ANDROID_INSTRUMENTATION_FAILED_TESTS_ONLY_RETRY,
                false);
    if (enableAndroidInstrumentationFailedTestsOnlyRetry
        && driver.equals("AndroidInstrumentation")) {
      logger.atInfo().log(
          "Processing test retry for test %s running with AndroidInstrumentation.",
          currentTestInfo.locator().getId());
      return androidInstrumentationTestRetryProcessor.generateRetryTestTargetsProperty(
          currentTestInfo);
    }
    return ImmutableMap.of();
  }
}
