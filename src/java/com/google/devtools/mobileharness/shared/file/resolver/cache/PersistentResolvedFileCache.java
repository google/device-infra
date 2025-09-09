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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.common.base.Enums;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveResult;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveSource;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolvedFile;
import com.google.devtools.mobileharness.shared.file.resolver.cache.ResolvedFileCache.CacheLoader;
import com.google.devtools.mobileharness.shared.util.cache.persistent.CacheBuilder;
import com.google.devtools.mobileharness.shared.util.cache.persistent.CacheKey;
import com.google.devtools.mobileharness.shared.util.cache.persistent.PersistentCache;
import com.google.devtools.mobileharness.shared.util.cache.persistent.proto.MetadataProto.ChecksumAlgorithm;
import com.google.devtools.mobileharness.shared.util.cache.persistent.proto.MetadataProto.StorageApi;
import java.nio.file.Path;
import java.util.Optional;

/** Cache for resolved files in persistent file system. */
public final class PersistentResolvedFileCache {

  public static final String PERSISTENT_CACHE_KEY = "persistent";
  public static final String TEAM_KEY = "team";
  public static final String STORAGE_API_KEY = "storage_api";
  public static final String CHECKSUM_ALGORITHM_KEY = "checksum_algorithm";
  public static final String CHECKSUM_KEY = "checksum";

  private final PersistentCache<ResolveSource> persistentCache;

  PersistentResolvedFileCache(PersistentCache<ResolveSource> persistentCache) {
    this.persistentCache = persistentCache;
  }

  public PersistentResolvedFileCache(CacheBuilder cacheBuilder, CacheLoader cacheLoader) {
    this(cacheBuilder.build(k -> cacheLoader.load(k).map(result -> getOnlyPath(result))));
  }

  public Optional<ResolveResult> getCachedResolveResult(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException {
    CacheKey<ResolveSource> cacheKey = createCacheKey(resolveSource);
    Optional<Path> cachedResult =
        persistentCache.get(cacheKey, Path.of(resolveSource.targetDir()), /* isTargetDir= */ true);
    return cachedResult.map((p) -> createResolveResult(p, resolveSource));
  }

  public boolean isCachedResolveResultValid(ResolveSource resolveSource) {
    try {
      validateResolveSource(resolveSource);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  // @throws IllegalArgumentException if the resolve source is invalid.
  private void validateResolveSource(ResolveSource resolveSource) {
    checkArgument(resolveSource.parameters().containsKey(TEAM_KEY), "Team is not set.");
    checkArgument(
        resolveSource.parameters().containsKey(STORAGE_API_KEY), "Storage API is not set.");
    com.google.common.base.Optional<StorageApi> storageApi =
        Enums.getIfPresent(StorageApi.class, resolveSource.parameters().get(STORAGE_API_KEY));
    checkArgument(
        storageApi.isPresent(),
        "Invalid storage API: %s",
        resolveSource.parameters().get(STORAGE_API_KEY));
    checkArgument(
        resolveSource.parameters().containsKey(CHECKSUM_ALGORITHM_KEY),
        "Checksum algorithm is not set.");
    com.google.common.base.Optional<ChecksumAlgorithm> checksumAlgorithm =
        Enums.getIfPresent(
            ChecksumAlgorithm.class, resolveSource.parameters().get(CHECKSUM_ALGORITHM_KEY));
    checkArgument(
        checksumAlgorithm.isPresent(),
        "Invalid checksum algorithm: %s",
        resolveSource.parameters().get(CHECKSUM_ALGORITHM_KEY));
    checkArgument(resolveSource.parameters().containsKey(CHECKSUM_KEY), "Checksum is not set.");
    checkArgument(
        resolveSource.parameters().containsKey(PERSISTENT_CACHE_KEY),
        "Persistent cache is not set.");
    checkArgument(
        Boolean.valueOf(resolveSource.parameters().get(PERSISTENT_CACHE_KEY)),
        "Persistent cache is not enabled.");
  }

  private CacheKey<ResolveSource> createCacheKey(ResolveSource resolveSource) {
    validateResolveSource(resolveSource);
    return CacheKey.create(
        resolveSource,
        resolveSource.parameters().get(TEAM_KEY),
        StorageApi.valueOf(resolveSource.parameters().get(STORAGE_API_KEY)),
        ChecksumAlgorithm.valueOf(resolveSource.parameters().get(CHECKSUM_ALGORITHM_KEY)),
        resolveSource.parameters().get(CHECKSUM_KEY));
  }

  private ResolveResult createResolveResult(Path path, ResolveSource resolveSource) {
    return ResolveResult.of(
        ImmutableList.of(ResolvedFile.create(path.toString(), null)),
        ImmutableMap.of(),
        resolveSource);
  }

  private static Path getOnlyPath(ResolveResult resolveResult) {
    ResolvedFile resolvedFile = getOnlyElement(resolveResult.resolvedFiles());
    return Path.of(resolvedFile.path());
  }
}
