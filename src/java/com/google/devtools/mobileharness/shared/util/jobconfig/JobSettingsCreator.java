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

package com.google.devtools.mobileharness.shared.util.jobconfig;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Job.Repeat;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;

/** JobSettingsCreator creates the settings from a JobConfig */
public final class JobSettingsCreator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static JobSetting createJobSetting(String jobId, JobConfig jobConfig)
      throws InterruptedException {
    Timeout.Builder timeout = JobConfigHelper.finalizeTimeout(jobConfig);

    // Finalizes job retry setting.
    Retry.Builder retry = Retry.newBuilder();
    retry.setTestAttempts(jobConfig.getTestAttempts());
    retry.setRetryLevel(jobConfig.getRetryLevel());

    // Finalizes job repeat setting.
    Repeat.Builder repeat = Repeat.newBuilder();
    repeat.setRepeatRuns(jobConfig.getRepeatRuns());

    JobSetting.Builder setting =
        JobSetting.newBuilder()
            .setTimeout(timeout.build())
            .setRetry(retry.build())
            .setPriority(jobConfig.getPriority())
            .setAllocationExitStrategy(jobConfig.getAllocationExitStrategy())
            .setRepeat(repeat.build());
    if (!jobConfig.getRemoteFileDir().isEmpty()) {
      setting.setRemoteFileDir(jobConfig.getRemoteFileDir());
    }

    // Finalizes the GEN_FILE dir.
    LocalFileUtil fileUtil = new LocalFileUtil();
    String genFileDir;
    if (!jobConfig.getGenFileDir().isEmpty()) {
      genFileDir = jobConfig.getGenFileDir();
    } else if (new SystemUtil().getTestUndeclaredOutputDir() != null) {
      genFileDir = new SystemUtil().getTestUndeclaredOutputDir();
    } else {
      genFileDir = getDefaultTmpDir() + "/" + jobId + "_gen";
    }
    try {
      fileUtil.removeFileOrDir(genFileDir);
    } catch (MobileHarnessException e) {
      new SystemUtil()
          .exit(
              ExitCode.Shared.FILE_OPERATION_ERROR,
              "Failed to clean up GEN_FILE dir: " + genFileDir,
              e);
    }
    logger.atInfo().log("Gen file dir: %s", genFileDir);
    setting.setGenFileDir(genFileDir);

    // Finalizes the tmp file dir.
    String tmpFileDir = getDefaultTmpDir() + "/" + jobId + "_tmp";
    logger.atInfo().log("Tmp file dir: %s", tmpFileDir);
    setting.setTmpFileDir(tmpFileDir);
    return setting.build();
  }

  private static String getDefaultTmpDir() {
    // https://bazel.build/reference/test-encyclopedia#test-interaction-filesystem
    return System.getenv("TEST_TMPDIR");
  }

  private JobSettingsCreator() {}
}
