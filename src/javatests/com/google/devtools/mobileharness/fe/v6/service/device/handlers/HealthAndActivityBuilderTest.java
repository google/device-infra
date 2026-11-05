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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.HealthCategory;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.device.provider.RunningTestInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.device.util.DeviceHealth;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthAndActivityInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.UiState;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class HealthAndActivityBuilderTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private RunningTestInfoProvider runningTestInfoProvider;

  private HealthAndActivityBuilder builder;

  @Before
  public void setUp() {
    when(runningTestInfoProvider.getRunningTest(anyString()))
        .thenReturn(immediateFuture(Optional.empty()));
    builder = new HealthAndActivityBuilder(runningTestInfoProvider);
  }

  private record Case(DeviceInfo device, String title, UiState uiState) {}

  @Test
  public void title_isTheDeviceHealthTitleForEveryState() {
    ImmutableList<Case> cases =
        ImmutableList.of(
            new Case(
                quarantined(device(DeviceStatus.IDLE, "AndroidRealDevice")),
                "Quarantined",
                UiState.BLOCKED),
            new Case(
                device(DeviceStatus.IDLE, "AndroidRealDevice"),
                "In Service (Idle)",
                UiState.HEALTHY),
            new Case(
                device(DeviceStatus.BUSY, "AndroidRealDevice"), "In Service (Busy)", UiState.BUSY),
            new Case(
                device(DeviceStatus.DIRTY, "AndroidRealDevice"),
                "In Transition (Cleanup)",
                UiState.TRANSITIONING),
            new Case(
                device(DeviceStatus.BUSY, "FailedAndroidDevice"),
                "Auto Recovery",
                UiState.RECOVERING),
            new Case(
                device(DeviceStatus.IDLE, "AndroidRealDevice").toBuilder()
                    .setHealthCategory(HealthCategory.HEALTH_CATEGORY_NEED_MANUAL_REPAIR)
                    .build(),
                "Needs Manual Repair",
                UiState.ERROR),
            new Case(
                device(DeviceStatus.IDLE, "AndroidRealDevice").toBuilder()
                    .setDeviceStatusValue(999)
                    .build(),
                "Unknown",
                UiState.UI_STATE_UNSPECIFIED));

    for (Case c : cases) {
      HealthAndActivityInfo info = builder.buildHealthAndActivityInfo(c.device());

      assertWithMessage("device %s", c.device()).that(info.getTitle()).isEqualTo(c.title());
      assertWithMessage("device %s", c.device())
          .that(info.getTitle())
          .isEqualTo(DeviceHealth.of(c.device()).title());
      assertWithMessage("device %s", c.device()).that(info.getUiState()).isEqualTo(c.uiState());
    }
  }

  @Test
  public void deviceTypes_markAbnormalTypes() {
    HealthAndActivityInfo info =
        builder.buildHealthAndActivityInfo(
            device(DeviceStatus.IDLE, "AndroidRealDevice", "DisconnectedDevice"));

    assertThat(info.getDeviceTypesList()).hasSize(2);
    assertThat(info.getDeviceTypes(0).getIsAbnormal()).isFalse();
    assertThat(info.getDeviceTypes(1).getIsAbnormal()).isTrue();
    assertThat(info.getTitle()).isEqualTo("Needs Manual Repair");
    assertThat(info.getDeviceStatus().getIsCritical()).isTrue();
  }

  @Test
  public void busyRecoveringDevice_reportsARecoveryTask() {
    HealthAndActivityInfo info =
        builder.buildHealthAndActivityInfo(device(DeviceStatus.BUSY, "FailedAndroidDevice"));

    assertThat(info.getCurrentTask().getType()).isEqualTo("Recovery Task");
  }

  @Test
  public void busyHealthyDevice_reportsATest() {
    HealthAndActivityInfo info =
        builder.buildHealthAndActivityInfo(device(DeviceStatus.BUSY, "AndroidRealDevice"));

    assertThat(info.getCurrentTask().getType()).isEqualTo("Test");
  }

  private static DeviceInfo device(DeviceStatus status, String... types) {
    DeviceFeature.Builder feature = DeviceFeature.newBuilder();
    for (String type : types) {
      feature.addType(type);
    }
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-1"))
        .setDeviceStatus(status)
        .setDeviceFeature(feature)
        .build();
  }

  private static DeviceInfo quarantined(DeviceInfo deviceInfo) {
    return deviceInfo.toBuilder()
        .setDeviceCondition(
            DeviceCondition.newBuilder()
                .addTempDimension(
                    TempDimension.newBuilder()
                        .setDimension(
                            DeviceDimension.newBuilder().setName("quarantined").setValue("true"))))
        .build();
  }
}
