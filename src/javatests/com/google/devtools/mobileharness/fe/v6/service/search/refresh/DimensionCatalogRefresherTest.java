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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.DimensionOverlayRaw;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.FleetDataSource;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DimensionCatalogRefresherTest {

  private final DimensionCatalogStore store = new DimensionCatalogStore();
  private final ListeningExecutorService executor = newDirectExecutorService();
  private ListeningScheduledExecutorService scheduledExecutor;

  @Before
  public void setUp() {
    scheduledExecutor =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadScheduledExecutor());
  }

  @After
  public void tearDown() {
    scheduledExecutor.shutdownNow();
  }

  @Test
  public void constructor_doesNotTriggerBackgroundTasksOrModifyStore() {
    FakeFleetDataSource source =
        new FakeFleetDataSource(Fleet.FLEET_SELF, ImmutableSet.of("model", "carrier"));
    DimensionCatalogRefresher refresher =
        new DimensionCatalogRefresher(
            ImmutableMap.of(Fleet.FLEET_SELF, source), store, executor, scheduledExecutor);

    // Constructor must be pure and not start background tasks or pull data.
    assertThat(store.getDimensionNames(Fleet.FLEET_SELF)).isEmpty();
    assertThat(source.pullCount).isEqualTo(0);
    refresher.stop();
  }

  @Test
  public void refreshOnce_populatesStorePerFleet() {
    FakeFleetDataSource selfSource =
        new FakeFleetDataSource(Fleet.FLEET_SELF, ImmutableSet.of("model", "carrier"));
    FakeFleetDataSource atsSource =
        new FakeFleetDataSource(Fleet.FLEET_ATS, ImmutableSet.of("wifi_ssid"));
    DimensionCatalogRefresher refresher =
        new DimensionCatalogRefresher(
            ImmutableMap.of(Fleet.FLEET_SELF, selfSource, Fleet.FLEET_ATS, atsSource),
            store,
            executor,
            scheduledExecutor);

    refresher.refreshOnce();

    assertThat(store.getDimensionNames(Fleet.FLEET_SELF)).containsExactly("model", "carrier");
    assertThat(store.getDimensionNames(Fleet.FLEET_ATS)).containsExactly("wifi_ssid");
    assertThat(selfSource.pullCount).isEqualTo(1);
    assertThat(atsSource.pullCount).isEqualTo(1);
  }

  @Test
  public void refreshOnce_failingFleet_retainsPreviousStoreContent() {
    store.setDimensionNames(Fleet.FLEET_SELF, ImmutableSet.of("existing_dim"));
    FailingFleetDataSource failingSource = new FailingFleetDataSource(Fleet.FLEET_SELF);
    DimensionCatalogRefresher refresher =
        new DimensionCatalogRefresher(
            ImmutableMap.of(Fleet.FLEET_SELF, failingSource), store, executor, scheduledExecutor);

    refresher.refreshOnce();

    // Existing data is retained despite failure.
    assertThat(store.getDimensionNames(Fleet.FLEET_SELF)).containsExactly("existing_dim");
  }

  @Test
  public void startAndStop_lifecycle() {
    FakeFleetDataSource source =
        new FakeFleetDataSource(Fleet.FLEET_SELF, ImmutableSet.of("model"));
    DimensionCatalogRefresher refresher =
        new DimensionCatalogRefresher(
            ImmutableMap.of(Fleet.FLEET_SELF, source), store, executor, scheduledExecutor);

    refresher.start();
    // Subsequent start call is a no-op.
    refresher.start();
    refresher.stop();
    // Subsequent stop call is safe.
    refresher.stop();
  }

  private static final class FakeFleetDataSource implements FleetDataSource {
    private final Fleet fleet;
    private final ImmutableSet<String> dimensions;
    private int pullCount = 0;

    FakeFleetDataSource(Fleet fleet, ImmutableSet<String> dimensions) {
      this.fleet = fleet;
      this.dimensions = dimensions;
    }

    @Override
    public Fleet fleet() {
      return fleet;
    }

    @Override
    public ListenableFuture<CoreFleetRawData> pull() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<DimensionOverlayRaw> pullDimension(String dimensionName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<ImmutableSet<String>> pullDimensionNames() {
      pullCount++;
      return immediateFuture(dimensions);
    }
  }

  private static final class FailingFleetDataSource implements FleetDataSource {
    private final Fleet fleet;

    FailingFleetDataSource(Fleet fleet) {
      this.fleet = fleet;
    }

    @Override
    public Fleet fleet() {
      return fleet;
    }

    @Override
    public ListenableFuture<CoreFleetRawData> pull() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<DimensionOverlayRaw> pullDimension(String dimensionName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<ImmutableSet<String>> pullDimensionNames() {
      return immediateFailedFuture(new RuntimeException("Pull failed"));
    }
  }
}
