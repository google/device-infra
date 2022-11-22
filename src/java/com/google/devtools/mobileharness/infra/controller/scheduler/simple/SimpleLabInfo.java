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

package com.google.devtools.mobileharness.infra.controller.scheduler.simple;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.lab.DeviceScheduleUnit;
import com.google.devtools.mobileharness.api.model.lab.LabScheduleUnit;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/** Lab data model for {@link SimpleScheduler} only. */
class SimpleLabInfo {

  /** Basic lab information. */
  private final LabScheduleUnit labUnit;

  /** {DeviceSerial, BasicDeviceInfo} mapping of the devices in this lab server. */
  private final ConcurrentHashMap<String, DeviceScheduleUnit> devices = new ConcurrentHashMap<>();

  SimpleLabInfo(LabScheduleUnit basicInfo) {
    this.labUnit = basicInfo;
  }

  /** Gets the lab information related to scheduling. */
  public LabScheduleUnit getScheduleUnit() {
    return labUnit;
  }

  /**
   * Adds/updates the device to the lab.
   *
   * @return the previous {@link DeviceScheduleUnit} associated with device serial, or <tt>null</tt>
   *     if there was no device with the given serial previously.
   */
  @Nullable
  public DeviceScheduleUnit upsertDevice(DeviceScheduleUnit device) {
    return devices.put(device.locator().id(), device);
  }

  /**
   * Removes the device from lab.
   *
   * @return the previous {@link DeviceScheduleUnit} associated with device serial, or <tt>null</tt>
   *     if there was no device with the given serial.
   */
  @Nullable
  public DeviceScheduleUnit removeDevice(String deviceSerial) {
    return devices.remove(deviceSerial);
  }

  /** Gets all the devices in this lab. */
  public ImmutableSet<DeviceScheduleUnit> getDevices() {
    return ImmutableSet.copyOf(devices.values());
  }

  /**
   * Gets the {@link DeviceScheduleUnit} associated with device serial, or <tt>null</tt> if there
   * was no device with the given serial.
   */
  @Nullable
  public DeviceScheduleUnit getDevice(String deviceSerial) {
    return devices.get(deviceSerial);
  }
}
