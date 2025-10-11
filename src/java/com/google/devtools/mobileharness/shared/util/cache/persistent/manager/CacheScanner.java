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

import static java.nio.file.Files.walkFileTree;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.base.DataSize;
import com.google.devtools.mobileharness.shared.util.cache.persistent.CachePaths;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Optional;

/** Utility for scanning a persistent cache directory to gather metadata on cache entries. */
class CacheScanner {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LocalFileUtil localFileUtil;

  public CacheScanner() {
    this.localFileUtil = new LocalFileUtil();
  }

  /**
   * Scans the root cache directory to find all cache entries.
   *
   * @param rootCacheDir the root directory of the persistent cache
   * @return a list of all found cache entries
   */
  ImmutableList<CacheEntry> scan(Path rootCacheDir) {
    ImmutableList.Builder<CacheEntry> entries = ImmutableList.builder();
    try {
      walkFileTree(
          rootCacheDir,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              if (rootCacheDir.equals(dir)) {
                return FileVisitResult.CONTINUE;
              }
              // Check if this directory is at the 3rd depth relative to rootCacheDir
              if (rootCacheDir.relativize(dir).getNameCount() == 3) {
                createCacheEntry(dir).ifPresent(entries::add);
                return FileVisitResult.SKIP_SUBTREE; // We found an entry, no need to go deeper
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
              logger.atWarning().withCause(exc).log("Failed to visit file: %s", file);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to scan root cache directory: %s", rootCacheDir);
    }
    return entries.build();
  }

  private Optional<CacheEntry> createCacheEntry(Path entryPath) {
    CachePaths cachePaths = CachePaths.create(entryPath);

    if (!localFileUtil.isFileExist(cachePaths.dataPath())
        || !localFileUtil.isFileExist(cachePaths.metadataPath())
        || !localFileUtil.isFileExist(cachePaths.lockPath())) {
      logger.atWarning().log("Incomplete cache entry at %s. Deleting directory.", entryPath);
      try {
        localFileUtil.removeFileOrDir(entryPath);
      } catch (MobileHarnessException | InterruptedException e) {
        logger.atWarning().withCause(e).log(
            "Failed to delete incomplete cache entry: %s", entryPath);
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
      }
      return Optional.empty();
    }

    try {
      DataSize size = localFileUtil.getFileOrDirDataSize(cachePaths.cacheDirPath().toString());
      Instant lastAccessTime = localFileUtil.getFileLastModifiedTime(cachePaths.metadataPath());
      return Optional.of(CacheEntry.create(cachePaths, size, lastAccessTime));
    } catch (MobileHarnessException | InterruptedException e) {
      logger.atWarning().withCause(e).log("Failed to create cache entry for path %s", entryPath);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return Optional.empty();
    }
  }
}
