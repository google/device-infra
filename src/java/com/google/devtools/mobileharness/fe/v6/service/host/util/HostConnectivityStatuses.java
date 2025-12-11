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

import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostConnectivityStatus;
import java.util.Optional;

/** Utility class for creating {@link HostConnectivityStatus}. */
public final class HostConnectivityStatuses {

  private HostConnectivityStatuses() {}

  /** Creates the {@link HostConnectivityStatus} based on the {@link LabInfo}. */
  public static HostConnectivityStatus create(Optional<LabInfo> labInfoOpt) {
    if (labInfoOpt.isEmpty()) {
      return HostConnectivityStatus.newBuilder()
          .setState(HostConnectivityStatus.State.UNKNOWN)
          .setTitle("Unknown")
          .setTooltip("The host's connectivity status is unknown.")
          .build();
    }

    LabStatus labStatus = labInfoOpt.get().getLabStatus();
    return switch (labStatus) {
      case LAB_RUNNING ->
          HostConnectivityStatus.newBuilder()
              .setState(HostConnectivityStatus.State.RUNNING)
              .setTitle("Running")
              .setTooltip("Host is running and connected. OmniLab is receiving heartbeats.")
              .build();
      case LAB_MISSING ->
          HostConnectivityStatus.newBuilder()
              .setState(HostConnectivityStatus.State.MISSING)
              .setTitle("Missing")
              .setTooltip(
                  "Heartbeat is missing. OmniLab has not received a heartbeat from this host.")
              .build();
      default ->
          HostConnectivityStatus.newBuilder()
              .setState(HostConnectivityStatus.State.UNKNOWN)
              .setTitle("Unknown")
              .setTooltip("The host's connectivity status is unknown.")
              .build();
    };
  }
}
