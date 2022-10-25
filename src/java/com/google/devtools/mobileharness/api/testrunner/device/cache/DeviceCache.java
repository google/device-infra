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

package com.google.devtools.mobileharness.api.testrunner.device.cache;

import com.google.common.base.Suppliers;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Shared cache of devices which are undergoing reboot and should not be detected offline.
 *
 * @see #cache(String, String, Duration)
 */
public abstract class DeviceCache {

  private static final Supplier<DeviceCache> INSTANCE = Suppliers.memoize(DeviceCache::initialize);

  public static DeviceCache getInstance() {
    return INSTANCE.get();
  }

  private static DeviceCache initialize() {
    try {
      return createLocalDeviceCache();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to create device cache.", e);
    }
  }

  private static DeviceCache createLocalDeviceCache() throws ReflectiveOperationException {
    return Class.forName(
            "com.google.devtools.mobileharness.api.testrunner.device.cache.LocalDeviceCache")
        .asSubclass(DeviceCache.class)
        .getDeclaredConstructor()
        .newInstance();
  }

  /**
   * Caches the device with device ID {@code deviceControlId}.
   *
   * <p>If a device is cached, in the following {@code timeout} period of time, we will still treat
   * this device as it is in a normal state even though MH device manager cannot detect this device.
   * In other words, MH will <b>NOT</b> stop the current test on the cached device even though the
   * device is currently undetectable. Also, MH will <b>NOT</b> reboot and recover the cached
   * device.
   *
   * <p>Please remember to: 1. call this method before you reboot/fastboot/... a device in your MH
   * driver/decorator/plugin. 2. call {@link #invalidateCache(String)} after you ensure the device
   * is back to available.
   *
   * <p>If you call this method more than once without calling {@link #invalidateCache(String)}, the
   * previous cached record will be overridden.
   *
   * <p>After an MH test ends, all device caches belonging to the test will be invalidated
   * automatically. You can only cache devices which belong to your test.
   *
   * @param deviceControlId device ID to cache (device control ID)
   * @param deviceType the type of the device to cache, e.g., "AndroidRealDevice", "IosRealDevice",
   *     etc., in most cases which is the simple class name of a {@code Device} instance
   * @param timeout duration this cache can exist
   * @return boolean to indicate whether the device is successfully cached
   */
  @CanIgnoreReturnValue
  public abstract boolean cache(String deviceControlId, String deviceType, Duration timeout);

  /**
   * Discards the cache of device with ID {@code deviceControlId}.
   *
   * @see #cache(String, String, Duration)
   * @param deviceControlId device ID to discard
   * @return boolean to indicate whether the invalidate operation succeeds
   */
  @CanIgnoreReturnValue
  public abstract boolean invalidateCache(String deviceControlId);
}
