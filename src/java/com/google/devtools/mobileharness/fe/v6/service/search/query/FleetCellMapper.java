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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.DeviceRef;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.HostRef;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Indicator;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.LinkCell;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.NavTarget;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Row;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.StatusCell;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TextCell;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DeviceRecord;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import java.util.List;
import javax.inject.Inject;

/** Turns indexed device records into result-table {@link Column}s and typed {@link Cell}s. */
public final class FleetCellMapper {

  /** Device status name to semantic indicator. */
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

  /** Builds the header for a column key. */
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
    if (keyId.equals(FleetSearchKeys.FIELD_UUID)) {
      return Cell.newBuilder().setLink(deviceLink(device)).build();
    }
    if (keyId.equals(FleetSearchKeys.HOST_NAME)) {
      return Cell.newBuilder().setLink(hostLink(device)).build();
    }
    if (keyId.equals(FleetSearchKeys.FIELD_STATUS)) {
      String status = firstValue(device.values(FleetSearchKeys.FIELD_STATUS));
      return Cell.newBuilder().setStatus(statusCell(status)).build();
    }
    return Cell.newBuilder().setText(textCell(displayValues(device, keyId, snapshot))).build();
  }

  private static LinkCell deviceLink(DeviceRecord device) {
    String hostName = firstValue(device.values(FleetSearchKeys.HOST_NAME));
    String hostIp = firstValue(device.values(FleetSearchKeys.HOST_IP));
    return LinkCell.newBuilder()
        .setText(device.deviceId())
        .setTarget(
            NavTarget.newBuilder()
                .setDevice(
                    DeviceRef.newBuilder()
                        .setId(device.deviceId())
                        .setHostName(hostName)
                        .setHostIp(hostIp)))
        .build();
  }

  private static LinkCell hostLink(DeviceRecord device) {
    String hostName = firstValue(device.values(FleetSearchKeys.HOST_NAME));
    String hostIp = firstValue(device.values(FleetSearchKeys.HOST_IP));
    return LinkCell.newBuilder()
        .setText(hostName)
        .setTarget(
            NavTarget.newBuilder()
                .setHost(HostRef.newBuilder().setHostName(hostName).setHostIp(hostIp)))
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
    if (keyId.equals(FleetSearchKeys.HOST_ATS_CONTROLLER)) {
      return atsControllerValues(device, snapshot);
    }
    return device.values(keyId);
  }

  private static ImmutableList<String> atsControllerValues(
      DeviceRecord device, FleetSnapshot snapshot) {
    ImmutableList<String> vals = device.values(FleetSearchKeys.HOST_ATS_CONTROLLER);
    if (vals.isEmpty()) {
      return ImmutableList.of();
    }
    String id = vals.get(0);
    if (id.isEmpty()) {
      return ImmutableList.of();
    }
    String display =
        snapshot
            .index()
            .valueDisplays(FleetSearchKeys.HOST_ATS_CONTROLLER)
            .getOrDefault(Ascii.toLowerCase(id), id);
    return ImmutableList.of(display);
  }

  private static String firstValue(ImmutableList<String> list) {
    return Iterables.getFirst(list, "");
  }
}
