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

package com.google.devtools.mobileharness.fe.v6.service.host.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerInfo.Activity;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerInfo.ActivityState;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LifecycleActionType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LabServerActionAvailabilitiesTest {

  // Start target activities: DRAINED, STOPPED, UNKNOWN.

  @Test
  public void start_targetActivities_true() {
    for (ActivityState s :
        new ActivityState[] {ActivityState.DRAINED, ActivityState.STOPPED, ActivityState.UNKNOWN}) {
      assertThat(
              LabServerActionAvailabilities.isTargetActivity(
                  LifecycleActionType.START, activity(s)))
          .isTrue();
    }
  }

  @Test
  public void start_nonTargetActivities_false() {
    for (ActivityState s :
        new ActivityState[] {
          ActivityState.STARTED, ActivityState.STARTED_BUT_DISCONNECTED, ActivityState.ERROR
        }) {
      assertThat(
              LabServerActionAvailabilities.isTargetActivity(
                  LifecycleActionType.START, activity(s)))
          .isFalse();
    }
  }

  // Restart/Stop target activities: STARTED, STARTED_BUT_DISCONNECTED, ERROR.

  @Test
  public void restartAndStop_targetActivities_true() {
    for (ActivityState s :
        new ActivityState[] {
          ActivityState.STARTED, ActivityState.STARTED_BUT_DISCONNECTED, ActivityState.ERROR
        }) {
      assertThat(
              LabServerActionAvailabilities.isTargetActivity(
                  LifecycleActionType.RESTART, activity(s)))
          .isTrue();
      assertThat(
              LabServerActionAvailabilities.isTargetActivity(LifecycleActionType.STOP, activity(s)))
          .isTrue();
    }
  }

  @Test
  public void restartAndStop_stopped_false() {
    assertThat(
            LabServerActionAvailabilities.isTargetActivity(
                LifecycleActionType.RESTART, activity(ActivityState.STOPPED)))
        .isFalse();
    assertThat(
            LabServerActionAvailabilities.isTargetActivity(
                LifecycleActionType.STOP, activity(ActivityState.STOPPED)))
        .isFalse();
  }

  private static Activity activity(ActivityState state) {
    return Activity.newBuilder().setState(state).build();
  }
}
