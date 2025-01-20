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

package com.google.wireless.qa.mobileharness.shared.api.validator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.reflection.ClientClassUtil;
import com.google.devtools.mobileharness.shared.util.reflection.ValidatorClassUtil;
import com.google.wireless.qa.mobileharness.shared.api.validator.job.JobValidator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Checker for validating the job information. */
public class JobChecker {

  /** Factory for creating {@link Validator} instances. */
  private final ValidatorFactory validatorFactory;

  public JobChecker() {
    this(new ValidatorFactory());
  }

  @VisibleForTesting
  JobChecker(ValidatorFactory validatorFactory) {
    this.validatorFactory = validatorFactory;
  }

  /** Validates the job information. */
  public void validateJob(JobInfo jobInfo) throws MobileHarnessException, InterruptedException {
    JobType jobType = jobInfo.type();
    List<Class<? extends Validator>> validatorClasses = new ArrayList<>();
    List<Class<? extends JobValidator>> jobValidatorClasses = new ArrayList<>();

    // Checks the driver type.
    String driverName = jobType.getDriver();
    Optional<Class<? extends JobValidator>> jobValidatorClass =
        ClientClassUtil.getJobValidatorClass(driverName);
    if (jobValidatorClass.isPresent()) {
      jobValidatorClasses.add(jobValidatorClass.get());
    } else {
      Optional<Class<? extends Validator>> validatorClass =
          ValidatorClassUtil.getValidatorClass(driverName);
      validatorClass.ifPresent(validatorClasses::add);
    }

    // Checks the decorator type.
    for (String decoratorName : jobType.getDecoratorList()) {
      jobValidatorClass = ClientClassUtil.getJobValidatorClass(decoratorName);
      if (jobValidatorClass.isPresent()) {
        jobValidatorClasses.add(jobValidatorClass.get());
      } else {
        Optional<Class<? extends Validator>> validatorClass =
            ValidatorClassUtil.getValidatorClass(decoratorName);
        validatorClass.ifPresent(validatorClasses::add);
      }
    }

    // Runs the job validators.
    List<String> errors = new ArrayList<>();
    for (JobValidator jobValidator : validatorFactory.createJobValidators(jobValidatorClasses)) {
      errors.addAll(jobValidator.validate(jobInfo));
    }

    // Runs the legacy validators.
    for (Validator validator : validatorFactory.createValidators(validatorClasses)) {
      errors.addAll(validator.validateJob(jobInfo));
    }

    if (!errors.isEmpty()) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_CONFIG_GENERIC_ERROR,
          "Job configuration error:\n - " + Joiner.on("\n - ").join(errors));
    }
  }
}
