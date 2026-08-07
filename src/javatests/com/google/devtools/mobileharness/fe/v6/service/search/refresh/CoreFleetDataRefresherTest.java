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

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.DimensionOverlayRaw;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.FleetDataSource;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.inject.Guice;
import java.time.Instant;
import java.time.InstantSource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link CoreFleetDataRefresher#refreshOnce()}. Scheduled timing is deliberately not
 * exercised here because sleep-based timing tests are flaky.
 */
@RunWith(JUnit4.class)
public final class CoreFleetDataRefresherTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  // FleetIndexBuilder has a package-private @Inject constructor in a sibling package, so it is
  // built
  // through an empty Guice injector rather than constructed directly. The store and refresher live
  // in this package, so they are constructed directly with the fake sources.
  private final FleetIndexBuilder indexBuilder =
      Guice.createInjector().getInstance(FleetIndexBuilder.class);
  private final FleetSnapshotStore store = new FleetSnapshotStore();

  @Test
  public void refreshOnce_publishesSnapshotPerFleet() {
    FakeFleetDataSource selfSource = new FakeFleetDataSource(Fleet.FLEET_SELF);
    FakeFleetDataSource atsSource = new FakeFleetDataSource(Fleet.FLEET_ATS);
    selfSource.setResult(deviceResult(2));
    atsSource.setResult(deviceResult(1));

    refresher(selfSource, atsSource).refreshOnce();

    assertThat(store.get(Fleet.FLEET_SELF).deviceCount()).isEqualTo(2);
    assertThat(store.get(Fleet.FLEET_SELF).buildTime()).isEqualTo(BUILD_TIME);
    assertThat(store.get(Fleet.FLEET_ATS).deviceCount()).isEqualTo(1);
    assertThat(store.get(Fleet.FLEET_ATS).buildTime()).isEqualTo(BUILD_TIME);
  }

  @Test
  public void refreshOnce_oneFleetFails_keepsItsPreviousSnapshotAndRefreshesOthers() {
    FakeFleetDataSource selfSource = new FakeFleetDataSource(Fleet.FLEET_SELF);
    FakeFleetDataSource atsSource = new FakeFleetDataSource(Fleet.FLEET_ATS);
    CoreFleetDataRefresher refresher = refresher(selfSource, atsSource);

    // A first refresh publishes a snapshot for both fleets.
    selfSource.setResult(deviceResult(2));
    atsSource.setResult(deviceResult(1));
    refresher.refreshOnce();

    // The ATS pull now fails, while the SELF source returns a larger fleet.
    selfSource.setResult(deviceResult(3));
    atsSource.setFailure(new RuntimeException("ats controller unavailable"));
    refresher.refreshOnce();

    // SELF refreshed to the new fleet; ATS kept its previous snapshot despite the failure.
    assertThat(store.get(Fleet.FLEET_SELF).deviceCount()).isEqualTo(3);
    assertThat(store.get(Fleet.FLEET_ATS).deviceCount()).isEqualTo(1);
  }

  @Test
  public void buildInitialIndexWithRetry_succeedsFirstAttempt() {
    FakeFleetDataSource selfSource = new FakeFleetDataSource(Fleet.FLEET_SELF);
    selfSource.setResult(deviceResult(2));

    refresher(selfSource).buildInitialIndexWithRetry();

    assertThat(store.hasSnapshot(Fleet.FLEET_SELF)).isTrue();
    assertThat(store.get(Fleet.FLEET_SELF).deviceCount()).isEqualTo(2);
  }

  @Test
  public void buildInitialIndexWithRetry_failsAllAttempts_throwsIllegalStateException() {
    FakeFleetDataSource selfSource = new FakeFleetDataSource(Fleet.FLEET_SELF);
    selfSource.setFailure(new RuntimeException("master unreachable"));
    CoreFleetDataRefresher refresher = refresher(selfSource);

    assertThrows(IllegalStateException.class, refresher::buildInitialIndexWithRetry);
    assertThat(store.hasSnapshot(Fleet.FLEET_SELF)).isFalse();
  }

  private CoreFleetDataRefresher refresher(FleetDataSource... sources) {
    ImmutableMap.Builder<Fleet, FleetDataSource> map = ImmutableMap.builder();
    for (FleetDataSource source : sources) {
      map.put(source.fleet(), source);
    }
    return new CoreFleetDataRefresher(
        map.buildOrThrow(),
        indexBuilder,
        store,
        InstantSource.fixed(BUILD_TIME),
        newDirectExecutorService(),
        ThreadPools.createStandardScheduledThreadPool("test-timeout", 1));
  }

  private static LabQueryResult deviceResult(int deviceCount) {
    DeviceList.Builder deviceList = DeviceList.newBuilder();
    for (int i = 0; i < deviceCount; i++) {
      deviceList.addDeviceInfo(
          DeviceInfo.newBuilder()
              .setDeviceLocator(DeviceLocator.newBuilder().setId("device-" + i))
              .setDeviceStatus(DeviceStatus.IDLE));
    }
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .addLabData(LabData.newBuilder().setDeviceList(deviceList)))
        .build();
  }

  /** In-memory {@link FleetDataSource} that returns a configured result or a failed future. */
  private static final class FakeFleetDataSource implements FleetDataSource {
    private final Fleet fleet;
    private ListenableFuture<CoreFleetRawData> result =
        immediateFuture(CoreFleetRawData.ofLabData(LabQueryResult.getDefaultInstance()));

    FakeFleetDataSource(Fleet fleet) {
      this.fleet = fleet;
    }

    void setResult(LabQueryResult result) {
      this.result = immediateFuture(CoreFleetRawData.ofLabData(result));
    }

    void setFailure(RuntimeException failure) {
      this.result = immediateFailedFuture(failure);
    }

    @Override
    public Fleet fleet() {
      return fleet;
    }

    @Override
    public ListenableFuture<CoreFleetRawData> pull() {
      return result;
    }

    @Override
    public ListenableFuture<DimensionOverlayRaw> pullDimension(String keyId) {
      return immediateFuture(DimensionOverlayRaw.create(keyId, ImmutableMap.of()));
    }
  }
}
