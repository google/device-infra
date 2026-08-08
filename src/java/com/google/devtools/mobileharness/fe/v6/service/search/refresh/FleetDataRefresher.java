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

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.FleetDataSource;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Drives the periodic fleet search index refresh across every bound fleet.
 *
 * <p>One refresh is {@link #refreshOnce}: for each bound {@link FleetDataSource}, pull that fleet's
 * raw data, rebuild its index, and publish the snapshot into the {@link FleetSnapshotStore} under
 * that fleet. {@link #start} runs it on a single daemon thread at a fixed delay; {@link #stop}
 * shuts that thread down. A per-fleet failure is logged and swallowed so the other fleets still
 * refresh and the failing fleet keeps serving its previous snapshot. One refresher drives all
 * fleets: one in the OSS build (FLEET_SELF), two in the internal build (FLEET_SELF and FLEET_ATS).
 *
 * <p>The fleets refresh in parallel. Each fleet's pull runs asynchronously with a {@link
 * #PULL_TIMEOUT} bound, and its index build and publish run on the shared executor. A single
 * refresh cycle fans out all the fleets and then waits for them all to finish before returning, so
 * the fixed-delay cadence measures the gap between completed cycles.
 */
@Singleton
public final class FleetDataRefresher {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String REFRESH_THREAD_NAME = "fleet-search-refresh";

  private static final Duration PULL_TIMEOUT = Duration.ofMinutes(2);

  private final Map<Fleet, FleetDataSource> sources;
  private final FleetIndexBuilder indexBuilder;
  private final FleetSnapshotStore snapshotStore;
  private final InstantSource instantSource;
  private final ListeningExecutorService executor;
  private final ListeningScheduledExecutorService scheduledExecutor;

  // The periodic loop that drives refreshOnce at a fixed delay. Created in start(), torn down in
  // stop(). Distinct from the injected scheduledExecutor, which only backs the per-pull timeout.
  @Nullable private ScheduledExecutorService periodicExecutor;
  @Nullable private ScheduledFuture<?> scheduledTask;

  @Inject
  FleetDataRefresher(
      Map<Fleet, FleetDataSource> sources,
      FleetIndexBuilder indexBuilder,
      FleetSnapshotStore snapshotStore,
      InstantSource instantSource,
      ListeningExecutorService executor,
      ListeningScheduledExecutorService scheduledExecutor) {
    this.sources = sources;
    this.indexBuilder = indexBuilder;
    this.snapshotStore = snapshotStore;
    this.instantSource = instantSource;
    this.executor = executor;
    this.scheduledExecutor = scheduledExecutor;
  }

  /**
   * Runs one full refresh over every bound fleet: for each, pull the raw data, build a snapshot
   * stamped with the current time, and publish it under that fleet. The fleets refresh in parallel,
   * each bounded by {@link #PULL_TIMEOUT}. A failure for one fleet is logged and swallowed so that
   * fleet keeps serving its previous snapshot and the remaining fleets still refresh. The cycle
   * blocks until every fleet has finished so the fixed-delay cadence spaces out completed cycles.
   */
  public void refreshOnce() {
    List<ListenableFuture<Void>> perFleet = new ArrayList<>();
    for (Map.Entry<Fleet, FleetDataSource> entry : sources.entrySet()) {
      Fleet fleet = entry.getKey();
      FleetDataSource source = entry.getValue();
      Instant start = instantSource.instant();
      ListenableFuture<FleetRawData> rawFuture =
          Futures.withTimeout(source.pull(), PULL_TIMEOUT, scheduledExecutor);
      ListenableFuture<Void> published =
          Futures.transform(
              rawFuture,
              rawData -> {
                FleetSnapshot snapshot = indexBuilder.build(rawData, instantSource.instant());
                snapshotStore.publish(fleet, snapshot);
                logger.atInfo().log(
                    "Fleet search index for %s refreshed with %d devices across %d hosts in %d ms.",
                    fleet,
                    snapshot.deviceCount(),
                    snapshot.hostCount(),
                    Duration.between(start, instantSource.instant()).toMillis());
                return null;
              },
              executor);
      ListenableFuture<Void> guarded =
          Futures.catching(
              published,
              Exception.class,
              e -> {
                logger.atWarning().withCause(e).log(
                    "Fleet search index refresh for %s failed after %d ms. Keeping its previously"
                        + " published snapshot.",
                    fleet, Duration.between(start, instantSource.instant()).toMillis());
                return null;
              },
              executor);
      perFleet.add(guarded);
    }
    try {
      // Each per-fleet future already catches its own failure, so this await is defensive.
      Uninterruptibles.getUninterruptibly(
          Futures.whenAllComplete(perFleet).call(() -> null, directExecutor()));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Unexpected error awaiting fleet refresh completion.");
    }
  }

  /**
   * Starts the periodic refresh on a single daemon thread. The first refresh runs immediately, then
   * again {@code interval} after each prior refresh completes.
   */
  public synchronized void start(Duration interval) {
    if (periodicExecutor != null) {
      logger.atWarning().log("Fleet search refresh already started. Ignoring start request.");
      return;
    }
    periodicExecutor =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat(REFRESH_THREAD_NAME).setDaemon(true).build());
    // Stagger the first refresh by a random delay within [0, interval) so that replicas behind
    // the same GSLB VIP spread their pulls across the refresh window instead of all firing at
    // startup. This reduces the peak concurrent load on the upstream master.
    long jitterMs = ThreadLocalRandom.current().nextLong(interval.toMillis());
    logger.atInfo().log(
        "Fleet search refresh starting with %d ms jitter (interval %s).", jitterMs, interval);
    scheduledTask =
        periodicExecutor.scheduleWithFixedDelay(
            this::refreshOnce, jitterMs, interval.toMillis(), MILLISECONDS);
  }

  /**
   * Stops the periodic refresh, shutting the refresh thread down. Safe to call when not started.
   * The injected executors are owned by Guice and are left running.
   */
  public synchronized void stop() {
    if (scheduledTask != null) {
      scheduledTask.cancel(false);
      scheduledTask = null;
    }
    if (periodicExecutor != null) {
      periodicExecutor.shutdownNow();
      periodicExecutor = null;
    }
  }
}
