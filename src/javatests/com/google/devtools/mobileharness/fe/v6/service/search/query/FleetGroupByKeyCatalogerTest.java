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
import com.google.common.collect.ImmutableMap;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.CoverageInfo;
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
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsHostKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
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
  public void deviceBrowse_unfiltered_countsAllSectionsIncludingSingleGroupKeys() {
    FleetGroupByKeyCatalogResponse response =
        cataloger.getGroupByKeyCatalog(
            deviceCorpus,
            FleetGroupByKeyCatalogRequest.newBuilder().addGroupBy("host_field::host_name").build());

    // AtsCuration promotes host_field::host_name in deviceGroupByRow(), which produces 2 groups
    // (lab-a and lab-b), so Suggested leads.
    assertThat(headings(response))
        .containsExactly(
            "Suggested",
            "Built-in fields",
            "Built-in fields from connected host",
            "Dimensions",
            "Host properties from connected host")
        .inOrder();

    FleetGroupByKeySection suggested = section(response, "Suggested");
    FleetGroupByKeyEntry hostNameSuggested = entryFor(suggested, "host_field::host_name");
    assertThat(hostNameSuggested.getApplied()).isTrue();
    assertThat(hostNameSuggested.getGroupCount().getDisplayCase()).isEqualTo(DisplayCase.SHOWN);
    assertThat(hostNameSuggested.getGroupCount().getShown().getCount()).isEqualTo(2);
    assertThat(hostNameSuggested.getCoverage().getDisplayCase())
        .isEqualTo(CoverageInfo.DisplayCase.NOT_SHOWN);

    FleetGroupByKeySection builtin = section(response, "Built-in fields");
    assertThat(builtin.getTotalAvailable()).isEqualTo(0);
    assertThat(entryFor(builtin, "device_field::status").getGroupCount().getShown().getCount())
        .isEqualTo(2);
    assertThat(entryFor(builtin, "device_field::status").getCoverage().getDisplayCase())
        .isEqualTo(CoverageInfo.DisplayCase.NOT_SHOWN);
    assertThat(entryFor(builtin, "device_field::uuid").getGroupCount().getShown().getCount())
        .isEqualTo(3);

    // host_field::connectivity is "Running" on all 3 devices (1 group) and is kept with count 1.
    FleetGroupByKeySection hostBuiltin = section(response, "Built-in fields from connected host");
    assertThat(hostBuiltin.getTotalAvailable()).isEqualTo(0);
    assertThat(
            entryFor(hostBuiltin, "host_field::connectivity").getGroupCount().getShown().getCount())
        .isEqualTo(1);
    assertThat(entryFor(hostBuiltin, "host_field::host_name").getGroupCount().getShown().getCount())
        .isEqualTo(2);
    assertThat(entryFor(hostBuiltin, "host_field::host_name").getCoverage().getDisplayCase())
        .isEqualTo(CoverageInfo.DisplayCase.NOT_SHOWN);

    // Browse lists declared dimensions only: the ATS registry declares dimension::model among the
    // fixture's four dimensions (model, pool, uniform, f01), and the total counts all four.
    FleetGroupByKeySection dimensions = section(response, "Dimensions");
    assertThat(keys(dimensions)).containsExactly("dimension::model");
    assertThat(dimensions.getTotalAvailable()).isEqualTo(4);
    FleetGroupByKeyEntry modelEntry = entryFor(dimensions, "dimension::model");
    assertThat(modelEntry.getGroupCount().getShown().getCount()).isEqualTo(2);
    assertThat(modelEntry.getCoverage().getDisplayCase()).isEqualTo(CoverageInfo.DisplayCase.SHOWN);
    assertThat(modelEntry.getCoverage().getShown().getCount()).isEqualTo(3);

    // Host properties from connected host has 8 properties in the index: host_os, location, and
    // prop_uniform (coverage 3) plus p1..p5 (coverage 2); slicing to PROP_TOP (5) returns the top 5
    // with total 8.
    FleetGroupByKeySection properties = section(response, "Host properties from connected host");
    assertThat(keys(properties))
        .containsExactly(
            "host_property::host_os",
            "host_property::location",
            "host_property::prop_uniform",
            "host_property::p1",
            "host_property::p2")
        .inOrder();
    assertThat(properties.getTotalAvailable()).isEqualTo(8);
    FleetGroupByKeyEntry locationEntry = entryFor(properties, "host_property::location");
    assertThat(locationEntry.getGroupCount().getShown().getCount()).isEqualTo(2);
    assertThat(locationEntry.getCoverage().getDisplayCase())
        .isEqualTo(CoverageInfo.DisplayCase.NOT_SHOWN);
    assertThat(
            entryFor(properties, "host_property::prop_uniform")
                .getGroupCount()
                .getShown()
                .getCount())
        .isEqualTo(1);
  }

  @Test
  public void deviceSearch_unfiltered_countsLongTailDimensionsIncludingSingleGroupKeys() {
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

    // dimension::uniform is "same" on all 3 devices (1 group) and is kept with count 1.
    FleetGroupByKeyCatalogResponse uniform =
        cataloger.getGroupByKeyCatalog(
            deviceCorpus, FleetGroupByKeyCatalogRequest.newBuilder().setQuery("uniform").build());
    assertThat(
            entryFor(section(uniform, "Dimensions"), "dimension::uniform")
                .getGroupCount()
                .getShown()
                .getCount())
        .isEqualTo(1);

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

    // Under IDLE filter, both devices are on lab-a, so host_field::host_name and
    // host_field::connectivity each produce 1 group and stay in Suggested and Built-in fields from
    // connected host with count 1.
    assertThat(headings(response))
        .containsExactly(
            "Suggested",
            "Built-in fields",
            "Built-in fields from connected host",
            "Dimensions",
            "Host properties from connected host")
        .inOrder();
    assertThat(
            entryFor(section(response, "Suggested"), "host_field::host_name")
                .getGroupCount()
                .getShown()
                .getCount())
        .isEqualTo(1);
    assertThat(
            entryFor(
                    section(response, "Built-in fields from connected host"),
                    "host_field::connectivity")
                .getGroupCount()
                .getShown()
                .getCount())
        .isEqualTo(1);

    // Built-in fields are counted over the 2 IDLE devices: device_field::driver ("NoOpDriver" on
    // device-0, missing on device-1) and device_field::uuid both produce 2 groups, while
    // device_field::status and device_field::type each produce 1 group and are kept with count 1.
    FleetGroupByKeySection builtin = section(response, "Built-in fields");
    assertThat(entryFor(builtin, "device_field::driver").getGroupCount().getShown().getCount())
        .isEqualTo(2);
    assertThat(entryFor(builtin, "device_field::uuid").getGroupCount().getShown().getCount())
        .isEqualTo(2);
    assertThat(entryFor(builtin, "device_field::status").getGroupCount().getShown().getCount())
        .isEqualTo(1);

    // Dimensions and Host properties from connected host are not counted for groups under a filter
    // (groupCount NOT_SHOWN), while Dimensions still shows entity coverage.
    FleetGroupByKeySection dimensions = section(response, "Dimensions");
    FleetGroupByKeyEntry modelEntry = entryFor(dimensions, "dimension::model");
    assertThat(modelEntry.getGroupCount().getDisplayCase()).isEqualTo(DisplayCase.NOT_SHOWN);
    assertThat(modelEntry.getCoverage().getDisplayCase()).isEqualTo(CoverageInfo.DisplayCase.SHOWN);
    assertThat(modelEntry.getCoverage().getShown().getCount()).isEqualTo(3);

    FleetGroupByKeySection properties = section(response, "Host properties from connected host");
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
    assertThat(entryFor(properties, "host_property::location").getCoverage().getDisplayCase())
        .isEqualTo(CoverageInfo.DisplayCase.NOT_SHOWN);

    // When a filter matches 0 devices, unapplied counted keys produce 0 groups and are dropped,
    // while an already-applied group-by key is still returned marked applied.
    Filter noMatch =
        Filter.newBuilder()
            .setKey("device_field::status")
            .setSimple(
                SimpleMatch.newBuilder().addValues(FilterValue.newBuilder().setValue("MISSING")))
            .build();
    FleetGroupByKeyCatalogResponse emptyScopeWithApplied =
        cataloger.getGroupByKeyCatalog(
            deviceCorpus,
            FleetGroupByKeyCatalogRequest.newBuilder()
                .addFilters(noMatch)
                .addGroupBy("host_field::host_name")
                .build());
    assertThat(headings(emptyScopeWithApplied))
        .containsExactly(
            "Suggested",
            "Built-in fields from connected host",
            "Dimensions",
            "Host properties from connected host")
        .inOrder();
    assertThat(keys(section(emptyScopeWithApplied, "Built-in fields from connected host")))
        .containsExactly("host_field::host_name");
    FleetGroupByKeyEntry appliedHostName =
        entryFor(section(emptyScopeWithApplied, "Suggested"), "host_field::host_name");
    assertThat(appliedHostName.getApplied()).isTrue();
    assertThat(appliedHostName.getGroupCount().getShown().getCount()).isEqualTo(0);
  }

  @Test
  public void deviceSearch_includesCatalogDimensionsWithCoverageAndNotShownGroupCount() {
    DimensionCatalogStore store = new DimensionCatalogStore();
    store.setDimensionCounts(
        Fleet.FLEET_SELF, ImmutableMap.of("custom_catalog_dim", 0, "counted_catalog_dim", 17));
    FleetGroupByKeyCataloger customCataloger = new FleetGroupByKeyCataloger(store);

    FleetGroupByKeyCatalogResponse response =
        customCataloger.getGroupByKeyCatalog(
            deviceCorpus,
            FleetGroupByKeyCatalogRequest.newBuilder().setQuery("catalog_dim").build());

    assertThat(headings(response)).containsExactly("Dimensions");
    FleetGroupByKeySection dimensions = section(response, "Dimensions");
    FleetGroupByKeyEntry countedEntry = entryFor(dimensions, "dimension::counted_catalog_dim");
    assertThat(countedEntry.getGroupCount().getDisplayCase()).isEqualTo(DisplayCase.NOT_SHOWN);
    assertThat(countedEntry.getCoverage().getDisplayCase())
        .isEqualTo(CoverageInfo.DisplayCase.SHOWN);
    assertThat(countedEntry.getCoverage().getShown().getCount()).isEqualTo(17);

    FleetGroupByKeyEntry uncountedEntry = entryFor(dimensions, "dimension::custom_catalog_dim");
    assertThat(uncountedEntry.getGroupCount().getDisplayCase()).isEqualTo(DisplayCase.NOT_SHOWN);
    assertThat(uncountedEntry.getCoverage().getDisplayCase())
        .isEqualTo(CoverageInfo.DisplayCase.NOT_SHOWN);
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
    // groups; connectivity ("Lab Server Connectivity", "Running" on both hosts) produces 1 group
    // and is kept with count 1.
    assertThat(keys(builtin))
        .containsExactly(
            "host_field::device_count",
            "host_field::host_ip",
            "host_field::host_name",
            "host_field::connectivity")
        .inOrder();
    FleetGroupByKeyEntry hostName = entryFor(builtin, "host_field::host_name");
    assertThat(hostName.getGroupCount().getShown().getCount()).isEqualTo(2);
    assertThat(hostName.getCoverage().getDisplayCase())
        .isEqualTo(CoverageInfo.DisplayCase.NOT_SHOWN);
    assertThat(entryFor(builtin, "host_field::connectivity").getGroupCount().getShown().getCount())
        .isEqualTo(1);

    FleetGroupByKeySection properties = section(response, "Host properties");
    FleetGroupByKeyEntry location = entryFor(properties, "host_property::location");
    assertThat(location.getGroupCount().getShown().getCount()).isEqualTo(2);
    assertThat(location.getCoverage().getDisplayCase()).isEqualTo(CoverageInfo.DisplayCase.SHOWN);
    assertThat(location.getCoverage().getShown().getCount()).isEqualTo(2);
  }

  @Test
  public void hostSearch_filtersHostSectionsAndShowsCoverageForProperties() {
    FleetGroupByKeyCatalogResponse locationResponse =
        cataloger.getGroupByKeyCatalog(
            hostCorpus,
            FleetGroupByKeyCatalogRequest.newBuilder()
                .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
                .setQuery("location")
                .build());

    assertThat(headings(locationResponse)).containsExactly("Host properties");
    FleetGroupByKeyEntry location =
        entryFor(section(locationResponse, "Host properties"), "host_property::location");
    assertThat(location.getGroupCount().getShown().getCount()).isEqualTo(2);
    assertThat(location.getCoverage().getDisplayCase()).isEqualTo(CoverageInfo.DisplayCase.SHOWN);
    assertThat(location.getCoverage().getShown().getCount()).isEqualTo(2);

    FleetGroupByKeyCatalogResponse hostNameResponse =
        cataloger.getGroupByKeyCatalog(
            hostCorpus,
            FleetGroupByKeyCatalogRequest.newBuilder()
                .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
                .setQuery("host_name")
                .build());

    assertThat(headings(hostNameResponse)).containsExactly("Built-in fields");
    FleetGroupByKeyEntry hostName =
        entryFor(section(hostNameResponse, "Built-in fields"), "host_field::host_name");
    assertThat(hostName.getGroupCount().getShown().getCount()).isEqualTo(2);
    assertThat(hostName.getCoverage().getDisplayCase())
        .isEqualTo(CoverageInfo.DisplayCase.NOT_SHOWN);
  }

  @Test
  public void suggested_showsCoverageForCuratedDimensionAndHostProperty() {
    ScenarioCuration curation = new SuggestedCoverageCuration();
    DeviceCorpus curatedDeviceCorpus = new DeviceCorpus(snapshot, devicePostings, curation);
    HostCorpus curatedHostCorpus =
        new HostCorpus(snapshot, LazyPostings.forHosts(snapshot.hosts()), curation);

    FleetGroupByKeyCatalogResponse deviceResponse =
        cataloger.getGroupByKeyCatalog(
            curatedDeviceCorpus, FleetGroupByKeyCatalogRequest.getDefaultInstance());
    FleetGroupByKeySection deviceSuggested = section(deviceResponse, "Suggested");
    assertThat(keys(deviceSuggested))
        .containsExactly("host_field::host_name", "dimension::model")
        .inOrder();
    assertThat(entryFor(deviceSuggested, "host_field::host_name").getCoverage().getDisplayCase())
        .isEqualTo(CoverageInfo.DisplayCase.NOT_SHOWN);
    FleetGroupByKeyEntry suggestedModel = entryFor(deviceSuggested, "dimension::model");
    assertThat(suggestedModel.getCoverage().getDisplayCase())
        .isEqualTo(CoverageInfo.DisplayCase.SHOWN);
    assertThat(suggestedModel.getCoverage().getShown().getCount()).isEqualTo(3);

    FleetGroupByKeyCatalogResponse hostResponse =
        cataloger.getGroupByKeyCatalog(
            curatedHostCorpus,
            FleetGroupByKeyCatalogRequest.newBuilder()
                .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
                .build());
    FleetGroupByKeySection hostSuggested = section(hostResponse, "Suggested");
    assertThat(keys(hostSuggested))
        .containsExactly("host_field::host_name", "host_property::location")
        .inOrder();
    assertThat(entryFor(hostSuggested, "host_field::host_name").getCoverage().getDisplayCase())
        .isEqualTo(CoverageInfo.DisplayCase.NOT_SHOWN);
    FleetGroupByKeyEntry suggestedLocation = entryFor(hostSuggested, "host_property::location");
    assertThat(suggestedLocation.getCoverage().getDisplayCase())
        .isEqualTo(CoverageInfo.DisplayCase.SHOWN);
    assertThat(suggestedLocation.getCoverage().getShown().getCount()).isEqualTo(2);
  }

  @Test
  public void versionEntry_carriesKeyDescriptionInSuggestedAndBuiltinSections() {
    LabQueryResult versionLab =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData(
                            "lab-a",
                            "1.1.1.1",
                            dims("location", "mtv"),
                            device(
                                "device-0",
                                DeviceStatus.IDLE,
                                "android_real_device",
                                "alice",
                                dims("release_version", "16")))))
            .build();
    FleetSnapshot versionSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(versionLab, BUILD_TIME);
    ScenarioCuration versionCuration =
        new SuggestedCoverageCuration() {
          @Override
          public ImmutableList<DeviceKeyDescriptor> deviceGroupByRow() {
            return ImmutableList.of(DeviceKeys.VERSION);
          }
        };
    DeviceCorpus versionCorpus =
        new DeviceCorpus(
            versionSnapshot, new LazyPostings(versionSnapshot.devices()), versionCuration);

    FleetGroupByKeyCatalogResponse response =
        cataloger.getGroupByKeyCatalog(
            versionCorpus, FleetGroupByKeyCatalogRequest.getDefaultInstance());

    assertThat(entryFor(section(response, "Suggested"), DeviceKeys.VERSION.id()).getDescription())
        .isEqualTo(DeviceKeys.VERSION.display().description());
    assertThat(
            entryFor(section(response, "Built-in fields"), DeviceKeys.VERSION.id())
                .getDescription())
        .isEqualTo(DeviceKeys.VERSION.display().description());
    assertThat(
            entryFor(section(response, "Built-in fields"), DeviceKeys.STATUS.id()).getDescription())
        .isEmpty();
  }

  /**
   * Curation promoting a built-in field alongside a dimension (device) and host property (host).
   */
  private static class SuggestedCoverageCuration implements ScenarioCuration {
    private final AtsDeviceKeyRegistry deviceRegistry = new AtsDeviceKeyRegistry();
    private final AtsHostKeyRegistry hostRegistry = new AtsHostKeyRegistry();

    @Override
    public ImmutableList<DeviceKeyDescriptor> deviceFilterByRow() {
      return ImmutableList.of();
    }

    @Override
    public ImmutableList<DeviceKeyDescriptor> deviceGroupByRow() {
      return ImmutableList.of(DeviceKeys.HOST_NAME, DeviceKeys.MODEL);
    }

    @Override
    public ImmutableList<DeviceKeyDescriptor> deviceDefaultColumns() {
      return ImmutableList.of();
    }

    @Override
    public ImmutableList<DeviceKeyDescriptor> deviceRecommendedColumns() {
      return ImmutableList.of();
    }

    @Override
    public ImmutableList<HostKeyDescriptor> hostFilterByRow() {
      return ImmutableList.of();
    }

    @Override
    public ImmutableList<HostKeyDescriptor> hostGroupByRow() {
      return ImmutableList.of(HostKeys.HOST_NAME, hostRegistry.hostPropertyKey("location").get());
    }

    @Override
    public ImmutableList<HostKeyDescriptor> hostDefaultColumns() {
      return ImmutableList.of();
    }

    @Override
    public ImmutableList<HostKeyDescriptor> hostRecommendedColumns() {
      return ImmutableList.of();
    }

    @Override
    public KeyPriority keyPriority() {
      return FleetKeyPriority.INSTANCE;
    }

    @Override
    public DeviceKeyRegistry deviceKeyRegistry() {
      return deviceRegistry;
    }

    @Override
    public HostKeyRegistry hostKeyRegistry() {
      return hostRegistry;
    }

    @Override
    public boolean landingEnabled() {
      return false;
    }
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
   * <p>Every device has uniform=same, so that dimension makes one group. f01 and driver are set on
   * device-0 only, so each makes two groups: its value and the no-value group.
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
