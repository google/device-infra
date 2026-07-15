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
import com.google.devtools.mobileharness.fe.v6.service.proto.common.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostConnectivityStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UiLabType;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureReadiness;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Utility class to build {@link ActionButtonState} for advanced operations entry button. */
@Singleton
public final class HostAdvancedOperationsButtonBuilder {

  private final FeatureReadiness featureReadiness;

  @Inject
  HostAdvancedOperationsButtonBuilder(FeatureReadiness featureReadiness) {
    this.featureReadiness = featureReadiness;
  }

  public ActionButtonState build(
      Optional<LabInfo> labInfoOpt,
      Optional<String> labTypeOpt,
      HostConnectivityStatus connectivityStatus) {
    boolean isFusion =
        HostTypes.determineUiLabTypes(labInfoOpt, labTypeOpt).contains(UiLabType.FUSION);
    boolean isReady = featureReadiness.isAdvancedOperationsReady();

    // Advanced operations run against a live lab server, so they are only enabled when the lab
    // server state shown in the lab server info card (its connectivity with the OmniLab master) is
    // RUNNING, i.e. OmniLab is receiving heartbeats from the host.
    boolean isRunning = connectivityStatus.getState() == HostConnectivityStatus.State.RUNNING;

    return ActionButtonState.newBuilder()
        .setVisible(isFusion)
        .setEnabled(isRunning)
        .setIsReady(isReady)
        .setTooltip(
            isRunning
                ? "Advanced operations and diagnostics for Fusion hosts"
                : "Advanced operations are only available when the lab server is Running.")
        .build();
  }
}
