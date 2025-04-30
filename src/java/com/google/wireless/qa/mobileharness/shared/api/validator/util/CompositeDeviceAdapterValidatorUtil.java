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

package com.google.wireless.qa.mobileharness.shared.api.validator.util;

import com.google.common.base.Joiner;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.reflection.ClientClassUtil;
import com.google.wireless.qa.mobileharness.shared.api.job.JobTypeUtil;
import com.google.wireless.qa.mobileharness.shared.api.validator.ValidatorFactory;
import com.google.wireless.qa.mobileharness.shared.api.validator.job.JobValidator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import com.google.wireless.qa.mobileharness.shared.proto.spec.Google3File;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.SubDeviceJobSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.SubDeviceJobSpec.FileSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.SubDeviceJobSpec.ParamSpec;
import java.util.List;
import java.util.Optional;

/** Utils for validators of decorators that extend from {@code CompositeDeviceAdapterBase}. */
public final class CompositeDeviceAdapterValidatorUtil {
  private CompositeDeviceAdapterValidatorUtil() {}

  /** If any of the given decorator classes have validators, instantiates and runs them. */
  public static void runSubValidators(List<String> errors, List<String> decorators, JobInfo job)
      throws InterruptedException {
    ValidatorFactory validatorFactory = new ValidatorFactory();
    for (String decorator : decorators) {
      Optional<Class<? extends JobValidator>> jobValidatorClass =
          ClientClassUtil.getJobValidatorClass(decorator);
      if (jobValidatorClass.isPresent()) {
        JobValidator jobValidator;
        try {
          jobValidator = validatorFactory.createJobValidator(jobValidatorClass.get());
        } catch (MobileHarnessException e) {
          errors.add(
              String.format(
                  "Failed to instantiate job validator class \"%s\": %s",
                  jobValidatorClass.get().getSimpleName(), e));
          continue;
        }
        errors.addAll(jobValidator.validate(job));
      }
    }
  }

  /**
   * Creates a {@link JobInfo} from a SubDeviceJobSpec.
   *
   * @param rootJobInfo The {@link JobInfo} from the root job
   * @param deviceSpec The spec containing the params, files and decorators for this subdevice
   * @return a {@link JobInfo} with the same settings as the root ID, but with a custom type string,
   *     param, and file list.
   * @throws MobileHarnessException if resolving files or parsing the job spec fails.
   */
  public static JobInfo makeSubDeviceJobInfo(JobInfo rootJobInfo, SubDeviceJobSpec deviceSpec)
      throws MobileHarnessException {
    String jobTypeStr =
        "_nodevice_+_nodriver_+" + Joiner.on('+').join(deviceSpec.getDecoratorList());
    JobType jobType = JobTypeUtil.parseString(jobTypeStr);
    JobInfo subJobInfo =
        JobInfo.newBuilder()
            .setLocator(rootJobInfo.locator())
            .setJobUser(rootJobInfo.jobUser())
            .setType(jobType)
            .setSetting(rootJobInfo.setting())
            .build();
    for (ParamSpec param : deviceSpec.getParamsList()) {
      subJobInfo.params().add(param.getName(), param.getValue());
    }
    // Add files from job's root info to sub job info.
    subJobInfo.files().addAll(rootJobInfo.files().getAll());
    // Add files from scoped job spec to sub job info.
    for (FileSpec fileSpec : deviceSpec.getFilesList()) {
      for (String filePath : fileSpec.getFilesList()) {
        subJobInfo.files().add(fileSpec.getTag(), filePath);
      }
      for (Google3File file : fileSpec.getG3FilesList()) {
        for (String output : file.getOutputList()) {
          subJobInfo.files().add(fileSpec.getTag(), output);
        }
      }
    }
    // This subJobInfo is only used to pass Job parameters to sub tests. Job status does not mean
    // anything to test runner. Set the status to "RUNNING" to avoid {@link MobileHarnessException}
    // with {@link ErrorCode.JOB_NOT_STARTED} at {@link JobInfo#getExpireTime}, error message:
    // "Failed to calculate the job expire time because job is not started. Please set the
    // job status from NEW to any other status."
    // This will be set to DONE once all tests are completed.
    subJobInfo.status().set(TestStatus.RUNNING);
    return subJobInfo;
  }
}
