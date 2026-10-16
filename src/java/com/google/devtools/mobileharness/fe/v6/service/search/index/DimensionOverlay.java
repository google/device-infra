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
 * <p>Holds the snapshot-independent ground truth ({@link #uuidToValues}, {@link #sortedValues},
 * {@link #valueDisplays}) derived purely from the pulled dimension data.
 *
 * <p>Posting lists ({@code value -> int[] deviceIndex}), value counts, and key statistics are bound
 * to a specific snapshot's device ordering via {@link #bind(FleetSnapshot)}. When a snapshot
 * rotates, they are re-indexed in &lt;1 ms against {@link FleetSnapshot#uuidToIndex()}.
 */
public final class DimensionOverlay {

  private final String keyId;
  private final ImmutableMap<String, ImmutableList<String>> uuidToValues;
  private final ImmutableList<String> sortedValues;
  private final ImmutableMap<String, String> valueDisplays;

  private volatile SnapshotBoundPostings boundPostings;

  private DimensionOverlay(
      String keyId,
      ImmutableMap<String, ImmutableList<String>> uuidToValues,
      ImmutableList<String> sortedValues,
      ImmutableMap<String, String> valueDisplays,
      SnapshotBoundPostings boundPostings) {
    this.keyId = checkNotNull(keyId);
    this.uuidToValues = checkNotNull(uuidToValues);
    this.sortedValues = checkNotNull(sortedValues);
    this.valueDisplays = checkNotNull(valueDisplays);
    this.boundPostings = checkNotNull(boundPostings);
  }

  public String keyId() {
    return keyId;
  }

  public ImmutableMap<String, ImmutableList<String>> uuidToValues() {
    return uuidToValues;
  }

  public ImmutableList<String> sortedValues() {
    return sortedValues;
  }

  public ImmutableMap<String, String> valueDisplays() {
    return valueDisplays;
  }

  public ImmutableList<String> valuesForUuid(String uuid) {
    ImmutableList<String> values = uuidToValues.get(uuid);
    return values != null ? values : ImmutableList.of();
  }

  public ImmutableSet<String> lowerValuesForUuid(String uuid) {
    ImmutableList<String> values = uuidToValues.get(uuid);
    if (values == null || values.isEmpty()) {
      return ImmutableSet.of();
    }
    return values.stream().map(Ascii::toLowerCase).collect(toImmutableSet());
  }

  /**
   * Returns posting lists, value counts, and key statistics aligned with the given snapshot.
   * Re-indexes in &lt;1 ms if the snapshot has rotated since the last bind.
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

  private SnapshotBoundPostings reindex(FleetSnapshot snapshot) {
    ImmutableMap<String, Integer> uuidToIndex = snapshot.uuidToIndex();
    Map<String, List<Integer>> postingsMap = new HashMap<>();
    int entityCount = 0;
    Set<String> singleCombinations = new HashSet<>();
    Set<ImmutableSet<String>> multiCombinations = new HashSet<>();

    for (Map.Entry<String, ImmutableList<String>> entry : uuidToValues.entrySet()) {
      String uuid = entry.getKey();
      Integer deviceIndex = uuidToIndex.get(uuid);
      if (deviceIndex == null) {
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
        snapshot.buildTime(), frozen.buildOrThrow(), boundCounts.buildOrThrow(), stats);
  }

  /** Builds a {@link DimensionOverlay} from raw pulled data and initial snapshot. */
  public static DimensionOverlay create(DimensionOverlayRaw raw, FleetSnapshot snapshot) {
    String keyId = raw.keyId();
    ImmutableMap<String, ImmutableList<String>> uuidToValues = raw.uuidToValues();

    Map<String, String> valueDisplays = new HashMap<>();
    Set<String> distinctValues = new HashSet<>();

    for (ImmutableList<String> values : uuidToValues.values()) {
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
            uuidToValues,
            ImmutableList.copyOf(sortedList),
            ImmutableMap.copyOf(valueDisplays),
            new SnapshotBoundPostings(
                Instant.EPOCH, ImmutableMap.of(), ImmutableMap.of(), KeyStats.EMPTY));

    // Bind to the given snapshot immediately.
    overlay.bind(snapshot);
    return overlay;
  }

  /** Posting lists, value counts, and key statistics bound to a specific snapshot build time. */
  public static final class SnapshotBoundPostings {
    private final Instant buildTime;
    private final ImmutableMap<String, int[]> postings;
    private final ImmutableMap<String, Integer> valueCounts;
    private final KeyStats stats;

    SnapshotBoundPostings(
        Instant buildTime,
        ImmutableMap<String, int[]> postings,
        ImmutableMap<String, Integer> valueCounts,
        KeyStats stats) {
      this.buildTime = buildTime;
      this.postings = postings;
      this.valueCounts = valueCounts;
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

    public KeyStats stats() {
      return stats;
    }

    private static final int[] EMPTY_POSTING = new int[0];
  }
}
