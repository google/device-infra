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
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Cell;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Column;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetUtilization;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DeviceRecord;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DeviceValueExtractor;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.Postings;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * The device projection of a {@link FleetSnapshot} for the query classes.
 *
 * <p>Records are the snapshot's devices, identified by device UUID. The value projection delegates
 * to {@link DeviceValueExtractor} (lowercased sets) and {@link FleetCellMapper} (display values,
 * headers, typed cells), so it mirrors exactly what the device index builder recorded. Utilization
 * is the device idle / busy / other bucketing, so a device group carries a utilization breakdown.
 */
public final class DeviceCorpus implements SearchCorpus {

  /**
   * Device type keywords that mark a device as recovering rather than serving. A device reporting
   * IDLE or BUSY while carrying one of these types is counted under "other" utilization. Ported
   * from the prototype's {@code _ABNORMAL_TYPE_KEYWORDS}.
   */
  private static final ImmutableSet<String> ABNORMAL_TYPE_KEYWORDS =
      ImmutableSet.of(
          "FAILED",
          "ABNORMAL",
          "DISCONNECTED",
          "OFFLINE",
          "UNAUTHORIZED",
          "FASTBOOT",
          "FASTBOOTDMODE");

  private final FleetSnapshot snapshot;
  private final Postings postings;
  @Nullable private final ScenarioCuration curation;
  private final FleetCellMapper cellMapper = new FleetCellMapper();

  public DeviceCorpus(
      FleetSnapshot snapshot, Postings postings, @Nullable ScenarioCuration curation) {
    this.snapshot = snapshot;
    this.postings = postings;
    this.curation = curation;
  }

  @Override
  public FleetIndex index() {
    return snapshot.index();
  }

  @Override
  public Postings postings() {
    return postings;
  }

  @Override
  public int recordCount() {
    return snapshot.deviceCount();
  }

  @Override
  public String recordId(int index) {
    return snapshot.devices().get(index).deviceId();
  }

  @Override
  public SearchEntity entity() {
    return SearchEntity.SEARCH_ENTITY_DEVICE;
  }

  @Override
  public String identifierKey() {
    return FleetSearchKeys.FIELD_UUID;
  }

  @Override
  public boolean plainValueKey(String keyId) {
    return FleetSearchKeys.PLAIN_VALUE_KEYS.contains(keyId);
  }

  @Override
  public ImmutableSet<String> valuesForKey(int index, String keyId) {
    return DeviceValueExtractor.valuesForKey(snapshot.devices().get(index), keyId);
  }

  @Override
  public ImmutableList<String> displayValues(int index, String keyId) {
    return FleetCellMapper.displayValues(snapshot.devices().get(index), keyId, snapshot);
  }

  @Override
  public Column column(String keyId) {
    return cellMapper.column(keyId, snapshot);
  }

  @Override
  public Cell cell(int index, String keyId) {
    return cellMapper.cell(keyId, snapshot.devices().get(index), snapshot);
  }

  /**
   * The three utilization counts for one group, collapsing each device to idle, busy, or other.
   * Ported from the prototype's {@code _utilization} plus {@code _util_bucket}. Counts are
   * returned, not percentages, because the frontend renders a bar and thrice-rounded percentages no
   * longer sum to the total.
   */
  @Override
  public Optional<FleetUtilization> utilization(List<Integer> memberIndices) {
    ImmutableList<DeviceRecord> devices = snapshot.devices();
    int idle = 0;
    int busy = 0;
    for (int deviceIndex : memberIndices) {
      switch (utilBucket(devices.get(deviceIndex))) {
        case IDLE -> idle++;
        case BUSY -> busy++;
        case OTHER -> {}
      }
    }
    int total = memberIndices.size();
    return Optional.of(
        FleetUtilization.newBuilder()
            .setIdle(idle)
            .setBusy(busy)
            .setOther(total - idle - busy)
            .setTotal(total)
            .build());
  }

  @Override
  @Nullable
  public ScenarioCuration curation() {
    return curation;
  }

  /**
   * Which utilization bucket a device is in. Serving requires more than a status: a device
   * reporting IDLE or BUSY while carrying an abnormal type is recovering, not working, and one with
   * no type at all cannot run anything. Quarantined-and-idle is held back deliberately, so it is
   * not available capacity however idle it looks.
   */
  private static UtilBucket utilBucket(DeviceRecord device) {
    String status = Ascii.toUpperCase(device.status());
    if ((!status.equals("IDLE") && !status.equals("BUSY")) || device.types().isEmpty()) {
      return UtilBucket.OTHER;
    }
    for (String type : device.types()) {
      String upper = Ascii.toUpperCase(type);
      for (String keyword : ABNORMAL_TYPE_KEYWORDS) {
        if (upper.contains(keyword)) {
          return UtilBucket.OTHER;
        }
      }
    }
    if (status.equals("IDLE")) {
      return device.quarantined() ? UtilBucket.OTHER : UtilBucket.IDLE;
    }
    return UtilBucket.BUSY;
  }

  /** Utilization bucket a device falls into within a group. */
  private enum UtilBucket {
    IDLE,
    BUSY,
    OTHER
  }
}
