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

package com.google.devtools.mobileharness.infra.lab.rpc.stub.helper;

import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.CheckJobsRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.CloseTestRequest;
import com.google.devtools.mobileharness.infra.master.rpc.stub.JobSyncStub;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** RPC stub helper for talking to MobileHarness Master V5 JobSyncService. */
public class JobSyncHelper {
  /** Logger for this stub. */
  public static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Stub for syncing job info with Master. */
  private final JobSyncStub jobSyncStub;

  /**
   * Creates a Stubby stub for talking to MobileHarness Master.
   *
   * @param jobSyncStub the master stub for syncing job
   */
  public JobSyncHelper(JobSyncStub jobSyncStub) {
    this.jobSyncStub = jobSyncStub;
  }

  /**
   * Closes the test in Master.
   *
   * @param deviceDirty whether to leave the device as DIRTY in master
   */
  public void closeTest(
      TestLocator testLocator,
      TestResult testResult,
      DeviceLocator deviceLocator,
      boolean deviceDirty)
      throws InterruptedException {
    String target =
        String.format(
            "test %s on device %s, DeviceDirty=%s", testLocator.id(), deviceLocator, deviceDirty);
    logger.atInfo().log("Close %s", target);
    // Releases device in an async rpc call because it can be invoked when the test runner thread is
    // interrupted. See b/17142244.
    CloseTestRequest req =
        CloseTestRequest.newBuilder()
            .setJobId(testLocator.jobLocator().id())
            .setTestId(testLocator.id())
            .setTestResult(testResult)
            .addDeviceId(deviceLocator.id())
            .setDeviceDirty(deviceDirty)
            .setLabLocator(deviceLocator.labLocator().toProto())
            .setLabIp(deviceLocator.labLocator().ip())
            .setTimestampMsFromLab(Instant.now().toEpochMilli())
            .build();
    try {
      jobSyncStub.closeTest(req).get();
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Failed to close %s", target);
    }
  }

  /**
   * Checks whether jobs are alive in Master.
   *
   * @param allJobIds ids of the jobs to be checked
   * @return ids of the alive jobs in Master
   * @throws MobileHarnessException if fails to talk to Master or error occurs
   */
  public List<String> getAliveJobs(Collection<String> allJobIds) throws MobileHarnessException {
    logger.atInfo().log("Check jobs: %s", allJobIds);
    if (allJobIds == null || allJobIds.isEmpty()) {
      return Lists.newArrayList();
    }
    CheckJobsRequest req = CheckJobsRequest.newBuilder().addAllJobId(allJobIds).build();

    try {
      return jobSyncStub.checkJobs(req).getJobIdList();
    } catch (RpcExceptionWithErrorId e) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_JOB_SYNC_CHECK_JOB_ERROR,
          String.format("Failed to verify the job status in Master for jobs %s", allJobIds),
          e);
    }
  }
}
