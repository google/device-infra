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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.base.DataSize;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Orchestrator for the cache management process. Implements {@link Runnable} to be executed by a
 * scheduler.
 */
public final class CacheManager implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Path rootCacheDir;
  private final DataSize maxCacheSize;
  private final CacheScanner cacheScanner;
  private final EvictionPolicy evictionPolicy;
  private final CacheEvictor cacheEvictor;
  private final LocalFileUtil localFileUtil;

  @Inject
  CacheManager(
      @Named("root_cache_dir") Path rootCacheDir,
      @Named("max_cache_size") DataSize maxCacheSize,
      CacheScanner cacheScanner,
      EvictionPolicy evictionPolicy,
      CacheEvictor cacheEvictor) {
    this(
        rootCacheDir,
        maxCacheSize,
        cacheScanner,
        evictionPolicy,
        cacheEvictor,
        new LocalFileUtil());
  }

  @VisibleForTesting
  CacheManager(
      Path rootCacheDir,
      DataSize maxCacheSize,
      CacheScanner cacheScanner,
      EvictionPolicy evictionPolicy,
      CacheEvictor cacheEvictor,
      LocalFileUtil localFileUtil) {
    this.rootCacheDir = rootCacheDir;
    this.maxCacheSize = maxCacheSize;
    this.cacheScanner = cacheScanner;
    this.evictionPolicy = evictionPolicy;
    this.cacheEvictor = cacheEvictor;
    this.localFileUtil = localFileUtil;
  }

  @Override
  public void run() {
    logger.atInfo().log("Starting cache manager run.");
    try {
      scanAndEvict();
    } catch (MobileHarnessException | InterruptedException e) {
      logger.atWarning().withCause(e).log("Error during cache scan and eviction cycle");
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @VisibleForTesting
  void scanAndEvict() throws MobileHarnessException, InterruptedException {
    // Phase 1: Fast Pre-Check using 'du'
    DataSize totalSize = getTotalCacheSize();

    if (totalSize.compareTo(maxCacheSize) <= 0) {
      logger.atInfo().log(
          "Cache size %s is within the limit %s, checked via 'du'. No eviction needed.",
          totalSize, maxCacheSize);
      return;
    }

    // Phase 2: Deep Scan and Eviction
    logger.atInfo().log(
        "Cache size %s exceeds limit %s. Starting deep scan for eviction.",
        totalSize, maxCacheSize);

    ImmutableList<CacheEntry> allEntries = cacheScanner.scan(rootCacheDir);
    List<CacheEntry> entriesToEvict = evictionPolicy.selectForEviction(allEntries);
    cacheEvictor.evict(entriesToEvict);
  }

  private DataSize getTotalCacheSize() throws MobileHarnessException, InterruptedException {
    return localFileUtil.getFileOrDirDataSize(rootCacheDir.toString());
  }
}
