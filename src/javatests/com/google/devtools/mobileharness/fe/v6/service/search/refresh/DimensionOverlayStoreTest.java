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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DimensionOverlay;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.DimensionOverlayRaw;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.FleetDataSource;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDisplay;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DimensionOverlayStore}. */
@RunWith(JUnit4.class)
public final class DimensionOverlayStoreTest {

  private static final DeviceKeyDescriptor CARRIER_KEY =
      new AtsDeviceKeyRegistry().createLongTailDimensionKey("carrier").get();
  private static final DeviceKeyDescriptor OS_VERSION_KEY =
      new AtsDeviceKeyRegistry().createLongTailDimensionKey("os_version").get();
  private static final DeviceKeyDescriptor BUILD_ID_KEY =
      new AtsDeviceKeyRegistry().createLongTailDimensionKey("build_id").get();

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
            "dimension::carrier", ImmutableMap.of("dev-1", ImmutableList.of("Verizon")));

    ImmutableMap<String, DimensionOverlay> result =
        overlayStore
            .loadOverlaysAsync(Fleet.FLEET_SELF, ImmutableSet.of(CARRIER_KEY), executor)
            .get();

    assertThat(result.keySet()).containsExactly("dimension::carrier");
    assertThat(
            result
                .get("dimension::carrier")
                .bind(snapshotStore.get(Fleet.FLEET_SELF))
                .valueCounts())
        .containsEntry("verizon", 1);
    assertThat(dataSource.pullCount.get()).isEqualTo(1);

    // Second call hits cache; does not pull again
    ImmutableMap<String, DimensionOverlay> secondResult =
        overlayStore
            .loadOverlaysAsync(Fleet.FLEET_SELF, ImmutableSet.of(CARRIER_KEY), executor)
            .get();

    assertThat(secondResult.keySet()).containsExactly("dimension::carrier");
    assertThat(dataSource.pullCount.get()).isEqualTo(1);
  }

  @Test
  public void loadOverlaysAsync_concurrentCallsForSameKey_deduplicates() throws Exception {
    SettableFuture<DimensionOverlayRaw> inFlight = SettableFuture.create();
    dataSource.setPendingFuture(inFlight);

    ListenableFuture<ImmutableMap<String, DimensionOverlay>> future1 =
        overlayStore.loadOverlaysAsync(Fleet.FLEET_SELF, ImmutableSet.of(CARRIER_KEY), executor);
    ListenableFuture<ImmutableMap<String, DimensionOverlay>> future2 =
        overlayStore.loadOverlaysAsync(Fleet.FLEET_SELF, ImmutableSet.of(CARRIER_KEY), executor);

    assertThat(dataSource.pullCount.get()).isEqualTo(1);

    // Complete the in-flight pull
    inFlight.set(
        DimensionOverlayRaw.create(
            "dimension::carrier", ImmutableMap.of("dev-1", ImmutableList.of("Verizon"))));

    assertThat(future1.get()).containsKey("dimension::carrier");
    assertThat(future2.get()).containsKey("dimension::carrier");
    assertThat(dataSource.pullCount.get()).isEqualTo(1);
  }

  @Test
  public void loadOverlaysAsync_duplicateKeyIdsInInputSet_deduplicatesWithoutConflict()
      throws Exception {
    dataSource.rawResult =
        DimensionOverlayRaw.create(
            "dimension::provisioned_by", ImmutableMap.of("dev-1", ImmutableList.of("mh")));
    DeviceKeyDescriptor key1 =
        DeviceKeyDescriptor.builder()
            .setId("dimension::provisioned_by")
            .setDisplay(KeyDisplay.of("provisioned_by"))
            .setIsDimension(true)
            .setIsLongTail(true)
            .build();
    DeviceKeyDescriptor key2 =
        DeviceKeyDescriptor.builder()
            .setId("dimension::provisioned_by")
            .setDisplay(KeyDisplay.of("Provisioned By"))
            .setIsDimension(true)
            .setIsLongTail(true)
            .build();

    // First call populates cache; second call hits cache with two distinct descriptors sharing id.
    ImmutableMap<String, DimensionOverlay> firstResult =
        overlayStore
            .loadOverlaysAsync(Fleet.FLEET_SELF, ImmutableSet.of(key1, key2), executor)
            .get();
    ImmutableMap<String, DimensionOverlay> secondResult =
        overlayStore
            .loadOverlaysAsync(Fleet.FLEET_SELF, ImmutableSet.of(key1, key2), executor)
            .get();

    assertThat(firstResult.keySet()).containsExactly("dimension::provisioned_by");
    assertThat(secondResult.keySet()).containsExactly("dimension::provisioned_by");
    assertThat(dataSource.pullCount.get()).isEqualTo(1);
  }

  @Test
  public void loadOverlaysAsync_multipleColdKeys_batchesIntoSinglePullAndDeduplicatesInFlight()
      throws Exception {
    BatchedFakeDataSource batchedSource = new BatchedFakeDataSource();
    DimensionOverlayStore batchedStore =
        new DimensionOverlayStore(ImmutableMap.of(Fleet.FLEET_SELF, batchedSource), snapshotStore);

    // Unbound fleet returns empty immediately.
    assertThat(
            batchedStore
                .loadOverlaysAsync(Fleet.FLEET_ATS, ImmutableSet.of(CARRIER_KEY), executor)
                .get())
        .isEmpty();

    SettableFuture<ImmutableMap<String, DimensionOverlayRaw>> batch1 = SettableFuture.create();
    SettableFuture<ImmutableMap<String, DimensionOverlayRaw>> batch2 = SettableFuture.create();
    batchedSource.pendingBatches.add(batch1);
    batchedSource.pendingBatches.add(batch2);

    ListenableFuture<ImmutableMap<String, DimensionOverlay>> call1 =
        batchedStore.loadOverlaysAsync(
            Fleet.FLEET_SELF, ImmutableSet.of(CARRIER_KEY, OS_VERSION_KEY), executor);
    assertThat(batchedSource.batchCalls).hasSize(1);
    assertThat(batchedSource.batchCalls.get(0)).containsExactly(CARRIER_KEY, OS_VERSION_KEY);

    // Concurrent call overlaps CARRIER_KEY (piggybacks on batch1) and adds BUILD_ID_KEY (batch2).
    ListenableFuture<ImmutableMap<String, DimensionOverlay>> call2 =
        batchedStore.loadOverlaysAsync(
            Fleet.FLEET_SELF, ImmutableSet.of(CARRIER_KEY, BUILD_ID_KEY), executor);
    assertThat(batchedSource.batchCalls).hasSize(2);
    assertThat(batchedSource.batchCalls.get(1)).containsExactly(BUILD_ID_KEY);

    batch1.set(
        ImmutableMap.of(
            "dimension::carrier",
            DimensionOverlayRaw.create(
                "dimension::carrier", ImmutableMap.of("dev-1", ImmutableList.of("Verizon"))),
            "dimension::os_version",
            DimensionOverlayRaw.create(
                "dimension::os_version", ImmutableMap.of("dev-2", ImmutableList.of("16")))));
    batch2.set(
        ImmutableMap.of(
            "dimension::build_id",
            DimensionOverlayRaw.create(
                "dimension::build_id", ImmutableMap.of("dev-1", ImmutableList.of("AP4A")))));

    ImmutableMap<String, DimensionOverlay> result1 = call1.get();
    assertThat(result1.keySet()).containsExactly("dimension::carrier", "dimension::os_version");
    assertThat(result1.get("dimension::carrier").valuesForUuid("dev-1")).containsExactly("Verizon");
    assertThat(result1.get("dimension::os_version").valuesForUuid("dev-2")).containsExactly("16");
    ImmutableMap<String, DimensionOverlay> result2 = call2.get();
    assertThat(result2.keySet()).containsExactly("dimension::carrier", "dimension::build_id");
    assertThat(result2.get("dimension::carrier").valuesForUuid("dev-1")).containsExactly("Verizon");
    assertThat(result2.get("dimension::build_id").valuesForUuid("dev-1")).containsExactly("AP4A");
  }

  @Test
  public void loadOverlaysAsync_batchPullFailure_propagatesErrorAndAllowsRetry() throws Exception {
    BatchedFakeDataSource batchedSource = new BatchedFakeDataSource();
    DimensionOverlayStore batchedStore =
        new DimensionOverlayStore(ImmutableMap.of(Fleet.FLEET_SELF, batchedSource), snapshotStore);
    batchedSource.pendingBatches.add(immediateFailedFuture(new RuntimeException("RPC failed")));

    ExecutionException expected =
        assertThrows(
            ExecutionException.class,
            () ->
                batchedStore
                    .loadOverlaysAsync(
                        Fleet.FLEET_SELF, ImmutableSet.of(CARRIER_KEY, OS_VERSION_KEY), executor)
                    .get());
    assertThat(expected).hasCauseThat().hasMessageThat().contains("RPC failed");

    assertRetrySucceeds(batchedStore, batchedSource);
  }

  @Test
  public void loadOverlaysAsync_callbackFailure_propagatesErrorAndAllowsRetry() throws Exception {
    BatchedFakeDataSource batchedSource = new BatchedFakeDataSource();
    DimensionOverlayStore batchedStore =
        new DimensionOverlayStore(ImmutableMap.of(Fleet.FLEET_SELF, batchedSource), snapshotStore);
    // A null result makes onSuccess throw while building overlays.
    batchedSource.pendingBatches.add(immediateFuture(null));

    assertThrows(
        ExecutionException.class,
        () ->
            batchedStore
                .loadOverlaysAsync(
                    Fleet.FLEET_SELF, ImmutableSet.of(CARRIER_KEY, OS_VERSION_KEY), executor)
                .get());

    assertRetrySucceeds(batchedStore, batchedSource);
  }

  @Test
  public void loadOverlaysAsync_pullDimensionsThrowsSynchronously_failsFutureAndAllowsRetry()
      throws Exception {
    BatchedFakeDataSource batchedSource = new BatchedFakeDataSource();
    DimensionOverlayStore batchedStore =
        new DimensionOverlayStore(ImmutableMap.of(Fleet.FLEET_SELF, batchedSource), snapshotStore);
    batchedSource.throwOnNextPull = new IllegalArgumentException("Key must be a dimension");

    ListenableFuture<ImmutableMap<String, DimensionOverlay>> future =
        batchedStore.loadOverlaysAsync(
            Fleet.FLEET_SELF, ImmutableSet.of(CARRIER_KEY, OS_VERSION_KEY), executor);

    ExecutionException expected = assertThrows(ExecutionException.class, future::get);
    assertThat(expected).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertRetrySucceeds(batchedStore, batchedSource);
  }

  @Test
  public void loadOverlaysAsync_missingKeyInBatchResult_failsThatKeyWithoutCachingIt()
      throws Exception {
    BatchedFakeDataSource batchedSource = new BatchedFakeDataSource();
    DimensionOverlayStore batchedStore =
        new DimensionOverlayStore(ImmutableMap.of(Fleet.FLEET_SELF, batchedSource), snapshotStore);
    batchedSource.pendingBatches.add(
        immediateFuture(
            ImmutableMap.of(
                "dimension::carrier",
                DimensionOverlayRaw.create(
                    "dimension::carrier", ImmutableMap.of("dev-1", ImmutableList.of("ATT"))))));

    ExecutionException expected =
        assertThrows(
            ExecutionException.class,
            () ->
                batchedStore
                    .loadOverlaysAsync(
                        Fleet.FLEET_SELF, ImmutableSet.of(CARRIER_KEY, OS_VERSION_KEY), executor)
                    .get());
    assertThat(expected).hasCauseThat().isInstanceOf(IllegalStateException.class);
    assertThat(expected).hasCauseThat().hasMessageThat().contains("dimension::os_version");

    // The returned key was cached, so loading it alone makes no new pull.
    assertThat(
            batchedStore
                .loadOverlaysAsync(Fleet.FLEET_SELF, ImmutableSet.of(CARRIER_KEY), executor)
                .get()
                .get("dimension::carrier")
                .valuesForUuid("dev-1"))
        .containsExactly("ATT");
    assertThat(batchedSource.batchCalls).hasSize(1);

    // The missing key was not cached, so loading it pulls again.
    batchedSource.pendingBatches.add(
        immediateFuture(
            ImmutableMap.of(
                "dimension::os_version",
                DimensionOverlayRaw.create(
                    "dimension::os_version", ImmutableMap.of("dev-2", ImmutableList.of("16"))))));
    assertThat(
            batchedStore
                .loadOverlaysAsync(Fleet.FLEET_SELF, ImmutableSet.of(OS_VERSION_KEY), executor)
                .get()
                .get("dimension::os_version")
                .valuesForUuid("dev-2"))
        .containsExactly("16");
    assertThat(batchedSource.batchCalls).hasSize(2);
  }

  /**
   * Asserts a follow-up load pulls again and succeeds, so the failed attempt left no stale state.
   */
  private void assertRetrySucceeds(
      DimensionOverlayStore batchedStore, BatchedFakeDataSource batchedSource) throws Exception {
    batchedSource.pendingBatches.add(
        immediateFuture(
            ImmutableMap.of(
                "dimension::carrier",
                DimensionOverlayRaw.create(
                    "dimension::carrier", ImmutableMap.of("dev-1", ImmutableList.of("ATT"))),
                "dimension::os_version",
                DimensionOverlayRaw.create(
                    "dimension::os_version", ImmutableMap.of("dev-2", ImmutableList.of("16"))))));

    ImmutableMap<String, DimensionOverlay> retryResult =
        batchedStore
            .loadOverlaysAsync(
                Fleet.FLEET_SELF, ImmutableSet.of(CARRIER_KEY, OS_VERSION_KEY), executor)
            .get();

    assertThat(retryResult.get("dimension::carrier").valuesForUuid("dev-1")).containsExactly("ATT");
    assertThat(retryResult.get("dimension::os_version").valuesForUuid("dev-2"))
        .containsExactly("16");
  }

  private static final class FakeDataSource implements FleetDataSource {
    final AtomicInteger pullCount = new AtomicInteger(0);
    volatile DimensionOverlayRaw rawResult =
        DimensionOverlayRaw.create("dimension::empty", ImmutableMap.of());
    volatile SettableFuture<DimensionOverlayRaw> pendingFuture = null;

    void setPendingFuture(SettableFuture<DimensionOverlayRaw> pending) {
      this.pendingFuture = pending;
    }

    @Override
    public Fleet fleet() {
      return Fleet.FLEET_SELF;
    }

    @Override
    public ListenableFuture<CoreFleetRawData> pull() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<DimensionOverlayRaw> pullDimension(DeviceKeyDescriptor dimensionKey) {
      pullCount.incrementAndGet();
      if (pendingFuture != null) {
        return pendingFuture;
      }
      return immediateFuture(
          DimensionOverlayRaw.create(dimensionKey.id(), rawResult.uuidToValues()));
    }
  }

  private static final class BatchedFakeDataSource implements FleetDataSource {
    final List<ImmutableSet<DeviceKeyDescriptor>> batchCalls = new ArrayList<>();
    final List<ListenableFuture<ImmutableMap<String, DimensionOverlayRaw>>> pendingBatches =
        new ArrayList<>();
    RuntimeException throwOnNextPull;

    @Override
    public Fleet fleet() {
      return Fleet.FLEET_SELF;
    }

    @Override
    public ListenableFuture<CoreFleetRawData> pull() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<DimensionOverlayRaw> pullDimension(DeviceKeyDescriptor dimensionKey) {
      throw new AssertionError("Expected batched pullDimensions call");
    }

    @Override
    public ListenableFuture<ImmutableMap<String, DimensionOverlayRaw>> pullDimensions(
        Set<DeviceKeyDescriptor> dimensionKeys) {
      if (throwOnNextPull != null) {
        RuntimeException toThrow = throwOnNextPull;
        throwOnNextPull = null;
        throw toThrow;
      }
      batchCalls.add(ImmutableSet.copyOf(dimensionKeys));
      return pendingBatches.remove(0);
    }
  }
}
