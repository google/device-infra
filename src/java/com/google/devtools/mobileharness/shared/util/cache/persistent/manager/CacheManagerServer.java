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

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.shared.util.base.BinaryPrefix;
import com.google.devtools.mobileharness.shared.util.base.DataSize;
import com.google.devtools.mobileharness.shared.util.base.SizeUnit;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessLogger;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** A server that periodically triggers the {@link CacheManager}. */
public final class CacheManagerServer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String LOG_DIR_NAME = "persistent_cache_manager_log";

  private final CacheManager cacheManager;
  private final Duration checkInterval;
  private final ScheduledExecutorService scheduler;

  public CacheManagerServer(
      CacheManager cacheManager, Duration checkInterval, ScheduledExecutorService scheduler) {
    this.cacheManager = cacheManager;
    this.checkInterval = checkInterval;
    this.scheduler = scheduler;
  }

  /** Starts the server, scheduling the cache manager to run at a fixed interval. */
  public void start() {
    logger.atInfo().log("Starting CacheManagerServer, check interval: %s", checkInterval);
    var unused =
        scheduler.scheduleAtFixedRate(cacheManager, 0, checkInterval.toMillis(), MILLISECONDS);
  }

  /** Stops the server. */
  public void stop() {
    logger.atInfo().log("Stopping CacheManagerServer.");
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(5, SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  public static void main(String[] args) {
    // Parses flags.
    Flags.parse(args);

    String persistentCacheDir = Flags.instance().persistentCacheDir.get();
    if (isNullOrEmpty(persistentCacheDir)) {
      logger.atSevere().log("Flag --persistent_cache_dir must be set.");
      System.exit(1);
    }
    Path rootCacheDir = Path.of(persistentCacheDir);

    String logDir = PathUtil.join(DirCommon.getPublicDirRoot(), LOG_DIR_NAME);
    MobileHarnessLogger.init(logDir);
    logger.atInfo().log("Persistent Cache Manager writes logs to the directory: %s", logDir);

    DataSize maxCacheSize =
        DataSize.of(
            Flags.instance().maxPersistentCacheSizeInGigabytes.getNonNull(),
            BinaryPrefix.GIBI,
            SizeUnit.BYTES);
    double trimToRatio = Flags.instance().cacheEvictionTrimToRatio.getNonNull();
    Duration checkInterval = Flags.instance().cacheEvictionCheckInterval.getNonNull();
    ListeningExecutorService evictorExecutor =
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4));
    Injector injector =
        Guice.createInjector(
            new CacheManagerModule(rootCacheDir, maxCacheSize, trimToRatio, evictorExecutor));
    CacheManager cacheManager = injector.getInstance(CacheManager.class);
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    CacheManagerServer server = new CacheManagerServer(cacheManager, checkInterval, scheduler);

    // Add a shutdown hook to gracefully stop the server
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  logger.atInfo().log("Shutdown hook activated. Stopping CacheManagerServer...");
                  server.stop();
                  evictorExecutor.shutdownNow();
                  try {
                    if (!evictorExecutor.awaitTermination(5, SECONDS)) {
                      logger.atWarning().log("Evictor executor did not terminate in time.");
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                  logger.atInfo().log("CacheManagerServer stopped.");
                }));

    // Start the server and wait for other threads to terminate.
    server.start();
    logger.atInfo().log("CacheManagerServer is running. Press Ctrl+C to stop.");
  }
}
