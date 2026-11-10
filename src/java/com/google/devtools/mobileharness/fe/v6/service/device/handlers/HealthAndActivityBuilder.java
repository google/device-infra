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
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.HealthCategory;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.device.provider.RunningTestInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.device.provider.RunningTestInfoProvider.RunningTestInfo;
import com.google.devtools.mobileharness.fe.v6.service.device.util.DeviceHealth;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceType;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthAndActivityInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthAndActivityInfo.CurrentTask;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthAndActivityInfo.Diagnostics;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthState;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.UiState;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Builder for the {@link HealthAndActivityInfo} proto.
 *
 * <p>Renders the health that {@link DeviceHealth} classifies: one of the four standard health
 * categories (In Service, In Transition, Auto Recovery, Needs Manual Repair), plus a Quarantined
 * special case. The category is not exposed on the wire: it is surfaced to the frontend through the
 * {@code title} text and a presentation-only {@link UiState}, per the FE v6 BFF principle. The
 * title is {@link DeviceHealth#title()}, which is also the value of the {@code
 * device_field::health} fleet search key, so the device page and fleet search always agree.
 *
 * <p>During the frontend/backend release transition the builder also dual-writes the transitional
 * {@code state} ({@link HealthState}) so a frontend on the other side of a skew window still
 * renders an icon. The dual-write is removed by a follow-up cleanup CL.
 */
public final class HealthAndActivityBuilder {

  private static final Logger logger = Logger.getLogger(HealthAndActivityBuilder.class.getName());

  /** Timeout for the MOSS running-test lookup (best-effort, non-blocking for the page). */
  private static final Duration MOSS_LOOKUP_TIMEOUT = Duration.ofSeconds(5);

  private final RunningTestInfoProvider runningTestInfoProvider;

  @Inject
  HealthAndActivityBuilder(RunningTestInfoProvider runningTestInfoProvider) {
    this.runningTestInfoProvider = runningTestInfoProvider;
  }

  @SuppressWarnings("deprecation") // Intentional dual-write of the deprecated state field during
  // the frontend/backend release transition. Removed by the follow-up cleanup CL.
  public HealthAndActivityInfo buildHealthAndActivityInfo(DeviceInfo deviceInfo) {
    HealthAndActivityInfo.Builder builder = HealthAndActivityInfo.newBuilder();

    String status = deviceInfo.getDeviceStatus().toString();
    List<String> types = deviceInfo.getDeviceFeature().getTypeList();
    Timestamp lastInServiceTime = deviceInfo.getDeviceCondition().getLastHealthyTime();

    ImmutableList<DeviceType> feDeviceTypes =
        types.stream()
            .map(type ->
                DeviceType.newBuilder()
                    .setType(type)
                    .setIsAbnormal(DeviceHealth.isTypeAbnormal(type))
                    .build())
            .collect(toImmutableList());
    builder.addAllDeviceTypes(feDeviceTypes);
    boolean hasAbnormalTypes = feDeviceTypes.stream().anyMatch(DeviceType::getIsAbnormal);

    // Look up the running test for BUSY devices (populates current_task with test/job id).
    Optional<RunningTestInfo> runningTest = Optional.empty();
    if (status.equals(DeviceStatus.BUSY.name())) {
      runningTest = lookUpRunningTest(deviceInfo);
    }

    DeviceHealth health = DeviceHealth.of(deviceInfo);
    builder.setTitle(health.title());
    switch (health.state()) {
      case QUARANTINED ->
          // Quarantine is an FE-detected special case: a healthy device manually withheld from
          // tests. It bypasses the health category entirely.
          builder
              .setSubtitle("Device is idle, but quarantined and unavailable for tests.")
              .setUiState(UiState.BLOCKED)
              .setState(HealthState.IDLE_BUT_QUARANTINED)
              .setDiagnostics(
                  Diagnostics.newBuilder()
                      .setDiagnosis("Device has been manually quarantined while in IDLE state.")
                      .setExplanation(
                          "Quarantined devices cannot be allocated for tests until they are"
                              + " unquarantined, even if otherwise healthy."));
      case IN_SERVICE_BUSY ->
          builder
              .setSubtitle("The device is healthy and currently running a task.")
              .setUiState(UiState.BUSY)
              .setState(HealthState.IN_SERVICE_BUSY);
      case IN_SERVICE_IDLE ->
          builder
              .setSubtitle("The device is healthy and ready for new tasks.")
              .setUiState(UiState.HEALTHY)
              .setState(HealthState.IN_SERVICE_IDLE);
      case IN_TRANSITION ->
          builder
              .setSubtitle(transitionSubtitle(status))
              .setUiState(UiState.TRANSITIONING)
              .setState(HealthState.OUT_OF_SERVICE_TEMP_MAINT)
              .setDiagnostics(
                  Diagnostics.newBuilder()
                      .setDiagnosis("The device is in a transition state (" + status + ").")
                      .setExplanation(transitionSubtitle(status)));
      case AUTO_RECOVERY ->
          builder
              .setSubtitle(
                  "An automated recovery task is running; the device should return to service on"
                      + " its own.")
              .setUiState(UiState.RECOVERING)
              .setState(HealthState.OUT_OF_SERVICE_RECOVERING)
              .setDiagnostics(
                  Diagnostics.newBuilder()
                      .setDiagnosis("Device is running a recovery task.")
                      .setExplanation(
                          "An automated recovery task is running. If successful, the device will"
                              + " return to service automatically. No immediate action is"
                              + " required."));
      case NEEDS_MANUAL_REPAIR ->
          builder
              .setSubtitle("The device is in an error state and requires attention.")
              .setUiState(UiState.ERROR)
              .setState(HealthState.OUT_OF_SERVICE_NEEDS_FIXING)
              .setDiagnostics(
                  buildNeedsRepairDiagnostics(status, feDeviceTypes, hasAbnormalTypes, types));
      case UNKNOWN ->
          builder
              .setSubtitle("The device's health could not be determined.")
              .setUiState(UiState.UI_STATE_UNSPECIFIED)
              .setState(HealthState.UNKNOWN);
    }

    builder.setDeviceStatus(
        HealthAndActivityInfo.DeviceStatus.newBuilder()
            .setStatus(status)
            .setIsCritical(
                builder.getUiState() == UiState.ERROR || builder.getUiState() == UiState.BLOCKED));

    if (Timestamps.isValid(lastInServiceTime) && lastInServiceTime.getSeconds() > 0) {
      builder.setLastInServiceTime(lastInServiceTime);
    }

    if (status.equals(DeviceStatus.BUSY.name())) {
      boolean isRecovering = health.category() == HealthCategory.HEALTH_CATEGORY_IN_AUTO_RECOVERY;
      CurrentTask.Builder currentTask =
          CurrentTask.newBuilder().setType(isRecovering ? "Recovery Task" : "Test");
      runningTest.ifPresent(info -> currentTask.setTaskId(info.testId()).setJobId(info.jobId()));
      builder.setCurrentTask(currentTask);
    }

    return builder.build();
  }

  private static String transitionSubtitle(String status) {
    return switch (status) {
      case "INIT" -> "The device is initializing and should be ready shortly.";
      case "PREPPING" ->
          "The device is preparing for a task (e.g. low battery or storage) and is temporarily"
              + " unavailable.";
      case "LAMEDUCK" ->
          "The device is draining and finishing current work before its host is updated.";
      case "DIRTY" -> "The device is cleaning up after a task and should be ready shortly.";
      case "DYING" -> "The device is shutting down (e.g. rebooting) and should return shortly.";
      default -> "The device is temporarily unavailable and should return to service shortly.";
    };
  }

  private static Diagnostics buildNeedsRepairDiagnostics(
      String status,
      ImmutableList<DeviceType> feDeviceTypes,
      boolean hasAbnormalTypes,
      List<String> types) {
    Diagnostics.Builder diagnosticsBuilder = Diagnostics.newBuilder();
    StringBuilder diagnosisBuilder = new StringBuilder();
    StringBuilder explanationBuilder = new StringBuilder();
    StringBuilder actionBuilder = new StringBuilder();

    if (types.isEmpty()) {
      diagnosisBuilder.append("The device has no type detected.\n");
      explanationBuilder.append(
          "OmniLab cannot determine the device type, which is essential for test allocation.\n");
      actionBuilder.append(
          "Check the device's connection and ensure it is recognized by the system.\n");
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
          "These types indicate a problem with the device's state, such as being disconnected or in"
              + " a bad state.\n");
      actionBuilder.append(
          "Investigate the specific abnormal types to understand the root cause. Check device logs"
              + " and physical state.\n");
    }

    diagnosisBuilder.append("The device status is ").append(status).append(".\n");
    switch (status) {
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
      default -> explanationBuilder.append("The device requires manual attention.\n");
    }

    diagnosticsBuilder
        .setDiagnosis(diagnosisBuilder.toString().trim())
        .setExplanation(explanationBuilder.toString().trim());
    if (actionBuilder.length() > 0) {
      diagnosticsBuilder.setSuggestedAction(actionBuilder.toString().trim());
    }
    return diagnosticsBuilder.build();
  }

  private Optional<RunningTestInfo> lookUpRunningTest(DeviceInfo deviceInfo) {
    String deviceId = deviceInfo.getDeviceLocator().getId();
    try {
      return runningTestInfoProvider
          .getRunningTest(deviceId)
          .get(MOSS_LOOKUP_TIMEOUT.toMillis(), MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.log(
          Level.WARNING, "Interrupted looking up running test for BUSY device " + deviceId, e);
    } catch (ExecutionException | TimeoutException e) {
      // Best-effort: log and continue so the device detail page still renders.
      logger.log(Level.WARNING, "Failed to look up running test for BUSY device " + deviceId, e);
    }
    return Optional.empty();
  }
}
