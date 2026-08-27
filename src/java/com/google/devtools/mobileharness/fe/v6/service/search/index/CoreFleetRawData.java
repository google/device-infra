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
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;

/**
 * The complete set of raw inputs a data source produces for one core build pass, consumed by {@link
 * FleetIndexBuilder} to produce a {@link FleetSnapshot}.
 *
 * <p>{@link #labData()} is the LabInfo query result that every deployment provides. The enrichment
 * maps are deployment-neutral overlays keyed by device id and host name respectively: a source
 * fills whatever it has and leaves the rest empty. A LabInfo-only source supplies just {@link
 * #labData()}; ats-one adds device WiFi SSIDs; the internal 1p source adds host enrichment. The
 * builder joins each device and host to its enrichment entry, if any, and treats a missing entry as
 * no extra data.
 */
@AutoValue
public abstract class CoreFleetRawData {

  /**
   * Lab query result from LabInfoService. The base data every device and host record derives from.
   */
  public abstract LabQueryResult labData();

  /** Per-device enrichment keyed by device id. */
  public abstract ImmutableMap<String, DeviceEnrichment> deviceEnrichments();

  /** Per-host enrichment keyed by host name. */
  public abstract ImmutableMap<String, HostEnrichment> hostEnrichments();

  /**
   * Friendly display names for ATS controllers, keyed by controller id. The ats-all source fills
   * this so each {@code host_field::ats_controller} value can show a friendly display (for example
   * "Partner Lab: Xiaomi") while its filter term stays the controller id. Empty in deployments that
   * do not carry ATS controllers.
   */
  public abstract ImmutableMap<String, String> atsControllerDisplays();

  /** Creates a new builder with empty enrichment maps. */
  public static Builder builder() {
    return new AutoValue_CoreFleetRawData.Builder()
        .setDeviceEnrichments(ImmutableMap.of())
        .setHostEnrichments(ImmutableMap.of())
        .setAtsControllerDisplays(ImmutableMap.of());
  }

  /** Creates raw data from lab query results alone, with no enrichment. */
  public static CoreFleetRawData ofLabData(LabQueryResult labData) {
    return builder().setLabData(labData).build();
  }

  /** Builder for {@link CoreFleetRawData}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setLabData(LabQueryResult labData);

    public abstract Builder setDeviceEnrichments(
        ImmutableMap<String, DeviceEnrichment> deviceEnrichments);

    public abstract Builder setHostEnrichments(
        ImmutableMap<String, HostEnrichment> hostEnrichments);

    public abstract Builder setAtsControllerDisplays(
        ImmutableMap<String, String> atsControllerDisplays);

    public abstract CoreFleetRawData build();
  }
}
