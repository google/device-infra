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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.DIM_TOP;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.PROP_TOP;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SEARCH_LIMIT;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_BUILTIN;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_CONNECTED_HOST_PROPERTIES;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_DIMENSIONS;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_HOST_BUILTIN;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_PROPERTIES;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.norm;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.CoverageInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterChipMetadata;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterKeyCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterKeyCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterKeyEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFilterKeySection;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.DeviceKeyLists;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.HostKeyLists;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionCatalogStore;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import java.util.List;
import java.util.function.ToIntFunction;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Builds the filter-key catalog for the "Add a filter" dialog across device and host search.
 *
 * <p>Categorizes pickable filter keys into ordered sections:
 *
 * <ul>
 *   <li><b>Built-in fields</b>: Standard device or host attributes listed in full and sorted
 *       alphabetically by display name.
 *   <li><b>Built-in fields from connected host</b>: Common host fields projected onto devices
 *       (device search only), listed in full and sorted alphabetically by display name.
 *   <li><b>Dimensions</b>: Device dimensions ranked by device coverage (device search only). Browse
 *       mode lists declared dimensions only; see {@link KeyCatalogs#declared}.
 *   <li><b>Host properties from connected host</b> / <b>Host properties</b>: Host properties ranked
 *       by device or host coverage.
 * </ul>
 *
 * <p>In search mode each section keeps only the keys matching the query, best match first (exact
 * name, then prefix, then substring), and the section's own order within one match rank; see {@link
 * KeyCatalogs#searchMatches}.
 *
 * <p>Each entry carries the {@link FleetFilterChipMetadata} needed to open the value picker
 * immediately, the {@link CoverageInfo} display decision (shown for device dimensions on device
 * search and for host properties on host search), and whether the key already has an active filter
 * chip.
 */
@Singleton
public final class FleetFilterKeyCataloger {

  private final DimensionCatalogStore dimensionCatalogStore;

  @Inject
  FleetFilterKeyCataloger(DimensionCatalogStore dimensionCatalogStore) {
    this.dimensionCatalogStore = dimensionCatalogStore;
  }

  /** Builds its own collaborators with an empty catalog store; for tests and one-off use. */
  public FleetFilterKeyCataloger() {
    this(new DimensionCatalogStore());
  }

  /** Returns the ordered filter-key catalog for the requested entity and dialog state. */
  public FleetFilterKeyCatalogResponse getFilterKeyCatalog(
      SearchCorpus corpus, FleetFilterKeyCatalogRequest request) {
    if (corpus.entity() == SearchEntity.SEARCH_ENTITY_HOST) {
      return getHostCatalog((HostCorpus) corpus, request);
    }
    return getDeviceCatalog((DeviceCorpus) corpus, request);
  }

  /**
   * Device search: Built-in fields, Built-in fields from connected host, Dimensions, and Host
   * properties from connected host partitioned by {@link KeyCatalogs#deviceKeys}.
   */
  private FleetFilterKeyCatalogResponse getDeviceCatalog(
      DeviceCorpus corpus, FleetFilterKeyCatalogRequest request) {
    ImmutableMap<String, Integer> indexedCounts = KeyCounts.entityCounts(corpus.index());
    ImmutableSet<String> appliedKeys =
        request.getFiltersList().stream().map(Filter::getKey).collect(toImmutableSet());
    DeviceKeyLists keys =
        KeyCatalogs.deviceKeys(corpus, request.getFleet(), dimensionCatalogStore, indexedCounts);
    ImmutableMap<String, Integer> deviceCounts = keys.deviceCounts();

    FleetFilterKeyCatalogResponse.Builder response = FleetFilterKeyCatalogResponse.newBuilder();
    String query = request.getQuery().trim();
    if (!query.isEmpty()) {
      String q = norm(query);
      ToIntFunction<DeviceKeyDescriptor> rank =
          desc -> KeyCatalogs.matchRank(desc, DeviceKeyDisplays.titleDisplayName(desc), q);
      addDeviceSection(
          response,
          SECTION_BUILTIN,
          KeyCatalogs.searchMatches(keys.builtin(), rank),
          0,
          deviceCounts,
          appliedKeys);
      addDeviceSection(
          response,
          SECTION_HOST_BUILTIN,
          KeyCatalogs.searchMatches(keys.hostBuiltin(), rank),
          0,
          deviceCounts,
          appliedKeys);
      addDeviceSection(
          response,
          SECTION_DIMENSIONS,
          KeyCatalogs.searchMatches(keys.dimensions(), rank),
          SEARCH_LIMIT,
          deviceCounts,
          appliedKeys);
      addDeviceSection(
          response,
          SECTION_CONNECTED_HOST_PROPERTIES,
          KeyCatalogs.searchMatches(keys.properties(), rank),
          SEARCH_LIMIT,
          deviceCounts,
          appliedKeys);
      return response.build();
    }
    addDeviceSection(response, SECTION_BUILTIN, keys.builtin(), 0, deviceCounts, appliedKeys);
    addDeviceSection(
        response, SECTION_HOST_BUILTIN, keys.hostBuiltin(), 0, deviceCounts, appliedKeys);
    addDeviceSection(
        response,
        SECTION_DIMENSIONS,
        KeyCatalogs.declared(keys.dimensions()),
        DIM_TOP,
        keys.dimensions().size(),
        deviceCounts,
        appliedKeys);
    addDeviceSection(
        response,
        SECTION_CONNECTED_HOST_PROPERTIES,
        keys.properties(),
        PROP_TOP,
        deviceCounts,
        appliedKeys);
    return response.build();
  }

  /**
   * Host search: Built-in fields and Host properties partitioned by {@link KeyCatalogs#hostKeys}.
   */
  private FleetFilterKeyCatalogResponse getHostCatalog(
      HostCorpus corpus, FleetFilterKeyCatalogRequest request) {
    ImmutableMap<String, Integer> hostCounts = KeyCounts.entityCounts(corpus.index());
    ImmutableSet<String> appliedKeys =
        request.getFiltersList().stream().map(Filter::getKey).collect(toImmutableSet());
    HostKeyLists keys = KeyCatalogs.hostKeys(corpus, hostCounts);

    FleetFilterKeyCatalogResponse.Builder response = FleetFilterKeyCatalogResponse.newBuilder();
    String query = request.getQuery().trim();
    if (!query.isEmpty()) {
      String q = norm(query);
      ToIntFunction<HostKeyDescriptor> rank =
          desc -> KeyCatalogs.matchRank(desc, HostKeyDisplays.titleDisplayName(desc), q);
      addHostSection(
          response,
          SECTION_BUILTIN,
          KeyCatalogs.searchMatches(keys.builtin(), rank),
          0,
          hostCounts,
          appliedKeys);
      addHostSection(
          response,
          SECTION_PROPERTIES,
          KeyCatalogs.searchMatches(keys.properties(), rank),
          SEARCH_LIMIT,
          hostCounts,
          appliedKeys);
      return response.build();
    }
    addHostSection(response, SECTION_BUILTIN, keys.builtin(), 0, hostCounts, appliedKeys);
    addHostSection(
        response, SECTION_PROPERTIES, keys.properties(), PROP_TOP, hostCounts, appliedKeys);
    return response.build();
  }

  /**
   * Appends a section unless it has no entries to show. A {@code limit} of 0 lists every key and
   * reports no total; a positive limit keeps the first keys in the given order and reports how many
   * there are.
   */
  private static void addDeviceSection(
      FleetFilterKeyCatalogResponse.Builder response,
      String heading,
      List<DeviceKeyDescriptor> descriptors,
      int limit,
      ImmutableMap<String, Integer> deviceCounts,
      ImmutableSet<String> appliedKeys) {
    addDeviceSection(
        response, heading, descriptors, limit, descriptors.size(), deviceCounts, appliedKeys);
  }

  /**
   * Same as above, with an explicit total for a section that lists fewer keys than it offers: the
   * browse Dimensions section lists declared dimensions only but reports every dimension, since the
   * rest are one search away. Sections with no entries to display are omitted so the dialog never
   * renders an empty section heading.
   */
  private static void addDeviceSection(
      FleetFilterKeyCatalogResponse.Builder response,
      String heading,
      List<DeviceKeyDescriptor> descriptors,
      int limit,
      int total,
      ImmutableMap<String, Integer> deviceCounts,
      ImmutableSet<String> appliedKeys) {
    if (descriptors.isEmpty()) {
      return;
    }
    FleetFilterKeySection.Builder section = FleetFilterKeySection.newBuilder().setHeading(heading);
    if (limit > 0) {
      section.setTotalAvailable(total);
    }
    descriptors.stream()
        .limit(limit > 0 ? limit : Long.MAX_VALUE)
        .map(
            desc ->
                deviceEntry(
                    desc, deviceCounts.getOrDefault(desc.id(), 0), appliedKeys.contains(desc.id())))
        .forEach(section::addEntries);
    response.addSections(section);
  }

  /** Host counterpart of {@link #addDeviceSection}; shows coverage for host properties only. */
  private static void addHostSection(
      FleetFilterKeyCatalogResponse.Builder response,
      String heading,
      List<HostKeyDescriptor> descriptors,
      int limit,
      ImmutableMap<String, Integer> hostCounts,
      ImmutableSet<String> appliedKeys) {
    if (descriptors.isEmpty()) {
      return;
    }
    FleetFilterKeySection.Builder section = FleetFilterKeySection.newBuilder().setHeading(heading);
    if (limit > 0) {
      section.setTotalAvailable(descriptors.size());
    }
    descriptors.stream()
        .limit(limit > 0 ? limit : Long.MAX_VALUE)
        .map(
            desc ->
                hostEntry(
                    desc, hostCounts.getOrDefault(desc.id(), 0), appliedKeys.contains(desc.id())))
        .forEach(section::addEntries);
    response.addSections(section);
  }

  /**
   * One device row. The metadata is everything the value picker needs on click, built the same way
   * as for the suggestions, so the picker opens with no second request.
   */
  private static FleetFilterKeyEntry deviceEntry(
      DeviceKeyDescriptor desc, int deviceCount, boolean applied) {
    return FleetFilterKeyEntry.newBuilder()
        .setKey(desc.id())
        .setMetadata(
            FleetFilterChipMetadata.newBuilder()
                .setKeyDisplayName(DeviceKeyDisplays.titleDisplayName(desc))
                .setCanUseAdvanced(true)
                .setIsPlural(desc.display().isPlural())
                .setDescription(desc.display().description()))
        .setCoverage(KeyCatalogs.coverage(desc.isDimension(), deviceCount))
        .setApplied(applied)
        .build();
  }

  /** One host row. Host search shows coverage for host properties only. */
  private static FleetFilterKeyEntry hostEntry(
      HostKeyDescriptor desc, int hostCount, boolean applied) {
    return FleetFilterKeyEntry.newBuilder()
        .setKey(desc.id())
        .setMetadata(
            FleetFilterChipMetadata.newBuilder()
                .setKeyDisplayName(HostKeyDisplays.titleDisplayName(desc))
                .setCanUseAdvanced(true)
                .setIsPlural(desc.display().isPlural())
                .setDescription(desc.display().description()))
        .setCoverage(KeyCatalogs.coverage(desc.isHostProperty(), hostCount))
        .setApplied(applied)
        .build();
  }
}
