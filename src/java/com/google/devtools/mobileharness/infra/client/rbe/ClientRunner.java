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

package com.google.devtools.mobileharness.infra.client.rbe;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.ClientApi;
import com.google.devtools.mobileharness.infra.client.api.mode.local.LocalMode;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/** Main execution logic of OmniLab client to run MH test on RBE. */
class ClientRunner {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ClientApi clientApi;
  private final LocalMode localMode;

  private final SystemUtil systemUtil;

  @Inject
  ClientRunner(ClientApi clientApi, LocalMode localMode, SystemUtil systemUtil) {
    this.clientApi = clientApi;
    this.localMode = localMode;
    this.systemUtil = systemUtil;
  }

  /** Runs all the jobs and wait for the jobs to complete. */
  ExitCode run(List<JobInfo> jobInfos) throws InterruptedException {
    List<JobInfo> startedJobInfos = startJobs(jobInfos);
    for (JobInfo jobInfo : jobInfos) {
      clientApi.waitForJob(jobInfo.locator().getId());
    }
    return checkJobResults(startedJobInfos);
  }

  private List<JobInfo> startJobs(List<JobInfo> jobInfos) throws InterruptedException {
    List<JobInfo> startedJobInfos = new ArrayList<>();
    try {
      for (JobInfo jobInfo : jobInfos) {
        clientApi.startJob(jobInfo, localMode);
        startedJobInfos.add(jobInfo);
      }
    } catch (MobileHarnessException e) {
      for (JobInfo jobInfo : startedJobInfos) {
        clientApi.killJob(jobInfo.locator().getId());
      }
      systemUtil.exit(ExitCode.Client.START_JOB_ERROR, e);
    }
    return startedJobInfos;
  }

  private ExitCode checkJobResults(List<JobInfo> jobInfos) {
    int totalTests = 0;
    for (JobInfo jobInfo : jobInfos) {
      TestResult jobResult = jobInfo.result().toNewResult().get().type();
      if (jobResult != TestResult.PASS && jobResult != TestResult.SKIP) {
        return ExitCode.Client.TEST_FAILURE;
      }
      totalTests += jobInfo.tests().getAll().keySet().size();
    }
    if (jobInfos.size() > 1) {
      logger.atInfo().log("OK (%d test in %d jobs)", totalTests, jobInfos.size());
    } else {
      logger.atInfo().log("OK (%d test)", totalTests);
    }
    return ExitCode.Shared.OK;
  }
}
