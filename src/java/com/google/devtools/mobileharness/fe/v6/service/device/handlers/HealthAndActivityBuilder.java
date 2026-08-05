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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.HealthCategory;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.device.provider.RunningTestInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.device.provider.RunningTestInfoProvider.RunningTestInfo;
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
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Builder for the {@link HealthAndActivityInfo} proto.
 *
 * <p>Classifies a device into one of the four standard health categories (In Service, In
 * Transition, Auto Recovery, Needs Manual Repair), plus a Quarantined special case. The category is
 * not exposed on the wire: it is surfaced to the frontend through the {@code title} text and a
 * presentation-only {@link UiState}, per the FE v6 BFF principle.
 *
 * <p>During the frontend/backend release transition the builder also dual-writes the transitional
 * {@code state} ({@link HealthState}) so a frontend on the other side of a skew window still
 * renders an icon. The dual-write is removed by a follow-up cleanup CL.
 */
public final class HealthAndActivityBuilder {

  private static final Logger logger = Logger.getLogger(HealthAndActivityBuilder.class.getName());

  /** Timeout for the MOSS running-test lookup (best-effort, non-blocking for the page). */
  private static final Duration MOSS_LOOKUP_TIMEOUT = Duration.ofSeconds(5);

  /** Device type substrings that indicate the device is in a bad state. Device-family-agnostic. */
  private static final ImmutableSet<String> ABNORMAL_TYPE_KEYWORDS =
      ImmutableSet.of(
          "FAILED",
          "ABNORMAL",
          "DISCONNECTED",
          "OFFLINE",
          "UNAUTHORIZED",
          "FASTBOOT",
          "FASTBOOTDMODE");

  /** Statuses in which the device is temporarily unavailable but expected to self-recover. */
  private static final ImmutableSet<String> TRANSIT_STATUSES =
      ImmutableSet.of(
          DeviceStatus.INIT.name(),
          DeviceStatus.PREPPING.name(),
          DeviceStatus.LAMEDUCK.name(),
          DeviceStatus.DIRTY.name(),
          DeviceStatus.DYING.name());

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

    // Look up the running test for BUSY devices (populates current_task with test/job id).
    Optional<RunningTestInfo> runningTest = Optional.empty();
    if (status.equals(DeviceStatus.BUSY.name())) {
      runningTest = lookUpRunningTest(deviceInfo);
    }

    HealthCategory category =
        resolveHealthCategory(deviceInfo, status, !types.isEmpty(), hasAbnormalTypes);

    if (isQuarantined && status.equals(DeviceStatus.IDLE.name())) {
      // Quarantine is an FE-detected special case: a healthy device manually withheld from tests.
      // It bypasses the health category entirely.
      builder
          .setTitle("Quarantined")
          .setSubtitle("Device is idle, but quarantined and unavailable for tests.")
          .setUiState(UiState.BLOCKED)
          .setState(HealthState.IDLE_BUT_QUARANTINED)
          .setDiagnostics(
              Diagnostics.newBuilder()
                  .setDiagnosis("Device has been manually quarantined while in IDLE state.")
                  .setExplanation(
                      "Quarantined devices cannot be allocated for tests until they are"
                          + " unquarantined, even if otherwise healthy."));
    } else {
      switch (category) {
        case HEALTH_CATEGORY_IN_SERVICE -> {
          if (status.equals(DeviceStatus.BUSY.name())) {
            builder
                .setTitle("In Service (Busy)")
                .setSubtitle("The device is healthy and currently running a task.")
                .setUiState(UiState.BUSY)
                .setState(HealthState.IN_SERVICE_BUSY);
          } else {
            builder
                .setTitle("In Service (Idle)")
                .setSubtitle("The device is healthy and ready for new tasks.")
                .setUiState(UiState.HEALTHY)
                .setState(HealthState.IN_SERVICE_IDLE);
          }
        }
        case HEALTH_CATEGORY_IN_TRANSITION ->
            builder
                .setTitle("In Transition (" + transitionDetail(status) + ")")
                .setSubtitle(transitionSubtitle(status))
                .setUiState(UiState.TRANSITIONING)
                .setState(HealthState.OUT_OF_SERVICE_TEMP_MAINT)
                .setDiagnostics(
                    Diagnostics.newBuilder()
                        .setDiagnosis("The device is in a transition state (" + status + ").")
                        .setExplanation(transitionSubtitle(status)));
        case HEALTH_CATEGORY_IN_AUTO_RECOVERY ->
            builder
                .setTitle("Auto Recovery")
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
        case HEALTH_CATEGORY_NEED_MANUAL_REPAIR ->
            builder
                .setTitle("Needs Manual Repair")
                .setSubtitle("The device is in an error state and requires attention.")
                .setUiState(UiState.ERROR)
                .setState(HealthState.OUT_OF_SERVICE_NEEDS_FIXING)
                .setDiagnostics(
                    buildNeedsRepairDiagnostics(status, feDeviceTypes, hasAbnormalTypes, types));
        default ->
            builder
                .setTitle("Unknown")
                .setSubtitle("The device's health could not be determined.")
                .setUiState(UiState.UI_STATE_UNSPECIFIED)
                .setState(HealthState.UNKNOWN);
      }
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
      boolean isRecovering = category == HealthCategory.HEALTH_CATEGORY_IN_AUTO_RECOVERY;
      CurrentTask.Builder currentTask =
          CurrentTask.newBuilder().setType(isRecovering ? "Recovery Task" : "Test");
      runningTest.ifPresent(info -> currentTask.setTaskId(info.testId()).setJobId(info.jobId()));
      builder.setCurrentTask(currentTask);
    }

    return builder.build();
  }

  /**
   * Resolves the health category of the device.
   *
   * <p>Prefers upstream {@link HealthCategory} from {@link DeviceInfo} (populated by 1P Master
   * GetLabInfo). If absent or unspecified (e.g. non-Android devices, ATS LabInfo), falls back to
   * local {@link #computeCategory}.
   */
  private static HealthCategory resolveHealthCategory(
      DeviceInfo deviceInfo, String status, boolean hasTypes, boolean hasAbnormalTypes) {
    if (deviceInfo.hasHealthCategory()
        && deviceInfo.getHealthCategory() != HealthCategory.HEALTH_CATEGORY_UNSPECIFIED) {
      return deviceInfo.getHealthCategory();
    }
    return computeCategory(status, hasTypes, hasAbnormalTypes);
  }

  /**
   * Classifies the device into a standard health category. Status- and type-driven only, so it
   * applies uniformly to every device family (Android, iOS, emulator/virtual, testbed, misc).
   *
   * <p>TODO: Retire this method once calculateHealthCategory(status, type) is moved to a shared
   * package and supports non-Android device families.
   */
  private static HealthCategory computeCategory(
      String status, boolean hasTypes, boolean hasAbnormalTypes) {
    boolean idleOrBusy =
        status.equals(DeviceStatus.IDLE.name()) || status.equals(DeviceStatus.BUSY.name());
    // A device with no detected type cannot be allocated, so it is only "In Service" when it has at
    // least one (non-abnormal) type.
    if (idleOrBusy && hasTypes && !hasAbnormalTypes) {
      return HealthCategory.HEALTH_CATEGORY_IN_SERVICE;
    }
    if (TRANSIT_STATUSES.contains(status)) {
      return HealthCategory.HEALTH_CATEGORY_IN_TRANSITION;
    }
    // BUSY + abnormal type = auto recovery. The upstream standard treats any BUSY device with
    // unhealthy device types as being in automated recovery, without inspecting the running job.
    if (status.equals(DeviceStatus.BUSY.name()) && hasAbnormalTypes) {
      return HealthCategory.HEALTH_CATEGORY_IN_AUTO_RECOVERY;
    }
    // Not BUSY, or BUSY without abnormal types but missing types entirely.
    // No detected type also needs a human: the device cannot be allocated.
    if (status.equals(DeviceStatus.FAILED.name())
        || status.equals(DeviceStatus.MISSING.name())
        || hasAbnormalTypes
        || !hasTypes) {
      return HealthCategory.HEALTH_CATEGORY_NEED_MANUAL_REPAIR;
    }
    return HealthCategory.HEALTH_CATEGORY_UNSPECIFIED;
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

  private static boolean isTypeAbnormal(String type) {
    String upperType = Ascii.toUpperCase(type);
    for (String keyword : ABNORMAL_TYPE_KEYWORDS) {
      if (upperType.contains(keyword)) {
        return true;
      }
    }
    return false;
  }
}
