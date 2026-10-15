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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionCatalogStore;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDescriptor;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * What the Add filter and Add group-by dialogs have in common: section headings, candidate key
 * partitioning and sorting, how many entries a long-tail section shows in browse and search mode,
 * and how a typed query matches a key.
 */
final class KeyCatalogs {

  private KeyCatalogs() {}

  static final String SECTION_SUGGESTED = "Suggested";
  static final String SECTION_BUILTIN = "Built-in fields";
  static final String SECTION_DIMENSIONS = "Dimensions";
  static final String SECTION_PROPERTIES = "Host properties";

  /**
   * Dimensions shown in browse mode: the most common ones, by device coverage. Deliberately short:
   * nobody browses thousands of dimensions, and a user after a specific one types its name, which
   * search ranks first (see {@link #searchMatches}).
   */
  static final int DIM_TOP = 10;

  /**
   * The dimensions declared in this deployment's key catalog, in the given order.
   *
   * <p>Browse mode lists only these. A long-tail dimension is any name devices report, including
   * typos and case variants (a device reporting {@code Model} passes master's case-insensitive mask
   * for {@code model} and lands in the snapshot as its own key), so ranking long-tail names by
   * coverage would fill the short browse list with noise. Search still reaches every dimension.
   */
  static ImmutableList<DeviceKeyDescriptor> declared(List<DeviceKeyDescriptor> dimensions) {
    return dimensions.stream().filter(desc -> !desc.isLongTail()).collect(toImmutableList());
  }

  /** Host properties shown in browse mode: the most common ones, by coverage. */
  static final int PROP_TOP = 5;

  /** Entries shown per long-tail section in search mode. */
  static final int SEARCH_LIMIT = 50;

  /**
   * Partitioned and sorted candidate keys for device search:
   *
   * <ul>
   *   <li><b>builtin</b>: every indexed key that is neither a dimension nor a host property, sorted
   *       alphabetically by display name.
   *   <li><b>dimensions</b>: every indexed dimension (from the core index or loaded filter
   *       overlays) merged with every dimension name {@link DimensionCatalogStore} discovered
   *       fleet-wide, ranked by device coverage descending and then display name.
   *   <li><b>properties</b>: every indexed host property, ranked by device coverage descending and
   *       then display name.
   * </ul>
   */
  record DeviceKeyLists(
      ImmutableList<DeviceKeyDescriptor> builtin,
      ImmutableList<DeviceKeyDescriptor> dimensions,
      ImmutableList<DeviceKeyDescriptor> properties) {}

  /**
   * Partitioned and sorted candidate keys for host search:
   *
   * <ul>
   *   <li><b>builtin</b>: every indexed native host field, sorted alphabetically by display name.
   *   <li><b>properties</b>: every indexed host property, ranked by host coverage descending and
   *       then display name.
   * </ul>
   */
  record HostKeyLists(
      ImmutableList<HostKeyDescriptor> builtin, ImmutableList<HostKeyDescriptor> properties) {}

  /** Partitions and sorts the device corpus keys for both key catalog dialogs. */
  static DeviceKeyLists deviceKeys(
      DeviceCorpus corpus,
      Fleet fleet,
      DimensionCatalogStore dimensionCatalogStore,
      ImmutableMap<String, Integer> deviceCounts) {
    Comparator<DeviceKeyDescriptor> byName =
        Comparator.comparing(desc -> Ascii.toLowerCase(DeviceKeyDisplays.titleDisplayName(desc)));
    Comparator<DeviceKeyDescriptor> byCoverage =
        Comparator.comparing(
                (DeviceKeyDescriptor desc) -> deviceCounts.getOrDefault(desc.id(), 0),
                Comparator.reverseOrder())
            .thenComparing(byName);

    ImmutableList<DeviceKeyDescriptor> indexed =
        corpus.index().keyIds().stream()
            .map(corpus::getKey)
            .flatMap(Optional::stream)
            .collect(toImmutableList());

    ImmutableList<DeviceKeyDescriptor> builtin =
        indexed.stream()
            .filter(desc -> !desc.isDimension() && !desc.isHostProperty())
            .sorted(byName)
            .collect(toImmutableList());
    // Dimensions discovered fleet-wide but not indexed join the indexed ones; they carry coverage 0
    // here because their values are not in the snapshot. The indexed descriptor wins when both
    // name the same key.
    ImmutableList<DeviceKeyDescriptor> dimensions =
        ImmutableList.sortedCopyOf(
            byCoverage,
            Streams.concat(
                    indexed.stream().filter(DeviceKeyDescriptor::isDimension),
                    dimensionCatalogStore.getDimensionNames(fleet).stream()
                        .map(corpus.deviceKeyRegistry()::dimensionKey)
                        .flatMap(Optional::stream))
                .collect(
                    toImmutableMap(DeviceKeyDescriptor::id, desc -> desc, (first, second) -> first))
                .values());
    ImmutableList<DeviceKeyDescriptor> properties =
        indexed.stream()
            .filter(DeviceKeyDescriptor::isHostProperty)
            .sorted(byCoverage)
            .collect(toImmutableList());

    return new DeviceKeyLists(builtin, dimensions, properties);
  }

  /** Partitions and sorts the host corpus keys for both key catalog dialogs. */
  static HostKeyLists hostKeys(HostCorpus corpus, ImmutableMap<String, Integer> hostCounts) {
    Comparator<HostKeyDescriptor> byName =
        Comparator.comparing(desc -> Ascii.toLowerCase(HostKeyDisplays.titleDisplayName(desc)));
    Comparator<HostKeyDescriptor> byCoverage =
        Comparator.comparing(
                (HostKeyDescriptor desc) -> hostCounts.getOrDefault(desc.id(), 0),
                Comparator.reverseOrder())
            .thenComparing(byName);

    ImmutableList<HostKeyDescriptor> indexed =
        corpus.index().keyIds().stream()
            .map(corpus::getKey)
            .flatMap(Optional::stream)
            .collect(toImmutableList());
    ImmutableList<HostKeyDescriptor> builtin =
        indexed.stream()
            .filter(HostKeyDescriptor::isHostField)
            .sorted(byName)
            .collect(toImmutableList());
    ImmutableList<HostKeyDescriptor> properties =
        indexed.stream()
            .filter(HostKeyDescriptor::isHostProperty)
            .sorted(byCoverage)
            .collect(toImmutableList());

    return new HostKeyLists(builtin, properties);
  }

  /** Lowercased, underscores read as spaces, so "lab_location" and "Lab Location" compare equal. */
  static String norm(String s) {
    return s.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
  }

  /** Rank of a key that the query does not match at all. */
  static final int NO_MATCH = 3;

  /**
   * How well a normalized query matches a key, by display name or bare name: 0 for an exact name, 1
   * for a name that starts with the query, 2 for a name that contains it, {@link #NO_MATCH}
   * otherwise.
   */
  static int matchRank(KeyDescriptor desc, String displayName, String normalizedQuery) {
    String display = norm(displayName);
    String bare = norm(desc.bareName());
    if (display.equals(normalizedQuery) || bare.equals(normalizedQuery)) {
      return 0;
    }
    if (display.startsWith(normalizedQuery) || bare.startsWith(normalizedQuery)) {
      return 1;
    }
    if (display.contains(normalizedQuery) || bare.contains(normalizedQuery)) {
      return 2;
    }
    return NO_MATCH;
  }

  /**
   * The keys of one section that match the query, best match first.
   *
   * <p>What the user typed outranks how common a key is: a key named exactly as the query leads
   * even when its coverage is low or unknown, so a search for a rare key is never truncated away
   * behind common keys that merely contain the query. Within one match rank the section's own order
   * (coverage, then name) is kept, because the sort is stable.
   */
  static <T> ImmutableList<T> searchMatches(List<T> keys, ToIntFunction<T> matchRank) {
    return keys.stream()
        .filter(key -> matchRank.applyAsInt(key) < NO_MATCH)
        .sorted(Comparator.comparingInt(matchRank))
        .collect(toImmutableList());
  }
}
