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

import com.google.common.base.Ascii;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCacheManager.CacheType;
import java.time.Duration;
import java.util.Collection;
import javax.annotation.Nullable;

/** The facade of the device cache manager for RPC services. */
public final class RpcDeviceCache {

  private RpcDeviceCache() {}

  /** Leases or extends the lease of the device caches for the specified string cache type. */
  public static void cache(
      String cacheType,
      Collection<String> deviceControlIds,
      Duration timeout,
      @Nullable String leaseId) {
    CacheType type = getCacheType(cacheType);
    DeviceCacheManager manager = DeviceCacheManager.getInstance();
    for (String deviceControlId : deviceControlIds) {
      manager.cache(type, deviceControlId, /* deviceType= */ null, timeout, leaseId);
    }
  }

  /** Invalidates the device caches of the specified string cache type. */
  public static void invalidate(
      String cacheType, Collection<String> deviceControlIds, @Nullable String leaseId) {
    CacheType type = getCacheType(cacheType);
    DeviceCacheManager manager = DeviceCacheManager.getInstance();
    for (String deviceControlId : deviceControlIds) {
      manager.invalidate(type, deviceControlId, leaseId);
    }
  }

  private static CacheType getCacheType(String cacheType) {
    return CacheType.valueOf(Ascii.toUpperCase(cacheType));
  }
}
