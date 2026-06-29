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

import com.google.devtools.mobileharness.fe.v6.service.proto.host.DaemonServerInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerInfo.ActivityState;

/**
 * Shared availability rules for lab server lifecycle actions (Start/Restart/Stop).
 *
 * <p>Each predicate encodes the same target-activity + daemon condition used to decide button
 * visibility in {@code LabServerStart/Restart/StopButtonBuilder}, so that the {@code
 * PreflightLabServerLifecycle} handler stays aligned with the button visibility logic.
 */
public final class LabServerActionAvailabilities {

  private LabServerActionAvailabilities() {}

  /** Whether Start is doable given the current lab activity and daemon state. */
  public static boolean isStartAvailable(ActivityState activity, DaemonServerInfo.State daemon) {
    boolean targetActivity =
        activity == ActivityState.DRAINED
            || activity == ActivityState.STOPPED
            || activity == ActivityState.UNKNOWN;
    boolean daemonRunning = daemon == DaemonServerInfo.State.RUNNING;
    boolean daemonMissing = daemon == DaemonServerInfo.State.MISSING;
    return daemonMissing || (daemonRunning && targetActivity);
  }

  /** Whether Restart is doable given the current lab activity and daemon state. */
  public static boolean isRestartAvailable(ActivityState activity, DaemonServerInfo.State daemon) {
    return daemon == DaemonServerInfo.State.RUNNING && isRunningLikeActivity(activity);
  }

  /** Whether Stop is doable given the current lab activity and daemon state. */
  public static boolean isStopAvailable(ActivityState activity, DaemonServerInfo.State daemon) {
    return daemon == DaemonServerInfo.State.RUNNING && isRunningLikeActivity(activity);
  }

  private static boolean isRunningLikeActivity(ActivityState activity) {
    return activity == ActivityState.STARTED
        || activity == ActivityState.STARTED_BUT_DISCONNECTED
        || activity == ActivityState.ERROR;
  }
}
