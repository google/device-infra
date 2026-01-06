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

package com.google.devtools.mobileharness.platform.android.xts.suite.retry;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.platform.android.xts.common.TestStatus;
import java.time.Duration;

/** Util to calculate retry statistics. */
public class RetryStatsHelper {

  /** Data class to hold the retry statistics for a module. */
  @AutoValue
  public abstract static class RetryStatistics {

    /** Creates a {@link RetryStatistics}. */
    public static RetryStatistics of(long retrySuccess, long retryFailure, Duration retryTime) {
      return new AutoValue_RetryStatsHelper_RetryStatistics(retrySuccess, retryFailure, retryTime);
    }

    public abstract long retrySuccess();

    public abstract long retryFailure();

    public abstract Duration retryTime();
  }

  public RetryStatistics calculateModuleRetryStats(Module retryModule, Module previousModule) {
    long retrySuccess =
        Sets.difference(getFailedTests(previousModule), getFailedTests(retryModule)).size();
    long retryFailure = getFailedTests(retryModule).size();
    Duration retryTime = Duration.ofMillis(retryModule.getRuntimeMillis());
    return RetryStatistics.of(retrySuccess, retryFailure, retryTime);
  }

  private static ImmutableSet<String> getFailedTests(Module module) {
    ImmutableSet.Builder<String> failedTests = ImmutableSet.builder();

    for (TestCase testCase : module.getTestCaseList()) {
      StringBuilder fullTestName = new StringBuilder(testCase.getName()).append("#");
      for (Test test : testCase.getTestList()) {
        if (test.getResult()
                .equals(TestStatus.convertToTestStatusCompatibilityString(TestStatus.FAILURE))
            || test.getResult()
                .equals(TestStatus.convertToTestStatusCompatibilityString(TestStatus.SKIPPED))
            || test.getResult()
                .equals(TestStatus.convertToTestStatusCompatibilityString(TestStatus.WARNING))) {
          // Retry skipped and warning test as well
          failedTests.add(fullTestName.append(test.getName()).toString());
        }
      }
    }
    return failedTests.build();
  }
}
