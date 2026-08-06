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
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerReleaseState;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerReleaseStatus;
import java.util.Optional;

/** Utility class for creating {@link LabServerReleaseStatus}. */
public final class LabActivities {

  private LabActivities() {}

  /**
   * Creates the {@link LabServerReleaseStatus} based on the {@link HostReleaseInfo.ComponentInfo}.
   */
  public static LabServerReleaseStatus create(
      Optional<HostReleaseInfo.ComponentInfo> labReleaseOpt, boolean isCoreLab) {
    if (labReleaseOpt.isEmpty()) {
      if (isCoreLab) {
        return LabServerReleaseStatus.newBuilder()
            .setState(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_UNSPECIFIED)
            .setTitle("N/A")
            .setTooltip("Lab Server release status is not applicable for Core Labs.")
            .build();
      } else {
        return LabServerReleaseStatus.newBuilder()
            .setState(LabServerReleaseState.LAB_SERVER_RELEASE_STATE_UNKNOWN)
            .setTitle("Unknown")
            .setTooltip("Lab Server release status is unknown.")
            .build();
      }
    }
    String rawStatus = labReleaseOpt.get().status().orElse("UNKNOWN");
    LabServerReleaseStatus.Builder builder = LabServerReleaseStatus.newBuilder();
    LabServerReleaseState state = LabServerReleaseState.LAB_SERVER_RELEASE_STATE_UNKNOWN;
    String title = "Unknown";
    String tooltip = "The Lab Server release status is unknown.";

    switch (rawStatus) {
      case "STARTING" -> {
        state = LabServerReleaseState.LAB_SERVER_RELEASE_STATE_STARTING;
        title = "Starting";
        tooltip = "The release system is attempting to start the Lab Server process.";
      }
      case "RUNNING" -> {
        state = LabServerReleaseState.LAB_SERVER_RELEASE_STATE_RUNNING;
        title = "Running";
        tooltip = "The Lab Server process is running as reported by the release system.";
      }
      case "ERROR" -> {
        state = LabServerReleaseState.LAB_SERVER_RELEASE_STATE_ERROR;
        title = "Error";
        tooltip =
            "The release system encountered an error attempting to manage the Lab Server process"
                + " on this host.";
      }
      case "DRAINING" -> {
        state = LabServerReleaseState.LAB_SERVER_RELEASE_STATE_DRAINING;
        title = "Draining";
        tooltip =
            "The Lab Server is finishing its current tasks and will not accept new ones before"
                + " stopping.";
      }
      case "DRAINED" -> {
        state = LabServerReleaseState.LAB_SERVER_RELEASE_STATE_DRAINED;
        title = "Drained";
        tooltip = "The Lab Server has finished all tasks and is not accepting new ones.";
      }
      case "STOPPING" -> {
        state = LabServerReleaseState.LAB_SERVER_RELEASE_STATE_STOPPING;
        title = "Stopping";
        tooltip = "The release system is attempting to stop the Lab Server process.";
      }
      case "STOPPED" -> {
        state = LabServerReleaseState.LAB_SERVER_RELEASE_STATE_STOPPED;
        title = "Stopped";
        tooltip = "The Lab Server process is reported as stopped by the release system.";
      }
      default -> {
        // Defaults are already set
      }
    }
    return builder.setState(state).setTitle(title).setTooltip(tooltip).build();
  }
}
