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

import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_CONNECTIVITY;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_DAEMON_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_DEVICE_COUNT;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_IP;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_LAB_SERVER_VERSION;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_LAB_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_NAME;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_OS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_RELEASE_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_RELEASE_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.PROP_PREFIX;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import javax.inject.Inject;

/**
 * Turns indexed host records into result-table {@link Column}s and typed {@link Cell}s.
 *
 * <p>This is the host-entity analogue of {@link FleetCellMapper}. It builds the same typed {@code
 * Cell} oneof (TextCell, LinkCell, StatusCell) the frontend renders generically, but projects a
 * {@link HostRecord} onto host-name identity, host connectivity status, and the host device count
 * rather than the device projection. Value extraction mirrors the host key namespaces stamped by
 * {@code FleetIndexBuilder.indexHost} and read back by {@link HostValueExtractor}, but reads
 * display-cased values straight from the {@link HostRecord} forward store rather than the
 * lowercased index terms.
 */
public final class HostCellMapper {

  /**
   * Host connectivity value to semantic indicator. The connectivity text is uppercased before
   * lookup, so it matches the bucketed titles the host detail page uses (for example "Running",
   * "Missing"). Any value not listed here maps to {@link Indicator#INDICATOR_NEUTRAL}, which covers
   * the "Unknown" bucket. This ports the prototype's {@code HOST_CONNECTIVITY_SEVERITY} (Running ->
   * ok, Missing -> error, Unknown -> neutral) into the shared indicator vocabulary, matching {@link
   * FleetCellMapper}'s device status mapping style.
   */
  private static final ImmutableMap<String, Indicator> CONNECTIVITY_INDICATORS =
      ImmutableMap.<String, Indicator>builder()
          .put("RUNNING", Indicator.INDICATOR_OK)
          .put("MISSING", Indicator.INDICATOR_ERROR)
          .put("UNKNOWN", Indicator.INDICATOR_NEUTRAL)
          .buildOrThrow();

  @Inject
  HostCellMapper() {}

  /**
   * Builds the header for a column key. The display name comes from the host index when the key is
   * present in the fleet, and falls back to a name derived from the key namespace otherwise.
   */
  public Column column(String keyId, FleetSnapshot snapshot) {
    String display =
        snapshot.hostIndex().displayNames().getOrDefault(keyId, deriveDisplayName(keyId));
    return Column.newBuilder().setKey(keyId).setDisplayName(display).build();
  }

  /** Builds a typed cell for a host and column key. */
  public Cell cell(String keyId, HostRecord host, FleetSnapshot snapshot) {
    return switch (keyId) {
      case HOST_NAME -> Cell.newBuilder().setLink(hostLink(host)).build();
      case HOST_CONNECTIVITY ->
          Cell.newBuilder().setStatus(statusCell(host.hostConnectivity())).build();
      // The device count is a numeric string, rendered as a plain TextCell. All remaining host keys
      // (including the multi-valued lab type, comma-joined like device type/owner) and host
      // properties also render as TextCell.
      default -> Cell.newBuilder().setText(textCell(displayValues(host, keyId, snapshot))).build();
    };
  }

  private static LinkCell hostLink(HostRecord host) {
    // TODO: For ats-all multi-controller routing, consider populating
    // universe/ats_controller on HostRef once HostRecord.atsController is indexed.
    return LinkCell.newBuilder()
        .setText(host.hostName())
        .setTarget(
            NavTarget.newBuilder()
                .setHost(
                    HostRef.newBuilder().setHostName(host.hostName()).setHostIp(host.hostIp())))
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
   * absent); a multi-valued key yields all its values. This mirrors {@link HostValueExtractor} but
   * keeps the original casing, so the searcher reuses it to derive sort values.
   */
  static ImmutableList<String> displayValues(
      HostRecord host, String keyId, FleetSnapshot snapshot) {
    return switch (keyId) {
      case HOST_NAME -> singleton(host.hostName());
      case HOST_IP -> singleton(host.hostIp());
      case HOST_OS -> singleton(host.hostOs());
      case HOST_CONNECTIVITY -> singleton(host.hostConnectivity());
      case HOST_LAB_TYPE -> host.labTypes();
      case HOST_DAEMON_STATUS ->
          host.daemonStatus().map(ImmutableList::of).orElse(ImmutableList.of());
      case HOST_RELEASE_STATUS ->
          host.releaseStatus().map(ImmutableList::of).orElse(ImmutableList.of());
      case HOST_RELEASE_TYPE ->
          host.releaseType().map(ImmutableList::of).orElse(ImmutableList.of());
      case HOST_LAB_SERVER_VERSION ->
          host.labServerVersion().map(ImmutableList::of).orElse(ImmutableList.of());
      case HOST_DEVICE_COUNT -> ImmutableList.of(String.valueOf(host.deviceCount()));
      default -> prefixedValues(host, keyId);
    };
  }

  private static ImmutableList<String> prefixedValues(HostRecord host, String keyId) {
    if (keyId.startsWith(PROP_PREFIX)) {
      String value = host.hostProperties().get(keyId.substring(PROP_PREFIX.length()));
      return value == null ? ImmutableList.of() : singleton(value);
    }
    return ImmutableList.of();
  }

  private static ImmutableList<String> singleton(String value) {
    return value.isEmpty() ? ImmutableList.of() : ImmutableList.of(value);
  }

  /**
   * Derives a display name from a key id for keys absent from the host index. Mirrors the namespace
   * derivation the index builder applies to discovered host properties.
   */
  private static String deriveDisplayName(String keyId) {
    int separator = keyId.indexOf("::");
    String namespace = separator >= 0 ? keyId.substring(0, separator) : "";
    String name = separator >= 0 ? keyId.substring(separator + 2) : keyId;
    return switch (namespace) {
      case "dim" -> "Dimension " + name;
      case "prop" -> "Host Property " + name;
      default -> name;
    };
  }
}
