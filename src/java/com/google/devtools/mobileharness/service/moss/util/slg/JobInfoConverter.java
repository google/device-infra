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

package com.google.devtools.mobileharness.service.moss.util.slg;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.mobileharness.api.model.job.out.Finding;
import com.google.devtools.mobileharness.api.model.job.out.Findings;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Warnings;
import com.google.devtools.mobileharness.api.model.proto.Diagnostic;
import com.google.devtools.mobileharness.api.model.proto.Diagnostic.Finding.Severity;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.devtools.mobileharness.service.moss.proto.Slg.FilesProto;
import com.google.devtools.mobileharness.service.moss.proto.Slg.JobInfoProto;
import com.google.devtools.mobileharness.service.moss.proto.Slg.JobScheduleUnitProto;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobHelper;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInternalFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.JobInInternalFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.in.ScopedSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.RemoteFiles;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Status;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Utility class to convert a {@link JobInfo} to a {@link JobInfoProto} in forward and backward. */
public final class JobInfoConverter {

  private JobInfoConverter() {}

  /**
   * Gets a {@link JobInfo} by the given {@link JobInfoProto} and the prefix to create local
   * directories.
   *
   * @param jobDir the job root directory
   */
  public static JobInfo fromProto(
      boolean resumeFiles, @Nullable String rootDir, JobInfoProto jobInfoProto) {
    JobScheduleUnitProto jobScheduleUnitProto = jobInfoProto.getJobScheduleUnit();
    JobLocator jobLocator =
        new JobLocator(
            com.google.devtools.mobileharness.api.model.job.JobLocator.of(
                jobScheduleUnitProto.getJobLocator()));
    JobSetting jobSetting =
        JobSettingConverter.fromProto(
            resumeFiles,
            rootDir == null ? null : PathUtil.join(rootDir, "j_" + jobLocator.getId()),
            jobScheduleUnitProto.getJobSetting());
    Timing timing = TimingConverter.fromProto(jobScheduleUnitProto.getTiming());
    Params params =
        ParamsConverter.fromProto(timing.toNewTiming(), jobScheduleUnitProto.getParams());
    Files files =
        FilesConverter.fromProto(
            timing.toNewTiming(),
            resumeFiles ? jobInfoProto.getFiles() : FilesProto.getDefaultInstance());
    ;
    ScopedSpecs scopedSpecs =
        JobInInternalFactory.createScopedSpecs(
            params, timing, jobScheduleUnitProto.getScopedSpecsJsonString());
    SubDeviceSpecs subDeviceSpecs =
        SubDeviceSpecsConverter.fromProto(timing, params, jobScheduleUnitProto.getSubDeviceSpecs());
    RemoteFiles remoteGenFiles =
        RemoteFilesConverter.fromProto(timing, jobInfoProto.getRemoteGenFiles());
    RemoteFiles remoteRunFiles =
        RemoteFilesConverter.fromProto(timing, jobInfoProto.getRemoteRunFiles());
    Status status = StatusConverter.fromProto(timing, jobInfoProto.getStatus());
    Result result = ResultConverter.fromProto(timing, params, jobInfoProto.getResult());
    Log log = new Log(timing);
    Properties properties = PropertiesConverter.fromProto(timing, jobInfoProto.getProperties());
    Findings findings = new Findings(log);
    if (jobInfoProto.getFindingCount() > 0) {
      jobInfoProto.getFindingList().forEach(findings::add);
    } else {
      // Persisted by an older version, which only stored the WARNING findings as errors.
      findings.addAll(Severity.WARNING, jobInfoProto.getErrorList());
    }
    Warnings warnings = new Warnings(timing.toNewTiming(), findings);
    JobUser jobUser;
    if (jobScheduleUnitProto.hasJobUser()) {
      jobUser = jobScheduleUnitProto.getJobUser();
    } else {
      jobUser = JobUser.newBuilder().setRunAs(jobScheduleUnitProto.getUser()).build();
    }
    JobInfo jobInfo =
        JobInternalFactory.createJobInfo(
            jobLocator,
            jobUser,
            jobScheduleUnitProto.getJobType(),
            jobSetting,
            timing,
            params,
            files,
            scopedSpecs,
            subDeviceSpecs,
            remoteGenFiles,
            remoteRunFiles,
            status,
            result,
            log,
            properties,
            warnings,
            findings,
            jobInfoProto.getJobSpec());
    if (jobInfoProto.getTestInfoCount() > 0) {
      JobHelper.addTests(
          jobInfo.tests(),
          jobInfoProto.getTestInfoList().stream()
              .map(
                  testInfoProto ->
                      TestInfoConverter.fromProto(
                          resumeFiles, jobInfo, /* parentTestInfo= */ null, testInfoProto))
              .collect(Collectors.toList()));
    }
    return jobInfo;
  }

  /** Gets a {@link JobInfoProto} by the given {@link JobInfo}. */
  public static JobInfoProto toProto(JobInfo jobInfo) {
    JobScheduleUnitProto jobScheduleUnit =
        JobScheduleUnitProto.newBuilder()
            .setJobLocator(jobInfo.locator().toNewJobLocator().toProto())
            .setUser(jobInfo.jobUser().getRunAs())
            .setJobUser(jobInfo.jobUser())
            .setJobType(jobInfo.type())
            .setJobSetting(JobSettingConverter.toProto(jobInfo.setting()))
            .setTiming(TimingConverter.toProto(jobInfo.timing()))
            .setParams(ParamsConverter.toProto(jobInfo.params()))
            .setScopedSpecsJsonString(jobInfo.scopedSpecs().toJsonString())
            .setSubDeviceSpecs(SubDeviceSpecsConverter.toProto(jobInfo.subDeviceSpecs()))
            .build();
    // Takes a single snapshot of the findings and derives the errors from it, so that they are
    // consistent with each other even if the findings are updated concurrently.
    ImmutableList<Diagnostic.Finding> findings =
        jobInfo.findings().getAll().stream().map(Finding::toProto).collect(toImmutableList());
    return JobInfoProto.newBuilder()
        .setJobScheduleUnit(jobScheduleUnit)
        .setFiles(FilesConverter.toProto(jobInfo.inputFiles()))
        .setRemoteGenFiles(RemoteFilesConverter.toProto(jobInfo.remoteGenFiles()))
        .setRemoteRunFiles(RemoteFilesConverter.toProto(jobInfo.remoteRunFiles()))
        .setStatus(StatusConverter.toProto(jobInfo.status()))
        .setResult(ResultConverter.toProto(jobInfo.resultWithCause()))
        .setProperties(PropertiesConverter.toProto(jobInfo.properties()))
        // Only contains WARNING findings. Kept for the readers which don't support findings.
        .addAllError(
            findings.stream()
                .filter(finding -> finding.getSeverity() == Severity.WARNING)
                .map(finding -> ErrorModelConverter.toExceptionDetail(finding.getDetail()))
                .collect(toImmutableList()))
        .addAllFinding(findings)
        .setJobSpec(jobInfo.protoSpec().getProto())
        .addAllTestInfo(
            jobInfo.tests().getAll().values().stream()
                .map(TestInfoConverter::toProto)
                .collect(Collectors.toList()))
        .build();
  }
}
