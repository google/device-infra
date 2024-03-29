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

import static com.google.devtools.mobileharness.shared.util.error.MoreThrowables.shortDebugString;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.ClassUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ValidatorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.Decorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.validator.job.JobValidator;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Checker for validating the job information. */
public class JobChecker {
  /** The logger. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
    Set<Class<?>> annotationValidatorClasses = new HashSet<>();

    // Checks the driver type.
    String driverName = jobType.getDriver();
    Optional<Class<? extends JobValidator>> jobValidatorClass =
        ClassUtil.getJobValidatorClass(driverName);
    if (jobValidatorClass.isPresent()) {
      jobValidatorClasses.add(jobValidatorClass.get());
    } else {
      Optional<Class<? extends Validator>> validatorClass = ClassUtil.getValidatorClass(driverName);
      validatorClass.ifPresent(validatorClasses::add);
      try {
        Class<? extends Driver> driverClass = ClassUtil.getDriverClass(driverName);
        ClassUtil.getClassesWithAllSteps(driverClass, annotationValidatorClasses);
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to load validator methods of driver [%s], skip them, reason=[%s]",
            driverName, shortDebugString(e));
      }
    }

    // Checks the decorator type.
    for (String decoratorName : jobType.getDecoratorList()) {
      jobValidatorClass = ClassUtil.getJobValidatorClass(decoratorName);
      if (jobValidatorClass.isPresent()) {
        jobValidatorClasses.add(jobValidatorClass.get());
      } else {
        Optional<Class<? extends Validator>> validatorClass =
            ClassUtil.getValidatorClass(decoratorName);
        validatorClass.ifPresent(validatorClasses::add);
        try {
          Class<? extends Decorator> decoratorClass = ClassUtil.getDecoratorClass(decoratorName);
          ClassUtil.getClassesWithAllSteps(decoratorClass, annotationValidatorClasses);
        } catch (MobileHarnessException e) {
          logger.atWarning().log(
              "Failed to load validator methods of decorator [%s], skip them, reason=[%s]",
              decoratorName, shortDebugString(e));
        }
      }
    }

    // Runs the job validators.
    List<String> errors = Lists.newLinkedList();
    for (JobValidator jobValidator : validatorFactory.createJobValidators(jobValidatorClasses)) {
      errors.addAll(jobValidator.validate(jobInfo));
    }

    // Runs the legacy validators.
    for (Validator validator : validatorFactory.createValidators(validatorClasses)) {
      errors.addAll(validator.validateJob(jobInfo));
    }

    // Runs the validator methods.
    for (Class<?> clazz : annotationValidatorClasses) {
      errors.addAll(validateJobByValidatorMethods(jobInfo, clazz));
    }

    if (!errors.isEmpty()) {
      throw new MobileHarnessException(
          ErrorCode.JOB_CONFIG_ERROR,
          "Job configuration error:\n - " + Joiner.on("\n - ").join(errors));
    }
  }

  /**
   * Validates the given job info by all job validator methods declared in the given class.
   *
   * <p>The method could be used for testing job validator methods.
   *
   * @param jobInfo the job info
   * @param clazz the class in which the job validator methods declared will be invoked
   * @return the validation result of all job validator methods declared in the given class
   * @throws MobileHarnessException if the given class has an invalid job validator method, failed
   *     to run a job validator method or an invoked job validator method throws an exception
   * @throws InterruptedException if an invoked job validator method throws it
   * @see ValidatorAnnotation
   */
  @VisibleForTesting
  @SuppressWarnings("unchecked")
  public static List<String> validateJobByValidatorMethods(JobInfo jobInfo, Class<?> clazz)
      throws MobileHarnessException, InterruptedException {
    List<String> result = new ArrayList<>();
    for (Method method : clazz.getDeclaredMethods()) {
      if (ClassUtil.isValidatorMethod(method, ValidatorAnnotation.Type.JOB)) {
        logger.atInfo().log("Job validator method: %s", method);
        method.setAccessible(true);
        try {
          result.addAll((List<String>) method.invoke(/* obj= */ null, jobInfo));
        } catch (IllegalAccessException | IllegalArgumentException e) {
          throw new MobileHarnessException(
              ErrorCode.JOB_VALIDATE_ERROR,
              String.format("Failed to run job validator %s: %s", method, e),
              e);
        } catch (InvocationTargetException e) {
          Throwable targetException = e.getTargetException();
          if (targetException instanceof InterruptedException) {
            throw (InterruptedException) targetException;
          } else {
            throw new MobileHarnessException(
                ErrorCode.JOB_VALIDATE_ERROR,
                String.format("Exception thrown by job validator %s: %s", method, targetException),
                targetException);
          }
        }
      }
    }
    return result;
  }
}
