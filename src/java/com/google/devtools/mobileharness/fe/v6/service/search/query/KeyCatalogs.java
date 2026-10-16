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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.CoverageInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.CoverageInfo.CoverageNotShown;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.CoverageInfo.CoverageShown;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionCatalogStore;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDescriptor;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Shared key catalog logic across the Configure columns, Add filter, and Add group-by dialogs:
 * section headings, candidate key partitioning and sorting, coverage display decisions, how many
 * entries a long-tail section shows in browse and search mode, and how a typed query matches a key.
 */
final class KeyCatalogs {

  private KeyCatalogs() {}

  static final String SECTION_SUGGESTED = "Suggested";
  static final String SECTION_BUILTIN = "Built-in fields";
  static final String SECTION_HOST_BUILTIN = "Built-in fields from connected host";
  static final String SECTION_DIMENSIONS = "Dimensions";
  static final String SECTION_CONNECTED_HOST_PROPERTIES = "Host properties from connected host";
  static final String SECTION_PROPERTIES = "Host properties";

  private static final CoverageInfo COVERAGE_NOT_SHOWN =
      CoverageInfo.newBuilder().setNotShown(CoverageNotShown.getDefaultInstance()).build();

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
   *   <li><b>builtin</b>: every indexed native device field or config key, sorted alphabetically by
   *       display name.
   *   <li><b>hostBuiltin</b>: every indexed built-in field projected from the connected host,
   *       sorted alphabetically by display name.
   *   <li><b>dimensions</b>: every indexed dimension (from the core index or loaded filter
   *       overlays) merged with every dimension name {@link DimensionCatalogStore} discovered
   *       fleet-wide, ranked by device coverage descending and then display name.
   *   <li><b>properties</b>: every indexed host property projected from the connected host, ranked
   *       by device coverage descending and then display name.
   *   <li><b>deviceCounts</b>: per-key device coverage counts combining the snapshot index with
   *       fleet-wide dimension counts from {@link DimensionCatalogStore}.
   * </ul>
   */
  record DeviceKeyLists(
      ImmutableList<DeviceKeyDescriptor> builtin,
      ImmutableList<DeviceKeyDescriptor> hostBuiltin,
      ImmutableList<DeviceKeyDescriptor> dimensions,
      ImmutableList<DeviceKeyDescriptor> properties,
      ImmutableMap<String, Integer> deviceCounts) {}

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

  /** Partitions and sorts the device corpus keys for all key catalog dialogs. */
  static DeviceKeyLists deviceKeys(
      DeviceCorpus corpus,
      Fleet fleet,
      DimensionCatalogStore dimensionCatalogStore,
      ImmutableMap<String, Integer> indexedCounts) {
    ImmutableMap<String, Integer> catalogCounts = dimensionCatalogStore.getDimensionCounts(fleet);
    ImmutableList.Builder<DeviceKeyDescriptor> catalogDimensions = ImmutableList.builder();
    ImmutableMap<String, Integer> deviceCounts;
    if (catalogCounts.isEmpty()) {
      deviceCounts = indexedCounts;
    } else {
      Map<String, Integer> merged = new HashMap<>(indexedCounts);
      catalogCounts.forEach(
          (name, count) ->
              corpus
                  .deviceKeyRegistry()
                  .dimensionKey(name)
                  .ifPresent(
                      desc -> {
                        catalogDimensions.add(desc);
                        if (count > 0) {
                          merged.putIfAbsent(desc.id(), count);
                        }
                      }));
      deviceCounts = ImmutableMap.copyOf(merged);
    }

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
            .filter(desc -> !desc.isDimension() && !desc.isHostProperty() && !desc.isHostField())
            .sorted(byName)
            .collect(toImmutableList());
    ImmutableList<DeviceKeyDescriptor> hostBuiltin =
        indexed.stream()
            .filter(DeviceKeyDescriptor::isHostField)
            .sorted(byName)
            .collect(toImmutableList());
    ImmutableList<DeviceKeyDescriptor> dimensions =
        ImmutableList.sortedCopyOf(
            byCoverage,
            Streams.concat(
                    indexed.stream().filter(DeviceKeyDescriptor::isDimension),
                    catalogDimensions.build().stream())
                .collect(
                    toImmutableMap(DeviceKeyDescriptor::id, desc -> desc, (first, second) -> first))
                .values());
    ImmutableList<DeviceKeyDescriptor> properties =
        indexed.stream()
            .filter(DeviceKeyDescriptor::isHostProperty)
            .sorted(byCoverage)
            .collect(toImmutableList());

    return new DeviceKeyLists(builtin, hostBuiltin, dimensions, properties, deviceCounts);
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

  /**
   * Builds the {@link CoverageInfo} display decision: shown when {@code showCount} is true and
   * {@code count} is positive (device dimensions on device search, host properties on host search),
   * not shown otherwise.
   */
  static CoverageInfo coverage(boolean showCount, int count) {
    if (showCount && count > 0) {
      return CoverageInfo.newBuilder().setShown(CoverageShown.newBuilder().setCount(count)).build();
    }
    return COVERAGE_NOT_SHOWN;
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
