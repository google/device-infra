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
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DimensionOverlay;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.DimensionOverlayRaw;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.FleetDataSource;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * <p>Asynchronously loads cold dimensions via {@link FleetDataSource#pullDimensions(Set)} with
 * per-key in-flight deduplication and returns strong-reference maps for query execution. Overlays
 * are cached in a bounded per-fleet LRU cache (capacity 50, TTL 30m).
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
      Fleet fleet, Set<DeviceKeyDescriptor> keys, Executor executor) {
    if (keys.isEmpty()) {
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
    Set<String> seenKeyIds = new HashSet<>();
    Map<String, SettableFuture<DimensionOverlay>> newlyStarted = new LinkedHashMap<>();
    ImmutableSet.Builder<DeviceKeyDescriptor> keysToPull = ImmutableSet.builder();

    for (DeviceKeyDescriptor key : keys) {
      String keyId = key.id();
      if (!seenKeyIds.add(keyId)) {
        continue;
      }
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
          newlyStarted.put(keyId, settable);
          keysToPull.add(key);
        }
      }

      futures.add(Futures.transform(pullFuture, overlay -> Map.entry(keyId, overlay), executor));
    }

    if (!newlyStarted.isEmpty()) {
      // Every newly started future is already registered in inFlightMap, so any failure, including
      // a synchronous throw from pullDimensions, must complete them and clear their entries.
      try {
        ListenableFuture<ImmutableMap<String, DimensionOverlayRaw>> batchFuture =
            dataSource.pullDimensions(keysToPull.build());
        Futures.addCallback(
            batchFuture,
            new FutureCallback<>() {
              @Override
              public void onSuccess(ImmutableMap<String, DimensionOverlayRaw> rawByKey) {
                try {
                  FleetSnapshot snapshot = snapshotStore.get(fleet);
                  for (Map.Entry<String, SettableFuture<DimensionOverlay>> entry :
                      newlyStarted.entrySet()) {
                    String keyId = entry.getKey();
                    SettableFuture<DimensionOverlay> settable = entry.getValue();
                    DimensionOverlayRaw raw = rawByKey.get(keyId);
                    if (raw == null) {
                      // A data source must return an entry for every requested key. Failing the
                      // key instead of caching an empty overlay avoids serving zero matches for
                      // the whole cache TTL.
                      inFlightMap.remove(keyId, settable);
                      settable.setException(
                          new IllegalStateException(
                              String.format(
                                  "Data source for %s returned no overlay for requested key %s",
                                  fleet, keyId)));
                      continue;
                    }
                    DimensionOverlay overlay = DimensionOverlay.create(raw, snapshot);
                    cache.put(keyId, overlay);
                    inFlightMap.remove(keyId, settable);
                    settable.set(overlay);
                  }
                } catch (Throwable t) {
                  failBatch(inFlightMap, newlyStarted, t);
                }
              }

              @Override
              public void onFailure(Throwable t) {
                failBatch(inFlightMap, newlyStarted, t);
              }
            },
            executor);
      } catch (Throwable t) {
        failBatch(inFlightMap, newlyStarted, t);
      }
    }

    return Futures.transform(
        Futures.allAsList(futures),
        entries -> ImmutableMap.<String, DimensionOverlay>builder().putAll(entries).buildOrThrow(),
        executor);
  }

  private static void failBatch(
      ConcurrentMap<String, ListenableFuture<DimensionOverlay>> inFlightMap,
      Map<String, SettableFuture<DimensionOverlay>> newlyStarted,
      Throwable t) {
    for (Map.Entry<String, SettableFuture<DimensionOverlay>> entry : newlyStarted.entrySet()) {
      inFlightMap.remove(entry.getKey(), entry.getValue());
      entry.getValue().setException(t);
    }
  }
}
