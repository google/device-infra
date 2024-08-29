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

package com.google.wireless.qa.mobileharness.shared.model.job;

import com.google.devtools.mobileharness.api.model.job.in.Dirs;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.devtools.mobileharness.service.moss.proto.Slg.JobSettingProto;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.in.ScopedSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Errors;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.RemoteFiles;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Result;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Status;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;

/**
 * The factory to help create instances under this package. It's only used internally to support
 * making job/test data models persistent.
 */
public final class JobInternalFactory {

  private JobInternalFactory() {}

  /**
   * Creates a {@link JobSetting} instance by the given {@link Dirs} and {@link JobSettingProto}.
   */
  public static JobSetting createJobSetting(Dirs dirs, JobSettingProto jobSettingProto) {
    return new JobSetting(dirs, jobSettingProto);
  }

  /**
   * Creates a {@link JobInfo} instance by the given all required final fields of the class. Note:
   * the tests of the job aren't offered, they are added later after the class is constructed.
   */
  public static JobInfo createJobInfo(
      JobLocator locator,
      JobUser jobUser,
      JobType type,
      JobSetting setting,
      Timing timing,
      Params params,
      Files files,
      ScopedSpecs scopedSpecs,
      SubDeviceSpecs subDeviceSpecs,
      RemoteFiles remoteGenFiles,
      RemoteFiles remoteRunFiles,
      Status status,
      Result result,
      Log log,
      Properties properties,
      Errors errors,
      JobSpec jobSpec) {
    return new JobInfo(
        locator,
        jobUser,
        type,
        setting,
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
        errors,
        jobSpec);
  }

  /**
   * Creates a {@link TestInfo} instance by the given all required final fields of the class. Note:
   * the sub-tests aren't offered, they are added later after the class is constructed.
   */
  public static TestInfo createTestInfo(
      TestLocator testLocator,
      Timing timing,
      JobInfo jobInfo,
      TestInfo parentTest,
      Files files,
      RemoteFiles remoteGenFiles,
      Status status,
      Result result,
      Log log,
      Properties properties,
      Errors errors) {
    return new TestInfo(
        testLocator,
        timing,
        jobInfo,
        parentTest,
        files,
        remoteGenFiles,
        status,
        result,
        log,
        properties,
        errors);
  }
}
