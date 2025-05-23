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
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.platform.testbed.adhoc.AdhocTestbedConfig;
import com.google.wireless.qa.mobileharness.shared.api.device.CompositeDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.device.TestbedDevice;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.time.Duration;

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
    Duration cacheTimeout = testInfo.timer().remainingTimeJava();
    // If we're using an adhoc TestbedDevice, we should only cache its subdevices. When these
    // subdevices are re-detected after the adhoc testbed is done using them, they don't need to be
    // set up again. The TestbedDevice is only meant to be used with one test so there is no benefit
    // to caching it.
    if (!isAdhocTestbedDevice(device)) {
      DeviceCache.getInstance()
          .cache(device.getDeviceId(), device.getClass().getSimpleName(), cacheTimeout);
    }
    if (device instanceof CompositeDevice) {
      for (Device subDevice : ((CompositeDevice) device).getManagedDevices()) {
        DeviceCache.getInstance()
            .cache(subDevice.getDeviceId(), subDevice.getClass().getSimpleName(), cacheTimeout);
      }
    }
  }

  /**
   * Uncaches a composite device and its subDevices.
   *
   * @param device CompositeDevice to uncache
   */
  public static void uncacheTestbed(Device device) {
    DeviceCache.getInstance().invalidateCache(device.getDeviceId());
    if (device instanceof CompositeDevice) {
      for (Device subDevice : ((CompositeDevice) device).getManagedDevices()) {
        DeviceCache.getInstance().invalidateCache(subDevice.getDeviceId());
      }
    }
  }

  private static boolean isAdhocTestbedDevice(Device device) {
    if (!(device instanceof TestbedDevice)) {
      return false;
    }
    TestbedDevice testbedDevice = (TestbedDevice) device;
    return testbedDevice.getConfig() instanceof AdhocTestbedConfig;
  }
}
