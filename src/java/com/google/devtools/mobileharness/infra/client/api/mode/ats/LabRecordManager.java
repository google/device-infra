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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabRecordProto.DeviceRecord;
import com.google.devtools.mobileharness.api.query.proto.LabRecordProto.LabRecord;
import com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
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

  private final Map<String, RecordHistory<LabRecord, LabInfo>> labHistories =
      new ConcurrentHashMap<>();
  private final Map<String, RecordHistory<DeviceRecord, DeviceInfo>> deviceHistories =
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
  void addLabRecordIfLabInfoChanged(LabInfo labInfo) {
    String hostName = labInfo.getLabLocator().getHostName();
    RecordHistory<LabRecord, LabInfo> labHistory =
        labHistories.computeIfAbsent(
            hostName, host -> new RecordHistory<>(host, clock, new LabInfoRecordAdapter()));
    labHistory.addRecordIfInfoChanged(labInfo);
  }

  /** Adds the device record if important device info is changed. */
  void addDeviceRecordIfDeviceInfoChanged(DeviceInfo deviceInfo) {
    String deviceUuid = deviceInfo.getDeviceUuid();
    RecordHistory<DeviceRecord, DeviceInfo> deviceHistory =
        deviceHistories.computeIfAbsent(
            deviceUuid,
            host -> new RecordHistory<>(deviceUuid, clock, new DeviceInfoRecordAdapter()));
    deviceHistory.addRecordIfInfoChanged(deviceInfo);
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
      RecordHistory<LabRecord, LabInfo> labHistory = labHistories.get(hostName);
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
      RecordHistory<DeviceRecord, DeviceInfo> deviceHistory = deviceHistories.get(deviceUuid);
      return deviceHistory == null ? ImmutableList.of() : deviceHistory.getRecords();
    }
  }

  @VisibleForTesting
  void addLabRecordWhenBecomeMissing() {
    for (RecordHistory<LabRecord, LabInfo> labHistory : labHistories.values()) {
      labHistory.addRecordWhenBecomeMissing();
    }
  }

  @VisibleForTesting
  void addDeviceRecordWhenBecomeMissing() {
    for (RecordHistory<DeviceRecord, DeviceInfo> deviceHistory : deviceHistories.values()) {
      deviceHistory.addRecordWhenBecomeMissing();
    }
  }

  private static class RecordHistory<RecordT extends Message, InfoT extends Message> {
    private static final int RECORD_MAX_SIZE = 100;

    @SuppressWarnings("unused")
    private final String id;

    private final Clock clock;

    @GuardedBy("itself")
    private final EvictingQueue<RecordT> recordQueue;

    private final InfoRecordAdapter<RecordT, InfoT> infoRecordAdapter;

    private RecordHistory(
        String id, Clock clock, InfoRecordAdapter<RecordT, InfoT> infoRecordAdapter) {
      this.id = id;
      this.clock = clock;
      this.infoRecordAdapter = infoRecordAdapter;
      recordQueue = EvictingQueue.create(RECORD_MAX_SIZE);
    }

    private boolean isInfoChanged(InfoT previousInfo, InfoT newInfo) {
      if (!infoRecordAdapter.getStatus(previousInfo).equals(infoRecordAdapter.getStatus(newInfo))) {
        return true;
      }
      return !infoRecordAdapter
          .getImportantFeature(previousInfo)
          .equals(infoRecordAdapter.getImportantFeature(newInfo));
    }

    private ImmutableList<RecordT> getRecords() {
      synchronized (recordQueue) {
        return ImmutableList.copyOf(recordQueue);
      }
    }

    private void addRecordIfInfoChanged(InfoT info) {
      synchronized (recordQueue) {
        RecordT lastRecord = recordQueue.peek();
        if (lastRecord == null
            || isInfoChanged(infoRecordAdapter.getInfoFromRecord(lastRecord), info)) {
          internalAddRecord(info);
        }
      }
    }

    private void addRecordWhenBecomeMissing() {
      synchronized (recordQueue) {
        RecordT lastRecord = recordQueue.peek();
        if (lastRecord == null) {
          return;
        }
        Instant lastUpdateTime =
            TimeUtils.toJavaInstant(infoRecordAdapter.getTimestamp(lastRecord));
        Instant now = clock.instant();
        if (now.isAfter(lastUpdateTime.plus(MISSING_DELAY))
            && !infoRecordAdapter.isMissing(infoRecordAdapter.getInfoFromRecord(lastRecord))) {
          InfoT newInfo = infoRecordAdapter.getInfoFromRecord(lastRecord);
          newInfo = infoRecordAdapter.setMissing(newInfo);
          internalAddRecord(newInfo);
        }
      }
    }

    @GuardedBy("recordQueue")
    private void internalAddRecord(InfoT info) {
      recordQueue.offer(infoRecordAdapter.generateRecordFromInfo(info));
    }
  }

  /**
   * Adapter to process Device/LabRecord, Device/LabInfo protobuf message. So RecordHistory can
   * decouple from the detailed Record/Info type.
   */
  private interface InfoRecordAdapter<RecordT extends Message, InfoT extends Message> {

    /** Gets the XXXInfo message from the XXXRecord message. */
    InfoT getInfoFromRecord(RecordT record);

    /** Generates the XXXRecord message from XXXInfo message. */
    RecordT generateRecordFromInfo(InfoT info);

    /** Gets the status from XXXInfo as a string type. */
    String getStatus(InfoT info);

    /** Sets the status as MISSING in XXXInfo message. */
    InfoT setMissing(InfoT info);

    /** Checks whether the XXXInfo's status is MISSING. */
    boolean isMissing(InfoT info);

    /**
     * Get the feature field's important info from XXXInfo.
     *
     * <p>when the important features are changed, a new record should be added.
     */
    Message getImportantFeature(InfoT info);

    /** Gets the timestamp of one XXXRecord. */
    Timestamp getTimestamp(RecordT record);
  }

  /** The adapter for LabInfo and LabRecord. */
  private class LabInfoRecordAdapter implements InfoRecordAdapter<LabRecord, LabInfo> {

    @Override
    public LabInfo getInfoFromRecord(LabRecord record) {
      return record.getLabInfo();
    }

    @Override
    public LabRecord generateRecordFromInfo(LabInfo info) {
      return LabRecord.newBuilder()
          .setTimestamp(TimeUtils.toProtoTimestamp(clock.instant()))
          .setLabInfo(info)
          .build();
    }

    @Override
    public String getStatus(LabInfo info) {
      return info.getLabStatus().name();
    }

    @Override
    public LabInfo setMissing(LabInfo info) {
      return info.toBuilder().setLabStatus(LabStatus.LAB_MISSING).build();
    }

    @Override
    public boolean isMissing(LabInfo info) {
      return info.getLabStatus() == LabStatus.LAB_MISSING;
    }

    @Override
    public Message getImportantFeature(LabInfo info) {
      return info.getLabServerFeature();
    }

    @Override
    public Timestamp getTimestamp(LabRecord record) {
      return record.getTimestamp();
    }
  }

  /** The adapter for DeviceInfo and DeviceRecord. */
  private class DeviceInfoRecordAdapter implements InfoRecordAdapter<DeviceRecord, DeviceInfo> {

    @Override
    public DeviceInfo getInfoFromRecord(DeviceRecord record) {
      return record.getDeviceInfo();
    }

    @Override
    public DeviceRecord generateRecordFromInfo(DeviceInfo info) {
      return DeviceRecord.newBuilder()
          .setTimestamp(TimeUtils.toProtoTimestamp(clock.instant()))
          .setDeviceInfo(info)
          .build();
    }

    @Override
    public String getStatus(DeviceInfo info) {
      return info.getDeviceStatus().name();
    }

    @Override
    public DeviceInfo setMissing(DeviceInfo info) {
      return info.toBuilder().setDeviceStatus(DeviceStatus.MISSING).build();
    }

    @Override
    public boolean isMissing(DeviceInfo info) {
      return info.getDeviceStatus() == DeviceStatus.MISSING;
    }

    @Override
    public Message getImportantFeature(DeviceInfo info) {
      return info.getDeviceFeature();
    }

    @Override
    public Timestamp getTimestamp(DeviceRecord record) {
      return record.getTimestamp();
    }
  }
}
