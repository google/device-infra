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

package com.google.devtools.mobileharness.fe.v6.service.search.index;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.DimensionOverlayRaw;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.DimensionOverlayRaw.DeviceKey;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory index and posting cache for one on-demand long-tail dimension.
 *
 * <p>Holds the snapshot-independent ground truth ({@link #deviceValues}, {@link #sortedValues},
 * {@link #valueDisplays}) derived purely from the pulled dimension data.
 *
 * <p>Posting lists ({@code value -> int[] deviceIndex}), per-index device values, value counts, and
 * key statistics are bound to a specific snapshot's device ordering via {@link
 * #bind(FleetSnapshot)}. When a snapshot rotates, they are re-indexed against {@link
 * FleetSnapshot#uuidToIndex()}.
 */
public final class DimensionOverlay {

  private final String keyId;
  private final ImmutableMap<DeviceKey, ImmutableList<String>> deviceValues;
  private final ImmutableList<String> sortedValues;
  private final ImmutableMap<String, String> valueDisplays;

  private volatile SnapshotBoundPostings boundPostings;

  private DimensionOverlay(
      String keyId,
      ImmutableMap<DeviceKey, ImmutableList<String>> deviceValues,
      ImmutableList<String> sortedValues,
      ImmutableMap<String, String> valueDisplays,
      SnapshotBoundPostings boundPostings) {
    this.keyId = checkNotNull(keyId);
    this.deviceValues = checkNotNull(deviceValues);
    this.sortedValues = checkNotNull(sortedValues);
    this.valueDisplays = checkNotNull(valueDisplays);
    this.boundPostings = checkNotNull(boundPostings);
  }

  public String keyId() {
    return keyId;
  }

  public ImmutableMap<DeviceKey, ImmutableList<String>> deviceValues() {
    return deviceValues;
  }

  public ImmutableList<String> sortedValues() {
    return sortedValues;
  }

  public ImmutableMap<String, String> valueDisplays() {
    return valueDisplays;
  }

  /**
   * Returns posting lists, per-index values, value counts, and key statistics aligned with the
   * given snapshot. Re-indexes if the snapshot has rotated since the last bind.
   */
  @CanIgnoreReturnValue
  public SnapshotBoundPostings bind(FleetSnapshot snapshot) {
    SnapshotBoundPostings current = boundPostings;
    if (current.buildTime().equals(snapshot.buildTime())) {
      return current;
    }
    SnapshotBoundPostings updated = reindex(snapshot);
    this.boundPostings = updated;
    return updated;
  }

  // Safe: every slot of displayValuesByIndex is assigned only an ImmutableList<String>.
  @SuppressWarnings({"unchecked", "rawtypes"})
  private SnapshotBoundPostings reindex(FleetSnapshot snapshot) {
    ImmutableMap<DeviceKey, Integer> uuidToIndex = snapshot.uuidToIndex();
    Map<String, List<Integer>> postingsMap = new HashMap<>();
    int maxDefinedIndex = snapshot.deviceCount();
    for (Integer idx : uuidToIndex.values()) {
      if (idx >= maxDefinedIndex) {
        maxDefinedIndex = idx + 1;
      }
    }
    ImmutableList<String>[] displayValuesByIndex = new ImmutableList[maxDefinedIndex];
    int entityCount = 0;
    Set<String> singleCombinations = new HashSet<>();
    Set<ImmutableSet<String>> multiCombinations = new HashSet<>();
    Set<DeviceKey> uniqueFallbackKeys = uniqueUniverseDeviceKeys(deviceValues.keySet());

    for (Map.Entry<DeviceKey, ImmutableList<String>> entry : deviceValues.entrySet()) {
      Integer deviceIndex = resolveDeviceIndex(uuidToIndex, entry.getKey(), uniqueFallbackKeys);
      if (deviceIndex == null
          || deviceIndex < 0
          || deviceIndex >= maxDefinedIndex
          || displayValuesByIndex[deviceIndex] != null) {
        continue;
      }
      Set<String> seenValues = new HashSet<>();
      for (String rawVal : entry.getValue()) {
        String valLower = Ascii.toLowerCase(rawVal);
        if (!valLower.isEmpty() && seenValues.add(valLower)) {
          postingsMap.computeIfAbsent(valLower, k -> new ArrayList<>()).add(deviceIndex);
        }
      }
      if (!seenValues.isEmpty()) {
        displayValuesByIndex[deviceIndex] = entry.getValue();
        entityCount++;
        if (seenValues.size() == 1) {
          singleCombinations.add(seenValues.iterator().next());
        } else {
          multiCombinations.add(ImmutableSet.copyOf(seenValues));
        }
      }
    }

    ImmutableMap.Builder<String, int[]> frozen = ImmutableMap.builder();
    ImmutableMap.Builder<String, Integer> boundCounts = ImmutableMap.builder();
    for (Map.Entry<String, List<Integer>> entry : postingsMap.entrySet()) {
      List<Integer> list = entry.getValue();
      Collections.sort(list);
      frozen.put(entry.getKey(), Ints.toArray(list));
      boundCounts.put(entry.getKey(), list.size());
    }
    KeyStats stats =
        new KeyStats(
            entityCount,
            singleCombinations.size() + multiCombinations.size(),
            !multiCombinations.isEmpty());
    return new SnapshotBoundPostings(
        snapshot.buildTime(),
        frozen.buildOrThrow(),
        boundCounts.buildOrThrow(),
        displayValuesByIndex,
        stats);
  }

  private static Set<DeviceKey> uniqueUniverseDeviceKeys(Set<DeviceKey> keys) {
    Set<DeviceKey> unique = new HashSet<>();
    Set<DeviceKey> duplicates = new HashSet<>();
    for (DeviceKey key : keys) {
      DeviceKey fallbackKey = new DeviceKey(key.universe(), "", key.deviceId());
      if (!unique.add(fallbackKey)) {
        duplicates.add(fallbackKey);
      }
    }
    unique.removeAll(duplicates);
    return unique;
  }

  private static Integer resolveDeviceIndex(
      ImmutableMap<DeviceKey, Integer> uuidToIndex,
      DeviceKey key,
      Set<DeviceKey> uniqueFallbackKeys) {
    Integer exact = uuidToIndex.get(key);
    if (exact != null) {
      return exact;
    }
    DeviceKey fallbackKey = new DeviceKey(key.universe(), "", key.deviceId());
    return uniqueFallbackKeys.contains(fallbackKey) ? uuidToIndex.get(fallbackKey) : null;
  }

  /** Builds a {@link DimensionOverlay} from raw pulled data and initial snapshot. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static DimensionOverlay create(DimensionOverlayRaw raw, FleetSnapshot snapshot) {
    String keyId = raw.keyId();
    ImmutableMap<DeviceKey, ImmutableList<String>> deviceValues = raw.deviceValues();

    Map<String, String> valueDisplays = new HashMap<>();
    Set<String> distinctValues = new HashSet<>();

    for (ImmutableList<String> values : deviceValues.values()) {
      Set<String> seenForDevice = new HashSet<>();
      for (String rawVal : values) {
        String valLower = Ascii.toLowerCase(rawVal);
        if (!valLower.isEmpty() && seenForDevice.add(valLower)) {
          distinctValues.add(valLower);
          valueDisplays.putIfAbsent(valLower, rawVal);
        }
      }
    }

    List<String> sortedList = new ArrayList<>(distinctValues);
    Collections.sort(sortedList);

    DimensionOverlay overlay =
        new DimensionOverlay(
            keyId,
            deviceValues,
            ImmutableList.copyOf(sortedList),
            ImmutableMap.copyOf(valueDisplays),
            new SnapshotBoundPostings(
                Instant.MIN,
                ImmutableMap.of(),
                ImmutableMap.of(),
                new ImmutableList[0],
                KeyStats.EMPTY));

    // Bind to the given snapshot immediately.
    overlay.bind(snapshot);
    return overlay;
  }

  /**
   * Posting lists, per-index values, value counts, and key statistics bound to a specific snapshot
   * build time.
   */
  public static final class SnapshotBoundPostings {
    private final Instant buildTime;
    private final ImmutableMap<String, int[]> postings;
    private final ImmutableMap<String, Integer> valueCounts;
    private final ImmutableList<String>[] displayValuesByIndex;
    private final KeyStats stats;

    SnapshotBoundPostings(
        Instant buildTime,
        ImmutableMap<String, int[]> postings,
        ImmutableMap<String, Integer> valueCounts,
        ImmutableList<String>[] displayValuesByIndex,
        KeyStats stats) {
      this.buildTime = buildTime;
      this.postings = postings;
      this.valueCounts = valueCounts;
      this.displayValuesByIndex = displayValuesByIndex;
      this.stats = stats;
    }

    public Instant buildTime() {
      return buildTime;
    }

    public int[] get(String valueLower) {
      int[] posting = postings.get(valueLower);
      return posting != null ? posting : EMPTY_POSTING;
    }

    public ImmutableMap<String, int[]> postings() {
      return postings;
    }

    public ImmutableMap<String, Integer> valueCounts() {
      return valueCounts;
    }

    public ImmutableList<String> displayValuesForIndex(int deviceIndex) {
      if (deviceIndex < 0 || deviceIndex >= displayValuesByIndex.length) {
        return ImmutableList.of();
      }
      ImmutableList<String> values = displayValuesByIndex[deviceIndex];
      return values != null ? values : ImmutableList.of();
    }

    public ImmutableSet<String> lowerValuesForIndex(int deviceIndex) {
      return displayValuesForIndex(deviceIndex).stream()
          .filter(v -> !v.isEmpty())
          .map(Ascii::toLowerCase)
          .collect(toImmutableSet());
    }

    public KeyStats stats() {
      return stats;
    }

    private static final int[] EMPTY_POSTING = new int[0];
  }
}
