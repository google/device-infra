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

import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveResult;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveSource;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolvedFile;
import com.google.devtools.mobileharness.shared.file.resolver.cache.ResolvedFileCache.CacheLoader;
import com.google.devtools.mobileharness.shared.util.cache.persistent.CacheBuilder;
import com.google.devtools.mobileharness.shared.util.cache.persistent.CacheKey;
import com.google.devtools.mobileharness.shared.util.cache.persistent.CacheResult;
import com.google.devtools.mobileharness.shared.util.cache.persistent.PersistentCache;
import com.google.devtools.mobileharness.shared.util.file.checksum.proto.ChecksumProto.Checksum;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;

/** Cache for resolved files in persistent file system. */
public class PersistentResolvedFileCache {

  public static final String PERSISTENT_CACHE_KEY = "persistent";
  public static final String TEAM_KEY = "team";
  private static final String DEFAULT_TEAM = "ats";

  private final PersistentCache<ResolveSource> persistentCache;

  @VisibleForTesting
  PersistentResolvedFileCache(PersistentCache<ResolveSource> persistentCache) {
    this.persistentCache = persistentCache;
  }

  PersistentResolvedFileCache(CacheBuilder cacheBuilder, CacheLoader cacheLoader) {
    this(cacheBuilder.build(k -> cacheLoader.load(k).map(result -> getOnlyPath(result))));
  }

  @Nullable
  public static PersistentResolvedFileCache create(CacheLoader cacheLoader) {
    CacheBuilder cacheBuilder = CacheBuilder.getInstance();
    if (cacheBuilder != null) {
      return new PersistentResolvedFileCache(cacheBuilder, cacheLoader);
    } else {
      return null;
    }
  }

  public Optional<ResolveResult> getCachedResolveResult(
      ResolveSource resolveSource, Checksum checksum)
      throws MobileHarnessException, InterruptedException {
    CacheKey<ResolveSource> cacheKey = createCacheKey(resolveSource, checksum);
    Optional<CacheResult> cachedResult =
        persistentCache.get(cacheKey, Path.of(resolveSource.targetDir()), /* isTargetDir= */ true);
    return cachedResult.map((r) -> createResolveResult(r, resolveSource));
  }

  public static boolean usePersistentCache(ResolveSource resolveSource) {
    return resolveSource.parameters().containsKey(PERSISTENT_CACHE_KEY)
        && Boolean.parseBoolean(resolveSource.parameters().get(PERSISTENT_CACHE_KEY));
  }

  private CacheKey<ResolveSource> createCacheKey(ResolveSource resolveSource, Checksum checksum) {
    return CacheKey.create(
        resolveSource, resolveSource.parameters().getOrDefault(TEAM_KEY, DEFAULT_TEAM), checksum);
  }

  private ResolveResult createResolveResult(CacheResult cacheResult, ResolveSource resolveSource) {
    return ResolveResult.of(
        ImmutableList.of(
            ResolvedFile.create(
                cacheResult.symlinkPath().toString(), cacheResult.getEncodedChecksum())),
        ImmutableMap.of(),
        resolveSource);
  }

  private static Path getOnlyPath(ResolveResult resolveResult) {
    ResolvedFile resolvedFile = getOnlyElement(resolveResult.resolvedFiles());
    return Path.of(resolvedFile.path());
  }
}
