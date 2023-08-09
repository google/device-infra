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

package com.google.devtools.mobileharness.infra.lab.rpc.service.util;

import com.google.common.collect.Lists;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.in.Dirs;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceRequirement;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.message.StrPairUtil;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.KickOffTestRequest;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.time.Instant;
import java.util.List;

/**
 * Utility for creating {@link TestInfo} from {@link KickOffTestRequest} in MH test engine or lab
 * server.
 */
public class TestInfoCreator {

  private final JobDirFactory jobDirsFactory;
  private final LocalFileUtil localFileUtil;

  public TestInfoCreator(JobDirFactory jobDirsFactory, LocalFileUtil localFileUtil) {
    this.jobDirsFactory = jobDirsFactory;
    this.localFileUtil = localFileUtil;
  }

  /**
   * @param req whose client_version should be greater than 4.29.0
   */
  public TestInfo create(KickOffTestRequest req)
      throws MobileHarnessException, InterruptedException {
    // Creates JobSetting.
    Dirs jobDirs = jobDirsFactory.getJobDirs(req.getJob().getJobId(), req.getTest().getTestId());
    JobSetting jobSetting =
        JobSetting.newBuilder()
            .setTimeout(req.getJob().getTimeout())
            .setGenFileDir(jobDirs.genFileDir())
            .setRunFileDir(jobDirs.runFileDir())
            .setTmpFileDir(jobDirs.tmpFileDir())
            .setRemoteFileDir(jobDirs.remoteFileDir().orElse(null))
            .setHasTestSubdirs(jobDirs.hasTestSubdirs())
            .setLocalFileUtil(localFileUtil)
            .build();

    // Creates Timing of the job.
    Timing jobTiming = new Timing(Instant.ofEpochMilli(req.getJob().getJobCreateTimeMs()));
    jobTiming.start(Instant.ofEpochMilli(req.getJob().getJobStartTimeMs()));

    List<DeviceRequirement> deviceRequirements =
        req.getJob().getJobFeature().getDeviceRequirements().getDeviceRequirementList();

    // Creates JobType.
    DeviceRequirement primaryDeviceRequirement = deviceRequirements.get(0);

    JobType jobType =
        JobType.newBuilder()
            .setDriver(req.getJob().getJobFeature().getDriver())
            .setDevice(primaryDeviceRequirement.getDeviceType())
            .addAllDecorator(Lists.reverse(primaryDeviceRequirement.getDecoratorList()))
            .build();

    // Creates JobInfo.
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator(req.getJob().getJobId(), req.getJob().getJobName()))
            .setJobUser(req.getJob().getJobFeature().getUser())
            .setType(jobType)
            .setSetting(jobSetting)
            .setTiming(jobTiming)
            .build();

    // Sets JobSpec, ScopedSpecs, Params and Properties of the job.
    jobInfo.protoSpec().setProto(req.getJob().getJobSpec()).tryResolveUnknownExtension();
    try {
      jobInfo.scopedSpecs().addJson(req.getJob().getJobScopedSpecsJson());
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SET_JOB_SCOPED_SPECS_ERROR_IN_LAB,
          "Failed to set job scoped specs: " + req.getJob().getJobScopedSpecsJson(),
          e);
    }
    jobInfo.params().addAll(StrPairUtil.convertCollectionToMap(req.getJob().getJobParamList()));
    jobInfo
        .properties()
        .addAll(StrPairUtil.convertCollectionToMap(req.getJob().getJobPropertyList()));

    // Sets SubDeviceSpecs of the job.
    jobInfo
        .subDeviceSpecs()
        .getSubDevice(0)
        .dimensions()
        .addAll(primaryDeviceRequirement.getDimensionsMap());

    deviceRequirements.stream()
        .skip(1L)
        .forEach(
            deviceRequirement ->
                jobInfo
                    .subDeviceSpecs()
                    .addSubDevice(
                        deviceRequirement.getDeviceType(),
                        deviceRequirement.getDimensionsMap(),
                        deviceRequirement.getDecoratorList()));

    List<String> jsonSpecs = req.getJob().getDeviceScopedSpecsJsonList();
    for (int i = 0; i < jsonSpecs.size(); i++) {
      jobInfo.subDeviceSpecs().getSubDevice(i).scopedSpecs().addJson(jsonSpecs.get(i));
    }

    // Creates Timing of the test.
    Timing testTiming = new Timing(Instant.ofEpochMilli(req.getTest().getTestCreateTimeMs()));
    testTiming.start(Instant.ofEpochMilli(req.getTest().getTestStartTimeMs()));

    // Creates TestInfo
    TestInfo testInfo =
        jobInfo.tests().add(req.getTest().getTestId(), req.getTest().getTestName(), testTiming);

    // Sets Properties of the test.
    testInfo
        .properties()
        .addAll(StrPairUtil.convertCollectionToMap(req.getTest().getTestPropertyList()));
    return testInfo;
  }
}
