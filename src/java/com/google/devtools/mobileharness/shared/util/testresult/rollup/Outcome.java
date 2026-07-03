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
import com.google.common.collect.Ordering;
import java.util.List;

/** Data class representing a test execution outcome. */
@AutoValue
public abstract class Outcome {

  /** Outcome summary values representing test execution outcome categories. */
  public enum OutcomeSummary {
    SUCCESS,
    FAILURE,
    INCONCLUSIVE,
    SKIPPED,
    FLAKY
  }

  public abstract OutcomeSummary summary();

  public static Outcome create(OutcomeSummary summary) {
    return builder().setSummary(summary).build();
  }

  public static Builder builder() {
    return new AutoValue_Outcome.Builder();
  }

  /** Builder for {@link Outcome}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setSummary(OutcomeSummary summary);

    public abstract Outcome build();
  }

  private static final Ordering<OutcomeSummary> WORST_OUTCOME_SUMMARY_FIRST_ORDERING =
      Ordering.explicit(
          OutcomeSummary.FAILURE,
          OutcomeSummary.INCONCLUSIVE,
          OutcomeSummary.FLAKY,
          OutcomeSummary.SUCCESS,
          OutcomeSummary.SKIPPED);

  /** Rolls up multiple outcomes into a single outcome using the worst outcome first strategy. */
  public static Outcome rollUp(List<Outcome> outcomes) {
    if (outcomes.isEmpty()) {
      return create(OutcomeSummary.INCONCLUSIVE);
    }
    OutcomeSummary worstSummary =
        outcomes.stream()
            .map(Outcome::summary)
            .min(WORST_OUTCOME_SUMMARY_FIRST_ORDERING)
            .orElse(OutcomeSummary.INCONCLUSIVE);
    return create(worstSummary);
  }
}
