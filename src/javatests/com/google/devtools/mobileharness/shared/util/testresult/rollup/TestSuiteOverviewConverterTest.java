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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TestSuiteOverviewConverterTest {

  @Test
  public void toCompleteTestSuiteOverviews_emptyInputs_returnsEmptyList() {
    assertThat(
            TestSuiteOverviewConverter.toCompleteTestSuiteOverviews(
                ImmutableList.of(), ImmutableList.of()))
        .isEmpty();
  }

  @Test
  public void toCompleteTestSuiteOverviews_noTestCasesForSuite_discardsOverview() {
    TestSuiteOverview overview =
        TestSuiteOverview.builder()
            .setName("SuiteA")
            .setElapsedTime(Duration.ofSeconds(10))
            .build();
    TestCase testCase =
        TestCase.builder()
            .setTestCaseReference(TestCaseReference.create("testMethod", "FakeClass", "OtherSuite"))
            .setStatus(TestStatus.PASSED)
            .build();

    assertThat(
            TestSuiteOverviewConverter.toCompleteTestSuiteOverviews(
                ImmutableList.of(overview), ImmutableList.of(testCase)))
        .isEmpty();
  }

  @Test
  public void toCompleteTestSuiteOverviews_populatesCorrectStats() {
    TestSuiteOverview overview =
        TestSuiteOverview.builder()
            .setName("SuiteA")
            .setElapsedTime(Duration.ofSeconds(10))
            .build();

    TestCase testCase1 = createTestCase("SuiteA", TestStatus.PASSED);
    TestCase testCase2 = createTestCase("SuiteA", TestStatus.FAILED);
    TestCase testCase3 = createTestCase("SuiteA", TestStatus.ERROR);
    TestCase testCase4 = createTestCase("SuiteA", TestStatus.SKIPPED);
    TestCase testCase5 = createTestCase("SuiteA", TestStatus.FLAKY);

    ImmutableList<TestSuiteOverview> result =
        TestSuiteOverviewConverter.toCompleteTestSuiteOverviews(
            ImmutableList.of(overview),
            ImmutableList.of(testCase1, testCase2, testCase3, testCase4, testCase5));

    TestSuiteOverview expected =
        TestSuiteOverview.builder()
            .setName("SuiteA")
            .setElapsedTime(Duration.ofSeconds(10))
            .setTotalCount(5)
            .setErrorCount(1)
            .setFailureCount(1)
            .setSkippedCount(1)
            .setFlakyCount(1)
            .build();

    assertThat(result).containsExactly(expected);
  }

  private static TestCase createTestCase(String suiteName, TestStatus status) {
    return TestCase.builder()
        .setTestCaseReference(TestCaseReference.create("method", "Class", suiteName))
        .setStatus(status)
        .build();
  }
}
