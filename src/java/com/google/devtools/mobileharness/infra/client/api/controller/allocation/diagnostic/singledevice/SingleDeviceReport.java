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

package com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.singledevice;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.DeviceFilter;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.Report;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Allocation diagnostic report for a job when it fails to allocate any device.
 *
 * <p>Never try to use this report when your job can actually allocate devices. Otherwise, the
 * report message will be misleading.
 */
@NotThreadSafe
public class SingleDeviceReport implements Report {

  private final JobScheduleUnit job;
  @Nullable private final SingleDeviceAssessment overallAssessment;
  private final Map<String, SingleDeviceAssessment> deviceIdsToAssessments = new HashMap<>();
  private final ListMultimap<Integer, String> scoresToDeviceIds = ArrayListMultimap.create();
  private final boolean noPerfectCandidate;
  private final int maxCandidateType;

  /**
   * Create an allocation report for a job when it fails to allocate any device.
   *
   * @param overallAssessment Overall assessment of the job with all the device candidates, for
   *     quickly identify the job requirements which can't be supported by any devices. If overall
   *     assessment is null, means no candidate found after applying {@link DeviceFilter}.
   */
  SingleDeviceReport(
      JobScheduleUnit job,
      @Nullable SingleDeviceAssessment overallAssessment,
      boolean noPerfectCandidate) {
    this.job = Preconditions.checkNotNull(job);
    this.overallAssessment = overallAssessment;
    this.noPerfectCandidate = noPerfectCandidate;
    this.maxCandidateType = 30;
  }

  /** Returns the score of the overall assessment. */
  int getOverallScore() {
    return overallAssessment == null
        ? SingleDeviceAssessment.MIN_SCORE
        : overallAssessment.getScore();
  }

  /**
   * Returns the overall assessment. For quickly identify the job requirements which can't be
   * supported by any devices. If overall assessment is absent, means no device candidate found.
   */
  Optional<SingleDeviceAssessment> getOverallAssessment() {
    return Optional.ofNullable(overallAssessment);
  }

  /**
   * Saves/Updates the assessment of a single device for the current job. Individual device is only
   * assessed when the overall assessment of the job is lower than {@link
   * SingleDeviceAssessment#MAX_SCORE}.
   */
  void setDeviceAssessment(String deviceId, SingleDeviceAssessment deviceAssessment) {
    SingleDeviceAssessment preDeviceAssessment =
        deviceIdsToAssessments.put(deviceId, deviceAssessment);
    if (preDeviceAssessment != null) {
      scoresToDeviceIds.remove(preDeviceAssessment.getScore(), deviceId);
    }
    scoresToDeviceIds.put(deviceAssessment.getScore(), deviceId);
  }

  /**
   * Retrieves the assessment of a single device for the current job. Individual device is only
   * assessed when the overall assessment of the job is lower than {@link
   * SingleDeviceAssessment#MAX_SCORE}.
   */
  Optional<SingleDeviceAssessment> getDeviceAssessment(String deviceId) {
    return Optional.ofNullable(deviceIdsToAssessments.get(deviceId));
  }

  /** Returns whether there's at least one device that can match all user requirement. */
  @Override
  public boolean hasPerfectMatch() {
    return scoresToDeviceIds.containsKey(SingleDeviceAssessment.MAX_SCORE);
  }

  Collection<String> getPerfectMatchDevices() {
    return scoresToDeviceIds.get(SingleDeviceAssessment.MAX_SCORE);
  }

  /** Generates the result with the human readable report, as well as the allocation error type. */
  @Override
  public Result getResult() {
    // For shared pool skip diagnostic.
    JobType jobType = job.type();

    if (overallAssessment == null) {
      if (jobType.getDevice().equals("AndroidLocalEmulator")) {
        return Result.create(
            InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR,
            "No "
                + jobType.getDevice()
                + " found"
                + getDiagnosticCandidateFilterSuffix()
                + " Please see go/omnilab-why-test-not-assigned#no-androidlocalemulator-found for"
                + " solution.",
            null);
      }
      return Result.create(
          InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR,
          "No " + jobType.getDevice() + " found" + getDiagnosticCandidateFilterSuffix(),
          null);
    }

    StringBuilder report = new StringBuilder();

    MobileHarnessException cause = null;
    if (overallAssessment.getScore() < SingleDeviceAssessment.MAX_SCORE) {
      if (!overallAssessment.isAccessible()) {
        String msg =
            String.format(
                "You (%s) don't have access to any %s%s. Please use the 'run_as' flag to specify"
                    + " an authorized MDB group you are in, or contact the device owners"
                    + " to request access.\n",
                job.jobUser().getRunAs(),
                jobType.getDevice(),
                getDiagnosticCandidateFilterSuffix());
        report.append(msg);
        cause =
            new MobileHarnessException(
                InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR_DEVICE_NO_ACCESS, msg);
      }
      if (!overallAssessment.isDriverSupported()) {
        report.append(
            String.format(
                "No %s can support driver %s%s.\n",
                jobType.getDevice(), jobType.getDriver(), getDiagnosticCandidateFilterSuffix()));
      }
      if (!overallAssessment.isDeviceTypeSupported()) {
        report.append(
            String.format(
                "No %s can support device type %s%s.\n",
                jobType.getDevice(), jobType.getDevice(), getDiagnosticCandidateFilterSuffix()));
      }
      if (!overallAssessment.isDecoratorsSupported()) {
        report.append(
            String.format(
                "No %s can support decorators %s%s.\n",
                jobType.getDevice(),
                overallAssessment.getUnsupportedDecorators(),
                getDiagnosticCandidateFilterSuffix()));
      }
      if (!overallAssessment.isDimensionsSupported()) {
        String msg =
            String.format(
                "No %s can support job dimensions %s%s.\n",
                jobType.getDevice(),
                overallAssessment.getUnsupportedDimensions(),
                getDiagnosticCandidateFilterSuffix());
        report.append(msg);
        if (cause == null) { // Do not override the cause
          cause =
              new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR_DEVICE_NOT_EXIST, msg);
        }
      }
      if (!overallAssessment.isDimensionsSatisfied()) {
        report.append(
            String.format(
                "Job does not satisfy the required dimensions" + " of any %s %s%s.\n",
                jobType.getDevice(),
                overallAssessment.getUnsatisfiedDimensions(),
                getDiagnosticCandidateFilterSuffix()));
      }
      if (!overallAssessment.isIdle()) {
        report.append(
            String.format(
                "No IDLE %s%s. Please extend the timeout settingsx to wait longer.\n",
                jobType.getDevice(), getDiagnosticCandidateFilterSuffix()));
      }

      return Result.create(
          InfraErrorId.CLIENT_JR_MNM_ALLOC_DEVICE_NOT_SATISFY_SLO, report.toString(), cause);
    }

    // Checks whether there are any devices can support all requirements.
    Collection<String> goodIds = scoresToDeviceIds.get(SingleDeviceAssessment.MAX_SCORE);
    if (!goodIds.isEmpty()) {
      if (job.setting().getTimeout().getStartTimeoutMs() < Duration.ofSeconds(60).toMillis()) {
        return Result.create(
            InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR,
            report
                .append(
                    String.format(
                        "MH failed to allocate any devices within %d ms. "
                            + "Please increase your start_timeout setting "
                            + "to >60 seconds and try again",
                        job.setting().getTimeout().getStartTimeoutMs()))
                .toString(),
            null);
      } else if (noPerfectCandidate) {
        return Result.create(
            InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR,
            report
                .append(
                    String.format(
                        "MH failed to find suitable device with allocation exit strategy %s."
                            + " Consider to use another allocation exit strategy.",
                        job.setting().getAllocationExitStrategy()))
                .toString(),
            null);
      } else {
        report
            .append("Your job should be able to allocate the following ")
            .append(goodIds.size())
            .append(
                " devices but MH somehow failed to allocate them. Please try again."
                    + " If you still see this error after retrying,"
                    + " please file a bug against the Mobile Harness team via go/mh-bug:\n - ")
            .append(Joiner.on("\n - ").join(goodIds.stream().limit(maxCandidateType).iterator()));
        if (goodIds.size() > maxCandidateType) {
          report
              .append("\n - ...(truncated ")
              .append(goodIds.size() - maxCandidateType)
              .append(" devices)...");
        }
        return Result.create(InfraErrorId.CLIENT_JR_ALLOC_INFRA_ERROR, report.toString(), null);
      }
    }

    // Gives suggestions.
    // And also generates a cause with the best guess.
    List<String> candidateTypes = new ArrayList<>(maxCandidateType);
    for (int score = SingleDeviceAssessment.MAX_SCORE - 1;
        score >= SingleDeviceAssessment.MIN_SCORE;
        score--) {
      Collection<String> ids = scoresToDeviceIds.get(score);
      if (ids.isEmpty()) {
        continue;
      }
      // Groups the devices with the same error together as one candidate type.
      ListMultimap<String, String> errorToIds = LinkedListMultimap.create();
      for (String id : ids) {
        SingleDeviceAssessment assessment = deviceIdsToAssessments.get(id);
        if (assessment != null) {
          StringBuilder error = new StringBuilder();
          error.append("============ Score ").append(score).append(" ============\nErrors:");
          if (!assessment.isAccessible()) {
            error
                .append("\n - NO_ACCESS (current user: ")
                .append(job.jobUser().getRunAs())
                .append(")");
            if (cause == null) {
              String msg =
                  String.format(
                      "NO_ACCESS (current user: %s) for device %s.", job.jobUser().getRunAs(), id);
              cause =
                  new MobileHarnessException(
                      InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR_DEVICE_NO_ACCESS, msg);
            }
          }
          if (assessment.isPotentialAccessible()) {
            error
                .append(
                    "\n - POTENTIAL_ACCESS: The device owner is the default value. Need to change"
                        + " to the current user: ")
                .append(job.jobUser().getRunAs());
            if (cause == null) {
              String msg =
                  String.format(
                      "POTENTIAL_ACCESS: The device %s owner is the default value. Need to change "
                          + " to the current user: %s",
                      id, job.jobUser().getRunAs());
              cause =
                  new MobileHarnessException(
                      InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR_DEVICE_NO_ACCESS, msg);
            }
          }
          if (!assessment.isDriverSupported()) {
            error.append("\n - DRIVER_NOT_SUPPORTED: ").append(jobType.getDriver());
          }
          if (!assessment.isDeviceTypeSupported()) {
            error.append("\n - DEVICE_TYPE_NOT_SUPPORTED: ").append(jobType.getDevice());
          }
          if (!assessment.isDecoratorsSupported()) {
            error
                .append("\n - DECORATORS_NOT_SUPPORTED: ")
                .append(assessment.getUnsupportedDecorators());
          }
          if (!assessment.isDimensionsSupported()) {
            error
                .append("\n - DIMENSIONS_NOT_SUPPORTED: ")
                .append(assessment.getUnsupportedDimensions());
          }
          if (!assessment.isDimensionsSatisfied()) {
            error
                .append("\n - DIMENSIONS_NOT_SATISFIED: ")
                .append(assessment.getUnsatisfiedDimensions());
          }
          if (assessment.isMissing()) {
            error.append("\n - DEVICE_IS_MISSING");
            if (cause == null) {
              String msg = String.format("DEVICE_IS_MISSING for device %s.", id);
              cause =
                  new MobileHarnessException(
                      InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR_DEVICE_MISSING, msg);
            }
          } else if (!assessment.isIdle()) {
            error.append("\n - NOT_IDLE");
            if (cause == null) {
              String msg = String.format("NOT_IDLE for device %s.", id);
              cause =
                  new MobileHarnessException(
                      InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR_DEVICE_BUSY, msg);
            }
          }
          errorToIds.put(error.toString(), id);
        }
      }

      // Collects all the candidate types of the same score.
      for (String error : errorToIds.keySet()) {
        List<String> idsWithSameError = errorToIds.get(error);
        StringBuilder candidateType = new StringBuilder();
        candidateType
            .append(error)
            .append("\nCandidates:\n - ")
            .append(Joiner.on("\n - ").join(idsWithSameError.stream().limit(2).iterator()));
        if (idsWithSameError.size() > 2) {
          candidateType
              .append("\n - (truncated ")
              .append(idsWithSameError.size() - 2)
              .append(" devices)");
        }
        candidateTypes.add(candidateType.toString());
        if (candidateTypes.size() >= maxCandidateType) {
          break;
        }
      }
      if (candidateTypes.size() >= maxCandidateType) {
        break;
      }
    }

    // No candidate found.
    if (candidateTypes.isEmpty()) {
      return Result.create(
          InfraErrorId.CLIENT_JR_ALLOC_INFRA_ERROR,
          "Diagnostician can not determine why devices were not allocated.",
          null);
    }

    // Print the candidate types.
    report
        .append(
            "No device can meet all of your requirements."
                + " Did you mean to use one of the following devices:\n")
        .append(Joiner.on("\n").join(candidateTypes));
    if (candidateTypes.size() >= maxCandidateType) {
      report.append("\n==== (truncated other candidate devices) ====");
    }
    return Result.create(InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR, report.toString(), cause);
  }

  /** A postfix about diagnostic candidate filter in the report. */
  private String getDiagnosticCandidateFilterSuffix() {
    return "";
  }
}
