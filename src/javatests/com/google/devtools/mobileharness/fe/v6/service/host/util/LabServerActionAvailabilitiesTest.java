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

import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerReleaseState;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerReleaseStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LifecycleActionType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LabServerActionAvailabilitiesTest {

  // Start target activities: DRAINED, STOPPED, UNKNOWN.

  @Test
  public void start_targetActivities_true() {
    for (LabServerReleaseState s :
        new LabServerReleaseState[] {
          LabServerReleaseState.LAB_SERVER_RELEASE_STATE_DRAINED,
          LabServerReleaseState.LAB_SERVER_RELEASE_STATE_STOPPED,
          LabServerReleaseState.LAB_SERVER_RELEASE_STATE_UNKNOWN
        }) {
      assertThat(
              LabServerActionAvailabilities.isTargetActivity(
                  LifecycleActionType.START, releaseStatus(s)))
          .isTrue();
    }
  }

  @Test
  public void start_nonTargetActivities_false() {
    for (LabServerReleaseState s :
        new LabServerReleaseState[] {
          LabServerReleaseState.LAB_SERVER_RELEASE_STATE_RUNNING,
          LabServerReleaseState.LAB_SERVER_RELEASE_STATE_ERROR
        }) {
      assertThat(
              LabServerActionAvailabilities.isTargetActivity(
                  LifecycleActionType.START, releaseStatus(s)))
          .isFalse();
    }
  }

  // Restart/Stop target activities: RUNNING, ERROR.

  @Test
  public void restartAndStop_targetActivities_true() {
    for (LabServerReleaseState s :
        new LabServerReleaseState[] {
          LabServerReleaseState.LAB_SERVER_RELEASE_STATE_RUNNING,
          LabServerReleaseState.LAB_SERVER_RELEASE_STATE_ERROR
        }) {
      assertThat(
              LabServerActionAvailabilities.isTargetActivity(
                  LifecycleActionType.RESTART, releaseStatus(s)))
          .isTrue();
      assertThat(
              LabServerActionAvailabilities.isTargetActivity(
                  LifecycleActionType.STOP, releaseStatus(s)))
          .isTrue();
    }
  }

  @Test
  public void restartAndStop_stopped_false() {
    assertThat(
            LabServerActionAvailabilities.isTargetActivity(
                LifecycleActionType.RESTART,
                releaseStatus(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_STOPPED)))
        .isFalse();
    assertThat(
            LabServerActionAvailabilities.isTargetActivity(
                LifecycleActionType.STOP,
                releaseStatus(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_STOPPED)))
        .isFalse();
  }

  private static LabServerReleaseStatus releaseStatus(LabServerReleaseState state) {
    return LabServerReleaseStatus.newBuilder().setState(state).build();
  }
}
