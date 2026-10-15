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
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.DIM_TOP;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.PROP_TOP;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SEARCH_LIMIT;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_BUILTIN;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_DIMENSIONS;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_PROPERTIES;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_SUGGESTED;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.norm;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupByKeyCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupByKeyCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupByKeyEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupByKeySection;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.GroupCountInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.GroupCountInfo.GroupCountNotShown;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.GroupCountInfo.GroupCountShown;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.DeviceKeyLists;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.HostKeyLists;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionCatalogStore;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Builds the group-by-key catalog for the "Add a group-by" dialog across device and host search.
 *
 * <p>Categorizes pickable group-by keys into ordered sections:
 *
 * <ul>
 *   <li><b>Suggested</b>: The scenario's curated quick group-by keys in display order.
 *   <li><b>Built-in fields</b>: Standard device or host attributes sorted alphabetically.
 *   <li><b>Dimensions</b>: Device dimensions ranked by device coverage (device search only). Browse
 *       mode lists declared dimensions only; see {@link KeyCatalogs#declared}.
 *   <li><b>Host properties</b>: Host properties ranked by device or host coverage.
 * </ul>
 *
 * <p>Each entry carries a {@link GroupCountInfo} display decision: the number of groups the key
 * would produce under the active filters (distinct value combinations plus one for the no-value
 * bucket when some entities lack the key), or {@link GroupCountNotShown} when the backend does not
 * count that section. With no active filter every indexed section is counted from the per-snapshot
 * key statistics, which costs no scan; under an active filter only Suggested and Built-in fields
 * are counted, one pass over the result set per key, so long-tail sections do not pay for keys the
 * user will scroll past. Unapplied counted keys that would produce fewer than two groups are
 * omitted before slicing to the section limit, while counted keys already in {@code
 * request.getGroupByList()} are kept even when they produce a single group and marked {@code
 * applied}.
 *
 * <p>In search mode each section keeps only the keys matching the query, best match first (exact
 * name, then prefix, then substring), and the section's own order within one match rank; see {@link
 * KeyCatalogs#searchMatches}.
 */
@Singleton
public final class FleetGroupByKeyCataloger {

  /**
   * A grouping needs at least two groups to be worth offering: one group is the ungrouped list
   * again. Same threshold as the prototype's {@code GROUP_SUGGEST_MIN}.
   */
  private static final int MIN_GROUP_COUNT = 2;

  private final FleetFilterEngine filterEngine;
  private final DimensionCatalogStore dimensionCatalogStore;

  @Inject
  FleetGroupByKeyCataloger(
      FleetFilterEngine filterEngine, DimensionCatalogStore dimensionCatalogStore) {
    this.filterEngine = filterEngine;
    this.dimensionCatalogStore = dimensionCatalogStore;
  }

  /** Builds its own filter engine around the given catalog store; for tests and one-off use. */
  public FleetGroupByKeyCataloger(DimensionCatalogStore dimensionCatalogStore) {
    this(new FleetFilterEngine(), dimensionCatalogStore);
  }

  /** Builds its own collaborators with an empty catalog store; for tests and one-off use. */
  public FleetGroupByKeyCataloger() {
    this(new FleetFilterEngine(), new DimensionCatalogStore());
  }

  /** Returns the ordered group-by-key catalog for the requested entity and dialog state. */
  public FleetGroupByKeyCatalogResponse getGroupByKeyCatalog(
      SearchCorpus corpus, FleetGroupByKeyCatalogRequest request) {
    if (corpus.entity() == SearchEntity.SEARCH_ENTITY_HOST) {
      return getHostCatalog((HostCorpus) corpus, request);
    }
    return getDeviceCatalog((DeviceCorpus) corpus, request);
  }

  /**
   * Device search: Suggested (curated Quick Group-by keys present in the index), plus Built-in
   * fields, Dimensions, and Host properties partitioned by {@link KeyCatalogs#deviceKeys}.
   */
  private FleetGroupByKeyCatalogResponse getDeviceCatalog(
      DeviceCorpus corpus, FleetGroupByKeyCatalogRequest request) {
    ImmutableSet<String> indexedKeys = corpus.index().keyIds();
    ImmutableMap<String, Integer> deviceCounts =
        KeyCounts.entityCounts(corpus.index(), corpus.postings());

    ImmutableList<DeviceKeyDescriptor> suggested =
        Optional.ofNullable(corpus.curation())
            .map(ScenarioCuration::deviceGroupByRow)
            .orElse(ImmutableList.of())
            .stream()
            .filter(desc -> indexedKeys.contains(desc.id()))
            .collect(toImmutableList());
    DeviceKeyLists keys =
        KeyCatalogs.deviceKeys(corpus, request.getFleet(), dimensionCatalogStore, deviceCounts);

    return build(
        corpus,
        request,
        indexedKeys,
        sections(
            request,
            DeviceKeyDisplays::titleDisplayName,
            suggested,
            keys.builtin(),
            keys.dimensions(),
            KeyCatalogs.declared(keys.dimensions()),
            keys.properties()));
  }

  /**
   * Host search: Suggested, Built-in fields, and Host properties partitioned by {@link
   * KeyCatalogs#hostKeys}, with no Dimensions section.
   */
  private FleetGroupByKeyCatalogResponse getHostCatalog(
      HostCorpus corpus, FleetGroupByKeyCatalogRequest request) {
    ImmutableSet<String> indexedKeys = corpus.index().keyIds();
    ImmutableMap<String, Integer> hostCounts =
        KeyCounts.entityCounts(corpus.index(), corpus.postings());

    ImmutableList<HostKeyDescriptor> suggested =
        Optional.ofNullable(corpus.curation())
            .map(ScenarioCuration::hostGroupByRow)
            .orElse(ImmutableList.of())
            .stream()
            .filter(desc -> indexedKeys.contains(desc.id()))
            .collect(toImmutableList());
    HostKeyLists keys = KeyCatalogs.hostKeys(corpus, hostCounts);

    return build(
        corpus,
        request,
        indexedKeys,
        sections(
            request,
            HostKeyDisplays::titleDisplayName,
            suggested,
            keys.builtin(),
            ImmutableList.of(),
            ImmutableList.of(),
            keys.properties()));
  }

  /**
   * The four candidate sections in order.
   *
   * <p>Browse mode (empty query) lists Suggested and Built-in fields in full, the top {@link
   * KeyCatalogs#DIM_TOP} declared dimensions ({@code browseDimensions}, see {@link
   * KeyCatalogs#declared}) with a total that counts every dimension, and the top {@link
   * KeyCatalogs#PROP_TOP} host properties. Search mode keeps the matching keys of every section,
   * best match first, and up to {@link KeyCatalogs#SEARCH_LIMIT} per long-tail section.
   *
   * <p>Suggested and Built-in fields are always counted. Dimensions and Host properties are counted
   * only when no filter is active: then every count comes from the per-snapshot key statistics at
   * no cost, while under a filter each count is a pass over the result set.
   */
  private static <T extends KeyDescriptor> ImmutableList<CandidateSection> sections(
      FleetGroupByKeyCatalogRequest request,
      Function<T, String> displayName,
      ImmutableList<T> suggested,
      ImmutableList<T> builtin,
      ImmutableList<T> dimensions,
      ImmutableList<T> browseDimensions,
      ImmutableList<T> properties) {
    boolean countLongTail = request.getFiltersList().isEmpty();
    String query = request.getQuery().trim();
    if (query.isEmpty()) {
      return ImmutableList.of(
          candidates(SECTION_SUGGESTED, suggested, 0, true, displayName),
          candidates(SECTION_BUILTIN, builtin, 0, true, displayName),
          candidates(
              SECTION_DIMENSIONS,
              browseDimensions,
              DIM_TOP,
              dimensions.size(),
              countLongTail,
              displayName),
          candidates(SECTION_PROPERTIES, properties, PROP_TOP, countLongTail, displayName));
    }
    String q = norm(query);
    ToIntFunction<T> rank = desc -> KeyCatalogs.matchRank(desc, displayName.apply(desc), q);
    return ImmutableList.of(
        candidates(
            SECTION_SUGGESTED, KeyCatalogs.searchMatches(suggested, rank), 0, true, displayName),
        candidates(SECTION_BUILTIN, KeyCatalogs.searchMatches(builtin, rank), 0, true, displayName),
        candidates(
            SECTION_DIMENSIONS,
            KeyCatalogs.searchMatches(dimensions, rank),
            SEARCH_LIMIT,
            countLongTail,
            displayName),
        candidates(
            SECTION_PROPERTIES,
            KeyCatalogs.searchMatches(properties, rank),
            SEARCH_LIMIT,
            countLongTail,
            displayName));
  }

  /**
   * Builds a candidate section. A {@code limit} of 0 keeps every key and reports no total; a
   * positive limit caps the shown entries in {@link #build} after single-group keys are dropped and
   * reports {@code total_available}.
   */
  private static <T extends KeyDescriptor> CandidateSection candidates(
      String heading,
      ImmutableList<T> descriptors,
      int limit,
      boolean counted,
      Function<T, String> displayName) {
    return candidates(heading, descriptors, limit, descriptors.size(), counted, displayName);
  }

  /**
   * Same as above, with an explicit total for a section that lists fewer keys than it offers (the
   * browse Dimensions section).
   */
  private static <T extends KeyDescriptor> CandidateSection candidates(
      String heading,
      ImmutableList<T> descriptors,
      int limit,
      int total,
      boolean counted,
      Function<T, String> displayName) {
    ImmutableList<CandidateEntry> entries =
        descriptors.stream()
            .map(desc -> new CandidateEntry(desc.id(), displayName.apply(desc)))
            .collect(toImmutableList());
    return new CandidateSection(heading, entries, limit, total, counted);
  }

  /**
   * Turns the candidate sections into the response: decides each entry's group count, drops
   * unapplied counted keys that would make fewer than {@link #MIN_GROUP_COUNT} groups before
   * applying the section limit, marks applied keys, and omits sections left with no entries so the
   * dialog never renders an empty section heading.
   *
   * <p>An entry is counted only when its section is counted and the snapshot indexes the key. A
   * catalog-only dimension has no values in the snapshot, so it is never counted and never dropped;
   * it is shown with {@link GroupCountNotShown}. In browse mode, the Dimensions section only
   * iterates declared dimensions while {@code rawTotal} counts every dimension, so {@code
   * total_available} subtracts only single-group declared dimensions (most undeclared dimensions
   * are catalog-only names whose group count is unknown anyway).
   */
  private FleetGroupByKeyCatalogResponse build(
      SearchCorpus corpus,
      FleetGroupByKeyCatalogRequest request,
      ImmutableSet<String> indexedKeys,
      ImmutableList<CandidateSection> candidateSections) {
    ImmutableSet<String> appliedKeys = ImmutableSet.copyOf(request.getGroupByList());
    // The filtered result set is materialized only when a filter is active; with none, the
    // per-snapshot key statistics answer for the whole corpus.
    Optional<ImmutableList<Integer>> current =
        request.getFiltersList().isEmpty()
            ? Optional.empty()
            : Optional.of(filterEngine.match(corpus, request.getFiltersList()));

    Map<String, Integer> groupCounts = new HashMap<>();
    FleetGroupByKeyCatalogResponse.Builder response = FleetGroupByKeyCatalogResponse.newBuilder();
    for (CandidateSection section : candidateSections) {
      int dropped = 0;
      List<FleetGroupByKeyEntry> kept = new ArrayList<>();
      for (CandidateEntry candidate : section.entries()) {
        String keyId = candidate.keyId();
        boolean applied = appliedKeys.contains(keyId);
        GroupCountInfo countInfo;
        if (section.counted() && indexedKeys.contains(keyId)) {
          int groups =
              groupCounts.computeIfAbsent(keyId, id -> KeyCounts.groupCount(corpus, current, id));
          if (groups < MIN_GROUP_COUNT && !applied) {
            dropped++;
            continue;
          }
          countInfo =
              GroupCountInfo.newBuilder()
                  .setShown(GroupCountShown.newBuilder().setCount(groups))
                  .build();
        } else {
          countInfo =
              GroupCountInfo.newBuilder()
                  .setNotShown(GroupCountNotShown.getDefaultInstance())
                  .build();
        }
        kept.add(
            FleetGroupByKeyEntry.newBuilder()
                .setKey(keyId)
                .setDisplayName(candidate.displayName())
                .setGroupCount(countInfo)
                .setApplied(applied)
                .build());
      }
      if (kept.isEmpty()) {
        continue;
      }
      FleetGroupByKeySection.Builder sectionBuilder =
          FleetGroupByKeySection.newBuilder().setHeading(section.heading());
      kept.stream()
          .limit(section.limit() > 0 ? section.limit() : Long.MAX_VALUE)
          .forEach(sectionBuilder::addEntries);
      if (section.limit() > 0) {
        sectionBuilder.setTotalAvailable(section.rawTotal() - dropped);
      }
      response.addSections(sectionBuilder);
    }
    return response.build();
  }

  /** One key offered in a section, before its group count is decided. */
  private record CandidateEntry(String keyId, String displayName) {}

  /**
   * One section before group counts are decided.
   *
   * @param entries the section's candidate keys in rank order
   * @param limit maximum entries to show (0 for unlimited, in which case no total is reported)
   * @param rawTotal how many keys the section has before dropping single-group keys
   * @param counted whether the section's keys get a group count
   */
  private record CandidateSection(
      String heading, List<CandidateEntry> entries, int limit, int rawTotal, boolean counted) {}
}
