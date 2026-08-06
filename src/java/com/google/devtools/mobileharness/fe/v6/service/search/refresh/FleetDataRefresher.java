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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.FleetDataSource;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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
 */
@Singleton
public final class FleetDataRefresher {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String REFRESH_THREAD_NAME = "fleet-search-refresh";

  private final Map<Fleet, FleetDataSource> sources;
  private final FleetIndexBuilder indexBuilder;
  private final FleetSnapshotStore snapshotStore;
  private final InstantSource instantSource;

  @Nullable private ScheduledExecutorService executor;
  @Nullable private ScheduledFuture<?> scheduledTask;

  @Inject
  FleetDataRefresher(
      Map<Fleet, FleetDataSource> sources,
      FleetIndexBuilder indexBuilder,
      FleetSnapshotStore snapshotStore,
      InstantSource instantSource) {
    this.sources = sources;
    this.indexBuilder = indexBuilder;
    this.snapshotStore = snapshotStore;
    this.instantSource = instantSource;
  }

  /**
   * Runs one full refresh over every bound fleet: for each, pull the raw data, build a snapshot
   * stamped with the current time, and publish it under that fleet. A failure for one fleet is
   * logged and swallowed so that fleet keeps serving its previous snapshot and the remaining fleets
   * still refresh.
   */
  public void refreshOnce() {
    for (Map.Entry<Fleet, FleetDataSource> entry : sources.entrySet()) {
      Fleet fleet = entry.getKey();
      FleetDataSource source = entry.getValue();
      try {
        FleetRawData rawData = source.pull();
        FleetSnapshot snapshot = indexBuilder.build(rawData, instantSource.instant());
        snapshotStore.publish(fleet, snapshot);
        logger.atInfo().log(
            "Fleet search index for %s refreshed with %d devices across %d hosts.",
            fleet, snapshot.deviceCount(), snapshot.hostCount());
      } catch (Exception e) {
        logger.atWarning().withCause(e).log(
            "Fleet search index refresh for %s failed. Keeping its previously published snapshot.",
            fleet);
      }
    }
  }

  /**
   * Starts the periodic refresh on a single daemon thread. The first refresh runs immediately, then
   * again {@code interval} after each prior refresh completes.
   */
  public synchronized void start(Duration interval) {
    if (executor != null) {
      logger.atWarning().log("Fleet search refresh already started. Ignoring start request.");
      return;
    }
    executor =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat(REFRESH_THREAD_NAME).setDaemon(true).build());
    scheduledTask =
        executor.scheduleWithFixedDelay(this::refreshOnce, 0, interval.toMillis(), MILLISECONDS);
  }

  /**
   * Stops the periodic refresh, shutting the refresh thread down. Safe to call when not started.
   */
  public synchronized void stop() {
    if (scheduledTask != null) {
      scheduledTask.cancel(false);
      scheduledTask = null;
    }
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
  }
}
