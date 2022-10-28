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

package com.google.wireless.qa.mobileharness.shared.model.lab;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/** A set of devices that belongs to the same lab. */
public class DeviceInfos {
  /** {DeviceSerial, DeviceInfo} mapping of the devices in the same lab. */
  private final Map<String, DeviceInfo> devices = new ConcurrentHashMap<>();

  /**
   * Adds a new {@link DeviceInfo} to the lab.
   *
   * @throws MobileHarnessException if adding a device with existing serial device serial to the
   *     same lab
   */
  @CanIgnoreReturnValue
  public DeviceInfos add(DeviceInfo deviceInfo) throws MobileHarnessException {
    DeviceInfo previousDevice = devices.putIfAbsent(deviceInfo.locator().getSerial(), deviceInfo);
    if (previousDevice != null) {
      throw new MobileHarnessException(
          ErrorCode.DEVICE_DUPLICATED,
          "Device " + deviceInfo.locator() + " already exists in the lab");
    }
    return this;
  }

  /** Whether this lab contains any devices. */
  public boolean isEmpty() {
    return devices.isEmpty();
  }

  /** Gets the the number of the devices. */
  public int size() {
    return devices.size();
  }

  /** Gets the {@link DeviceInfo} with the given device serial. Or null if it doesn't exist. */
  @Nullable
  public DeviceInfo get(String serial) {
    return devices.get(serial);
  }

  /** Returns all the devices in the this lab. */
  public Map<String, DeviceInfo> getAll() {
    return ImmutableMap.copyOf(devices);
  }

  @Override
  public String toString() {
    return devices.keySet().toString();
  }
}
