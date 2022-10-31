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

package com.google.devtools.mobileharness.infra.controller.device;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/** Base implementation for {@link DeviceStatusProvider}. */
public abstract class BaseDeviceStatusProvider implements DeviceStatusProvider {

  /** The cache time of duplicated uuid. */
  private static final Duration DUPLICATED_UUID_CACHE_TIME = Duration.ofHours(1);

  /** Caches of duplicated device uuid. */
  private final Cache<String, Boolean> duplicatedUuidCache;

  protected BaseDeviceStatusProvider() {
    this(CacheBuilder.newBuilder().expireAfterWrite(DUPLICATED_UUID_CACHE_TIME).build());
  }

  protected BaseDeviceStatusProvider(Cache<String, Boolean> duplicatedUuidCache) {
    this.duplicatedUuidCache = duplicatedUuidCache;
  }

  /** {@inheritDoc} It will expire after {@code DUPLICATED_UUID_CACHE_TIME} if cached. */
  @Override
  public void updateDuplicatedUuid(String deviceUuid) {
    duplicatedUuidCache.put(deviceUuid, true);
  }

  @Override
  public Map<Device, DeviceStatusInfo> getAllDeviceStatusWithoutDuplicatedUuid(
      boolean realtimeDetect) throws InterruptedException {
    return getAllDeviceStatus(realtimeDetect).entrySet().stream()
        .filter(x -> duplicatedUuidCache.getIfPresent(x.getKey().getDeviceUuid()) == null)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
