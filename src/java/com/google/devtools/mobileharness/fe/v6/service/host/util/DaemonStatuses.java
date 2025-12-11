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
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DaemonServerInfo;
import java.util.Optional;

/** Utility class for creating {@link DaemonServerInfo.Status}. */
public final class DaemonStatuses {

  private DaemonStatuses() {}

  /**
   * Creates the {@link DaemonServerInfo.Status} based on the {@link HostReleaseInfo.ComponentInfo}.
   */
  public static DaemonServerInfo.Status create(
      Optional<HostReleaseInfo.ComponentInfo> daemonReleaseOpt) {
    if (daemonReleaseOpt.isPresent()) {
      return DaemonServerInfo.Status.newBuilder()
          .setState(DaemonServerInfo.State.RUNNING)
          .setTitle("Running")
          .setTooltip("The Daemon Server is running.")
          .build();
    } else {
      return DaemonServerInfo.Status.newBuilder()
          .setState(DaemonServerInfo.State.MISSING)
          .setTitle("Missing")
          .setTooltip("The Daemon Server is missing.")
          .build();
    }
  }
}
