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

package com.google.devtools.mobileharness.infra.client.api.mode.local;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.client.api.mode.local.LocalDeviceAllocator.DeviceVerifier;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceRunner;
import java.util.Optional;

/** A {@link DeviceVerifier} based on {@link LocalDeviceManager}. */
class LocalDeviceVerifier implements DeviceVerifier {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LocalDeviceManager localDeviceManager;

  LocalDeviceVerifier(LocalDeviceManager localDeviceManager) {
    this.localDeviceManager = localDeviceManager;
  }

  @Override
  public Optional<String> verifyDeviceForAllocation(String deviceId) {
    LocalDeviceRunner deviceRunner = localDeviceManager.getLocalDeviceRunner(deviceId);
    if (deviceRunner == null || !deviceRunner.isAvailable()) {
      return Optional.of(
          String.format(
              "Failed to reserve device %s because device is %s",
              deviceId, (deviceRunner == null ? "disconnected" : "busy")));
    }
    return Optional.empty();
  }

  @Override
  public Optional<Boolean> getDeviceDirtyForAllocationRelease(String deviceId)
      throws InterruptedException {
    LocalDeviceRunner deviceRunner = localDeviceManager.getLocalDeviceRunner(deviceId);
    if (deviceRunner == null) {
      logger.atInfo().log(
          "Device runner of device %s not found. Mark the device as DIRTY", deviceId);
      return Optional.of(true);
    } else if (!deviceRunner.getDevice().canReboot()) {
      logger.atInfo().log("Device %s not rebootable.", deviceId);
      return Optional.of(false);
    } else if (!deviceRunner.isAlive()) {
      logger.atInfo().log("Device %s is not alive.", deviceId);
      return Optional.of(true);
    }
    return Optional.empty();
  }
}
