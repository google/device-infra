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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.flogger.FluentLogger;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.annotation.concurrent.GuardedBy;

/**
 * Manages all device caches which are going through rebooting and running tests in container.
 *
 * <ol>
 *   <li>For devices undergoing reboot, they are not supposed to be detected offline and cached as
 *       {@code CacheType.GENERAL}.
 *   <li>For devices running tests in container, they are passed through to container and cannot be
 *       detected by lab. These devices are cached as {@code CacheType.CONTAINER} to avoid tests in
 *       container being killed. This class is supposed to be used only by MH device manager and
 *       some specific MH device detectors, like {@link
 *       com.google.wireless.qa.mobileharness.shared.api.detector.CacheableDetector}.
 * </ol>
 */
public class DeviceCacheManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Clock for generating the time used with the caches. */
  @VisibleForTesting static volatile Clock clock = Clock.systemUTC();

  /** Total number of cache overridden. */
  private static final AtomicInteger overriddenCount = new AtomicInteger(0);

  @GuardedBy("itself")
  private static final Map<CacheType, Map<String, CacheInfo>> cachesByCacheType =
      new EnumMap<>(CacheType.class);

  static {
    cachesByCacheType.put(CacheType.GENERAL, new HashMap<>());
    cachesByCacheType.put(CacheType.CONTAINER, new HashMap<>());
  }

  private static final Supplier<DeviceCacheManager> INSTANCE =
      Suppliers.memoize(DeviceCacheManager::new);

  public static DeviceCacheManager getInstance() {
    return INSTANCE.get();
  }

  @AutoValue
  abstract static class CacheInfo {
    private static CacheInfo create(String deviceType, Timestamp expireTimestamp) {
      return new AutoValue_DeviceCacheManager_CacheInfo(deviceType, expireTimestamp);
    }

    /** Device type this cache is for. */
    abstract String deviceType();

    /** Timestamp when this cache will expire. */
    abstract Timestamp expireTimestamp();
  }

  enum CacheType {
    GENERAL,
    CONTAINER
  }

  boolean cache(CacheType cacheType, String deviceControlId, String deviceType, Duration timeout) {
    synchronized (cachesByCacheType) {
      Map<String, CacheInfo> caches = cachesByCacheType.get(cacheType);
      if (caches.containsKey(deviceControlId) && overriddenCount.incrementAndGet() % 100 == 0) {
        logger.atWarning().log(
            "Cache(%s) for device [%s] is overridden while the old cache info is %s with"
                + " stacktrace: %s",
            cacheType,
            deviceControlId,
            caches.get(deviceControlId),
            Throwables.getStackTraceAsString(new Throwable()));
      }
      logger.atInfo().log(
          "Add cache(%s) for device [%s] with timeout %s", cacheType, deviceControlId, timeout);

      Instant deadline = clock.instant().plus(timeout);
      caches.put(
          deviceControlId,
          CacheInfo.create(
              deviceType,
              Timestamps.fromNanos(
                  deadline.getEpochSecond() * 1_000_000_000 + deadline.getNano())));
      return true;
    }
  }

  boolean invalidate(CacheType cacheType, String deviceControlId) {
    synchronized (cachesByCacheType) {
      Map<String, CacheInfo> caches = cachesByCacheType.get(cacheType);
      if (caches.remove(deviceControlId) == null) {
        logger.atWarning().log(
            "Invalidating [%s] cache for device [%s] failed.", cacheType, deviceControlId);
        return false;
      }
      logger.atInfo().log(
          "Invalidating [%s] cache for device [%s] succeeded.", cacheType, deviceControlId);
      return true;
    }
  }

  /** Invalidate both container and general device cache. */
  public void invalidateAllCaches(String deviceControId) {
    invalidate(CacheType.GENERAL, deviceControId);
    invalidate(CacheType.CONTAINER, deviceControId);
  }

  public Set<String> getCachedDevices(String deviceType) {
    synchronized (cachesByCacheType) {
      Set<String> cachedIds = getCachedDevices(CacheType.GENERAL, deviceType);
      Set<String> containerCaches = getCachedDevices(CacheType.CONTAINER, deviceType);
      cachedIds.addAll(containerCaches);
      return cachedIds;
    }
  }

  @GuardedBy("cachesByCacheType")
  Set<String> getCachedDevices(CacheType cacheType, String deviceType) {
    Set<String> cachedIds = new HashSet<>();
    Map<String, CacheInfo> caches = cachesByCacheType.get(cacheType);
    if (caches != null) {
      caches
          .entrySet()
          .removeIf(
              entry ->
                  clock
                      .instant()
                      .isAfter(
                          Instant.ofEpochSecond(
                              entry.getValue().expireTimestamp().getSeconds(),
                              entry.getValue().expireTimestamp().getNanos())));
      caches.forEach(
          (key, value) -> {
            if (value.deviceType().equals(deviceType)) {
              cachedIds.add(key);
            }
          });
    }
    return cachedIds;
  }
}
