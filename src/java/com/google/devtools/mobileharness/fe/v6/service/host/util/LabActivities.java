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

import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostReleaseInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostConnectivityStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerInfo.Activity;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerInfo.ActivityState;
import java.util.Optional;

/** Utility class for creating {@link Activity}. */
public final class LabActivities {

  private LabActivities() {}

  /**
   * Creates the {@link Activity} based on the {@link HostReleaseInfo.ComponentInfo} and {@link
   * HostConnectivityStatus}.
   */
  public static Activity create(
      Optional<HostReleaseInfo.ComponentInfo> labReleaseOpt,
      HostConnectivityStatus connectivityStatus) {
    if (labReleaseOpt.isEmpty()) {
      return Activity.newBuilder()
          .setState(ActivityState.ACTIVITY_STATE_UNSPECIFIED)
          .setTitle("N/A")
          .setTooltip("Lab Server activity is not applicable for this host type (e.g., Core Labs).")
          .build();
    }
    String rawStatus = labReleaseOpt.get().status().orElse("UNKNOWN");
    Activity.Builder builder = Activity.newBuilder();
    ActivityState activityState = ActivityState.UNKNOWN;
    String title = "Unknown";
    String tooltip = "The Lab Server activity state is unknown.";

    switch (rawStatus) {
      case "STARTING" -> {
        activityState = ActivityState.STARTING;
        title = "Starting";
        tooltip = "The release system is attempting to start the Lab Server process.";
      }
      case "RUNNING" -> {
        if (connectivityStatus.getState() == HostConnectivityStatus.State.MISSING) {
          activityState = ActivityState.STARTED_BUT_DISCONNECTED;
          title = "Started (but disconnected)";
          tooltip =
              "The Lab Server process was started by the release system, but OmniLab is NOT"
                  + " receiving heartbeats from this host.";
        } else {
          activityState = ActivityState.STARTED;
          title = "Started";
          tooltip =
              "The Lab Server process was started by the release system, and OmniLab is receiving"
                  + " heartbeats.";
        }
      }
      case "ERROR" -> {
        activityState = ActivityState.ERROR;
        title = "Error";
        tooltip =
            "The release system encountered an error attempting to manage the Lab Server process"
                + " on this host.";
      }
      case "DRAINING" -> {
        activityState = ActivityState.DRAINING;
        title = "Draining";
        tooltip =
            "The Lab Server is finishing its current tasks and will not accept new ones before"
                + " stopping.";
      }
      case "DRAINED" -> {
        activityState = ActivityState.DRAINED;
        title = "Drained";
        tooltip = "The Lab Server has finished all tasks and is not accepting new ones.";
      }
      case "STOPPING" -> {
        activityState = ActivityState.STOPPING;
        title = "Stopping";
        tooltip = "The release system is attempting to stop the Lab Server process.";
      }
      case "STOPPED" -> {
        activityState = ActivityState.STOPPED;
        title = "Stopped";
        tooltip = "The Lab Server process is reported as stopped by the release system.";
      }
      default -> {
        // Defaults are already set
      }
    }
    return builder.setState(activityState).setTitle(title).setTooltip(tooltip).build();
  }
}
