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

package com.google.devtools.mobileharness.fe.v6.service.search.refresh;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DimensionOverlay;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.DimensionOverlayRaw;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.FleetDataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Thread-safe store for on-demand {@link DimensionOverlay} instances across fleets.
 *
 * <p>Asynchronously loads cold dimensions via {@link FleetDataSource#pullDimension(String)} with
 * in-flight deduplication and returns strong-reference maps for query execution. Overlays are
 * cached in a bounded per-fleet LRU cache (capacity 50, TTL 30m).
 */
@Singleton
public final class DimensionOverlayStore {

  private static final long MAX_CACHE_SIZE = 50L;
  private static final Duration CACHE_TTL = Duration.ofMinutes(30);

  private final Map<Fleet, FleetDataSource> dataSources;
  private final FleetSnapshotStore snapshotStore;
  private final ConcurrentMap<Fleet, Cache<String, DimensionOverlay>> memoryCaches =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<Fleet, ConcurrentMap<String, ListenableFuture<DimensionOverlay>>>
      inFlight = new ConcurrentHashMap<>();

  @Inject
  DimensionOverlayStore(Map<Fleet, FleetDataSource> dataSources, FleetSnapshotStore snapshotStore) {
    this.dataSources = checkNotNull(dataSources);
    this.snapshotStore = checkNotNull(snapshotStore);
  }

  /**
   * Asynchronously loads all requested overlay keys for the given fleet and returns a strong
   * reference map of loaded overlays. Keys that are already cached return immediately.
   */
  public ListenableFuture<ImmutableMap<String, DimensionOverlay>> loadOverlaysAsync(
      Fleet fleet, Set<String> keyIds, Executor executor) {
    if (keyIds.isEmpty()) {
      return immediateFuture(ImmutableMap.of());
    }

    FleetDataSource dataSource = dataSources.get(fleet);
    if (dataSource == null) {
      return immediateFuture(ImmutableMap.of());
    }

    Cache<String, DimensionOverlay> cache =
        memoryCaches.computeIfAbsent(
            fleet,
            f ->
                CacheBuilder.newBuilder()
                    .maximumSize(MAX_CACHE_SIZE)
                    .expireAfterWrite(CACHE_TTL)
                    .build());
    ConcurrentMap<String, ListenableFuture<DimensionOverlay>> inFlightMap =
        inFlight.computeIfAbsent(fleet, f -> new ConcurrentHashMap<>());

    List<ListenableFuture<Map.Entry<String, DimensionOverlay>>> futures = new ArrayList<>();

    for (String keyId : keyIds) {
      DimensionOverlay cached = cache.getIfPresent(keyId);
      if (cached != null) {
        futures.add(immediateFuture(Map.entry(keyId, cached)));
        continue;
      }

      ListenableFuture<DimensionOverlay> pullFuture = inFlightMap.get(keyId);
      if (pullFuture == null) {
        SettableFuture<DimensionOverlay> settable = SettableFuture.create();
        ListenableFuture<DimensionOverlay> existing = inFlightMap.putIfAbsent(keyId, settable);
        if (existing != null) {
          pullFuture = existing;
        } else {
          pullFuture = settable;
          ListenableFuture<DimensionOverlayRaw> rawFuture = dataSource.pullDimension(keyId);
          Futures.addCallback(
              rawFuture,
              new FutureCallback<>() {
                @Override
                public void onSuccess(DimensionOverlayRaw raw) {
                  try {
                    FleetSnapshot snapshot = snapshotStore.get(fleet);
                    DimensionOverlay overlay = DimensionOverlay.create(raw, snapshot);
                    cache.put(keyId, overlay);
                    inFlightMap.remove(keyId);
                    settable.set(overlay);
                  } catch (Throwable t) {
                    inFlightMap.remove(keyId);
                    settable.setException(t);
                  }
                }

                @Override
                public void onFailure(Throwable t) {
                  inFlightMap.remove(keyId);
                  settable.setException(t);
                }
              },
              executor);
        }
      }

      futures.add(Futures.transform(pullFuture, overlay -> Map.entry(keyId, overlay), executor));
    }

    return Futures.transform(
        Futures.allAsList(futures),
        entries -> ImmutableMap.<String, DimensionOverlay>builder().putAll(entries).buildOrThrow(),
        executor);
  }
}
