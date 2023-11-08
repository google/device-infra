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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceScheduleUnit;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.lab.LabScheduleUnit;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceRunner;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalDeviceChangeEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalDeviceDownEvent;

/**
 * Event handler to sync the local device/test change between {@link LocalDeviceManager} and local
 * {@link AbstractScheduler}.
 */
class LocalDeviceManagerSchedulerSyncer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final LabScheduleUnit LOCAL_LAB_UNIT = new LabScheduleUnit(LabLocator.LOCALHOST);
  private final LocalDeviceManager deviceManager;
  private final AbstractScheduler scheduler;
  private final ApiConfig apiConfig;

  public LocalDeviceManagerSchedulerSyncer(
      LocalDeviceManager localDeviceManager, AbstractScheduler localScheduler) {
    this(localDeviceManager, localScheduler, ApiConfig.getInstance());
  }

  @VisibleForTesting
  LocalDeviceManagerSchedulerSyncer(
      LocalDeviceManager localDeviceManager,
      AbstractScheduler localScheduler,
      ApiConfig apiConfig) {
    this.deviceManager = localDeviceManager;
    this.scheduler = localScheduler;
    this.apiConfig = apiConfig;
  }

  @Subscribe
  public void onDeviceChanged(LocalDeviceChangeEvent event) throws InterruptedException {
    String deviceId = event.getDeviceControlId();
    String deviceType = event.getDeviceType();
    LocalDeviceRunner deviceRunner = deviceManager.getLocalDeviceRunner(deviceId, deviceType);
    DeviceStatus deviceStatus = deviceRunner.getDeviceStatus();

    if (deviceStatus == DeviceStatus.IDLE) {
      logger.atInfo().log("Update device %s(%s) to scheduler", deviceId, deviceType);
      scheduler.upsertDevice(toDeviceScheduleUnit(deviceRunner.getDevice()), LOCAL_LAB_UNIT);
    } else if (!deviceRunner.isReady()) {
      // Removes the device from Scheduler and closes the test on the device if any.
      logger.atInfo().log(
          "Remove device %s(%s) from scheduler because it is not ready", deviceId, deviceType);
      scheduler.unallocate(DeviceLocator.of(deviceId, LabLocator.LOCALHOST), true, true);
    } else {
      logger.atInfo().log(
          "Skip updating device %s(%s) to scheduler because it is %s",
          deviceId, deviceType, deviceStatus.name());
    }
  }

  @Subscribe
  public void onDeviceDown(LocalDeviceDownEvent event) throws InterruptedException {
    String deviceId = event.getDeviceControlId();
    logger.atInfo().log("Device %s is down, remove it from scheduler", deviceId);
    scheduler.unallocate(DeviceLocator.of(deviceId, LabLocator.LOCALHOST), true, true);
  }

  /** Converts the device data model to a scheduler compatible one. */
  private DeviceScheduleUnit toDeviceScheduleUnit(Device device) {
    String deviceId = device.getDeviceId();
    DeviceScheduleUnit deviceUnit =
        new DeviceScheduleUnit(DeviceLocator.of(deviceId, LabLocator.LOCALHOST));
    deviceUnit.types().addAll(device.getDeviceTypes());
    deviceUnit.drivers().addAll(device.getDriverTypes());
    deviceUnit.decorators().addAll(device.getDecoratorTypes());
    // TODO: Also copy the device required dimensions(go/mh-required-dimensions).
    deviceUnit.dimensions().supported().addAll(device.getDimensions());
    deviceUnit.owners().addAll(apiConfig.getOwners(deviceId));
    return deviceUnit;
  }
}
