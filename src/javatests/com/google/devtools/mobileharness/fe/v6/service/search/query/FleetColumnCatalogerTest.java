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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.CoverageInfo.DisplayCase;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogSection;
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

/** Unit tests for {@link FleetColumnCataloger}. */
@RunWith(JUnit4.class)
public final class FleetColumnCatalogerTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  // Synthetic fleet built through the real index builder, across two hosts:
  //   device-0: IDLE, android, owner alice, model pixel, pool shared, dim host_name lab-a,
  //             plus ten filler dimensions carried only by this device.
  //   device-1: IDLE, android, owner alice, model pixel, pool shared, dim host_name lab-a.
  //   device-2: BUSY, ios,     owner carol, model iphone, pool dedicated, dim host_name lab-b.
  // Every device carries a "host_name" dimension equal to its real host name, so
  // dimension::host_name
  // never disagrees with the built-in host_field::host_name and is detected as redundant. Both
  // hosts
  // carry a "location" host property so a host_property:: key exists. The ten filler dimensions
  // push the
  // non-redundant dimension count past the browse top-N so truncation is exercised.
  // FleetIndexBuilder and FleetColumnCataloger have package-private @Inject constructors, so obtain
  // them through Guice rather than constructing directly.
  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final LazyPostings postings = new LazyPostings(snapshot.devices());
  private final DeviceCorpus corpus = new DeviceCorpus(snapshot, postings, null);
  private final FleetColumnCataloger cataloger =
      Guice.createInjector().getInstance(FleetColumnCataloger.class);

  @Test
  public void defaultConstructor_works() {
    FleetColumnCataloger defaultCataloger = new FleetColumnCataloger();
    FleetColumnCatalogResponse response =
        defaultCataloger.getColumnCatalog(corpus, FleetColumnCatalogRequest.getDefaultInstance());
    assertThat(headings(response)).contains("Built-in fields");
  }

  @Test
  public void dimensionsSection_includesCatalogOnlyDimensions() {
    DimensionCatalogStore store = new DimensionCatalogStore();
    store.setDimensionNames(Fleet.FLEET_SELF, ImmutableSet.of("custom_catalog_dim"));
    FleetColumnCataloger customCataloger = new FleetColumnCataloger(store);

    FleetColumnCatalogResponse response =
        customCataloger.getColumnCatalog(corpus, FleetColumnCatalogRequest.getDefaultInstance());

    FleetColumnCatalogSection dimensions = section(response, "Dimensions");
    assertThat(keys(dimensions)).containsExactly("dimension::model");
    assertThat(dimensions.getTotalAvailable()).isEqualTo(13);

    FleetColumnCatalogResponse queryResponse =
        customCataloger.getColumnCatalog(
            corpus, FleetColumnCatalogRequest.newBuilder().setQuery("custom_catalog_dim").build());
    FleetColumnCatalogSection dimensionsSection = section(queryResponse, "Dimensions");
    assertThat(keys(dimensionsSection)).contains("dimension::custom_catalog_dim");
    assertThat(
            entryFor(dimensionsSection, "dimension::custom_catalog_dim")
                .getCoverage()
                .getDisplayCase())
        .isEqualTo(DisplayCase.NOT_SHOWN);
    assertThat(headings(queryResponse)).doesNotContain("Search results");
  }

  @Test
  public void dimensionsSection_includesCatalogDimensionsWithCounts() {
    DimensionCatalogStore store = new DimensionCatalogStore();
    store.setDimensionCounts(Fleet.FLEET_SELF, ImmutableMap.of("counted_catalog_dim", 42));
    FleetColumnCataloger customCataloger = new FleetColumnCataloger(store);

    FleetColumnCatalogResponse browseResponse =
        customCataloger.getColumnCatalog(corpus, FleetColumnCatalogRequest.getDefaultInstance());
    assertThat(keys(section(browseResponse, "Dimensions"))).containsExactly("dimension::model");
    assertThat(section(browseResponse, "Dimensions").getTotalAvailable()).isEqualTo(13);

    FleetColumnCatalogResponse queryResponse =
        customCataloger.getColumnCatalog(
            corpus, FleetColumnCatalogRequest.newBuilder().setQuery("counted_catalog_dim").build());
    FleetColumnCatalogSection dimensionsSection = section(queryResponse, "Dimensions");
    FleetColumnCatalogEntry entry = entryFor(dimensionsSection, "dimension::counted_catalog_dim");
    assertThat(entry.getCoverage().getDisplayCase()).isEqualTo(DisplayCase.SHOWN);
    assertThat(entry.getCoverage().getShown().getCount()).isEqualTo(42);
  }

  @Test
  public void dimensionsSection_indexedCountTakesPrecedenceOverCatalogStoreCount() {
    DimensionCatalogStore store = new DimensionCatalogStore();
    store.setDimensionCounts(Fleet.FLEET_SELF, ImmutableMap.of("model", 99));
    FleetColumnCataloger customCataloger = new FleetColumnCataloger(store);

    FleetColumnCatalogResponse response =
        customCataloger.getColumnCatalog(corpus, FleetColumnCatalogRequest.getDefaultInstance());

    FleetColumnCatalogEntry modelEntry =
        entryFor(section(response, "Dimensions"), "dimension::model");
    assertThat(modelEntry.getCoverage().getDisplayCase()).isEqualTo(DisplayCase.SHOWN);
    assertThat(modelEntry.getCoverage().getShown().getCount()).isEqualTo(3);
  }

  @Test
  public void getColumnCatalog_respectsRequestedFleet() {
    DimensionCatalogStore store = new DimensionCatalogStore();
    store.setDimensionNames(Fleet.FLEET_SELF, ImmutableSet.of("self_catalog_dim"));
    store.setDimensionNames(Fleet.FLEET_ATS, ImmutableSet.of("ats_catalog_dim"));
    FleetColumnCataloger customCataloger = new FleetColumnCataloger(store);

    FleetColumnCatalogResponse atsResponse =
        customCataloger.getColumnCatalog(
            corpus,
            FleetColumnCatalogRequest.newBuilder()
                .setFleet(Fleet.FLEET_ATS)
                .setQuery("catalog_dim")
                .build());
    FleetColumnCatalogResponse defaultResponse =
        customCataloger.getColumnCatalog(
            corpus,
            FleetColumnCatalogRequest.newBuilder()
                .setFleet(Fleet.FLEET_UNSPECIFIED)
                .setQuery("catalog_dim")
                .build());

    assertThat(keys(section(atsResponse, "Dimensions"))).contains("dimension::ats_catalog_dim");
    assertThat(keys(section(atsResponse, "Dimensions")))
        .doesNotContain("dimension::self_catalog_dim");

    assertThat(keys(section(defaultResponse, "Dimensions")))
        .contains("dimension::self_catalog_dim");
    assertThat(keys(section(defaultResponse, "Dimensions")))
        .doesNotContain("dimension::ats_catalog_dim");
  }

  @Test
  public void browse_sectionsInOrder() {
    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(corpus, FleetColumnCatalogRequest.getDefaultInstance());

    // No filters, no recents, no query: the suggested and search sections are absent, and the four
    // browse sections appear in their fixed order.
    assertThat(headings(response))
        .containsExactly(
            "Built-in fields",
            "Built-in fields from connected host",
            "Dimensions",
            "Host properties from connected host")
        .inOrder();
  }

  @Test
  public void builtinSections_listDeviceAndHostBuiltinsSortedByDisplayName() {
    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(corpus, FleetColumnCatalogRequest.getDefaultInstance());

    FleetColumnCatalogSection builtin = section(response, "Built-in fields");
    assertThat(keys(builtin))
        .containsExactly("device_field::status", "device_field::type", "device_field::uuid")
        .inOrder();
    assertThat(builtin.getTotalAvailable()).isEqualTo(0);

    FleetColumnCatalogSection hostBuiltin =
        section(response, "Built-in fields from connected host");
    assertThat(keys(hostBuiltin))
        .containsExactly("host_field::host_ip", "host_field::connectivity", "host_field::host_name")
        .inOrder();
    assertThat(hostBuiltin.getTotalAvailable()).isEqualTo(0);
  }

  @Test
  public void dimensionsSection_excludesRedundantDimAndReportsTotal() {
    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(corpus, FleetColumnCatalogRequest.getDefaultInstance());

    FleetColumnCatalogSection dimensions = section(response, "Dimensions");
    // dimension::host_name is present in the fleet but restates host_field::host_name on every
    // device, so it is detected as redundant and dropped from the dimensions section.
    assertThat(snapshot.index().keyIds()).contains("dimension::host_name");
    assertThat(
            headings(
                cataloger.getColumnCatalog(
                    corpus, FleetColumnCatalogRequest.newBuilder().setQuery("host_name").build())))
        .doesNotContain("Dimensions");

    // Browse lists declared dimensions only (dimension::model in the ATS registry), while
    // total_available counts all 12 non-redundant dimensions (model, pool, and the ten fillers).
    assertThat(keys(dimensions)).containsExactly("dimension::model");
    assertThat(dimensions.getTotalAvailable()).isEqualTo(12);
    assertThat(dimensions.getEntriesCount()).isEqualTo(1);
  }

  @Test
  public void coverage_shownForIndexedDimensionsOnly() {
    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(corpus, FleetColumnCatalogRequest.getDefaultInstance());

    // Built-in fields exist on every device, so coverage is not shown.
    assertThat(
            entryFor(section(response, "Built-in fields"), "device_field::status")
                .getCoverage()
                .getDisplayCase())
        .isEqualTo(DisplayCase.NOT_SHOWN);
    assertThat(
            entryFor(
                    section(response, "Built-in fields from connected host"),
                    "host_field::host_name")
                .getCoverage()
                .getDisplayCase())
        .isEqualTo(DisplayCase.NOT_SHOWN);
    // model is carried by all three devices (pixel on two, iphone on one).
    FleetColumnCatalogEntry modelEntry =
        entryFor(section(response, "Dimensions"), "dimension::model");
    assertThat(modelEntry.getCoverage().getDisplayCase()).isEqualTo(DisplayCase.SHOWN);
    assertThat(modelEntry.getCoverage().getShown().getCount()).isEqualTo(3);
    // Host properties measure host coverage rather than device coverage, so coverage is not shown.
    assertThat(
            entryFor(
                    section(response, "Host properties from connected host"),
                    "host_property::location")
                .getCoverage()
                .getDisplayCase())
        .isEqualTo(DisplayCase.NOT_SHOWN);
  }

  @Test
  public void suggested_fromActiveFiltersAndRecentKeys() {
    FleetColumnCatalogRequest request =
        FleetColumnCatalogRequest.newBuilder()
            .addFilters(Filter.newBuilder().setKey("dimension::model"))
            .addRecentKeys("device_field::status")
            .build();

    FleetColumnCatalogResponse response = cataloger.getColumnCatalog(corpus, request);

    // The suggested section leads, before the browse sections.
    assertThat(headings(response).get(0)).isEqualTo("Suggested for you");
    FleetColumnCatalogSection suggested = section(response, "Suggested for you");
    assertThat(keys(suggested))
        .containsExactly("dimension::model", "device_field::status")
        .inOrder();
    assertThat(entryFor(suggested, "dimension::model").getReason())
        .isEqualTo("in your active filters");
    assertThat(entryFor(suggested, "dimension::model").getCoverage().getDisplayCase())
        .isEqualTo(DisplayCase.NOT_SHOWN);
    assertThat(entryFor(suggested, "device_field::status").getReason()).isEqualTo("recently used");
    assertThat(entryFor(suggested, "device_field::status").getCoverage().getDisplayCase())
        .isEqualTo(DisplayCase.NOT_SHOWN);
    // The suggested section reports no total.
    assertThat(suggested.getTotalAvailable()).isEqualTo(0);
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
                            "mtv",
                            device(
                                "device-0",
                                DeviceStatus.IDLE,
                                "android_real_device",
                                "alice",
                                dims("release_version", "16")))))
            .build();
    FleetSnapshot versionSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(versionLab, BUILD_TIME);
    DeviceCorpus versionCorpus =
        new DeviceCorpus(versionSnapshot, new LazyPostings(versionSnapshot.devices()), null);

    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(
            versionCorpus,
            FleetColumnCatalogRequest.newBuilder().addRecentKeys("device_field::version").build());

    assertThat(
            entryFor(section(response, "Suggested for you"), "device_field::version")
                .getDescription())
        .isNotEmpty();
    assertThat(
            entryFor(section(response, "Built-in fields"), "device_field::version")
                .getDescription())
        .isEqualTo(
            entryFor(section(response, "Suggested for you"), "device_field::version")
                .getDescription());
    assertThat(
            entryFor(section(response, "Built-in fields"), "device_field::status").getDescription())
        .isEmpty();
  }

  @Test
  public void suggested_omittedWhenNothingToSuggest() {
    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(corpus, FleetColumnCatalogRequest.getDefaultInstance());

    assertThat(headings(response)).doesNotContain("Suggested for you");
  }

  @Test
  public void search_filtersCategoriesInPlaceAndOmitsEmptySections() {
    FleetColumnCatalogResponse withQuery =
        cataloger.getColumnCatalog(
            corpus, FleetColumnCatalogRequest.newBuilder().setQuery("model").build());

    // Under Scheme B (Category Filter Tree Mode), categories are filtered in place.
    // 'model' matches dimension::model, so Dimensions section is present, Search results is
    // omitted.
    FleetColumnCatalogSection dimensions = section(withQuery, "Dimensions");
    assertThat(keys(dimensions)).contains("dimension::model");
    assertThat(dimensions.getTotalAvailable()).isEqualTo(1);
    assertThat(headings(withQuery)).doesNotContain("Search results");
    assertThat(headings(withQuery)).doesNotContain("Suggested for you");

    // With no query, browse sections are returned and Search results is omitted.
    FleetColumnCatalogResponse noQuery =
        cataloger.getColumnCatalog(corpus, FleetColumnCatalogRequest.getDefaultInstance());
    assertThat(headings(noQuery)).doesNotContain("Search results");
  }

  @Test
  public void dimensionsSection_keepsDimensionSharingBuiltinBareNameWhenValuesDisagree() {
    // Two hosts ("lab-a" and "lab-b") whose devices swap the "host_name" dimension ("lab-b" on
    // lab-a, "lab-a" on lab-b). The aggregate index counts match host_field::host_name (1 each),
    // so canMatchInIndex passes and the per-record comparison loop detects the disagreement.
    // Meanwhile dimension::uuid ("other-uuid") is rejected up front by canMatchInIndex.
    LabQueryResult disagreeingFleet =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(2)
                    .addLabData(
                        labData(
                            "lab-a",
                            "1.1.1.1",
                            "mtv",
                            device(
                                "device-0",
                                DeviceStatus.IDLE,
                                "android_real_device",
                                "alice",
                                dims("host_name", "lab-b", "uuid", "other-uuid"))))
                    .addLabData(
                        labData(
                            "lab-b",
                            "2.2.2.2",
                            "sjc",
                            device(
                                "device-1",
                                DeviceStatus.IDLE,
                                "android_real_device",
                                "bob",
                                dims("host_name", "lab-a")))))
            .build();
    FleetSnapshot disagreeingSnapshot =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(disagreeingFleet, BUILD_TIME);
    DeviceCorpus disagreeingCorpus =
        new DeviceCorpus(
            disagreeingSnapshot, new LazyPostings(disagreeingSnapshot.devices()), null);

    FleetColumnCatalogResponse hostNameSearch =
        cataloger.getColumnCatalog(
            disagreeingCorpus,
            FleetColumnCatalogRequest.newBuilder().setQuery("host_name").build());
    assertThat(keys(section(hostNameSearch, "Dimensions"))).containsExactly("dimension::host_name");

    FleetColumnCatalogResponse uuidSearch =
        cataloger.getColumnCatalog(
            disagreeingCorpus, FleetColumnCatalogRequest.newBuilder().setQuery("uuid").build());
    assertThat(keys(section(uuidSearch, "Dimensions"))).containsExactly("dimension::uuid");
  }

  @Test
  public void redundantCache_reusedAcrossCallsAndInvalidatedWhenSnapshotRefreshes() {
    FleetColumnCataloger cachingCataloger = new FleetColumnCataloger();
    FleetColumnCatalogRequest hostNameQuery =
        FleetColumnCatalogRequest.newBuilder().setQuery("host_name").build();

    // First call populates redundantCache for FLEET_SELF (dimension::host_name is redundant).
    FleetColumnCatalogResponse first =
        cachingCataloger.getColumnCatalog(corpus, FleetColumnCatalogRequest.getDefaultInstance());
    assertThat(section(first, "Dimensions").getTotalAvailable()).isEqualTo(12);
    assertThat(headings(cachingCataloger.getColumnCatalog(corpus, hostNameQuery)))
        .doesNotContain("Dimensions");

    // Build a refreshed snapshot with the exact same 3 devices (recordCount = 3) and the exact
    // same set of 19 key strings, except device-0's host_name dimension is "lab-b" (disagreeing
    // with host_field::host_name = "lab-a").
    FleetSnapshot refreshedSnapshot =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(fleet("lab-b"), BUILD_TIME.plusSeconds(60));
    assertThat(refreshedSnapshot.deviceCount()).isEqualTo(snapshot.deviceCount());
    assertThat(refreshedSnapshot.index().keyIds()).isEqualTo(snapshot.index().keyIds());
    assertThat(refreshedSnapshot.index().keyIds()).isNotSameInstanceAs(snapshot.index().keyIds());

    // Prove that the second call hits redundantCache without re-scanning records: pair
    // refreshedSnapshot.devices() (where device-0 disagrees) with snapshot.index() (which shares
    // the exact same keyIds ImmutableSet reference). A fresh cataloger scans the records and keeps
    // dimension::host_name, whereas cachingCataloger returns the cached redundant set and omits it.
    FleetSnapshot sameIndexDisagreeingRecordsSnapshot =
        FleetSnapshot.builder()
            .setBuildTime(BUILD_TIME.plusSeconds(30))
            .setDevices(refreshedSnapshot.devices())
            .setHosts(refreshedSnapshot.hosts())
            .setIndex(snapshot.index())
            .setHostIndex(snapshot.hostIndex())
            .setUuidToIndex(refreshedSnapshot.uuidToIndex())
            .build();
    DeviceCorpus sameIndexDisagreeingRecordsCorpus =
        new DeviceCorpus(
            sameIndexDisagreeingRecordsSnapshot,
            new LazyPostings(sameIndexDisagreeingRecordsSnapshot.devices()),
            null);
    assertThat(
            keys(
                section(
                    new FleetColumnCataloger()
                        .getColumnCatalog(sameIndexDisagreeingRecordsCorpus, hostNameQuery),
                    "Dimensions")))
        .containsExactly("dimension::host_name");
    FleetColumnCatalogResponse cachedResponse =
        cachingCataloger.getColumnCatalog(
            sameIndexDisagreeingRecordsCorpus, FleetColumnCatalogRequest.getDefaultInstance());
    assertThat(section(cachedResponse, "Dimensions").getTotalAvailable()).isEqualTo(12);
    assertThat(
            headings(
                cachingCataloger.getColumnCatalog(
                    sameIndexDisagreeingRecordsCorpus, hostNameQuery)))
        .doesNotContain("Dimensions");

    // Now pass refreshedCorpus (same recordCount = 3 and equal keyIds contents, but a distinct
    // keyIds ImmutableSet instance): cached.keyIds() == keyIds is false, invalidating the cache.
    DeviceCorpus refreshedCorpus =
        new DeviceCorpus(refreshedSnapshot, new LazyPostings(refreshedSnapshot.devices()), null);
    FleetColumnCatalogResponse afterRefresh =
        cachingCataloger.getColumnCatalog(
            refreshedCorpus, FleetColumnCatalogRequest.getDefaultInstance());
    assertThat(section(afterRefresh, "Dimensions").getTotalAvailable()).isEqualTo(13);
    assertThat(
            keys(
                section(
                    cachingCataloger.getColumnCatalog(refreshedCorpus, hostNameQuery),
                    "Dimensions")))
        .containsExactly("dimension::host_name");
  }

  @Test
  public void search_ranksExactMatchAheadOfPrefixAndSubstring() {
    DimensionCatalogStore store = new DimensionCatalogStore();
    store.setDimensionNames(Fleet.FLEET_SELF, ImmutableSet.of("f0", "f0_prefix", "a_f0_sub"));
    FleetColumnCataloger customCataloger = new FleetColumnCataloger(store);

    FleetColumnCatalogResponse response =
        customCataloger.getColumnCatalog(
            corpus, FleetColumnCatalogRequest.newBuilder().setQuery("f0").build());

    ImmutableList<String> dimensionKeys = keys(section(response, "Dimensions"));
    assertThat(dimensionKeys.get(0)).isEqualTo("dimension::f0");
    assertThat(dimensionKeys)
        .containsAtLeast(
            "dimension::f0", "dimension::f01", "dimension::f0_prefix", "dimension::a_f0_sub")
        .inOrder();
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
    return fleet("lab-a");
  }

  private static LabQueryResult fleet(String device0HostNameDim) {
    Map<String, String> device0Dims = new LinkedHashMap<>();
    device0Dims.put("model", "pixel");
    device0Dims.put("pool", "shared");
    device0Dims.put("host_name", device0HostNameDim);
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
