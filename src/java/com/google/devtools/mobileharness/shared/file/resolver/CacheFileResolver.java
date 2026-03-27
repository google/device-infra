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

package com.google.devtools.mobileharness.shared.file.resolver;

import static com.google.devtools.mobileharness.shared.file.resolver.cache.PersistentResolvedFileCache.usePersistentCache;
import static java.util.concurrent.TimeUnit.HOURS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.file.resolver.cache.LocalCache;
import com.google.devtools.mobileharness.shared.file.resolver.cache.PersistentResolvedFileCache;
import com.google.devtools.mobileharness.shared.file.resolver.cache.ResolvedFileCache.CachedResolveResult;
import com.google.devtools.mobileharness.shared.util.file.checksum.proto.ChecksumProto.Checksum;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/** The file resolver to cache the resolved files. */
@ThreadSafe
public class CacheFileResolver extends AbstractFileResolver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration SUCCEED_CACHE_EXPIRATION_TIME = Duration.ofHours(3);
  private static final Duration FAIL_CACHE_EXPIRATION_TIME = Duration.ofMinutes(3);

  private final LocalCache localCache;
  @Nullable private final PersistentResolvedFileCache persistentResolvedFileCache;
  private final LocalFileUtil localFileUtil;
  private final InstantSource instantSource;

  public CacheFileResolver(
      ListeningExecutorService executorService,
      LocalFileUtil localFileUtil,
      InstantSource instantSource) {
    super(executorService);
    this.localFileUtil = localFileUtil;
    this.instantSource = instantSource;
    this.localCache = new LocalCache(executorService, instantSource, (key) -> super.resolve(key));
    this.persistentResolvedFileCache =
        PersistentResolvedFileCache.create((key) -> super.resolve(key));
  }

  @VisibleForTesting
  CacheFileResolver(
      ListeningExecutorService executorService,
      LocalFileUtil localFileUtil,
      InstantSource instantSource,
      PersistentResolvedFileCache persistentResolvedFileCache) {
    super(executorService);
    this.localFileUtil = localFileUtil;
    this.instantSource = instantSource;
    this.localCache = new LocalCache(executorService, instantSource, (key) -> super.resolve(key));
    this.persistentResolvedFileCache = persistentResolvedFileCache;
  }

  @Override
  protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
    // The cache logic is processed in resolve method.
    return false;
  }

  @Override
  protected ResolveResult actuallyResolve(ResolveSource resolveSource)
      throws MobileHarnessException {
    throw new MobileHarnessException(
        BasicErrorId.RESOLVE_FILE_GENERIC_ERROR,
        "Should not call CacheFileResolver.actuallyResolve method directly.");
  }

  @Override
  public Optional<ResolveResult> resolve(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException {
    return internalResolve(resolveSource);
  }

  private Optional<ResolveResult> internalResolve(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException {
    if (persistentResolvedFileCache != null && usePersistentCache(resolveSource)) {
      Optional<Checksum> checksum = getChecksum(resolveSource);
      if (checksum.isPresent()) {
        return persistentResolvedFileCache.getCachedResolveResult(resolveSource, checksum.get());
      }
    }
    CachedResolveResult cachedResult = localCache.getCachedResolveResult(resolveSource);
    if (!cachedResult.isCachedData()) {
      try {
        return cachedResult.cachedResult().get();
      } catch (ExecutionException e) {
        if (e.getCause() instanceof MobileHarnessException) {
          throw (MobileHarnessException) e.getCause();
        } else if (e.getCause() instanceof InterruptedException) {
          throw (InterruptedException) e.getCause();
        } else if (e.getCause() instanceof Error) {
          throw (Error) e.getCause();
        } else if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new AssertionError("Should not happen.", e);
      }
    } else {
      logger.atInfo().log("%s has been resolved before. Use the cached value.", resolveSource);
      // If already cached, use cached future's result.
      Optional<ResolveResult> optionalResolveResult;
      try {
        optionalResolveResult = cachedResult.cachedResult().get(RESOLVE_TIMEOUT_IN_HOUR, HOURS);
      } catch (ExecutionException e) {
        if ((e.getCause() instanceof MobileHarnessException
                && ((MobileHarnessException) e.getCause()).getErrorId()
                    == BasicErrorId.RESOLVE_FILE_TIMEOUT)
            || e.getCause() instanceof InterruptedException) {
          logger.atInfo().log(
              "Previous resolve process for %s did not succeed because of timeout or interruption."
                  + " Need to re-resolve.",
              resolveSource);
          localCache.removeItemIfValueMatch(resolveSource, cachedResult.cachedResult());
          return internalResolve(resolveSource);
        } else if (instantSource
            .instant()
            .isAfter(
                cachedResult
                    .cachedResult()
                    .finishTimestamp()
                    .orElse(instantSource.instant())
                    .plus(FAIL_CACHE_EXPIRATION_TIME))) {
          localCache.removeItemIfValueMatch(resolveSource, cachedResult.cachedResult());
          return internalResolve(resolveSource);
        } else {
          throw new MobileHarnessException(
              BasicErrorId.RESOLVE_FILE_CACHED_EXCEPTION_ERROR,
              String.format(
                  "Previous resolve process for %s did not succeed because of exception.",
                  resolveSource),
              e);
        }
      } catch (TimeoutException e) {
        throw new MobileHarnessException(
            BasicErrorId.RESOLVE_FILE_TIMEOUT,
            String.format("Timeout while resolving file %s", resolveSource),
            e);
      }
      if (instantSource
          .instant()
          .isAfter(
              cachedResult
                  .cachedResult()
                  .finishTimestamp()
                  .orElse(instantSource.instant())
                  .plus(SUCCEED_CACHE_EXPIRATION_TIME))) {
        localCache.removeItemIfValueMatch(resolveSource, cachedResult.cachedResult());
        return internalResolve(resolveSource);
      }

      if (optionalResolveResult.isPresent()) {
        ResolveResult resolveResult = optionalResolveResult.get();
        logger.atInfo().log("Previous resolved result: %s", resolveResult);
        for (ResolvedFile resolvedFile : resolveResult.resolvedFiles()) {
          if (!localFileUtil.isFileOrDirExist(resolvedFile.path())) {
            logger.atInfo().log(
                "Previous cached resolved file %s for %s doesn't exist any more. Need to"
                    + " re-resolve.",
                resolvedFile.path(), resolveSource);
            // If any file in the cached result doesn't exist any more, retire cached value
            // and re-resolve.
            localCache.removeItemIfValueMatch(resolveSource, cachedResult.cachedResult());
            return internalResolve(resolveSource);
          }
        }
        if (!resolveResult.resolveSource().equals(resolveSource)) {
          String cachedRootPath = resolveResult.resolveSource().targetDir();
          String thisRootPath = resolveSource.targetDir();
          List<ResolvedFile> thisResolvedFiles = new ArrayList<>();
          for (ResolvedFile cachedResolvedFile : resolveResult.resolvedFiles()) {
            String thisPath =
                PathUtil.join(
                    thisRootPath, PathUtil.makeRelative(cachedRootPath, cachedResolvedFile.path()));
            if (!localFileUtil.isFileOrDirExist(thisPath)) {
              try {
                localFileUtil.prepareDir(PathUtil.dirname(thisPath));
                localFileUtil.copyFileOrDir(cachedResolvedFile.path(), thisPath);
              } catch (MobileHarnessException e) {
                // There may be race condition. The cached file is removed after first check.
                // So check again.
                if (localFileUtil.isFileOrDirExist(cachedResolvedFile.path())
                    && localFileUtil.getFileOrDirSize(cachedResolvedFile.path())
                        == localFileUtil.getFileOrDirSize(thisPath)) {
                  logger.atInfo().withCause(e).log(
                      "Failed to copy file because of two concurrent copies to same destination."
                          + " It's acceptable because the former copy succeeds.");
                } else {
                  logger.atInfo().withCause(e).log(
                      "Previous cached resolved file for %s doesn't exist any more. Need to"
                          + " re-resolve.",
                      resolveSource);
                  // If any file in the cached result doesn't exist any more, retire cached value
                  // and re-resolve.
                  localCache.removeItemIfValueMatch(resolveSource, cachedResult.cachedResult());
                  // Remove already copied file.
                  for (ResolvedFile resolvedFile : thisResolvedFiles) {
                    localFileUtil.removeFileOrDir(resolvedFile.path());
                  }
                  return internalResolve(resolveSource);
                }
              }
            }
            thisResolvedFiles.add(
                ResolvedFile.create(
                    thisPath,
                    cachedResolvedFile.checksum().orElse(null),
                    cachedResolvedFile.spec().orElse(null)));
          }
          ResolveResult result =
              ResolveResult.of(
                  ImmutableList.copyOf(thisResolvedFiles),
                  resolveResult.properties(),
                  resolveSource);
          logger.atInfo().log("Resolved result: %s", result);
          return Optional.of(result);
        }
      }
      return optionalResolveResult;
    }
  }

  @Override
  protected Set<ResolveResult> preBatchProcess(List<ResolveSource> resolveSources)
      throws MobileHarnessException, InterruptedException, ExecutionException {
    Set<ResolveResult> resolveResults = super.preBatchProcess(resolveSources);
    for (ResolveResult resolveResult : resolveResults) {
      localCache.addResolveResult(resolveResult, instantSource.instant());
    }
    return resolveResults;
  }
}
