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

package com.google.devtools.mobileharness.fe.v6.service.search.index;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.DimensionOverlayRaw;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DimensionOverlay}. */
@RunWith(JUnit4.class)
public final class DimensionOverlayTest {

  @Test
  public void create_and_bind_reindexesAccurately() {
    DimensionOverlayRaw raw =
        DimensionOverlayRaw.create(
            "dimension::carrier",
            ImmutableMap.of(
                "uuid-1", ImmutableList.of("Verizon"),
                "uuid-2", ImmutableList.of("T-Mobile", "AT&T"),
                "uuid-3", ImmutableList.of("Verizon")));

    FleetSnapshot snapshotA =
        FleetSnapshot.builder()
            .setBuildTime(Instant.ofEpochSecond(1000))
            .setDevices(ImmutableList.of())
            .setHosts(ImmutableList.of())
            .setIndex(CoreFleetIndex.empty())
            .setHostIndex(CoreFleetIndex.empty())
            .setUuidToIndex(ImmutableMap.of("uuid-1", 0, "uuid-2", 1, "uuid-3", 2))
            .build();

    DimensionOverlay overlay = DimensionOverlay.create(raw, snapshotA);

    assertThat(overlay.keyId()).isEqualTo("dimension::carrier");
    assertThat(overlay.sortedValues()).containsExactly("at&t", "t-mobile", "verizon").inOrder();
    assertThat(overlay.valueCounts()).containsEntry("verizon", 2);
    assertThat(overlay.valueDisplays()).containsEntry("verizon", "Verizon");

    // Check postings bound to Snapshot A
    DimensionOverlay.SnapshotBoundPostings boundA = overlay.bind(snapshotA);
    assertThat(boundA.get("verizon")).asList().containsExactly(0, 2).inOrder();
    assertThat(boundA.get("t-mobile")).asList().containsExactly(1);
    assertThat(boundA.get("at&t")).asList().containsExactly(1);

    // Snapshot B: uuid-1 moved to index 5, uuid-3 moved to index 1, uuid-2 is decommissioned
    // (absent)
    FleetSnapshot snapshotB =
        FleetSnapshot.builder()
            .setBuildTime(Instant.ofEpochSecond(2000))
            .setDevices(ImmutableList.of())
            .setHosts(ImmutableList.of())
            .setIndex(CoreFleetIndex.empty())
            .setHostIndex(CoreFleetIndex.empty())
            .setUuidToIndex(ImmutableMap.of("uuid-1", 5, "uuid-3", 1))
            .build();

    DimensionOverlay.SnapshotBoundPostings boundB = overlay.bind(snapshotB);
    assertThat(boundB.get("verizon")).asList().containsExactly(1, 5).inOrder();
    assertThat(boundB.get("t-mobile")).isEmpty(); // uuid-2 was dropped safely
  }

  @Test
  public void bind_and_overlayView_computesKeyStatsAndExcludesAbsentUuids() {
    DimensionOverlayRaw raw =
        DimensionOverlayRaw.create(
            "dimension::tag",
            ImmutableMap.of(
                "uuid-1", ImmutableList.of("Alpha", "alpha"),
                "uuid-2", ImmutableList.of("Alpha", "Beta"),
                "uuid-3", ImmutableList.of("beta", "alpha"),
                "uuid-empty", ImmutableList.of(""),
                "uuid-absent", ImmutableList.of("Gamma")));

    FleetSnapshot snapshotA =
        FleetSnapshot.builder()
            .setBuildTime(Instant.ofEpochSecond(1000))
            .setDevices(ImmutableList.of())
            .setHosts(ImmutableList.of())
            .setIndex(CoreFleetIndex.empty())
            .setHostIndex(CoreFleetIndex.empty())
            .setUuidToIndex(ImmutableMap.of("uuid-1", 0, "uuid-2", 1, "uuid-3", 2, "uuid-empty", 3))
            .build();

    DimensionOverlay overlay = DimensionOverlay.create(raw, snapshotA);
    // 3 carrying devices in snapshotA (uuid-1, uuid-2, uuid-3); 2 distinct combinations:
    // {"alpha"} and {"alpha", "beta"}. uuid-empty and uuid-absent are excluded.
    assertThat(overlay.bind(snapshotA).stats()).isEqualTo(new KeyStats(3, 2));

    OverlayView viewA = OverlayView.bind(snapshotA, ImmutableMap.of("dimension::tag", overlay));
    assertThat(viewA.keyStats("dimension::tag")).isEqualTo(new KeyStats(3, 2));
    assertThat(viewA.keyStats("dimension::unknown")).isEqualTo(KeyStats.EMPTY);

    FleetSnapshot snapshotB =
        FleetSnapshot.builder()
            .setBuildTime(Instant.ofEpochSecond(2000))
            .setDevices(ImmutableList.of())
            .setHosts(ImmutableList.of())
            .setIndex(CoreFleetIndex.empty())
            .setHostIndex(CoreFleetIndex.empty())
            .setUuidToIndex(ImmutableMap.of("uuid-1", 0))
            .build();

    assertThat(overlay.bind(snapshotB).stats()).isEqualTo(new KeyStats(1, 1));
    OverlayView viewB = OverlayView.bind(snapshotB, ImmutableMap.of("dimension::tag", overlay));
    assertThat(viewB.keyStats("dimension::tag")).isEqualTo(new KeyStats(1, 1));
  }
}
