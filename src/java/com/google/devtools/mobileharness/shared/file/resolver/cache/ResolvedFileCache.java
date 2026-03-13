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

package com.google.devtools.mobileharness.shared.file.resolver.cache;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveResult;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveSource;
import com.google.devtools.mobileharness.shared.util.concurrent.TimestampedFuture;
import com.google.errorprone.annotations.ThreadSafe;
import java.util.Optional;

/** An interface to cache the resolved file results. */
@ThreadSafe
public interface ResolvedFileCache {

  /** A functional interface for a loader. */
  @FunctionalInterface
  public static interface CacheLoader {
    Optional<ResolveResult> load(ResolveSource key)
        throws MobileHarnessException, InterruptedException;
  }

  /** The cached result of a resolve source. */
  public static class CachedResolveResult {
    private final TimestampedFuture<Optional<ResolveResult>> cachedResult;
    private final boolean isCachedData;

    CachedResolveResult(
        TimestampedFuture<Optional<ResolveResult>> cachedResult, boolean isCachedData) {
      this.cachedResult = cachedResult;
      this.isCachedData = isCachedData;
    }

    /** Creates a {@link CachedResolveResult}. */
    public static CachedResolveResult create(
        TimestampedFuture<Optional<ResolveResult>> cachedResult, boolean isCachedData) {
      return new CachedResolveResult(cachedResult, isCachedData);
    }

    /** Returns the cached result. */
    public TimestampedFuture<Optional<ResolveResult>> cachedResult() {
      return cachedResult;
    }

    /** Returns whether the result is got from cache. */
    public boolean isCachedData() {
      return isCachedData;
    }
  }

  /**
   * Gets the cached result for the given resolve source.
   *
   * <p>This method is basically async and returns as soon as the get or load decision is made.
   *
   * @param resolveSource the resolve source to be cached
   * @return the cached result
   * @throws MobileHarnessException if an error occurs
   * @throws InterruptedException if the current thread is interrupted
   */
  CachedResolveResult getCachedResolveResult(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException;
}
