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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.HealthCategory;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.shared.util.device.DeviceHealthUtil;
import java.util.List;

/**
 * The health of one device: one of the four standard health categories (In Service, In Transition,
 * Auto Recovery, Needs Manual Repair), or Quarantined for an idle device that is withheld from
 * tests, together with its title, such as {@code In Service (Idle)}.
 *
 * <p>The category is the upstream {@link DeviceInfo#getHealthCategory()} when the data source sets
 * it, and is otherwise derived from the device's status and types alone, so it applies uniformly to
 * every device family (Android, iOS, emulator/virtual, testbed, misc) and every deployment.
 */
public final class DeviceHealth {

  /** Which health state the device is in. */
  public enum State {
    /** Idle but manually quarantined, so tests cannot be allocated to it. */
    QUARANTINED,
    /** Healthy and ready for new tasks. */
    IN_SERVICE_IDLE,
    /** Healthy and running a task. */
    IN_SERVICE_BUSY,
    /** Temporarily unavailable in a transition status, expected to return on its own. */
    IN_TRANSITION,
    /** Running an automated recovery task, expected to return to service on its own. */
    AUTO_RECOVERY,
    /** In an error state that needs a person to fix it. */
    NEEDS_MANUAL_REPAIR,
    /** The health could not be determined. */
    UNKNOWN,
  }

  /**
   * The {@link DeviceInfo} fields {@link #of} reads, as field mask paths: the status, the types,
   * the upstream category, and the condition that carries the {@code quarantined} temp dimension.
   */
  public static final ImmutableList<String> INPUT_FIELD_PATHS =
      ImmutableList.of(
          "device_status", "device_feature.type", "health_category", "device_condition");

  /** The temp dimension that marks a device as quarantined when its value is {@code true}. */
  private static final String QUARANTINED_DIMENSION = "quarantined";

  private final State state;
  private final HealthCategory category;
  private final String title;

  private DeviceHealth(State state, HealthCategory category, String title) {
    this.state = state;
    this.category = category;
    this.title = title;
  }

  /** Classifies {@code deviceInfo}. */
  public static DeviceHealth of(DeviceInfo deviceInfo) {
    DeviceStatus status = deviceInfo.getDeviceStatus();
    List<String> types = deviceInfo.getDeviceFeature().getTypeList();
    HealthCategory category = resolveHealthCategory(deviceInfo, status, types);
    // Quarantine is a special case on top of the category: a device manually withheld from tests
    // while idle. It takes precedence over the category.
    State state =
        isQuarantined(deviceInfo) && status == DeviceStatus.IDLE
            ? State.QUARANTINED
            : stateOf(category, status);
    return new DeviceHealth(state, category, formatTitle(state, status.name()));
  }

  /** The health state. */
  public State state() {
    return state;
  }

  /**
   * The resolved health category, {@link HealthCategory#HEALTH_CATEGORY_UNSPECIFIED} when it cannot
   * be determined. It is resolved for a quarantined device too, which reports {@link
   * State#QUARANTINED} instead.
   */
  public HealthCategory category() {
    return category;
  }

  /**
   * The health title: {@code Quarantined}, {@code In Service (Idle)}, {@code In Service (Busy)},
   * {@code In Transition (<detail>)} such as {@code In Transition (Cleanup)}, {@code Auto
   * Recovery}, {@code Needs Manual Repair}, or {@code Unknown}.
   */
  public String title() {
    return title;
  }

  /**
   * Prefers the upstream {@link HealthCategory} from {@link DeviceInfo}. If absent or unspecified
   * (for example ATS LabInfo), falls back to {@link DeviceHealthUtil#calculateHealthCategory}.
   */
  private static HealthCategory resolveHealthCategory(
      DeviceInfo deviceInfo, DeviceStatus status, List<String> types) {
    if (deviceInfo.hasHealthCategory()
        && deviceInfo.getHealthCategory() != HealthCategory.HEALTH_CATEGORY_UNSPECIFIED) {
      return deviceInfo.getHealthCategory();
    }
    HealthCategory category = DeviceHealthUtil.calculateHealthCategory(status, types);
    if (category != HealthCategory.HEALTH_CATEGORY_UNSPECIFIED) {
      return category;
    }
    // A device in IDLE or BUSY with no detected type cannot be allocated and needs manual repair.
    if (status == DeviceStatus.IDLE || status == DeviceStatus.BUSY) {
      return HealthCategory.HEALTH_CATEGORY_NEED_MANUAL_REPAIR;
    }
    return HealthCategory.HEALTH_CATEGORY_UNSPECIFIED;
  }

  /** Whether the device carries the {@code quarantined=true} temp dimension (case-insensitive). */
  private static boolean isQuarantined(DeviceInfo deviceInfo) {
    for (TempDimension tempDimension : deviceInfo.getDeviceCondition().getTempDimensionList()) {
      DeviceDimension dimension = tempDimension.getDimension();
      if (dimension.getName().equals(QUARANTINED_DIMENSION)
          && Ascii.equalsIgnoreCase(dimension.getValue(), "true")) {
        return true;
      }
    }
    return false;
  }

  private static State stateOf(HealthCategory category, DeviceStatus status) {
    return switch (category) {
      case HEALTH_CATEGORY_IN_SERVICE ->
          status == DeviceStatus.BUSY ? State.IN_SERVICE_BUSY : State.IN_SERVICE_IDLE;
      case HEALTH_CATEGORY_IN_TRANSITION -> State.IN_TRANSITION;
      case HEALTH_CATEGORY_IN_AUTO_RECOVERY -> State.AUTO_RECOVERY;
      case HEALTH_CATEGORY_NEED_MANUAL_REPAIR -> State.NEEDS_MANUAL_REPAIR;
      default -> State.UNKNOWN;
    };
  }

  private static String formatTitle(State state, String status) {
    return switch (state) {
      case QUARANTINED -> "Quarantined";
      case IN_SERVICE_IDLE -> "In Service (Idle)";
      case IN_SERVICE_BUSY -> "In Service (Busy)";
      case IN_TRANSITION -> "In Transition (" + transitionDetail(status) + ")";
      case AUTO_RECOVERY -> "Auto Recovery";
      case NEEDS_MANUAL_REPAIR -> "Needs Manual Repair";
      case UNKNOWN -> "Unknown";
    };
  }

  private static String transitionDetail(String status) {
    return switch (status) {
      case "INIT" -> "Initializing";
      case "PREPPING" -> "Preparing";
      case "LAMEDUCK" -> "Lameduck";
      case "DIRTY" -> "Cleanup";
      case "DYING" -> "Shutting Down";
      default -> "Transitioning";
    };
  }
}
