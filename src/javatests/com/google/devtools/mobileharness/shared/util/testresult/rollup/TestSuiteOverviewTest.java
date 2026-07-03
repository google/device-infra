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
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TestSuiteOverviewTest {

  @Test
  public void builder_fieldsAssertion() {
    TestSuiteOverview overview =
        TestSuiteOverview.builder()
            .setName("SuiteA")
            .setElapsedTime(Duration.ofSeconds(12))
            .setTotalCount(10)
            .setErrorCount(1)
            .setFailureCount(2)
            .setSkippedCount(3)
            .setFlakyCount(4)
            .build();

    assertThat(overview.name()).isEqualTo("SuiteA");
    assertThat(overview.elapsedTime()).isEqualTo(Duration.ofSeconds(12));
    assertThat(overview.totalCount()).isEqualTo(10);
    assertThat(overview.errorCount()).isEqualTo(1);
    assertThat(overview.failureCount()).isEqualTo(2);
    assertThat(overview.skippedCount()).isEqualTo(3);
    assertThat(overview.flakyCount()).isEqualTo(4);
  }

  @Test
  public void create_fieldsAssertion() {
    TestSuiteOverview overview =
        TestSuiteOverview.create(
            /* name= */ "SuiteB",
            /* elapsedTime= */ Duration.ofSeconds(5),
            /* totalCount= */ 5,
            /* errorCount= */ 0,
            /* failureCount= */ 1,
            /* skippedCount= */ 0,
            /* flakyCount= */ 0);

    assertThat(overview.name()).isEqualTo("SuiteB");
    assertThat(overview.elapsedTime()).isEqualTo(Duration.ofSeconds(5));
    assertThat(overview.totalCount()).isEqualTo(5);
    assertThat(overview.errorCount()).isEqualTo(0);
    assertThat(overview.failureCount()).isEqualTo(1);
    assertThat(overview.skippedCount()).isEqualTo(0);
    assertThat(overview.flakyCount()).isEqualTo(0);
  }

  @Test
  public void rollUp_emptyList_returnsEmptyList() {
    assertThat(TestSuiteOverview.rollUp(ImmutableList.of())).isEmpty();
  }

  @Test
  public void rollUp_handlesIndividualAndMaximumElapsedTime() {
    TestSuiteOverview overview1 =
        TestSuiteOverview.create(
            /* name= */ "SuiteA",
            /* elapsedTime= */ Duration.ofSeconds(5),
            /* totalCount= */ 10,
            /* errorCount= */ 0,
            /* failureCount= */ 0,
            /* skippedCount= */ 0,
            /* flakyCount= */ 0);
    TestSuiteOverview overview2 =
        TestSuiteOverview.create(
            /* name= */ "SuiteA",
            /* elapsedTime= */ Duration.ofSeconds(15),
            /* totalCount= */ 10,
            /* errorCount= */ 0,
            /* failureCount= */ 0,
            /* skippedCount= */ 0,
            /* flakyCount= */ 0);
    TestSuiteOverview overview3 =
        TestSuiteOverview.create(
            /* name= */ "SuiteB",
            /* elapsedTime= */ Duration.ofSeconds(20),
            /* totalCount= */ 5,
            /* errorCount= */ 0,
            /* failureCount= */ 0,
            /* skippedCount= */ 0,
            /* flakyCount= */ 0);

    ImmutableList<TestSuiteOverview> rolledUp =
        TestSuiteOverview.rollUp(ImmutableList.of(overview1, overview2, overview3));

    // Should create exactly one for SuiteA (with 15s) and one for SuiteB (with 20s).
    // Note: other fields (e.g. totalCount) are defaulted to 0 in rolled-up overview.
    TestSuiteOverview expectedRollupA =
        TestSuiteOverview.builder()
            .setName("SuiteA")
            .setElapsedTime(Duration.ofSeconds(15))
            .build();
    TestSuiteOverview expectedRollupB =
        TestSuiteOverview.builder()
            .setName("SuiteB")
            .setElapsedTime(Duration.ofSeconds(20))
            .build();

    assertThat(rolledUp).containsExactly(expectedRollupA, expectedRollupB);
  }
}
