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

import static java.util.Objects.requireNonNull;

import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Lock-free store of the current serving {@link SnapshotBundle} per fleet.
 *
 * <p>The refresh cycle builds a fresh immutable {@link FleetSnapshot} for each fleet it manages and
 * publishes it with {@link #publish}, which pairs the snapshot with fresh device and host {@link
 * LazyPostings} in a single {@link SnapshotBundle}. Query code that needs both a snapshot and its
 * posting lists reads {@link #getBundle} once so record ordinals in the posting lists always match
 * the snapshot's device and host lists. Because each bundle is immutable and each fleet's entry is
 * replaced atomically by the concurrent map, readers never see a partially built snapshot or
 * mismatched posting lists and never need a lock. A fleet with no published snapshot yet reads back
 * as an empty bundle backed by {@link FleetSnapshot#empty()} so queries before the first refresh
 * return empty results rather than null.
 */
@Singleton
public final class FleetSnapshotStore {

  /**
   * Immutable pairing of a serving {@link FleetSnapshot} with the lazy posting lists over its
   * device and host lists.
   */
  public record SnapshotBundle(
      FleetSnapshot snapshot, LazyPostings devicePostings, LazyPostings hostPostings) {

    public SnapshotBundle {
      requireNonNull(snapshot);
      requireNonNull(devicePostings);
      requireNonNull(hostPostings);
    }

    /**
     * Creates a {@link SnapshotBundle} for {@code snapshot} with fresh device and host postings.
     */
    public static SnapshotBundle of(FleetSnapshot snapshot) {
      return new SnapshotBundle(
          snapshot, new LazyPostings(snapshot.devices()), LazyPostings.forHosts(snapshot.hosts()));
    }
  }

  private static final SnapshotBundle EMPTY = SnapshotBundle.of(FleetSnapshot.empty());

  private final ConcurrentMap<Fleet, SnapshotBundle> bundles = new ConcurrentHashMap<>();

  @Inject
  FleetSnapshotStore() {}

  /**
   * Returns the current serving bundle for the fleet, or an empty bundle backed by {@link
   * FleetSnapshot#empty()} if none has been published yet.
   */
  public SnapshotBundle getBundle(Fleet fleet) {
    return bundles.getOrDefault(fleet, EMPTY);
  }

  /** Returns the current snapshot for the fleet, or {@link FleetSnapshot#empty()} if none yet. */
  public FleetSnapshot get(Fleet fleet) {
    return getBundle(fleet).snapshot();
  }

  /** Returns true if a snapshot has been published for the fleet. */
  public boolean hasSnapshot(Fleet fleet) {
    return bundles.containsKey(fleet);
  }

  /** Publishes a new snapshot and its lazy posting lists atomically for the fleet. */
  public void publish(Fleet fleet, FleetSnapshot snapshot) {
    bundles.put(fleet, SnapshotBundle.of(snapshot));
  }
}
