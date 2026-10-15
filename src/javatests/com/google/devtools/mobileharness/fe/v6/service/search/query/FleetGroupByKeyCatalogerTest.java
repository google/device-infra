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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupByKeyCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupByKeyCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupByKeyEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupByKeySection;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.GroupCountInfo.DisplayCase;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
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

/** Unit tests for {@link FleetGroupByKeyCataloger}. */
@RunWith(JUnit4.class)
public final class FleetGroupByKeyCatalogerTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final LazyPostings devicePostings = new LazyPostings(snapshot.devices());
  private final DeviceCorpus deviceCorpus =
      new DeviceCorpus(snapshot, devicePostings, new AtsCuration());
  private final HostCorpus hostCorpus =
      new HostCorpus(snapshot, LazyPostings.forHosts(snapshot.hosts()), new AtsCuration());
  private final FleetGroupByKeyCataloger cataloger = new FleetGroupByKeyCataloger();

  @Test
  public void deviceBrowse_unfiltered_countsAllSectionsAndDropsSingleGroupKeys() {
    FleetGroupByKeyCatalogResponse response =
        cataloger.getGroupByKeyCatalog(
            deviceCorpus,
            FleetGroupByKeyCatalogRequest.newBuilder().addGroupBy("host_field::host_name").build());

    // AtsCuration promotes host_field::host_name in deviceGroupByRow(), which produces 2 groups
    // (lab-a and lab-b), so Suggested leads.
    assertThat(headings(response))
        .containsExactly("Suggested", "Built-in fields", "Dimensions", "Host properties")
        .inOrder();

    FleetGroupByKeySection suggested = section(response, "Suggested");
    FleetGroupByKeyEntry hostNameSuggested = entryFor(suggested, "host_field::host_name");
    assertThat(hostNameSuggested.getApplied()).isTrue();
    assertThat(hostNameSuggested.getGroupCount().getDisplayCase()).isEqualTo(DisplayCase.SHOWN);
    assertThat(hostNameSuggested.getGroupCount().getShown().getCount()).isEqualTo(2);

    // host_field::connectivity is "Running" on all 3 devices (1 group), so it is dropped from
    // Built-in fields as a no-op grouping.
    FleetGroupByKeySection builtin = section(response, "Built-in fields");
    assertThat(builtin.getTotalAvailable()).isEqualTo(0);
    assertThat(keys(builtin)).doesNotContain("host_field::connectivity");
    assertThat(entryFor(builtin, "device_field::status").getGroupCount().getShown().getCount())
        .isEqualTo(2);
    assertThat(entryFor(builtin, "device_field::uuid").getGroupCount().getShown().getCount())
        .isEqualTo(3);

    // Browse lists declared dimensions only: the ATS registry declares dimension::model among the
    // fixture's four dimensions (model, pool, uniform, f01), and the total counts all four.
    FleetGroupByKeySection dimensions = section(response, "Dimensions");
    assertThat(keys(dimensions)).containsExactly("dimension::model");
    assertThat(dimensions.getTotalAvailable()).isEqualTo(4);
    assertThat(entryFor(dimensions, "dimension::model").getGroupCount().getShown().getCount())
        .isEqualTo(2);

    // Host properties has 8 properties in the index: host_os and prop_uniform (coverage 3, 1 group
    // each) plus location (coverage 3, 2 groups) and p1..p5 (coverage 2, 2 groups each). Dropping
    // host_os and prop_uniform leaves 6 two-group properties; slicing to PROP_TOP (5) returns the
    // top 5 with total 6.
    FleetGroupByKeySection properties = section(response, "Host properties");
    assertThat(keys(properties))
        .containsExactly(
            "host_property::location",
            "host_property::p1",
            "host_property::p2",
            "host_property::p3",
            "host_property::p4")
        .inOrder();
    assertThat(properties.getTotalAvailable()).isEqualTo(6);
    assertThat(
            entryFor(properties, "host_property::location").getGroupCount().getShown().getCount())
        .isEqualTo(2);
  }

  @Test
  public void deviceSearch_unfiltered_countsLongTailDimensionsAndDropsSingleGroupKeys() {
    // dimension::f01 is "x" on device-0 and missing on device-1/device-2, producing 2 groups
    // ("x" + no-value bucket).
    FleetGroupByKeyCatalogResponse f01 =
        cataloger.getGroupByKeyCatalog(
            deviceCorpus, FleetGroupByKeyCatalogRequest.newBuilder().setQuery("f01").build());
    assertThat(
            entryFor(section(f01, "Dimensions"), "dimension::f01")
                .getGroupCount()
                .getShown()
                .getCount())
        .isEqualTo(2);

    // dimension::uniform is "same" on all 3 devices (1 group), so it is dropped and the section,
    // left empty, is omitted.
    FleetGroupByKeyCatalogResponse uniform =
        cataloger.getGroupByKeyCatalog(
            deviceCorpus, FleetGroupByKeyCatalogRequest.newBuilder().setQuery("uniform").build());
    assertThat(headings(uniform)).doesNotContain("Dimensions");

    // Searching a promoted key ("host_name") counts it in Suggested.
    FleetGroupByKeyCatalogResponse hostName =
        cataloger.getGroupByKeyCatalog(
            deviceCorpus, FleetGroupByKeyCatalogRequest.newBuilder().setQuery("host_name").build());
    assertThat(
            entryFor(section(hostName, "Suggested"), "host_field::host_name")
                .getGroupCount()
                .getShown()
                .getCount())
        .isEqualTo(2);
  }

  @Test
  public void deviceBrowse_underFilter_countsOnlySuggestedAndBuiltinAndLeavesLongTailNotShown() {
    // Filter to IDLE devices (device-0 and device-1, both on lab-a with model=pixel).
    Filter statusIdle =
        Filter.newBuilder()
            .setKey("device_field::status")
            .setSimple(
                SimpleMatch.newBuilder().addValues(FilterValue.newBuilder().setValue("IDLE")))
            .build();

    FleetGroupByKeyCatalogResponse response =
        cataloger.getGroupByKeyCatalog(
            deviceCorpus,
            FleetGroupByKeyCatalogRequest.newBuilder().addFilters(statusIdle).build());

    // Under IDLE filter, both devices are on lab-a, so host_field::host_name produces only 1 group
    // and Suggested becomes empty and is omitted when unapplied.
    assertThat(headings(response))
        .containsExactly("Built-in fields", "Dimensions", "Host properties")
        .inOrder();

    // Built-in fields are counted over the 2 IDLE devices: device_field::driver ("NoOpDriver" on
    // device-0, missing on device-1) and device_field::uuid both produce 2 groups, while
    // device_field::owner ({bob, alice} on device-0 and {alice, bob} on device-1) canonicalizes to
    // 1 multi-valued combination and is dropped.
    FleetGroupByKeySection builtin = section(response, "Built-in fields");
    assertThat(keys(builtin)).containsExactly("device_field::driver", "device_field::uuid");
    assertThat(entryFor(builtin, "device_field::driver").getGroupCount().getShown().getCount())
        .isEqualTo(2);
    assertThat(entryFor(builtin, "device_field::uuid").getGroupCount().getShown().getCount())
        .isEqualTo(2);

    // Dimensions and Host properties are not counted under a filter: they carry NOT_SHOWN.
    FleetGroupByKeySection dimensions = section(response, "Dimensions");
    assertThat(entryFor(dimensions, "dimension::model").getGroupCount().getDisplayCase())
        .isEqualTo(DisplayCase.NOT_SHOWN);

    FleetGroupByKeySection properties = section(response, "Host properties");
    assertThat(keys(properties))
        .containsExactly(
            "host_property::host_os",
            "host_property::location",
            "host_property::prop_uniform",
            "host_property::p1",
            "host_property::p2")
        .inOrder();
    assertThat(properties.getTotalAvailable()).isEqualTo(8);
    assertThat(entryFor(properties, "host_property::location").getGroupCount().getDisplayCase())
        .isEqualTo(DisplayCase.NOT_SHOWN);

    // When an already-applied group-by key collapses to a single group under an active filter, it
    // is still returned marked applied rather than dropped.
    FleetGroupByKeyCatalogResponse withApplied =
        cataloger.getGroupByKeyCatalog(
            deviceCorpus,
            FleetGroupByKeyCatalogRequest.newBuilder()
                .addFilters(statusIdle)
                .addGroupBy("host_field::host_name")
                .build());
    FleetGroupByKeyEntry appliedHostName =
        entryFor(section(withApplied, "Suggested"), "host_field::host_name");
    assertThat(appliedHostName.getApplied()).isTrue();
    assertThat(appliedHostName.getGroupCount().getShown().getCount()).isEqualTo(1);
  }

  @Test
  public void deviceSearch_includesCatalogOnlyDimensionsWithNotShownGroupCount() {
    DimensionCatalogStore store = new DimensionCatalogStore();
    store.setDimensionNames(Fleet.FLEET_SELF, ImmutableSet.of("custom_catalog_dim"));
    FleetGroupByKeyCataloger customCataloger = new FleetGroupByKeyCataloger(store);

    FleetGroupByKeyCatalogResponse response =
        customCataloger.getGroupByKeyCatalog(
            deviceCorpus,
            FleetGroupByKeyCatalogRequest.newBuilder().setQuery("custom_catalog_dim").build());

    assertThat(headings(response)).containsExactly("Dimensions");
    FleetGroupByKeyEntry entry =
        entryFor(section(response, "Dimensions"), "dimension::custom_catalog_dim");
    assertThat(entry.getGroupCount().getDisplayCase()).isEqualTo(DisplayCase.NOT_SHOWN);
  }

  @Test
  public void deviceSearch_exactNameLeadsEvenWithUnknownGroupCount() {
    DimensionCatalogStore store = new DimensionCatalogStore();
    store.setDimensionNames(Fleet.FLEET_SELF, ImmutableSet.of("f0"));
    FleetGroupByKeyCataloger customCataloger = new FleetGroupByKeyCataloger(store);

    FleetGroupByKeyCatalogResponse response =
        customCataloger.getGroupByKeyCatalog(
            deviceCorpus, FleetGroupByKeyCatalogRequest.newBuilder().setQuery("f0").build());

    // dimension::f0 (catalog-only, exact) leads dimension::f01 (indexed, prefix).
    assertThat(keys(section(response, "Dimensions")))
        .containsExactly("dimension::f0", "dimension::f01")
        .inOrder();
  }

  @Test
  public void hostBrowse_countsHostGroupsAndOmitsDimensions() {
    FleetGroupByKeyCatalogResponse response =
        cataloger.getGroupByKeyCatalog(
            hostCorpus,
            FleetGroupByKeyCatalogRequest.newBuilder()
                .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
                .build());

    assertThat(headings(response)).containsExactly("Built-in fields", "Host properties").inOrder();
    FleetGroupByKeySection builtin = section(response, "Built-in fields");
    // host_name (lab-a, lab-b), host_ip (1.1.1.1, 2.2.2.2), and device_count (2, 1) all produce 2
    // groups; connectivity ("Running" on both hosts) produces 1 group and is dropped.
    assertThat(keys(builtin))
        .containsExactly("host_field::device_count", "host_field::host_ip", "host_field::host_name")
        .inOrder();
    assertThat(entryFor(builtin, "host_field::host_name").getGroupCount().getShown().getCount())
        .isEqualTo(2);
  }

  private static ImmutableList<String> headings(FleetGroupByKeyCatalogResponse response) {
    return response.getSectionsList().stream()
        .map(FleetGroupByKeySection::getHeading)
        .collect(toImmutableList());
  }

  private static FleetGroupByKeySection section(
      FleetGroupByKeyCatalogResponse response, String heading) {
    return response.getSectionsList().stream()
        .filter(s -> s.getHeading().equals(heading))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no section " + heading));
  }

  private static ImmutableList<String> keys(FleetGroupByKeySection section) {
    return section.getEntriesList().stream()
        .map(FleetGroupByKeyEntry::getKey)
        .collect(toImmutableList());
  }

  private static FleetGroupByKeyEntry entryFor(FleetGroupByKeySection section, String key) {
    return section.getEntriesList().stream()
        .filter(e -> e.getKey().equals(key))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no entry " + key));
  }

  /**
   * Three devices on two labs:
   *
   * <ul>
   *   <li>lab-a (1.1.1.1, location mtv, prop_uniform=same, p1 to p5): device-0 (IDLE, pixel,
   *       shared, driver NoOpDriver, f01=x) and device-1 (IDLE, pixel, shared).
   *   <li>lab-b (2.2.2.2, location nyc, prop_uniform=same): device-2 (BUSY, iphone, dedicated).
   * </ul>
   *
   * <p>Every device has uniform=same, so that dimension makes one group and is dropped. f01 and
   * driver are set on device-0 only, so each makes two groups: its value and the no-value group.
   */
  private static LabQueryResult fleet() {
    Map<String, String> device0Dims = new LinkedHashMap<>();
    device0Dims.put("model", "pixel");
    device0Dims.put("pool", "shared");
    device0Dims.put("uniform", "same");
    device0Dims.put("f01", "x");

    DeviceInfo.Builder device0Builder =
        device("device-0", DeviceStatus.IDLE, "android_real_device", "bob", device0Dims)
            .toBuilder();
    device0Builder.getDeviceFeatureBuilder().addOwner("alice").addDriver("NoOpDriver");
    DeviceInfo device0 = device0Builder.build();

    DeviceInfo.Builder device1Builder =
        device(
            "device-1",
            DeviceStatus.IDLE,
            "android_real_device",
            "alice",
            dims("model", "pixel", "pool", "shared", "uniform", "same"))
            .toBuilder();
    device1Builder.getDeviceFeatureBuilder().addOwner("bob");
    DeviceInfo device1 = device1Builder.build();

    DeviceInfo device2 =
        device(
            "device-2",
            DeviceStatus.BUSY,
            "ios_real_device",
            "carol",
            dims("model", "iphone", "pool", "dedicated", "uniform", "same"));

    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(2)
                .addLabData(
                    labData(
                        "lab-a",
                        "1.1.1.1",
                        dims(
                            "location",
                            "mtv",
                            "prop_uniform",
                            "same",
                            "p1",
                            "v1",
                            "p2",
                            "v2",
                            "p3",
                            "v3",
                            "p4",
                            "v4",
                            "p5",
                            "v5"),
                        device0,
                        device1))
                .addLabData(
                    labData(
                        "lab-b",
                        "2.2.2.2",
                        dims("location", "nyc", "prop_uniform", "same"),
                        device2)))
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
