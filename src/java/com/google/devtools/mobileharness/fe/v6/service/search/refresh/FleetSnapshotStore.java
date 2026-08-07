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

import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Lock-free store of the current serving {@link FleetSnapshot} and {@link LazyPostings} per fleet.
 *
 * <p>The refresh cycle builds a fresh immutable snapshot for each fleet it manages and publishes it
 * with {@link #publish}. Query code reads the current snapshot for a fleet with {@link #get} and
 * the lazily built posting lists with {@link #postings}. Because each snapshot is immutable and
 * each fleet's entry is replaced atomically by the concurrent map, readers never see a partially
 * built snapshot and never need a lock. A fleet with no published snapshot yet reads back as {@link
 * FleetSnapshot#empty()} so queries before the first refresh return empty results rather than null.
 */
@Singleton
public final class FleetSnapshotStore {

  private final ConcurrentMap<Fleet, FleetSnapshot> snapshots = new ConcurrentHashMap<>();
  private final ConcurrentMap<Fleet, LazyPostings> postingsCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<Fleet, LazyPostings> hostPostingsCache = new ConcurrentHashMap<>();

  @Inject
  FleetSnapshotStore() {}

  /** Returns the current snapshot for the fleet, or {@link FleetSnapshot#empty()} if none yet. */
  public FleetSnapshot get(Fleet fleet) {
    return snapshots.getOrDefault(fleet, FleetSnapshot.empty());
  }

  /** Returns true if a snapshot has been published for the fleet. */
  public boolean hasSnapshot(Fleet fleet) {
    return snapshots.containsKey(fleet);
  }

  /** Returns the lazy device posting lists for the fleet, building a new one if none exists yet. */
  public LazyPostings postings(Fleet fleet) {
    return postingsCache.computeIfAbsent(fleet, f -> new LazyPostings(get(f).devices()));
  }

  /** Returns the lazy host posting lists for the fleet, building a new one if none exists yet. */
  public LazyPostings hostPostings(Fleet fleet) {
    return hostPostingsCache.computeIfAbsent(fleet, f -> LazyPostings.forHosts(get(f).hosts()));
  }

  /** Publishes a new snapshot as the serving snapshot for the fleet. */
  public void publish(Fleet fleet, FleetSnapshot snapshot) {
    // TODO: Consider bundling FleetSnapshot and LazyPostings into a single atomic
    // container (e.g. SnapshotEntry) if atomic pairing across get() and postings() is desired.
    snapshots.put(fleet, snapshot);
    postingsCache.put(fleet, new LazyPostings(snapshot.devices()));
    hostPostingsCache.put(fleet, LazyPostings.forHosts(snapshot.hosts()));
  }
}
