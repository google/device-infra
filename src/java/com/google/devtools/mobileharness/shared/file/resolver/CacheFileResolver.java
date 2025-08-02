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

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.util.concurrent.TimeUnit.HOURS;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.cache.FileCacheManager;
import com.google.devtools.mobileharness.shared.util.cache.FileCacheManager.CacheStatus;
import com.google.devtools.mobileharness.shared.util.cache.RecordedResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.ThreadSafe;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

/** The file resolver to cache the resolved files. */
@javax.annotation.concurrent.ThreadSafe
public class CacheFileResolver extends AbstractFileResolver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration SUCCEED_CACHE_EXPIRATION_TIME = Duration.ofHours(3);
  private static final Duration FAIL_CACHE_EXPIRATION_TIME = Duration.ofMinutes(3);

  /** The files and parameters to be resolved. */
  @Immutable
  @AutoValue
  abstract static class CachedResolveSource {
    private static CachedResolveSource create(ResolveSource resolveSource) {
      return new AutoValue_CacheFileResolver_CachedResolveSource(
          resolveSource.path(), resolveSource.parameters());
    }

    abstract String path();

    abstract ImmutableMap<String, String> parameters();
  }

  @ThreadSafe
  private static final class LocalCacheManager
      implements FileCacheManager<ResolveSource, ResolveResult> {

    private final ConcurrentMap<CachedResolveSource, RecordedResult<ResolveResult>> cache =
        new ConcurrentHashMap<>();
    private final Executor executor;
    private final InstantSource instantSource;

    LocalCacheManager(Executor executor, InstantSource instantSource) {
      this.executor = executor;
      this.instantSource = instantSource;
    }

    @Override
    public CacheStatus<ResolveResult> getOrLoad(
        ResolveSource resolveSource, CacheLoader<ResolveSource, ResolveResult> loader) {
      CachedResolveSource key = CachedResolveSource.create(resolveSource);
      SettableFuture<Optional<ResolveResult>> resolveResultFuture = SettableFuture.create();
      RecordedResult<ResolveResult> cachedResolveResult =
          RecordedResult.create(resolveResultFuture, executor, instantSource);
      RecordedResult<ResolveResult> previousResult = cache.putIfAbsent(key, cachedResolveResult);
      if (previousResult == null) {
        logger.atInfo().log("%s has not been resolved before. Need to resolve.", key);
        // If not cached, resolve and set the resolved result to future.
        resolveResultFuture.setFuture(Futures.submit(() -> loader.load(resolveSource), executor));
        return CacheStatus.create(cachedResolveResult, /* loadInvoked= */ true);
      } else {
        return CacheStatus.create(previousResult, /* loadInvoked= */ false);
      }
    }

    private void removeItemIfValueMatch(
        ResolveSource key, RecordedResult<ResolveResult> expectedValue) {
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

    private void addResolveResult(ResolveResult resolveResult, Instant resolveTimestamp) {
      cache.putIfAbsent(
          CachedResolveSource.create(resolveResult.resolveSource()),
          RecordedResult.create(
              immediateFuture(Optional.of(resolveResult)),
              executor,
              InstantSource.fixed(resolveTimestamp)));
    }
  }

  private final LocalCacheManager cacheManager;
  private final LocalFileUtil localFileUtil;
  private final InstantSource instantSource;

  public CacheFileResolver(
      ListeningExecutorService executorService,
      LocalFileUtil localFileUtil,
      InstantSource instantSource) {
    super(executorService);
    this.localFileUtil = localFileUtil;
    this.instantSource = instantSource;
    this.cacheManager = new LocalCacheManager(executorService, instantSource);
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
    CacheStatus<ResolveResult> cacheStatus =
        cacheManager.getOrLoad(resolveSource, (key) -> super.resolve(key));
    if (cacheStatus.loadInvoked()) {
      try {
        return cacheStatus.cachedResult().valueFuture().get();
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
        optionalResolveResult =
            cacheStatus.cachedResult().valueFuture().get(RESOLVE_TIMEOUT_IN_HOUR, HOURS);
      } catch (ExecutionException e) {
        if ((e.getCause() instanceof MobileHarnessException
                && ((MobileHarnessException) e.getCause()).getErrorId()
                    == BasicErrorId.RESOLVE_FILE_TIMEOUT)
            || e.getCause() instanceof InterruptedException) {
          logger.atInfo().log(
              "Previous resolve process for %s did not succeed because of timeout or interruption."
                  + " Need to re-resolve.",
              resolveSource);
          cacheManager.removeItemIfValueMatch(resolveSource, cacheStatus.cachedResult());
          return internalResolve(resolveSource);
        } else if (instantSource
            .instant()
            .isAfter(
                cacheStatus
                    .cachedResult()
                    .finishTimestamp()
                    .orElse(instantSource.instant())
                    .plus(FAIL_CACHE_EXPIRATION_TIME))) {
          cacheManager.removeItemIfValueMatch(resolveSource, cacheStatus.cachedResult());
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
              cacheStatus
                  .cachedResult()
                  .finishTimestamp()
                  .orElse(instantSource.instant())
                  .plus(SUCCEED_CACHE_EXPIRATION_TIME))) {
        cacheManager.removeItemIfValueMatch(resolveSource, cacheStatus.cachedResult());
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
            cacheManager.removeItemIfValueMatch(resolveSource, cacheStatus.cachedResult());
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
                  cacheManager.removeItemIfValueMatch(resolveSource, cacheStatus.cachedResult());
                  // Remove already copied file.
                  for (ResolvedFile resolvedFile : thisResolvedFiles) {
                    localFileUtil.removeFileOrDir(resolvedFile.path());
                  }
                  return internalResolve(resolveSource);
                }
              }
            }
            thisResolvedFiles.add(
                ResolvedFile.create(thisPath, cachedResolvedFile.checksum().orElse(null)));
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
      throws MobileHarnessException, InterruptedException {
    Set<ResolveResult> resolveResults = super.preBatchProcess(resolveSources);
    for (ResolveResult resolveResult : resolveResults) {
      cacheManager.addResolveResult(resolveResult, instantSource.instant());
    }
    return resolveResults;
  }
}
