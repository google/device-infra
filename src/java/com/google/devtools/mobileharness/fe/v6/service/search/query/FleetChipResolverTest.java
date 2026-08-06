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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ComplexMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ContainsSubstring;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetChipResolverRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetChipResolverResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetResolvedFilterChip;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetResolvedGroupByChip;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesExactly;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.NoValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.inject.Guice;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetChipResolver}. */
@RunWith(JUnit4.class)
public final class FleetChipResolverTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  // Synthetic fleet built through the real index builder:
  //   device-0: IDLE, owner alice, dim os=android, dim model="Pixel 8".
  //   device-1: IDLE, owner alice, dim os=android, dim model="Pixel 8".
  //   device-2: BUSY, owner bob,   dim os=ios,     dim model="Galaxy".
  //   device-3: IDLE, owner alice, no os, no model.
  // Both FleetIndexBuilder and FleetChipResolver have package-private @Inject constructors, so
  // obtain
  // them through Guice rather than constructing directly.
  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final FleetChipResolver resolver =
      Guice.createInjector().getInstance(FleetChipResolver.class);

  @Test
  public void simpleSingleValue_pillAndMetadata() {
    // A lowercase input value proves the resolver looks up original casing via valueDisplays.
    FleetChipResolverResponse response =
        resolver.resolve(snapshot, request(simple("field::status", "idle")));

    assertThat(response.getFilterChipsCount()).isEqualTo(1);
    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.getPillKey()).isEqualTo("Status");
    assertThat(chip.getPillCondition()).isEqualTo("IDLE");
    assertThat(chip.getMetadata().getKeyDisplayName()).isEqualTo("Status");
    assertThat(chip.getMetadata().getIsPlural()).isFalse();
    assertThat(chip.getMetadata().getCanUseAdvanced()).isTrue();
  }

  @Test
  public void simpleMultiValue_showsCount() {
    FleetChipResolverResponse response =
        resolver.resolve(snapshot, request(simple("field::status", "idle", "busy")));

    assertThat(response.getFilterChips(0).getPillCondition()).isEqualTo("2");
  }

  @Test
  public void negatedSingleValue_notEqualValue() {
    FleetChipResolverResponse response =
        resolver.resolve(snapshot, request(simpleNegated("field::status", "idle")));

    assertThat(response.getFilterChips(0).getPillCondition()).isEqualTo("\u2260 IDLE");
  }

  @Test
  public void negatedMultiValue_notEqualCount() {
    FleetChipResolverResponse response =
        resolver.resolve(snapshot, request(simpleNegated("field::status", "idle", "busy")));

    assertThat(response.getFilterChips(0).getPillCondition()).isEqualTo("\u2260 2");
  }

  @Test
  public void noValueEntry_emptyText() {
    assertThat(
            resolver
                .resolve(snapshot, request(noValue("dim::os", false)))
                .getFilterChips(0)
                .getPillCondition())
        .isEqualTo("empty");
    assertThat(
            resolver
                .resolve(snapshot, request(noValue("dim::os", true)))
                .getFilterChips(0)
                .getPillCondition())
        .isEqualTo("not empty");
  }

  @Test
  public void pluralKey_owner_isPluralMetadata() {
    // The compact condition text does not carry a verb; is_plural is metadata for the frontend.
    FleetChipResolverResponse response =
        resolver.resolve(snapshot, request(simpleNegated("field::owner", "alice")));

    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.getPillKey()).isEqualTo("Owners");
    assertThat(chip.getPillCondition()).isEqualTo("\u2260 alice");
    assertThat(chip.getMetadata().getIsPlural()).isTrue();
  }

  @Test
  public void complexContains_conditionText() {
    FleetChipResolverResponse response =
        resolver.resolve(snapshot, request(contains("dim::model", "pix")));

    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.getPillKey()).isEqualTo("Model");
    assertThat(chip.getPillCondition()).isEqualTo("contains pix");
  }

  @Test
  public void complexMatchesExactly_singleValueOriginalCasing() {
    // A lowercase input resolves back to its original casing "Pixel 8".
    FleetChipResolverResponse response =
        resolver.resolve(snapshot, request(exactly("dim::model", "pixel 8")));

    assertThat(response.getFilterChips(0).getPillCondition()).isEqualTo("is exactly Pixel 8");
  }

  @Test
  public void complexMatchesExactly_multipleValuesShowCount() {
    FleetChipResolverResponse response =
        resolver.resolve(snapshot, request(exactly("dim::model", "pixel 8", "galaxy")));

    assertThat(response.getFilterChips(0).getPillCondition()).isEqualTo("is exactly 2");
  }

  @Test
  public void atsControllerKey_cannotUseAdvanced() {
    FleetChipResolverResponse response =
        resolver.resolve(snapshot, request(simple("host::ats_controller", "controller-a")));

    assertThat(response.getFilterChips(0).getMetadata().getCanUseAdvanced()).isFalse();
  }

  @Test
  public void groupByChips_pillKeyAndDisplayName() {
    FleetChipResolverResponse response =
        resolver.resolve(
            snapshot,
            FleetChipResolverRequest.newBuilder()
                .addGroupByKeys("dim::model")
                .addGroupByKeys("host::host_name")
                .build());

    assertThat(response.getGroupByChipsCount()).isEqualTo(2);
    FleetResolvedGroupByChip model = response.getGroupByChips(0);
    assertThat(model.getPillKey()).isEqualTo("Model");
    assertThat(model.getDisplayName()).isEqualTo("Model");
    FleetResolvedGroupByChip hostName = response.getGroupByChips(1);
    assertThat(hostName.getPillKey()).isEqualTo("Host Name");
    assertThat(hostName.getDisplayName()).isEqualTo("Host Name");
  }

  @Test
  public void resolve_arraysParallelToRequestInOrder() {
    FleetChipResolverResponse response =
        resolver.resolve(
            snapshot,
            FleetChipResolverRequest.newBuilder()
                .addFilters(simple("field::status", "idle"))
                .addFilters(simple("field::owner", "alice"))
                .addFilters(contains("dim::model", "pix"))
                .addGroupByKeys("dim::os")
                .build());

    assertThat(response.getFilterChipsCount()).isEqualTo(3);
    assertThat(response.getFilterChips(0).getPillKey()).isEqualTo("Status");
    assertThat(response.getFilterChips(1).getPillKey()).isEqualTo("Owners");
    assertThat(response.getFilterChips(2).getPillKey()).isEqualTo("Model");
    assertThat(response.getGroupByChipsCount()).isEqualTo(1);
    assertThat(response.getGroupByChips(0).getPillKey()).isEqualTo("OS");
  }

  // --- Request and filter helpers ---

  private static FleetChipResolverRequest request(Filter filter) {
    return FleetChipResolverRequest.newBuilder().addFilters(filter).build();
  }

  private static Filter simple(String key, String... values) {
    SimpleMatch.Builder simple = SimpleMatch.newBuilder();
    for (String value : values) {
      simple.addValues(FilterValue.newBuilder().setValue(value));
    }
    return Filter.newBuilder().setKey(key).setSimple(simple).build();
  }

  private static Filter simpleNegated(String key, String... values) {
    SimpleMatch.Builder simple = SimpleMatch.newBuilder().setNegated(true);
    for (String value : values) {
      simple.addValues(FilterValue.newBuilder().setValue(value));
    }
    return Filter.newBuilder().setKey(key).setSimple(simple).build();
  }

  private static Filter noValue(String key, boolean negated) {
    return Filter.newBuilder()
        .setKey(key)
        .setSimple(
            SimpleMatch.newBuilder()
                .addValues(FilterValue.newBuilder().setNoValue(NoValue.getDefaultInstance()))
                .setNegated(negated))
        .build();
  }

  private static Filter contains(String key, String needle) {
    return Filter.newBuilder()
        .setKey(key)
        .setComplex(
            ComplexMatch.newBuilder()
                .setContainsSubstring(ContainsSubstring.newBuilder().setValue(needle)))
        .build();
  }

  private static Filter exactly(String key, String... values) {
    MatchesExactly.Builder exactly = MatchesExactly.newBuilder();
    for (String value : values) {
      exactly.addValues(value);
    }
    return Filter.newBuilder()
        .setKey(key)
        .setComplex(ComplexMatch.newBuilder().setMatchesExactly(exactly))
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
    return device("device-0", DeviceStatus.IDLE, "alice", "android", "Pixel 8");
  }

  private static DeviceInfo device1() {
    return device("device-1", DeviceStatus.IDLE, "alice", "android", "Pixel 8");
  }

  private static DeviceInfo device2() {
    return device("device-2", DeviceStatus.BUSY, "bob", "ios", "Galaxy");
  }

  private static DeviceInfo device3() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-3"))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(DeviceFeature.newBuilder().addOwner("alice"))
        .build();
  }

  private static DeviceInfo device(
      String id, DeviceStatus status, String owner, String os, String model) {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId(id))
        .setDeviceStatus(status)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addOwner(owner)
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("os", os))
                        .addSupportedDimension(dimension("model", model))))
        .build();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }
}
