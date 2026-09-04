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

import static com.google.common.collect.ImmutableList.toImmutableList;

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
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.HostRecord;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import javax.inject.Inject;

/**
 * Turns indexed host records into result-table {@link Column}s and typed {@link Cell}s.
 *
 * <p>Mirrors {@link FleetCellMapper} for host-entity search, rendering:
 *
 * <ul>
 *   <li>{@code host_field::host_name} as a {@link LinkCell} to the host page;
 *   <li>{@code host_field::connectivity} as a {@link StatusCell} with semantic color indicator;
 *   <li>all remaining host fields and host properties as comma-joined {@link TextCell}s.
 * </ul>
 */
public final class HostCellMapper {

  /** Host connectivity status name to semantic indicator. */
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

  /** Builds a typed cell for a host and column key. */
  public Cell cell(String keyId, HostRecord host, FleetSnapshot snapshot) {
    if (keyId.equals(HostKeys.HOST_NAME.id())) {
      return Cell.newBuilder().setLink(hostLink(host)).build();
    }
    if (keyId.equals(HostKeys.CONNECTIVITY.id())) {
      String connectivity = firstValue(host.values(HostKeys.CONNECTIVITY.id()));
      return Cell.newBuilder().setStatus(statusCell(connectivity)).build();
    }
    return Cell.newBuilder().setText(textCell(displayValues(host, keyId, snapshot))).build();
  }

  private static LinkCell hostLink(HostRecord host) {
    String hostName = host.hostName();
    String hostIp = firstValue(host.values(HostKeys.HOST_IP.id()));
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
   * builder uses.
   */
  static ImmutableList<String> displayValues(
      HostRecord host, String keyId, FleetSnapshot snapshot) {
    ImmutableList<String> values = host.values(keyId);
    if (values.isEmpty()) {
      return ImmutableList.of();
    }
    ImmutableMap<String, String> displays = snapshot.hostIndex().valueDisplays(keyId);
    if (displays.isEmpty()) {
      return values;
    }
    return values.stream()
        .map(val -> displays.getOrDefault(Ascii.toLowerCase(val), val))
        .collect(toImmutableList());
  }

  private static String firstValue(ImmutableList<String> list) {
    return Iterables.getFirst(list, "");
  }
}
