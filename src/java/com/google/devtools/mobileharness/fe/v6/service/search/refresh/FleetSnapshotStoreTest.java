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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetSnapshotStore}. */
@RunWith(JUnit4.class)
public final class FleetSnapshotStoreTest {

  private final FleetSnapshotStore store = new FleetSnapshotStore();

  @Test
  public void get_beforeAnyPublish_returnsEmpty() {
    FleetSnapshot snapshot = store.get(Fleet.FLEET_SELF);

    assertThat(snapshot.deviceCount()).isEqualTo(0);
    assertThat(snapshot.hostCount()).isEqualTo(0);
    assertThat(snapshot.buildTime()).isEqualTo(Instant.EPOCH);
  }

  @Test
  public void publish_thenGet_returnsPublishedSnapshot() {
    FleetSnapshot published = snapshotAt(1_700_000_000L);

    store.publish(Fleet.FLEET_SELF, published);

    assertThat(store.get(Fleet.FLEET_SELF)).isSameInstanceAs(published);
  }

  @Test
  public void publish_twoFleets_areIndependent() {
    FleetSnapshot selfSnapshot = snapshotAt(1_700_000_000L);
    FleetSnapshot atsSnapshot = snapshotAt(1_800_000_000L);

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

  private static FleetSnapshot snapshotAt(long epochSecond) {
    return FleetSnapshot.builder()
        .setBuildTime(Instant.ofEpochSecond(epochSecond))
        .setDevices(ImmutableList.of())
        .setHosts(ImmutableList.of())
        .setIndex(FleetIndex.empty())
        .build();
  }
}
