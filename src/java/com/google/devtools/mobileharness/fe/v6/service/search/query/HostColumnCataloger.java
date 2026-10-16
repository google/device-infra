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

import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.PROP_TOP;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SEARCH_LIMIT;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_BUILTIN;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_PROPERTIES;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.norm;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogSection;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.HostKeyLists;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.ToIntFunction;
import javax.inject.Inject;

/**
 * Builds the column catalog for the host entity.
 *
 * <p>Enumerates the host index: built-in {@code host_field::} keys and raw {@code host_property::}
 * properties ranked by distinct host coverage. Dimensions do not exist for hosts and are omitted.
 */
public final class HostColumnCataloger {

  private static final String SECTION_SUGGESTED = "Suggested for you";

  private static final String REASON_ACTIVE_FILTER = "in your active filters";
  private static final String REASON_RECENTLY_USED = "recently used";

  @Inject
  HostColumnCataloger() {}

  /**
   * Builds the complete column catalog for the host entity.
   *
   * @param corpus the host projection providing the index
   * @param request the dialog state: an optional query, current filters, and recently used keys
   */
  public FleetColumnCatalogResponse getColumnCatalog(
      HostCorpus corpus, FleetColumnCatalogRequest request) {
    FleetIndex index = corpus.index();
    ImmutableMap<String, Integer> hostCounts = KeyCounts.entityCounts(index);
    HostKeyLists keys = KeyCatalogs.hostKeys(corpus, hostCounts);

    FleetColumnCatalogResponse.Builder response = FleetColumnCatalogResponse.newBuilder();
    String query = request.getQuery().trim();
    if (!query.isEmpty()) {
      String q = norm(query);
      ToIntFunction<HostKeyDescriptor> rank =
          desc -> KeyCatalogs.matchRank(desc, HostKeyDisplays.titleDisplayName(desc), q);
      addSection(
          response,
          SECTION_BUILTIN,
          KeyCatalogs.searchMatches(keys.builtin(), rank),
          0,
          hostCounts);
      addSection(
          response,
          SECTION_PROPERTIES,
          KeyCatalogs.searchMatches(keys.properties(), rank),
          SEARCH_LIMIT,
          hostCounts);
      return response.build();
    }

    addSuggestedSection(response, corpus, index, hostCounts, request);
    addSection(response, SECTION_BUILTIN, keys.builtin(), 0, hostCounts);
    addSection(response, SECTION_PROPERTIES, keys.properties(), PROP_TOP, hostCounts);
    return response.build();
  }

  private static void addSuggestedSection(
      FleetColumnCatalogResponse.Builder response,
      HostCorpus corpus,
      FleetIndex index,
      ImmutableMap<String, Integer> hostCounts,
      FleetColumnCatalogRequest request) {
    ImmutableSet<String> presentKeys = index.keyIds();
    LinkedHashSet<String> added = new LinkedHashSet<>();
    ImmutableList.Builder<FleetColumnCatalogEntry> entries = ImmutableList.builder();

    for (Filter filter : request.getFiltersList()) {
      String keyId = filter.getKey();
      if (presentKeys.contains(keyId)
          && hostCounts.getOrDefault(keyId, 0) > 0
          && added.add(keyId)) {
        entries.add(suggestedEntry(corpus, keyId, REASON_ACTIVE_FILTER));
      }
    }

    for (String keyId : request.getRecentKeysList()) {
      if (presentKeys.contains(keyId)
          && hostCounts.getOrDefault(keyId, 0) > 0
          && added.add(keyId)) {
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
      List<HostKeyDescriptor> descriptors,
      int limit,
      ImmutableMap<String, Integer> hostCounts) {
    if (descriptors.isEmpty()) {
      return;
    }
    FleetColumnCatalogSection.Builder section =
        FleetColumnCatalogSection.newBuilder().setHeading(heading);
    if (limit > 0) {
      section.setTotalAvailable(descriptors.size());
    }
    descriptors.stream()
        .limit(limit > 0 ? limit : Long.MAX_VALUE)
        .map(desc -> entry(desc, hostCounts.getOrDefault(desc.id(), 0)))
        .forEach(section::addEntries);
    response.addSections(section);
  }

  private static FleetColumnCatalogEntry entry(HostKeyDescriptor desc, int hostCount) {
    return FleetColumnCatalogEntry.newBuilder()
        .setKey(desc.id())
        .setDisplayName(HostKeyDisplays.titleDisplayName(desc))
        .setCoverage(KeyCatalogs.coverage(desc.isHostProperty(), hostCount))
        .build();
  }

  private static FleetColumnCatalogEntry suggestedEntry(
      HostCorpus corpus, String keyId, String reason) {
    String displayName = corpus.getKey(keyId).map(HostKeyDisplays::titleDisplayName).orElse(keyId);
    return FleetColumnCatalogEntry.newBuilder()
        .setKey(keyId)
        .setDisplayName(displayName)
        .setCoverage(KeyCatalogs.coverage(false, 0))
        .setReason(reason)
        .build();
  }
}
