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

import com.google.common.collect.Ordering;
import com.google.devtools.mobileharness.shared.util.testresult.Outcome;
import com.google.devtools.mobileharness.shared.util.testresult.OutcomeSummary;
import java.util.List;

/** Helper utility class that rolls up outcomes. */
public final class OutcomeRollupHelper {

  public static final Ordering<OutcomeSummary> WORST_OUTCOME_SUMMARY_FIRST_ORDERING =
      Ordering.explicit(
          OutcomeSummary.FAILURE,
          OutcomeSummary.INCONCLUSIVE,
          OutcomeSummary.FLAKY,
          OutcomeSummary.SUCCESS,
          OutcomeSummary.SKIPPED);

  private OutcomeRollupHelper() {}

  /** Rolls up multiple outcomes to a single outcome using the worst outcome first strategy. */
  public static Outcome rollUpOutcomes(List<Outcome> outcomes) {
    if (outcomes.isEmpty()) {
      return Outcome.create(OutcomeSummary.INCONCLUSIVE);
    }

    OutcomeSummary worstSummary =
        outcomes.stream()
            .map(Outcome::summary)
            .min(WORST_OUTCOME_SUMMARY_FIRST_ORDERING)
            .orElse(OutcomeSummary.INCONCLUSIVE);

    return Outcome.create(worstSummary);
  }
}
