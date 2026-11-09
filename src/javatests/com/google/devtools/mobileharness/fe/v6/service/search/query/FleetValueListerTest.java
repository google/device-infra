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

package com.google.devtools.mobileharness.fe.v6.service.search.query;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetCountedValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetCountedValueList;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPlainValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetValueListRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetValueListResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeyRegistry;
import com.google.inject.Guice;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetValueLister}. */
@RunWith(JUnit4.class)
public final class FleetValueListerTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  // Synthetic fleet built through the real index builder:
  //   device-0: IDLE, owner alice, dim os=android.
  //   device-1: IDLE, owner alice, dim os=android.
  //   device-2: BUSY, owner bob,   dim os=ios.
  //   device-3: IDLE, owner alice, no os dimension (drives the os no-value entry).
  // FleetIndexBuilder and FleetValueLister both have package-private @Inject constructors, so
  // obtain
  // them through Guice rather than constructing directly.
  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final LazyPostings postings = new LazyPostings(snapshot.devices());
  private final DeviceCorpus corpus = new DeviceCorpus(snapshot, postings, null);
  private final FleetValueLister lister =
      Guice.createInjector().getInstance(FleetValueLister.class);

  @Test
  public void countedKey_noFilters_filteredEqualsTotal() {
    FleetValueListResponse response =
        lister.listValues(corpus, "device_field::status", ImmutableList.of());

    assertThat(response.getKindCase()).isEqualTo(FleetValueListResponse.KindCase.COUNTED);
    FleetCountedValueList counted = response.getCounted();

    // Sorted by filtered count descending: IDLE (3) before BUSY (1).
    assertThat(valuesOf(counted)).containsExactly("IDLE", "BUSY").inOrder();
    FleetCountedValue idle = byValue(counted, "IDLE");
    assertThat(idle.getDisplayLabel()).isEqualTo("IDLE");
    assertThat(idle.getFiltered()).isEqualTo(3);
    assertThat(idle.getTotal()).isEqualTo(3);
    FleetCountedValue busy = byValue(counted, "BUSY");
    assertThat(busy.getFiltered()).isEqualTo(1);
    assertThat(busy.getTotal()).isEqualTo(1);
  }

  @Test
  public void countedKey_otherFilterApplied_filteredBelowTotal() {
    FleetValueListResponse response =
        lister.listValues(
            corpus,
            "device_field::status",
            ImmutableList.of(simple("device_field::owner", "alice")));

    FleetCountedValueList counted = response.getCounted();
    // Owner alice covers device-0, device-1, device-3, all IDLE.
    FleetCountedValue idle = byValue(counted, "IDLE");
    assertThat(idle.getFiltered()).isEqualTo(3);
    assertThat(idle.getTotal()).isEqualTo(3);
    // The only BUSY device (device-2) is owned by bob, so it drops out of the filtered count.
    FleetCountedValue busy = byValue(counted, "BUSY");
    assertThat(busy.getFiltered()).isEqualTo(0);
    assertThat(busy.getTotal()).isEqualTo(1);

    // Filtered descending puts IDLE (3) ahead of BUSY (0).
    assertThat(valuesOf(counted)).containsExactly("IDLE", "BUSY").inOrder();
  }

  @Test
  public void ownChipExcluded_doesNotShrinkOwnValueList() {
    // Filtering on the same key must not collapse its own picker to the selected value.
    FleetValueListResponse response =
        lister.listValues(
            corpus,
            "device_field::status",
            ImmutableList.of(simple("device_field::status", "BUSY")));

    FleetCountedValueList counted = response.getCounted();
    assertThat(valuesOf(counted)).containsExactly("IDLE", "BUSY").inOrder();
    // Both values keep their full fleet counts because the status chip is excluded.
    assertThat(byValue(counted, "IDLE").getFiltered()).isEqualTo(3);
    assertThat(byValue(counted, "BUSY").getFiltered()).isEqualTo(1);
  }

  @Test
  public void noValueEntry_presentWhenSomeDevicesLackKey() {
    FleetValueListResponse response =
        lister.listValues(corpus, "dimension::os", ImmutableList.of());

    FleetCountedValueList counted = response.getCounted();
    assertThat(valuesOf(counted)).containsExactly("android", "ios").inOrder();
    assertThat(byValue(counted, "android").getTotal()).isEqualTo(2);
    assertThat(byValue(counted, "ios").getTotal()).isEqualTo(1);

    // device-3 has no os dimension.
    assertThat(counted.hasNoValueEntry()).isTrue();
    assertThat(counted.getNoValueEntry().getTotal()).isEqualTo(1);
    assertThat(counted.getNoValueEntry().getFiltered()).isEqualTo(1);
  }

  @Test
  public void noValueEntry_filteredBelowTotalUnderOtherFilter() {
    FleetValueListResponse response =
        lister.listValues(
            corpus, "dimension::os", ImmutableList.of(simple("device_field::status", "BUSY")));

    FleetCountedValueList counted = response.getCounted();
    // Only device-2 (BUSY, os=ios) survives the filter.
    assertThat(byValue(counted, "ios").getFiltered()).isEqualTo(1);
    assertThat(byValue(counted, "android").getFiltered()).isEqualTo(0);
    // Filtered descending: ios (1) before android (0).
    assertThat(valuesOf(counted)).containsExactly("ios", "android").inOrder();

    // device-3 lacks os but is IDLE, so it is outside the BUSY filtered set.
    assertThat(counted.hasNoValueEntry()).isTrue();
    assertThat(counted.getNoValueEntry().getTotal()).isEqualTo(1);
    assertThat(counted.getNoValueEntry().getFiltered()).isEqualTo(0);
  }

  @Test
  public void identifierKey_returnsPlainListSortedByValue() {
    FleetValueListResponse response =
        lister.listValues(corpus, "device_field::uuid", ImmutableList.of());

    assertThat(response.getKindCase()).isEqualTo(FleetValueListResponse.KindCase.PLAIN);
    ImmutableList.Builder<String> values = ImmutableList.builder();
    for (FleetPlainValue value : response.getPlain().getValuesList()) {
      values.add(value.getValue());
    }
    assertThat(values.build())
        .containsExactly("device-0", "device-1", "device-2", "device-3")
        .inOrder();
    // Every device carries a uuid, so there is no no-value entry.
    assertThat(response.getPlain().hasNoValueEntry()).isFalse();
  }

  @Test
  public void hostCorpus_hostNameIsPlain_deviceCountIsCounted() {
    HostCorpus hostCorpus = new HostCorpus(snapshot, LazyPostings.forHosts(snapshot.hosts()), null);

    // Host name is an identifier: plain list without counts.
    FleetValueListResponse hostNameResp =
        lister.listValues(hostCorpus, "host_field::host_name", ImmutableList.of());
    assertThat(hostNameResp.getKindCase()).isEqualTo(FleetValueListResponse.KindCase.PLAIN);

    // Device count is a discrete facet metric: counted list WITH counts!
    FleetValueListResponse countResp =
        lister.listValues(hostCorpus, "host_field::device_count", ImmutableList.of());
    assertThat(countResp.getKindCase()).isEqualTo(FleetValueListResponse.KindCase.COUNTED);
    assertThat(valuesOf(countResp.getCounted())).containsExactly("2");
    assertThat(byValue(countResp.getCounted(), "2").getTotal()).isEqualTo(2);
  }

  @Test
  public void unknownKey_returnsEmptyCountedListWithoutNoValueEntry() {
    FleetValueListResponse response =
        lister.listValues(corpus, "dimension::does_not_exist", ImmutableList.of());

    assertThat(response.getKindCase()).isEqualTo(FleetValueListResponse.KindCase.COUNTED);
    assertThat(response.getCounted().getValuesList()).isEmpty();
    // An unknown key must not report the whole fleet as lacking it.
    assertThat(response.getCounted().hasNoValueEntry()).isFalse();
  }

  @Test
  public void extractOverlayKeys_sameLongTailDimensionInKeyAndFilter_deduplicates() {
    AtsDeviceKeyRegistry registry = new AtsDeviceKeyRegistry();
    FleetValueListRequest request =
        FleetValueListRequest.newBuilder()
            .setKey("dimension::provisioned_by")
            .addFilters(simple("dimension::provisioned_by", "foo"))
            .build();

    assertThat(FleetValueLister.extractOverlayKeys(registry, request)).hasSize(1);
  }

  @Test
  public void countedKey_equalFilteredCounts_tieBreaksNumerically() {
    LabQueryResult tempFleet =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData(
                            "lab1",
                            "1.1.1.1",
                            deviceWithTemp("dev-0", "9"),
                            deviceWithTemp("dev-1", "38"),
                            deviceWithTemp("dev-2", "-1"),
                            deviceWithTemp("dev-3", "25"))))
            .build();
    FleetSnapshot tempSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(tempFleet, BUILD_TIME);
    DeviceCorpus tempCorpus =
        new DeviceCorpus(tempSnapshot, new LazyPostings(tempSnapshot.devices()), null);

    FleetValueListResponse response =
        lister.listValues(tempCorpus, "dimension::battery_temperature", ImmutableList.of());

    assertThat(valuesOf(response.getCounted())).containsExactly("-1", "9", "25", "38").inOrder();
  }

  @Test
  public void countedKey_equalFilteredCounts_selectsVersionOrDecimalModePerKey() {
    LabQueryResult mixedFleet =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData(
                            "lab1",
                            "1.1.1.1",
                            deviceWithDimension("dev-0", "software_version", "17.10"),
                            deviceWithDimension("dev-1", "software_version", "0.10"),
                            deviceWithDimension("dev-2", "software_version", "17.4"),
                            deviceWithDimension("dev-3", "software_version", "0.9"),
                            deviceWithDimension("dev-4", "voltage", "1.5"),
                            deviceWithDimension("dev-5", "voltage", "-1.25"),
                            deviceWithDimension("dev-6", "voltage", "1.25"),
                            deviceWithDimension("dev-7", "voltage", "-1.5"))))
            .build();
    FleetSnapshot mixedSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(mixedFleet, BUILD_TIME);
    DeviceCorpus mixedCorpus =
        new DeviceCorpus(mixedSnapshot, new LazyPostings(mixedSnapshot.devices()), null);

    FleetValueListResponse versionResponse =
        lister.listValues(mixedCorpus, "dimension::software_version", ImmutableList.of());
    assertThat(valuesOf(versionResponse.getCounted()))
        .containsExactly("0.9", "0.10", "17.4", "17.10")
        .inOrder();

    FleetValueListResponse voltageResponse =
        lister.listValues(mixedCorpus, "dimension::voltage", ImmutableList.of());
    assertThat(valuesOf(voltageResponse.getCounted()))
        .containsExactly("-1.5", "-1.25", "1.25", "1.5")
        .inOrder();
  }

  @Test
  public void identifierHexKeys_returnPlainListInLexicographicalOrder() {
    LabQueryResult hexFleet =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData(
                            "lab1",
                            "1.1.1.1",
                            deviceWithDimension("1aef0001", "mac_address", "1a:00:00:00:00:01"),
                            deviceWithDimension("09ef0001", "mac_address", "09:00:00:00:00:01"),
                            deviceWithDimension("10ef0001", "mac_address", "10:00:00:00:00:01"),
                            deviceWithDimension("0aef0001", "mac_address", "0a:00:00:00:00:01"))))
            .build();
    FleetSnapshot hexSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(hexFleet, BUILD_TIME);
    DeviceCorpus hexCorpus =
        new DeviceCorpus(hexSnapshot, new LazyPostings(hexSnapshot.devices()), null);

    FleetValueListResponse uuidResponse =
        lister.listValues(hexCorpus, "device_field::uuid", ImmutableList.of());
    assertThat(
            uuidResponse.getPlain().getValuesList().stream()
                .map(FleetPlainValue::getValue)
                .collect(toImmutableList()))
        .containsExactly("09ef0001", "0aef0001", "10ef0001", "1aef0001")
        .inOrder();

    FleetValueListResponse macResponse =
        lister.listValues(hexCorpus, "dimension::mac_address", ImmutableList.of());
    assertThat(
            macResponse.getPlain().getValuesList().stream()
                .map(FleetPlainValue::getValue)
                .collect(toImmutableList()))
        .containsExactly(
            "09:00:00:00:00:01", "0a:00:00:00:00:01", "10:00:00:00:00:01", "1a:00:00:00:00:01")
        .inOrder();
  }

  // --- Helpers ---

  private static ImmutableList<String> valuesOf(FleetCountedValueList list) {
    return list.getValuesList().stream()
        .map(FleetCountedValue::getValue)
        .collect(toImmutableList());
  }

  private static FleetCountedValue byValue(FleetCountedValueList list, String value) {
    for (FleetCountedValue entry : list.getValuesList()) {
      if (entry.getValue().equals(value)) {
        return entry;
      }
    }
    throw new AssertionError("no counted value with raw value " + value);
  }

  private static Filter simple(String key, String value) {
    return Filter.newBuilder()
        .setKey(key)
        .setSimple(SimpleMatch.newBuilder().addValues(FilterValue.newBuilder().setValue(value)))
        .build();
  }

  // --- Synthetic fleet ---

  private static LabQueryResult fleet() {
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(2)
                .addLabData(labData("lab1", "1.1.1.1", device0(), device1()))
                .addLabData(labData("lab2", "2.2.2.2", device2(), device3())))
        .build();
  }

  private static LabData labData(String hostName, String ip, DeviceInfo... devices) {
    DeviceList.Builder deviceList = DeviceList.newBuilder().setDeviceTotalCount(devices.length);
    for (DeviceInfo device : devices) {
      deviceList.addDeviceInfo(device);
    }
    return LabData.newBuilder()
        .setLabInfo(
            LabInfo.newBuilder()
                .setLabLocator(LabLocator.newBuilder().setHostName(hostName).setIp(ip))
                .setLabStatus(LabStatus.LAB_RUNNING))
        .setDeviceList(deviceList)
        .build();
  }

  private static DeviceInfo device0() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-0"))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addOwner("alice")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("os", "android"))))
        .build();
  }

  private static DeviceInfo device1() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-1"))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addOwner("alice")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("os", "android"))))
        .build();
  }

  private static DeviceInfo device2() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-2"))
        .setDeviceStatus(DeviceStatus.BUSY)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addOwner("bob")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("os", "ios"))))
        .build();
  }

  private static DeviceInfo device3() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-3"))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(DeviceFeature.newBuilder().addOwner("alice"))
        .build();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }

  private static DeviceInfo deviceWithTemp(String id, String temp) {
    return deviceWithDimension(id, "battery_temperature", temp);
  }

  private static DeviceInfo deviceWithDimension(String id, String name, String value) {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId(id))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension(name, value))))
        .build();
  }
}
