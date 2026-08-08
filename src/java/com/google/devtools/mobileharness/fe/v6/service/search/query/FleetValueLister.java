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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetCountedNoValueEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetCountedValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetCountedValueList;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPlainValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPlainValueList;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetValueListResponse;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;

/**
 * Builds the value list shown by a simple chip's value picker: every distinct value of one key,
 * with the counts the picker needs to help an operator choose what to switch to.
 *
 * <p>This is the Java port of the search prototype's {@code value_list} plus {@code
 * get_key_values}. It runs entirely over an in-memory {@link FleetSnapshot}. For each distinct
 * value of the key it reports two counts: {@code total}, the fleet-wide device count for that
 * value, and {@code filtered}, the count of devices that both carry the value and satisfy the rest
 * of the current query.
 *
 * <p>The filtered set deliberately drops the requesting key's own chip. The picker is offering the
 * operator alternatives for that one key, so counting must ignore whichever value is already
 * selected, otherwise every alternative would read as zero and only the current value would show a
 * count.
 *
 * <p>Most keys return a {@link FleetCountedValueList} sorted by filtered count descending.
 * High-cardinality identifier keys (device UUID, serial numbers, MAC addresses and the like) return
 * a {@link FleetPlainValueList} with no counts, sorted by value ascending, because a count column
 * over thousands of unique identifiers carries no signal.
 */
public final class FleetValueLister {

  private final FleetFilterEngine filterEngine;

  @Inject
  FleetValueLister(FleetFilterEngine filterEngine) {
    this.filterEngine = filterEngine;
  }

  /**
   * Returns the value list for one key under the current filters.
   *
   * @param snapshot the fleet snapshot to read
   * @param keyId the namespaced key whose values to enumerate (for example {@code field::status})
   * @param filters the current filter chips; the chip on {@code keyId} is excluded from the
   *     filtered counts so the picker offers alternatives to the current selection
   */
  public FleetValueListResponse listValues(
      FleetSnapshot snapshot, String keyId, List<Filter> filters) {
    return listValues(snapshot, keyId, filters, new LazyPostings(snapshot.devices()));
  }

  /**
   * Returns the value list for one key under the current filters.
   *
   * @param snapshot the fleet snapshot to read
   * @param keyId the namespaced key whose values to enumerate (for example {@code field::status})
   * @param filters the current filter chips; the chip on {@code keyId} is excluded from the
   *     filtered counts so the picker offers alternatives to the current selection
   * @param postings the lazy posting lists for intersection counting
   */
  public FleetValueListResponse listValues(
      FleetSnapshot snapshot, String keyId, List<Filter> filters, LazyPostings postings) {
    FleetIndex index = snapshot.index();
    boolean knownKey = index.keyIds().contains(keyId);
    ImmutableList<String> values = index.sortedValues().getOrDefault(keyId, ImmutableList.of());

    // The filtered set drops this key's own chip. With no other filters this is the whole fleet, so
    // every value's filtered count equals its total, which is exactly the prototype's behavior.
    BitSet filteredSet =
        toBitSet(filterEngine.match(snapshot, otherFilters(filters, keyId), postings));

    if (FleetSearchKeys.PLAIN_VALUE_KEYS.contains(keyId)) {
      return FleetValueListResponse.newBuilder().setPlain(buildPlain(index, keyId, values)).build();
    }
    return FleetValueListResponse.newBuilder()
        .setCounted(buildCounted(index, keyId, values, snapshot, filteredSet, knownKey, postings))
        .build();
  }

  private static FleetCountedValueList buildCounted(
      FleetIndex index,
      String keyId,
      ImmutableList<String> values,
      FleetSnapshot snapshot,
      BitSet filteredSet,
      boolean knownKey,
      LazyPostings postings) {
    // Collect in the index's ascending value order so equal filtered counts stay value-ascending
    // after the stable sort below.
    List<FleetCountedValue> entries = new ArrayList<>();
    for (String value : values) {
      String display = displayFor(index, keyId, value);
      entries.add(
          FleetCountedValue.newBuilder()
              .setValue(display)
              .setDisplayLabel(display)
              .setFiltered(intersectionCount(postings.get(keyId, value), filteredSet))
              .setTotal(index.valueCount(keyId, value))
              .build());
    }
    entries.sort(Comparator.comparingInt(FleetCountedValue::getFiltered).reversed());

    FleetCountedValueList.Builder builder =
        FleetCountedValueList.newBuilder().addAllValues(entries);
    if (knownKey) {
      int totalNoValue = noValueTotal(snapshot, postings, keyId);
      if (totalNoValue > 0) {
        builder.setNoValueEntry(
            FleetCountedNoValueEntry.newBuilder()
                .setFiltered(noValueFiltered(postings, keyId, filteredSet))
                .setTotal(totalNoValue));
      }
    }
    return builder.build();
  }

  private static FleetPlainValueList buildPlain(
      FleetIndex index, String keyId, ImmutableList<String> values) {
    // Values are already in ascending order in the index's sorted value list.
    FleetPlainValueList.Builder builder = FleetPlainValueList.newBuilder();
    for (String value : values) {
      String display = displayFor(index, keyId, value);
      builder.addValues(FleetPlainValue.newBuilder().setValue(display).setDisplayLabel(display));
    }
    // For plain value keys, we cannot check noValueTotal without postings. That is acceptable
    // because plain value keys (UUIDs etc.) rarely have missing-value entries.
    return builder.build();
  }

  /** All filters except the one on {@code keyId}. Preserves order. */
  private static ImmutableList<Filter> otherFilters(List<Filter> filters, String keyId) {
    ImmutableList.Builder<Filter> others = ImmutableList.builder();
    for (Filter filter : filters) {
      if (!filter.getKey().equals(keyId)) {
        others.add(filter);
      }
    }
    return others.build();
  }

  /** The value's original-casing display, falling back to the normalized value when absent. */
  private static String displayFor(FleetIndex index, String keyId, String normalizedValue) {
    ImmutableMap<String, String> displays = index.valueDisplays().get(keyId);
    if (displays != null) {
      String display = displays.get(normalizedValue);
      if (display != null) {
        return display;
      }
    }
    return normalizedValue;
  }

  /** Number of devices in {@code posting} that are also in the filtered set. */
  private static int intersectionCount(int[] posting, BitSet filteredSet) {
    int count = 0;
    for (int deviceIndex : posting) {
      if (filteredSet.get(deviceIndex)) {
        count++;
      }
    }
    return count;
  }

  /** Fleet-wide count of devices that lack the key entirely. */
  private static int noValueTotal(FleetSnapshot snapshot, LazyPostings postings, String keyId) {
    return snapshot.deviceCount() - devicesWithKey(postings, keyId).cardinality();
  }

  /** Count of devices in the filtered set that lack the key entirely. */
  private static int noValueFiltered(LazyPostings postings, String keyId, BitSet filteredSet) {
    BitSet lacking = (BitSet) filteredSet.clone();
    lacking.andNot(devicesWithKey(postings, keyId));
    return lacking.cardinality();
  }

  /** Union of every posting list for the key: the devices that carry at least one value for it. */
  private static BitSet devicesWithKey(LazyPostings postings, String keyId) {
    BitSet withKey = new BitSet();
    for (int[] posting : postings.forKey(keyId).values()) {
      for (int deviceIndex : posting) {
        withKey.set(deviceIndex);
      }
    }
    return withKey;
  }

  private static BitSet toBitSet(ImmutableList<Integer> indices) {
    BitSet set = new BitSet();
    for (int deviceIndex : indices) {
      set.set(deviceIndex);
    }
    return set;
  }
}
