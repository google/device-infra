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
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.Striped;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.file.resolver.cache.LocalCache;
import com.google.devtools.mobileharness.shared.file.resolver.cache.PersistentResolvedFileCache;
import com.google.devtools.mobileharness.shared.file.resolver.cache.ResolvedFileCache.CachedResolveResult;
import com.google.devtools.mobileharness.shared.file.resolver.cache.SlotCache;
import com.google.devtools.mobileharness.shared.util.concurrent.Callables;
import com.google.devtools.mobileharness.shared.util.concurrent.MobileHarnessRunnable;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.checksum.proto.ChecksumProto.Checksum;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/** The file resolver to cache the resolved files. */
@ThreadSafe
public class CacheFileResolver extends AbstractFileResolver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration SUCCEED_CACHE_EXPIRATION_TIME = Duration.ofHours(3);
  private static final Duration FAIL_CACHE_EXPIRATION_TIME = Duration.ofMinutes(3);

  /** The resolving parameter of the file version. See {@link FileResolver#getVersion}. */
  @VisibleForTesting static final String PARAM_FILE_VERSION = "file_version";

  /** Suffixes of resolved files that are safe to create hard links across target directories. */
  private static final ImmutableSet<String> LINKABLE_FILE_SUFFIX =
      ImmutableSet.of("aab", "apk", "gz", "img", "jar", "par", "tar", "zip");

  private final LocalCache localCache;
  @Nullable private final PersistentResolvedFileCache persistentResolvedFileCache;

  /** Caches the resolved files in place of {@link #localCache}, if set. */
  @Nullable private final SlotCache slotCache;

  /** Keeps two callers from putting a file at the same path at once. */
  private final Striped<Lock> outOfSlotLocks = Striped.lazyWeakLock(1024);

  private final LocalFileUtil localFileUtil;
  private final InstantSource instantSource;

  public CacheFileResolver(
      ListeningExecutorService executorService,
      LocalFileUtil localFileUtil,
      InstantSource instantSource) {
    super(executorService);
    this.localFileUtil = localFileUtil;
    this.instantSource = instantSource;
    this.localCache = new LocalCache(executorService, instantSource, super::resolve);
    this.persistentResolvedFileCache = PersistentResolvedFileCache.create(super::resolve);
    this.slotCache = null;
  }

  /**
   * Creates a resolver that caches the resolved files in a directory of its own, where no caller
   * can remove them, instead of in the directories of the callers that asked first. See {@link
   * SlotCache}.
   */
  public CacheFileResolver(
      ListeningExecutorService executorService,
      LocalFileUtil localFileUtil,
      InstantSource instantSource,
      SlotCache.Options slotCacheOptions) {
    this(
        executorService,
        localFileUtil,
        instantSource,
        slotCacheOptions,
        ThreadPools.createStandardScheduledThreadPool("slot-cache-sweep", /* corePoolSize= */ 1));
  }

  @VisibleForTesting
  CacheFileResolver(
      ListeningExecutorService executorService,
      LocalFileUtil localFileUtil,
      InstantSource instantSource,
      SlotCache.Options slotCacheOptions,
      ScheduledExecutorService sweepScheduler) {
    super(executorService);
    this.localFileUtil = localFileUtil;
    this.instantSource = instantSource;
    this.localCache = new LocalCache(executorService, instantSource, super::resolve);
    this.persistentResolvedFileCache = PersistentResolvedFileCache.create(super::resolve);
    this.slotCache =
        new SlotCache(
            slotCacheOptions,
            SUCCEED_CACHE_EXPIRATION_TIME,
            FAIL_CACHE_EXPIRATION_TIME,
            executorService,
            sweepScheduler,
            instantSource,
            localFileUtil,
            super::resolve);
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
    this.localCache = new LocalCache(executorService, instantSource, super::resolve);
    this.persistentResolvedFileCache = persistentResolvedFileCache;
    this.slotCache = null;
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

  /**
   * Adds the version of the file, if supported by the resolver, to the parameters of the resolve
   * source, so that the version is part of the cache key and a changed file is not served from the
   * cache. Removes any existing {@link #PARAM_FILE_VERSION} parameter if the version is empty or
   * cannot be retrieved (e.g., on recursive retries in {@link #internalResolve}).
   */
  private ResolveSource withFileVersion(ResolveSource resolveSource) throws InterruptedException {
    Optional<String> version = Optional.empty();
    try {
      version = getVersion(resolveSource);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to get version for %s. Fall back to caching without file version.",
          resolveSource);
    }
    if (version.isEmpty() && !resolveSource.parameters().containsKey(PARAM_FILE_VERSION)) {
      return resolveSource;
    }
    Map<String, String> parameters = new HashMap<>(resolveSource.parameters());
    if (version.isPresent()) {
      parameters.put(PARAM_FILE_VERSION, version.get());
    } else {
      parameters.remove(PARAM_FILE_VERSION);
    }
    return ResolveSource.create(
        resolveSource.path(),
        resolveSource.tag(),
        ImmutableMap.copyOf(parameters),
        resolveSource.targetDir(),
        resolveSource.tmpDir());
  }

  @SuppressWarnings("Interruption")
  private Optional<ResolveResult> internalResolve(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException {
    if (persistentResolvedFileCache != null && usePersistentCache(resolveSource)) {
      Optional<Checksum> checksum = Optional.empty();
      try {
        checksum = getChecksum(resolveSource);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to get checksum for %s. Fall back to non-persistent cache.", resolveSource);
      }
      if (checksum.isPresent()) {
        return persistentResolvedFileCache.getCachedResolveResult(resolveSource, checksum.get());
      }
    }
    if (slotCache != null) {
      return resolveThroughSlot(slotCache, resolveSource);
    }
    resolveSource = withFileVersion(resolveSource);
    CachedResolveResult cachedResult = localCache.getCachedResolveResult(resolveSource);
    if (!cachedResult.isCachedData()) {
      return getOwnResolveResult(cachedResult.cachedResult());
    } else {
      logger.atInfo().log("%s has been resolved before. Use the cached value.", resolveSource);
      // If already cached, use cached future's result.
      Optional<ResolveResult> optionalResolveResult;
      try {
        optionalResolveResult =
            cachedResult.cachedResult().get(getResolveTimeout().toMillis(), MILLISECONDS);
      } catch (CancellationException e) {
        logger.atInfo().log(
            "Previous resolve process for %s was cancelled. Need to re-resolve.", resolveSource);
        localCache.removeItemIfValueMatch(resolveSource, cachedResult.cachedResult());
        return internalResolve(resolveSource);
      } catch (ExecutionException e) {
        if ((e.getCause() instanceof MobileHarnessException
                && ((MobileHarnessException) e.getCause()).getErrorId()
                    == BasicErrorId.RESOLVE_FILE_TIMEOUT)
            || MoreThrowables.isInterruption(e.getCause())) {
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
        // Interrupt the long-running resolution task because it is considered as a zombie future.
        cachedResult.cachedResult().cancel(/* mayInterruptIfRunning= */ true);
        localCache.removeItemIfValueMatch(resolveSource, cachedResult.cachedResult());
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
              // Whether thisPath holds the cached file.
              boolean copied;
              try {
                localFileUtil.prepareDir(PathUtil.dirname(thisPath));
                // Hard links can be used for read-only archive/package files so multiple jobs
                // sharing the same resolved file share the same inode and page cache (and keep the
                // file alive even if the first job cleans up its run directory), while other files
                // and directories are copied to avoid in-place mutation side effects.
                if (LINKABLE_FILE_SUFFIX.contains(
                        Ascii.toLowerCase(Files.getFileExtension(thisPath)))
                    && !localFileUtil.isDirExist(cachedResolvedFile.path())) {
                  try {
                    localFileUtil.hardLinkFile(cachedResolvedFile.path(), thisPath);
                    copied = true;
                  } catch (MobileHarnessException e) {
                    // Hard links may fail across filesystems or due to permissions. If the cached
                    // file is gone or a concurrent resolve already created thisPath, let the outer
                    // catch handle it instead of falling back to copy.
                    if (!localFileUtil.isFileExist(cachedResolvedFile.path())
                        || localFileUtil.isFileOrDirExist(thisPath)) {
                      throw e;
                    }
                    logger.atInfo().withCause(e).log(
                        "Failed to hard link %s to %s. Fall back to copy.",
                        cachedResolvedFile.path(), thisPath);
                    copied = copyCachedFileOrDir(cachedResolvedFile.path(), thisPath);
                  }
                } else {
                  copied = copyCachedFileOrDir(cachedResolvedFile.path(), thisPath);
                }
              } catch (MobileHarnessException e) {
                // There may be race condition. The cached file is removed after first check.
                // So check again.
                if (localFileUtil.isFileOrDirExist(cachedResolvedFile.path())
                    && localFileUtil.getFileOrDirSize(cachedResolvedFile.path())
                        == localFileUtil.getFileOrDirSize(thisPath)) {
                  logger.atInfo().withCause(e).log(
                      "Failed to copy or link file because of two concurrent copies to same"
                          + " destination. It's acceptable because the former copy succeeds.");
                  copied = true;
                } else {
                  logger.atInfo().withCause(e).log(
                      "Previous cached resolved file for %s doesn't exist any more. Need to"
                          + " re-resolve.",
                      resolveSource);
                  copied = false;
                }
              }
              if (!copied) {
                // If any file in the cached result doesn't exist any more, retire cached value
                // and re-resolve.
                localCache.removeItemIfValueMatch(resolveSource, cachedResult.cachedResult());
                // Remove already copied file.
                for (ResolvedFile resolvedFile : thisResolvedFiles) {
                  localFileUtil.removeFileOrDir(resolvedFile.path());
                }
                // A directory copied in part, by a copy that failed or not, would pass for a
                // finished copy when the file is resolved again and another job has cached it by
                // then. While the cached directory is there, thisPath may instead belong to a
                // concurrent copy to the same destination.
                if (localFileUtil.isDirExist(thisPath)
                    && !localFileUtil.isDirExist(cachedResolvedFile.path())) {
                  localFileUtil.removeFileOrDir(thisPath);
                }
                return internalResolve(resolveSource);
              }
            }
            thisResolvedFiles.add(
                ResolvedFile.create(
                    thisPath,
                    cachedResolvedFile.fingerprint().orElse(null),
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

  /**
   * Returns the result of the resolution this thread started, which it cancels if it stops waiting
   * for it.
   */
  @SuppressWarnings("Interruption")
  private static Optional<ResolveResult> getOwnResolveResult(
      Future<Optional<ResolveResult>> resolveResult)
      throws MobileHarnessException, InterruptedException {
    try {
      return resolveResult.get();
    } catch (CancellationException e) {
      // This thread created the task. If cancelled, we want to cancel the background task to
      // avoid zombie futures.
      resolveResult.cancel(/* mayInterruptIfRunning= */ true);
      throw new MobileHarnessException(
          BasicErrorId.RESOLVE_FILE_GENERIC_ERROR, "Downloading was cancelled", e);
    } catch (InterruptedException e) {
      // This thread created the task. If interrupted, we want to cancel the background task to
      // avoid zombie futures.
      resolveResult.cancel(/* mayInterruptIfRunning= */ true);
      Thread.currentThread().interrupt();
      throw new MobileHarnessException(
          BasicErrorId.RESOLVE_FILE_GENERIC_ERROR, "Downloading is interrupted", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof MobileHarnessException mhe) {
        throw mhe;
      } else if (e.getCause() instanceof InterruptedException interruptedException) {
        throw interruptedException;
      } else if (e.getCause() instanceof Error error) {
        throw error;
      } else if (e.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new AssertionError("Should not happen.", e);
    }
  }

  /**
   * Copies a cached resolved file or directory to {@code thisPath}, which must not exist, and
   * returns whether {@code thisPath} holds the copy. A directory copied only in part is left for
   * the caller to remove.
   *
   * <p>{@link LocalFileUtil#copyFileOrDir(String, String)} skips whatever vanishes while it runs
   * instead of failing, and the job that resolved the cached files may remove them at any time. A
   * partial copy of a directory whose removal has not finished when the copy ends goes undetected.
   */
  private boolean copyCachedFileOrDir(String cachedPath, String thisPath)
      throws MobileHarnessException, InterruptedException {
    if (localFileUtil.isFileOrDirExist(cachedPath)) {
      localFileUtil.copyFileOrDir(cachedPath, thisPath);
      // A file is copied whole or not at all, but a directory can be copied in part, which shows
      // only in its source being gone.
      if (localFileUtil.isFileOrDirExist(thisPath)
          && (!localFileUtil.isDirExist(thisPath) || localFileUtil.isDirExist(cachedPath))) {
        return true;
      }
    }
    logger.atInfo().log(
        "Cached resolved file %s is gone, so it was not copied to %s in full. Need to re-resolve.",
        cachedPath, thisPath);
    return false;
  }

  /**
   * Resolves {@code resolveSource} into a slot of {@code slotCache}, or waits for the caller that
   * is doing so, and returns the files as links or copies in the directories of {@code
   * resolveSource}.
   *
   * <p>No caller's directory backs what another caller reads, so unlike in {@link #internalResolve}
   * a cached file cannot vanish while it is linked or copied.
   */
  @SuppressWarnings("Interruption")
  private Optional<ResolveResult> resolveThroughSlot(
      SlotCache slotCache, ResolveSource originalResolveSource)
      throws MobileHarnessException, InterruptedException {
    while (true) {
      ResolveSource resolveSource = withFileVersion(originalResolveSource);
      SlotCache.Lookup lookup = slotCache.get(resolveSource);
      SlotCache.Entry entry = lookup.entry();
      Optional<ResolveResult> slotResult;
      if (lookup.isNew()) {
        try {
          slotResult = getOwnResolveResult(entry.future());
        } catch (MobileHarnessException | InterruptedException | RuntimeException | Error e) {
          if (entry.future().isCancelled()
              || MoreThrowables.isInterruption(e)
              || (e instanceof MobileHarnessException mhe
                  && mhe.getErrorId() == BasicErrorId.RESOLVE_FILE_TIMEOUT)) {
            slotCache.retire(entry);
          }
          throw e;
        }
      } else {
        logger.atInfo().log("%s has been resolved before. Use the cached value.", resolveSource);
        try {
          slotResult = entry.future().get(getResolveTimeout().toMillis(), MILLISECONDS);
        } catch (CancellationException e) {
          logger.atInfo().log(
              "Previous resolve process for %s was cancelled. Need to re-resolve.", resolveSource);
          slotCache.retire(entry);
          continue;
        } catch (ExecutionException e) {
          boolean timedOutOrInterrupted =
              MoreThrowables.isInterruption(e.getCause())
                  || (e.getCause() instanceof MobileHarnessException mhe
                      && mhe.getErrorId() == BasicErrorId.RESOLVE_FILE_TIMEOUT);
          if (!timedOutOrInterrupted) {
            throw new MobileHarnessException(
                BasicErrorId.RESOLVE_FILE_CACHED_EXCEPTION_ERROR,
                String.format(
                    "Previous resolve process for %s did not succeed because of exception.",
                    resolveSource),
                e);
          }
          logger.atInfo().log(
              "Previous resolve process for %s did not succeed because of timeout or interruption."
                  + " Need to re-resolve.",
              resolveSource);
          slotCache.retire(entry);
          continue;
        } catch (TimeoutException e) {
          // Interrupt the long-running resolution task because it is considered as a zombie future.
          entry.future().cancel(/* mayInterruptIfRunning= */ true);
          slotCache.retire(entry);
          throw new MobileHarnessException(
              BasicErrorId.RESOLVE_FILE_TIMEOUT,
              String.format("Timeout while resolving file %s", resolveSource),
              e);
        }
      }
      if (slotResult.isEmpty()) {
        return slotResult;
      }
      Optional<ResolveResult> result =
          copyOutOfSlot(slotCache, entry, slotResult.get(), resolveSource, lookup.isNew());
      if (result.isPresent()) {
        return result;
      }
    }
  }

  /**
   * Returns {@code slotResult} for {@code resolveSource}, with every file in the slot of {@code
   * entry} linked or copied into the directories of {@code resolveSource}. Returns empty if the
   * slot is not kept any more or a previously cached file is missing from the slot.
   */
  private Optional<ResolveResult> copyOutOfSlot(
      SlotCache slotCache,
      SlotCache.Entry entry,
      ResolveResult slotResult,
      ResolveSource resolveSource,
      boolean isNew)
      throws MobileHarnessException, InterruptedException {
    boolean inSlot =
        slotResult.resolvedFiles().stream()
            .anyMatch(file -> entry.pathFor(resolveSource, file.path()).isPresent());
    if (!inSlot) {
      // The resolver fetched nothing and names the files where they are.
      return Optional.of(
          ResolveResult.of(slotResult.resolvedFiles(), slotResult.properties(), resolveSource));
    }
    if (!entry.acquire()) {
      logger.atInfo().log(
          "The cache entry of %s was retired before its files were used. Need to re-resolve.",
          resolveSource);
      return Optional.empty();
    }
    boolean succeeded = false;
    List<String> createdPaths = new ArrayList<>();
    try {
      ImmutableList.Builder<ResolvedFile> files = ImmutableList.builder();
      for (ResolvedFile slotFile : slotResult.resolvedFiles()) {
        Optional<String> path = entry.pathFor(resolveSource, slotFile.path());
        if (path.isPresent() && !copyFromSlot(slotFile.path(), path.get(), createdPaths)) {
          slotCache.retire(entry);
          if (isNew) {
            throw new MobileHarnessException(
                BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND,
                String.format(
                    "Resolved file %s for %s does not exist in cache slot.",
                    slotFile.path(), resolveSource));
          }
          logger.atWarning().log(
              "Cached resolved file %s for %s is missing from slot. Retiring entry and"
                  + " re-resolving.",
              slotFile.path(), resolveSource);
          return Optional.empty();
        }
        files.add(
            ResolvedFile.create(
                path.orElse(slotFile.path()),
                slotFile.fingerprint().orElse(null),
                slotFile.spec().orElse(null)));
      }
      ResolveResult result =
          ResolveResult.of(files.build(), slotResult.properties(), resolveSource);
      logger.atInfo().log("Resolved result: %s", result);
      succeeded = true;
      return Optional.of(result);
    } finally {
      try {
        if (!succeeded) {
          removeCreatedFiles(createdPaths);
        }
      } finally {
        entry.release();
      }
    }
  }

  private enum CopyOutcome {
    ALREADY_EXISTS,
    CREATED,
    SOURCE_MISSING,
  }

  /**
   * Puts the file or directory at {@code slotPath} at {@code path}, recording newly created file
   * paths in {@code createdPaths}. Returns {@code false} if {@code slotPath} (or a child inside it)
   * is missing on disk.
   *
   * <p>Directories are copied entry-by-entry with per-file locking so a concurrent source targeting
   * a file inside the same directory neither causes nested copying nor races on that file.
   */
  private boolean copyFromSlot(String slotPath, String path, List<String> createdPaths)
      throws MobileHarnessException, InterruptedException {
    String normalizedSlotPath = normalizeLocalPath(slotPath);
    String normalizedPath = normalizeLocalPath(path);
    if (!existsNoFollow(normalizedSlotPath)) {
      return false;
    }
    if (localFileUtil.isDirExist(normalizedSlotPath) && !isSymbolicLink(normalizedSlotPath)) {
      return copyDirectory(normalizedSlotPath, normalizedPath, createdPaths);
    }
    CopyOutcome outcome = linkOrCopyFile(normalizedSlotPath, normalizedPath);
    if (outcome == CopyOutcome.CREATED) {
      createdPaths.add(normalizedPath);
    }
    return outcome != CopyOutcome.SOURCE_MISSING;
  }

  private boolean copyDirectory(
      String normalizedSlotDir, String normalizedDestDir, List<String> createdPaths)
      throws MobileHarnessException, InterruptedException {
    if (!localFileUtil.isDirExist(normalizedSlotDir)) {
      return false;
    }
    File[] children;
    try {
      children = localFileUtil.listFilesOrDirs(normalizedSlotDir);
    } catch (MobileHarnessException e) {
      if (!localFileUtil.isDirExist(normalizedSlotDir)) {
        return false;
      }
      throw e;
    }
    localFileUtil.prepareDir(normalizedDestDir);
    for (File child : children) {
      String childSrc = normalizeLocalPath(child.getPath());
      String childDest = PathUtil.join(normalizedDestDir, child.getName());
      if (child.isDirectory() && !isSymbolicLink(childSrc)) {
        if (!copyDirectory(childSrc, childDest, createdPaths)) {
          return false;
        }
      } else {
        CopyOutcome outcome = linkOrCopyFile(childSrc, childDest);
        if (outcome == CopyOutcome.CREATED) {
          createdPaths.add(childDest);
        } else if (outcome == CopyOutcome.SOURCE_MISSING) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Puts the file at {@code normalizedSlotPath} at {@code normalizedPath}, unless something is
   * already there.
   *
   * <p>As in {@link #internalResolve}, regular files with suffixes in {@link #LINKABLE_FILE_SUFFIX}
   * are hard-linked, and the rest are copied with file attributes preserved.
   */
  private CopyOutcome linkOrCopyFile(String normalizedSlotPath, String normalizedPath)
      throws MobileHarnessException, InterruptedException {
    // Two sources of one job (or a directory and a file inside it) can target the same path.
    Lock lock = outOfSlotLocks.get(normalizedPath);
    lock.lockInterruptibly();
    try {
      if (!existsNoFollow(normalizedSlotPath)) {
        return CopyOutcome.SOURCE_MISSING;
      }
      if (existsNoFollow(normalizedPath)) {
        return CopyOutcome.ALREADY_EXISTS;
      }
      localFileUtil.prepareDir(PathUtil.dirname(normalizedPath));
      if (!isSymbolicLink(normalizedSlotPath)
          && LINKABLE_FILE_SUFFIX.contains(
              Ascii.toLowerCase(Files.getFileExtension(normalizedPath)))) {
        try {
          localFileUtil.hardLinkFile(normalizedSlotPath, normalizedPath);
          return CopyOutcome.CREATED;
        } catch (MobileHarnessException e) {
          if (!existsNoFollow(normalizedSlotPath)) {
            return CopyOutcome.SOURCE_MISSING;
          }
          logger.atInfo().withCause(e).log(
              "Failed to hard link %s to %s. Fall back to copy.",
              normalizedSlotPath, normalizedPath);
        }
      }
      copyWithCleanup(normalizedSlotPath, normalizedPath);
      return existsNoFollow(normalizedPath) ? CopyOutcome.CREATED : CopyOutcome.SOURCE_MISSING;
    } finally {
      lock.unlock();
    }
  }

  private boolean existsNoFollow(String path) {
    return localFileUtil.isFileOrDirExist(path) || isSymbolicLink(path);
  }

  private boolean isSymbolicLink(String path) {
    try {
      return localFileUtil.isSymbolicLink(Path.of(path));
    } catch (InvalidPathException e) {
      return false;
    }
  }

  private void copyWithCleanup(String src, String dst)
      throws MobileHarnessException, InterruptedException {
    try {
      localFileUtil.copyFileOrDir(src, dst, /* copyAttributes= */ true);
    } catch (MobileHarnessException | InterruptedException e) {
      // What a copy cut short leaves behind would pass for the whole file the next time the
      // path is asked for. Callables.runAll clears the interrupt flag around rm -rf.
      MoreThrowables.runAndSuppressException(
          e,
          () -> Callables.runAll((MobileHarnessRunnable) () -> localFileUtil.removeFileOrDir(dst)));
      throw e;
    }
  }

  private void removeCreatedFiles(List<String> paths) {
    for (String path : paths) {
      Lock lock = outOfSlotLocks.get(path);
      lock.lock();
      try {
        Callables.runAll((MobileHarnessRunnable) () -> localFileUtil.removeFileOrDir(path));
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log("Failed to remove partial destination %s.", path);
      } catch (InterruptedException e) {
        logger.atWarning().withCause(e).log("Interrupted removing partial destination %s.", path);
        Thread.currentThread().interrupt();
      } finally {
        lock.unlock();
      }
    }
  }

  private static String normalizeLocalPath(String path) {
    try {
      return Path.of(path).normalize().toString();
    } catch (InvalidPathException e) {
      return PathUtil.removeExtraneousSlashes(path);
    }
  }

  @Override
  protected Set<ResolveResult> preBatchProcess(List<ResolveSource> resolveSources)
      throws MobileHarnessException, InterruptedException {
    if (slotCache != null) {
      // A batch is resolved into the directories of its caller, which no other caller may read
      // from, so each file is resolved on its own into a slot instead.
      return ImmutableSet.of();
    }
    Set<ResolveResult> resolveResults = super.preBatchProcess(resolveSources);
    for (ResolveResult resolveResult : resolveResults) {
      localCache.addResolveResult(resolveResult, instantSource.instant());
    }
    return resolveResults;
  }
}
