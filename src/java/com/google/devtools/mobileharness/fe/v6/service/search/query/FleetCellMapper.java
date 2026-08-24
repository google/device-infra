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

import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.CONFIG_WIFI_SSID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.DIM_PREFIX;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.DIM_QUARANTINED;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_DECORATOR;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_DRIVER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_EXECUTOR;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_OWNER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_UUID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_ATS_CONTROLLER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_CONNECTIVITY;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_DAEMON_STATUS;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.DeviceRef;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.HostRef;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Indicator;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.LinkCell;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.NavTarget;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Row;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.StatusCell;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TextCell;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DeviceRecord;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import java.util.List;
import javax.inject.Inject;

/**
 * Turns indexed device records into result-table {@link Column}s and typed {@link Cell}s.
 *
 * <p>This is the Java port of the search prototype's {@code _cell} and column builders, adapted to
 * the typed {@code Cell} oneof (TextCell, LinkCell, StatusCell). The backend decides the cell type
 * per column so the frontend renders generically via {@code switch(cell.kind)} without
 * entity-specific logic.
 *
 * <p>Value extraction mirrors the key namespaces recorded by {@code FleetIndexBuilder} and matched
 * by {@code FleetFilterEngine} ({@code field::}, {@code dim::}, {@code prop::}, {@code host::},
 * {@code config::}), but reads display-cased values straight from the {@link DeviceRecord} forward
 * store rather than the lowercased index terms.
 */
public final class FleetCellMapper {

  /**
   * Device status name to semantic indicator. The status text is uppercased before lookup. Any
   * status not listed here maps to {@link Indicator#INDICATOR_NEUTRAL}, which covers transitional
   * states such as NEW and PREPPING that carry no health judgment. This follows the adaptation
   * guide: OK is green, ACTIVE is blue (in-progress), ERROR is red, NEUTRAL is gray.
   */
  private static final ImmutableMap<String, Indicator> STATUS_INDICATORS =
      ImmutableMap.<String, Indicator>builder()
          .put("IDLE", Indicator.INDICATOR_OK)
          .put("BUSY", Indicator.INDICATOR_ACTIVE)
          .put("RUNNING", Indicator.INDICATOR_ACTIVE)
          .put("MISSING", Indicator.INDICATOR_ERROR)
          .put("FAILED", Indicator.INDICATOR_ERROR)
          .put("ERROR", Indicator.INDICATOR_ERROR)
          .buildOrThrow();

  @Inject
  FleetCellMapper() {}

  /**
   * Builds the header for a column key. The display name comes from the fleet index when the key is
   * present in the fleet, and falls back to a name derived from the key namespace otherwise.
   */
  public Column column(String keyId, FleetSnapshot snapshot) {
    String display = snapshot.index().displayName(keyId);
    return Column.newBuilder().setKey(keyId).setDisplayName(display).build();
  }

  /** Builds one result row for a device: its UUID as id, plus one cell per requested column key. */
  public Row row(DeviceRecord device, List<String> columnKeys, FleetSnapshot snapshot) {
    Row.Builder row = Row.newBuilder().setId(device.deviceId());
    for (String keyId : columnKeys) {
      row.addCells(cell(keyId, device, snapshot));
    }
    return row.build();
  }

  /** Builds a typed cell for a device and column key. */
  public Cell cell(String keyId, DeviceRecord device, FleetSnapshot snapshot) {
    return switch (keyId) {
      case FIELD_UUID -> Cell.newBuilder().setLink(deviceLink(device)).build();
      case HOST_NAME -> Cell.newBuilder().setLink(hostLink(device)).build();
      case FIELD_STATUS -> Cell.newBuilder().setStatus(statusCell(device.status())).build();
      // TODO: dim::quarantined renders as a plain "Yes"/"No" TextCell for now. It may become a
      // StatusCell (e.g. quarantined -> ERROR) once the frontend design settles.
      // TODO: multi-value tag keys (owners, types, labels, ...) are comma-joined into a single
      // TextCell here. Some may become ChipsCell later; the adaptation guide reserves ChipsCell for
      // standalone tags, so it is deliberately not used yet.
      default ->
          Cell.newBuilder().setText(textCell(displayValues(device, keyId, snapshot))).build();
    };
  }

  private static LinkCell deviceLink(DeviceRecord device) {
    return LinkCell.newBuilder()
        .setText(device.deviceId())
        .setTarget(
            NavTarget.newBuilder()
                .setDevice(
                    DeviceRef.newBuilder()
                        .setId(device.deviceId())
                        .setHostName(device.hostName())
                        .setHostIp(device.hostIp())))
        .build();
  }

  private static LinkCell hostLink(DeviceRecord device) {
    return LinkCell.newBuilder()
        .setText(device.hostName())
        .setTarget(
            NavTarget.newBuilder()
                .setHost(
                    HostRef.newBuilder().setHostName(device.hostName()).setHostIp(device.hostIp())))
        .build();
  }

  private static StatusCell statusCell(String status) {
    Indicator indicator =
        STATUS_INDICATORS.getOrDefault(Ascii.toUpperCase(status), Indicator.INDICATOR_NEUTRAL);
    return StatusCell.newBuilder().setText(status).setIndicator(indicator).build();
  }

  private static TextCell textCell(ImmutableList<String> values) {
    return TextCell.newBuilder().setValue(String.join(", ", values)).build();
  }

  /**
   * The device's display-cased values for a key, in the same key-namespace scheme the index builder
   * uses. A single-valued key yields a one-element list (or an empty list when the value is
   * absent); a multi-valued key yields all its values. The searcher reuses this to derive sort
   * values.
   */
  static ImmutableList<String> displayValues(
      DeviceRecord device, String keyId, FleetSnapshot snapshot) {
    return switch (keyId) {
      case FIELD_UUID -> singleton(device.deviceId());
      case FIELD_STATUS -> singleton(device.status());
      case FIELD_TYPE -> device.types();
      case FIELD_OWNER -> device.owners();
      case FIELD_DRIVER -> device.drivers();
      case FIELD_DECORATOR -> device.decorators();
      case FIELD_EXECUTOR -> device.executors();
      case DIM_QUARANTINED -> ImmutableList.of(device.quarantined() ? "Yes" : "No");
      case HOST_NAME -> singleton(device.hostName());
      case HOST_IP -> singleton(device.hostIp());
      case CONFIG_WIFI_SSID -> device.wifiSsid().map(ImmutableList::of).orElse(ImmutableList.of());
      // The device stores the raw controller id; show the friendly display from the index's
      // per-value display map, falling back to the id itself when the registry has no entry.
      case HOST_ATS_CONTROLLER -> atsControllerValues(device, snapshot);
      // Cross-entity host attributes stamped onto each device. These mirror the value sets recorded
      // by DeviceValueExtractor.valuesForKey: lab type is multi valued, the rest single valued,
      // with
      // the optionals collapsing to an empty list when absent so the column renders blank.
      case HOST_LAB_TYPE -> device.labTypes();
      case HOST_OS -> singleton(device.hostOs());
      case HOST_CONNECTIVITY -> singleton(device.hostConnectivity());
      case HOST_DAEMON_STATUS ->
          device.daemonStatus().map(ImmutableList::of).orElse(ImmutableList.of());
      case HOST_RELEASE_STATUS ->
          device.releaseStatus().map(ImmutableList::of).orElse(ImmutableList.of());
      case HOST_RELEASE_TYPE ->
          device.releaseType().map(ImmutableList::of).orElse(ImmutableList.of());
      case HOST_LAB_SERVER_VERSION ->
          device.labServerVersion().map(ImmutableList::of).orElse(ImmutableList.of());
      default -> prefixedValues(device, keyId);
    };
  }

  private static ImmutableList<String> atsControllerValues(
      DeviceRecord device, FleetSnapshot snapshot) {
    return device
        .atsController()
        .filter(id -> !id.isEmpty())
        .map(
            id ->
                ImmutableList.of(
                    snapshot
                        .index()
                        .valueDisplays(HOST_ATS_CONTROLLER)
                        .getOrDefault(Ascii.toLowerCase(id), id)))
        .orElse(ImmutableList.of());
  }

  private static ImmutableList<String> prefixedValues(DeviceRecord device, String keyId) {
    if (keyId.startsWith(DIM_PREFIX)) {
      return device
          .dimensions()
          .getOrDefault(keyId.substring(DIM_PREFIX.length()), ImmutableList.of());
    }
    if (keyId.startsWith(PROP_PREFIX)) {
      String value = device.hostProperties().get(keyId.substring(PROP_PREFIX.length()));
      return value == null ? ImmutableList.of() : singleton(value);
    }
    return ImmutableList.of();
  }

  private static ImmutableList<String> singleton(String value) {
    return value.isEmpty() ? ImmutableList.of() : ImmutableList.of(value);
  }
}
