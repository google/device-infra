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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * Manages a local disk cache for ATS file server files, using SHA256 as key and LRU for eviction.
 */
class AtsFileCacheManager {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final long BYTES_PER_MB = 1024L * 1024;
  private final ReadWriteLock cacheIoLock = new ReentrantReadWriteLock();
  private long currentCacheSizeBytes = 0L;

  private final LocalFileUtil localFileUtil;

  private static volatile AtsFileCacheManager instance;
  private static final Object INSTANCE_LOCK = new Object();

  private final Path cacheDir;
  private final boolean isEnabled;

  /** Returns the singleton instance of AtsFileCacheManager. */
  public static AtsFileCacheManager getInstance() {
    AtsFileCacheManager result = instance;
    if (result == null) {
      synchronized (INSTANCE_LOCK) {
        result = instance;
        if (result == null) {
          instance = result = new AtsFileCacheManager(new LocalFileUtil());
        }
      }
    }
    return result;
  }

  @VisibleForTesting
  AtsFileCacheManager(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
    long cacheSizeMb = Flags.instance().atsFileResolverCacheSizeMb.getNonNull();
    String cacheDirStr = Flags.instance().atsFileResolverCacheDir.get();

    boolean enabled = false;
    Path path = null;

    if (!Flags.instance().enableAtsFileResolverCache.getNonNull()
        || cacheSizeMb <= 0
        || Strings.isNullOrEmpty(cacheDirStr)) {
      logger.atFine().log(
          "ATS file cache is disabled: enabled=%b, size=%dMB, dir='%s'.",
          Flags.instance().enableAtsFileResolverCache.getNonNull(), cacheSizeMb, cacheDirStr);
      enabled = false;
      path = null;
    } else {
      path = Path.of(cacheDirStr);
      try {
        localFileUtil.prepareDir(path);
        logger.atInfo().log(
            "ATS file cache is enabled: size=%dMB, dir='%s'.", cacheSizeMb, cacheDirStr);
        enabled = true;
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "ATS file cache disabled: Failed to prepare cache directory %s.", path);
        enabled = false;
        path = null;
      }
    }
    this.isEnabled = enabled;
    this.cacheDir = path;
    if (this.isEnabled) {
      initializeCacheSize();
    }
  }

  @VisibleForTesting
  static void resetInstanceForTesting() {
    instance = null;
  }

  private void initializeCacheSize() {
    if (!isEnabled) {
      this.currentCacheSizeBytes = 0L;
      return;
    }
    if (!Files.exists(cacheDir)) {
      this.currentCacheSizeBytes = 0L;
      return;
    }
    try (Stream<Path> stream = Files.list(cacheDir)) {
      this.currentCacheSizeBytes =
          stream.filter(Files::isRegularFile).mapToLong(this::getFileSize).sum();
      logger.atInfo().log("Initialized cache size from disk: %d bytes", this.currentCacheSizeBytes);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to scan cache directory %s to initialize size. Assuming size is 0.", cacheDir);
      this.currentCacheSizeBytes = 0L;
    }
  }

  /**
   * Copies the file from cache to the destination if it exists in cache.
   *
   * <p>If the file exists, its last modified time is updated and it's copied to the destination.
   *
   * @param sha256 The SHA256 hash of the file to retrieve from cache.
   * @param destination The path to copy the cached file to.
   * @return true if the file was found in cache and copied successfully, false otherwise.
   * @throws MobileHarnessException if the file copy fails.
   * @throws InterruptedException if the file copy is interrupted.
   */
  public boolean copyFromCacheIfPresent(String sha256, Path destination)
      throws MobileHarnessException, InterruptedException {
    if (!isEnabled) {
      return false;
    }
    Path cachedFile = cacheDir.resolve(sha256);
    cacheIoLock.readLock().lock();
    try {
      if (Files.exists(cachedFile)) {
        try {
          Files.setLastModifiedTime(cachedFile, Files.getLastModifiedTime(cachedFile));
          logger.atInfo().log("Cached file %s exists, copying to %s", cachedFile, destination);
          localFileUtil.copyFileOrDir(cachedFile.toString(), destination.toString());
          return true;
        } catch (IOException e) {
          logger.atWarning().withCause(e).log(
              "Failed to update mtime or copy cached file %s, file will be re-downloaded.",
              cachedFile);
          return false;
        }
      } else {
        return false;
      }
    } finally {
      cacheIoLock.readLock().unlock();
    }
  }

  /**
   * Adds a file to the cache.
   *
   * @param sourceFile The path to the file to add to the cache.
   * @param sha256 The SHA256 hash of the file.
   */
  public void addToCache(Path sourceFile, String sha256) {
    if (!isEnabled) {
      return;
    }
    cacheIoLock.writeLock().lock();
    try {
      try {
        localFileUtil.prepareDir(cacheDir);
        Path cachedFile = cacheDir.resolve(sha256);
        if (!Files.exists(cachedFile)) {
          logger.atInfo().log("Caching file %s to %s", sourceFile, cachedFile);
          localFileUtil.copyFileOrDir(sourceFile.toString(), cachedFile.toString());
          currentCacheSizeBytes += getFileSize(cachedFile);
          evictCacheIfNecessary();
        }
      } catch (MobileHarnessException | IOException e) {
        logger.atWarning().withCause(e).log("Failed to add file %s to cache.", sourceFile);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.atWarning().withCause(e).log(
            "Interrupted while adding file %s to cache.", sourceFile);
      }
    } finally {
      cacheIoLock.writeLock().unlock();
    }
  }

  private void evictCacheIfNecessary() throws IOException {
    if (!isEnabled) {
      return;
    }
    Path cachePath = cacheDir;
    if (!Files.exists(cachePath)) {
      return;
    }

    long cacheSizeLimitBytes = getCacheSizeLimitBytes();
    if (currentCacheSizeBytes > cacheSizeLimitBytes) {
      logger.atInfo().log(
          "Cache size %d exceeds limit %d, evicting...",
          currentCacheSizeBytes, cacheSizeLimitBytes);
      List<Path> filesToEvict;
      try (Stream<Path> stream = Files.list(cachePath)) {
        filesToEvict =
            stream
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparingLong(this::getFileLastModifiedTime))
                .toList();
      }

      for (Path file : filesToEvict) {
        if (currentCacheSizeBytes <= cacheSizeLimitBytes) {
          break;
        }
        long fileSize = getFileSize(file);
        try {
          Files.delete(file);
          currentCacheSizeBytes -= fileSize;
          logger.atInfo().log("Evicted %s from cache.", file);
        } catch (IOException e) {
          logger.atWarning().withCause(e).log("Failed to evict %s from cache.", file);
        }
      }
    }
  }

  private long getFileSize(Path path) {
    try {
      return Files.size(path);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to get size of file %s.", path);
      return 0;
    }
  }

  private long getCacheSizeLimitBytes() {
    return Flags.instance().atsFileResolverCacheSizeMb.getNonNull() * BYTES_PER_MB;
  }

  private long getFileLastModifiedTime(Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to get last modified time of file %s.", path);
      return 0;
    }
  }
}
