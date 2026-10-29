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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.controller.device.DeviceInfoManager;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.annotation.Nullable;
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
 *       some specific MH device dispatchers, like {@link
 *       com.google.devtools.mobileharness.api.devicemanager.dispatcher.CacheableDispatcher}.
 * </ol>
 */
public class DeviceCacheManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Supplier<DeviceCacheManager> INSTANCE =
      Suppliers.memoize(DeviceCacheManager::new);

  public static DeviceCacheManager getInstance() {
    return INSTANCE.get();
  }

  /** Clock for generating the time used with the caches. */
  @VisibleForTesting volatile Clock clock = Clock.systemUTC();

  /** Total number of cache overridden. */
  private final AtomicInteger overriddenCount = new AtomicInteger(0);

  @GuardedBy("itself")
  private final Map<CacheType, Map<String, CacheInfo>> cachesByCacheType =
      new EnumMap<>(CacheType.class);

  private final DeviceInfoManager deviceInfoManager;

  @VisibleForTesting
  DeviceCacheManager() {
    this(DeviceInfoManager.getInstance());
  }

  @VisibleForTesting
  DeviceCacheManager(DeviceInfoManager deviceInfoManager) {
    this.deviceInfoManager = deviceInfoManager;
    for (CacheType cacheType : CacheType.values()) {
      cachesByCacheType.put(cacheType, new HashMap<>());
    }
  }

  @AutoValue
  abstract static class CacheInfo {
    private static CacheInfo create(
        String deviceType, Timestamp expireTimestamp, @Nullable String leaseId) {
      return new AutoValue_DeviceCacheManager_CacheInfo(
          deviceType, expireTimestamp, Optional.ofNullable(leaseId));
    }

    /** Device type this cache is for. */
    abstract String deviceType();

    /** Timestamp when this cache will expire. */
    abstract Timestamp expireTimestamp();

    /** Lease ID of the cache. Empty if not leased */
    abstract Optional<String> leaseId();
  }

  /** Types of device cache. */
  public enum CacheType {
    GENERAL,
    CONTAINER,
    XTS,
  }

  /**
   * Caches the device by looking up its device type from {@link DeviceInfoManager}, falling back to
   * any existing cached device type for renewal. Returns {@code false} if the device type cannot be
   * determined.
   */
  @CanIgnoreReturnValue
  boolean cache(
      CacheType cacheType, String deviceControlId, Duration timeout, @Nullable String leaseId) {
    synchronized (cachesByCacheType) {
      String deviceType = getDeviceType(deviceControlId);
      if (deviceType == null) {
        CacheInfo oldCache = cachesByCacheType.get(cacheType).get(deviceControlId);
        if (oldCache != null) {
          deviceType = oldCache.deviceType();
        }
      }
      if (deviceType == null) {
        logger.atWarning().log(
            "Skip adding [%s] cache for device [%s] because its device type cannot be determined.",
            cacheType, deviceControlId);
        return false;
      }
      return cache(cacheType, deviceControlId, deviceType, timeout, leaseId);
    }
  }

  @CanIgnoreReturnValue
  boolean cache(
      CacheType cacheType,
      String deviceControlId,
      String deviceType,
      Duration timeout,
      @Nullable String leaseId) {
    synchronized (cachesByCacheType) {
      Set<CacheType> oldTypes = getCacheTypesOfDevice(deviceControlId);
      Map<String, CacheInfo> caches = cachesByCacheType.get(cacheType);
      CacheInfo oldCache = caches.get(deviceControlId);
      if (oldCache != null && overriddenCount.incrementAndGet() % 100 == 0) {
        logger.atWarning().log(
            "Cache(%s) for device [%s] is overridden while the old cache info is %s with"
                + " stacktrace: %s",
            cacheType,
            deviceControlId,
            oldCache,
            Throwables.getStackTraceAsString(new Throwable()));
      }
      logger.atInfo().log(
          "Add cache(%s) for device [%s] with timeout %s, leaseId %s",
          cacheType, deviceControlId, timeout, leaseId);

      Instant deadline = clock.instant().plus(timeout);
      caches.put(
          deviceControlId,
          CacheInfo.create(
              deviceType,
              Timestamps.fromNanos(deadline.getEpochSecond() * 1_000_000_000 + deadline.getNano()),
              leaseId));
      Set<CacheType> newTypes = getCacheTypesOfDevice(deviceControlId);
      if (!oldTypes.equals(newTypes)) {
        logger.atInfo().log(
            "Device [%s] cache types changed: %s -> %s", deviceControlId, oldTypes, newTypes);
      }
      return true;
    }
  }

  @CanIgnoreReturnValue
  boolean invalidate(CacheType cacheType, String deviceControlId, @Nullable String leaseId) {
    synchronized (cachesByCacheType) {
      Set<CacheType> oldTypes = getCacheTypesOfDevice(deviceControlId);
      Map<String, CacheInfo> caches = cachesByCacheType.get(cacheType);
      CacheInfo oldCache = caches.get(deviceControlId);
      if (oldCache == null) {
        logger.atWarning().log(
            "Invalidating [%s] cache for device [%s] failed. Cache not found.",
            cacheType, deviceControlId);
        return false;
      }
      if (leaseId != null
          && oldCache.leaseId().isPresent()
          && !oldCache.leaseId().get().equals(leaseId)) {
        logger.atInfo().log(
            "Skip invalidating [%s] cache for device [%s] because leaseId %s does not match the"
                + " stored leaseId %s.",
            cacheType, deviceControlId, leaseId, oldCache.leaseId().get());
        return false;
      }
      caches.remove(deviceControlId);
      logger.atInfo().log(
          "Invalidating [%s] cache for device [%s] succeeded.", cacheType, deviceControlId);
      Set<CacheType> newTypes = getCacheTypesOfDevice(deviceControlId);
      if (!oldTypes.equals(newTypes)) {
        logger.atInfo().log(
            "Device [%s] cache types changed: %s -> %s", deviceControlId, oldTypes, newTypes);
      }
      return true;
    }
  }

  public boolean isCached(CacheType cacheType, String deviceControlId) {
    synchronized (cachesByCacheType) {
      Map<String, CacheInfo> caches = cachesByCacheType.get(cacheType);
      return caches != null && caches.containsKey(deviceControlId);
    }
  }

  /** Invalidate both container and general device cache. */
  public void invalidateGeneralAndContainerCaches(String deviceControlId) {
    invalidate(CacheType.GENERAL, deviceControlId, /* leaseId= */ null);
    invalidate(CacheType.CONTAINER, deviceControlId, /* leaseId= */ null);
  }

  public Set<String> getCachedDevices(String deviceType) {
    synchronized (cachesByCacheType) {
      Set<String> result = new HashSet<>();

      for (CacheType cacheType : CacheType.values()) {
        result.addAll(getCachedDevices(cacheType, deviceType));
      }
      return result;
    }
  }

  @GuardedBy("cachesByCacheType")
  Set<String> getCachedDevices(CacheType cacheType, String deviceType) {
    Set<String> cachedIds = new HashSet<>();
    Map<String, CacheInfo> caches = cachesByCacheType.get(cacheType);
    if (caches != null) {
      removeExpiredCaches(caches);
      caches.forEach(
          (key, value) -> {
            if (value.deviceType().equals(deviceType)) {
              cachedIds.add(key);
            }
          });
    }
    return cachedIds;
  }

  public ImmutableMap<CacheType, ImmutableSet<String>> getCachedDevicesByCacheType() {
    synchronized (cachesByCacheType) {
      ImmutableMap.Builder<CacheType, ImmutableSet<String>> result = ImmutableMap.builder();
      for (CacheType cacheType : CacheType.values()) {
        Map<String, CacheInfo> caches = cachesByCacheType.get(cacheType);
        if (caches != null) {
          removeExpiredCaches(caches);
          if (!caches.isEmpty()) {
            result.put(cacheType, ImmutableSet.copyOf(caches.keySet()));
          }
        }
      }
      return result.buildOrThrow();
    }
  }

  @GuardedBy("cachesByCacheType")
  private void removeExpiredCaches(Map<String, CacheInfo> caches) {
    caches
        .entrySet()
        .removeIf(
            entry ->
                clock
                    .instant()
                    .isAfter(TimeUtils.toJavaInstant(entry.getValue().expireTimestamp())));
  }

  @GuardedBy("cachesByCacheType")
  private Set<CacheType> getCacheTypesOfDevice(String deviceControlId) {
    Set<CacheType> types = EnumSet.noneOf(CacheType.class);
    for (Map.Entry<CacheType, Map<String, CacheInfo>> entry : cachesByCacheType.entrySet()) {
      if (entry.getValue().containsKey(deviceControlId)) {
        types.add(entry.getKey());
      }
    }
    return types;
  }

  @Nullable
  private String getDeviceType(String deviceControlId) {
    return deviceInfoManager
        .get(deviceControlId)
        .flatMap(
            deviceInfo ->
                deviceInfo.dimensions().supported().get(Dimension.Name.DEVICE_CLASS_NAME).stream()
                    .findFirst())
        .orElse(null);
  }
}
