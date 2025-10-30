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

package com.google.devtools.mobileharness.infra.master.central.model.lab;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.master.central.proto.Lab.LabServerCondition;
import com.google.devtools.mobileharness.infra.master.central.proto.Lab.LabServerProfile;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Data access object of a lab. */
@AutoValue
public abstract class LabDao {
  /** {DeviceId, DeviceDao} mapping of the devices in this lab. */
  private final Map<String, DeviceDao> devices = new HashMap<>();

  private final AtomicReference<Boolean> isSharedPool = new AtomicReference<>(null);

  public static LabDao create(
      LabLocator locator,
      Optional<LabServerProfile> labServerProfile,
      Optional<LabServerCondition> labServerCondition) {
    return new AutoValue_LabDao(locator, labServerProfile, labServerCondition);
  }

  public abstract LabLocator locator();

  public abstract Optional<LabServerProfile> labServerProfile();

  public abstract Optional<LabServerCondition> labServerCondition();

  /**
   * Adds the device to the lab only if the device does not exist in lab.
   *
   * @return the previous {@link DeviceDao} associated with device ID, or empty if there was no
   *     device with the given ID previously.
   */
  @CanIgnoreReturnValue
  public Optional<DeviceDao> addDevice(DeviceDao device) {
    // Set it to true as long as one device is shared.
    // The device info may not have the pool=shared dimension when wrangler return bad data.
    // If that is the first device we check on the lab, it could wrongly identify the lab as
    // satellite lab.
    isSharedPool.getAndUpdate(
        prev -> {
          if (prev != null && prev) {
            return true;
          }
          return DeviceConditionUtil.isSharedPoolDevice(device);
        });
    return Optional.ofNullable(devices.put(device.locator().id(), device));
  }

  /**
   * Removes the device from lab.
   *
   * @return the previous {@link DeviceDao} associated with device ID, or empty if there was no
   *     device with the given ID.
   */
  public Optional<DeviceDao> removeDevice(String deviceId) {
    return Optional.ofNullable(devices.remove(deviceId));
  }

  /** Returns all the devices in this lab. */
  public ImmutableSet<DeviceDao> getDevices() {
    return ImmutableSet.copyOf(devices.values());
  }

  /** Returns the number of the devices in this lab. */
  public int getDeviceCount() {
    return devices.size();
  }

  /**
   * Gets the {@link DeviceDao} associated with device ID, or emtpy if there was no device with the
   * given ID.
   */
  public Optional<DeviceDao> getDevice(String deviceId) {
    return Optional.ofNullable(devices.get(deviceId));
  }

  public boolean isSharedPoolLab() {
    return Boolean.TRUE.equals(isSharedPool.get());
  }
}
