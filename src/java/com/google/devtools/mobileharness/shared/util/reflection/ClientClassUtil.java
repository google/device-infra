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

package com.google.devtools.mobileharness.shared.util.reflection;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.lister.Lister;
import com.google.wireless.qa.mobileharness.shared.api.validator.job.JobValidator;
import com.google.wireless.qa.mobileharness.shared.util.ReflectionUtil;
import java.util.Optional;
import javax.annotation.Nullable;

/** Util for loading client side classes like {@link Lister} and {@link JobValidator}. */
public class ClientClassUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Gets the job validator class of the given driver or decorator. The validator class should be a
   * subclass of {@link JobValidator} and in package {@link
   * com.google.wireless.qa.mobileharness.shared.api.validator.job}.
   *
   * @return the validator class for the given driver, or null if can not find the test validator
   *     class
   */
  public static Optional<Class<? extends JobValidator>> getJobValidatorClass(
      String driverOrDecoratorSimpleClassName) {
    try {
      return Optional.of(
          ReflectionUtil.getClass(
              driverOrDecoratorSimpleClassName + JobValidator.class.getSimpleName(),
              JobValidator.class,
              ClassConstants.SHARED_API_PACKAGE_NAME + ".validator.job"));
    } catch (MobileHarnessException e) {
      logger.atInfo().log(
          "No job validator for driver/decorator \"%s\" (expected class with name %s)",
          driverOrDecoratorSimpleClassName,
          driverOrDecoratorSimpleClassName + JobValidator.class.getSimpleName());
      return Optional.empty();
    }
  }

  /**
   * Gets the test lister class of the given driver. The test lister class should be a sub-class of
   * {@link Lister} and in package {@link com.google.wireless.qa.mobileharness.shared.api.lister}.
   *
   * @return the lister class for the given driver, or null if can not find the test lister class
   */
  @Nullable
  public static Class<? extends Lister> getListerClass(String driver) {
    try {
      return ReflectionUtil.getClass(
          driver + Lister.class.getSimpleName(),
          Lister.class,
          ClassConstants.SHARED_API_PACKAGE_NAME + ".lister");
    } catch (MobileHarnessException e) {
      logger.atInfo().log("No test lister found for driver \"%s\"", driver);
      return null;
    }
  }

  private ClientClassUtil() {}
}
