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
import com.google.devtools.mobileharness.shared.util.testresult.rollup.Outcome.OutcomeSummary;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class OutcomeTest {

  @Test
  public void create_success() {
    Outcome outcome = Outcome.create(OutcomeSummary.SUCCESS);
    assertThat(outcome.summary()).isEqualTo(OutcomeSummary.SUCCESS);
  }

  @Test
  public void builder_success() {
    Outcome outcome = Outcome.builder().setSummary(OutcomeSummary.FAILURE).build();
    assertThat(outcome.summary()).isEqualTo(OutcomeSummary.FAILURE);
  }

  @Test
  public void rollUp_emptyList_returnsInconclusive() {
    Outcome rolledUp = Outcome.rollUp(ImmutableList.of());
    assertThat(rolledUp.summary()).isEqualTo(OutcomeSummary.INCONCLUSIVE);
  }

  @Test
  public void rollUp_singleOutcome_returnsSameOutcome() {
    Outcome rolledUp = Outcome.rollUp(ImmutableList.of(Outcome.create(OutcomeSummary.FLAKY)));
    assertThat(rolledUp.summary()).isEqualTo(OutcomeSummary.FLAKY);
  }

  @Test
  public void rollUp_multipleOutcomesWithFailure_returnsFailure() {
    Outcome rolledUp =
        Outcome.rollUp(
            ImmutableList.of(
                Outcome.create(OutcomeSummary.INCONCLUSIVE),
                Outcome.create(OutcomeSummary.FAILURE),
                Outcome.create(OutcomeSummary.SUCCESS)));
    assertThat(rolledUp.summary()).isEqualTo(OutcomeSummary.FAILURE);
  }

  @Test
  public void rollUp_multipleOutcomesWithInconclusive_returnsInconclusive() {
    Outcome rolledUp =
        Outcome.rollUp(
            ImmutableList.of(
                Outcome.create(OutcomeSummary.SUCCESS),
                Outcome.create(OutcomeSummary.FLAKY),
                Outcome.create(OutcomeSummary.INCONCLUSIVE)));
    assertThat(rolledUp.summary()).isEqualTo(OutcomeSummary.INCONCLUSIVE);
  }

  @Test
  public void rollUp_multipleOutcomesWithFlaky_returnsFlaky() {
    Outcome rolledUp =
        Outcome.rollUp(
            ImmutableList.of(
                Outcome.create(OutcomeSummary.SUCCESS),
                Outcome.create(OutcomeSummary.SKIPPED),
                Outcome.create(OutcomeSummary.FLAKY)));
    assertThat(rolledUp.summary()).isEqualTo(OutcomeSummary.FLAKY);
  }

  @Test
  public void rollUp_multipleOutcomesWithSuccessAndSkipped_returnsSuccess() {
    Outcome rolledUp =
        Outcome.rollUp(
            ImmutableList.of(
                Outcome.create(OutcomeSummary.SKIPPED), Outcome.create(OutcomeSummary.SUCCESS)));
    assertThat(rolledUp.summary()).isEqualTo(OutcomeSummary.SUCCESS);
  }
}
