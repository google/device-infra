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

package com.google.devtools.mobileharness.platform.android.xts.common.util;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.Striped;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.shared.util.base.BinaryPrefix;
import com.google.devtools.mobileharness.shared.util.base.DataSize;
import com.google.devtools.mobileharness.shared.util.base.SizeUnit;
import com.google.devtools.mobileharness.shared.util.concurrent.Callables;
import com.google.devtools.mobileharness.shared.util.concurrent.MobileHarnessRunnable;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Shared host cache for unzipped and chunk-restored xTS packages across ATS server sessions and lab
 * worker test runs.
 *
 * <p>Avoids unpacking 20–33 GB xTS archives into per-session or per-test directories repeatedly
 * when sequential or concurrent runs use the same xTS package. Enforces active-session reference
 * pinning, single-flight striped locking, TTL expiration ({@code --ats_xts_unzip_cache_ttl}), and
 * LRU entry-count / disk-size eviction ({@code --ats_xts_unzip_cache_max_entries}, {@code
 * --ats_xts_unzip_cache_max_size_gb}).
 */
@Singleton
public final class XtsPackageUnzipCache {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Duration DEFAULT_UNZIP_TIMEOUT = Duration.ofHours(1);
  private static final int CONTENT_SAMPLE_BYTES = 64 * 1024;
  private static final int KEY_LOCK_STRIPES = 64;

  /**
   * Process-wide cache states keyed by {@code cacheRootDir} so that sessions instantiated across
   * separate per-session Guice injectors in OLC server (or tests in Lab Server) share the same
   * cache state, while unit tests with distinct {@code public_dir} temp folders remain isolated.
   */
  private static final ConcurrentHashMap<Path, CacheState> CACHE_STATES_BY_ROOT =
      new ConcurrentHashMap<>();

  /** Resolves the xTS type (e.g. {@code cts}) from an unpacked xTS root directory. */
  @FunctionalInterface
  public interface XtsTypeProvider {
    String getXtsType(String xtsRootDir);
  }

  /**
   * Creates a shared xTS package unzip cache rooted at {@link DirUtil#getXtsWorkerUnzipCacheDir}.
   */
  public static XtsPackageUnzipCache forWorker(
      LocalFileUtil localFileUtil,
      XtsChunkedTestCasesRestorer xtsChunkedTestCasesRestorer,
      Clock clock,
      Duration unzipTimeout) {
    return new XtsPackageUnzipCache(
        localFileUtil,
        xtsChunkedTestCasesRestorer,
        clock,
        DirUtil::getXtsWorkerUnzipCacheDir,
        unzipTimeout);
  }

  private final LocalFileUtil localFileUtil;
  private final XtsChunkedTestCasesRestorer xtsChunkedTestCasesRestorer;
  private final Clock clock;
  private final Supplier<String> cacheRootDirSupplier;
  private final Duration unzipTimeout;

  @Inject
  public XtsPackageUnzipCache(
      LocalFileUtil localFileUtil,
      XtsChunkedTestCasesRestorer xtsChunkedTestCasesRestorer,
      Clock clock) {
    this(
        localFileUtil,
        xtsChunkedTestCasesRestorer,
        clock,
        DirUtil::getXtsUnzipCacheDir,
        DEFAULT_UNZIP_TIMEOUT);
  }

  private XtsPackageUnzipCache(
      LocalFileUtil localFileUtil,
      XtsChunkedTestCasesRestorer xtsChunkedTestCasesRestorer,
      Clock clock,
      Supplier<String> cacheRootDirSupplier,
      Duration unzipTimeout) {
    this.localFileUtil = localFileUtil;
    this.xtsChunkedTestCasesRestorer = xtsChunkedTestCasesRestorer;
    this.clock = clock;
    this.cacheRootDirSupplier = cacheRootDirSupplier;
    this.unzipTimeout = unzipTimeout;
  }

  /**
   * Returns true if a valid, non-expired unzipped directory for the given zip file and password is
   * currently present in the cache.
   */
  public boolean isCached(String zipFilePath, String password) {
    CacheState state = getCacheState(getCacheRootDir());
    CacheEntry entry = state.entries.get(computeCacheKey(zipFilePath, password));
    return entry != null
        && (entry.isInUse() || !isExpired(entry, clock.instant(), getTtl()))
        && localFileUtil.isDirExist(entry.cachedDirPath);
  }

  /**
   * Acquires a shared unzipped and chunk-restored xTS package for {@code sessionId}, linking {@code
   * targetDirPath} to the cached directory, pinning the cache entry until {@link #release} is
   * called, and returning the resolved xTS type.
   */
  @CanIgnoreReturnValue
  public String acquire(
      String sessionId,
      String zipFilePath,
      String targetDirPath,
      String password,
      XtsTypeProvider xtsTypeProvider)
      throws MobileHarnessException, InterruptedException {
    Path cacheRootDir = getCacheRootDir();
    CacheState state = getCacheState(cacheRootDir);
    ensureInitialized(state, cacheRootDir);

    String cacheKey = computeCacheKey(zipFilePath, password);
    drainPendingDeletions(state);
    evictExpiredEntries(state);

    int maxEntries = getMaxEntries();
    long maxSizeBytes = getMaxSizeBytes();
    if (!hasReusableEntry(state, cacheKey) && (maxEntries > 0 || maxSizeBytes > 0)) {
      evictForCapacity(
          state,
          maxEntries > 0 ? Math.max(0, maxEntries - 1) : -1,
          maxSizeBytes,
          /* excludeKey= */ cacheKey);
    }

    Lock keyLock = state.keyLocks.get(cacheKey);
    keyLock.lockInterruptibly();
    String resolvedXtsType;
    boolean coldMissUnpacked = false;
    try {
      Instant now = clock.instant();
      CacheEntry existing = state.entries.get(cacheKey);
      if (existing != null) {
        if (tryClaimIdleExpiredEntry(state, existing, now, getTtl())) {
          logger.atInfo().log(
              "Evicting expired cached unzipped xTS package [%s] at [%s] before acquire.",
              existing.cacheKey, existing.cachedDirPath);
          deleteEvictedDir(state, cacheKey, Path.of(existing.cachedDirPath));
        } else if (localFileUtil.isDirExist(existing.cachedDirPath)) {
          linkTargetDirToCache(existing.cachedDirPath, targetDirPath);
          bindSessionLease(state, existing, sessionId, now);
          touchCacheRootDir(cacheRootDir);
          logger.atInfo().log(
              "Reusing cached unzipped xTS package for session [%s]: zip=[%s], cacheDir=[%s],"
                  + " activeRefs=[%d]",
              sessionId, zipFilePath, existing.cachedDirPath, existing.activeRefCount());
          return existing.xtsType;
        } else {
          synchronized (state.evictionLock) {
            state.entries.remove(cacheKey, existing);
          }
        }
      }

      String cachedDirPath = cacheRootDir.resolve(cacheKey).toString();
      state.pendingDeletions.remove(cacheKey);
      if (localFileUtil.isFileOrDirExist(cachedDirPath)) {
        localFileUtil.removeFileOrDir(cachedDirPath);
      }
      localFileUtil.prepareDir(cachedDirPath);

      boolean success = false;
      try {
        logger.atInfo().log(
            "Unzipping xTS package [%s] into shared cache [%s] for session [%s]...",
            zipFilePath, cachedDirPath, sessionId);
        localFileUtil.unzipFile(zipFilePath, cachedDirPath, unzipTimeout, password);
        linkTargetDirToCache(cachedDirPath, targetDirPath);
        resolvedXtsType = xtsTypeProvider.getXtsType(targetDirPath);
        xtsChunkedTestCasesRestorer.restoreTestCases(cachedDirPath, resolvedXtsType);

        long sizeBytes = calculateEntrySizeBytes(cachedDirPath, zipFilePath);
        Instant completedTime = clock.instant();
        CacheEntry newEntry =
            new CacheEntry(cacheKey, cachedDirPath, resolvedXtsType, sizeBytes, completedTime);
        synchronized (state.evictionLock) {
          state.entries.put(cacheKey, newEntry);
          bindSessionLeaseLocked(state, newEntry, sessionId, completedTime);
        }
        touchCacheRootDir(cacheRootDir);
        coldMissUnpacked = true;
        success = true;

        logger.atInfo().log(
            "Cached newly unzipped xTS package for session [%s]: zip=[%s], cacheDir=[%s],"
                + " sizeBytes=[%d], xtsType=[%s]",
            sessionId, zipFilePath, cachedDirPath, sizeBytes, resolvedXtsType);
      } finally {
        if (!success) {
          synchronized (state.evictionLock) {
            state.entries.remove(cacheKey);
          }
          cleanupFailedAcquire(cachedDirPath, targetDirPath);
        }
      }
    } finally {
      keyLock.unlock();
    }

    if (coldMissUnpacked && (maxEntries > 0 || maxSizeBytes > 0)) {
      // Enforce post-unzip capacity limits outside keyLock now that newEntry's actual unpacked size
      // is recorded, avoiding holding multiple stripe locks simultaneously.
      try {
        evictForCapacity(
            state, maxEntries > 0 ? maxEntries : -1, maxSizeBytes, /* excludeKey= */ cacheKey);
      } catch (InterruptedException e) {
        rollbackSessionOnInterrupt(state, sessionId, targetDirPath);
        throw e;
      }
    }
    return resolvedXtsType;
  }

  /**
   * Releases the session's lease on its cached xTS directory, removes the session's symlink at
   * {@code targetDirPath} without deleting the underlying shared cache entry, and triggers an
   * eviction sweep for any expired or over-capacity idle entries.
   */
  public void release(String sessionId, String targetDirPath)
      throws MobileHarnessException, InterruptedException {
    Path cacheRootDir = getCacheRootDir();
    CacheState state = getCacheState(cacheRootDir);
    Callables.runAll(
        (MobileHarnessRunnable) () -> localFileUtil.removeFileOrDir(Path.of(targetDirPath)),
        () -> {
          releaseSessionLease(state, sessionId);
          touchCacheRootDir(cacheRootDir);
        },
        this::evictIfNeeded);
  }

  /** Runs TTL expiration and LRU capacity eviction across all idle cache entries. */
  public void evictIfNeeded() throws InterruptedException {
    CacheState state = getCacheState(getCacheRootDir());
    drainPendingDeletions(state);
    evictExpiredEntries(state);
    int maxEntries = getMaxEntries();
    evictForCapacity(
        state, maxEntries > 0 ? maxEntries : -1, getMaxSizeBytes(), /* excludeKey= */ null);
  }

  private void touchCacheRootDir(Path cacheRootDir) {
    try {
      if (Files.exists(cacheRootDir)) {
        Files.setLastModifiedTime(cacheRootDir, FileTime.from(clock.instant()));
      }
    } catch (IOException e) {
      logger.atFine().withCause(e).log(
          "Unable to update lastModifiedTime on cache root dir [%s].", cacheRootDir);
    }
  }

  private boolean hasReusableEntry(CacheState state, String cacheKey) {
    CacheEntry entry = state.entries.get(cacheKey);
    return entry != null
        && (entry.isInUse() || !isExpired(entry, clock.instant(), getTtl()))
        && localFileUtil.isDirExist(entry.cachedDirPath);
  }

  private static void bindSessionLease(
      CacheState state, CacheEntry entry, String sessionId, Instant now) {
    synchronized (state.evictionLock) {
      bindSessionLeaseLocked(state, entry, sessionId, now);
    }
  }

  private static void bindSessionLeaseLocked(
      CacheState state, CacheEntry entry, String sessionId, Instant now) {
    entry.acquire(sessionId, now);
    String prevKey = state.sessionToCacheKey.put(sessionId, entry.cacheKey);
    if (prevKey != null && !prevKey.equals(entry.cacheKey)) {
      CacheEntry prevEntry = state.entries.get(prevKey);
      if (prevEntry != null) {
        prevEntry.release(sessionId, now);
      }
    }
  }

  private void releaseSessionLease(CacheState state, String sessionId) {
    synchronized (state.evictionLock) {
      String cacheKey = state.sessionToCacheKey.remove(sessionId);
      if (cacheKey != null) {
        CacheEntry entry = state.entries.get(cacheKey);
        if (entry != null) {
          entry.release(sessionId, clock.instant());
          logger.atInfo().log(
              "Released cached unzipped xTS package for session [%s]: cacheKey=[%s],"
                  + " remainingActiveRefs=[%d]",
              sessionId, cacheKey, entry.activeRefCount());
        }
      }
    }
  }

  private void rollbackSessionOnInterrupt(
      CacheState state, String sessionId, String targetDirPath) {
    releaseSessionLease(state, sessionId);
    boolean wasInterrupted = Thread.interrupted();
    try {
      localFileUtil.removeFileOrDir(Path.of(targetDirPath));
    } catch (MobileHarnessException | InterruptedException e) {
      logger.atWarning().withCause(e).log(
          "Failed to clean up target symlink [%s] after interrupted acquire.", targetDirPath);
    } finally {
      if (wasInterrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @VisibleForTesting
  String computeCacheKey(String zipFilePath, String password) {
    Path resolvedPath = resolveRealPathIfSymlink(Path.of(zipFilePath));
    String resolvedPathStr = resolvedPath.toString();

    long mtimeMillis = 0L;
    long fileSize = 0L;
    if (localFileUtil.isFileExist(resolvedPathStr)) {
      try {
        mtimeMillis = localFileUtil.getFileLastModifiedTime(resolvedPathStr).toEpochMilli();
      } catch (MobileHarnessException e) {
        logger.atFine().withCause(e).log(
            "Unable to read last modified time for [%s], using 0.", resolvedPathStr);
      }
      try {
        fileSize = localFileUtil.getFileSize(resolvedPathStr);
      } catch (MobileHarnessException e) {
        logger.atFine().withCause(e).log(
            "Unable to read file size for [%s], using 0.", resolvedPathStr);
      }
    }

    Path runDir = Path.of(DirUtil.getRunDir());
    String keyPathComponent = resolvedPathStr;
    String versionToken = String.valueOf(mtimeMillis);
    if (resolvedPath.startsWith(runDir)) {
      Path relativeToRunDir = runDir.relativize(resolvedPath);
      if (relativeToRunDir.getNameCount() >= 2) {
        // Strip the per-job <jobId> directory under mh_lab_run_files so copies of the same
        // resolved xTS zip across jobs on a remote worker share the same canonical path key.
        keyPathComponent = relativeToRunDir.subpath(1, relativeToRunDir.getNameCount()).toString();
      }
      // CacheFileResolver copies resolved files into each job's runFileDir without preserving
      // mtime, while cl/998461483 re-downloads files into runFileDir when replaced in place on the
      // ATS file server. Use a fast head+tail content sample digest (covering the zip header and
      // end-of-archive Central Directory) so unchanged copies hit the cache while replaced-in-place
      // archives immediately invalidate it.
      versionToken = computeFastContentSampleDigest(resolvedPath, fileSize).orElse(versionToken);
    }

    String rawKey = keyPathComponent + "|" + versionToken + "|" + fileSize + "|" + password;
    String baseName = MoreFiles.getNameWithoutExtension(resolvedPath);
    String keyHash = Hashing.sha256().hashString(rawKey, UTF_8).toString().substring(0, 32);
    return baseName.isEmpty() ? keyHash : baseName + "_" + keyHash;
  }

  private Path resolveRealPathIfSymlink(Path path) {
    if (localFileUtil.isSymbolicLink(path)) {
      try {
        return localFileUtil.getFileOrDirRealPath(path);
      } catch (MobileHarnessException e) {
        logger.atFine().withCause(e).log("Unable to resolve symlink target for [%s].", path);
      }
    }
    return path;
  }

  private Optional<String> computeFastContentSampleDigest(Path path, long fileSize) {
    if (fileSize <= 0L || !localFileUtil.isFileExist(path)) {
      return Optional.empty();
    }
    try {
      ByteSource fileBytes = MoreFiles.asByteSource(path);
      long headLen = Math.min(CONTENT_SAMPLE_BYTES, fileSize);
      long tailOffset = Math.max(headLen, fileSize - CONTENT_SAMPLE_BYTES);
      ByteSource sample =
          fileSize > CONTENT_SAMPLE_BYTES
              ? ByteSource.concat(
                  fileBytes.slice(0, headLen), fileBytes.slice(tailOffset, fileSize - tailOffset))
              : fileBytes.slice(0, headLen);
      return Optional.of(sample.hash(Hashing.sha256()).toString());
    } catch (IOException e) {
      logger.atFine().withCause(e).log("Unable to compute fast content sample for [%s].", path);
      return Optional.empty();
    }
  }

  @VisibleForTesting
  Path getCacheRootDir() {
    return Path.of(cacheRootDirSupplier.get());
  }

  @VisibleForTesting
  Optional<CacheEntry> getEntrySnapshot(String zipFilePath, String password) {
    CacheState state = getCacheState(getCacheRootDir());
    return Optional.ofNullable(state.entries.get(computeCacheKey(zipFilePath, password)));
  }

  @VisibleForTesting
  ImmutableList<CacheEntry> getAllEntrySnapshots() {
    return ImmutableList.sortedCopyOf(
        Comparator.comparing(CacheEntry::lastAccessTime),
        getCacheState(getCacheRootDir()).entries.values());
  }

  @VisibleForTesting
  long getTotalCachedSizeBytes() {
    return getTotalSizeBytes(getCacheState(getCacheRootDir()));
  }

  private static CacheState getCacheState(Path cacheRootDir) {
    return CACHE_STATES_BY_ROOT.computeIfAbsent(cacheRootDir, k -> new CacheState());
  }

  private void ensureInitialized(CacheState state, Path cacheRootDir)
      throws MobileHarnessException, InterruptedException {
    if (state.initialized.get()) {
      return;
    }
    state.initLock.lockInterruptibly();
    try {
      if (state.initialized.get()) {
        return;
      }
      if (localFileUtil.isDirExist(cacheRootDir)) {
        for (Path subDir : localFileUtil.listDirs(cacheRootDir)) {
          if (!state.entries.containsKey(subDir.getFileName().toString())) {
            logger.atInfo().log(
                "Removing orphaned xTS unzip cache directory on startup: %s", subDir);
            try {
              localFileUtil.removeFileOrDir(subDir);
            } catch (MobileHarnessException e) {
              logger.atWarning().withCause(e).log(
                  "Failed to remove orphaned xTS unzip cache directory: %s", subDir);
            }
          }
        }
      } else {
        localFileUtil.prepareDir(cacheRootDir);
      }
      state.initialized.set(true);
    } finally {
      state.initLock.unlock();
    }
  }

  private void linkTargetDirToCache(String cachedDirPath, String targetDirPath)
      throws MobileHarnessException, InterruptedException {
    localFileUtil.removeFileOrDir(Path.of(targetDirPath));
    localFileUtil.prepareParentDir(targetDirPath);
    localFileUtil.linkFileOrDir(cachedDirPath, targetDirPath);
  }

  private void cleanupFailedAcquire(String cachedDirPath, String targetDirPath) {
    try {
      Callables.runAll(
          (MobileHarnessRunnable) () -> localFileUtil.removeFileOrDir(Path.of(targetDirPath)),
          () -> localFileUtil.removeFileOrDir(Path.of(cachedDirPath)));
    } catch (MobileHarnessException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      logger.atWarning().withCause(e).log(
          "Failed to clean up after failed xTS unzip: target=[%s], cacheDir=[%s].",
          targetDirPath, cachedDirPath);
    }
  }

  private long calculateEntrySizeBytes(String cachedDirPath, String zipFilePath) {
    Path dirPath = Path.of(cachedDirPath);
    try {
      if (localFileUtil.isDirExist(dirPath)) {
        AtomicLong totalBytes = new AtomicLong();
        Files.walkFileTree(
            dirPath,
            new SimpleFileVisitor<Path>() {
              @Override
              public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                  totalBytes.addAndGet(attrs.size());
                }
                return FileVisitResult.CONTINUE;
              }
            });
        if (totalBytes.get() > 0L) {
          return totalBytes.get();
        }
      }
      if (localFileUtil.isFileExist(zipFilePath)) {
        return localFileUtil.getFileSize(zipFilePath);
      }
    } catch (IOException | MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to calculate size for cached xTS dir [%s], defaulting to 0.", cachedDirPath);
    }
    return 0L;
  }

  private void evictExpiredEntries(CacheState state) throws InterruptedException {
    Duration ttl = getTtl();
    if (ttl.isNegative() || ttl.isZero()) {
      return;
    }
    Instant now = clock.instant();
    for (CacheEntry candidate : ImmutableList.copyOf(state.entries.values())) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      if (!candidate.isInUse() && isExpired(candidate, now, ttl)) {
        tryEvictExpiredCandidate(state, candidate, now, ttl);
      }
    }
  }

  private void tryEvictExpiredCandidate(
      CacheState state, CacheEntry candidate, Instant now, Duration ttl)
      throws InterruptedException {
    Lock candidateLock = state.keyLocks.get(candidate.cacheKey);
    if (!candidateLock.tryLock()) {
      return;
    }
    try {
      if (!tryClaimIdleExpiredEntry(state, candidate, now, ttl)) {
        return;
      }
      logger.atInfo().log(
          "Evicting cached unzipped xTS package [%s] at [%s] (size=%d bytes, lastAccess=%s):"
              + " TTL (%s) expired",
          candidate.cacheKey,
          candidate.cachedDirPath,
          candidate.sizeBytes,
          candidate.lastAccessTime(),
          ttl);
      deleteEvictedDir(state, candidate.cacheKey, Path.of(candidate.cachedDirPath));
    } finally {
      candidateLock.unlock();
    }
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }
  }

  private static boolean tryClaimIdleExpiredEntry(
      CacheState state, CacheEntry candidate, Instant now, Duration ttl) {
    synchronized (state.evictionLock) {
      CacheEntry current = state.entries.get(candidate.cacheKey);
      if (current != candidate || current.isInUse() || !isExpired(current, now, ttl)) {
        return false;
      }
      state.entries.remove(candidate.cacheKey, candidate);
      state.pendingDeletions.put(candidate.cacheKey, Path.of(candidate.cachedDirPath));
      return true;
    }
  }

  private static boolean isExpired(CacheEntry entry, Instant now, Duration ttl) {
    return !ttl.isNegative()
        && !ttl.isZero()
        && Duration.between(entry.lastAccessTime(), now).compareTo(ttl) > 0;
  }

  private void evictForCapacity(
      CacheState state, int maxEntries, long maxSizeBytes, @Nullable String excludeKey)
      throws InterruptedException {
    removeEntriesWithMissingCachedDir(state);
    ImmutableList<CacheEntry> idleCandidates;
    synchronized (state.evictionLock) {
      idleCandidates =
          state.entries.values().stream()
              .filter(entry -> !entry.isInUse() && !entry.cacheKey.equals(excludeKey))
              .sorted(Comparator.comparing(CacheEntry::lastAccessTime))
              .collect(toImmutableList());
    }

    for (CacheEntry candidate : idleCandidates) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      synchronized (state.evictionLock) {
        if (!isOverCapacityLocked(state, maxEntries, maxSizeBytes)) {
          break;
        }
      }
      tryEvictCapacityCandidate(state, candidate, maxEntries, maxSizeBytes);
    }
  }

  private void tryEvictCapacityCandidate(
      CacheState state, CacheEntry candidate, int maxEntries, long maxSizeBytes)
      throws InterruptedException {
    Lock candidateLock = state.keyLocks.get(candidate.cacheKey);
    if (!candidateLock.tryLock()) {
      return;
    }
    try {
      String reason;
      synchronized (state.evictionLock) {
        CacheEntry current = state.entries.get(candidate.cacheKey);
        if (current != candidate
            || current.isInUse()
            || !isOverCapacityLocked(state, maxEntries, maxSizeBytes)) {
          return;
        }
        boolean overEntryLimit = maxEntries >= 0 && state.entries.size() > maxEntries;
        reason =
            overEntryLimit
                ? String.format(
                    "entry count (%d) exceeds max (%d)", state.entries.size(), maxEntries)
                : String.format(
                    "total size (%d bytes) exceeds max (%d bytes)",
                    getTotalSizeBytes(state), maxSizeBytes);
        state.entries.remove(candidate.cacheKey, candidate);
        state.pendingDeletions.put(candidate.cacheKey, Path.of(candidate.cachedDirPath));
      }
      logger.atInfo().log(
          "Evicting cached unzipped xTS package [%s] at [%s] (size=%d bytes, lastAccess=%s): %s",
          candidate.cacheKey,
          candidate.cachedDirPath,
          candidate.sizeBytes,
          candidate.lastAccessTime(),
          reason);
      deleteEvictedDir(state, candidate.cacheKey, Path.of(candidate.cachedDirPath));
    } finally {
      candidateLock.unlock();
    }
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }
  }

  /**
   * Drops entries whose cached directories were deleted outside the cache, so stale entries are
   * removed first and never cause a valid idle entry to be evicted for capacity.
   */
  private void removeEntriesWithMissingCachedDir(CacheState state) {
    for (CacheEntry entry : ImmutableList.copyOf(state.entries.values())) {
      if (localFileUtil.isDirExist(entry.cachedDirPath)) {
        continue;
      }
      synchronized (state.evictionLock) {
        if (state.entries.remove(entry.cacheKey, entry)) {
          logger.atInfo().log(
              "Cached unzipped xTS package directory [%s] is missing on disk; dropping entry [%s].",
              entry.cachedDirPath, entry.cacheKey);
        }
      }
    }
  }

  private static boolean isOverCapacityLocked(CacheState state, int maxEntries, long maxSizeBytes) {
    boolean overEntryLimit = maxEntries >= 0 && state.entries.size() > maxEntries;
    boolean overSizeLimit = maxSizeBytes > 0 && getTotalSizeBytes(state) > maxSizeBytes;
    return overEntryLimit || overSizeLimit;
  }

  private void drainPendingDeletions(CacheState state) throws InterruptedException {
    for (Entry<String, Path> pending : ImmutableList.copyOf(state.pendingDeletions.entrySet())) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      String pendingKey = pending.getKey();
      Path pendingPath = pending.getValue();
      Lock candidateLock = state.keyLocks.get(pendingKey);
      if (!candidateLock.tryLock()) {
        continue;
      }
      try {
        if (!state.entries.containsKey(pendingKey)
            && pendingPath.equals(state.pendingDeletions.get(pendingKey))) {
          deleteEvictedDir(state, pendingKey, pendingPath);
        } else {
          state.pendingDeletions.remove(pendingKey, pendingPath);
        }
      } finally {
        candidateLock.unlock();
      }
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
    }
  }

  private void deleteEvictedDir(CacheState state, String cacheKey, Path dirPath) {
    boolean wasInterrupted = Thread.interrupted();
    try {
      localFileUtil.removeFileOrDir(dirPath);
      state.pendingDeletions.remove(cacheKey, dirPath);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to remove evicted xTS unzip cache directory: %s", dirPath);
    } catch (InterruptedException e) {
      wasInterrupted = true;
      logger.atWarning().withCause(e).log(
          "Interrupted while removing evicted xTS unzip cache directory [%s]; queued for retry.",
          dirPath);
    } finally {
      if (wasInterrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static long getTotalSizeBytes(CacheState state) {
    long total = 0L;
    for (CacheEntry entry : state.entries.values()) {
      total += entry.sizeBytes;
    }
    return total;
  }

  private static int getMaxEntries() {
    return Flags.atsXtsUnzipCacheMaxEntries.getNonNull();
  }

  private static long getMaxSizeBytes() {
    long maxSizeGb = Flags.atsXtsUnzipCacheMaxSizeGb.getNonNull();
    return maxSizeGb > 0
        ? DataSize.of(maxSizeGb, BinaryPrefix.GIBI, SizeUnit.BYTES).toBytes()
        : -1L;
  }

  private static Duration getTtl() {
    return Flags.atsXtsUnzipCacheTtl.getNonNull();
  }

  private static final class CacheState {
    final ConcurrentHashMap<String, CacheEntry> entries = new ConcurrentHashMap<>();
    final Striped<Lock> keyLocks = Striped.lock(KEY_LOCK_STRIPES);
    final ConcurrentHashMap<String, String> sessionToCacheKey = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Path> pendingDeletions = new ConcurrentHashMap<>();
    final ReentrantLock initLock = new ReentrantLock();
    final Object evictionLock = new Object();
    final AtomicBoolean initialized = new AtomicBoolean(false);
  }

  @VisibleForTesting
  static final class CacheEntry {
    private final String cacheKey;
    private final String cachedDirPath;
    private final String xtsType;
    private final long sizeBytes;
    private final Set<String> activeSessionIds = ConcurrentHashMap.newKeySet();
    private volatile Instant lastAccessTime;

    CacheEntry(
        String cacheKey,
        String cachedDirPath,
        String xtsType,
        long sizeBytes,
        Instant lastAccessTime) {
      this.cacheKey = cacheKey;
      this.cachedDirPath = cachedDirPath;
      this.xtsType = xtsType;
      this.sizeBytes = sizeBytes;
      this.lastAccessTime = lastAccessTime;
    }

    String cachedDirPath() {
      return cachedDirPath;
    }

    Instant lastAccessTime() {
      return lastAccessTime;
    }

    int activeRefCount() {
      return activeSessionIds.size();
    }

    ImmutableSet<String> activeSessionIds() {
      return ImmutableSet.copyOf(activeSessionIds);
    }

    boolean isInUse() {
      return !activeSessionIds.isEmpty();
    }

    void acquire(String sessionId, Instant now) {
      activeSessionIds.add(sessionId);
      this.lastAccessTime = now;
    }

    void release(String sessionId, Instant now) {
      this.lastAccessTime = now;
      activeSessionIds.remove(sessionId);
    }
  }
}
