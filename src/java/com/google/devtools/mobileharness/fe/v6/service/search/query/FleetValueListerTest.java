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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetValueListResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
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
  private final FleetValueLister lister =
      Guice.createInjector().getInstance(FleetValueLister.class);

  @Test
  public void countedKey_noFilters_filteredEqualsTotal() {
    FleetValueListResponse response =
        lister.listValues(snapshot, "field::status", ImmutableList.of(), postings);

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
            snapshot, "field::status", ImmutableList.of(simple("field::owner", "alice")), postings);

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
            snapshot, "field::status", ImmutableList.of(simple("field::status", "BUSY")), postings);

    FleetCountedValueList counted = response.getCounted();
    assertThat(valuesOf(counted)).containsExactly("IDLE", "BUSY").inOrder();
    // Both values keep their full fleet counts because the status chip is excluded.
    assertThat(byValue(counted, "IDLE").getFiltered()).isEqualTo(3);
    assertThat(byValue(counted, "BUSY").getFiltered()).isEqualTo(1);
  }

  @Test
  public void noValueEntry_presentWhenSomeDevicesLackKey() {
    FleetValueListResponse response =
        lister.listValues(snapshot, "dim::os", ImmutableList.of(), postings);

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
            snapshot, "dim::os", ImmutableList.of(simple("field::status", "BUSY")), postings);

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
        lister.listValues(snapshot, "field::uuid", ImmutableList.of(), postings);

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
  public void unknownKey_returnsEmptyCountedListWithoutNoValueEntry() {
    FleetValueListResponse response =
        lister.listValues(snapshot, "dim::does_not_exist", ImmutableList.of(), postings);

    assertThat(response.getKindCase()).isEqualTo(FleetValueListResponse.KindCase.COUNTED);
    assertThat(response.getCounted().getValuesList()).isEmpty();
    // An unknown key must not report the whole fleet as lacking it.
    assertThat(response.getCounted().hasNoValueEntry()).isFalse();
  }

  // --- Helpers ---

  private static ImmutableList<String> valuesOf(FleetCountedValueList list) {
    ImmutableList.Builder<String> values = ImmutableList.builder();
    for (FleetCountedValue value : list.getValuesList()) {
      values.add(value.getValue());
    }
    return values.build();
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
}
