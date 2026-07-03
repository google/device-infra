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
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;

/** Helper converter class to build full {@link TestSuiteOverview} from its components. */
public final class TestSuiteOverviewConverter {

  private TestSuiteOverviewConverter() {}

  /**
   * Combines rolled up {@link TestSuiteOverview}s and rolled up {@link TestCase}s to return a
   * complete, rolled up test suite overviews with updated status counts.
   *
   * <p>{@link TestSuiteOverview} in {@code rolledUpTestSuiteOverviews} will be discarded if it's
   * not present in {@code rolledUpTestCases}.
   */
  public static ImmutableList<TestSuiteOverview> toCompleteTestSuiteOverviews(
      ImmutableList<TestSuiteOverview> rolledUpTestSuiteOverviews,
      ImmutableList<TestCase> rolledUpTestCases) {
    ImmutableListMultimap<String, TestCase> testSuiteNameToTestCases =
        Multimaps.index(
            rolledUpTestCases, testCase -> testCase.testCaseReference().testSuiteName());
    ImmutableList.Builder<TestSuiteOverview> testSuiteOverviewsBuilder = ImmutableList.builder();
    for (TestSuiteOverview testSuiteOverview : rolledUpTestSuiteOverviews) {
      String suiteName = testSuiteOverview.name();
      if (testSuiteNameToTestCases.containsKey(suiteName)) {
        ImmutableList<TestCase> cases = testSuiteNameToTestCases.get(suiteName);
        int total = cases.size();
        int error = 0;
        int failure = 0;
        int skipped = 0;
        int flaky = 0;
        for (TestCase tc : cases) {
          switch (tc.status()) {
            case ERROR -> error++;
            case FAILED -> failure++;
            case SKIPPED -> skipped++;
            case FLAKY -> flaky++;
            default -> {}
          }
        }
        testSuiteOverviewsBuilder.add(
            testSuiteOverview.toBuilder()
                .setTotalCount(total)
                .setErrorCount(error)
                .setFailureCount(failure)
                .setSkippedCount(skipped)
                .setFlakyCount(flaky)
                .build());
      }
    }
    return testSuiteOverviewsBuilder.build();
  }
}
