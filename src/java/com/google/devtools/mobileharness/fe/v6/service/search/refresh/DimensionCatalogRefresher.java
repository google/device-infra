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

package com.google.devtools.mobileharness.fe.v6.service.search.refresh;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.FleetDataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Drives the periodic refresh of device dimension names across fleets every 1 hour.
 *
 * <p>Populates {@link DimensionCatalogStore} with complete dimension names so query-layer
 * components like {@code FleetSuggester} and {@code FleetColumnCataloger} can discover long-tail
 * dimensions without having to pull values fleet-wide.
 */
@Singleton
public final class DimensionCatalogRefresher {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration PULL_TIMEOUT = Duration.ofMinutes(2);
  private static final Duration REFRESH_PERIOD = Duration.ofHours(1);
  private static final String REFRESH_THREAD_NAME = "dimension-catalog-refresher-%d";

  private final Map<Fleet, FleetDataSource> sources;
  private final DimensionCatalogStore catalogStore;
  private final ListeningExecutorService executor;
  private final ListeningScheduledExecutorService scheduledExecutor;

  @Nullable private ScheduledExecutorService periodicExecutor;
  @Nullable private ScheduledFuture<?> scheduledTask;

  @Inject
  DimensionCatalogRefresher(
      Map<Fleet, FleetDataSource> sources,
      DimensionCatalogStore catalogStore,
      ListeningExecutorService executor,
      ListeningScheduledExecutorService scheduledExecutor) {
    this.sources = sources;
    this.catalogStore = catalogStore;
    this.executor = executor;
    this.scheduledExecutor = scheduledExecutor;
  }

  /**
   * Starts the periodic refresh on a single daemon thread. The first refresh runs immediately, then
   * again {@code REFRESH_PERIOD} after each prior refresh completes.
   */
  public synchronized void start() {
    if (periodicExecutor != null) {
      logger.atWarning().log("Dimension catalog refresh already started. Ignoring start request.");
      return;
    }
    periodicExecutor =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat(REFRESH_THREAD_NAME).setDaemon(true).build());
    scheduledTask =
        periodicExecutor.scheduleWithFixedDelay(
            this::refreshOnce, 0, REFRESH_PERIOD.toMillis(), MILLISECONDS);
  }

  /**
   * Stops the periodic refresh, shutting the refresh thread down. Safe to call when not started.
   * The injected executors are owned by Guice and are left running.
   */
  public synchronized void stop() {
    if (scheduledTask != null) {
      scheduledTask.cancel(/* mayInterruptIfRunning= */ false);
      scheduledTask = null;
    }
    if (periodicExecutor != null) {
      periodicExecutor.shutdownNow();
      periodicExecutor = null;
    }
  }

  /**
   * Executes a single refresh cycle over every bound fleet: discovers all dimension names and
   * updates {@link DimensionCatalogStore}. The fleets refresh in parallel, each bounded by {@link
   * #PULL_TIMEOUT}. A failure for one fleet is logged and swallowed so that fleet keeps its
   * previous catalog and the remaining fleets still refresh. The cycle blocks until every fleet has
   * finished so the fixed-delay cadence spaces out completed cycles.
   */
  public void refreshOnce() {
    List<ListenableFuture<Void>> perFleet = new ArrayList<>();
    for (Map.Entry<Fleet, FleetDataSource> entry : sources.entrySet()) {
      Fleet fleet = entry.getKey();
      FleetDataSource source = entry.getValue();

      ListenableFuture<ImmutableSet<String>> namesFuture =
          Futures.withTimeout(source.pullDimensionNames(), PULL_TIMEOUT, scheduledExecutor);
      ListenableFuture<Void> updated =
          Futures.transform(
              namesFuture,
              names -> {
                catalogStore.setDimensionNames(fleet, names);
                logger.atInfo().log(
                    "Dimension catalog for %s refreshed with %d dimension names.",
                    fleet, names.size());
                return null;
              },
              executor);

      ListenableFuture<Void> safe =
          Futures.catching(
              updated,
              Exception.class,
              e -> {
                logger.atWarning().withCause(e).log(
                    "Failed to refresh dimension catalog for fleet %s; retaining previous catalog.",
                    fleet);
                return null;
              },
              executor);
      perFleet.add(safe);
    }
    try {
      Uninterruptibles.getUninterruptibly(
          Futures.whenAllComplete(perFleet).call(() -> null, directExecutor()));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log(
          "Unexpected error awaiting dimension catalog refresh completion.");
    }
  }
}
