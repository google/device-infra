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

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Optional;

/**
 * The complete set of raw inputs a data source produces for one core build pass, consumed by {@link
 * FleetIndexBuilder} to produce a {@link FleetSnapshot}.
 *
 * <p>{@link #labDataByUniverse()} holds each {@link UniverseScope}'s {@link LabQueryResult}; {@link
 * #labData()} provides the merged {@link LabQueryResult} view across all universes. Single-universe
 * data sources supply {@link Builder#setLabData} (which records the result under {@link
 * UniverseScope#SELF}) together with optional {@link #hostEnrichments()} and {@link
 * #deviceEnrichments()}. Multi-universe data sources supply {@link Builder#setLabDataByUniverse}
 * and {@link Builder#setUniverseHostEnrichments} keyed by {@link UniverseScope} so host and device
 * records retain their universe provenance even when two universes report identical or empty host
 * names.
 */
@AutoValue
public abstract class CoreFleetRawData {

  /**
   * Lab query results keyed by {@link UniverseScope}. Single-universe sources contain one {@link
   * UniverseScope#SELF} entry; multi-universe sources contain one entry per responding universe.
   */
  public abstract ImmutableMap<UniverseScope, LabQueryResult> labDataByUniverse();

  /**
   * Merged lab query result across all entries in {@link #labDataByUniverse()}. For single-universe
   * sources, returns that universe's {@link LabQueryResult} directly.
   */
  @Memoized
  public LabQueryResult labData() {
    if (labDataByUniverse().size() == 1) {
      return labDataByUniverse().values().iterator().next();
    }
    LabQueryResult.LabView.Builder mergedLabView = LabQueryResult.LabView.newBuilder();
    for (LabQueryResult result : labDataByUniverse().values()) {
      mergedLabView.addAllLabData(result.getLabView().getLabDataList());
    }
    return LabQueryResult.newBuilder().setLabView(mergedLabView).build();
  }

  /** Per-device enrichment keyed by device id (for single-universe sources). */
  public abstract ImmutableMap<String, DeviceEnrichment> deviceEnrichments();

  /** Per-host enrichment keyed by host name (for single-universe sources). */
  public abstract ImmutableMap<String, HostEnrichment> hostEnrichments();

  /**
   * Per-universe host enrichment applied to every host pulled from that {@link UniverseScope} (used
   * by multi-universe data sources where host names may collide across universes).
   */
  public abstract ImmutableMap<UniverseScope, HostEnrichment> universeHostEnrichments();

  /**
   * Deployment-defined composite device keys whose values {@link FleetIndexBuilder} extracts from
   * each {@code DeviceInfo}'s {@link DeviceKeyDescriptor#deviceInfoSources()} and deduplicates in
   * order.
   */
  public abstract ImmutableList<DeviceKeyDescriptor> derivedDeviceKeys();

  /** Creates a new builder with empty enrichment maps. */
  public static Builder builder() {
    return new AutoValue_CoreFleetRawData.Builder()
        .setDeviceEnrichments(ImmutableMap.of())
        .setHostEnrichments(ImmutableMap.of())
        .setUniverseHostEnrichments(ImmutableMap.of())
        .setDerivedDeviceKeys(ImmutableList.of());
  }

  /** Creates raw data from lab query results alone, with no enrichment. */
  public static CoreFleetRawData ofLabData(LabQueryResult labData) {
    return builder().setLabData(labData).build();
  }

  /** Builder for {@link CoreFleetRawData}. */
  @AutoValue.Builder
  public abstract static class Builder {
    private Optional<LabQueryResult> singleLabData = Optional.empty();

    public abstract Builder setLabDataByUniverse(
        ImmutableMap<UniverseScope, LabQueryResult> labDataByUniverse);

    abstract Optional<ImmutableMap<UniverseScope, LabQueryResult>> labDataByUniverse();

    /** Sets the lab query result for a single-universe ({@link UniverseScope#SELF}) data source. */
    @CanIgnoreReturnValue
    public final Builder setLabData(LabQueryResult labData) {
      this.singleLabData = Optional.of(labData);
      return this;
    }

    public abstract Builder setDeviceEnrichments(
        ImmutableMap<String, DeviceEnrichment> deviceEnrichments);

    public abstract Builder setHostEnrichments(
        ImmutableMap<String, HostEnrichment> hostEnrichments);

    public abstract Builder setUniverseHostEnrichments(
        ImmutableMap<UniverseScope, HostEnrichment> universeHostEnrichments);

    public abstract Builder setDerivedDeviceKeys(
        ImmutableList<DeviceKeyDescriptor> derivedDeviceKeys);

    abstract CoreFleetRawData autoBuild();

    public final CoreFleetRawData build() {
      checkState(
          !(labDataByUniverse().isPresent() && singleLabData.isPresent()),
          "Cannot set both labDataByUniverse and labData on CoreFleetRawData.Builder");
      if (labDataByUniverse().isEmpty()) {
        setLabDataByUniverse(
            ImmutableMap.of(
                UniverseScope.SELF, singleLabData.orElse(LabQueryResult.getDefaultInstance())));
      }
      return autoBuild();
    }
  }
}
