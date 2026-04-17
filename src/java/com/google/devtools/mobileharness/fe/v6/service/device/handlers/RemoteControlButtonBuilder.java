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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.IneligibilityReasonCode;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceInfoUtil;
import com.google.devtools.mobileharness.fe.v6.service.shared.SubDeviceInfoListFactory;
import com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol.RemoteControlEligibilityChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol.RemoteControlEligibilityContext;
import com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol.RemoteControlEligibilityResult;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureReadiness;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Utility class to build {@link ActionButtonState} for remote control button. */
@Singleton
class RemoteControlButtonBuilder {

  private final RemoteControlEligibilityChecker checker;
  private final SubDeviceInfoListFactory subDeviceInfoListFactory;
  private final FeatureManagerFactory featureManagerFactory;
  private final FeatureReadiness featureReadiness;

  @Inject
  RemoteControlButtonBuilder(
      RemoteControlEligibilityChecker checker,
      SubDeviceInfoListFactory subDeviceInfoListFactory,
      FeatureManagerFactory featureManagerFactory,
      FeatureReadiness featureReadiness) {
    this.checker = checker;
    this.subDeviceInfoListFactory = subDeviceInfoListFactory;
    this.featureManagerFactory = featureManagerFactory;
    this.featureReadiness = featureReadiness;
  }

  public ActionButtonState build(DeviceInfo deviceInfo, UniverseScope universe) {
    if (!featureManagerFactory.create(universe).isDeviceRemoteControlFeatureEnabled()) {
      return ActionButtonState.newBuilder().setVisible(false).build();
    }

    ImmutableMap<String, String> dimensions = DeviceInfoUtil.getDimensions(deviceInfo);

    boolean hasCommSub = false;
    if (deviceInfo.getDeviceFeature().getTypeList().contains("TestbedDevice")) {
      hasCommSub =
          subDeviceInfoListFactory.create(dimensions).stream()
              .anyMatch(
                  sub ->
                      sub.getDimensionsList().stream()
                          .anyMatch(d -> d.getName().equals("communication_type")));
    }

    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setIsMultipleSelection(false)
            .setIsSubDevice(false)
            .setHasCommSubDevice(hasCommSub)
            .setDeviceStatus(deviceInfo.getDeviceStatus())
            .setDrivers(ImmutableSet.copyOf(deviceInfo.getDeviceFeature().getDriverList()))
            .setTypes(ImmutableSet.copyOf(deviceInfo.getDeviceFeature().getTypeList()))
            .setDimensions(dimensions)
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    // If the device is not idle, we still want to show the remote control button, but disable it.
    // Otherwise, the user will not be able to click the button and see the reason code.
    return ActionButtonState.newBuilder()
        .setVisible(
            result.isEligible()
                || result
                    .reasonCode()
                    .filter(IneligibilityReasonCode.DEVICE_NOT_IDLE::equals)
                    .isPresent())
        .setIsReady(featureReadiness.isDeviceRemoteControlReady())
        .setEnabled(result.isEligible())
        .setTooltip(result.reasonMessage().orElse(""))
        .build();
  }
}
