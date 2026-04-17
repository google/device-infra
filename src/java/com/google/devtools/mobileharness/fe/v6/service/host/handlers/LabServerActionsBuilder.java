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
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerActions;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Builder for {@link LabServerActions}. */
@Singleton
public class LabServerActionsBuilder {

  private final LabServerReleaseButtonBuilder labServerReleaseButtonBuilder;
  private final LabServerDeployButtonBuilder labServerDeployButtonBuilder;
  private final LabServerStartButtonBuilder labServerStartButtonBuilder;
  private final LabServerRestartButtonBuilder labServerRestartButtonBuilder;
  private final LabServerStopButtonBuilder labServerStopButtonBuilder;
  private final LabServerUpdatePassThroughFlagsButtonBuilder
      labServerUpdatePassThroughFlagsButtonBuilder;

  @Inject
  LabServerActionsBuilder(
      LabServerReleaseButtonBuilder labServerReleaseButtonBuilder,
      LabServerDeployButtonBuilder labServerDeployButtonBuilder,
      LabServerStartButtonBuilder labServerStartButtonBuilder,
      LabServerRestartButtonBuilder labServerRestartButtonBuilder,
      LabServerStopButtonBuilder labServerStopButtonBuilder,
      LabServerUpdatePassThroughFlagsButtonBuilder labServerUpdatePassThroughFlagsButtonBuilder) {
    this.labServerReleaseButtonBuilder = labServerReleaseButtonBuilder;
    this.labServerDeployButtonBuilder = labServerDeployButtonBuilder;
    this.labServerStartButtonBuilder = labServerStartButtonBuilder;
    this.labServerRestartButtonBuilder = labServerRestartButtonBuilder;
    this.labServerStopButtonBuilder = labServerStopButtonBuilder;
    this.labServerUpdatePassThroughFlagsButtonBuilder =
        labServerUpdatePassThroughFlagsButtonBuilder;
  }

  /** Builds {@link LabServerActions} based on universe. */
  public LabServerActions build(
      UniverseScope universe, Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return LabServerActions.newBuilder()
        .setRelease(labServerReleaseButtonBuilder.build(universe, labInfoOpt, labTypeOpt))
        .setDeploy(labServerDeployButtonBuilder.build(universe, labInfoOpt, labTypeOpt))
        .setStart(labServerStartButtonBuilder.build(universe))
        .setRestart(labServerRestartButtonBuilder.build(universe))
        .setStop(labServerStopButtonBuilder.build(universe))
        .setUpdatePassThroughFlags(labServerUpdatePassThroughFlagsButtonBuilder.build(universe))
        .build();
  }
}
