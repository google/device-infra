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

package com.google.devtools.mobileharness.infra.master.central.model.lab;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Test.TestLocator;
import com.google.devtools.mobileharness.infra.master.central.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.infra.master.central.proto.Device.DeviceProfile;
import java.time.Duration;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DeviceConditionUtil}. */
@RunWith(JUnit4.class)
public class DeviceConditionUtilTest {

  private DeviceDao deviceDao;

  private DeviceLocator deviceLocator;

  @Before
  public void setUp() {
    deviceLocator = DeviceLocator.of("id", LabLocator.of("ip", "host"));
    deviceDao =
        DeviceDao.create(
            deviceLocator,
            DeviceProfile.getDefaultInstance(),
            DeviceCondition.getDefaultInstance());
  }

  @Test
  public void checkStatus_hasAllocation() {
    DeviceCondition deviceCondition =
        DeviceCondition.newBuilder()
            .setAllocatedTestLocator(TestLocator.getDefaultInstance())
            .setAllocationModifyTimeMsFromMaster(1L)
            .setDirty(false)
            .setDirtyModifyTimeMsFromMaster(2L)
            .setStatusFromLab(DeviceStatus.IDLE)
            .setStatusFromLabModifyTimeMsFromMaster(3L)
            .build();
    assertThat(DeviceConditionUtil.summarizeStatus(deviceCondition)).isEqualTo(DeviceStatus.BUSY);
    assertThat(DeviceConditionUtil.summarizeStatusModifyTime(deviceCondition))
        .isEqualTo(Instant.ofEpochMilli(1L));
    assertThat(DeviceConditionUtil.isIdle(deviceCondition)).isFalse();
    assertThat(DeviceConditionUtil.isReady(deviceCondition)).isTrue();
    assertThat(DeviceConditionUtil.isInHealthyStatus(deviceCondition)).isTrue();
  }

  @Test
  public void checkStatus_dirty() {
    DeviceCondition deviceCondition =
        DeviceCondition.newBuilder()
            .setAllocationModifyTimeMsFromMaster(1L)
            .setDirty(true)
            .setDirtyModifyTimeMsFromMaster(2L)
            .setStatusFromLab(DeviceStatus.IDLE)
            .setStatusFromLabModifyTimeMsFromMaster(3L)
            .build();
    assertThat(DeviceConditionUtil.summarizeStatus(deviceCondition)).isEqualTo(DeviceStatus.DIRTY);
    assertThat(DeviceConditionUtil.summarizeStatusModifyTime(deviceCondition))
        .isEqualTo(Instant.ofEpochMilli(2L));
    assertThat(DeviceConditionUtil.isIdle(deviceCondition)).isFalse();
    assertThat(DeviceConditionUtil.isReady(deviceCondition)).isFalse();
    assertThat(DeviceConditionUtil.isInHealthyStatus(deviceCondition)).isFalse();
  }

  @Test
  public void checkStatus_missing() {
    DeviceCondition deviceCondition =
        DeviceCondition.newBuilder()
            .setAllocationModifyTimeMsFromMaster(1L)
            .setDirty(true)
            .setDirtyModifyTimeMsFromMaster(2L)
            .setStatusFromLab(DeviceStatus.MISSING)
            .setStatusFromLabModifyTimeMsFromMaster(3L)
            .build();
    assertThat(DeviceConditionUtil.summarizeStatus(deviceCondition))
        .isEqualTo(DeviceStatus.MISSING);
    assertThat(DeviceConditionUtil.summarizeStatusModifyTime(deviceCondition))
        .isEqualTo(Instant.ofEpochMilli(2L));
    assertThat(DeviceConditionUtil.isIdle(deviceCondition)).isFalse();
    assertThat(DeviceConditionUtil.isReady(deviceCondition)).isFalse();
    assertThat(DeviceConditionUtil.isInHealthyStatus(deviceCondition)).isFalse();
  }

  @Test
  public void checkStatus_idle() {
    DeviceCondition deviceCondition =
        DeviceCondition.newBuilder()
            .setAllocationModifyTimeMsFromMaster(1L)
            .setDirty(false)
            .setDirtyModifyTimeMsFromMaster(2L)
            .setStatusFromLab(DeviceStatus.IDLE)
            .setStatusFromLabModifyTimeMsFromMaster(3L)
            .build();
    assertThat(DeviceConditionUtil.summarizeStatus(deviceCondition)).isEqualTo(DeviceStatus.IDLE);
    assertThat(DeviceConditionUtil.summarizeStatusModifyTime(deviceCondition))
        .isEqualTo(Instant.ofEpochMilli(3L));
    assertThat(DeviceConditionUtil.isIdle(deviceCondition)).isTrue();
    assertThat(DeviceConditionUtil.isReady(deviceCondition)).isTrue();
    assertThat(DeviceConditionUtil.isInHealthyStatus(deviceCondition)).isTrue();
  }

  @Test
  public void checkStatus_init() {
    DeviceCondition deviceCondition =
        DeviceCondition.newBuilder()
            .setAllocationModifyTimeMsFromMaster(5L)
            .setDirty(false)
            .setDirtyModifyTimeMsFromMaster(4L)
            .setStatusFromLab(DeviceStatus.INIT)
            .setStatusFromLabModifyTimeMsFromMaster(3L)
            .build();
    assertThat(DeviceConditionUtil.summarizeStatus(deviceCondition)).isEqualTo(DeviceStatus.INIT);
    assertThat(DeviceConditionUtil.summarizeStatusModifyTime(deviceCondition))
        .isEqualTo(Instant.ofEpochMilli(5L));
    assertThat(DeviceConditionUtil.isIdle(deviceCondition)).isFalse();
    assertThat(DeviceConditionUtil.isReady(deviceCondition)).isFalse();
    assertThat(DeviceConditionUtil.isInHealthyStatus(deviceCondition)).isFalse();
  }

  @Test
  public void mergeMnmDeviceStatusIfAny() {
    assertThat(
            DeviceConditionUtil.mergeMnmDeviceStatusIfAny(
                DeviceCondition.newBuilder()
                    .setStatusFromLab(DeviceStatus.IDLE)
                    .setStatusFromDm(DeviceStatus.BUSY)
                    .build(),
                DeviceStatus.LAMEDUCK))
        .isEqualTo(DeviceStatus.BUSY);
    assertThat(
            DeviceConditionUtil.mergeMnmDeviceStatusIfAny(
                DeviceCondition.newBuilder().setStatusFromLab(DeviceStatus.IDLE).build(),
                DeviceStatus.LAMEDUCK))
        .isEqualTo(DeviceStatus.LAMEDUCK);
    assertThat(
            DeviceConditionUtil.mergeMnmDeviceStatusIfAny(
                DeviceCondition.newBuilder().setStatusFromLab(DeviceStatus.IDLE).build(),
                DeviceStatus.IDLE))
        .isEqualTo(DeviceStatus.IDLE);
    assertThat(
            DeviceConditionUtil.mergeMnmDeviceStatusIfAny(
                DeviceCondition.newBuilder()
                    .setStatusFromLab(DeviceStatus.BUSY)
                    .setStatusFromDm(DeviceStatus.INIT)
                    .build(),
                DeviceStatus.LAMEDUCK))
        .isEqualTo(DeviceStatus.BUSY);
    assertThat(
            DeviceConditionUtil.mergeMnmDeviceStatusIfAny(
                DeviceCondition.newBuilder().setStatusFromLab(DeviceStatus.MISSING).build(),
                DeviceStatus.LAMEDUCK))
        .isEqualTo(DeviceStatus.MISSING);
    assertThat(
            DeviceConditionUtil.mergeMnmDeviceStatusIfAny(
                DeviceCondition.newBuilder().setStatusFromLab(DeviceStatus.FAILED).build(),
                DeviceStatus.IDLE))
        .isEqualTo(DeviceStatus.FAILED);
  }

  @Test
  public void isExpired_true() {
    DeviceCondition deviceCondition =
        DeviceCondition.newBuilder()
            .setSyncTimeMsFromMaster(
                Instant.now()
                    .minus(DeviceConditionUtil.EXPIRATION_THRESHOLD)
                    .minusMillis(1L)
                    .toEpochMilli())
            .setSyncTimeMsFromLab(Instant.now().toEpochMilli())
            .build();
    assertThat(DeviceConditionUtil.isExpired(deviceCondition)).isTrue();
  }

  @Test
  public void isExpired_false() {
    DeviceCondition deviceCondition =
        DeviceCondition.newBuilder()
            .setSyncTimeMsFromMaster(Instant.now().toEpochMilli())
            .setSyncTimeMsFromLab(
                Instant.now()
                    .minus(DeviceConditionUtil.EXPIRATION_THRESHOLD)
                    .minusMillis(1L)
                    .toEpochMilli())
            .build();
    assertThat(DeviceConditionUtil.isExpired(deviceCondition)).isFalse();
  }

  @Test
  public void shouldRemove_expired() {
    DeviceCondition deviceCondition =
        DeviceCondition.newBuilder()
            .setStatusFromLab(DeviceStatus.MISSING)
            .setSyncTimeMsFromMaster(
                Instant.now()
                    .minus(DeviceConditionUtil.getRemovalThreshold())
                    .minusSeconds(1L)
                    .toEpochMilli())
            .setSyncTimeMsFromLab(
                Instant.now()
                    .minus(DeviceConditionUtil.getRemovalThreshold())
                    .minusSeconds(1L)
                    .toEpochMilli())
            .build();
    deviceDao =
        DeviceDao.create(deviceLocator, DeviceProfile.getDefaultInstance(), deviceCondition);

    assertThat(DeviceConditionUtil.shouldRemoveMissingDevice(deviceDao, ImmutableSet.of()))
        .isTrue();
  }

  @Test
  public void shouldRemove_expiredEphemeralDevice() {
    DeviceCondition deviceCondition =
        DeviceCondition.newBuilder()
            .setStatusFromLab(DeviceStatus.MISSING)
            .setSyncTimeMsFromMaster(
                Instant.now().minus(Duration.ofDays(1)).minusSeconds(1L).toEpochMilli())
            .setSyncTimeMsFromLab(
                Instant.now().minus(Duration.ofDays(1)).minusSeconds(1L).toEpochMilli())
            .build();
    deviceDao =
        DeviceDao.create(
            deviceLocator,
            DeviceProfile.newBuilder()
                .setFeature(DeviceFeature.newBuilder().addType("CloudTFAvdDevice"))
                .build(),
            deviceCondition);

    assertThat(DeviceConditionUtil.shouldRemoveMissingDevice(deviceDao, ImmutableSet.of()))
        .isTrue();
  }

  @Test
  public void shouldRemove_aliveOnOtherLabsEvenNotExpired() {
    DeviceCondition deviceCondition =
        DeviceCondition.newBuilder()
            .setStatusFromLab(DeviceStatus.MISSING)
            .setSyncTimeMsFromMaster(
                Instant.now()
                    .minus(DeviceConditionUtil.getRemovalThreshold())
                    .plusMillis(1000L)
                    .toEpochMilli())
            .setSyncTimeMsFromLab(Instant.now().toEpochMilli())
            .build();

    deviceDao =
        DeviceDao.create(deviceLocator, DeviceProfile.getDefaultInstance(), deviceCondition);
    assertThat(DeviceConditionUtil.shouldRemoveMissingDevice(deviceDao, ImmutableSet.of("id")))
        .isTrue();
  }

  @Test
  public void shouldRemove_notExpired() {
    DeviceCondition deviceCondition =
        DeviceCondition.newBuilder()
            .setStatusFromLab(DeviceStatus.MISSING)
            .setSyncTimeMsFromMaster(Instant.now().toEpochMilli())
            .setSyncTimeMsFromLab(
                Instant.now()
                    .minus(DeviceConditionUtil.getRemovalThreshold())
                    .minusMillis(1L)
                    .toEpochMilli())
            .build();
    deviceDao =
        DeviceDao.create(deviceLocator, DeviceProfile.getDefaultInstance(), deviceCondition);

    assertThat(DeviceConditionUtil.shouldRemoveMissingDevice(deviceDao, ImmutableSet.of()))
        .isFalse();

    deviceCondition =
        DeviceCondition.newBuilder()
            .setSyncTimeMsFromMaster(
                Instant.now()
                    .minus(DeviceConditionUtil.getRemovalThreshold())
                    .plusMillis(1000L)
                    .toEpochMilli())
            .setSyncTimeMsFromLab(Instant.now().toEpochMilli())
            .build();
    deviceDao =
        DeviceDao.create(deviceLocator, DeviceProfile.getDefaultInstance(), deviceCondition);

    assertThat(DeviceConditionUtil.shouldRemoveMissingDevice(deviceDao, ImmutableSet.of()))
        .isFalse();
  }

  @Test
  public void isSharedPoolDevice_true_requiredDimension() {
    deviceDao =
        DeviceDao.create(
            deviceLocator,
            DeviceProfile.newBuilder()
                .setFeature(
                    DeviceFeature.newBuilder()
                        .setCompositeDimension(
                            DeviceCompositeDimension.newBuilder()
                                .addRequiredDimension(
                                    DeviceDimension.newBuilder()
                                        .setName("pool")
                                        .setValue("shared"))))
                .build(),
            DeviceCondition.getDefaultInstance());
    assertThat(DeviceConditionUtil.isSharedPoolDevice(deviceDao)).isTrue();
  }

  @Test
  public void isSharedPoolDevice_true_supportedDimension() {
    deviceDao =
        DeviceDao.create(
            deviceLocator,
            DeviceProfile.newBuilder()
                .setFeature(
                    DeviceFeature.newBuilder()
                        .setCompositeDimension(
                            DeviceCompositeDimension.newBuilder()
                                .addSupportedDimension(
                                    DeviceDimension.newBuilder()
                                        .setName("pool")
                                        .setValue("shared"))))
                .build(),
            DeviceCondition.getDefaultInstance());
    assertThat(DeviceConditionUtil.isSharedPoolDevice(deviceDao)).isTrue();
  }

  @Test
  public void isSharedPoolDevice_false() {
    deviceDao =
        DeviceDao.create(
            deviceLocator,
            DeviceProfile.newBuilder()
                .setFeature(
                    DeviceFeature.newBuilder()
                        .setCompositeDimension(
                            DeviceCompositeDimension.newBuilder()
                                .addRequiredDimension(
                                    DeviceDimension.newBuilder()
                                        .setName("pool")
                                        .setValue("satellite"))))
                .build(),
            DeviceCondition.getDefaultInstance());

    assertThat(DeviceConditionUtil.isSharedPoolDevice(deviceDao)).isFalse();
  }

  @Test
  public void getDeviceModel() {
    deviceDao =
        DeviceDao.create(
            deviceLocator,
            DeviceProfile.getDefaultInstance(),
            DeviceCondition.getDefaultInstance());
    assertThat(DeviceConditionUtil.getDeviceModel(deviceDao)).isEmpty();

    deviceDao =
        DeviceDao.create(
            deviceLocator,
            DeviceProfile.newBuilder()
                .setFeature(
                    DeviceFeature.newBuilder()
                        .setCompositeDimension(
                            DeviceCompositeDimension.newBuilder()
                                .addSupportedDimension(
                                    DeviceDimension.newBuilder()
                                        .setName("model")
                                        .setValue("fake-model"))))
                .build(),
            DeviceCondition.getDefaultInstance());
    assertThat(DeviceConditionUtil.getDeviceModel(deviceDao)).hasValue("fake-model");
  }

  @Test
  public void getDeviceVersion() {
    deviceDao =
        DeviceDao.create(
            deviceLocator,
            DeviceProfile.getDefaultInstance(),
            DeviceCondition.getDefaultInstance());
    assertThat(DeviceConditionUtil.getDeviceVersion(deviceDao)).isEmpty();

    deviceDao =
        DeviceDao.create(
            deviceLocator,
            DeviceProfile.newBuilder()
                .setFeature(
                    DeviceFeature.newBuilder()
                        .addType("AndroidRealDevice")
                        .setCompositeDimension(
                            DeviceCompositeDimension.newBuilder()
                                .addSupportedDimension(
                                    DeviceDimension.newBuilder()
                                        .setName("sdk_version")
                                        .setValue("fake-sdk-version"))
                                .addSupportedDimension(
                                    DeviceDimension.newBuilder()
                                        .setName("software_version")
                                        .setValue("fake-software-version"))))
                .build(),
            DeviceCondition.getDefaultInstance());
    assertThat(DeviceConditionUtil.getDeviceVersion(deviceDao)).hasValue("fake-sdk-version");

    deviceDao =
        DeviceDao.create(
            deviceLocator,
            DeviceProfile.newBuilder()
                .setFeature(
                    DeviceFeature.newBuilder()
                        .addType("IosRealDevice")
                        .setCompositeDimension(
                            DeviceCompositeDimension.newBuilder()
                                .addSupportedDimension(
                                    DeviceDimension.newBuilder()
                                        .setName("sdk_version")
                                        .setValue("fake-sdk-version"))
                                .addSupportedDimension(
                                    DeviceDimension.newBuilder()
                                        .setName("software_version")
                                        .setValue("fake-software-version"))))
                .build(),
            DeviceCondition.getDefaultInstance());
    assertThat(DeviceConditionUtil.getDeviceVersion(deviceDao)).hasValue("fake-software-version");

    deviceDao =
        DeviceDao.create(
            deviceLocator,
            DeviceProfile.newBuilder()
                .setFeature(
                    DeviceFeature.newBuilder()
                        .addType("NonRealDevice")
                        .setCompositeDimension(
                            DeviceCompositeDimension.newBuilder()
                                .addSupportedDimension(
                                    DeviceDimension.newBuilder()
                                        .setName("sdk_version")
                                        .setValue("fake-sdk-version"))
                                .addSupportedDimension(
                                    DeviceDimension.newBuilder()
                                        .setName("software_version")
                                        .setValue("fake-software-version"))))
                .build(),
            DeviceCondition.getDefaultInstance());
    assertThat(DeviceConditionUtil.getDeviceVersion(deviceDao)).isEmpty();
  }
}
