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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toJavaInstant;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceType;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthAndActivityInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthAndActivityInfo.CurrentTask;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthAndActivityInfo.Diagnostics;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthState;
import com.google.protobuf.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;

/** Builder for the HealthAndActivityInfo proto. */
public final class HealthAndActivityBuilder {

  private static final ImmutableSet<String> ABNORMAL_TYPE_KEYWORDS =
      ImmutableSet.of(
          "FAILED",
          "ABNORMAL",
          "DISCONNECTED",
          "OFFLINE",
          "UNAUTHORIZED",
          "FASTBOOT",
          "FASTBOOTDMODE");

  private final InstantSource instantSource;

  @Inject
  HealthAndActivityBuilder(InstantSource instantSource) {
    this.instantSource = instantSource;
  }

  public HealthAndActivityInfo buildHealthAndActivityInfo(DeviceInfo deviceInfo) {
    HealthAndActivityInfo.Builder builder = HealthAndActivityInfo.newBuilder();

    String status = deviceInfo.getDeviceStatus().toString();
    List<String> types = deviceInfo.getDeviceFeature().getTypeList();
    Timestamp lastInServiceTime = deviceInfo.getDeviceCondition().getLastHealthyTime();
    List<TempDimension> tempDimensions = deviceInfo.getDeviceCondition().getTempDimensionList();

    boolean isQuarantined =
        tempDimensions.stream()
            .anyMatch(
                dim ->
                    dim.getDimension().getName().equals("quarantined")
                        && dim.getDimension().getValue().toLowerCase(Locale.ROOT).equals("true"));

    ImmutableList<DeviceType> feDeviceTypes =
        types.stream()
            .map(
                type ->
                    DeviceType.newBuilder()
                        .setType(type)
                        .setIsAbnormal(isTypeAbnormal(type))
                        .build())
            .collect(toImmutableList());
    builder.addAllDeviceTypes(feDeviceTypes);

    boolean hasAbnormalTypes = feDeviceTypes.stream().anyMatch(DeviceType::getIsAbnormal);

    // A. Quarantined (and IDLE)
    if (isQuarantined && status.equals(DeviceStatus.IDLE.name())) {
      builder
          .setTitle("Quarantined")
          .setSubtitle("Device is idle, but quarantined and unavailable for tests.")
          .setState(HealthState.OUT_OF_SERVICE_NEEDS_FIXING)
          .setDiagnostics(
              Diagnostics.newBuilder()
                  .setDiagnosis("Device has been manually quarantined while in IDLE state.")
                  .setExplanation(
                      "Quarantined devices cannot be allocated for tests until they are"
                          + " unquarantined, even if otherwise healthy."));
    } else if (!types.isEmpty()
        && (status.equals(DeviceStatus.IDLE.name()) || status.equals(DeviceStatus.BUSY.name()))
        && !hasAbnormalTypes) {
      // B. In Service
      if (status.equals(DeviceStatus.IDLE.name())) {
        builder
            .setTitle("In Service (Idle)")
            .setSubtitle("The device is healthy and ready for new tasks.")
            .setState(HealthState.IN_SERVICE_IDLE);
      } else { // BUSY
        builder
            .setTitle("In Service (Busy)")
            .setSubtitle("The device is healthy and currently running a task.")
            .setState(HealthState.IN_SERVICE_BUSY);
      }
    } else {
      // C. Out of Service
      Instant lastInServiceInstant = toJavaInstant(lastInServiceTime);
      Duration sinceLastInService = Duration.between(lastInServiceInstant, instantSource.instant());
      boolean isUnderAnHour = sinceLastInService.compareTo(Duration.ofHours(1)) < 0;

      boolean isRecovering = status.equals(DeviceStatus.BUSY.name()) && hasAbnormalTypes;
      boolean isTempMaint =
          ImmutableSet.of(
                      DeviceStatus.INIT.name(),
                      DeviceStatus.DIRTY.name(),
                      DeviceStatus.LAMEDUCK.name())
                  .contains(status)
              && isUnderAnHour;

      if (isRecovering) {
        // C.1. Recovering (Yellow)
        builder
            .setTitle("Out of Service (Recovering)")
            .setSubtitle("The device is running an automated recovery task.")
            .setState(HealthState.OUT_OF_SERVICE_RECOVERING)
            .setDiagnostics(
                Diagnostics.newBuilder()
                    .setDiagnosis("Device is running a recovery task.")
                    .setExplanation(
                        "An automated recovery task is running. If successful, the device will"
                            + " return to service automatically. No immediate action is"
                            + " required."));
      } else if (isTempMaint) {
        // C.2. Temporary Maintenance (Yellow)
        builder
            .setTitle("Out of Service (may be temporary)")
            .setSubtitle("The device is temporarily unavailable due to routine maintenance.")
            .setState(HealthState.OUT_OF_SERVICE_TEMP_MAINT)
            .setDiagnostics(
                Diagnostics.newBuilder()
                    .setDiagnosis("Device is in a temporary maintenance state (" + status + ").")
                    .setExplanation(
                        "This is usually part of a routine process like initialization or"
                            + " cleanup. The device is expected to become available shortly."));
      } else {
        // C.3. Needs Fixing (Red)
        builder
            .setTitle("Out of Service (Needs Fixing)")
            .setSubtitle("The device is in an error state and requires attention.")
            .setState(HealthState.OUT_OF_SERVICE_NEEDS_FIXING);

        Diagnostics.Builder diagnosticsBuilder = Diagnostics.newBuilder();
        StringBuilder diagnosisBuilder = new StringBuilder();
        StringBuilder explanationBuilder = new StringBuilder();
        StringBuilder actionBuilder = new StringBuilder();

        if (types.isEmpty()) {
          diagnosisBuilder.append("The device has no type detected.\n");
          explanationBuilder.append(
              "OmniLab cannot determine the device type, which is essential for test"
                  + " allocation.\n");
          actionBuilder.append(
              "Check the device's connection and ensure it's not recognized by the system.\n");
        }

        if (hasAbnormalTypes) {
          String abnormalTypesString =
              feDeviceTypes.stream()
                  .filter(DeviceType::getIsAbnormal)
                  .map(DeviceType::getType)
                  .collect(joining(", "));
          diagnosisBuilder
              .append("The device has abnormal types: ")
              .append(abnormalTypesString)
              .append(".\n");
          explanationBuilder.append(
              "These types indicate a problem with the device's state, such as being"
                  + " disconnected or in a bad state.\n");
          actionBuilder.append(
              "Investigate the specific abnormal types to understand the root cause. Check"
                  + " device logs and physical state.\n");
        }

        diagnosisBuilder.append("The device status is ").append(status).append(".\n");
        switch (status) {
          case "INIT" -> explanationBuilder.append("This means it is initializing.\n");
          case "DIRTY" -> explanationBuilder.append("This means it is in a cleanup phase.\n");
          case "LAMEDUCK" ->
              explanationBuilder.append(
                  "This means its host is undergoing a lab server software update.\n");
          case "DYING" ->
              explanationBuilder.append(
                  "This means it is tearing down (e.g., rebooting or disconnected).\n");
          case "PREPPING" ->
              explanationBuilder.append(
                  "This means it is unavailable for testing (e.g., low battery, insufficient"
                      + " storage).\n");
          case "MISSING" -> {
            explanationBuilder.append("This means it has stopped sending heartbeats.\n");
            actionBuilder.append(
                "Check device power, USB connection, and ensure it's not stuck in a boot loop.\n");
          }
          case "FAILED" -> {
            explanationBuilder.append(
                "This means it failed to prepare for a task and could not be automatically"
                    + " recovered.\n");
            actionBuilder.append(
                "Check device logs on the lab host for more details on the failure.\n");
          }
          default -> explanationBuilder.append("Unexpected status.\n");
        }

        diagnosticsBuilder
            .setDiagnosis(diagnosisBuilder.toString().trim())
            .setExplanation(explanationBuilder.toString().trim());
        if (actionBuilder.length() > 0) {
          diagnosticsBuilder.setSuggestedAction(actionBuilder.toString().trim());
        }
        builder.setDiagnostics(diagnosticsBuilder);
      }
    }

    builder
        .setDeviceStatus(
            HealthAndActivityInfo.DeviceStatus.newBuilder()
                .setStatus(status)
                .setIsCritical(builder.getState() == HealthState.OUT_OF_SERVICE_NEEDS_FIXING))
        .setLastInServiceTime(lastInServiceTime);

    if (status.equals(DeviceStatus.BUSY.name())) {
      CurrentTask.Builder currentTask = CurrentTask.newBuilder();
      boolean isRecovering = hasAbnormalTypes;
      currentTask.setType(isRecovering ? "Recovery Task" : "Test");
      if (deviceInfo.getDeviceCondition().hasAllocatedTestLocator()) {
        currentTask
            .setTaskId(deviceInfo.getDeviceCondition().getAllocatedTestLocator().getName())
            .setJobId(
                deviceInfo
                    .getDeviceCondition()
                    .getAllocatedTestLocator()
                    .getJobLocator()
                    .getName());
      }
      builder.setCurrentTask(currentTask);
    }

    return builder.build();
  }

  private boolean isTypeAbnormal(String type) {
    String upperType = Ascii.toUpperCase(type);
    for (String keyword : ABNORMAL_TYPE_KEYWORDS) {
      if (upperType.contains(keyword)) {
        return true;
      }
    }
    return false;
  }
}
