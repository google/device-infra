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

package com.google.devtools.mobileharness.shared.util.device;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.HealthCategory;
import com.google.devtools.mobileharness.shared.constant.device.DeviceConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DeviceHealthUtil}. */
@RunWith(JUnit4.class)
public final class DeviceHealthUtilTest {

  @Test
  public void calculateHealthCategory_failedOrMissing_returnsNeedManualRepair() {
    assertThat(DeviceHealthUtil.calculateHealthCategory(DeviceStatus.FAILED, ImmutableList.of()))
        .isEqualTo(HealthCategory.HEALTH_CATEGORY_NEED_MANUAL_REPAIR);
    assertThat(
            DeviceHealthUtil.calculateHealthCategory(
                DeviceStatus.FAILED, ImmutableList.of("AndroidRealDevice")))
        .isEqualTo(HealthCategory.HEALTH_CATEGORY_NEED_MANUAL_REPAIR);
    assertThat(DeviceHealthUtil.calculateHealthCategory(DeviceStatus.MISSING, ImmutableList.of()))
        .isEqualTo(HealthCategory.HEALTH_CATEGORY_NEED_MANUAL_REPAIR);
    assertThat(
            DeviceHealthUtil.calculateHealthCategory(
                DeviceStatus.MISSING, ImmutableList.of("IosRealDevice")))
        .isEqualTo(HealthCategory.HEALTH_CATEGORY_NEED_MANUAL_REPAIR);
  }

  @Test
  public void calculateHealthCategory_transitStatuses_returnsInTransition() {
    for (DeviceStatus status : DeviceHealthUtil.TRANSIT_STATUSES) {
      assertWithMessage("status %s with empty types", status)
          .that(DeviceHealthUtil.calculateHealthCategory(status, ImmutableList.of()))
          .isEqualTo(HealthCategory.HEALTH_CATEGORY_IN_TRANSITION);
      assertWithMessage("status %s with AndroidRealDevice", status)
          .that(
              DeviceHealthUtil.calculateHealthCategory(
                  status, ImmutableList.of("AndroidRealDevice")))
          .isEqualTo(HealthCategory.HEALTH_CATEGORY_IN_TRANSITION);
      assertWithMessage("status %s with FailedDevice", status)
          .that(DeviceHealthUtil.calculateHealthCategory(status, ImmutableList.of("FailedDevice")))
          .isEqualTo(HealthCategory.HEALTH_CATEGORY_IN_TRANSITION);
    }
  }

  @Test
  public void calculateHealthCategory_emptyTypesOnNonTransitNonTerminalStatus_returnsUnspecified() {
    assertThat(DeviceHealthUtil.calculateHealthCategory(DeviceStatus.IDLE, ImmutableList.of()))
        .isEqualTo(HealthCategory.HEALTH_CATEGORY_UNSPECIFIED);
    assertThat(DeviceHealthUtil.calculateHealthCategory(DeviceStatus.BUSY, ImmutableList.of()))
        .isEqualTo(HealthCategory.HEALTH_CATEGORY_UNSPECIFIED);
  }

  @Test
  public void calculateHealthCategory_idleOrBusyWithHealthyTypes_returnsInService() {
    ImmutableList<String> healthyTypes =
        ImmutableList.of(
            "AndroidRealDevice",
            "AndroidLocalEmulator",
            "IosRealDevice",
            "IosRealDeviceOnLinux",
            "NormalTestbedDevice",
            "LinuxDevice",
            "CrosDevice",
            "WindowsDevice");
    for (String type : healthyTypes) {
      assertWithMessage("IDLE with %s", type)
          .that(DeviceHealthUtil.calculateHealthCategory(DeviceStatus.IDLE, ImmutableList.of(type)))
          .isEqualTo(HealthCategory.HEALTH_CATEGORY_IN_SERVICE);
      assertWithMessage("BUSY with %s", type)
          .that(DeviceHealthUtil.calculateHealthCategory(DeviceStatus.BUSY, ImmutableList.of(type)))
          .isEqualTo(HealthCategory.HEALTH_CATEGORY_IN_SERVICE);
    }
  }

  @Test
  public void calculateHealthCategory_idleWithUnhealthyType_returnsNeedManualRepair() {
    for (String unhealthyType : DeviceConstants.UNHEALTHY_DEVICE_TYPES) {
      assertWithMessage("IDLE with %s", unhealthyType)
          .that(
              DeviceHealthUtil.calculateHealthCategory(
                  DeviceStatus.IDLE, ImmutableList.of(unhealthyType)))
          .isEqualTo(HealthCategory.HEALTH_CATEGORY_NEED_MANUAL_REPAIR);
    }
  }

  @Test
  public void calculateHealthCategory_busyWithUnhealthyType_returnsInAutoRecovery() {
    for (String unhealthyType : DeviceConstants.UNHEALTHY_DEVICE_TYPES) {
      assertWithMessage("BUSY with %s", unhealthyType)
          .that(
              DeviceHealthUtil.calculateHealthCategory(
                  DeviceStatus.BUSY, ImmutableList.of(unhealthyType)))
          .isEqualTo(HealthCategory.HEALTH_CATEGORY_IN_AUTO_RECOVERY);
    }
  }

  @Test
  public void calculateHealthCategory_unrecognizedStatusWithHealthyType_returnsUnspecified() {
    assertThat(
            DeviceHealthUtil.calculateHealthCategory(
                DeviceStatus.UNRECOGNIZED, ImmutableList.of("AndroidRealDevice")))
        .isEqualTo(HealthCategory.HEALTH_CATEGORY_UNSPECIFIED);
  }
}
