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

package com.google.devtools.mobileharness.fe.v6.service.search.query;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Cell;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Column;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.HostRef;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Indicator;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.LinkCell;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.NavTarget;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.StatusCell;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TextCell;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.HostRecord;
import javax.inject.Inject;

/**
 * Turns indexed host records into result-table {@link Column}s and typed {@link Cell}s.
 *
 * <p>Mirrors {@link FleetCellMapper} for host-entity search, rendering:
 *
 * <ul>
 *   <li>{@code host::host_name} as a {@link LinkCell} to the host page;
 *   <li>{@code host::connectivity} as a {@link StatusCell} with semantic color indicator;
 *   <li>all remaining host fields and host properties as comma-joined {@link TextCell}s.
 * </ul>
 *
 * <p>Original value casing for per-value display maps (such as ATS controller display names) is
 * read from {@link FleetSnapshot#hostIndex()}'s {@link
 * com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex#valueDisplays(String)},
 * identical to the device path.
 */
public final class HostCellMapper {

  /**
   * Host connectivity status name to semantic indicator. Matches {@link
   * com.google.devtools.mobileharness.fe.v6.service.host.util.HostConnectivityStatuses#getTitle()}
   * and {@link
   * com.google.devtools.mobileharness.fe.v6.service.host.util.HostConnectivityStatuses#getIndicator()},
   * following {@link FleetCellMapper}'s device status mapping style.
   */
  private static final ImmutableMap<String, Indicator> CONNECTIVITY_INDICATORS =
      ImmutableMap.<String, Indicator>builder()
          .put("RUNNING", Indicator.INDICATOR_OK)
          .put("CONNECTED", Indicator.INDICATOR_OK)
          .put("MISSING", Indicator.INDICATOR_ERROR)
          .put("DISCONNECTED", Indicator.INDICATOR_ERROR)
          .put("UNKNOWN", Indicator.INDICATOR_NEUTRAL)
          .buildOrThrow();

  @Inject
  HostCellMapper() {}

  /**
   * Builds the header for a column key. The display name comes from the host index when the key is
   * present in the fleet, and falls back to a name derived from the key namespace otherwise.
   */
  public Column column(String keyId, FleetSnapshot snapshot) {
    String display = snapshot.hostIndex().displayName(keyId);
    return Column.newBuilder().setKey(keyId).setDisplayName(display).build();
  }

  /** Builds a typed cell for a host and column key. */
  public Cell cell(String keyId, HostRecord host, FleetSnapshot snapshot) {
    if (keyId.equals(FleetSearchKeys.HOST_NAME)) {
      return Cell.newBuilder().setLink(hostLink(host)).build();
    }
    if (keyId.equals(FleetSearchKeys.HOST_CONNECTIVITY)) {
      String connectivity = firstValue(host.values(FleetSearchKeys.HOST_CONNECTIVITY));
      return Cell.newBuilder().setStatus(statusCell(connectivity)).build();
    }
    // The device count is a numeric string, rendered as a plain TextCell. All remaining host keys
    // (including the multi-valued lab type, comma-joined like device type/owner) and host
    // properties also render as TextCell.
    return Cell.newBuilder().setText(textCell(displayValues(host, keyId, snapshot))).build();
  }

  private static LinkCell hostLink(HostRecord host) {
    // TODO: For ats-all multi-controller routing, consider populating
    // universe/ats_controller on HostRef once HostRecord.atsController is indexed.
    String hostName = host.hostName();
    String hostIp = firstValue(host.values(FleetSearchKeys.HOST_IP));
    return LinkCell.newBuilder()
        .setText(hostName)
        .setTarget(
            NavTarget.newBuilder()
                .setHost(HostRef.newBuilder().setHostName(hostName).setHostIp(hostIp)))
        .build();
  }

  private static StatusCell statusCell(String connectivity) {
    Indicator indicator =
        CONNECTIVITY_INDICATORS.getOrDefault(
            Ascii.toUpperCase(connectivity), Indicator.INDICATOR_NEUTRAL);
    return StatusCell.newBuilder().setText(connectivity).setIndicator(indicator).build();
  }

  private static TextCell textCell(ImmutableList<String> values) {
    return TextCell.newBuilder().setValue(String.join(", ", values)).build();
  }

  /**
   * The host's display-cased values for a key, in the same key-namespace scheme the host index
   * builder uses. A single-valued key yields a one-element list (or an empty list when the value is
   * absent); a multi-valued key yields all its values. The searcher reuses it to derive sort
   * values.
   */
  static ImmutableList<String> displayValues(
      HostRecord host, String keyId, FleetSnapshot snapshot) {
    if (keyId.equals(FleetSearchKeys.HOST_ATS_CONTROLLER)) {
      return atsControllerValues(host, snapshot);
    }
    return host.values(keyId);
  }

  private static ImmutableList<String> atsControllerValues(
      HostRecord host, FleetSnapshot snapshot) {
    ImmutableList<String> vals = host.values(FleetSearchKeys.HOST_ATS_CONTROLLER);
    if (vals.isEmpty()) {
      return ImmutableList.of();
    }
    String id = vals.get(0);
    if (id.isEmpty()) {
      return ImmutableList.of();
    }
    String display =
        snapshot
            .hostIndex()
            .valueDisplays(FleetSearchKeys.HOST_ATS_CONTROLLER)
            .getOrDefault(Ascii.toLowerCase(id), id);
    return ImmutableList.of(display);
  }

  private static String firstValue(ImmutableList<String> list) {
    return Iterables.getFirst(list, "");
  }
}
