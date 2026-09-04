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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogSection;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.Postings;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;

/**
 * Builds the column catalog for the host entity.
 *
 * <p>Enumerates the host index: built-in {@code host_field::} keys and raw {@code host_property::}
 * properties ranked by distinct host coverage. Dimensions do not exist for hosts and are omitted.
 */
public final class HostColumnCataloger {

  private static final String SECTION_SUGGESTED = "Suggested for you";
  private static final String SECTION_BUILTIN = "Built-in fields";
  private static final String SECTION_PROPERTIES = "Host properties";

  private static final String REASON_ACTIVE_FILTER = "in your active filters";
  private static final String REASON_RECENTLY_USED = "recently used";

  private static final int PROP_TOP = 5;
  private static final int SEARCH_LIMIT = 50;

  @Inject
  HostColumnCataloger() {}

  /**
   * Builds the complete column catalog for the host entity.
   *
   * @param corpus the host projection providing the index and postings
   * @param request the dialog state: an optional query, current filters, and recently used keys
   */
  public FleetColumnCatalogResponse getColumnCatalog(
      HostCorpus corpus, FleetColumnCatalogRequest request) {
    FleetIndex index = corpus.index();
    ImmutableMap<String, Integer> hostCounts = keyHostCounts(index, corpus.postings());

    Comparator<String> byHostCoverage =
        Comparator.<String>comparingInt(keyId -> -hostCounts.getOrDefault(keyId, 0))
            .thenComparing(keyId -> Ascii.toLowerCase(displayName(corpus, keyId)));

    List<String> builtin = new ArrayList<>();
    List<String> properties = new ArrayList<>();
    for (String keyId : index.keyIds()) {
      if (corpus.getKey(keyId).isEmpty() && !keyId.startsWith(HostKeys.PREFIX_HOST_PROPERTY)) {
        continue;
      }
      if (keyId.startsWith(HostKeys.PREFIX_HOST_PROPERTY)) {
        properties.add(keyId);
      } else if (keyId.startsWith(HostKeys.PREFIX_HOST_FIELD)) {
        builtin.add(keyId);
      }
    }

    builtin.sort(Comparator.comparing(keyId -> Ascii.toLowerCase(displayName(corpus, keyId))));
    properties.sort(byHostCoverage);

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
        addFullSection(response, corpus, SECTION_BUILTIN, matchingBuiltin, hostCounts);
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
            response, corpus, SECTION_PROPERTIES, matchingProps, SEARCH_LIMIT, hostCounts);
      }

      return response.build();
    }

    addSuggestedSection(response, corpus, index, hostCounts, request);

    if (!builtin.isEmpty()) {
      addFullSection(response, corpus, SECTION_BUILTIN, builtin, hostCounts);
    }
    if (!properties.isEmpty()) {
      addTopNSection(response, corpus, SECTION_PROPERTIES, properties, PROP_TOP, hostCounts);
    }

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
        entries.add(entry(corpus, keyId, hostCounts.getOrDefault(keyId, 0), REASON_ACTIVE_FILTER));
      }
    }

    for (String keyId : request.getRecentKeysList()) {
      if (presentKeys.contains(keyId)
          && hostCounts.getOrDefault(keyId, 0) > 0
          && added.add(keyId)) {
        entries.add(entry(corpus, keyId, hostCounts.getOrDefault(keyId, 0), REASON_RECENTLY_USED));
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
      HostCorpus corpus,
      String heading,
      List<String> keyIds,
      ImmutableMap<String, Integer> hostCounts) {
    FleetColumnCatalogSection.Builder section =
        FleetColumnCatalogSection.newBuilder().setHeading(heading);
    for (String keyId : keyIds) {
      section.addEntries(entry(corpus, keyId, hostCounts.getOrDefault(keyId, 0), ""));
    }
    response.addSections(section);
  }

  private static void addTopNSection(
      FleetColumnCatalogResponse.Builder response,
      HostCorpus corpus,
      String heading,
      List<String> keyIds,
      int limit,
      ImmutableMap<String, Integer> hostCounts) {
    int total = keyIds.size();
    List<String> top = keyIds.subList(0, Math.min(total, limit));
    FleetColumnCatalogSection.Builder section =
        FleetColumnCatalogSection.newBuilder().setHeading(heading).setTotalAvailable(total);
    for (String keyId : top) {
      section.addEntries(entry(corpus, keyId, hostCounts.getOrDefault(keyId, 0), ""));
    }
    response.addSections(section);
  }

  private static FleetColumnCatalogEntry entry(
      HostCorpus corpus, String keyId, int hostCount, String reason) {
    FleetColumnCatalogEntry.Builder b =
        FleetColumnCatalogEntry.newBuilder()
            .setKey(keyId)
            .setDisplayName(displayName(corpus, keyId))
            .setDeviceCount(hostCount);
    if (!reason.isEmpty()) {
      b.setReason(reason);
    }
    return b.build();
  }

  private static ImmutableMap<String, Integer> keyHostCounts(FleetIndex index, Postings postings) {
    ImmutableMap.Builder<String, Integer> counts = ImmutableMap.builder();
    for (String keyId : index.keyIds()) {
      BitSet hosts = new BitSet();
      for (int[] posting : postings.forKey(keyId).values()) {
        for (int hostIndex : posting) {
          hosts.set(hostIndex);
        }
      }
      counts.put(keyId, hosts.cardinality());
    }
    return counts.buildOrThrow();
  }

  private static String displayName(HostCorpus corpus, String keyId) {
    return corpus
        .getKey(keyId)
        .map(HostKeyDisplays::titleDisplayName)
        .orElseGet(
            () -> {
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
