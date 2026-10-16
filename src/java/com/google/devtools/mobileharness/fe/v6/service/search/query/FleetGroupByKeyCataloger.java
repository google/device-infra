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
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_CONNECTED_HOST_PROPERTIES;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_DIMENSIONS;
import static com.google.devtools.mobileharness.fe.v6.service.search.query.KeyCatalogs.SECTION_HOST_BUILTIN;
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
import java.util.function.Predicate;
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
 *   <li><b>Built-in fields from connected host</b>: Common host fields projected onto devices
 *       (device search only), sorted alphabetically.
 *   <li><b>Dimensions</b>: Device dimensions ranked by device coverage (device search only). Browse
 *       mode lists declared dimensions only; see {@link KeyCatalogs#declared}.
 *   <li><b>Host properties from connected host</b> / <b>Host properties</b>: Host properties ranked
 *       by device or host coverage.
 * </ul>
 *
 * <p>Each entry carries both a {@link GroupCountInfo} display decision (the number of groups the
 * key would produce under the active filters, or {@link GroupCountNotShown} when the backend does
 * not count that section) and a {@link
 * com.google.devtools.mobileharness.fe.v6.service.proto.search.CoverageInfo} display decision
 * (shown for device dimensions on device search and for host properties on host search). With no
 * active filter every indexed section is counted from the per-snapshot key statistics, which costs
 * no scan; under an active filter only Suggested and Built-in sections are counted, one pass over
 * the result set per key, so long-tail sections do not pay for keys the user will scroll past.
 * Single-group keys are still kept (with count 1) so users can add or adjust group-bys freely while
 * editing filters; unapplied counted keys are omitted only when the active filters leave zero
 * records (0 groups), while keys already in {@code request.getGroupByList()} are always kept and
 * marked {@code applied}.
 *
 * <p>In search mode each section keeps only the keys matching the query, best match first (exact
 * name, then prefix, then substring), and the section's own order within one match rank; see {@link
 * KeyCatalogs#searchMatches}.
 */
@Singleton
public final class FleetGroupByKeyCataloger {

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
   * fields, Built-in fields from connected host, Dimensions, and Host properties from connected
   * host partitioned by {@link KeyCatalogs#deviceKeys}.
   */
  private FleetGroupByKeyCatalogResponse getDeviceCatalog(
      DeviceCorpus corpus, FleetGroupByKeyCatalogRequest request) {
    ImmutableSet<String> indexedKeys = corpus.index().keyIds();
    ImmutableMap<String, Integer> indexedCounts = KeyCounts.entityCounts(corpus.index());

    ImmutableList<DeviceKeyDescriptor> suggested =
        Optional.ofNullable(corpus.curation())
            .map(ScenarioCuration::deviceGroupByRow)
            .orElse(ImmutableList.of())
            .stream()
            .filter(desc -> indexedKeys.contains(desc.id()))
            .collect(toImmutableList());
    DeviceKeyLists keys =
        KeyCatalogs.deviceKeys(corpus, request.getFleet(), dimensionCatalogStore, indexedCounts);

    return build(
        corpus,
        request,
        indexedKeys,
        keys.deviceCounts(),
        deviceSections(
            request,
            suggested,
            keys.builtin(),
            keys.hostBuiltin(),
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
    ImmutableMap<String, Integer> hostCounts = KeyCounts.entityCounts(corpus.index());

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
        hostCounts,
        hostSections(request, suggested, keys.builtin(), keys.properties()));
  }

  private static ImmutableList<CandidateSection> deviceSections(
      FleetGroupByKeyCatalogRequest request,
      ImmutableList<DeviceKeyDescriptor> suggested,
      ImmutableList<DeviceKeyDescriptor> builtin,
      ImmutableList<DeviceKeyDescriptor> hostBuiltin,
      ImmutableList<DeviceKeyDescriptor> dimensions,
      ImmutableList<DeviceKeyDescriptor> browseDimensions,
      ImmutableList<DeviceKeyDescriptor> properties) {
    Function<DeviceKeyDescriptor, String> displayName = DeviceKeyDisplays::titleDisplayName;
    Predicate<DeviceKeyDescriptor> showCoverage = DeviceKeyDescriptor::isDimension;
    boolean countLongTail = request.getFiltersList().isEmpty();
    String query = request.getQuery().trim();
    if (query.isEmpty()) {
      return ImmutableList.of(
          candidates(SECTION_SUGGESTED, suggested, 0, true, showCoverage, displayName),
          candidates(SECTION_BUILTIN, builtin, 0, true, showCoverage, displayName),
          candidates(SECTION_HOST_BUILTIN, hostBuiltin, 0, true, showCoverage, displayName),
          candidates(
              SECTION_DIMENSIONS,
              browseDimensions,
              DIM_TOP,
              dimensions.size(),
              countLongTail,
              showCoverage,
              displayName),
          candidates(
              SECTION_CONNECTED_HOST_PROPERTIES,
              properties,
              PROP_TOP,
              countLongTail,
              showCoverage,
              displayName));
    }
    String q = norm(query);
    ToIntFunction<DeviceKeyDescriptor> rank =
        desc -> KeyCatalogs.matchRank(desc, displayName.apply(desc), q);
    return ImmutableList.of(
        candidates(
            SECTION_SUGGESTED,
            KeyCatalogs.searchMatches(suggested, rank),
            0,
            true,
            showCoverage,
            displayName),
        candidates(
            SECTION_BUILTIN,
            KeyCatalogs.searchMatches(builtin, rank),
            0,
            true,
            showCoverage,
            displayName),
        candidates(
            SECTION_HOST_BUILTIN,
            KeyCatalogs.searchMatches(hostBuiltin, rank),
            0,
            true,
            showCoverage,
            displayName),
        candidates(
            SECTION_DIMENSIONS,
            KeyCatalogs.searchMatches(dimensions, rank),
            SEARCH_LIMIT,
            countLongTail,
            showCoverage,
            displayName),
        candidates(
            SECTION_CONNECTED_HOST_PROPERTIES,
            KeyCatalogs.searchMatches(properties, rank),
            SEARCH_LIMIT,
            countLongTail,
            showCoverage,
            displayName));
  }

  private static ImmutableList<CandidateSection> hostSections(
      FleetGroupByKeyCatalogRequest request,
      ImmutableList<HostKeyDescriptor> suggested,
      ImmutableList<HostKeyDescriptor> builtin,
      ImmutableList<HostKeyDescriptor> properties) {
    Function<HostKeyDescriptor, String> displayName = HostKeyDisplays::titleDisplayName;
    Predicate<HostKeyDescriptor> showCoverage = HostKeyDescriptor::isHostProperty;
    boolean countLongTail = request.getFiltersList().isEmpty();
    String query = request.getQuery().trim();
    if (query.isEmpty()) {
      return ImmutableList.of(
          candidates(SECTION_SUGGESTED, suggested, 0, true, showCoverage, displayName),
          candidates(SECTION_BUILTIN, builtin, 0, true, showCoverage, displayName),
          candidates(
              SECTION_PROPERTIES, properties, PROP_TOP, countLongTail, showCoverage, displayName));
    }
    String q = norm(query);
    ToIntFunction<HostKeyDescriptor> rank =
        desc -> KeyCatalogs.matchRank(desc, displayName.apply(desc), q);
    return ImmutableList.of(
        candidates(
            SECTION_SUGGESTED,
            KeyCatalogs.searchMatches(suggested, rank),
            0,
            true,
            showCoverage,
            displayName),
        candidates(
            SECTION_BUILTIN,
            KeyCatalogs.searchMatches(builtin, rank),
            0,
            true,
            showCoverage,
            displayName),
        candidates(
            SECTION_PROPERTIES,
            KeyCatalogs.searchMatches(properties, rank),
            SEARCH_LIMIT,
            countLongTail,
            showCoverage,
            displayName));
  }

  /**
   * Builds a candidate section. A {@code limit} of 0 keeps every key and reports no total; a
   * positive limit caps the shown entries in {@link #build} and reports {@code total_available}.
   */
  private static <T extends KeyDescriptor> CandidateSection candidates(
      String heading,
      ImmutableList<T> descriptors,
      int limit,
      boolean counted,
      Predicate<T> showCoverage,
      Function<T, String> displayName) {
    return candidates(
        heading, descriptors, limit, descriptors.size(), counted, showCoverage, displayName);
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
      Predicate<T> showCoverage,
      Function<T, String> displayName) {
    ImmutableList<CandidateEntry> entries =
        descriptors.stream()
            .map(
                desc ->
                    new CandidateEntry(desc.id(), displayName.apply(desc), showCoverage.test(desc)))
            .collect(toImmutableList());
    return new CandidateSection(heading, entries, limit, total, counted);
  }

  /**
   * Turns the candidate sections into the response: decides each entry's group count and entity
   * coverage, drops unapplied counted keys only when the active filters leave zero groups before
   * applying the section limit, marks applied keys, and omits sections left with no entries so the
   * dialog never renders an empty section heading.
   *
   * <p>An entry is counted only when its section is counted and the snapshot indexes the key. A
   * catalog-only dimension has no values in the snapshot, so it is never counted for groups and
   * never dropped; it is shown with {@link GroupCountNotShown}.
   */
  private FleetGroupByKeyCatalogResponse build(
      SearchCorpus corpus,
      FleetGroupByKeyCatalogRequest request,
      ImmutableSet<String> indexedKeys,
      ImmutableMap<String, Integer> entityCounts,
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
      List<FleetGroupByKeyEntry> kept = new ArrayList<>();
      for (CandidateEntry candidate : section.entries()) {
        String keyId = candidate.keyId();
        boolean applied = appliedKeys.contains(keyId);
        GroupCountInfo countInfo;
        if (section.counted() && indexedKeys.contains(keyId)) {
          int groups =
              groupCounts.computeIfAbsent(keyId, id -> KeyCounts.groupCount(corpus, current, id));
          if (groups == 0 && !applied) {
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
                .setCoverage(
                    KeyCatalogs.coverage(
                        candidate.showCoverage(), entityCounts.getOrDefault(keyId, 0)))
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
        sectionBuilder.setTotalAvailable(section.total());
      }
      response.addSections(sectionBuilder);
    }
    return response.build();
  }

  /** One key offered in a section, before its group count is decided. */
  private record CandidateEntry(String keyId, String displayName, boolean showCoverage) {}

  /**
   * One section before group counts are decided.
   *
   * @param entries the section's candidate keys in rank order
   * @param limit maximum entries to show (0 for unlimited, in which case no total is reported)
   * @param total how many keys the section offers, reported as {@code total_available}. Only a
   *     limited section reports it, and a limited (long-tail) section is counted only without
   *     filters, where every indexed key makes at least one group, so none of its keys is dropped
   * @param counted whether the section's keys get a group count
   */
  private record CandidateSection(
      String heading, List<CandidateEntry> entries, int limit, int total, boolean counted) {}
}
