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

package com.google.wireless.qa.mobileharness.shared.api;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.CompositeDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/** Static helper methods for managing composite devices and subclasses. */
public final class CompositeDeviceUtil {

  private CompositeDeviceUtil() {}

  /**
   * Caches a composite device and its subDevices.
   *
   * @param testInfo TestInfo for which the device is being cached
   * @param device CompositeDevice to cache
   */
  public static void cacheTestbed(TestInfo testInfo, Device device) throws MobileHarnessException {
    long cacheTimeoutMs = testInfo.timer().remainingTimeJava().toMillis();
    if (device instanceof CompositeDevice) {
      for (Device subDevice : ((CompositeDevice) device).getManagedDevices()) {
        DeviceCache.cache(
            subDevice.getClass().getSimpleName(),
            subDevice.getDeviceId(),
            cacheTimeoutMs,
            false /* autoDelete */);
      }
    }
    if (!(device instanceof CompositeDevice)) {
      DeviceCache.cache(
          device.getClass().getSimpleName(),
          device.getDeviceId(),
          cacheTimeoutMs,
          /* autoDelete= */ false);
    }
  }

  /**
   * Uncaches a composite device and its subDevices.
   *
   * @param device CompositeDevice to uncache
   */
  public static void uncacheTestbed(Device device) {
    DeviceCache.invalidateCache(device.getClass().getSimpleName(), device.getDeviceId());
    if (device instanceof CompositeDevice) {
      for (Device subDevice : ((CompositeDevice) device).getManagedDevices()) {
        DeviceCache.invalidateCache(subDevice.getClass().getSimpleName(), subDevice.getDeviceId());
      }
    }
  }
}
