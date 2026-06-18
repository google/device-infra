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

import com.google.devtools.mobileharness.fe.v6.service.proto.host.DaemonServerInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerInfo.ActivityState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LabServerActionAvailabilitiesTest {

  // ---- Start: available when (DRAINED|STOPPED|UNKNOWN) and daemon running, or daemon missing ----

  @Test
  public void start_daemonRunning_targetActivities_available() {
    for (ActivityState s :
        new ActivityState[] {ActivityState.DRAINED, ActivityState.STOPPED, ActivityState.UNKNOWN}) {
      assertThat(LabServerActionAvailabilities.isStartAvailable(s, DaemonServerInfo.State.RUNNING))
          .isTrue();
    }
  }

  @Test
  public void start_daemonRunning_nonTargetActivities_unavailable() {
    for (ActivityState s :
        new ActivityState[] {
          ActivityState.STARTED, ActivityState.STARTED_BUT_DISCONNECTED, ActivityState.ERROR
        }) {
      assertThat(LabServerActionAvailabilities.isStartAvailable(s, DaemonServerInfo.State.RUNNING))
          .isFalse();
    }
  }

  @Test
  public void start_daemonMissing_alwaysAvailable() {
    assertThat(
            LabServerActionAvailabilities.isStartAvailable(
                ActivityState.STARTED, DaemonServerInfo.State.MISSING))
        .isTrue();
  }

  // ---- Restart/Stop: available when (STARTED|STARTED_BUT_DISCONNECTED|ERROR) and daemon running

  @Test
  public void restartAndStop_daemonRunning_runningLikeActivities_available() {
    for (ActivityState s :
        new ActivityState[] {
          ActivityState.STARTED, ActivityState.STARTED_BUT_DISCONNECTED, ActivityState.ERROR
        }) {
      assertThat(
              LabServerActionAvailabilities.isRestartAvailable(s, DaemonServerInfo.State.RUNNING))
          .isTrue();
      assertThat(LabServerActionAvailabilities.isStopAvailable(s, DaemonServerInfo.State.RUNNING))
          .isTrue();
    }
  }

  @Test
  public void restartAndStop_stopped_unavailable() {
    assertThat(
            LabServerActionAvailabilities.isRestartAvailable(
                ActivityState.STOPPED, DaemonServerInfo.State.RUNNING))
        .isFalse();
    assertThat(
            LabServerActionAvailabilities.isStopAvailable(
                ActivityState.STOPPED, DaemonServerInfo.State.RUNNING))
        .isFalse();
  }

  @Test
  public void restartAndStop_daemonMissing_unavailable() {
    assertThat(
            LabServerActionAvailabilities.isRestartAvailable(
                ActivityState.STARTED, DaemonServerInfo.State.MISSING))
        .isFalse();
    assertThat(
            LabServerActionAvailabilities.isStopAvailable(
                ActivityState.STARTED, DaemonServerInfo.State.MISSING))
        .isFalse();
  }
}
