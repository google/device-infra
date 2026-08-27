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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.HostRecord;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import java.time.Instant;
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
  }

  @Test
  public void publish_and_get_updatesSnapshot() {
    FleetSnapshot snapshot1 = snapshotAt(1_700_000_000L);
    FleetSnapshot snapshot2 = snapshotAt(1_700_000_100L);

    store.publish(Fleet.FLEET_SELF, snapshot1);
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
    LazyPostings hostPostings = store.hostPostings(Fleet.FLEET_SELF);
    assertThat(hostPostings.get("host_field::device_count", "2")).asList().containsExactly(0);
    assertThat(hostPostings.get("host_field::device_count", "1")).asList().containsExactly(1);
    assertThat(hostPostings.get("host_field::host_name", "lab2")).asList().containsExactly(1);
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

  private static HostRecord host(String hostName, int deviceCount) {
    return HostRecord.create(
        hostName,
        ImmutableMap.of(
            HostKeys.HOST_NAME.id(), ImmutableList.of(hostName),
            HostKeys.DEVICE_COUNT.id(), ImmutableList.of(String.valueOf(deviceCount))),
        deviceCount);
  }
}
