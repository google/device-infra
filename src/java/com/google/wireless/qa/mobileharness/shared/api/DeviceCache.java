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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Shared cache of devices which are undergoing reboot and should not be detected offline.
 *
 * @deprecated Use {@link
 *     com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache#getInstance()}
 *     instead.
 */
@Deprecated
public final class DeviceCache {

  private DeviceCache() {}

  /** Default expiration time of a cached device IDs. */
  private static final long DEFAULT_CACHE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);

  private static final com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache
      newDeviceCache;

  static {
    newDeviceCache =
        com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache.getInstance();
  }

  /**
   * Caches the given device ID. Allows it to get disconnected for {@link
   * #DEFAULT_CACHE_TIMEOUT_MS}.
   *
   * @deprecated use {@link #cache(String, String, long)} instead
   * @param typeName Device type name to be cached, e.g., AndroidRealDevice, TestbedDevice, etc.
   * @param deviceId Device serial number to be cached
   */
  @Deprecated
  public static void cache(String typeName, String deviceId) {
    cache(typeName, deviceId, DEFAULT_CACHE_TIMEOUT_MS);
  }

  /**
   * Caches the device ID with the given device type class name.
   *
   * @deprecated use {@link #cache(String, String, long)} instead
   * @param typeName type name to index the devices in cache
   * @param deviceId device ID to cache
   * @param cacheTimeoutMs expiration time of the cache
   * @param autoDelete whether to auto remove cache when device is detectable, which is not used. We
   *     expect users to explicitly invalidate cache instead of using auto-delete.
   */
  @Deprecated
  public static void cache(
      String typeName,
      String deviceId,
      long cacheTimeoutMs,
      @SuppressWarnings("unused") boolean autoDelete) {
    cache(typeName, deviceId, cacheTimeoutMs);
  }

  /**
   * Caches the device ID with the given device type class name.
   *
   * @param typeName type name to index the devices in cache
   * @param deviceId device ID to cache
   * @param cacheTimeoutMs expiration time of the cache
   * @deprecated Use {@link
   *     com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache#cache} instead.
   */
  @Deprecated
  public static void cache(String typeName, String deviceId, long cacheTimeoutMs) {
    newDeviceCache.cache(deviceId, typeName, Duration.ofMillis(cacheTimeoutMs));
  }

  /**
   * Discards the cached device ID of the given type name.
   *
   * @deprecated Use {@link
   *     com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache#invalidateCache(String)}
   *     instead.
   */
  @Deprecated
  public static void invalidateCache(@SuppressWarnings("unused") String typeName, String deviceId) {
    newDeviceCache.invalidateCache(deviceId);
  }
}
