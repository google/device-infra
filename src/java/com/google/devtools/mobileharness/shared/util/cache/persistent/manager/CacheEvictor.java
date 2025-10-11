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

package com.google.devtools.mobileharness.shared.util.cache.persistent.manager;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;

/** Evicts cache entries from the persistent cache. */
class CacheEvictor {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ListeningExecutorService executorService;
  private final LocalFileUtil localFileUtil;

  @Inject
  CacheEvictor(@Named("cache_evictor_executor") ListeningExecutorService executorService) {
    this.executorService = executorService;
    this.localFileUtil = new LocalFileUtil();
  }

  @CanIgnoreReturnValue
  List<ListenableFuture<?>> evict(List<CacheEntry> entriesToEvict) {
    List<ListenableFuture<?>> futures = new ArrayList<>();
    for (CacheEntry entry : entriesToEvict) {
      futures.add(executorService.submit(() -> evictEntry(entry)));
    }
    return futures;
  }

  private void evictEntry(CacheEntry entry) {
    Path lockPath = entry.cachePaths().lockPath();
    Path dataPath = entry.cachePaths().dataPath();
    Path metadataPath = entry.cachePaths().metadataPath();
    Path cacheDirPath = entry.cachePaths().cacheDirPath();

    try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
        FileLock lock = channel.tryLock()) {
      if (lock != null) {
        logger.atInfo().log("Acquired lock for eviction: %s", cacheDirPath);
        try {
          // While holding the lock, delete the data and metadata to invalidate the entry.
          localFileUtil.removeFileOrDir(dataPath);
          localFileUtil.removeFileOrDir(metadataPath);
        } catch (MobileHarnessException | InterruptedException e) {
          logger.atWarning().withCause(e).log(
              "Failed to delete contents of cache entry: %s", cacheDirPath);
          if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
          }
          return; // Do not proceed if content deletion fails.
        }
      } else {
        logger.atInfo().log(
            "Could not acquire lock for eviction, entry is in use: %s", cacheDirPath);
        return;
      }
    } catch (OverlappingFileLockException e) {
      logger.atWarning().withCause(e).log(
          "Some other thread is trying to evict the same entry: %s", lockPath);
      return;
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to open/lock file for eviction: %s", lockPath);
      return;
    }

    // After the lock is released, check if the deletion was successful and clean up the directory.
    try {
      if (!localFileUtil.isFileExist(metadataPath)) {
        logger.atInfo().log("Contents deleted, removing empty cache directory: %s", cacheDirPath);
        localFileUtil.removeFileOrDir(cacheDirPath);
      } else {
        logger.atWarning().log(
            "Metadata file still exists after supposed deletion for %s. Aborting directory"
                + " cleanup.",
            cacheDirPath);
      }
    } catch (MobileHarnessException | InterruptedException e) {
      logger.atWarning().withCause(e).log(
          "Failed to remove cache entry directory: %s", cacheDirPath);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
