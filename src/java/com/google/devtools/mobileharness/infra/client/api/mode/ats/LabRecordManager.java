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

package com.google.devtools.mobileharness.infra.client.api.mode.ats;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceScheduleUnit;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabRecordProto.DeviceRecord;
import com.google.devtools.mobileharness.api.query.proto.LabRecordProto.LabRecord;
import com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.protobuf.Message;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;

/** The lab record manager. */
@Singleton
class LabRecordManager {

  private static final Duration MISSING_DELAY = Duration.ofMinutes(10L);

  private final Map<String, RecordHistory<LabRecordData, LabRecord>> labHistories =
      new ConcurrentHashMap<>();
  private final Map<String, RecordHistory<DeviceRecordData, DeviceRecord>> deviceHistories =
      new ConcurrentHashMap<>();
  private final Clock clock;

  private final ListeningScheduledExecutorService listeningScheduledExecutorService;

  @Inject
  LabRecordManager(
      Clock clock, ListeningScheduledExecutorService listeningScheduledExecutorService) {
    this.clock = clock;
    this.listeningScheduledExecutorService = listeningScheduledExecutorService;
  }

  void start() {
    MoreFutures.logFailure(
        listeningScheduledExecutorService.scheduleWithFixedDelay(
            this::addLabRecordWhenBecomeMissing, Duration.ofMinutes(1), Duration.ofMinutes(1)),
        Level.WARNING,
        "Failed to start the task to monitor and record missing labs.");
    MoreFutures.logFailure(
        listeningScheduledExecutorService.scheduleWithFixedDelay(
            this::addDeviceRecordWhenBecomeMissing, Duration.ofMinutes(1), Duration.ofMinutes(1)),
        Level.WARNING,
        "Failed to start the task to monitor and record missing labs.");
  }

  /** Adds the lab record if important lab info is changed. */
  void addLabRecordIfLabInfoChanged(LabRecordData labRecordData) {
    String hostName = labRecordData.labLocator().hostName();
    RecordHistory<LabRecordData, LabRecord> labHistory =
        labHistories.computeIfAbsent(hostName, host -> new RecordHistory<>(hostName, clock));
    labHistory.addRecordIfInfoChanged(labRecordData);
  }

  /** Adds the device record if important device info is changed. */
  void addDeviceRecordIfDeviceInfoChanged(DeviceRecordData deviceRecordData) {
    String deviceUuid = deviceRecordData.deviceUuid();
    RecordHistory<DeviceRecordData, DeviceRecord> deviceHistory =
        deviceHistories.computeIfAbsent(deviceUuid, uuid -> new RecordHistory<>(uuid, clock));
    deviceHistory.addRecordIfInfoChanged(deviceRecordData);
  }

  /**
   * Returns the lab records of given host. If the host name is empty, return all the lab records.
   */
  ImmutableList<LabRecord> getLabRecords(String hostName) {
    if (hostName.isEmpty()) {
      return labHistories.values().stream()
          .flatMap(labHistory -> labHistory.getRecords().stream())
          .collect(toImmutableList());
    } else {
      RecordHistory<LabRecordData, LabRecord> labHistory = labHistories.get(hostName);
      return labHistory == null ? ImmutableList.of() : labHistory.getRecords();
    }
  }

  /**
   * Returns the device records of given device. If the device id is empty, return all the device
   * records.
   */
  ImmutableList<DeviceRecord> getDeviceRecords(String deviceUuid) {
    if (deviceUuid.isEmpty()) {
      return deviceHistories.values().stream()
          .flatMap(deviceHistory -> deviceHistory.getRecords().stream())
          .collect(toImmutableList());
    } else {
      RecordHistory<DeviceRecordData, DeviceRecord> deviceHistory = deviceHistories.get(deviceUuid);
      return deviceHistory == null ? ImmutableList.of() : deviceHistory.getRecords();
    }
  }

  @VisibleForTesting
  void addLabRecordWhenBecomeMissing() {
    for (RecordHistory<LabRecordData, LabRecord> labHistory : labHistories.values()) {
      labHistory.addRecordWhenBecomeMissing();
    }
  }

  @VisibleForTesting
  void addDeviceRecordWhenBecomeMissing() {
    for (RecordHistory<DeviceRecordData, DeviceRecord> deviceHistory : deviceHistories.values()) {
      deviceHistory.addRecordWhenBecomeMissing();
    }
  }

  private static class RecordHistory<
      RecordDataT extends RecordData<RecordT, RecordDataT>, RecordT extends Message> {
    private static final int RECORD_MAX_SIZE = 100;

    @SuppressWarnings("unused")
    private final String id;

    private final Clock clock;

    @GuardedBy("recordQueue")
    private Instant lastUpdateTime;

    @GuardedBy("itself")
    private final EvictingQueue<RecordDataT> recordQueue;

    private RecordHistory(String id, Clock clock) {
      this.id = id;
      this.clock = clock;
      recordQueue = EvictingQueue.create(RECORD_MAX_SIZE);
      lastUpdateTime = clock.instant();
    }

    private ImmutableList<RecordT> getRecords() {
      synchronized (recordQueue) {
        return ImmutableList.copyOf(recordQueue).stream()
            .map(RecordData::toRecordProto)
            .collect(toImmutableList());
      }
    }

    private void addRecordIfInfoChanged(RecordDataT record) {
      synchronized (recordQueue) {
        lastUpdateTime = clock.instant();
        RecordDataT lastRecord = recordQueue.peek();
        if (lastRecord == null || !record.isImportantInfoEqual(lastRecord)) {
          internalAddRecord(record);
        }
      }
    }

    private void addRecordWhenBecomeMissing() {
      synchronized (recordQueue) {
        RecordDataT lastRecord = recordQueue.peek();
        if (lastRecord == null) {
          return;
        }
        Instant now = clock.instant();
        if (now.isAfter(lastUpdateTime.plus(MISSING_DELAY)) && !lastRecord.isMissing()) {
          RecordDataT newRecord = lastRecord.copyToMissingRecord(now);
          internalAddRecord(newRecord);
        }
      }
    }

    @GuardedBy("recordQueue")
    private void internalAddRecord(RecordDataT info) {
      recordQueue.offer(info);
    }
  }

  interface RecordData<RecordT extends Message, RecordDataT> {
    /** Gets the protobuf message of this record. */
    RecordT toRecordProto();

    /** Checks whether the XXXInfo's status is MISSING. */
    boolean isMissing();

    /** Checks whether two RecordDatas' important infos are equal. */
    boolean isImportantInfoEqual(RecordDataT another);

    /** Copies the current recordData to a new object and set its status as MISSING. */
    RecordDataT copyToMissingRecord(Instant timestamp);
  }

  /** Lab record data. */
  @AutoValue
  abstract static class LabRecordData implements RecordData<LabRecord, LabRecordData> {
    static LabRecordData create(
        Instant timestamp,
        LabLocator labLocator,
        LabServerSetting labServerSetting,
        LabServerFeature labServerFeature,
        LabStatus labStatus) {
      return new AutoValue_LabRecordManager_LabRecordData(
          timestamp, labLocator, labServerSetting, labServerFeature, labStatus);
    }

    abstract Instant timestamp();

    abstract LabLocator labLocator();

    abstract LabServerSetting labServerSetting();

    abstract LabServerFeature labServerFeature();

    abstract LabStatus labStatus();

    @Memoized
    @Override
    public LabRecord toRecordProto() {
      return LabRecord.newBuilder()
          .setTimestamp(TimeUtils.toProtoTimestamp(timestamp()))
          .setLabInfo(
              LabInfo.newBuilder()
                  .setLabLocator(labLocator().toProto())
                  .setLabServerSetting(labServerSetting())
                  .setLabServerFeature(labServerFeature())
                  .setLabStatus(labStatus()))
          .build();
    }

    @Memoized
    @Override
    public boolean isMissing() {
      return labStatus().equals(LabStatus.LAB_MISSING);
    }

    @Override
    public boolean isImportantInfoEqual(LabRecordData another) {
      if (!labStatus().equals(another.labStatus())) {
        return false;
      }
      return labServerFeature().equals(another.labServerFeature());
    }

    @Override
    public LabRecordData copyToMissingRecord(Instant timestamp) {
      return create(
          timestamp, labLocator(), labServerSetting(), labServerFeature(), LabStatus.LAB_MISSING);
    }
  }

  /** Device record data. */
  @AutoValue
  abstract static class DeviceRecordData implements RecordData<DeviceRecord, DeviceRecordData> {
    static DeviceRecordData create(
        Instant timestamp,
        DeviceLocator deviceLocator,
        String deviceUuid,
        DeviceScheduleUnit deviceScheduleUnit,
        DeviceStatus deviceStatus) {
      return new AutoValue_LabRecordManager_DeviceRecordData(
          timestamp, deviceLocator, deviceUuid, deviceScheduleUnit, deviceStatus);
    }

    abstract Instant timestamp();

    abstract DeviceLocator deviceLocator();

    abstract String deviceUuid();

    abstract DeviceScheduleUnit deviceScheduleUnit();

    abstract DeviceStatus deviceStatus();

    @Memoized
    @Override
    public DeviceRecord toRecordProto() {
      return DeviceRecord.newBuilder()
          .setTimestamp(TimeUtils.toProtoTimestamp(timestamp()))
          .setDeviceInfo(
              LabQueryProto.DeviceInfo.newBuilder()
                  .setDeviceLocator(deviceLocator().toProto())
                  .setDeviceUuid(deviceUuid())
                  .setDeviceStatus(deviceStatus())
                  .setDeviceFeature(deviceScheduleUnit().toFeature()))
          .build();
    }

    @Memoized
    @Override
    public boolean isMissing() {
      return deviceStatus().equals(DeviceStatus.MISSING);
    }

    @Override
    public boolean isImportantInfoEqual(DeviceRecordData another) {
      return deviceStatus().equals(another.deviceStatus())
          && deviceScheduleUnit().owners().equals(deviceScheduleUnit().owners());
    }

    @Override
    public DeviceRecordData copyToMissingRecord(Instant timestamp) {
      return create(
          timestamp, deviceLocator(), deviceUuid(), deviceScheduleUnit(), DeviceStatus.MISSING);
    }
  }
}
