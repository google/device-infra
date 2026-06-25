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

package com.google.devtools.mobileharness.infra.client.longrunningservice.util;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import java.time.Duration;

/** Interface for managing device cache associated with a long-running session. */
public interface SessionDeviceCache {

  /** Request payload to cache one device. */
  public record CacheRequest(
      LabLocator labLocator,
      ImmutableList<String> deviceControlIds,
      Duration timeout,
      String cacheType,
      String sessionId) {
    public CacheRequest {
      cacheType = checkCacheType(cacheType);
    }
  }

  /** Request payload to invalidate device cache. */
  public record InvalidateCacheRequest(
      LabLocator labLocator,
      ImmutableList<String> deviceControlIds,
      String cacheType,
      String sessionId) {
    public InvalidateCacheRequest {
      cacheType = checkCacheType(cacheType);
    }
  }

  /** Caches a device. */
  void cache(CacheRequest request) throws MobileHarnessException, InterruptedException;

  /** Invalidates device cache. */
  void invalidateCache(InvalidateCacheRequest request)
      throws MobileHarnessException, InterruptedException;

  /**
   * Validates and returns the normalized upper-case cache type.
   *
   * @param cacheType the cache type ("general" or "xts")
   * @return the normalized upper-case cache type
   * @throws IllegalArgumentException if cacheType is invalid
   */
  static String checkCacheType(String cacheType) {
    if (Ascii.equalsIgnoreCase(cacheType, "general") || Ascii.equalsIgnoreCase(cacheType, "xts")) {
      return Ascii.toUpperCase(cacheType);
    }
    throw new IllegalArgumentException(
        "Only general and xts cache types are supported, but got: " + cacheType);
  }
}
