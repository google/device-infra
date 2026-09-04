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
import static com.google.devtools.mobileharness.fe.v6.service.search.query.FleetKeyIds.bareName;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogSection;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.Postings;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionCatalogStore;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Builds the column catalog for the device entity.
 *
 * <p>Categorizes keys into:
 *
 * <ul>
 *   <li><b>Suggested for you</b>: Keys currently in active filters or recently used columns.
 *   <li><b>Built-in fields</b>: Standard device attributes and common projected host fields.
 *   <li><b>Dimensions</b>: Custom device dimensions ranked by device coverage, incorporating
 *       discovered dimension names from {@link DimensionCatalogStore}.
 *   <li><b>Host properties</b>: Projected host properties ranked by device coverage.
 *   <li><b>Search results</b>: Matching keys when a search query is provided.
 * </ul>
 */
@Singleton
public final class DeviceColumnCataloger {

  private static final String SECTION_SUGGESTED = "Suggested for you";
  private static final String SECTION_BUILTIN = "Built-in fields";
  private static final String SECTION_DIMENSIONS = "Dimensions";
  private static final String SECTION_PROPERTIES = "Host properties";

  private static final String REASON_ACTIVE_FILTER = "in your active filters";
  private static final String REASON_RECENTLY_USED = "recently used";

  private static final int DIM_TOP = 10;
  private static final int PROP_TOP = 5;
  private static final int SEARCH_LIMIT = 50;

  private final DimensionCatalogStore dimensionCatalogStore;

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
   * @param corpus the device projection providing the index, postings, and catalog dimensions
   * @param request the dialog state: an optional query, current filters, and recently used keys
   */
  public FleetColumnCatalogResponse getColumnCatalog(
      DeviceCorpus corpus, FleetColumnCatalogRequest request) {
    FleetIndex index = corpus.index();
    ImmutableMap<String, Integer> deviceCounts = keyDeviceCounts(index, corpus.postings());
    ImmutableSet<String> redundant = redundantDims(corpus, index);

    Fleet fleet =
        request.getFleet() == Fleet.FLEET_UNSPECIFIED ? Fleet.FLEET_SELF : request.getFleet();
    ImmutableSet<String> catalogDimensions = dimensionCatalogStore.getDimensionNames(fleet);

    Comparator<String> byCoverage =
        Comparator.<String>comparingInt(keyId -> -deviceCounts.getOrDefault(keyId, 0))
            .thenComparing(keyId -> Ascii.toLowerCase(displayName(corpus, keyId)));

    List<String> builtin = new ArrayList<>();
    List<String> dimensions = new ArrayList<>();
    List<String> properties = new ArrayList<>();
    for (String keyId : index.keyIds()) {
      if (corpus.getKey(keyId).isEmpty()
          && !keyId.startsWith(DeviceKeys.PREFIX_DIMENSION)
          && !keyId.startsWith(HostKeys.PREFIX_HOST_PROPERTY)) {
        continue;
      }
      if (keyId.startsWith(DeviceKeys.PREFIX_DIMENSION)) {
        if (!redundant.contains(keyId)) {
          dimensions.add(keyId);
        }
      } else if (keyId.startsWith(HostKeys.PREFIX_HOST_PROPERTY)) {
        properties.add(keyId);
      } else {
        builtin.add(keyId);
      }
    }

    // Merge discovered dimension names from catalog store.
    Set<String> seenDimensions = new HashSet<>(dimensions);
    for (String dimName : catalogDimensions) {
      String keyId = DeviceKeys.PREFIX_DIMENSION + dimName;
      if (!redundant.contains(keyId) && seenDimensions.add(keyId)) {
        dimensions.add(keyId);
      }
    }

    builtin.sort(Comparator.comparing(keyId -> Ascii.toLowerCase(displayName(corpus, keyId))));
    dimensions.sort(byCoverage);
    properties.sort(byCoverage);

    FleetColumnCatalogResponse.Builder response = FleetColumnCatalogResponse.newBuilder();

    if (!request.getQuery().isEmpty()) {
      String q = norm(request.getQuery());
      ImmutableList<String> matchingBuiltin =
          builtin.stream()
              .filter(
                  keyId -> {
                    String disp = norm(displayName(corpus, keyId));
                    String bare = norm(bareName(keyId));
                    return disp.contains(q) || bare.contains(q);
                  })
              .collect(toImmutableList());
      if (!matchingBuiltin.isEmpty()) {
        addFullSection(response, corpus, SECTION_BUILTIN, matchingBuiltin, deviceCounts);
      }

      ImmutableList<String> matchingDims =
          dimensions.stream()
              .filter(
                  keyId -> {
                    String disp = norm(displayName(corpus, keyId));
                    String bare = norm(bareName(keyId));
                    return disp.contains(q) || bare.contains(q);
                  })
              .collect(toImmutableList());
      if (!matchingDims.isEmpty()) {
        addTopNSection(
            response, corpus, SECTION_DIMENSIONS, matchingDims, SEARCH_LIMIT, deviceCounts);
      }

      ImmutableList<String> matchingProps =
          properties.stream()
              .filter(
                  keyId -> {
                    String disp = norm(displayName(corpus, keyId));
                    String bare = norm(bareName(keyId));
                    return disp.contains(q) || bare.contains(q);
                  })
              .collect(toImmutableList());
      if (!matchingProps.isEmpty()) {
        addTopNSection(
            response, corpus, SECTION_PROPERTIES, matchingProps, SEARCH_LIMIT, deviceCounts);
      }

      return response.build();
    }

    addSuggestedSection(response, corpus, index, deviceCounts, request);

    if (!builtin.isEmpty()) {
      addFullSection(response, corpus, SECTION_BUILTIN, builtin, deviceCounts);
    }
    if (!dimensions.isEmpty()) {
      addTopNSection(response, corpus, SECTION_DIMENSIONS, dimensions, DIM_TOP, deviceCounts);
    }
    if (!properties.isEmpty()) {
      addTopNSection(response, corpus, SECTION_PROPERTIES, properties, PROP_TOP, deviceCounts);
    }

    return response.build();
  }

  private static void addSuggestedSection(
      FleetColumnCatalogResponse.Builder response,
      DeviceCorpus corpus,
      FleetIndex index,
      ImmutableMap<String, Integer> deviceCounts,
      FleetColumnCatalogRequest request) {
    ImmutableSet<String> presentKeys = index.keyIds();
    LinkedHashSet<String> added = new LinkedHashSet<>();
    ImmutableList.Builder<FleetColumnCatalogEntry> entries = ImmutableList.builder();

    for (Filter filter : request.getFiltersList()) {
      String keyId = filter.getKey();
      if (presentKeys.contains(keyId) && added.add(keyId)) {
        entries.add(
            entry(corpus, keyId, deviceCounts.getOrDefault(keyId, 0), REASON_ACTIVE_FILTER));
      }
    }

    for (String keyId : request.getRecentKeysList()) {
      if (presentKeys.contains(keyId) && added.add(keyId)) {
        entries.add(
            entry(corpus, keyId, deviceCounts.getOrDefault(keyId, 0), REASON_RECENTLY_USED));
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

  private static void addFullSection(
      FleetColumnCatalogResponse.Builder response,
      DeviceCorpus corpus,
      String heading,
      List<String> keyIds,
      ImmutableMap<String, Integer> deviceCounts) {
    FleetColumnCatalogSection.Builder section =
        FleetColumnCatalogSection.newBuilder().setHeading(heading);
    for (String keyId : keyIds) {
      section.addEntries(entry(corpus, keyId, deviceCounts.getOrDefault(keyId, 0), ""));
    }
    response.addSections(section);
  }

  private static void addTopNSection(
      FleetColumnCatalogResponse.Builder response,
      DeviceCorpus corpus,
      String heading,
      List<String> keyIds,
      int limit,
      ImmutableMap<String, Integer> deviceCounts) {
    int total = keyIds.size();
    List<String> top = keyIds.subList(0, Math.min(total, limit));
    FleetColumnCatalogSection.Builder section =
        FleetColumnCatalogSection.newBuilder().setHeading(heading).setTotalAvailable(total);
    for (String keyId : top) {
      section.addEntries(entry(corpus, keyId, deviceCounts.getOrDefault(keyId, 0), ""));
    }
    response.addSections(section);
  }

  private static FleetColumnCatalogEntry entry(
      DeviceCorpus corpus, String keyId, int deviceCount, String reason) {
    FleetColumnCatalogEntry.Builder b =
        FleetColumnCatalogEntry.newBuilder()
            .setKey(keyId)
            .setDisplayName(displayName(corpus, keyId))
            .setDeviceCount(deviceCount);
    if (!reason.isEmpty()) {
      b.setReason(reason);
    }
    return b.build();
  }

  private static ImmutableSet<String> redundantDims(SearchCorpus corpus, FleetIndex index) {
    ImmutableSet.Builder<String> out = ImmutableSet.builder();
    for (String dimKey : index.keyIds()) {
      if (!dimKey.startsWith(DeviceKeys.PREFIX_DIMENSION)) {
        continue;
      }
      String bare = bareName(dimKey);
      String hostKey = HostKeys.PREFIX_HOST_FIELD + bare;
      String deviceKey = DeviceKeys.PREFIX_DEVICE_FIELD + bare;

      String twin =
          index.keyIds().contains(hostKey)
              ? hostKey
              : (index.keyIds().contains(deviceKey) ? deviceKey : null);
      if (twin == null) {
        continue;
      }

      int total = corpus.recordCount();
      boolean allMatch = true;
      for (int i = 0; i < total; i++) {
        ImmutableSet<String> dimVals = corpus.valuesForKey(i, dimKey);
        ImmutableSet<String> twinVals = corpus.valuesForKey(i, twin);
        if (dimVals.isEmpty()) {
          continue;
        }
        if (!dimVals.equals(twinVals)) {
          allMatch = false;
          break;
        }
      }
      if (allMatch) {
        out.add(dimKey);
      }
    }
    return out.build();
  }

  private static ImmutableMap<String, Integer> keyDeviceCounts(
      FleetIndex index, Postings postings) {
    ImmutableMap.Builder<String, Integer> counts = ImmutableMap.builder();
    for (String keyId : index.keyIds()) {
      BitSet devices = new BitSet();
      for (int[] posting : postings.forKey(keyId).values()) {
        for (int deviceIndex : posting) {
          devices.set(deviceIndex);
        }
      }
      counts.put(keyId, devices.cardinality());
    }
    return counts.buildOrThrow();
  }

  private static String displayName(DeviceCorpus corpus, String keyId) {
    return corpus
        .getKey(keyId)
        .map(DeviceKeyDisplays::titleDisplayName)
        .orElseGet(
            () -> {
              if (keyId.startsWith(DeviceKeys.PREFIX_DIMENSION)) {
                return "Dimension " + bareName(keyId);
              }
              if (keyId.startsWith(HostKeys.PREFIX_HOST_PROPERTY)) {
                return "Host Property " + bareName(keyId);
              }
              return bareName(keyId);
            });
  }

  private static String norm(String s) {
    return s.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
  }
}
