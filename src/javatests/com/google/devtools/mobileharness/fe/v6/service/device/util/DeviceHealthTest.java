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

package com.google.devtools.mobileharness.fe.v6.service.device.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.HealthCategory;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.device.util.DeviceHealth.State;
import com.google.protobuf.util.FieldMaskUtil;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceHealthTest {

  @Test
  public void of_idleDeviceWithNormalType_isInServiceIdle() {
    DeviceHealth health = DeviceHealth.of(device(DeviceStatus.IDLE, "AndroidRealDevice"));

    assertThat(health.state()).isEqualTo(State.IN_SERVICE_IDLE);
    assertThat(health.category()).isEqualTo(HealthCategory.HEALTH_CATEGORY_IN_SERVICE);
    assertThat(health.title()).isEqualTo("In Service (Idle)");
  }

  @Test
  public void of_busyDeviceWithNormalType_isInServiceBusy() {
    DeviceHealth health = DeviceHealth.of(device(DeviceStatus.BUSY, "IosRealDevice"));

    assertThat(health.state()).isEqualTo(State.IN_SERVICE_BUSY);
    assertThat(health.title()).isEqualTo("In Service (Busy)");
  }

  @Test
  public void of_upstreamCategory_winsOverStatusAndTypes() {
    DeviceInfo normalLookingIdle =
        device(DeviceStatus.IDLE, "AndroidRealDevice").toBuilder()
            .setHealthCategory(HealthCategory.HEALTH_CATEGORY_NEED_MANUAL_REPAIR)
            .build();

    DeviceHealth health = DeviceHealth.of(normalLookingIdle);

    assertThat(health.state()).isEqualTo(State.NEEDS_MANUAL_REPAIR);
    assertThat(health.category()).isEqualTo(HealthCategory.HEALTH_CATEGORY_NEED_MANUAL_REPAIR);
    assertThat(health.title()).isEqualTo("Needs Manual Repair");
  }

  @Test
  public void of_unspecifiedUpstreamCategory_fallsBackToStatusAndTypes() {
    DeviceInfo unspecified =
        device(DeviceStatus.IDLE, "AndroidRealDevice").toBuilder()
            .setHealthCategory(HealthCategory.HEALTH_CATEGORY_UNSPECIFIED)
            .build();

    assertThat(DeviceHealth.of(unspecified).state()).isEqualTo(State.IN_SERVICE_IDLE);
  }

  @Test
  public void of_quarantinedIdleDevice_isQuarantinedAndKeepsItsCategory() {
    DeviceHealth health =
        DeviceHealth.of(quarantined(device(DeviceStatus.IDLE, "AndroidRealDevice"), "TRUE"));

    assertThat(health.state()).isEqualTo(State.QUARANTINED);
    assertThat(health.category()).isEqualTo(HealthCategory.HEALTH_CATEGORY_IN_SERVICE);
    assertThat(health.title()).isEqualTo("Quarantined");
  }

  @Test
  public void of_quarantineOnlyAppliesToIdleDevicesWithTrueValue() {
    assertThat(
            DeviceHealth.of(quarantined(device(DeviceStatus.BUSY, "AndroidRealDevice"), "true"))
                .state())
        .isEqualTo(State.IN_SERVICE_BUSY);
    assertThat(
            DeviceHealth.of(quarantined(device(DeviceStatus.IDLE, "AndroidRealDevice"), "false"))
                .state())
        .isEqualTo(State.IN_SERVICE_IDLE);
  }

  @Test
  public void of_transitionStatuses_areInTransitionWithTheirDetail() {
    ImmutableMap<DeviceStatus, String> titles =
        ImmutableMap.of(
            DeviceStatus.INIT, "In Transition (Initializing)",
            DeviceStatus.PREPPING, "In Transition (Preparing)",
            DeviceStatus.LAMEDUCK, "In Transition (Lameduck)",
            DeviceStatus.DIRTY, "In Transition (Cleanup)",
            DeviceStatus.DYING, "In Transition (Shutting Down)");
    for (Map.Entry<DeviceStatus, String> entry : titles.entrySet()) {
      DeviceHealth health = DeviceHealth.of(device(entry.getKey(), "AndroidRealDevice"));
      assertWithMessage("status %s", entry.getKey())
          .that(health.state())
          .isEqualTo(State.IN_TRANSITION);
      assertWithMessage("status %s", entry.getKey())
          .that(health.title())
          .isEqualTo(entry.getValue());
    }
  }

  @Test
  public void of_upstreamTransitionCategoryOnNonTransitStatus_usesGenericDetail() {
    DeviceInfo info =
        device(DeviceStatus.IDLE, "AndroidRealDevice").toBuilder()
            .setHealthCategory(HealthCategory.HEALTH_CATEGORY_IN_TRANSITION)
            .build();

    assertThat(DeviceHealth.of(info).title()).isEqualTo("In Transition (Transitioning)");
  }

  @Test
  public void of_busyDeviceWithAbnormalType_isAutoRecovery() {
    DeviceHealth health = DeviceHealth.of(device(DeviceStatus.BUSY, "FailedAndroidDevice"));

    assertThat(health.state()).isEqualTo(State.AUTO_RECOVERY);
    assertThat(health.category()).isEqualTo(HealthCategory.HEALTH_CATEGORY_IN_AUTO_RECOVERY);
    assertThat(health.title()).isEqualTo("Auto Recovery");
  }

  @Test
  public void of_deviceThatCannotBeAllocated_needsManualRepair() {
    assertThat(
            DeviceHealth.of(device(DeviceStatus.IDLE, "AndroidRealDevice", "DisconnectedDevice"))
                .state())
        .isEqualTo(State.NEEDS_MANUAL_REPAIR);
    assertThat(DeviceHealth.of(device(DeviceStatus.IDLE)).title()).isEqualTo("Needs Manual Repair");
    assertThat(DeviceHealth.of(device(DeviceStatus.BUSY)).state())
        .isEqualTo(State.NEEDS_MANUAL_REPAIR);
    assertThat(DeviceHealth.of(device(DeviceStatus.MISSING, "AndroidRealDevice")).state())
        .isEqualTo(State.NEEDS_MANUAL_REPAIR);
    assertThat(DeviceHealth.of(device(DeviceStatus.FAILED, "AndroidRealDevice")).state())
        .isEqualTo(State.NEEDS_MANUAL_REPAIR);
  }

  @Test
  public void of_unrecognizedStatus_isUnknown() {
    DeviceInfo info =
        device(DeviceStatus.IDLE, "AndroidRealDevice").toBuilder()
            .setDeviceStatusValue(999)
            .build();

    DeviceHealth health = DeviceHealth.of(info);

    assertThat(health.state()).isEqualTo(State.UNKNOWN);
    assertThat(health.category()).isEqualTo(HealthCategory.HEALTH_CATEGORY_UNSPECIFIED);
    assertThat(health.title()).isEqualTo("Unknown");
  }

  @Test
  public void isTypeAbnormal_matchesBadStateKeywordsIgnoringCase() {
    assertThat(DeviceHealth.isTypeAbnormal("DisconnectedDevice")).isTrue();
    assertThat(DeviceHealth.isTypeAbnormal("failedAndroidDevice")).isTrue();
    assertThat(DeviceHealth.isTypeAbnormal("AndroidFastbootDevice")).isTrue();
    assertThat(DeviceHealth.isTypeAbnormal("AndroidRealDevice")).isFalse();
  }

  @Test
  public void inputFieldPaths_areDeviceInfoFieldPaths() {
    for (String path : DeviceHealth.INPUT_FIELD_PATHS) {
      assertWithMessage("%s is not a field path of DeviceInfo", path)
          .that(FieldMaskUtil.isValid(DeviceInfo.getDescriptor(), path))
          .isTrue();
    }
  }

  private static DeviceInfo device(DeviceStatus status, String... types) {
    DeviceFeature.Builder feature = DeviceFeature.newBuilder();
    for (String type : types) {
      feature.addType(type);
    }
    return DeviceInfo.newBuilder().setDeviceStatus(status).setDeviceFeature(feature).build();
  }

  private static DeviceInfo quarantined(DeviceInfo deviceInfo, String value) {
    return deviceInfo.toBuilder()
        .setDeviceCondition(
            DeviceCondition.newBuilder()
                .addTempDimension(
                    TempDimension.newBuilder()
                        .setDimension(
                            DeviceDimension.newBuilder().setName("quarantined").setValue(value))))
        .build();
  }
}
