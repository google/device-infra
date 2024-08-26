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

import com.google.devtools.mobileharness.api.model.job.in.Dirs;
import com.google.devtools.mobileharness.service.moss.proto.Slg.JobSettingProto;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInternalFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Retry;

/**
 * Utility class to help convert a {@link JobSetting} to a {@link JobSettingProto} in forward and
 * backward.
 */
final class JobSettingConverter {
  private JobSettingConverter() {}

  /**
   * Gets a {@link JobSetting} by the given {@link JobSettingProto} and the prefix to help create
   * local directories.
   *
   * @param jobDir the job root directory
   */
  static JobSetting fromProto(String jobDir, JobSettingProto jobSettingProto) {
    Dirs dirs = DirsConverter.fromProto(jobDir, jobSettingProto.getDirs());
    return JobInternalFactory.createJobSetting(dirs, jobSettingProto);
  }

  /**
   * Gets a {@link JobSettingProto} by the given {@link JobSetting}. Not all fields of a {@link
   * Dirs} are persistent, see {@link DirsConverter#toProto(Dirs)} for more detail.
   */
  static JobSettingProto toProto(JobSetting jobSetting) {
    return JobSettingProto.newBuilder()
        .setTimeout(jobSetting.getNewTimeout().toProto())
        .setRetry(
            Retry.newBuilder()
                .setTestAttempts(jobSetting.getRetry().getTestAttempts())
                .setRetryLevel(Retry.Level.valueOf(jobSetting.getRetry().getRetryLevel().name())))
        .setPriority(jobSetting.getNewPriority())
        .setAllocationExitStrategy(jobSetting.getAllocationExitStrategy())
        .setDirs(DirsConverter.toProto(jobSetting.dirs()))
        .setRepeat(jobSetting.getRepeat())
        .build();
  }
}
