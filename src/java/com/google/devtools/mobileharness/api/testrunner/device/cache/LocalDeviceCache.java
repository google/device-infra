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

import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCacheManager.CacheType;
import java.time.Duration;

/** Local implementation of {@link DeviceCache} */
public class LocalDeviceCache extends DeviceCache {

  private static final CacheType CACHE_TYPE = CacheType.GENERAL;

  @Override
  public boolean cache(String deviceControlId, String deviceType, Duration timeout) {
    return DeviceCacheManager.getInstance().cache(CACHE_TYPE, deviceControlId, deviceType, timeout);
  }

  @Override
  public boolean invalidateCache(String deviceControlId) {
    return DeviceCacheManager.getInstance().invalidate(CACHE_TYPE, deviceControlId);
  }
}
