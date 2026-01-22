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

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveResult;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveSource;
import com.google.devtools.mobileharness.shared.file.resolver.cache.ResolvedFileCache.CacheLoader;
import com.google.devtools.mobileharness.shared.file.resolver.cache.ResolvedFileCache.CachedResolveResult;
import com.google.devtools.mobileharness.shared.util.concurrent.TimestampedFuture;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.ThreadSafe;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
 * A local cache for {@link ResolvedFileCache}.
 *
 * <p>The cache is shared by all jobs with the same LocalCache instance. On the lab server side, the
 * cache is created once per lab server. As a result, the cache is shared by all jobs running on the
 * same lab server.
 */
@ThreadSafe
public final class LocalCache implements ResolvedFileCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ConcurrentMap<CachedResolveSource, TimestampedFuture<Optional<ResolveResult>>>
      cache = new ConcurrentHashMap<>();

  private final Executor executor;
  private final InstantSource instantSource;

  private final Object lock = new Object();

  @GuardedBy("lock")
  private final CacheLoader cacheLoader;

  /** The files and parameters to be resolved. */
  @Immutable
  @AutoValue
  abstract static class CachedResolveSource {
    private static CachedResolveSource create(ResolveSource resolveSource) {
      return new AutoValue_LocalCache_CachedResolveSource(
          resolveSource.path(), resolveSource.parameters());
    }

    abstract String path();

    abstract ImmutableMap<String, String> parameters();
  }

  public LocalCache(Executor executor, InstantSource instantSource, CacheLoader cacheLoader) {
    this.executor = executor;
    this.instantSource = instantSource;
    this.cacheLoader = cacheLoader;
  }

  @Override
  public CachedResolveResult getCachedResolveResult(ResolveSource resolveSource) {
    CachedResolveSource key = CachedResolveSource.create(resolveSource);
    SettableFuture<Optional<ResolveResult>> resolveResultFuture = SettableFuture.create();
    TimestampedFuture<Optional<ResolveResult>> cachedResolveResult =
        TimestampedFuture.create(resolveResultFuture, executor, instantSource);
    TimestampedFuture<Optional<ResolveResult>> previousResult =
        cache.putIfAbsent(key, cachedResolveResult);
    if (previousResult == null) {
      logger.atInfo().log("%s has not been resolved before. Need to resolve.", key);
      // If not cached, resolve and set the resolved result to future.
      resolveResultFuture.setFuture(
          Futures.submit(
              () -> {
                synchronized (lock) {
                  return cacheLoader.load(resolveSource);
                }
              },
              executor));
      return CachedResolveResult.create(cachedResolveResult, /* isCachedData= */ false);
    } else {
      return CachedResolveResult.create(previousResult, /* isCachedData= */ true);
    }
  }

  /**
   * Removes the item from the cache if the value matches the expected value.
   *
   * <p>This method is used to remove the cached value when the previous resolve process did not
   * succeed because of timeout or interruption.
   */
  public void removeItemIfValueMatch(
      ResolveSource key, TimestampedFuture<Optional<ResolveResult>> expectedValue) {
    cache.computeIfPresent(
        CachedResolveSource.create(key),
        (k, value) -> {
          if (value == expectedValue) {
            return null;
          } else {
            return value;
          }
        });
  }

  /** Adds the resolve result to the cache. */
  public void addResolveResult(ResolveResult resolveResult, Instant resolveTimestamp) {
    cache.putIfAbsent(
        CachedResolveSource.create(resolveResult.resolveSource()),
        TimestampedFuture.create(
            immediateFuture(Optional.of(resolveResult)),
            executor,
            InstantSource.fixed(resolveTimestamp)));
  }
}
