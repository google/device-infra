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

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCacheManager.CacheType;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionDeviceCache;
import javax.inject.Inject;

/** Local implementation of {@link SessionDeviceCache}. */
public class LocalSessionDeviceCache implements SessionDeviceCache {

  private final DeviceCacheManager deviceCacheManager;

  @Inject
  LocalSessionDeviceCache(DeviceCacheManager deviceCacheManager) {
    this.deviceCacheManager = deviceCacheManager;
  }

  @Override
  public void cache(CacheRequest request) throws MobileHarnessException {
    CacheType type = CacheType.valueOf(request.cacheType());
    for (String deviceControlId : request.deviceControlIds()) {
      deviceCacheManager.cache(
          type,
          deviceControlId,
          /* deviceType= */ null,
          request.timeout(),
          /* leaseId= */ request.sessionId());
    }
  }

  @Override
  public void invalidateCache(InvalidateCacheRequest request) throws MobileHarnessException {
    CacheType type = CacheType.valueOf(request.cacheType());
    for (String deviceControlId : request.deviceControlIds()) {
      deviceCacheManager.invalidate(type, deviceControlId, /* leaseId= */ request.sessionId());
    }
  }
}
