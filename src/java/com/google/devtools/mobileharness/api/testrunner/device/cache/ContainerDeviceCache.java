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
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCacheManager.CacheType;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Cache devices for container. Devices should be cached before container starts and the cache
 * should be discarded after container is closed. Since we haven't made it to implement device
 * detection logic in sandbox, this design is to avoid killing tests running in container after
 * devices are passed through to sandbox.
 *
 * <p>TODO: Remove this class when remote detection is implemented.
 */
public class ContainerDeviceCache {

  private static final CacheType CACHE_TYPE = CacheType.CONTAINER;

  private static final Supplier<ContainerDeviceCache> INSTANCE =
      Suppliers.memoize(ContainerDeviceCache::new);

  public static ContainerDeviceCache getInstance() {
    return INSTANCE.get();
  }

  @CanIgnoreReturnValue
  public boolean cache(String deviceControlId, String deviceType, Duration timeout) {
    return DeviceCacheManager.getInstance().cache(CACHE_TYPE, deviceControlId, deviceType, timeout);
  }

  @CanIgnoreReturnValue
  public boolean invalidateCache(String deviceControlId) {
    return DeviceCacheManager.getInstance().invalidate(CACHE_TYPE, deviceControlId);
  }
}
