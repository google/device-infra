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

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.testing.TestingExecutors;
import com.google.devtools.mobileharness.api.model.lab.DeviceScheduleUnit;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabRecordProto.DeviceRecord;
import com.google.devtools.mobileharness.api.query.proto.LabRecordProto.LabRecord;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.LabRecordManager.DeviceRecordData;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.LabRecordManager.LabRecordData;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.Timestamp;
import java.time.Clock;
import java.time.Instant;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class LabRecordManagerTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Bind @Mock private Clock clock;
  @Bind private ListeningScheduledExecutorService listeningScheduledExecutorService;

  @Inject private LabRecordManager labRecordManager;

  @Before
  public void setUp() {
    listeningScheduledExecutorService = TestingExecutors.sameThreadScheduledExecutor();
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(clock.instant()).thenReturn(Instant.EPOCH);
  }

  @Test
  public void addLabRecordWhenLabInfoChanged() {
    assertThat(labRecordManager.getLabRecords("host_name")).isEmpty();

    LabInfo labInfo1 =
        LabInfo.newBuilder()
            .setLabLocator(LabLocator.newBuilder().setHostName("host_name").build())
            .setLabStatus(LabStatus.LAB_RUNNING)
            .setLabServerSetting(LabServerSetting.getDefaultInstance())
            .setLabServerFeature(
                LabServerFeature.newBuilder()
                    .setHostProperties(
                        HostProperties.newBuilder()
                            .addHostProperty(
                                HostProperty.newBuilder()
                                    .setKey("sdk_version")
                                    .setValue("30")
                                    .build())
                            .build())
                    .build())
            .build();
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1));
    labRecordManager.addLabRecordIfLabInfoChanged(
        LabRecordData.create(
            Instant.ofEpochMilli(1),
            com.google.devtools.mobileharness.api.model.lab.LabLocator.of(labInfo1.getLabLocator()),
            labInfo1.getLabServerSetting(),
            labInfo1.getLabServerFeature(),
            labInfo1.getLabStatus()));
    assertThat(labRecordManager.getLabRecords("host_name"))
        .containsExactly(
            LabRecord.newBuilder()
                .setLabInfo(labInfo1)
                .setTimestamp(Timestamp.newBuilder().setNanos(1000000).build())
                .build());

    LabInfo labInfo2 =
        LabInfo.newBuilder()
            .setLabLocator(LabLocator.newBuilder().setHostName("host_name").build())
            .setLabStatus(LabStatus.LAB_RUNNING)
            .setLabServerSetting(LabServerSetting.getDefaultInstance())
            .setLabServerFeature(
                LabServerFeature.newBuilder()
                    .setHostProperties(
                        HostProperties.newBuilder()
                            .addHostProperty(
                                HostProperty.newBuilder()
                                    .setKey("sdk_version")
                                    .setValue("31")
                                    .build())
                            .build())
                    .build())
            .build();
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(2));
    labRecordManager.addLabRecordIfLabInfoChanged(
        LabRecordData.create(
            Instant.ofEpochMilli(2),
            com.google.devtools.mobileharness.api.model.lab.LabLocator.of(labInfo2.getLabLocator()),
            labInfo2.getLabServerSetting(),
            labInfo2.getLabServerFeature(),
            labInfo2.getLabStatus()));

    assertThat(labRecordManager.getLabRecords("host_name"))
        .containsExactly(
            LabRecord.newBuilder()
                .setLabInfo(labInfo1)
                .setTimestamp(Timestamp.newBuilder().setNanos(1000000).build())
                .build(),
            LabRecord.newBuilder()
                .setLabInfo(labInfo2)
                .setTimestamp(Timestamp.newBuilder().setNanos(2000000).build())
                .build());
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(3));
    labRecordManager.addLabRecordIfLabInfoChanged(
        LabRecordData.create(
            Instant.ofEpochMilli(3),
            com.google.devtools.mobileharness.api.model.lab.LabLocator.of(labInfo1.getLabLocator()),
            labInfo1.getLabServerSetting(),
            labInfo1.getLabServerFeature(),
            labInfo1.getLabStatus()));
    assertThat(labRecordManager.getLabRecords("host_name"))
        .containsExactly(
            LabRecord.newBuilder()
                .setLabInfo(labInfo1)
                .setTimestamp(Timestamp.newBuilder().setNanos(1000000).build())
                .build(),
            LabRecord.newBuilder()
                .setLabInfo(labInfo2)
                .setTimestamp(Timestamp.newBuilder().setNanos(2000000).build())
                .build());
  }

  @Test
  public void addLabRecordWhenBecomeMissing() {
    LabInfo labInfo1 =
        LabInfo.newBuilder()
            .setLabLocator(LabLocator.newBuilder().setHostName("host_name").build())
            .setLabStatus(LabStatus.LAB_RUNNING)
            .setLabServerSetting(LabServerSetting.getDefaultInstance())
            .setLabServerFeature(
                LabServerFeature.newBuilder()
                    .setHostProperties(
                        HostProperties.newBuilder()
                            .addHostProperty(
                                HostProperty.newBuilder()
                                    .setKey("sdk_version")
                                    .setValue("30")
                                    .build())
                            .build())
                    .build())
            .build();
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1));
    labRecordManager.addLabRecordIfLabInfoChanged(
        LabRecordData.create(
            Instant.ofEpochMilli(1),
            com.google.devtools.mobileharness.api.model.lab.LabLocator.of(labInfo1.getLabLocator()),
            labInfo1.getLabServerSetting(),
            labInfo1.getLabServerFeature(),
            labInfo1.getLabStatus()));
    assertThat(labRecordManager.getLabRecords("host_name"))
        .containsExactly(
            LabRecord.newBuilder()
                .setLabInfo(labInfo1)
                .setTimestamp(Timestamp.newBuilder().setNanos(1000000).build())
                .build());

    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1000 * 60 * 11)); // 11 mins
    labRecordManager.addLabRecordWhenBecomeMissing();

    assertThat(labRecordManager.getLabRecords("host_name"))
        .containsExactly(
            LabRecord.newBuilder()
                .setLabInfo(labInfo1)
                .setTimestamp(Timestamp.newBuilder().setNanos(1000000).build())
                .build(),
            LabRecord.newBuilder()
                .setLabInfo(labInfo1.toBuilder().setLabStatus(LabStatus.LAB_MISSING).build())
                .setTimestamp(TimeUtils.toProtoTimestamp(Instant.ofEpochSecond(660L)))
                .build());
  }

  @Test
  public void addDeviceRecordWhenLabInfoChanged() {
    assertThat(labRecordManager.getDeviceRecords("device_uuid")).isEmpty();

    DeviceInfo deviceInfo1 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(
                DeviceLocator.newBuilder()
                    .setId("device_uuid")
                    .setLabLocator(LabLocator.getDefaultInstance())
                    .build())
            .setDeviceStatus(DeviceStatus.BUSY)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("AndroidRealDevice")
                    .setCompositeDimension(DeviceCompositeDimension.getDefaultInstance()))
            .build();
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1));
    labRecordManager.addDeviceRecordIfDeviceInfoChanged(
        DeviceRecordData.create(
            Instant.ofEpochMilli(1),
            com.google.devtools.mobileharness.api.model.lab.DeviceLocator.of(
                deviceInfo1.getDeviceLocator()),
            deviceInfo1.getDeviceLocator().getId(),
            new DeviceScheduleUnit(
                    com.google.devtools.mobileharness.api.model.lab.DeviceLocator.of(
                        deviceInfo1.getDeviceLocator()))
                .addFeature(deviceInfo1.getDeviceFeature()),
            deviceInfo1.getDeviceStatus()));
    assertThat(labRecordManager.getDeviceRecords("device_uuid"))
        .containsExactly(
            DeviceRecord.newBuilder()
                .setDeviceInfo(deviceInfo1)
                .setTimestamp(Timestamp.newBuilder().setNanos(1000000).build())
                .build());

    DeviceInfo deviceInfo2 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(
                DeviceLocator.newBuilder()
                    .setId("device_uuid")
                    .setLabLocator(LabLocator.getDefaultInstance())
                    .build())
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("AndroidRealDevice")
                    .setCompositeDimension(DeviceCompositeDimension.getDefaultInstance()))
            .build();
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(2));
    labRecordManager.addDeviceRecordIfDeviceInfoChanged(
        DeviceRecordData.create(
            Instant.ofEpochMilli(2),
            com.google.devtools.mobileharness.api.model.lab.DeviceLocator.of(
                deviceInfo2.getDeviceLocator()),
            deviceInfo2.getDeviceLocator().getId(),
            new DeviceScheduleUnit(
                    com.google.devtools.mobileharness.api.model.lab.DeviceLocator.of(
                        deviceInfo2.getDeviceLocator()))
                .addFeature(deviceInfo2.getDeviceFeature()),
            deviceInfo2.getDeviceStatus()));
    assertThat(labRecordManager.getDeviceRecords("device_uuid"))
        .containsExactly(
            DeviceRecord.newBuilder()
                .setDeviceInfo(deviceInfo1)
                .setTimestamp(Timestamp.newBuilder().setNanos(1000000).build())
                .build(),
            DeviceRecord.newBuilder()
                .setDeviceInfo(deviceInfo2)
                .setTimestamp(Timestamp.newBuilder().setNanos(2000000).build())
                .build());
  }

  @Test
  public void addDeviceRecordWhenBecomeMissing() {
    DeviceInfo deviceInfo1 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(
                DeviceLocator.newBuilder()
                    .setId("device_uuid")
                    .setLabLocator(LabLocator.getDefaultInstance())
                    .build())
            .setDeviceStatus(DeviceStatus.BUSY)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("AndroidRealDevice")
                    .setCompositeDimension(DeviceCompositeDimension.getDefaultInstance()))
            .build();
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1));

    labRecordManager.addDeviceRecordIfDeviceInfoChanged(
        DeviceRecordData.create(
            Instant.ofEpochMilli(1),
            com.google.devtools.mobileharness.api.model.lab.DeviceLocator.of(
                deviceInfo1.getDeviceLocator()),
            deviceInfo1.getDeviceLocator().getId(),
            new DeviceScheduleUnit(
                    com.google.devtools.mobileharness.api.model.lab.DeviceLocator.of(
                        deviceInfo1.getDeviceLocator()))
                .addFeature(deviceInfo1.getDeviceFeature()),
            deviceInfo1.getDeviceStatus()));
    assertThat(labRecordManager.getDeviceRecords("device_uuid"))
        .containsExactly(
            DeviceRecord.newBuilder()
                .setDeviceInfo(deviceInfo1)
                .setTimestamp(Timestamp.newBuilder().setNanos(1000000).build())
                .build());

    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1000 * 60 * 11)); // 11 mins
    labRecordManager.addDeviceRecordWhenBecomeMissing();

    DeviceInfo deviceInfo2 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(
                DeviceLocator.newBuilder()
                    .setId("device_uuid")
                    .setLabLocator(LabLocator.getDefaultInstance())
                    .build())
            .setDeviceStatus(DeviceStatus.MISSING)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("AndroidRealDevice")
                    .setCompositeDimension(DeviceCompositeDimension.getDefaultInstance()))
            .build();
    assertThat(labRecordManager.getDeviceRecords("device_uuid"))
        .containsExactly(
            DeviceRecord.newBuilder()
                .setDeviceInfo(deviceInfo1)
                .setTimestamp(Timestamp.newBuilder().setNanos(1000000).build())
                .build(),
            DeviceRecord.newBuilder()
                .setDeviceInfo(deviceInfo2)
                .setTimestamp(TimeUtils.toProtoTimestamp(Instant.ofEpochSecond(660L)))
                .build());
  }
}
