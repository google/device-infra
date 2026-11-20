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

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.HealthCategory;
import java.util.Collection;

/** Shared utility for calculating {@link HealthCategory} of a device. */
public final class DeviceHealthUtil {

  /** The device types that are considered unhealthy. */
  public static final ImmutableSet<String> UNHEALTHY_DEVICE_TYPES =
      ImmutableSet.of(
          "AbnormalTestbedDevice",
          "AndroidFastbootDevice",
          "AndroidFastbootdModeDevice",
          "AndroidOfflineDevice",
          "AndroidRecoveryDevice",
          "AndroidUnauthorizedDevice",
          "DisconnectedDevice",
          "FailedDevice",
          "FuchsiaOfflineDevice",
          "FuchsiaRecoveringDevice");

  /** Statuses in which the device is eligible to be healthy (in service) when types are healthy. */
  public static final ImmutableSet<DeviceStatus> HEALTHY_STATUS =
      ImmutableSet.of(DeviceStatus.IDLE, DeviceStatus.BUSY);

  /** Statuses in which the device is temporarily unavailable and in transition. */
  public static final ImmutableSet<DeviceStatus> TRANSIT_STATUSES =
      ImmutableSet.of(
          DeviceStatus.INIT,
          DeviceStatus.PREPPING,
          DeviceStatus.LAMEDUCK,
          DeviceStatus.DIRTY,
          DeviceStatus.DYING);

  /**
   * Calculates the {@link HealthCategory} of a device from its status and device types.
   *
   * <p>The category is determined in the following order:
   *
   * <ol>
   *   <li>{@link DeviceStatus#FAILED} or {@link DeviceStatus#MISSING} returns {@link
   *       HealthCategory#HEALTH_CATEGORY_NEED_MANUAL_REPAIR}.
   *   <li>{@link #TRANSIT_STATUSES} ({@code INIT}, {@code PREPPING}, {@code LAMEDUCK}, {@code
   *       DIRTY}, {@code DYING}) returns {@link HealthCategory#HEALTH_CATEGORY_IN_TRANSITION}.
   *   <li>Empty {@code types} returns {@link HealthCategory#HEALTH_CATEGORY_UNSPECIFIED}.
   *   <li>{@link DeviceStatus#IDLE} or {@link DeviceStatus#BUSY} with no type in {@link
   *       #UNHEALTHY_DEVICE_TYPES} returns {@link HealthCategory#HEALTH_CATEGORY_IN_SERVICE}.
   *   <li>{@link DeviceStatus#BUSY} with any type in {@code UNHEALTHY_DEVICE_TYPES} returns {@link
   *       HealthCategory#HEALTH_CATEGORY_IN_AUTO_RECOVERY}.
   *   <li>Any other status with a type in {@code UNHEALTHY_DEVICE_TYPES} (such as {@code IDLE})
   *       returns {@link HealthCategory#HEALTH_CATEGORY_NEED_MANUAL_REPAIR}.
   *   <li>Otherwise, returns {@link HealthCategory#HEALTH_CATEGORY_UNSPECIFIED}.
   * </ol>
   */
  public static HealthCategory calculateHealthCategory(
      DeviceStatus status, Collection<String> types) {
    // FAILED or MISSING status always means NEED_MANUAL_REPAIR, regardless of device type.
    if (status == DeviceStatus.FAILED || status == DeviceStatus.MISSING) {
      return HealthCategory.HEALTH_CATEGORY_NEED_MANUAL_REPAIR;
    }

    // Status-based transition: INIT, PREPPING, LAMEDUCK, DIRTY, DYING
    if (TRANSIT_STATUSES.contains(status)) {
      return HealthCategory.HEALTH_CATEGORY_IN_TRANSITION;
    }

    if (types.isEmpty()) {
      return HealthCategory.HEALTH_CATEGORY_UNSPECIFIED;
    }

    boolean hasUnhealthyType = types.stream().anyMatch(UNHEALTHY_DEVICE_TYPES::contains);

    if (HEALTHY_STATUS.contains(status) && !hasUnhealthyType) {
      return HealthCategory.HEALTH_CATEGORY_IN_SERVICE;
    }

    if (status == DeviceStatus.BUSY && hasUnhealthyType) {
      return HealthCategory.HEALTH_CATEGORY_IN_AUTO_RECOVERY;
    }

    if (hasUnhealthyType) {
      return HealthCategory.HEALTH_CATEGORY_NEED_MANUAL_REPAIR;
    }

    return HealthCategory.HEALTH_CATEGORY_UNSPECIFIED;
  }

  private DeviceHealthUtil() {}
}
