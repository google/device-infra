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

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.AllocationDiagnostician;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.DeviceFilter;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.Report;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier.LabQueryResult;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import java.util.List;
import java.util.Optional;

/** An {@link AllocationDiagnostician} for multiple device jobs. */
public final class MultiDeviceDiagnostician implements AllocationDiagnostician {

  private final LabAssessor assessor;
  private final DeviceFilter deviceFilter;
  private final DeviceQuerier deviceQuerier;
  private final JobScheduleUnit job;

  private volatile LabReport lastReport;

  public MultiDeviceDiagnostician(JobScheduleUnit job, DeviceQuerier deviceQuerier) {
    this(new LabAssessor(), new DeviceFilter(), deviceQuerier, job);
  }

  @VisibleForTesting
  MultiDeviceDiagnostician(
      LabAssessor assessor,
      DeviceFilter deviceFilter,
      DeviceQuerier deviceQuerier,
      JobScheduleUnit job) {
    this.assessor = assessor;
    this.deviceFilter = deviceFilter;
    this.deviceQuerier = deviceQuerier;
    this.job = job;
  }

  /**
   * @see {@link AllocationDiagnostician#getLastReport()}
   */
  @Override
  public Optional<Report> getLastReport() {
    return Optional.ofNullable(lastReport);
  }

  @Override
  public void logExtraInfo() {}

  /**
   * @see {@link AllocationDiagnostician#diagnoseJob()}
   */
  @Override
  public LabReport diagnoseJob(boolean noPerfectCandidate)
      throws MobileHarnessException, InterruptedException {
    DeviceQueryFilter filter = deviceFilter.getFilter(job);
    List<LabQueryResult> labResults = deviceQuerier.queryDevicesByLab(filter);
    LabReport report = new LabReport(job);
    for (LabQueryResult labResult : labResults) {
      if (labResult.devices().size() < job.subDeviceSpecs().getSubDeviceCount()) {
        continue;
      }
      report.addLabAssessment(assessor.assess(job, labResult));
    }

    // If the current report indicates there should be a match but an earlier
    // report said there was not, the match must have become available after the job was done
    // waiting. The previous report should be returned to reflect what was happening when the job
    // was actually waiting.
    if (lastReport != null && report.hasPerfectMatch() && !lastReport.hasPerfectMatch()) {
      return lastReport;
    }
    lastReport = report;
    return report;
  }
}
