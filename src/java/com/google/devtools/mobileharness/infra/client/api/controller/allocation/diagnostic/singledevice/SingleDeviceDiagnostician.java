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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.AllocationDiagnostician;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.DeviceFilter;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.Report;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.shared.util.sharedpool.SharedPoolJobUtil;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Value;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Job;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.model.lab.LabLocator;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DimensionFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** For diagnostic the reasons about why a job can not allocate devices. */
public class SingleDeviceDiagnostician implements AllocationDiagnostician {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * If the perfect device count is less than this value, we'll only query the perfect devices
   * instead of all the devices.
   */
  private static final int MAX_QUERY_DEVICE_COUNT = 20;

  /** The job to diagnostic with. */
  private final JobInfo job;

  /** Filters to pre-filter the device candidates of the current job. */
  private final DeviceFilter filter;

  /** For retrieving the device information for diagnostic. */
  private final DeviceQuerier querier;

  /** Assessor for providing detail assessment of the support of a group of devices for a job. */
  private final SingleDeviceAssessor assessor;

  /** The report of the previous diagnostic. */
  @Nullable private volatile SingleDeviceReport lastReport;

  private final List<SingleDeviceReport> historyReports = new ArrayList<>();

  /** Creates a diagnostician for checking the reason why a job can not allocate devices. */
  public SingleDeviceDiagnostician(JobInfo job, DeviceQuerier querier) {
    this(job, new DeviceFilter(), querier, new SingleDeviceAssessor());
  }

  @VisibleForTesting
  SingleDeviceDiagnostician(
      JobInfo job, DeviceFilter filter, DeviceQuerier querier, SingleDeviceAssessor assessor) {
    this.job = job;
    this.filter = filter;
    this.querier = querier;
    this.assessor = assessor;
  }

  /** Gets the last report if the last invocation of {@link #diagnoseJob()}. */
  @Override
  public Optional<Report> getLastReport() {
    return Optional.ofNullable(lastReport);
  }

  /**
   * Diagnostic the job that fails to allocate any devices.
   *
   * <p>This method can be invoked multiple times when the job is waiting for device allocation. And
   * the report will reflect the worse case during the waiting of the allocation.
   */
  @Override
  public SingleDeviceReport diagnoseJob(boolean noPerfectCandidate)
      throws MobileHarnessException, InterruptedException {
    List<DeviceInfo> candidates = queryDevices();
    SingleDeviceReport report;

    // If the report has been generated in the previous diagnose, use the previous report as
    // the base report and only change the individual device assessment if necessary;
    // Else, generate a new report;
    if (lastReport != null) {
      report = lastReport;
    } else {
      if (candidates.isEmpty()) {
        report = new SingleDeviceReport(job, null, noPerfectCandidate);
      } else {
        report = new SingleDeviceReport(job, assessor.assess(job, candidates), noPerfectCandidate);
      }
    }

    boolean isUsingSharedPool = SharedPoolJobUtil.isUsingSharedPool(job);
    boolean isProdMaster = isUsingProdMaster(job);

    // When the overall assessment has MAX_SCORE, means every job requirements can always be
    // supported by some devices. But no single device supports all job requirements. So need to
    // further assess each individual devices.
    if (report.getOverallScore() == SingleDeviceAssessment.MAX_SCORE) {
      boolean hasDeviceMatchRequirementButBusy = false;
      for (DeviceInfo candidate : candidates) {
        String deviceId = candidate.locator().toString();
        SingleDeviceAssessment deviceAssessment = assessor.assess(job, candidate);

        Optional<SingleDeviceAssessment> preDeviceAssessment = report.getDeviceAssessment(deviceId);
        // Use the device assessment of this time if
        // 1. The device has no assessment before.
        // 2. The previous device assessment has MAX_SCORE but the assessment this time has
        //    smaller score.
        if (preDeviceAssessment.isEmpty()
            || (preDeviceAssessment.get().getScore() == SingleDeviceAssessment.MAX_SCORE
                && deviceAssessment.getScore() < SingleDeviceAssessment.MAX_SCORE)) {
          report.setDeviceAssessment(deviceId, deviceAssessment);
        }
        if (deviceAssessment.isRequirementMatchedButBusy()) {
          hasDeviceMatchRequirementButBusy = true;
        }
      }
    }

    lastReport = report;
    historyReports.add(report);
    return report;
  }

  private List<DeviceInfo> queryDevices() throws MobileHarnessException, InterruptedException {
    DeviceQueryFilter candidateFilter = getDeviceQueryFilter();
    List<DeviceInfo> candidates =
        querier.queryDevice(candidateFilter).getDeviceInfoList().stream()
            .map(this::convertDeviceInfo)
            .collect(Collectors.toList());
    candidates =
        candidates.stream()
            .filter(
                deviceInfo -> {
                  boolean deviceHasSimCardInfoDimension =
                      deviceInfo.dimensions().supported().get(Name.SIM_CARD_INFO).stream()
                          .anyMatch(value -> !value.equals(Value.NO_SIM));
                  boolean jobHasSimCardInfoDimension =
                      job.dimensions().get(Name.SIM_CARD_INFO) != null;
                  if (!jobHasSimCardInfoDimension && deviceHasSimCardInfoDimension) {
                    return false;
                  }

                  boolean deviceHasNonDefaultPoolNameDimension =
                      deviceInfo.dimensions().supported().get(Name.POOL_NAME).stream()
                          .anyMatch(value -> !Value.DEFAULT_POOL_NAME.equals(value));
                  boolean jobHasNonDefaultPoolNameDimension =
                      job.dimensions().get(Name.POOL_NAME) != null
                          && !Value.DEFAULT_POOL_NAME.equals(job.dimensions().get(Name.POOL_NAME));
                  if (!jobHasNonDefaultPoolNameDimension && deviceHasNonDefaultPoolNameDimension) {
                    return false;
                  }
                  return true;
                })
            .collect(Collectors.toList());
    return candidates;
  }

  /**
   * Gets the device query filter. <br>
   * If the report has been generated in the previous diagnose and there are not too many perfect
   * candidates, only query the information of these perfect candidates; Else, query all the
   * devices.
   */
  private DeviceQueryFilter getDeviceQueryFilter() {
    if (lastReport != null) {
      ImmutableList<String> devices =
          lastReport.getPerfectMatchDevices().stream()
              .map(
                  deviceUniversalId -> {
                    int splitterIndex = deviceUniversalId.indexOf('@');
                    if (splitterIndex > 0) {
                      return deviceUniversalId.substring(0, splitterIndex);
                    } else {
                      return deviceUniversalId;
                    }
                  })
              .collect(toImmutableList());
      if (devices.size() < MAX_QUERY_DEVICE_COUNT) {
        String value = String.join("|", devices);
        DeviceQueryFilter.Builder filter = DeviceQueryFilter.newBuilder();
        filter.addDimensionFilter(DimensionFilter.newBuilder().setName("id").setValueRegex(value));
        return filter.build();
      }
    }

    return filter.getFilter(job);
  }

  @Override
  public void logExtraInfo() {
    List<String> perfectCandidates = new ArrayList<>();
    for (int i = 0; i < historyReports.size(); i++) {
      Collection<String> perfectCandidatesInCurrentReport =
          historyReports.get(i).getPerfectMatchDevices();
      perfectCandidates.addAll(perfectCandidatesInCurrentReport);
      logger.atInfo().log(
          "Diagnose %d's perfect candidates: %s",
          i, perfectCandidatesInCurrentReport.stream().collect(joining(", ")));
    }

    for (String perfectCandidate : perfectCandidates) {
      StringBuilder candidateLog = new StringBuilder();
      candidateLog.append("Score for ").append(perfectCandidate).append(": ");
      for (SingleDeviceReport historyReport : historyReports) {
        Optional<SingleDeviceAssessment> deviceAssessment =
            historyReport.getDeviceAssessment(perfectCandidate);
        if (deviceAssessment.isPresent()) {
          candidateLog.append(deviceAssessment.get().getScore()).append(" ");
        } else {
          candidateLog.append("N/A ");
        }
      }
      logger.atInfo().log("%s", candidateLog);
    }
  }

  /** Converts the DeviceInfo proto returned by device query API to Java version. */
  private DeviceInfo convertDeviceInfo(DeviceQuery.DeviceInfo deviceProto) {
    String labIp =
        deviceProto.getDimensionList().stream()
            .filter(
                dimension -> dimension.getName().equalsIgnoreCase(Dimension.Name.HOST_IP.name()))
            .map(DeviceQuery.Dimension::getValue)
            .findFirst()
            .orElse("unknown");
    String hostName =
        deviceProto.getDimensionList().stream()
            .filter(
                dimension -> dimension.getName().equalsIgnoreCase(Dimension.Name.HOST_NAME.name()))
            .map(DeviceQuery.Dimension::getValue)
            .findFirst()
            .orElse("unknown");
    LabLocator labLocator = new LabLocator(labIp, hostName);
    DeviceLocator deviceLocator = new DeviceLocator(deviceProto.getId(), labLocator);
    DeviceInfo deviceInfo =
        new DeviceInfo(deviceLocator, DeviceStatus.valueOf(deviceProto.getStatus().toUpperCase()));
    deviceInfo.owners().addAll(deviceProto.getOwnerList());
    deviceInfo.types().addAll(deviceProto.getTypeList());
    deviceInfo.drivers().addAll(deviceProto.getDriverList());
    deviceInfo.decorators().addAll(deviceProto.getDecoratorList());
    for (DeviceQuery.Dimension dimension : deviceProto.getDimensionList()) {
      if (dimension.getRequired()) {
        deviceInfo.dimensions().required().add(dimension.getName(), dimension.getValue());
      } else {
        deviceInfo.dimensions().supported().add(dimension.getName(), dimension.getValue());
      }
    }
    return deviceInfo;
  }

  private static boolean isUsingProdMaster(JobInfo job) {
    return job.properties().getOptional(Job.MASTER_SPEC).orElse("").contains("PROD");
  }
}
