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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazily builds and caches posting lists (record index arrays) per key from a forward store.
 *
 * <p>Built once per {@link FleetSnapshot} lifetime and discarded when the snapshot is replaced by
 * the refresh cycle. A key's posting lists are constructed on first access by scanning the forward
 * store (O(N) per key, roughly 1 to 2 ms for 152K devices) and cached for subsequent lookups.
 *
 * <p>The forward store is supplied as a record-agnostic {@link RecordValues}, so the same posting
 * machinery serves the device index (backed by {@link DeviceValueExtractor}) and the host index
 * (backed by {@link HostValueExtractor}). The scan visits records in ascending index order, so a
 * posting list is always sorted, and the record index is stable across lookups.
 *
 * <p>Thread safety is provided by {@link ConcurrentHashMap#computeIfAbsent}, which guarantees that
 * at most one thread builds the posting lists for a given key.
 */
public final class LazyPostings {

  private static final int[] EMPTY = new int[0];

  /**
   * A record-agnostic view of a forward store: how many records it holds, and the lowercased value
   * set each record carries for a key. Implementations delegate to the matching value extractor.
   */
  public interface RecordValues {
    /** Number of records in the forward store. */
    int recordCount();

    /** Lowercased value set the record at {@code index} carries for {@code keyId}. */
    ImmutableSet<String> valuesForKey(int index, String keyId);
  }

  private final RecordValues source;
  private final ConcurrentHashMap<String, ImmutableMap<String, int[]>> cache =
      new ConcurrentHashMap<>();

  public LazyPostings(RecordValues source) {
    this.source = source;
  }

  /** Builds posting lists over a device forward store using {@link DeviceValueExtractor}. */
  public LazyPostings(ImmutableList<DeviceRecord> devices) {
    this(
        new RecordValues() {
          @Override
          public int recordCount() {
            return devices.size();
          }

          @Override
          public ImmutableSet<String> valuesForKey(int index, String keyId) {
            return DeviceValueExtractor.valuesForKey(devices.get(index), keyId);
          }
        });
  }

  /** Builds posting lists over a host forward store using {@link HostValueExtractor}. */
  public static LazyPostings forHosts(ImmutableList<HostRecord> hosts) {
    return new LazyPostings(
        new RecordValues() {
          @Override
          public int recordCount() {
            return hosts.size();
          }

          @Override
          public ImmutableSet<String> valuesForKey(int index, String keyId) {
            return HostValueExtractor.valuesForKey(hosts.get(index), keyId);
          }
        });
  }

  /** Returns the posting list for (key, value), or an empty array if absent. */
  public int[] get(String keyId, String value) {
    ImmutableMap<String, int[]> keyPostings = forKey(keyId);
    int[] posting = keyPostings.get(value);
    return posting != null ? posting : EMPTY;
  }

  /** Builds or returns cached posting lists for all values of a key. */
  public ImmutableMap<String, int[]> forKey(String keyId) {
    return cache.computeIfAbsent(keyId, this::buildKeyPostings);
  }

  private ImmutableMap<String, int[]> buildKeyPostings(String keyId) {
    Map<String, List<Integer>> temp = new HashMap<>();
    int count = source.recordCount();
    for (int i = 0; i < count; i++) {
      for (String value : source.valuesForKey(i, keyId)) {
        temp.computeIfAbsent(value, v -> new ArrayList<>()).add(i);
      }
    }
    ImmutableMap.Builder<String, int[]> result = ImmutableMap.builder();
    for (Map.Entry<String, List<Integer>> entry : temp.entrySet()) {
      List<Integer> list = entry.getValue();
      int[] arr = Ints.toArray(list);
      result.put(entry.getKey(), arr);
    }
    return result.buildOrThrow();
  }
}
