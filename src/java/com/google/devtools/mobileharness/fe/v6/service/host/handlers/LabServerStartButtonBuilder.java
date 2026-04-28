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
import com.google.devtools.mobileharness.fe.v6.service.host.util.HostTypes;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DaemonServerInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostConnectivityStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.LabServerInfo;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureReadiness;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Utility class to build {@link ActionButtonState} for lab server start button. */
@Singleton
public class LabServerStartButtonBuilder {

  private final FeatureManagerFactory featureManagerFactory;
  private final FeatureReadiness featureReadiness;

  @Inject
  LabServerStartButtonBuilder(
      FeatureManagerFactory featureManagerFactory, FeatureReadiness featureReadiness) {
    this.featureManagerFactory = featureManagerFactory;
    this.featureReadiness = featureReadiness;
  }

  public ActionButtonState build(
      UniverseScope universe,
      Optional<LabInfo> labInfoOpt,
      Optional<String> labTypeOpt,
      LabServerInfo.Activity activity,
      HostConnectivityStatus connectivityStatus,
      DaemonServerInfo.Status daemonStatus) {

    // TODO: Refactor this logic into a shared util class when it is needed by 2 consumers (e.g.,
    // for the preflight request).
    if (!featureManagerFactory.create(universe).isLabServerStartFeatureEnabled()) {
      return ActionButtonState.newBuilder().setVisible(false).build();
    }

    boolean isFusionOrCore = HostTypes.isCoreOrFusion(labInfoOpt, labTypeOpt);

    if (isFusionOrCore) {
      return ActionButtonState.newBuilder().setVisible(false).build();
    }

    boolean isTargetActivityState =
        activity.getState() == LabServerInfo.ActivityState.DRAINED
            || activity.getState() == LabServerInfo.ActivityState.STOPPED
            || activity.getState() == LabServerInfo.ActivityState.UNKNOWN;

    boolean daemonRunning = daemonStatus.getState() == DaemonServerInfo.State.RUNNING;
    boolean daemonMissing = daemonStatus.getState() == DaemonServerInfo.State.MISSING;

    boolean visibleCondition = daemonMissing || (daemonRunning && isTargetActivityState);

    if (!visibleCondition) {
      return ActionButtonState.newBuilder().setVisible(false).build();
    }

    boolean isReady = featureReadiness.isLabServerStartReady();

    String tooltip =
        daemonRunning
            ? "Start the lab server by deploying its configured software version and Pass Through"
                + " Flags."
            : "Can not start because daemon server is missing";

    return ActionButtonState.newBuilder()
        .setVisible(true)
        .setEnabled(daemonRunning)
        .setIsReady(isReady)
        .setTooltip(tooltip)
        .build();
  }
}
