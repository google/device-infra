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

/** Result type consisting of test results. */
@AutoValue
public abstract class TestResult {
  public abstract ImmutableList<TestCase> testCases();

  public abstract ImmutableList<TestSuiteOverview> testSuiteOverviews();

  public abstract Outcome outcome();

  public abstract State state();

  public static TestResult create(
      ImmutableList<TestCase> testCases,
      ImmutableList<TestSuiteOverview> testSuiteOverviews,
      Outcome outcome,
      State state) {
    return new AutoValue_TestResult(
        testCases,
        TestSuiteOverviewConverter.toCompleteTestSuiteOverviews(testSuiteOverviews, testCases),
        outcome,
        state);
  }
}
