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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.annotation.concurrent.ThreadSafe;

/** The file resolver to cache the resolved files. */
@ThreadSafe
public class CacheFileResolver extends AbstractFileResolver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The files and parameters to be resolved. */
  @AutoValue
  public abstract static class CachedResolveSource {
    public static CacheFileResolver.CachedResolveSource create(
        String path, ImmutableMap<String, String> parameters) {
      return new AutoValue_CacheFileResolver_CachedResolveSource(path, parameters);
    }

    public abstract String path();

    public abstract ImmutableMap<String, String> parameters();
  }

  /** Cache of resolved result. */
  private final Map<CachedResolveSource, ListenableFuture<Optional<ResolveResult>>>
      resolvedResultsCache = new ConcurrentHashMap<>();

  private final LocalFileUtil localFileUtil;

  public CacheFileResolver(ListeningExecutorService executorService, LocalFileUtil localFileUtil) {
    super(executorService);
    this.localFileUtil = localFileUtil;
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
    CachedResolveSource cachedResolveSource =
        CachedResolveSource.create(resolveSource.path(), resolveSource.parameters());
    SettableFuture<Optional<ResolveResult>> future = SettableFuture.create();
    // ConcurrentHashMap.putIfAbsent guarantees only one future can be really executed for same
    // ResolveSource.
    ListenableFuture<Optional<ResolveResult>> previousFuture =
        resolvedResultsCache.putIfAbsent(cachedResolveSource, future);
    if (previousFuture == null) {
      logger.atInfo().log("%s has not been resolved before. Need to resolve.", resolveSource);
      // If not cached, resolve and set the resolved result to future.
      try {
        Optional<ResolveResult> result = super.resolve(resolveSource);
        future.set(result);
        return result;
      } catch (MobileHarnessException | InterruptedException | Error | RuntimeException e) {
        future.setException(e);
        throw e;
      }
    } else {
      logger.atInfo().log("%s has been resolved before. Use the cached value.", resolveSource);
      // If already cached, use cached future's result.
      Optional<ResolveResult> optionalResolveResult;
      try {
        optionalResolveResult = previousFuture.get(RESOLVE_TIMEOUT_IN_HOUR, HOURS);
      } catch (ExecutionException e) {
        if ((e.getCause() instanceof MobileHarnessException
                && ((MobileHarnessException) e.getCause()).getErrorId()
                    == BasicErrorId.RESOLVE_FILE_TIMEOUT)
            || e.getCause() instanceof InterruptedException) {
          logger.atInfo().log(
              "Previous resolve process for %s did not succeed because of timeout or interruption."
                  + " Need to re-resolve.",
              resolveSource);
          removeItemIfValueMatch(cachedResolveSource, previousFuture);
          return internalResolve(resolveSource);
        } else {
          return MobileHarnessExceptions.rethrow(e, BasicErrorId.RESOLVE_FILE_GENERIC_ERROR);
        }
      } catch (TimeoutException e) {
        throw new MobileHarnessException(
            BasicErrorId.RESOLVE_FILE_TIMEOUT,
            String.format("Timeout while resolving file %s", resolveSource),
            e);
      }

      if (optionalResolveResult.isPresent()) {
        ResolveResult resolveResult = optionalResolveResult.get();
        logger.atInfo().log("Previous resolved result: %s", resolveResult);
        for (String path : resolveResult.paths()) {
          if (!localFileUtil.isFileOrDirExist(path)) {
            logger.atInfo().log(
                "Previous cached resolved file %s for %s doesn't exist any more. Need to"
                    + " re-resolve.",
                path, resolveSource);
            // If any file in the cached result doesn't exist any more, retire cached value
            // and re-resolve.
            removeItemIfValueMatch(cachedResolveSource, previousFuture);
            return internalResolve(resolveSource);
          }
        }
        if (!resolveResult.resolveSource().equals(resolveSource)) {
          String cachedRootPath = resolveResult.resolveSource().targetDir();
          String thisRootPath = resolveSource.targetDir();
          List<String> thisResolvedPaths = new ArrayList<>();
          for (String cachedPath : resolveResult.paths()) {
            String thisPath =
                PathUtil.join(thisRootPath, PathUtil.makeRelative(cachedRootPath, cachedPath));
            if (!localFileUtil.isFileOrDirExist(thisPath)) {
              try {
                localFileUtil.prepareDir(PathUtil.dirname(thisPath));
                localFileUtil.copyFileOrDir(cachedPath, thisPath);
              } catch (MobileHarnessException e) {
                // There may be race condition. The cached file is removed after first check.
                // So check again.
                if (localFileUtil.isFileOrDirExist(cachedPath)
                    && localFileUtil.getFileOrDirSize(cachedPath)
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
                  removeItemIfValueMatch(cachedResolveSource, previousFuture);
                  // Remove already copied file.
                  for (String path : thisResolvedPaths) {
                    localFileUtil.removeFileOrDir(path);
                  }
                  return internalResolve(resolveSource);
                }
              }
            }
            thisResolvedPaths.add(thisPath);
          }
          ResolveResult result =
              ResolveResult.create(
                  ImmutableList.copyOf(thisResolvedPaths),
                  resolveResult.properties(),
                  resolveSource);
          logger.atInfo().log("Resolved result: %s", result);
          return Optional.of(result);
        }
      }
      return optionalResolveResult;
    }
  }

  private void removeItemIfValueMatch(
      CachedResolveSource key, ListenableFuture<Optional<ResolveResult>> expectedValue) {
    resolvedResultsCache.computeIfPresent(
        key,
        (k, value) -> {
          if (value == expectedValue) {
            return null;
          } else {
            return value;
          }
        });
  }

  @Override
  protected Set<ResolveResult> preBatchProcess(List<ResolveSource> resolveSources)
      throws MobileHarnessException, InterruptedException {
    Set<ResolveResult> resolveResults = super.preBatchProcess(resolveSources);
    for (ResolveResult resolveResult : resolveResults) {
      resolvedResultsCache.putIfAbsent(
          CachedResolveSource.create(
              resolveResult.resolveSource().path(), resolveResult.resolveSource().parameters()),
          immediateFuture(Optional.of(resolveResult)));
    }
    return resolveResults;
  }
}
