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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import javax.annotation.Nullable;

/** The facade of the device cache manager for RPC services. */
public final class RpcDeviceCache {

  private RpcDeviceCache() {}

  /** Leases or extends the lease of the device cache for the specified string cache type. */
  @CanIgnoreReturnValue
  public static boolean cache(
      String cacheType, String deviceControlId, Duration timeout, @Nullable String leaseId) {
    CacheType type = CacheType.XTS;
    if (Ascii.equalsIgnoreCase("container", cacheType)) {
      type = CacheType.CONTAINER;
    } else if (Ascii.equalsIgnoreCase("general", cacheType)) {
      type = CacheType.GENERAL;
    }
    return DeviceCacheManager.getInstance()
        .cache(type, deviceControlId, /* deviceType= */ null, timeout, leaseId);
  }

  /** Invalidates the device cache of the specified string cache type. */
  @CanIgnoreReturnValue
  public static boolean invalidate(
      String cacheType, String deviceControlId, @Nullable String leaseId) {
    CacheType type = CacheType.XTS;
    if (Ascii.equalsIgnoreCase("container", cacheType)) {
      type = CacheType.CONTAINER;
    } else if (Ascii.equalsIgnoreCase("general", cacheType)) {
      type = CacheType.GENERAL;
    }
    return DeviceCacheManager.getInstance().invalidate(type, deviceControlId, leaseId);
  }
}
