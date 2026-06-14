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
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import javax.annotation.Nullable;

/** JobSettingsCreator creates the settings from a JobConfig */
public final class JobSettingsCreator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static JobSetting createJobSetting(
      String jobId, JobConfig jobConfig, @Nullable String tmpFileDir, SystemUtil systemUtil)
      throws InterruptedException {
    JobSetting.Builder setting = createCommonJobSettingBuilder(jobConfig, systemUtil);
    if (!jobConfig.getRemoteFileDir().isEmpty()) {
      setting.setRemoteFileDir(jobConfig.getRemoteFileDir());
    }

    // Finalizes the GEN_FILE dir.
    LocalFileUtil fileUtil = new LocalFileUtil();
    String genFileDir;
    if (!jobConfig.getGenFileDir().isEmpty()) {
      genFileDir = jobConfig.getGenFileDir();
    } else if (systemUtil.getTestUndeclaredOutputDir() != null) {
      genFileDir = systemUtil.getTestUndeclaredOutputDir();
    } else {
      genFileDir = getDefaultTmpDir() + "/" + jobId + "_gen";
    }
    try {
      fileUtil.removeFileOrDir(genFileDir);
    } catch (MobileHarnessException e) {
      systemUtil.exit(
          ExitCode.Shared.FILE_OPERATION_ERROR,
          "Failed to clean up GEN_FILE dir: " + genFileDir,
          e);
    }
    logger.atInfo().log("Gen file dir: %s", genFileDir);
    setting.setGenFileDir(genFileDir);

    // Finalizes the tmp file dir.
    if (tmpFileDir == null) {
      tmpFileDir = getDefaultTmpDir() + "/" + jobId + "_tmp";
    }
    logger.atInfo().log("Tmp file dir: %s", tmpFileDir);
    setting.setTmpFileDir(tmpFileDir);

    return setting.build();
  }

  /** Creates the settings builder from a JobConfig with custom sandboxed paths. */
  public static JobSetting.Builder createJobSetting(
      String jobId,
      JobConfig jobConfig,
      String sessionTmpDir,
      String sessionRemoteDir,
      SystemUtil systemUtil) {
    String jobDir = PathUtil.join(sessionTmpDir, "j_" + jobId);

    return createCommonJobSettingBuilder(jobConfig, systemUtil)
        .setRemoteFileDir(sessionRemoteDir)
        .setTmpFileDir(PathUtil.join(jobDir, "tmp"))
        .setRunFileDir(PathUtil.join(jobDir, "run"))
        .setGenFileDir(PathUtil.join(jobDir, "gen"));
  }

  private static JobSetting.Builder createCommonJobSettingBuilder(
      JobConfig jobConfig, SystemUtil systemUtil) {
    Timeout.Builder timeout = JobConfigHelper.finalizeTimeout(jobConfig, systemUtil);

    // Finalizes job retry setting.
    Retry.Builder retry =
        Retry.newBuilder()
            .setTestAttempts(
                jobConfig.hasTestAttempts() && jobConfig.getTestAttempts() > 0
                    ? jobConfig.getTestAttempts()
                    : JobSetting.getDefaultRetryInstance().getTestAttempts())
            .setRetryLevel(
                jobConfig.hasRetryLevel()
                    ? jobConfig.getRetryLevel()
                    : JobSetting.getDefaultRetryInstance().getRetryLevel());

    // Finalizes job repeat setting.
    Repeat.Builder repeat = Repeat.newBuilder();
    repeat.setRepeatRuns(jobConfig.getRepeatRuns());

    return JobSetting.newBuilder()
        .setTimeout(timeout.build())
        .setRetry(retry.build())
        .setPriority(jobConfig.getPriority())
        .setAllocationExitStrategy(jobConfig.getAllocationExitStrategy())
        .setRepeat(repeat.build());
  }

  private static String getDefaultTmpDir() {
    // https://bazel.build/reference/test-encyclopedia#test-interaction-filesystem
    return System.getenv("TEST_TMPDIR");
  }

  private JobSettingsCreator() {}
}
