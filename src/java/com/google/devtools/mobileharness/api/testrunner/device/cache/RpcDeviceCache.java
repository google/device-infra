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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCacheManager.CacheType;
import com.google.devtools.mobileharness.infra.controller.device.DeviceIdManager;
import java.time.Duration;
import java.util.Collection;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/** The facade of the device cache manager for RPC services. */
@Singleton
public class RpcDeviceCache {

  private final DeviceCacheManager deviceCacheManager;
  private final DeviceIdManager deviceIdManager;

  @Inject
  RpcDeviceCache() {
    this(DeviceCacheManager.getInstance(), DeviceIdManager.getInstance());
  }

  @VisibleForTesting
  RpcDeviceCache(DeviceCacheManager deviceCacheManager, DeviceIdManager deviceIdManager) {
    this.deviceCacheManager = deviceCacheManager;
    this.deviceIdManager = deviceIdManager;
  }

  /** Leases or extends the lease of the device caches for the specified string cache type. */
  public void cache(
      String cacheType,
      Collection<String> deviceControlIdsOrUuids,
      Duration timeout,
      @Nullable String leaseId) {
    CacheType type = getCacheType(cacheType);
    deviceControlIdsOrUuids.stream()
        .map(this::resolveDeviceControlId)
        .forEach(
            deviceControlId -> deviceCacheManager.cache(type, deviceControlId, timeout, leaseId));
  }

  /** Invalidates the device caches of the specified string cache type. */
  public void invalidate(
      String cacheType, Collection<String> deviceControlIdsOrUuids, @Nullable String leaseId) {
    CacheType type = getCacheType(cacheType);
    deviceControlIdsOrUuids.stream()
        .map(this::resolveDeviceControlId)
        .forEach(deviceControlId -> deviceCacheManager.invalidate(type, deviceControlId, leaseId));
  }

  private String resolveDeviceControlId(String deviceControlIdOrUuid) {
    return deviceIdManager
        .getDeviceIdFromUuid(deviceControlIdOrUuid)
        .map(DeviceId::controlId)
        .orElse(deviceControlIdOrUuid);
  }

  private static CacheType getCacheType(String cacheType) {
    return CacheType.valueOf(Ascii.toUpperCase(cacheType));
  }
}
