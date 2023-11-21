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

package com.google.devtools.mobileharness.infra.lab.controller.handler;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.daemon.health.handler.DrainHandler;
import com.google.inject.Inject;

/** Drain handler for LocalDeviceManager. */
public class DeviceManagerDrainHandler implements DrainHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final LocalDeviceManager deviceManager;

  @Inject
  public DeviceManagerDrainHandler(LocalDeviceManager deviceManager) {
    this.deviceManager = deviceManager;
  }

  @Override
  public boolean hasDrained() {
    logger.atInfo().log("Check if LocalDeviceManager has drained.");
    boolean hasDrained = false;
    try {
      // Note here we only check if device is not busy.
      hasDrained =
          deviceManager.getAllDeviceStatus(false).entrySet().stream()
              .noneMatch(
                  x ->
                      (x.getValue().getDeviceStatusWithTimestamp().getStatus()
                          == DeviceStatus.BUSY));
    } catch (InterruptedException ie) {
      logger.atInfo().log("Ignored interrupted exception when check device status.");
    }
    logger.atInfo().log("LocalDeviceManager has drained: %s", hasDrained);
    return hasDrained;
  }

  @Override
  public void drain() {
    logger.atInfo().log("LocalDeviceManager Drain Started!");
    deviceManager.enableDrainingMode();
    return;
  }
}
