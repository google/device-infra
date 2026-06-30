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
import com.google.devtools.mobileharness.shared.util.testresult.TestSuiteOverview;
import java.time.Duration;
import java.util.List;

/** Helper utility class that rolls up test suite overviews. */
public final class TestSuiteOverviewRollupHelper {

  private TestSuiteOverviewRollupHelper() {}

  /**
   * Rolls up the elapsed time of identical test suite overviews from different runs.
   *
   * <p>This only rolls up elapsed time to the longest time among all test suites.
   */
  public static ImmutableList<TestSuiteOverview> rollupTestSuiteOverviewsElapsedTime(
      List<TestResult> runs) {
    ImmutableList<TestSuiteOverview> allOverviews =
        runs.stream().flatMap(r -> r.testSuiteOverviews().stream()).collect(toImmutableList());
    ImmutableListMultimap<String, TestSuiteOverview> groupedOverviews =
        Multimaps.index(allOverviews, TestSuiteOverview::name);

    ImmutableList.Builder<TestSuiteOverview> rolledUpOverviewsBuilder = ImmutableList.builder();
    for (String name : groupedOverviews.keySet()) {
      ImmutableList<TestSuiteOverview> overviews = groupedOverviews.get(name);
      Duration longestElapsed = getLongestElapsedTime(overviews);
      TestSuiteOverview rawOverview =
          TestSuiteOverview.builder().setName(name).setElapsedTime(longestElapsed).build();
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
