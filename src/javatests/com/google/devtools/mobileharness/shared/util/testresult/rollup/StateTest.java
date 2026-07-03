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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StateTest {

  @Test
  public void rollUp_emptyList_returnsPending() {
    State rolledUp = State.rollUp(ImmutableList.of());
    assertThat(rolledUp).isEqualTo(State.PENDING);
  }

  @Test
  public void rollUp_singleState_returnsSameState() {
    assertThat(State.rollUp(ImmutableList.of(State.PENDING))).isEqualTo(State.PENDING);
    assertThat(State.rollUp(ImmutableList.of(State.IN_PROGRESS))).isEqualTo(State.IN_PROGRESS);
    assertThat(State.rollUp(ImmutableList.of(State.COMPLETE))).isEqualTo(State.COMPLETE);
  }

  @Test
  public void rollUp_pendingAndInProgress_returnsPending() {
    State rolledUp = State.rollUp(ImmutableList.of(State.PENDING, State.IN_PROGRESS));
    assertThat(rolledUp).isEqualTo(State.PENDING);
  }

  @Test
  public void rollUp_inProgressAndComplete_returnsInProgress() {
    State rolledUp = State.rollUp(ImmutableList.of(State.IN_PROGRESS, State.COMPLETE));
    assertThat(rolledUp).isEqualTo(State.IN_PROGRESS);
  }

  @Test
  public void rollUp_pendingAndComplete_returnsPending() {
    State rolledUp = State.rollUp(ImmutableList.of(State.PENDING, State.COMPLETE));
    assertThat(rolledUp).isEqualTo(State.PENDING);
  }

  @Test
  public void rollUp_allComplete_returnsComplete() {
    State rolledUp = State.rollUp(ImmutableList.of(State.COMPLETE, State.COMPLETE));
    assertThat(rolledUp).isEqualTo(State.COMPLETE);
  }
}
