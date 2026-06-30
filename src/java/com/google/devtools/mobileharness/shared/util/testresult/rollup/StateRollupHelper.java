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
import com.google.devtools.mobileharness.shared.util.testresult.State;
import java.util.List;

/** Helper utility class that rolls up states. */
public final class StateRollupHelper {

  public static final Ordering<State> WORST_STATE_FIRST_ORDERING =
      Ordering.explicit(State.PENDING, State.IN_PROGRESS, State.COMPLETE);

  private StateRollupHelper() {}

  /** Rolls up multiple states from different runs into a single state. */
  public static State rollUpShardStatesFromDifferentRuns(List<State> states) {
    return states.stream().min(WORST_STATE_FIRST_ORDERING).orElse(State.PENDING);
  }
}
