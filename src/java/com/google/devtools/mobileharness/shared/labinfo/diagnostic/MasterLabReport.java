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

package com.google.devtools.mobileharness.shared.labinfo.diagnostic;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Map.Entry.comparingByKey;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceRequirement;
import com.google.devtools.mobileharness.shared.labinfo.diagnostic.MasterLabAssessment.DeviceCandidate;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.DiagnoseJobSpec;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

/** A report for a lab host and its ability to satisfy multi-device job requirements. */
public final class MasterLabReport {

  private static final String INDENT = "    ";
  private static final int MAX_DEVICES_PER_REQUIREMENT = 5;
  static final int MAX_LABS = 15;

  // Copied from client-side Report interface to avoid dependency.
  public static final String DECORATORS_NOT_SUPPORTED = "DECORATORS_NOT_SUPPORTED";
  public static final String DIMENSIONS_NOT_SATISFIED = "DIMENSIONS_NOT_SATISFIED";
  public static final String DIMENSIONS_NOT_SUPPORTED = "DIMENSIONS_NOT_SUPPORTED";
  public static final String DRIVER_NOT_SUPPORTED = "DRIVER_NOT_SUPPORTED";
  public static final String DEVICE_TYPE_NOT_SUPPORTED = "DEVICE_TYPE_NOT_SUPPORTED";
  public static final String NO_ACCESS = "NO_ACCESS";
  public static final String NOT_IDLE = "NOT_IDLE";
  public static final String MISSING = "DEVICE_IS_MISSING";
  public static final String LINE_SEPARATOR = "========================================";

  /** Type of error in lab report. */
  public enum ErrorType {
    UNKNOWN,
    INFRA_ERROR,
    USER_CONFIG_ERROR
  }

  /** Result of lab report analysis. */
  @AutoValue
  public abstract static class Result {
    public static Result create(ErrorType errorType, String readableReport) {
      return new AutoValue_MasterLabReport_Result(errorType, readableReport);
    }

    public abstract ErrorType errorType();

    public abstract String readableReport();
  }

  private final DiagnoseJobSpec spec;
  private final Duration startTimeout;
  private final PriorityQueue<MasterLabAssessment> assessments;
  private final Map<String, MasterLabAssessment> hostToAssessments;

  public MasterLabReport(DiagnoseJobSpec spec) {
    this.spec = spec;
    this.startTimeout = TimeUtils.toJavaDuration(spec.getStartTimeout());
    this.assessments = new PriorityQueue<>((lab1, lab2) -> lab1.getScore() - lab2.getScore());
    this.hostToAssessments = new HashMap<>();
  }

  public boolean hasPerfectMatch() {
    return assessments.stream().anyMatch(MasterLabAssessment::hasMaxScore);
  }

  @CanIgnoreReturnValue
  public MasterLabReport addLabAssessment(MasterLabAssessment labAssessment, boolean isFirstRound) {
    String hostname = labAssessment.getHostname();
    if (isFirstRound) {
      assessments.add(labAssessment);
      hostToAssessments.put(hostname, labAssessment);
      if (assessments.size() > MAX_LABS) {
        MasterLabAssessment removedLabAssessment = assessments.poll();
        hostToAssessments.remove(removedLabAssessment.getHostname());
      }
    } else {
      MasterLabAssessment existingLabAssessment = hostToAssessments.get(hostname);
      if (existingLabAssessment != null) {
        if (labAssessment.getScore() < existingLabAssessment.getScore()) {
          assessments.remove(existingLabAssessment);
          assessments.add(labAssessment);
        }
      }
    }
    return this;
  }

  @VisibleForTesting
  ImmutableList<MasterLabAssessment> getSortedAssessments() {
    return ImmutableList.sortedCopyOf(
        (lab1, lab2) -> lab2.getScore() - lab1.getScore(), assessments);
  }

  public Result getResult() {
    if (assessments.isEmpty()) {
      return Result.create(
          ErrorType.USER_CONFIG_ERROR,
          "There are no device supporting the user requested device type.");
    }

    ImmutableList<MasterLabAssessment> labs = getSortedAssessments();
    StringBuilder readableReport = new StringBuilder();
    List<DeviceRequirement> specs = spec.getDeviceRequirements().getDeviceRequirementList();

    writeRequirements(readableReport, specs);

    List<MasterLabAssessment> perfectLabs = getPerfectLabs(labs);
    if (!perfectLabs.isEmpty()) {
      if (startTimeout != null
          && !startTimeout.isZero()
          && startTimeout.toMillis() < Duration.ofSeconds(60).toMillis()) {
        return Result.create(
            ErrorType.USER_CONFIG_ERROR,
            String.format(
                "OmniLab failed to allocate any devices within %d ms. "
                    + "Please increase your start_timeout setting "
                    + "to >60 seconds and try again.\n",
                startTimeout.toMillis()));
      }
      readableReport.append(
          "Your job should be able to allocate devices on any of the following lab hosts but"
              + " OmniLab failed to allocate them. Please try again. \n\n");
      writeLabs(readableReport, specs, perfectLabs);
      return Result.create(ErrorType.INFRA_ERROR, readableReport.toString());
    }

    readableReport.append(
        String.format(
            "No lab host was able to satisfy all requirements. These are the top %d closest"
                + " matches.\n\n",
            labs.size()));
    writeLabs(readableReport, specs, labs);
    return Result.create(ErrorType.USER_CONFIG_ERROR, readableReport.toString());
  }

  private static void writeRequirements(
      StringBuilder readableReport, List<DeviceRequirement> specs) {
    readableReport.append("Given the following device requirements:\n\n");
    for (int i = 0; i < specs.size(); i++) {
      readableReport.append(
          String.format(
              "Requirement %d:\n%s\n\n",
              i + 1, DeviceRequirementFormatter.create(specs.get(i)).toJson()));
    }
  }

  private void writeLabs(
      StringBuilder readableReport, List<DeviceRequirement> specs, List<MasterLabAssessment> labs) {
    for (MasterLabAssessment lab : labs) {
      readableReport.append(
          String.format(
              "%s Score %d %s\n\nHostname: %s\n\n",
              LINE_SEPARATOR, lab.getScore(), LINE_SEPARATOR, lab.getHostname()));
      for (int i = 0; i < specs.size(); i++) {
        writeDevicesForRequirement(readableReport, specs.get(i), lab, i + 1);
      }
    }
  }

  private void writeDevicesForRequirement(
      StringBuilder readableReport,
      DeviceRequirement spec,
      MasterLabAssessment lab,
      int requirementNumber) {
    MasterSingleDeviceAssessment overallAssessment = lab.getOverallDeviceAssessment(spec);
    if (!overallAssessment.hasMaxScore()) {
      readableReport.append(
          String.format(
              "Requirement %d: No device can satisfy the following requirements:\n",
              requirementNumber));
      writeDeviceErrors(readableReport, overallAssessment, INDENT);
      readableReport.append("\n");
      return;
    }

    ImmutableList<DeviceCandidate> candidates =
        lab.getTopCandidates(spec, MAX_DEVICES_PER_REQUIREMENT);
    if (candidates.get(0).assessment().hasMaxScore()) {
      List<DeviceCandidate> perfectCandidates =
          candidates.stream()
              .filter(candidate -> candidate.assessment().hasMaxScore())
              .collect(Collectors.toList());
      readableReport.append(
          String.format(
              "Requirement %d can be fulfilled with the following devices:\n", requirementNumber));
      for (DeviceCandidate candidate : perfectCandidates) {
        readableReport.append(String.format("%s- %s\n", INDENT, candidate.id()));
      }

      List<DeviceCandidate> imperfectCandidates =
          candidates.stream()
              .filter(candidate -> !candidate.assessment().hasMaxScore())
              .collect(Collectors.toList());
      if (!imperfectCandidates.isEmpty()) {
        readableReport.append(
            String.format("Other candidates for requirement %d:\n", requirementNumber));
        for (DeviceCandidate candidate : imperfectCandidates) {
          readableReport.append(String.format("%s- %s\n", INDENT, candidate.id()));
          writeDeviceErrors(readableReport, candidate.assessment(), INDENT + INDENT);
        }
      }

      readableReport.append("\n");
      return;
    }

    readableReport.append(String.format("Requirement %d top candidates:\n", requirementNumber));
    for (DeviceCandidate candidate : candidates) {
      readableReport.append(String.format("%s- %s\n", INDENT, candidate.id()));
      writeDeviceErrors(readableReport, candidate.assessment(), INDENT + INDENT);
    }
    readableReport.append("\n");
  }

  private void writeDeviceErrors(
      StringBuilder readableReport, MasterSingleDeviceAssessment deviceAssessment, String indent) {
    if (!deviceAssessment.isAccessible()) {
      readableReport.append(
          String.format(
              "%s- %s (current user: %s)\n", indent, NO_ACCESS, spec.getUser().getRunAs()));
    }
    if (!deviceAssessment.isDriverSupported()) {
      readableReport.append(String.format("%s- %s\n", indent, DRIVER_NOT_SUPPORTED));
    }
    if (!deviceAssessment.isDeviceTypeSupported()) {
      readableReport.append(String.format("%s- %s\n", indent, DEVICE_TYPE_NOT_SUPPORTED));
    }
    if (!deviceAssessment.isDecoratorsSupported()) {
      readableReport.append(
          String.format(
              "%s- %s: %s\n",
              indent,
              DECORATORS_NOT_SUPPORTED,
              deviceAssessment.getUnsupportedDecorators().stream()
                  .sorted()
                  .collect(Collectors.toList())));
    }
    if (!deviceAssessment.isDimensionsSupported()) {
      readableReport.append(
          String.format(
              "%s- %s: %s\n",
              indent,
              DIMENSIONS_NOT_SUPPORTED,
              sortedMap(deviceAssessment.getUnsupportedDimensions())));
    }
    if (!deviceAssessment.isDimensionsSatisfied()) {
      Map<String, String> unsatisfiedDimensions = new HashMap<>();
      for (Map.Entry<String, String> entries :
          deviceAssessment.getUnsatisfiedDimensions().entries()) {
        unsatisfiedDimensions.put(entries.getKey(), entries.getValue());
      }
      readableReport.append(
          String.format(
              "%s- %s: %s \n", indent, DIMENSIONS_NOT_SATISFIED, sortedMap(unsatisfiedDimensions)));
    }
    if (deviceAssessment.isMissing()) {
      readableReport.append(String.format("%s- %s\n", indent, MISSING));
    } else if (!deviceAssessment.isIdle()) {
      readableReport.append(String.format("%s- %s\n", indent, NOT_IDLE));
    }
  }

  private static List<MasterLabAssessment> getPerfectLabs(List<MasterLabAssessment> labs) {
    return labs.stream().filter(MasterLabAssessment::hasMaxScore).collect(Collectors.toList());
  }

  private static Map<String, String> sortedMap(Map<String, String> map) {
    return map.entrySet().stream()
        .sorted(comparingByKey())
        .collect(
            toImmutableMap(
                Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue));
  }

  @AutoValue
  abstract static class DeviceRequirementFormatter {
    abstract String type();

    abstract ImmutableMap<String, String> dimensions();

    abstract ImmutableList<String> decorators();

    static DeviceRequirementFormatter create(DeviceRequirement spec) {
      return new AutoValue_MasterLabReport_DeviceRequirementFormatter(
          spec.getDeviceType(),
          ImmutableMap.copyOf(sortedMap(spec.getDimensionsMap())),
          ImmutableList.sortedCopyOf(spec.getDecoratorList()));
    }

    String toJson() {
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      return gson.toJson(this);
    }
  }
}
