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

import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.DIM_PREFIX;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.PROP_PREFIX;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogSection;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/**
 * Builds the browsable column catalog for the column selector dialog. This is the Java port of the
 * search prototype's {@code column_catalog} (suggest_engine.py at depot HEAD, CL 959511642, lines
 * 3561 to 3639), adapted to the section-based {@link FleetColumnCatalogResponse}.
 *
 * <p>The response is an ordered list of sections that the frontend renders generically. The backend
 * owns which sections exist, their order, their headings, and their contents. The order is:
 *
 * <ol>
 *   <li>"Suggested for you": keys worth showing because they are in the current filters or were
 *       recently used. Each entry carries a reason. Omitted when there is nothing to suggest.
 *   <li>"Built-in fields": the small, fully browsable set of device fields, host attributes, and
 *       config fields, listed in full and sorted by display name.
 *   <li>"Dimensions": {@code dim::} keys, redundant ones excluded, ranked by device coverage and
 *       cut to a top-N. {@code total_available} carries the full non-redundant dimension count.
 *   <li>"Host properties": {@code prop::} keys, same ranking and top-N pattern.
 *   <li>"Search results": present only when the request carries a query. Keys from any namespace
 *       whose display name or bare key name matches the query, ranked by coverage and cut to a
 *       top-N.
 * </ol>
 *
 * <p>A dimension that merely restates a built-in field (for example {@code dim::host_name} beside
 * {@code host::host_name}) is dropped from every section here, browse and search alike, because
 * offering it as a separate column only asks the user to choose between identical data. Redundancy
 * is derived, not hand-listed: see {@link #redundantDims}. The dimension stays available as a
 * filter key; only the column catalog hides it. The adaptation guide drops the prototype's {@code
 * totalKeys}, {@code redundantHidden}, and {@code totalDevices} counts, so no count of hidden
 * dimensions is reported.
 *
 * <p>{@code device_count} on each entry is how many devices actually carry the key, which is what
 * makes a column worth adding: a dimension present on 12 of 153,201 devices is noise, and the
 * number says so.
 *
 * <p>TODO: add the host entity catalog over the host index, mirroring the prototype's {@code
 * _host_column_catalog}. This class implements the device entity only.
 */
public final class FleetColumnCataloger {

  private static final String SECTION_SUGGESTED = "Suggested for you";
  private static final String SECTION_BUILTIN = "Built-in fields";
  private static final String SECTION_DIMENSIONS = "Dimensions";
  private static final String SECTION_PROPERTIES = "Host properties";
  private static final String SECTION_SEARCH = "Search results";

  private static final String REASON_ACTIVE_FILTER = "in your active filters";
  private static final String REASON_RECENTLY_USED = "recently used";

  /**
   * How many top dimensions to show in browse mode. Ported from the prototype's {@code
   * COLUMN_DIM_TOP} (suggest_engine.py line 3502).
   */
  private static final int DIM_TOP = 10;

  /**
   * How many top host properties to show in browse mode. Ported from the prototype's {@code
   * COLUMN_PROP_TOP} (suggest_engine.py line 3503).
   */
  private static final int PROP_TOP = 5;

  /**
   * How many matches to return for a search query. Ported from the prototype's {@code
   * COLUMN_SEARCH_LIMIT} (suggest_engine.py line 3504).
   */
  private static final int SEARCH_LIMIT = 50;

  @Inject
  FleetColumnCataloger() {}

  /**
   * Returns the ordered column catalog for the current dialog state.
   *
   * @param corpus the corpus to read
   * @param request the dialog state: an optional query, the current filters, and recently used keys
   */
  public FleetColumnCatalogResponse getColumnCatalog(
      SearchCorpus corpus, FleetColumnCatalogRequest request) {
    FleetIndex index = corpus.index();
    ImmutableMap<String, Integer> deviceCounts = keyDeviceCounts(index, corpus.postings());
    ImmutableSet<String> redundant = redundantDims(corpus, index);

    Comparator<String> byCoverage =
        Comparator.<String>comparingInt(keyId -> -deviceCounts.getOrDefault(keyId, 0))
            .thenComparing(keyId -> Ascii.toLowerCase(displayName(index, keyId)));

    // Partition the present keys into the browse buckets, matching the prototype's namespace split:
    // dim:: keys are dimensions (unless redundant), prop:: keys are host properties, and everything
    // else (field::, host::, config::) is a built-in field.
    List<String> builtin = new ArrayList<>();
    List<String> dimensions = new ArrayList<>();
    List<String> properties = new ArrayList<>();
    for (String keyId : index.keyIds()) {
      if (keyId.startsWith(DIM_PREFIX)) {
        if (!redundant.contains(keyId)) {
          dimensions.add(keyId);
        }
      } else if (keyId.startsWith(PROP_PREFIX)) {
        properties.add(keyId);
      } else {
        builtin.add(keyId);
      }
    }
    builtin.sort(Comparator.comparing(keyId -> displayName(index, keyId)));
    dimensions.sort(byCoverage);
    properties.sort(byCoverage);

    FleetColumnCatalogResponse.Builder response = FleetColumnCatalogResponse.newBuilder();
    addSuggestedSection(response, index, deviceCounts, request);
    addFullSection(response, SECTION_BUILTIN, builtin, index, deviceCounts);
    addTopNSection(response, SECTION_DIMENSIONS, dimensions, DIM_TOP, index, deviceCounts);
    addTopNSection(response, SECTION_PROPERTIES, properties, PROP_TOP, index, deviceCounts);
    addSearchSection(response, index, deviceCounts, redundant, byCoverage, request.getQuery());
    return response.build();
  }

  /**
   * Appends the "Suggested for you" section. A key is suggested because it appears in the current
   * filters (the strongest signal that its column belongs on screen: you have narrowed to one pool
   * and cannot see which pool) or because it was recently used. Only keys present in this fleet are
   * offered, each key at most once with its first-seen reason, filters before recents. The section
   * is omitted entirely when there is nothing to suggest, so no empty heading is rendered.
   */
  private static void addSuggestedSection(
      FleetColumnCatalogResponse.Builder response,
      FleetIndex index,
      ImmutableMap<String, Integer> deviceCounts,
      FleetColumnCatalogRequest request) {
    Set<String> seen = new LinkedHashSet<>();
    List<FleetColumnCatalogEntry> entries = new ArrayList<>();
    for (Filter filter : request.getFiltersList()) {
      String keyId = filter.getKey();
      if (index.keyIds().contains(keyId) && seen.add(keyId)) {
        entries.add(entry(keyId, index, deviceCounts, REASON_ACTIVE_FILTER));
      }
    }
    for (String keyId : request.getRecentKeysList()) {
      if (index.keyIds().contains(keyId) && seen.add(keyId)) {
        entries.add(entry(keyId, index, deviceCounts, REASON_RECENTLY_USED));
      }
    }
    if (entries.isEmpty()) {
      return;
    }
    // The suggested set is short and fully shown, so it reports no total: a "showing N of M" line
    // would be noise here.
    response.addSections(
        FleetColumnCatalogSection.newBuilder()
            .setHeading(SECTION_SUGGESTED)
            .addAllEntries(entries));
  }

  /**
   * Appends a section that lists all of its keys, with no truncation and no total. Used for the
   * built-in fields, which are a small browsable set. A section with no keys is omitted so no empty
   * heading is rendered.
   */
  private static void addFullSection(
      FleetColumnCatalogResponse.Builder response,
      String heading,
      List<String> keyIds,
      FleetIndex index,
      ImmutableMap<String, Integer> deviceCounts) {
    if (keyIds.isEmpty()) {
      return;
    }
    FleetColumnCatalogSection.Builder section =
        FleetColumnCatalogSection.newBuilder().setHeading(heading);
    for (String keyId : keyIds) {
      section.addEntries(entry(keyId, index, deviceCounts, ""));
    }
    response.addSections(section);
  }

  /**
   * Appends a section cut to its top-N keys, reporting the full count in {@code total_available} so
   * the frontend can show "showing N of M". The keys arrive already ranked by coverage.
   */
  private static void addTopNSection(
      FleetColumnCatalogResponse.Builder response,
      String heading,
      List<String> rankedKeyIds,
      int topN,
      FleetIndex index,
      ImmutableMap<String, Integer> deviceCounts) {
    if (rankedKeyIds.isEmpty()) {
      return;
    }
    FleetColumnCatalogSection.Builder section =
        FleetColumnCatalogSection.newBuilder()
            .setHeading(heading)
            .setTotalAvailable(rankedKeyIds.size());
    for (String keyId : rankedKeyIds.subList(0, Math.min(topN, rankedKeyIds.size()))) {
      section.addEntries(entry(keyId, index, deviceCounts, ""));
    }
    response.addSections(section);
  }

  /**
   * Appends the "Search results" section, present only when the request carries a query. This is
   * the long-tail retrieval path: the top-N browse lists handle discovery, this handles retrieval
   * of a key the user already has in mind. A key matches when the query, normalized, is a substring
   * of its normalized display name or its normalized bare name. Redundant dimensions are excluded
   * here too. Matches span every namespace, are ranked by coverage, and are cut to a top-N with the
   * full hit count in {@code total_available}.
   *
   * <p>The section is always appended when a query is present, even with zero hits: the frontend
   * uses the empty section to render a "No results found" placeholder.
   */
  private static void addSearchSection(
      FleetColumnCatalogResponse.Builder response,
      FleetIndex index,
      ImmutableMap<String, Integer> deviceCounts,
      ImmutableSet<String> redundant,
      Comparator<String> byCoverage,
      String query) {
    if (query.isEmpty()) {
      return;
    }
    String normalizedQuery = norm(query);
    List<String> hits = new ArrayList<>();
    for (String keyId : index.keyIds()) {
      if (redundant.contains(keyId)) {
        continue;
      }
      if (norm(displayName(index, keyId)).contains(normalizedQuery)
          || norm(bareName(keyId)).contains(normalizedQuery)) {
        hits.add(keyId);
      }
    }
    hits.sort(byCoverage);
    FleetColumnCatalogSection.Builder section =
        FleetColumnCatalogSection.newBuilder()
            .setHeading(SECTION_SEARCH)
            .setTotalAvailable(hits.size());
    for (String keyId : hits.subList(0, Math.min(SEARCH_LIMIT, hits.size()))) {
      section.addEntries(entry(keyId, index, deviceCounts, ""));
    }
    response.addSections(section);
  }

  /**
   * Dimensions that only restate a built-in field. The fleet exposes several dimensions that mirror
   * a device or host field, for example {@code dim::host_name} beside {@code host::host_name}.
   * Ranked by popularity they take the top slots and ask the user to pick between two columns of
   * identical data. This is the Java port of the prototype's {@code _redundant_dims}
   * (suggest_engine.py lines 3506 to 3546).
   *
   * <p>Redundancy is derived, never hand-listed: a name match alone is not enough (a device or host
   * field can be present where the mirroring dimension is absent), so a dimension counts as
   * redundant only when it never disagrees with its twin on any device that carries it. That is
   * precisely "adds nothing", and it re-derives itself as the fleet changes. Values are compared
   * over the normalized per-record value sets that {@link SearchCorpus#valuesForKey} returns,
   * matching the prototype's comparison over its lowercased forward store.
   */
  private static ImmutableSet<String> redundantDims(SearchCorpus corpus, FleetIndex index) {
    // First non-dim key seen for each bare name is the twin candidate.
    Map<String, String> bareToBuiltin = new LinkedHashMap<>();
    for (String keyId : index.keyIds()) {
      if (!keyId.startsWith(DIM_PREFIX)) {
        bareToBuiltin.putIfAbsent(bareName(keyId), keyId);
      }
    }
    // Candidate dim -> its twin, pruned the moment the two disagree on a device that has the dim.
    Map<String, String> live = new LinkedHashMap<>();
    for (String keyId : index.keyIds()) {
      if (keyId.startsWith(DIM_PREFIX)) {
        String twin = bareToBuiltin.get(bareName(keyId));
        if (twin != null) {
          live.put(keyId, twin);
        }
      }
    }
    for (int i = 0; i < corpus.recordCount(); i++) {
      if (live.isEmpty()) {
        break;
      }
      int recordIndex = i;
      live.entrySet()
          .removeIf(
              e -> {
                ImmutableSet<String> dimValues = corpus.valuesForKey(recordIndex, e.getKey());
                return !dimValues.isEmpty()
                    && !dimValues.equals(corpus.valuesForKey(recordIndex, e.getValue()));
              });
    }
    return ImmutableSet.copyOf(live.keySet());
  }

  /**
   * Distinct-device count for every present key: the number of devices that carry at least one
   * value for the key, computed as the union of the key's posting lists. This is the prod analog of
   * the prototype's precomputed {@code key_device_count}.
   */
  private static ImmutableMap<String, Integer> keyDeviceCounts(
      FleetIndex index, LazyPostings postings) {
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

  private static FleetColumnCatalogEntry entry(
      String keyId, FleetIndex index, ImmutableMap<String, Integer> deviceCounts, String reason) {
    return FleetColumnCatalogEntry.newBuilder()
        .setKey(keyId)
        .setDisplayName(displayName(index, keyId))
        .setDeviceCount(deviceCounts.getOrDefault(keyId, 0))
        .setReason(reason)
        .build();
  }

  /** The full key display name, falling back to a namespace-derived name when absent. */
  private static String displayName(FleetIndex index, String keyId) {
    return index.displayNames().getOrDefault(keyId, deriveDisplayName(keyId));
  }

  private static String deriveDisplayName(String keyId) {
    int separator = keyId.indexOf("::");
    String namespace = separator >= 0 ? keyId.substring(0, separator) : "";
    String name = bareName(keyId);
    return switch (namespace) {
      case "dim" -> "Dimension " + name;
      case "prop" -> "Host Property " + name;
      default -> name;
    };
  }

  /** The bare name of a namespaced key: the segment after the last {@code ::}. */
  private static String bareName(String keyId) {
    int separator = keyId.lastIndexOf("::");
    return separator >= 0 ? keyId.substring(separator + 2) : keyId;
  }

  /**
   * Alias normalization matching the prototype's {@code _norm} (suggest_engine.py line 32):
   * lowercase, trimmed, and any run of whitespace or underscores collapsed to a single underscore,
   * so "lab type", "lab type", and "lab__type" all normalize identically.
   */
  private static String norm(String value) {
    return Ascii.toLowerCase(value.strip()).replaceAll("[\\s_]+", "_");
  }
}
