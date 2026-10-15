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
import com.google.common.collect.ImmutableSet;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.CoverageInfo.DisplayCase;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterKeyCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterKeyCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterKeyEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterKeySection;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionCatalogStore;
import com.google.inject.Guice;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetFilterKeyCataloger}. */
@RunWith(JUnit4.class)
public final class FleetFilterKeyCatalogerTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final LazyPostings devicePostings = new LazyPostings(snapshot.devices());
  private final DeviceCorpus deviceCorpus =
      new DeviceCorpus(snapshot, devicePostings, new AtsCuration());
  private final HostCorpus hostCorpus =
      new HostCorpus(snapshot, LazyPostings.forHosts(snapshot.hosts()), new AtsCuration());
  private final FleetFilterKeyCataloger cataloger = new FleetFilterKeyCataloger();

  @Test
  public void deviceBrowse_listsDeclaredDimensionsOnlyButCountsEveryDimension() {
    FleetFilterKeyCatalogResponse response =
        cataloger.getFilterKeyCatalog(
            deviceCorpus, FleetFilterKeyCatalogRequest.getDefaultInstance());

    assertThat(headings(response))
        .containsExactly("Built-in fields", "Dimensions", "Host properties")
        .inOrder();

    FleetFilterKeySection builtin = section(response, "Built-in fields");
    assertThat(builtin.getTotalAvailable()).isEqualTo(0);

    // The ATS registry declares only dimension::model among the fixture's 13 dimensions. Browse
    // lists that one, and the total still counts all 13, since the rest are one search away.
    FleetFilterKeySection dimensions = section(response, "Dimensions");
    assertThat(keys(dimensions)).containsExactly("dimension::model");
    assertThat(dimensions.getTotalAvailable()).isEqualTo(13);

    // Host properties has 6 properties in the index (host_os from FleetIndexBuilder plus location
    // on both labs and p1..p4 on lab-a); browse truncates to PROP_TOP (5) and reports total 6.
    FleetFilterKeySection properties = section(response, "Host properties");
    assertThat(keys(properties))
        .containsExactly(
            "host_property::host_os",
            "host_property::location",
            "host_property::p1",
            "host_property::p2",
            "host_property::p3")
        .inOrder();
    assertThat(properties.getTotalAvailable()).isEqualTo(6);
  }

  @Test
  public void deviceSearch_reachesLongTailDimensionsThatBrowseHides() {
    // Unlike the column catalog, the filter-key catalog keeps dimensions that restate built-ins
    // (such as dimension::host_name) so they remain pickable as filter keys.
    FleetFilterKeyCatalogResponse response =
        cataloger.getFilterKeyCatalog(
            deviceCorpus, FleetFilterKeyCatalogRequest.newBuilder().setQuery("host_name").build());

    assertThat(keys(section(response, "Dimensions"))).containsExactly("dimension::host_name");
  }

  @Test
  public void deviceBrowse_showsCoverageForDimensionsOnlyAndMarksAppliedFilters() {
    FleetFilterKeyCatalogRequest request =
        FleetFilterKeyCatalogRequest.newBuilder()
            .addFilters(Filter.newBuilder().setKey("device_field::status"))
            .addFilters(Filter.newBuilder().setKey("dimension::model"))
            .build();

    FleetFilterKeyCatalogResponse response = cataloger.getFilterKeyCatalog(deviceCorpus, request);

    FleetFilterKeyEntry statusEntry =
        entryFor(section(response, "Built-in fields"), "device_field::status");
    assertThat(statusEntry.getCoverage().getDisplayCase()).isEqualTo(DisplayCase.NOT_SHOWN);
    assertThat(statusEntry.getApplied()).isTrue();
    assertThat(statusEntry.getMetadata().getKeyDisplayName()).isEqualTo("Status");
    assertThat(statusEntry.getMetadata().getCanUseAdvanced()).isTrue();
    assertThat(statusEntry.getMetadata().getIsPlural()).isFalse();

    FleetFilterKeyEntry modelEntry = entryFor(section(response, "Dimensions"), "dimension::model");
    assertThat(modelEntry.getCoverage().getDisplayCase()).isEqualTo(DisplayCase.SHOWN);
    assertThat(modelEntry.getCoverage().getShown().getCount()).isEqualTo(3);
    assertThat(modelEntry.getApplied()).isTrue();
    assertThat(modelEntry.getMetadata().getKeyDisplayName()).isEqualTo("Model");

    FleetFilterKeyEntry locationEntry =
        entryFor(section(response, "Host properties"), "host_property::location");
    assertThat(locationEntry.getCoverage().getDisplayCase()).isEqualTo(DisplayCase.NOT_SHOWN);
    assertThat(locationEntry.getApplied()).isFalse();
    assertThat(locationEntry.getMetadata().getKeyDisplayName()).isEqualTo("Host Property location");
  }

  @Test
  public void deviceSearch_filtersCategoriesAndIncludesCatalogOnlyDimensionsWithNotShownCoverage() {
    DimensionCatalogStore store = new DimensionCatalogStore();
    store.setDimensionNames(Fleet.FLEET_SELF, ImmutableSet.of("custom_catalog_dim"));
    FleetFilterKeyCataloger customCataloger = new FleetFilterKeyCataloger(store);

    FleetFilterKeyCatalogResponse response =
        customCataloger.getFilterKeyCatalog(
            deviceCorpus,
            FleetFilterKeyCatalogRequest.newBuilder().setQuery("custom_catalog_dim").build());

    assertThat(headings(response)).containsExactly("Dimensions");
    FleetFilterKeySection dimensions = section(response, "Dimensions");
    assertThat(keys(dimensions)).containsExactly("dimension::custom_catalog_dim");
    FleetFilterKeyEntry entry = entryFor(dimensions, "dimension::custom_catalog_dim");
    assertThat(entry.getCoverage().getDisplayCase()).isEqualTo(DisplayCase.NOT_SHOWN);
    assertThat(entry.getMetadata().getKeyDisplayName()).isEqualTo("Dimension custom_catalog_dim");
  }

  @Test
  public void deviceSearch_exactNameLeadsEvenWithUnknownCoverage() {
    // All four catalog-only dimensions have coverage 0, so their unsearched order is alphabetical
    // ("a_dimension_f", "a_f0_sub", "f0", "f0_prefix", "f_dim"). Search ranks exact ahead of
    // prefix (by bareName or displayName) ahead of substring.
    DimensionCatalogStore store = new DimensionCatalogStore();
    store.setDimensionNames(
        Fleet.FLEET_SELF, ImmutableSet.of("f0", "f0_prefix", "a_f0_sub", "f_dim", "a_dimension_f"));
    FleetFilterKeyCataloger customCataloger = new FleetFilterKeyCataloger(store);

    FleetFilterKeyCatalogResponse response =
        customCataloger.getFilterKeyCatalog(
            deviceCorpus, FleetFilterKeyCatalogRequest.newBuilder().setQuery("f0").build());

    ImmutableList<String> dimensionKeys = keys(section(response, "Dimensions"));
    assertThat(dimensionKeys.get(0)).isEqualTo("dimension::f0");
    assertThat(dimensionKeys)
        .containsAtLeast(
            "dimension::f0", "dimension::f01", "dimension::f0_prefix", "dimension::a_f0_sub")
        .inOrder();
    assertThat(
            entryFor(section(response, "Dimensions"), "dimension::f0")
                .getCoverage()
                .getDisplayCase())
        .isEqualTo(DisplayCase.NOT_SHOWN);

    // "Dimension f_dim" starts with "dimension f" by display name, while "a_dimension_f" only
    // contains it as a substring.
    FleetFilterKeyCatalogResponse displayPrefixResponse =
        customCataloger.getFilterKeyCatalog(
            deviceCorpus,
            FleetFilterKeyCatalogRequest.newBuilder().setQuery("dimension f").build());
    assertThat(keys(section(displayPrefixResponse, "Dimensions")))
        .containsAtLeast("dimension::f_dim", "dimension::a_dimension_f")
        .inOrder();
  }

  @Test
  public void hostBrowseAndSearch_omitsDimensionsAndShowsNoCoverage() {
    FleetFilterKeyCatalogRequest browseRequest =
        FleetFilterKeyCatalogRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
            .addFilters(Filter.newBuilder().setKey("host_property::location"))
            .build();

    FleetFilterKeyCatalogResponse browseResponse =
        cataloger.getFilterKeyCatalog(hostCorpus, browseRequest);

    assertThat(headings(browseResponse))
        .containsExactly("Built-in fields", "Host properties")
        .inOrder();

    FleetFilterKeyEntry connectivity =
        entryFor(section(browseResponse, "Built-in fields"), "host_field::connectivity");
    assertThat(connectivity.getMetadata().getKeyDisplayName()).isEqualTo("Lab Server Connectivity");
    assertThat(connectivity.getCoverage().getDisplayCase()).isEqualTo(DisplayCase.NOT_SHOWN);
    assertThat(connectivity.getApplied()).isFalse();

    FleetFilterKeySection hostProperties = section(browseResponse, "Host properties");
    assertThat(keys(hostProperties))
        .containsExactly(
            "host_property::host_os",
            "host_property::location",
            "host_property::p1",
            "host_property::p2",
            "host_property::p3")
        .inOrder();
    assertThat(hostProperties.getTotalAvailable()).isEqualTo(6);
    FleetFilterKeyEntry location = entryFor(hostProperties, "host_property::location");
    assertThat(location.getMetadata().getKeyDisplayName()).isEqualTo("Host Property location");
    assertThat(location.getCoverage().getDisplayCase()).isEqualTo(DisplayCase.NOT_SHOWN);
    assertThat(location.getApplied()).isTrue();

    FleetFilterKeyCatalogResponse searchResponse =
        cataloger.getFilterKeyCatalog(
            hostCorpus,
            FleetFilterKeyCatalogRequest.newBuilder()
                .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
                .setQuery("location")
                .build());
    assertThat(headings(searchResponse)).containsExactly("Host properties");
    assertThat(keys(section(searchResponse, "Host properties")))
        .containsExactly("host_property::location");
  }

  private static ImmutableList<String> headings(FleetFilterKeyCatalogResponse response) {
    return response.getSectionsList().stream()
        .map(FleetFilterKeySection::getHeading)
        .collect(toImmutableList());
  }

  private static FleetFilterKeySection section(
      FleetFilterKeyCatalogResponse response, String heading) {
    return response.getSectionsList().stream()
        .filter(s -> s.getHeading().equals(heading))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no section " + heading));
  }

  private static ImmutableList<String> keys(FleetFilterKeySection section) {
    return section.getEntriesList().stream()
        .map(FleetFilterKeyEntry::getKey)
        .collect(toImmutableList());
  }

  private static FleetFilterKeyEntry entryFor(FleetFilterKeySection section, String key) {
    return section.getEntriesList().stream()
        .filter(e -> e.getKey().equals(key))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no entry " + key));
  }

  /**
   * Three devices on two labs:
   *
   * <ul>
   *   <li>lab-a (1.1.1.1, location mtv, p1 to p4): device-0 (IDLE, pixel, shared, plus dimensions
   *       f01 to f10) and device-1 (IDLE, pixel, shared).
   *   <li>lab-b (2.2.2.2, location nyc): device-2 (BUSY, iphone, dedicated).
   * </ul>
   *
   * <p>That is 13 dimensions (model, pool, host_name, f01 to f10), of which the ATS registry
   * declares only model, and 6 host properties (host_os from FleetIndexBuilder, location, and p1 to
   * p4).
   */
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
                .addLabData(
                    labData(
                        "lab-a",
                        "1.1.1.1",
                        dims("location", "mtv", "p1", "v1", "p2", "v2", "p3", "v3", "p4", "v4"),
                        device0,
                        device1))
                .addLabData(labData("lab-b", "2.2.2.2", dims("location", "nyc"), device2)))
        .build();
  }

  private static LabData labData(
      String hostName, String ip, Map<String, String> props, DeviceInfo... devices) {
    DeviceList.Builder deviceList = DeviceList.newBuilder().setDeviceTotalCount(devices.length);
    for (DeviceInfo device : devices) {
      deviceList.addDeviceInfo(device);
    }
    HostProperties.Builder hostProps = HostProperties.newBuilder();
    for (Map.Entry<String, String> entry : props.entrySet()) {
      hostProps.addHostProperty(
          HostProperty.newBuilder().setKey(entry.getKey()).setValue(entry.getValue()));
    }
    return LabData.newBuilder()
        .setLabInfo(
            LabInfo.newBuilder()
                .setLabLocator(LabLocator.newBuilder().setHostName(hostName).setIp(ip))
                .setLabStatus(LabStatus.LAB_RUNNING)
                .setLabServerFeature(LabServerFeature.newBuilder().setHostProperties(hostProps)))
        .setDeviceList(deviceList)
        .build();
  }

  private static DeviceInfo device(
      String id, DeviceStatus status, String type, String owner, Map<String, String> dimensions) {
    DeviceCompositeDimension.Builder composite = DeviceCompositeDimension.newBuilder();
    for (Map.Entry<String, String> entry : dimensions.entrySet()) {
      composite.addSupportedDimension(
          DeviceDimension.newBuilder().setName(entry.getKey()).setValue(entry.getValue()));
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
}
