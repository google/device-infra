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

package com.google.devtools.mobileharness.shared.util.cache;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.errorprone.annotations.ThreadSafe;
import com.google.errorprone.annotations.ThreadSafeTypeParameter;
import java.util.Optional;

/**
 * A generic file cache manager for storing the results of expensive file loading.
 *
 * @param <K> the type of the key
 * @param <V> the type of the value being cached
 */
@ThreadSafe
public interface FileCacheManager<K, @ThreadSafeTypeParameter V> {

  /**
   * A functional interface for a loader.
   *
   * @param <K> the type of the key
   * @param <V> the type of the value
   */
  @FunctionalInterface
  public static interface CacheLoader<K, V> {
    Optional<V> load(K key) throws MobileHarnessException, InterruptedException;
  }

  /**
   * The status of a cached result.
   *
   * @param <V> the type of the value being cached
   */
  @ThreadSafe
  public static class CacheStatus<@ThreadSafeTypeParameter V> {
    private final RecordedResult<V> cachedResult;
    private final boolean loadInvoked;

    CacheStatus(RecordedResult<V> cachedResult, boolean loadInvoked) {
      this.cachedResult = cachedResult;
      this.loadInvoked = loadInvoked;
    }

    public static <@ThreadSafeTypeParameter V> CacheStatus<V> create(
        RecordedResult<V> cachedResult, boolean loadInvoked) {
      return new CacheStatus<>(cachedResult, loadInvoked);
    }

    /** Returns the cached result. */
    public RecordedResult<V> cachedResult() {
      return cachedResult;
    }

    /** Returns whether the load operation was invoked. */
    public boolean loadInvoked() {
      return loadInvoked;
    }
  }

  /**
   * Gets the cached result for the given key, or loads it if it is not cached.
   *
   * <p>This method is basically async and returns as soon as the get or load decision is made.
   *
   * @param key the key of the cached result
   * @param loader the loader to use if the result is not cached
   * @return the cached result
   * @throws MobileHarnessException if an error occurs
   * @throws InterruptedException if the current thread is interrupted
   */
  CacheStatus<V> getOrLoad(K key, CacheLoader<K, V> loader)
      throws MobileHarnessException, InterruptedException;
}
