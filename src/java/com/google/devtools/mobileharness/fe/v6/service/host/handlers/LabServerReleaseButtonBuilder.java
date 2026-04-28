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
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureReadiness;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Utility class to build {@link ActionButtonState} for lab server release button. */
@Singleton
public class LabServerReleaseButtonBuilder {

  private final FeatureManagerFactory featureManagerFactory;
  private final FeatureReadiness featureReadiness;

  @Inject
  LabServerReleaseButtonBuilder(
      FeatureManagerFactory featureManagerFactory, FeatureReadiness featureReadiness) {
    this.featureManagerFactory = featureManagerFactory;
    this.featureReadiness = featureReadiness;
  }

  public ActionButtonState build(
      UniverseScope universe,
      Optional<LabInfo> labInfoOpt,
      Optional<String> labTypeOpt,
      boolean anyActionVisible,
      DaemonServerInfo.Status daemonStatus) {

    if (!featureManagerFactory.create(universe).isLabServerReleaseFeatureEnabled()) {
      return ActionButtonState.newBuilder().setVisible(false).build();
    }

    boolean daemonMissing = daemonStatus.getState() == DaemonServerInfo.State.MISSING;

    if (!anyActionVisible && !daemonMissing) {
      return ActionButtonState.newBuilder().setVisible(false).build();
    }

    boolean daemonRunning = daemonStatus.getState() == DaemonServerInfo.State.RUNNING;

    boolean isReady = featureReadiness.isLabServerReleaseReady();

    String tooltip =
        daemonRunning
            ? "Deploy a release or edit Pass Through Flags"
            : "Can not release because daemon server is missing";

    return ActionButtonState.newBuilder()
        .setVisible(true)
        .setEnabled(daemonRunning)
        .setIsReady(isReady)
        .setTooltip(tooltip)
        .build();
  }
}
