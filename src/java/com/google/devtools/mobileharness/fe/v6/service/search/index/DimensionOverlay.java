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
 * <p>Holds the snapshot-independent ground truth ({@link #uuidToValues}, {@link #valueCounts},
 * {@link #sortedValues}, {@link #valueDisplays}) derived purely from the pulled dimension data.
 *
 * <p>Posting lists ({@code value -> int[] deviceIndex}) are bound to a specific snapshot's device
 * ordering via {@link #bind(FleetSnapshot)}. When a snapshot rotates, postings are re-indexed in
 * &lt;1 ms against {@link FleetSnapshot#uuidToIndex()}.
 */
public final class DimensionOverlay {

  private final String keyId;
  private final ImmutableMap<String, ImmutableList<String>> uuidToValues;
  private final ImmutableMap<String, Integer> valueCounts;
  private final ImmutableList<String> sortedValues;
  private final ImmutableMap<String, String> valueDisplays;

  private volatile SnapshotBoundPostings boundPostings;

  private DimensionOverlay(
      String keyId,
      ImmutableMap<String, ImmutableList<String>> uuidToValues,
      ImmutableMap<String, Integer> valueCounts,
      ImmutableList<String> sortedValues,
      ImmutableMap<String, String> valueDisplays,
      SnapshotBoundPostings boundPostings) {
    this.keyId = checkNotNull(keyId);
    this.uuidToValues = checkNotNull(uuidToValues);
    this.valueCounts = checkNotNull(valueCounts);
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

  public ImmutableMap<String, Integer> valueCounts() {
    return valueCounts;
  }

  public ImmutableList<String> sortedValues() {
    return sortedValues;
  }

  public ImmutableMap<String, String> valueDisplays() {
    return valueDisplays;
  }

  public int valueCount(String value) {
    return valueCounts.getOrDefault(value, 0);
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
   * Returns posting lists aligned with the given snapshot. Re-indexes in &lt;1 ms if the snapshot
   * has rotated since the last bind.
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
    }

    ImmutableMap.Builder<String, int[]> frozen = ImmutableMap.builder();
    for (Map.Entry<String, List<Integer>> entry : postingsMap.entrySet()) {
      List<Integer> list = entry.getValue();
      Collections.sort(list);
      frozen.put(entry.getKey(), Ints.toArray(list));
    }
    return new SnapshotBoundPostings(snapshot.buildTime(), frozen.buildOrThrow());
  }

  /** Builds a {@link DimensionOverlay} from raw pulled data and initial snapshot. */
  public static DimensionOverlay create(DimensionOverlayRaw raw, FleetSnapshot snapshot) {
    String keyId = raw.keyId();
    ImmutableMap<String, ImmutableList<String>> uuidToValues = raw.uuidToValues();

    Map<String, Integer> valueCounts = new HashMap<>();
    Map<String, String> valueDisplays = new HashMap<>();
    Set<String> distinctValues = new HashSet<>();

    for (ImmutableList<String> values : uuidToValues.values()) {
      Set<String> seenForDevice = new HashSet<>();
      for (String rawVal : values) {
        String valLower = Ascii.toLowerCase(rawVal);
        if (!valLower.isEmpty() && seenForDevice.add(valLower)) {
          distinctValues.add(valLower);
          valueCounts.merge(valLower, 1, Integer::sum);
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
            ImmutableMap.copyOf(valueCounts),
            ImmutableList.copyOf(sortedList),
            ImmutableMap.copyOf(valueDisplays),
            new SnapshotBoundPostings(Instant.EPOCH, ImmutableMap.of()));

    // Bind to the given snapshot immediately.
    overlay.bind(snapshot);
    return overlay;
  }

  /** Posting lists bound to a specific snapshot build time. */
  public static final class SnapshotBoundPostings {
    private final Instant buildTime;
    private final ImmutableMap<String, int[]> postings;

    SnapshotBoundPostings(Instant buildTime, ImmutableMap<String, int[]> postings) {
      this.buildTime = buildTime;
      this.postings = postings;
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

    private static final int[] EMPTY_POSTING = new int[0];
  }
}
