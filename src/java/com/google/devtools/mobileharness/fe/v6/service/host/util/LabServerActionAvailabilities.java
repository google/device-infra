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

import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerReleaseState;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerReleaseStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LifecycleActionType;

/**
 * Shared target-activity check for lab server lifecycle actions (Start/Restart/Stop).
 *
 * <p>Returns whether the current lab server release status is one in which the given action makes
 * sense. This is the release-status-based portion of the button visibility logic in {@code
 * LabServerStart/Restart/StopButtonBuilder}; those builders combine it with their own daemon-state
 * rules to compute the final visible/enabled state. The {@code PreflightLabServerLifecycle} handler
 * gates purely on this check (the daemon-missing case already disables the button in the UI, so a
 * preflight is never triggered for it).
 */
public final class LabServerActionAvailabilities {

  private LabServerActionAvailabilities() {}

  /** Whether the current release status is a valid target for the given lifecycle action. */
  public static boolean isTargetActivity(
      LifecycleActionType action, LabServerReleaseStatus releaseStatus) {
    LabServerReleaseState state = releaseStatus.getState();
    return switch (action) {
      case START ->
          state == LabServerReleaseState.LAB_SERVER_RELEASE_STATE_DRAINED
              || state == LabServerReleaseState.LAB_SERVER_RELEASE_STATE_STOPPED
              || state == LabServerReleaseState.LAB_SERVER_RELEASE_STATE_UNKNOWN;
      case RESTART, STOP ->
          state == LabServerReleaseState.LAB_SERVER_RELEASE_STATE_RUNNING
              || state == LabServerReleaseState.LAB_SERVER_RELEASE_STATE_ERROR;
      default -> false;
    };
  }
}
