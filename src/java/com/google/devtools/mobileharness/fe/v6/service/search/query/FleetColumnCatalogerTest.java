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
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogSection;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.inject.Guice;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetColumnCataloger}. */
@RunWith(JUnit4.class)
public final class FleetColumnCatalogerTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  // Synthetic fleet built through the real index builder, across two hosts:
  //   device-0: IDLE, android, owner alice, model pixel, pool shared, dim host_name lab-a,
  //             plus ten filler dimensions carried only by this device.
  //   device-1: IDLE, android, owner alice, model pixel, pool shared, dim host_name lab-a.
  //   device-2: BUSY, ios,     owner carol, model iphone, pool dedicated, dim host_name lab-b.
  // Every device carries a "host_name" dimension equal to its real host name, so dim::host_name
  // never disagrees with the built-in host::host_name and is detected as redundant. Both hosts
  // carry a "location" host property so a prop:: key exists. The ten filler dimensions push the
  // non-redundant dimension count past the browse top-N so truncation is exercised.
  // FleetIndexBuilder and FleetColumnCataloger have package-private @Inject constructors, so obtain
  // them through Guice rather than constructing directly.
  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final LazyPostings postings = new LazyPostings(snapshot.devices());
  private final FleetColumnCataloger cataloger =
      Guice.createInjector().getInstance(FleetColumnCataloger.class);

  @Test
  public void browse_sectionsInOrder() {
    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(
            snapshot, FleetColumnCatalogRequest.getDefaultInstance(), postings);

    // No filters, no recents, no query: the suggested and search sections are absent, and the three
    // browse sections appear in their fixed order.
    assertThat(headings(response))
        .containsExactly("Built-in fields", "Dimensions", "Host properties")
        .inOrder();
  }

  @Test
  public void builtinSection_listsBuiltinsSortedByDisplayName() {
    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(
            snapshot, FleetColumnCatalogRequest.getDefaultInstance(), postings);

    // Built-ins are the field::, host::, and config:: keys, listed in full and sorted by display
    // name: Host IP, Host Name, Owners, Status, Type, UUID.
    FleetColumnCatalogSection builtin = section(response, "Built-in fields");
    assertThat(keys(builtin))
        .containsExactly(
            "host::host_ip",
            "host::host_name",
            "field::owner",
            "field::status",
            "field::type",
            "field::uuid")
        .inOrder();
    // A full section reports no total.
    assertThat(builtin.getTotalAvailable()).isEqualTo(0);
  }

  @Test
  public void dimensionsSection_excludesRedundantDimAndReportsTotal() {
    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(
            snapshot, FleetColumnCatalogRequest.getDefaultInstance(), postings);

    FleetColumnCatalogSection dimensions = section(response, "Dimensions");
    // dim::host_name is present in the fleet but restates host::host_name on every device, so it is
    // detected as redundant and dropped from the dimensions section.
    assertThat(snapshot.index().keyIds()).contains("dim::host_name");
    assertThat(keys(dimensions)).doesNotContain("dim::host_name");

    // The non-redundant dimensions are model, pool, quarantined, and the ten fillers: thirteen in
    // all. The section is cut to the browse top-N of ten, and total_available carries the full
    // count so the frontend can show "showing 10 of 13".
    assertThat(dimensions.getTotalAvailable()).isEqualTo(13);
    assertThat(dimensions.getEntriesCount()).isEqualTo(10);
  }

  @Test
  public void deviceCount_countsDistinctDevicesCarryingTheKey() {
    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(
            snapshot, FleetColumnCatalogRequest.getDefaultInstance(), postings);

    // Status is carried by all three devices.
    assertThat(entryFor(section(response, "Built-in fields"), "field::status").getDeviceCount())
        .isEqualTo(3);
    // model is carried by all three devices (pixel on two, iphone on one).
    assertThat(entryFor(section(response, "Dimensions"), "dim::model").getDeviceCount())
        .isEqualTo(3);
    // The location host property is carried by both hosts, so all three devices.
    assertThat(entryFor(section(response, "Host properties"), "prop::location").getDeviceCount())
        .isEqualTo(3);
  }

  @Test
  public void suggested_fromActiveFiltersAndRecentKeys() {
    FleetColumnCatalogRequest request =
        FleetColumnCatalogRequest.newBuilder()
            .addFilters(Filter.newBuilder().setKey("dim::model"))
            .addRecentKeys("field::status")
            .build();

    FleetColumnCatalogResponse response = cataloger.getColumnCatalog(snapshot, request, postings);

    // The suggested section leads, before the browse sections.
    assertThat(headings(response).get(0)).isEqualTo("Suggested for you");
    FleetColumnCatalogSection suggested = section(response, "Suggested for you");
    assertThat(keys(suggested)).containsExactly("dim::model", "field::status").inOrder();
    assertThat(entryFor(suggested, "dim::model").getReason()).isEqualTo("in your active filters");
    assertThat(entryFor(suggested, "field::status").getReason()).isEqualTo("recently used");
    // The suggested section reports no total.
    assertThat(suggested.getTotalAvailable()).isEqualTo(0);
  }

  @Test
  public void suggested_omittedWhenNothingToSuggest() {
    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(
            snapshot, FleetColumnCatalogRequest.getDefaultInstance(), postings);

    assertThat(headings(response)).doesNotContain("Suggested for you");
  }

  @Test
  public void search_presentOnlyWithQueryAndMatchesAcrossNamespaces() {
    FleetColumnCatalogResponse withQuery =
        cataloger.getColumnCatalog(
            snapshot, FleetColumnCatalogRequest.newBuilder().setQuery("model").build(), postings);

    FleetColumnCatalogSection search = section(withQuery, "Search results");
    assertThat(keys(search)).contains("dim::model");
    assertThat(search.getTotalAvailable()).isEqualTo(1);

    // With no query there is no search section.
    FleetColumnCatalogResponse noQuery =
        cataloger.getColumnCatalog(
            snapshot, FleetColumnCatalogRequest.getDefaultInstance(), postings);
    assertThat(headings(noQuery)).doesNotContain("Search results");
  }

  // --- Helpers ---

  private static ImmutableList<String> headings(FleetColumnCatalogResponse response) {
    ImmutableList.Builder<String> headings = ImmutableList.builder();
    for (FleetColumnCatalogSection section : response.getSectionsList()) {
      headings.add(section.getHeading());
    }
    return headings.build();
  }

  private static FleetColumnCatalogSection section(
      FleetColumnCatalogResponse response, String heading) {
    for (FleetColumnCatalogSection section : response.getSectionsList()) {
      if (section.getHeading().equals(heading)) {
        return section;
      }
    }
    throw new AssertionError("no section " + heading);
  }

  private static ImmutableList<String> keys(FleetColumnCatalogSection section) {
    ImmutableList.Builder<String> keys = ImmutableList.builder();
    for (FleetColumnCatalogEntry entry : section.getEntriesList()) {
      keys.add(entry.getKey());
    }
    return keys.build();
  }

  private static FleetColumnCatalogEntry entryFor(FleetColumnCatalogSection section, String key) {
    for (FleetColumnCatalogEntry entry : section.getEntriesList()) {
      if (entry.getKey().equals(key)) {
        return entry;
      }
    }
    throw new AssertionError("no entry " + key + " in section " + section.getHeading());
  }

  // --- Synthetic fleet ---

  private static LabQueryResult fleet() {
    Map<String, String> device0Dims = new LinkedHashMap<>();
    device0Dims.put("model", "pixel");
    device0Dims.put("pool", "shared");
    device0Dims.put("host_name", "lab-a");
    for (int i = 1; i <= 10; i++) {
      device0Dims.put(String.format("f%02d", i), "x");
    }

    DeviceInfo device0 =
        device("device-0", DeviceStatus.IDLE, "android_real_device", "alice", device0Dims);
    DeviceInfo device1 =
        device(
            "device-1",
            DeviceStatus.IDLE,
            "android_real_device",
            "alice",
            dims("model", "pixel", "pool", "shared", "host_name", "lab-a"));
    DeviceInfo device2 =
        device(
            "device-2",
            DeviceStatus.BUSY,
            "ios_real_device",
            "carol",
            dims("model", "iphone", "pool", "dedicated", "host_name", "lab-b"));

    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(2)
                .addLabData(labData("lab-a", "1.1.1.1", "mtv", device0, device1))
                .addLabData(labData("lab-b", "2.2.2.2", "nyc", device2)))
        .build();
  }

  private static LabData labData(
      String hostName, String ip, String location, DeviceInfo... devices) {
    DeviceList.Builder deviceList = DeviceList.newBuilder().setDeviceTotalCount(devices.length);
    for (DeviceInfo device : devices) {
      deviceList.addDeviceInfo(device);
    }
    return LabData.newBuilder()
        .setLabInfo(
            LabInfo.newBuilder()
                .setLabLocator(LabLocator.newBuilder().setHostName(hostName).setIp(ip))
                .setLabStatus(LabStatus.LAB_RUNNING)
                .setLabServerFeature(
                    LabServerFeature.newBuilder()
                        .setHostProperties(
                            HostProperties.newBuilder()
                                .addHostProperty(
                                    HostProperty.newBuilder()
                                        .setKey("location")
                                        .setValue(location)))))
        .setDeviceList(deviceList)
        .build();
  }

  private static DeviceInfo device(
      String id, DeviceStatus status, String type, String owner, Map<String, String> dimensions) {
    DeviceCompositeDimension.Builder composite = DeviceCompositeDimension.newBuilder();
    for (Map.Entry<String, String> entry : dimensions.entrySet()) {
      composite.addSupportedDimension(dimension(entry.getKey(), entry.getValue()));
    }
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId(id))
        .setDeviceStatus(status)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType(type)
                .addOwner(owner)
                .setCompositeDimension(composite))
        .build();
  }

  private static Map<String, String> dims(String... nameValuePairs) {
    Map<String, String> map = new LinkedHashMap<>();
    for (int i = 0; i < nameValuePairs.length; i += 2) {
      map.put(nameValuePairs[i], nameValuePairs[i + 1]);
    }
    return map;
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }
}
