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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.DIM_TOP;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.PROP_TOP;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SEARCH_LIMIT;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_BUILTIN;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_CONNECTED_HOST_PROPERTIES;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_DIMENSIONS;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_HOST_BUILTIN;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.norm;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogSection;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.DeviceKeyLists;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionCatalogStore;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.ToIntFunction;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Builds the column catalog for the device entity.
 *
 * <p>Categorizes keys into:
 *
 * <ul>
 *   <li><b>Suggested for you</b>: Keys currently in active filters or recently used columns.
 *   <li><b>Built-in fields</b>: Standard device attributes and config keys.
 *   <li><b>Built-in fields from connected host</b>: Common host fields projected onto devices.
 *   <li><b>Dimensions</b>: Custom device dimensions ranked by device coverage, incorporating
 *       discovered dimensions and counts from {@link DimensionCatalogStore}. Browse mode lists
 *       declared dimensions only; see {@link KeyCatalogs#declared}.
 *   <li><b>Host properties from connected host</b>: Projected host properties ranked by device
 *       coverage.
 * </ul>
 */
@Singleton
public final class DeviceColumnCataloger {

  private static final String SECTION_SUGGESTED = "Suggested for you";

  private static final String REASON_ACTIVE_FILTER = "in your active filters";
  private static final String REASON_RECENTLY_USED = "recently used";

  private record CachedRedundant(
      ImmutableSet<String> keyIds, int recordCount, ImmutableSet<String> redundant) {}

  private final DimensionCatalogStore dimensionCatalogStore;
  private final ConcurrentMap<Fleet, CachedRedundant> redundantCache = new ConcurrentHashMap<>();

  @Inject
  DeviceColumnCataloger(DimensionCatalogStore dimensionCatalogStore) {
    this.dimensionCatalogStore = dimensionCatalogStore;
  }

  public DeviceColumnCataloger() {
    this(new DimensionCatalogStore());
  }

  /**
   * Builds the complete column catalog for the device entity.
   *
   * @param corpus the device projection providing the index and catalog dimensions
   * @param request the dialog state: an optional query, current filters, and recently used keys
   */
  public FleetColumnCatalogResponse getColumnCatalog(
      DeviceCorpus corpus, FleetColumnCatalogRequest request) {
    FleetIndex index = corpus.index();
    Fleet fleet =
        request.getFleet() == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : request.getFleet();
    ImmutableMap<String, Integer> indexedCounts = KeyCounts.entityCounts(index);
    DeviceKeyLists keys =
        KeyCatalogs.deviceKeys(corpus, fleet, dimensionCatalogStore, indexedCounts);
    ImmutableMap<String, Integer> deviceCounts = keys.deviceCounts();
    ImmutableSet<String> redundant =
        redundantDims(fleet, corpus, index, keys.builtin(), keys.hostBuiltin(), keys.dimensions());
    ImmutableList<DeviceKeyDescriptor> dimensions =
        keys.dimensions().stream()
            .filter(desc -> !redundant.contains(desc.id()))
            .collect(toImmutableList());

    FleetColumnCatalogResponse.Builder response = FleetColumnCatalogResponse.newBuilder();
    String query = request.getQuery().trim();
    if (!query.isEmpty()) {
      String q = norm(query);
      ToIntFunction<DeviceKeyDescriptor> rank =
          desc -> KeyCatalogs.matchRank(desc, DeviceKeyDisplays.titleDisplayName(desc), q);
      addSection(
          response,
          SECTION_BUILTIN,
          KeyCatalogs.searchMatches(keys.builtin(), rank),
          0,
          deviceCounts);
      addSection(
          response,
          SECTION_HOST_BUILTIN,
          KeyCatalogs.searchMatches(keys.hostBuiltin(), rank),
          0,
          deviceCounts);
      addSection(
          response,
          SECTION_DIMENSIONS,
          KeyCatalogs.searchMatches(dimensions, rank),
          SEARCH_LIMIT,
          deviceCounts);
      addSection(
          response,
          SECTION_CONNECTED_HOST_PROPERTIES,
          KeyCatalogs.searchMatches(keys.properties(), rank),
          SEARCH_LIMIT,
          deviceCounts);
      return response.build();
    }

    addSuggestedSection(response, corpus, index, request);
    addSection(response, SECTION_BUILTIN, keys.builtin(), 0, deviceCounts);
    addSection(response, SECTION_HOST_BUILTIN, keys.hostBuiltin(), 0, deviceCounts);
    addSection(
        response,
        SECTION_DIMENSIONS,
        KeyCatalogs.declared(dimensions),
        DIM_TOP,
        dimensions.size(),
        deviceCounts);
    addSection(
        response, SECTION_CONNECTED_HOST_PROPERTIES, keys.properties(), PROP_TOP, deviceCounts);
    return response.build();
  }

  private static void addSuggestedSection(
      FleetColumnCatalogResponse.Builder response,
      DeviceCorpus corpus,
      FleetIndex index,
      FleetColumnCatalogRequest request) {
    ImmutableSet<String> presentKeys = index.keyIds();
    LinkedHashSet<String> added = new LinkedHashSet<>();
    ImmutableList.Builder<FleetColumnCatalogEntry> entries = ImmutableList.builder();

    for (Filter filter : request.getFiltersList()) {
      String keyId = filter.getKey();
      if (presentKeys.contains(keyId) && added.add(keyId)) {
        entries.add(suggestedEntry(corpus, keyId, REASON_ACTIVE_FILTER));
      }
    }

    for (String keyId : request.getRecentKeysList()) {
      if (presentKeys.contains(keyId) && added.add(keyId)) {
        entries.add(suggestedEntry(corpus, keyId, REASON_RECENTLY_USED));
      }
    }

    ImmutableList<FleetColumnCatalogEntry> entryList = entries.build();
    if (!entryList.isEmpty()) {
      response.addSections(
          FleetColumnCatalogSection.newBuilder()
              .setHeading(SECTION_SUGGESTED)
              .addAllEntries(entryList));
    }
  }

  private static void addSection(
      FleetColumnCatalogResponse.Builder response,
      String heading,
      List<DeviceKeyDescriptor> descriptors,
      int limit,
      ImmutableMap<String, Integer> deviceCounts) {
    addSection(response, heading, descriptors, limit, descriptors.size(), deviceCounts);
  }

  private static void addSection(
      FleetColumnCatalogResponse.Builder response,
      String heading,
      List<DeviceKeyDescriptor> descriptors,
      int limit,
      int total,
      ImmutableMap<String, Integer> deviceCounts) {
    if (descriptors.isEmpty()) {
      return;
    }
    FleetColumnCatalogSection.Builder section =
        FleetColumnCatalogSection.newBuilder().setHeading(heading);
    if (limit > 0) {
      section.setTotalAvailable(total);
    }
    descriptors.stream()
        .limit(limit > 0 ? limit : Long.MAX_VALUE)
        .map(desc -> entry(desc, deviceCounts.getOrDefault(desc.id(), 0)))
        .forEach(section::addEntries);
    response.addSections(section);
  }

  private static FleetColumnCatalogEntry entry(DeviceKeyDescriptor desc, int deviceCount) {
    return FleetColumnCatalogEntry.newBuilder()
        .setKey(desc.id())
        .setDisplayName(DeviceKeyDisplays.titleDisplayName(desc))
        .setDescription(desc.display().description())
        .setCoverage(KeyCatalogs.coverage(desc.isDimension(), deviceCount))
        .build();
  }

  private static FleetColumnCatalogEntry suggestedEntry(
      DeviceCorpus corpus, String keyId, String reason) {
    Optional<DeviceKeyDescriptor> key = corpus.getKey(keyId);
    return FleetColumnCatalogEntry.newBuilder()
        .setKey(keyId)
        .setDisplayName(key.map(DeviceKeyDisplays::titleDisplayName).orElse(keyId))
        .setDescription(key.map(desc -> desc.display().description()).orElse(""))
        .setCoverage(KeyCatalogs.coverage(false, 0))
        .setReason(reason)
        .build();
  }

  @SuppressWarnings("ReferenceEquality")
  private ImmutableSet<String> redundantDims(
      Fleet fleet,
      DeviceCorpus corpus,
      FleetIndex index,
      List<DeviceKeyDescriptor> builtin,
      List<DeviceKeyDescriptor> hostBuiltin,
      List<DeviceKeyDescriptor> dimensions) {
    ImmutableSet<String> keyIds = index.keyIds();
    int recordCount = corpus.recordCount();
    CachedRedundant cached = redundantCache.get(fleet);
    if (cached != null && cached.keyIds() == keyIds && cached.recordCount() == recordCount) {
      return cached.redundant();
    }

    ImmutableMap<String, String> bareToTwin =
        Streams.concat(builtin.stream(), hostBuiltin.stream())
            .collect(
                toImmutableMap(
                    DeviceKeyDescriptor::bareName,
                    DeviceKeyDescriptor::id,
                    (first, second) -> first));

    Map<String, String> liveCandidates = new LinkedHashMap<>();
    for (DeviceKeyDescriptor dim : dimensions) {
      if (!keyIds.contains(dim.id())) {
        continue;
      }
      String twin = bareToTwin.get(dim.bareName());
      if (twin != null && canMatchInIndex(index, dim.id(), twin)) {
        liveCandidates.put(dim.id(), twin);
      }
    }

    for (int i = 0; i < recordCount && !liveCandidates.isEmpty(); i++) {
      Iterator<Map.Entry<String, String>> it = liveCandidates.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<String, String> candidate = it.next();
        ImmutableSet<String> dimVals = corpus.valuesForKey(i, candidate.getKey());
        if (!dimVals.isEmpty() && !dimVals.equals(corpus.valuesForKey(i, candidate.getValue()))) {
          it.remove();
        }
      }
    }

    ImmutableSet<String> redundant = ImmutableSet.copyOf(liveCandidates.keySet());
    redundantCache.put(fleet, new CachedRedundant(keyIds, recordCount, redundant));
    return redundant;
  }

  private static boolean canMatchInIndex(FleetIndex index, String dimKey, String twin) {
    return index.valueCounts(dimKey).entrySet().stream()
        .allMatch(entry -> index.valueCount(twin, entry.getKey()) >= entry.getValue());
  }
}
