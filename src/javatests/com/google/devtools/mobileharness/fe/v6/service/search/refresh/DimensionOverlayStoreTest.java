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
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DimensionOverlay;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.DimensionOverlayRaw;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.FleetDataSource;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DimensionOverlayStore}. */
@RunWith(JUnit4.class)
public final class DimensionOverlayStoreTest {

  private final ListeningExecutorService executor = newDirectExecutorService();
  private final FleetSnapshotStore snapshotStore = new FleetSnapshotStore();
  private FakeDataSource dataSource;
  private DimensionOverlayStore overlayStore;

  @Before
  public void setUp() {
    FleetSnapshot snapshot =
        FleetSnapshot.builder()
            .setBuildTime(Instant.ofEpochSecond(1_000))
            .setDevices(ImmutableList.of())
            .setHosts(ImmutableList.of())
            .setIndex(CoreFleetIndex.empty())
            .setHostIndex(CoreFleetIndex.empty())
            .setUuidToIndex(ImmutableMap.of("dev-1", 0, "dev-2", 1))
            .build();
    snapshotStore.publish(Fleet.FLEET_SELF, snapshot);

    dataSource = new FakeDataSource();
    overlayStore =
        new DimensionOverlayStore(ImmutableMap.of(Fleet.FLEET_SELF, dataSource), snapshotStore);
  }

  @Test
  public void loadOverlaysAsync_emptyKeys_returnsImmediately() throws Exception {
    ImmutableMap<String, DimensionOverlay> result =
        overlayStore.loadOverlaysAsync(Fleet.FLEET_SELF, ImmutableSet.of(), executor).get();

    assertThat(result).isEmpty();
    assertThat(dataSource.pullCount.get()).isEqualTo(0);
  }

  @Test
  public void loadOverlaysAsync_coldKey_pullsFromDataSourceAndCaches() throws Exception {
    dataSource.rawResult =
        DimensionOverlayRaw.create(
            "dim::carrier", ImmutableMap.of("dev-1", ImmutableList.of("Verizon")));

    ImmutableMap<String, DimensionOverlay> result =
        overlayStore
            .loadOverlaysAsync(Fleet.FLEET_SELF, ImmutableSet.of("dim::carrier"), executor)
            .get();

    assertThat(result.keySet()).containsExactly("dim::carrier");
    assertThat(result.get("dim::carrier").valueCounts()).containsEntry("verizon", 1);
    assertThat(dataSource.pullCount.get()).isEqualTo(1);

    // Second call hits cache; does not pull again
    ImmutableMap<String, DimensionOverlay> secondResult =
        overlayStore
            .loadOverlaysAsync(Fleet.FLEET_SELF, ImmutableSet.of("dim::carrier"), executor)
            .get();

    assertThat(secondResult.keySet()).containsExactly("dim::carrier");
    assertThat(dataSource.pullCount.get()).isEqualTo(1);
  }

  @Test
  public void loadOverlaysAsync_concurrentCallsForSameKey_deduplicates() throws Exception {
    SettableFuture<DimensionOverlayRaw> inFlight = SettableFuture.create();
    dataSource.setPendingFuture(inFlight);

    ListenableFuture<ImmutableMap<String, DimensionOverlay>> future1 =
        overlayStore.loadOverlaysAsync(Fleet.FLEET_SELF, ImmutableSet.of("dim::carrier"), executor);
    ListenableFuture<ImmutableMap<String, DimensionOverlay>> future2 =
        overlayStore.loadOverlaysAsync(Fleet.FLEET_SELF, ImmutableSet.of("dim::carrier"), executor);

    assertThat(dataSource.pullCount.get()).isEqualTo(1);

    // Complete the in-flight pull
    inFlight.set(
        DimensionOverlayRaw.create(
            "dim::carrier", ImmutableMap.of("dev-1", ImmutableList.of("Verizon"))));

    assertThat(future1.get()).containsKey("dim::carrier");
    assertThat(future2.get()).containsKey("dim::carrier");
    assertThat(dataSource.pullCount.get()).isEqualTo(1);
  }

  private static final class FakeDataSource implements FleetDataSource {
    final AtomicInteger pullCount = new AtomicInteger(0);
    volatile DimensionOverlayRaw rawResult =
        DimensionOverlayRaw.create("dim::empty", ImmutableMap.of());
    volatile SettableFuture<DimensionOverlayRaw> pendingFuture = null;

    void setPendingFuture(SettableFuture<DimensionOverlayRaw> pending) {
      this.pendingFuture = pending;
    }

    @Override
    public Fleet fleet() {
      return Fleet.FLEET_SELF;
    }

    @Override
    public ListenableFuture<FleetRawData> pull() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<DimensionOverlayRaw> pullDimension(String keyId) {
      pullCount.incrementAndGet();
      if (pendingFuture != null) {
        return pendingFuture;
      }
      return immediateFuture(rawResult);
    }
  }
}
