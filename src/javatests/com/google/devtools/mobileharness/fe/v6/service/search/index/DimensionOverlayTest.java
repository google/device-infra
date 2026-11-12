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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.DimensionOverlayRaw;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.DimensionOverlayRaw.DeviceKey;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
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
            .setUuidToIndex(
                ImmutableMap.of(
                    DeviceKey.ofUuid("uuid-1"),
                    0,
                    DeviceKey.ofUuid("uuid-2"),
                    1,
                    DeviceKey.ofUuid("uuid-3"),
                    2))
            .build();

    DimensionOverlay overlay = DimensionOverlay.create(raw, snapshotA);

    assertThat(overlay.keyId()).isEqualTo("dimension::carrier");
    assertThat(overlay.sortedValues()).containsExactly("at&t", "t-mobile", "verizon").inOrder();
    assertThat(overlay.valueDisplays()).containsEntry("verizon", "Verizon");

    // Check postings bound to Snapshot A
    DimensionOverlay.SnapshotBoundPostings boundA = overlay.bind(snapshotA);
    assertThat(boundA.valueCounts()).containsEntry("verizon", 2);
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
            .setUuidToIndex(
                ImmutableMap.of(DeviceKey.ofUuid("uuid-1"), 5, DeviceKey.ofUuid("uuid-3"), 1))
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
            .setUuidToIndex(
                ImmutableMap.of(
                    DeviceKey.ofUuid("uuid-1"),
                    0,
                    DeviceKey.ofUuid("uuid-2"),
                    1,
                    DeviceKey.ofUuid("uuid-3"),
                    2,
                    DeviceKey.ofUuid("uuid-empty"),
                    3))
            .build();

    DimensionOverlay overlay = DimensionOverlay.create(raw, snapshotA);
    // 3 carrying devices in snapshotA (uuid-1, uuid-2, uuid-3); 2 distinct combinations:
    // {"alpha"} and {"alpha", "beta"}, the second making the key multi-valued. uuid-empty and
    // uuid-absent are excluded.
    assertThat(overlay.bind(snapshotA).stats()).isEqualTo(new KeyStats(3, 2, true));

    OverlayView viewA = OverlayView.bind(snapshotA, ImmutableMap.of("dimension::tag", overlay));
    assertThat(viewA.keyStats("dimension::tag")).isEqualTo(new KeyStats(3, 2, true));
    assertThat(viewA.valueCounts("dimension::tag")).containsExactly("alpha", 3, "beta", 2);
    assertThat(viewA.valueCount("dimension::tag", "alpha")).isEqualTo(3);
    assertThat(viewA.valueCount("dimension::tag", "gamma")).isEqualTo(0);
    assertThat(viewA.keyStats("dimension::unknown")).isEqualTo(KeyStats.EMPTY);
    assertThat(viewA.valueCounts("dimension::unknown")).isEmpty();
    assertThat(viewA.valueCount("dimension::unknown", "alpha")).isEqualTo(0);

    FleetSnapshot snapshotB =
        FleetSnapshot.builder()
            .setBuildTime(Instant.ofEpochSecond(2000))
            .setDevices(ImmutableList.of())
            .setHosts(ImmutableList.of())
            .setIndex(CoreFleetIndex.empty())
            .setHostIndex(CoreFleetIndex.empty())
            .setUuidToIndex(ImmutableMap.of(DeviceKey.ofUuid("uuid-1"), 0))
            .build();

    // Only uuid-1 ({"alpha"}) remains, so the key is single-valued in snapshotB.
    assertThat(overlay.bind(snapshotB).stats()).isEqualTo(new KeyStats(1, 1, false));
    OverlayView viewB = OverlayView.bind(snapshotB, ImmutableMap.of("dimension::tag", overlay));
    assertThat(viewB.keyStats("dimension::tag")).isEqualTo(new KeyStats(1, 1, false));
    assertThat(viewB.valueCounts("dimension::tag")).containsExactly("alpha", 1);
    assertThat(viewB.valueCount("dimension::tag", "alpha")).isEqualTo(1);
    assertThat(viewB.valueCount("dimension::tag", "beta")).isEqualTo(0);
  }

  @Test
  public void bind_and_overlayView_disambiguatesDuplicateEmulatorUuidAcrossHostsInSameUniverse() {
    LabQueryResult labResult =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .addLabData(
                        LabData.newBuilder()
                            .setLabInfo(
                                LabInfo.newBuilder()
                                    .setLabLocator(LabLocator.newBuilder().setHostName("host-a")))
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder()
                                                    .setId("emulator-5554")))))
                    .addLabData(
                        LabData.newBuilder()
                            .setLabInfo(
                                LabInfo.newBuilder()
                                    .setLabLocator(LabLocator.newBuilder().setHostName("host-b")))
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder()
                                                    .setId("emulator-5554"))))))
            .build();
    FleetSnapshot snapshot = new FleetIndexBuilder().build(labResult, Instant.ofEpochSecond(1000));

    // Bare "emulator-5554" is ambiguous across host-a and host-b and is omitted from uuidToIndex.
    assertThat(snapshot.uuidToIndex()).doesNotContainKey(DeviceKey.ofUuid("emulator-5554"));

    DimensionOverlayRaw raw =
        DimensionOverlayRaw.createForDevices(
            "dimension::carrier",
            ImmutableMap.of(
                new DeviceKey(UniverseScope.SELF, "host-a", "emulator-5554"),
                ImmutableList.of("Verizon"),
                new DeviceKey(UniverseScope.SELF, "host-b", "emulator-5554"),
                ImmutableList.of("T-Mobile"),
                DeviceKey.ofUuid("emulator-5554"),
                ImmutableList.of("AmbiguousIgnored")));

    DimensionOverlay overlay = DimensionOverlay.create(raw, snapshot);
    OverlayView view = OverlayView.bind(snapshot, ImmutableMap.of("dimension::carrier", overlay));

    assertThat(view.getPostings("dimension::carrier", "verizon")).asList().containsExactly(0);
    assertThat(view.getPostings("dimension::carrier", "t-mobile")).asList().containsExactly(1);
    assertThat(view.postingsForKey("dimension::carrier").get("verizon"))
        .asList()
        .containsExactly(0);
    assertThat(view.postingsForKey("dimension::carrier").get("t-mobile"))
        .asList()
        .containsExactly(1);
    assertThat(view.postingsForKey("dimension::unknown")).isEmpty();
    assertThat(view.displayValues(0, "dimension::carrier")).containsExactly("Verizon");
    assertThat(view.displayValues(1, "dimension::carrier")).containsExactly("T-Mobile");
    assertThat(view.displayValues(0, "dimension::unknown")).isEmpty();
    assertThat(view.displayValues(-1, "dimension::carrier")).isEmpty();
    assertThat(view.displayValues(2, "dimension::carrier")).isEmpty();
    assertThat(view.valuesForKey(0, "dimension::carrier")).containsExactly("verizon");
    assertThat(view.valuesForKey(1, "dimension::carrier")).containsExactly("t-mobile");
    assertThat(view.valuesForKey(0, "dimension::unknown")).isEmpty();
    assertThat(view.valuesForKey(-1, "dimension::carrier")).isEmpty();
    assertThat(view.valuesForKey(2, "dimension::carrier")).isEmpty();
    assertThat(view.valueCounts("dimension::unknown")).isEmpty();
    assertThat(view.valueCount("dimension::unknown", "verizon")).isEqualTo(0);
    assertThat(view.keyStats("dimension::unknown")).isEqualTo(KeyStats.EMPTY);
    assertThat(view.getPostings("dimension::unknown", "verizon")).isEmpty();
    assertThat(overlay.bind(snapshot).displayValuesForIndex(-1)).isEmpty();
    assertThat(overlay.bind(snapshot).displayValuesForIndex(2)).isEmpty();
    assertThat(CoreFleetRawData.ofLabData(labResult).labData()).isSameInstanceAs(labResult);
    assertThat(
            CoreFleetRawData.builder()
                .setLabDataByUniverse(
                    ImmutableMap.of(
                        UniverseScope.SELF,
                        labResult,
                        new UniverseScope.RoutedUniverse("u1"),
                        labResult))
                .build()
                .labData()
                .getLabView()
                .getLabDataCount())
        .isEqualTo(4);
    assertThrows(
        IllegalStateException.class,
        () ->
            CoreFleetRawData.builder()
                .setLabData(LabQueryResult.getDefaultInstance())
                .setLabDataByUniverse(ImmutableMap.of())
                .build());
  }

  @Test
  public void
      bind_fallsBackToUniverseScopedKeyWhenUniqueAndDoesNotShadowExactOrCrossUniverseMatches() {
    UniverseScope ctrl1 = new UniverseScope.RoutedUniverse("ctrl-1");
    UniverseScope ctrl2 = new UniverseScope.RoutedUniverse("ctrl-2");
    LabQueryResult ctrl1Result =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .addLabData(
                        LabData.newBuilder()
                            .setLabInfo(
                                LabInfo.newBuilder()
                                    .setLabLocator(LabLocator.newBuilder().setHostName("host-b")))
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder()
                                                    .setId("emulator-5554")))))
                    .addLabData(
                        LabData.newBuilder()
                            .setLabInfo(
                                LabInfo.newBuilder()
                                    .setLabLocator(LabLocator.newBuilder().setHostName("host-c")))
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder().setId("unique-dev")))
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder()
                                                    .setId("only-in-ctrl1"))))))
            .build();
    FleetSnapshot snapshot =
        new FleetIndexBuilder()
            .build(
                CoreFleetRawData.builder()
                    .setLabDataByUniverse(ImmutableMap.of(ctrl1, ctrl1Result))
                    .build(),
                Instant.ofEpochSecond(1000));

    // 1. emulator-5554 was on both host-a and host-b when the overlay was pulled, and host-a went
    //    offline in the snapshot (leaving only host-b at index 0). Because (ctrl1, "emulator-5554")
    //    is not unique in deviceValues, host-a's entry does not fall back onto host-b's index, so
    //    host-b's exact match ("T-Mobile") wins.
    // 2. unique-dev moved from host-a to host-c inside ctrl-1 (index 1): unique in deviceValues, so
    //    it resolves via the universe-scoped fallback (ctrl1, "", "unique-dev").
    // 3. (ctrl2, "host-d", "only-in-ctrl1") is in a different universe and never falls back across
    //    universes onto ctrl-1's only-in-ctrl1 (index 2).
    DimensionOverlayRaw raw =
        DimensionOverlayRaw.createForDevices(
            "dimension::carrier",
            ImmutableMap.of(
                new DeviceKey(ctrl1, "host-a", "emulator-5554"),
                ImmutableList.of("Verizon"),
                new DeviceKey(ctrl1, "host-b", "emulator-5554"),
                ImmutableList.of("T-Mobile"),
                new DeviceKey(ctrl1, "host-a", "unique-dev"),
                ImmutableList.of("AT&T"),
                new DeviceKey(ctrl2, "host-d", "only-in-ctrl1"),
                ImmutableList.of("CrossUniverseIgnored")));

    DimensionOverlay overlay = DimensionOverlay.create(raw, snapshot);
    OverlayView view = OverlayView.bind(snapshot, ImmutableMap.of("dimension::carrier", overlay));

    assertThat(view.displayValues(0, "dimension::carrier")).containsExactly("T-Mobile");
    assertThat(view.displayValues(1, "dimension::carrier")).containsExactly("AT&T");
    assertThat(view.displayValues(2, "dimension::carrier")).isEmpty();
  }
}
