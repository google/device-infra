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

package com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.multidevice;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.Report;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.multidevice.LabAssessment.DeviceCandidate;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.singledevice.SingleDeviceAssessment;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

/** A {@link Report} for a lab host and its ability to satisfy multi-device job requirements. */
public final class LabReport implements Report {

  private static final String INDENT = "    ";
  private static final int MAX_DEVICES_PER_REQUIREMENT = 5;
  static final int MAX_LABS = 15;

  private final JobScheduleUnit job;
  private final PriorityQueue<LabAssessment> assessments;

  public LabReport(JobScheduleUnit job) {
    this.job = job;
    this.assessments = new PriorityQueue<>((lab1, lab2) -> lab1.getScore() - lab2.getScore());
  }

  /**
   * @see {@link Report#hasPerfectMatch()}
   */
  @Override
  public boolean hasPerfectMatch() {
    return assessments.stream().anyMatch(LabAssessment::hasMaxScore);
  }

  /** Adds a {@link LabAssessment} to this Report to be included in the readable diagnosis. */
  @CanIgnoreReturnValue
  LabReport addLabAssessment(LabAssessment labAssessment) {
    assessments.add(labAssessment);
    if (assessments.size() > MAX_LABS) {
      assessments.poll();
    }
    return this;
  }

  @VisibleForTesting
  ImmutableList<LabAssessment> getSortedAssessments() {
    return ImmutableList.sortedCopyOf(
        (lab1, lab2) -> lab2.getScore() - lab1.getScore(), assessments);
  }

  /**
   * @see {@link Report#getResult()}
   */
  @Override
  public Report.Result getResult() {
    if (assessments.isEmpty()) {
      return Report.Result.create(
          InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR,
          "There are no device supporting the user requested device type.",
          null);
    }

    ImmutableList<LabAssessment> labs = getSortedAssessments();
    StringBuilder readableReport = new StringBuilder();
    List<SubDeviceSpec> specs = job.subDeviceSpecs().getAllSubDevices();

    writeRequirements(readableReport, specs);

    // If there are labs that can satisfy the requirement something else is probably wrong
    List<LabAssessment> perfectLabs = getPerfectLabs(labs);
    if (!perfectLabs.isEmpty()) {
      if (job.setting().getTimeout().getStartTimeoutMs() < Duration.ofSeconds(60).toMillis()) {
        return Report.Result.create(
            InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR,
            String.format(
                "MH failed to allocate any devices within %d ms. "
                    + "Please increase your start_timeout setting (refer to go/mh-timing) "
                    + "to >60 seconds and try again.\n",
                job.setting().getTimeout().getStartTimeoutMs()),
            null);
      }
      readableReport.append(
          "Your job should be able to allocate devices on any of the following lab hosts but MH "
              + "failed to allocate them. Please try again. If you still see this error after "
              + "retrying, please file a bug via go/mh-bug.\n\n");
      writeLabs(readableReport, specs, perfectLabs);
      return Report.Result.create(
          InfraErrorId.CLIENT_JR_ALLOC_INFRA_ERROR, readableReport.toString(), null);
    }

    // List the top MAX_LABS candidates
    readableReport.append(
        String.format(
            "No lab host was able to satisfy all requirements. These are the top %d closest"
                + " matches.\n\n",
            labs.size()));
    writeLabs(readableReport, specs, labs);
    return Report.Result.create(
        InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR, readableReport.toString(), null);
  }

  private static void writeRequirements(StringBuilder readableReport, List<SubDeviceSpec> specs) {
    readableReport.append("Given the following device requirements:\n\n");
    for (int i = 0; i < specs.size(); i++) {
      readableReport.append(
          String.format(
              "Requirement %d:\n%s\n\n",
              i + 1, SubDeviceSpecFormatter.create(specs.get(i)).toJson()));
    }
  }

  private void writeLabs(
      StringBuilder readableReport, List<SubDeviceSpec> specs, List<LabAssessment> labs) {
    for (LabAssessment lab : labs) {
      readableReport.append(
          String.format(
              "%s Score %d %s\n\nHostname: %s\n\n",
              Report.LINE_SEPARATOR, lab.getScore(), Report.LINE_SEPARATOR, lab.getHostname()));
      for (int i = 0; i < specs.size(); i++) {
        writeDevicesForRequirement(readableReport, specs.get(i), lab, i + 1);
      }
    }
  }

  private void writeDevicesForRequirement(
      StringBuilder readableReport, SubDeviceSpec spec, LabAssessment lab, int requirementNumber) {
    SingleDeviceAssessment overallAssessment = lab.getOverallDeviceAssessment(spec);
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

  /**
   * Writes error information for each {@link SingleDeviceAssessment}. The indent parameter allows
   * device errors to be displayed hierarchically with respect to the device serial or the
   * requirement number depending on the use case.
   */
  private void writeDeviceErrors(
      StringBuilder readableReport, SingleDeviceAssessment deviceAssessment, String indent) {
    if (!deviceAssessment.isAccessible()) {
      readableReport.append(
          String.format(
              "%s- %s (current user: %s)\n", indent, Report.NO_ACCESS, job.jobUser().getRunAs()));
    }
    if (!deviceAssessment.isDriverSupported()) {
      readableReport.append(String.format("%s- %s\n", indent, Report.DRIVER_NOT_SUPPORTED));
    }
    if (!deviceAssessment.isDeviceTypeSupported()) {
      readableReport.append(String.format("%s- %s\n", indent, Report.DEVICE_TYPE_NOT_SUPPORTED));
    }
    if (!deviceAssessment.isDecoratorsSupported()) {
      readableReport.append(
          String.format(
              "%s- %s: %s\n",
              indent,
              Report.DECORATORS_NOT_SUPPORTED,
              deviceAssessment.getUnsupportedDecorators().stream()
                  .sorted()
                  .collect(Collectors.toList())));
    }
    if (!deviceAssessment.isDimensionsSupported()) {
      readableReport.append(
          String.format(
              "%s- %s: %s\n",
              indent,
              Report.DIMENSIONS_NOT_SUPPORTED,
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
              "%s- %s: %s (see go/mh-required-dimensions)\n",
              indent, Report.DIMENSIONS_NOT_SATISFIED, sortedMap(unsatisfiedDimensions)));
    }
    if (deviceAssessment.isMissing()) {
      readableReport.append(String.format("%s- %s\n", indent, Report.MISSING));
    } else if (!deviceAssessment.isIdle()) {
      readableReport.append(String.format("%s- %s\n", indent, Report.NOT_IDLE));
    }
  }

  private static List<LabAssessment> getPerfectLabs(List<LabAssessment> labs) {
    return labs.stream().filter(LabAssessment::hasMaxScore).collect(Collectors.toList());
  }

  private static Map<String, String> sortedMap(Map<String, String> map) {
    return map.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (oldValue, newValue) -> oldValue,
                LinkedHashMap::new));
  }

  /** A utility for printing {@link SubDeviceSpec}s as JSON strings. */
  @AutoValue
  abstract static class SubDeviceSpecFormatter {
    abstract String type();

    abstract ImmutableMap<String, String> dimensions();

    abstract ImmutableList<String> decorators();

    static SubDeviceSpecFormatter create(SubDeviceSpec spec) {
      return new AutoValue_LabReport_SubDeviceSpecFormatter(
          spec.type(),
          ImmutableMap.copyOf(sortedMap(spec.dimensions().getAll())),
          ImmutableList.sortedCopyOf(spec.decorators().getAll()));
    }

    String toJson() {
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      return gson.toJson(this);
    }
  }
}
