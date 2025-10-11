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

package com.google.devtools.mobileharness.shared.util.cache.persistent.manager;

import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.base.DataSize;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * An {@link EvictionPolicy} that evicts the least recently accessed entries when the total cache
 * size exceeds a given limit.
 */
final class LraEvictionPolicy implements EvictionPolicy {

  private final DataSize maxCacheSize;
  private final double trimToRatio;

  /**
   * Creates an LRA eviction policy.
   *
   * @param maxCacheSize the maximum size of the cache
   * @param trimToRatio a ratio (e.g., 0.8) to which the cache size will be reduced when the total
   *     cache size exceeds the {@code maxCacheSize}
   */
  @Inject
  LraEvictionPolicy(
      @Named("max_cache_size") DataSize maxCacheSize, @Named("trim_to_ratio") double trimToRatio) {
    this.maxCacheSize = maxCacheSize;
    this.trimToRatio = trimToRatio;
  }

  @Override
  public ImmutableList<CacheEntry> selectForEviction(List<CacheEntry> allEntries) {
    DataSize totalSize =
        allEntries.stream().map(CacheEntry::size).reduce(DataSize.ZERO, DataSize::plus);
    if (totalSize.compareTo(maxCacheSize) <= 0) {
      return ImmutableList.of();
    }

    DataSize targetSize = maxCacheSize.multipliedBy(trimToRatio);
    DataSize toEvict = totalSize.minus(targetSize);
    DataSize evictedSize = DataSize.ZERO;

    // Sort entries by last access time, oldest first.
    List<CacheEntry> sortedEntries =
        allEntries.stream()
            .sorted(Comparator.comparing(CacheEntry::lastAccessTime))
            .collect(toCollection(ArrayList::new));

    ImmutableList.Builder<CacheEntry> evictionList = ImmutableList.builder();
    for (CacheEntry entry : sortedEntries) {
      if (evictedSize.compareTo(toEvict) >= 0) {
        break;
      }
      evictionList.add(entry);
      evictedSize = evictedSize.plus(entry.size());
    }
    return evictionList.build();
  }
}
