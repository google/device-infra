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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import java.time.Duration;
import java.util.List;

/** Data class representing basic test statistics for a test suite. */
@AutoValue
public abstract class TestSuiteOverview {
  /**
   * Name of the test suite.
   *
   * <p>For Android instrumentation tests, this is usually an empty string, i.e., there is only one
   * test suite. While for iOS tests, the test suite name is the test class name.
   */
  public abstract String name();

  /** Elapsed time of the test suite. */
  public abstract Duration elapsedTime();

  /** Total number of test cases. */
  public abstract int totalCount();

  /** Number of test cases with error status. */
  public abstract int errorCount();

  /** Number of test cases with failure status. */
  public abstract int failureCount();

  /** Number of test cases with skipped status. */
  public abstract int skippedCount();

  /** Number of test cases with flaky status. */
  public abstract int flakyCount();

  public static TestSuiteOverview create(
      String name,
      Duration elapsedTime,
      int totalCount,
      int errorCount,
      int failureCount,
      int skippedCount,
      int flakyCount) {
    return builder()
        .setName(name)
        .setElapsedTime(elapsedTime)
        .setTotalCount(totalCount)
        .setErrorCount(errorCount)
        .setFailureCount(failureCount)
        .setSkippedCount(skippedCount)
        .setFlakyCount(flakyCount)
        .build();
  }

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_TestSuiteOverview.Builder()
        .setTotalCount(0)
        .setErrorCount(0)
        .setFailureCount(0)
        .setSkippedCount(0)
        .setFlakyCount(0)
        .setElapsedTime(Duration.ZERO);
  }

  /** Builder for {@link TestSuiteOverview}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String name);

    public abstract Builder setElapsedTime(Duration elapsedTime);

    public abstract Builder setTotalCount(int totalCount);

    public abstract Builder setErrorCount(int errorCount);

    public abstract Builder setFailureCount(int failureCount);

    public abstract Builder setSkippedCount(int skippedCount);

    public abstract Builder setFlakyCount(int flakyCount);

    public abstract TestSuiteOverview build();
  }

  /**
   * Rolls up the elapsed time of identical test suite overviews.
   *
   * <p>This only rolls up elapsed time to the longest time among all test suites with the same
   * name.
   */
  public static ImmutableList<TestSuiteOverview> rollUp(
      List<TestSuiteOverview> testSuiteOverviews) {
    ImmutableListMultimap<String, TestSuiteOverview> groupedOverviews =
        Multimaps.index(testSuiteOverviews, TestSuiteOverview::name);

    ImmutableList.Builder<TestSuiteOverview> rolledUpOverviewsBuilder = ImmutableList.builder();
    for (String name : groupedOverviews.keySet()) {
      ImmutableList<TestSuiteOverview> overviews = groupedOverviews.get(name);
      Duration longestElapsed = getLongestElapsedTime(overviews);
      TestSuiteOverview rawOverview =
          builder().setName(name).setElapsedTime(longestElapsed).build();
      rolledUpOverviewsBuilder.add(rawOverview);
    }
    return rolledUpOverviewsBuilder.build();
  }

  private static Duration getLongestElapsedTime(List<TestSuiteOverview> testSuiteOverviews) {
    return testSuiteOverviews.stream()
        .map(TestSuiteOverview::elapsedTime)
        .max(Duration::compareTo)
        .orElse(Duration.ZERO);
  }
}
