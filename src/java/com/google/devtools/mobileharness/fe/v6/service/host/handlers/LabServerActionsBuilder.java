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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DaemonServerInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostConnectivityStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerActions;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerInfo;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Builder for {@link LabServerActions}. */
@Singleton
public class LabServerActionsBuilder {

  private final LabServerReleaseButtonBuilder labServerReleaseButtonBuilder;
  private final LabServerStartButtonBuilder labServerStartButtonBuilder;
  private final LabServerRestartButtonBuilder labServerRestartButtonBuilder;
  private final LabServerStopButtonBuilder labServerStopButtonBuilder;

  @Inject
  LabServerActionsBuilder(
      LabServerReleaseButtonBuilder labServerReleaseButtonBuilder,
      LabServerStartButtonBuilder labServerStartButtonBuilder,
      LabServerRestartButtonBuilder labServerRestartButtonBuilder,
      LabServerStopButtonBuilder labServerStopButtonBuilder) {
    this.labServerReleaseButtonBuilder = labServerReleaseButtonBuilder;
    this.labServerStartButtonBuilder = labServerStartButtonBuilder;
    this.labServerRestartButtonBuilder = labServerRestartButtonBuilder;
    this.labServerStopButtonBuilder = labServerStopButtonBuilder;
  }

  /** Builds {@link LabServerActions} based on universe and status. */
  public LabServerActions build(
      UniverseScope universe,
      Optional<LabInfo> labInfoOpt,
      Optional<String> labTypeOpt,
      LabServerInfo.Activity activity,
      HostConnectivityStatus connectivityStatus,
      DaemonServerInfo.Status daemonStatus) {

    ActionButtonState start =
        labServerStartButtonBuilder.build(
            universe, labInfoOpt, labTypeOpt, activity, connectivityStatus, daemonStatus);
    ActionButtonState restart =
        labServerRestartButtonBuilder.build(
            universe, labInfoOpt, labTypeOpt, activity, connectivityStatus, daemonStatus);
    ActionButtonState stop =
        labServerStopButtonBuilder.build(
            universe, labInfoOpt, labTypeOpt, activity, connectivityStatus, daemonStatus);

    boolean anyActionVisible = start.getVisible() || restart.getVisible() || stop.getVisible();

    ActionButtonState release =
        labServerReleaseButtonBuilder.build(
            universe, labInfoOpt, labTypeOpt, anyActionVisible, daemonStatus);

    return LabServerActions.newBuilder()
        .setRelease(release)
        .setStart(start)
        .setRestart(restart)
        .setStop(stop)
        .build();
  }
}
