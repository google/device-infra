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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DeviceRecord;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.HostRecord;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.FleetSnapshotStore.SnapshotBundle;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetSnapshotStore}. */
@RunWith(JUnit4.class)
public final class FleetSnapshotStoreTest {

  private final FleetSnapshotStore store = new FleetSnapshotStore();

  @Test
  public void get_emptyBeforePublish_returnsEmptySnapshotWithEpochBuildTime() {
    FleetSnapshot snapshot = store.get(Fleet.FLEET_SELF);

    assertThat(snapshot.buildTime()).isEqualTo(Instant.EPOCH);
    assertThat(snapshot.devices()).isEmpty();
    assertThat(snapshot.hosts()).isEmpty();
    assertThat(snapshot.index().keyIds()).isEmpty();
    assertThat(snapshot.hostIndex().keyIds()).isEmpty();
    assertThat(store.hasSnapshot(Fleet.FLEET_SELF)).isFalse();
  }

  @Test
  public void getBundle_emptyBeforePublish_returnsConsistentEmptyBundle() {
    SnapshotBundle bundle = store.getBundle(Fleet.FLEET_SELF);

    assertThat(bundle.snapshot().buildTime()).isEqualTo(Instant.EPOCH);
    assertThat(bundle.snapshot().devices()).isEmpty();
    assertThat(bundle.snapshot().hosts()).isEmpty();
    assertThat(bundle.devicePostings().get(DeviceKeys.STATUS.id(), "idle")).isEmpty();
    assertThat(bundle.hostPostings().get(HostKeys.DEVICE_COUNT.id(), "1")).isEmpty();
  }

  @Test
  public void snapshotBundle_nullComponent_throwsNullPointerException() {
    FleetSnapshot snapshot = FleetSnapshot.empty();
    LazyPostings devicePostings = new LazyPostings(snapshot.devices());
    LazyPostings hostPostings = LazyPostings.forHosts(snapshot.hosts());

    assertThrows(
        NullPointerException.class, () -> new SnapshotBundle(null, devicePostings, hostPostings));
    assertThrows(
        NullPointerException.class, () -> new SnapshotBundle(snapshot, null, hostPostings));
    assertThrows(
        NullPointerException.class, () -> new SnapshotBundle(snapshot, devicePostings, null));
  }

  @Test
  public void publish_and_get_updatesSnapshot() {
    FleetSnapshot snapshot1 = snapshotAt(1_700_000_000L);
    FleetSnapshot snapshot2 = snapshotAt(1_700_000_100L);

    store.publish(Fleet.FLEET_SELF, snapshot1);
    assertThat(store.hasSnapshot(Fleet.FLEET_SELF)).isTrue();
    assertThat(store.get(Fleet.FLEET_SELF)).isSameInstanceAs(snapshot1);

    store.publish(Fleet.FLEET_SELF, snapshot2);
    assertThat(store.get(Fleet.FLEET_SELF)).isSameInstanceAs(snapshot2);
  }

  @Test
  public void publish_distinctFleets_storedSeparately() {
    FleetSnapshot selfSnapshot = snapshotAt(1_700_000_000L);
    FleetSnapshot atsSnapshot = snapshotAt(1_700_000_200L);

    store.publish(Fleet.FLEET_SELF, selfSnapshot);
    store.publish(Fleet.FLEET_ATS, atsSnapshot);

    assertThat(store.get(Fleet.FLEET_SELF)).isSameInstanceAs(selfSnapshot);
    assertThat(store.get(Fleet.FLEET_ATS)).isSameInstanceAs(atsSnapshot);
  }

  @Test
  public void publish_oneFleet_leavesOtherFleetEmpty() {
    store.publish(Fleet.FLEET_SELF, snapshotAt(1_700_000_000L));

    assertThat(store.get(Fleet.FLEET_ATS).buildTime()).isEqualTo(Instant.EPOCH);
    assertThat(store.hasSnapshot(Fleet.FLEET_ATS)).isFalse();
  }

  @Test
  public void hostPostings_resolveHostKeysOverPublishedHosts() {
    FleetSnapshot snapshot =
        FleetSnapshot.builder()
            .setBuildTime(Instant.ofEpochSecond(1_700_000_000L))
            .setDevices(ImmutableList.of())
            .setHosts(ImmutableList.of(host("lab1", 2), host("lab2", 1)))
            .setIndex(CoreFleetIndex.empty())
            .setHostIndex(CoreFleetIndex.empty())
            .build();

    store.publish(Fleet.FLEET_SELF, snapshot);

    // The host postings are keyed by host index in hosts() order and resolve the host-only device
    // count key as well as the host name.
    LazyPostings hostPostings = store.getBundle(Fleet.FLEET_SELF).hostPostings();
    assertThat(hostPostings.get(HostKeys.DEVICE_COUNT.id(), "2")).asList().containsExactly(0);
    assertThat(hostPostings.get(HostKeys.DEVICE_COUNT.id(), "1")).asList().containsExactly(1);
    assertThat(hostPostings.get(HostKeys.HOST_NAME.id(), "lab2")).asList().containsExactly(1);
  }

  @Test
  public void publish_replacesSnapshotAndPostingsAtomicallyWithoutMutatingPriorBundle() {
    FleetSnapshot snapshot1 =
        FleetSnapshot.builder()
            .setBuildTime(Instant.ofEpochSecond(1_700_000_000L))
            .setDevices(ImmutableList.of(device("dev-a", "idle"), device("dev-b", "failed")))
            .setHosts(ImmutableList.of(host("lab1", 2), host("lab2", 1)))
            .setIndex(CoreFleetIndex.empty())
            .setHostIndex(CoreFleetIndex.empty())
            .build();
    FleetSnapshot snapshot2 =
        FleetSnapshot.builder()
            .setBuildTime(Instant.ofEpochSecond(1_700_000_120L))
            .setDevices(ImmutableList.of(device("dev-c", "failed")))
            .setHosts(ImmutableList.of(host("lab3", 1)))
            .setIndex(CoreFleetIndex.empty())
            .setHostIndex(CoreFleetIndex.empty())
            .build();

    store.publish(Fleet.FLEET_SELF, snapshot1);
    SnapshotBundle firstBundle = store.getBundle(Fleet.FLEET_SELF);

    store.publish(Fleet.FLEET_SELF, snapshot2);
    SnapshotBundle secondBundle = store.getBundle(Fleet.FLEET_SELF);

    assertThat(firstBundle.snapshot()).isSameInstanceAs(snapshot1);
    assertThat(firstBundle.devicePostings().get(DeviceKeys.STATUS.id(), "failed"))
        .asList()
        .containsExactly(1);
    assertThat(firstBundle.hostPostings().get(HostKeys.DEVICE_COUNT.id(), "1"))
        .asList()
        .containsExactly(1);

    assertThat(secondBundle.snapshot()).isSameInstanceAs(snapshot2);
    assertThat(secondBundle.devicePostings().get(DeviceKeys.STATUS.id(), "failed"))
        .asList()
        .containsExactly(0);
    assertThat(secondBundle.hostPostings().get(HostKeys.DEVICE_COUNT.id(), "1"))
        .asList()
        .containsExactly(0);
  }

  @Test
  public void getBundle_concurrentPublishAndRead_neverPairsMismatchedSnapshotAndPostings()
      throws Exception {
    // Two snapshots with incompatible ordinal layouts: pairing twoDeviceSnapshot's postings
    // (device ordinal 1 for "failed" and host ordinal 1 for "1") with oneDeviceSnapshot (size 1)
    // would throw IndexOutOfBoundsException or return the wrong device or host record.
    FleetSnapshot twoDeviceSnapshot =
        FleetSnapshot.builder()
            .setBuildTime(Instant.ofEpochSecond(1_700_000_000L))
            .setDevices(ImmutableList.of(device("dev-0", "idle"), device("dev-1", "failed")))
            .setHosts(ImmutableList.of(host("lab-0", 2), host("lab-1", 1)))
            .setIndex(CoreFleetIndex.empty())
            .setHostIndex(CoreFleetIndex.empty())
            .build();
    FleetSnapshot oneDeviceSnapshot =
        FleetSnapshot.builder()
            .setBuildTime(Instant.ofEpochSecond(1_700_000_060L))
            .setDevices(ImmutableList.of(device("dev-only", "failed")))
            .setHosts(ImmutableList.of(host("lab-only", 1)))
            .setIndex(CoreFleetIndex.empty())
            .setHostIndex(CoreFleetIndex.empty())
            .build();

    store.publish(Fleet.FLEET_SELF, twoDeviceSnapshot);

    int readerThreads = 4;
    ExecutorService executor = ThreadPools.createStandardThreadPool("snapshot-store-test");
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicBoolean writerDone = new AtomicBoolean(false);
    try {
      Future<?> writer =
          executor.submit(
              () -> {
                startLatch.await();
                for (int i = 0; i < 2_000; i++) {
                  store.publish(
                      Fleet.FLEET_SELF, (i % 2 == 0) ? oneDeviceSnapshot : twoDeviceSnapshot);
                }
                writerDone.set(true);
                return null;
              });

      List<Future<?>> readers = new ArrayList<>();
      for (int r = 0; r < readerThreads; r++) {
        readers.add(
            executor.submit(
                () -> {
                  startLatch.await();
                  while (!writerDone.get() && !Thread.currentThread().isInterrupted()) {
                    assertBundleOrdinalsMatchRecords(store.getBundle(Fleet.FLEET_SELF));
                  }
                  assertBundleOrdinalsMatchRecords(store.getBundle(Fleet.FLEET_SELF));
                  return null;
                }));
      }

      startLatch.countDown();
      writer.get(10, SECONDS);
      for (Future<?> reader : readers) {
        reader.get(10, SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static void assertBundleOrdinalsMatchRecords(SnapshotBundle bundle) {
    int[] failedDevices = bundle.devicePostings().get(DeviceKeys.STATUS.id(), "failed");
    assertThat(failedDevices).hasLength(1);
    DeviceRecord matchedDevice = bundle.snapshot().devices().get(failedDevices[0]);
    assertThat(matchedDevice.normalizedValues(DeviceKeys.STATUS.id())).containsExactly("failed");
    if (bundle.snapshot().deviceCount() == 2) {
      assertThat(matchedDevice.deviceId()).isEqualTo("dev-1");
    } else {
      assertThat(matchedDevice.deviceId()).isEqualTo("dev-only");
    }

    int[] oneDeviceHosts = bundle.hostPostings().get(HostKeys.DEVICE_COUNT.id(), "1");
    assertThat(oneDeviceHosts).hasLength(1);
    HostRecord matchedHost = bundle.snapshot().hosts().get(oneDeviceHosts[0]);
    if (bundle.snapshot().hostCount() == 2) {
      assertThat(matchedHost.hostName()).isEqualTo("lab-1");
    } else {
      assertThat(matchedHost.hostName()).isEqualTo("lab-only");
    }
  }

  private static FleetSnapshot snapshotAt(long epochSecond) {
    return FleetSnapshot.builder()
        .setBuildTime(Instant.ofEpochSecond(epochSecond))
        .setDevices(ImmutableList.of())
        .setHosts(ImmutableList.of())
        .setIndex(CoreFleetIndex.empty())
        .setHostIndex(CoreFleetIndex.empty())
        .build();
  }

  private static DeviceRecord device(String deviceId, String status) {
    return DeviceRecord.create(
        deviceId,
        ImmutableMap.of(
            DeviceKeys.UUID.id(), ImmutableList.of(deviceId),
            DeviceKeys.STATUS.id(), ImmutableList.of(status)));
  }

  private static HostRecord host(String hostName, int deviceCount) {
    return HostRecord.create(
        hostName,
        ImmutableMap.of(
            HostKeys.HOST_NAME.id(), ImmutableList.of(hostName),
            HostKeys.DEVICE_COUNT.id(), ImmutableList.of(String.valueOf(deviceCount))),
        deviceCount);
  }
}
