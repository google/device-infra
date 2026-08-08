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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.time.Instant;

/**
 * Immutable snapshot of one fleet's indexed data.
 *
 * <p>The refresh scheduler builds a new snapshot periodically from the data sources
 * (LabInfoService, HostInfoService, DeviceConfigService) and atomically swaps it into the serving
 * path via {@code AtomicReference}. Query code reads a snapshot reference once and operates on it
 * without locks.
 *
 * <p>This class holds the forward store (device and host records) plus the {@link FleetIndex} with
 * the posting lists, value counts, sorted value lists, and key catalog. {@link FleetIndexBuilder}
 * populates both from a single lab query result.
 */
@AutoValue
public abstract class FleetSnapshot {

  /** When this snapshot was built. Used for staleness monitoring. */
  public abstract Instant buildTime();

  /** Device forward store. Index position is the device's internal ID for posting lists. */
  public abstract ImmutableList<DeviceRecord> devices();

  /** Host forward store. */
  public abstract ImmutableList<HostRecord> hosts();

  /** Inverted index and value index over {@link #devices()}. */
  public abstract FleetIndex index();

  /** Inverted index and value index over {@link #hosts()}. */
  public abstract FleetIndex hostIndex();

  /** Convenience: number of devices in this snapshot. */
  public int deviceCount() {
    return devices().size();
  }

  /** Convenience: number of hosts in this snapshot. */
  public int hostCount() {
    return hosts().size();
  }

  /** Creates a new builder. */
  public static Builder builder() {
    return new AutoValue_FleetSnapshot.Builder();
  }

  /** An empty snapshot, used as the initial state before the first refresh completes. */
  public static FleetSnapshot empty() {
    return builder()
        .setBuildTime(Instant.EPOCH)
        .setDevices(ImmutableList.of())
        .setHosts(ImmutableList.of())
        .setIndex(FleetIndex.empty())
        .setHostIndex(FleetIndex.empty())
        .build();
  }

  /** Builder for {@link FleetSnapshot}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setBuildTime(Instant buildTime);

    public abstract Builder setDevices(ImmutableList<DeviceRecord> devices);

    public abstract Builder setHosts(ImmutableList<HostRecord> hosts);

    public abstract Builder setIndex(FleetIndex index);

    public abstract Builder setHostIndex(FleetIndex hostIndex);

    public abstract FleetSnapshot build();
  }
}
