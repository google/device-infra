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
import com.google.wireless.qa.mobileharness.shared.api.validator.Validator;
import com.google.wireless.qa.mobileharness.shared.util.ReflectionUtil;
import java.util.Optional;

/** Util for loading {@link Validator}. */
public class ValidatorClassUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Gets the validator class of the given driver or decorator. The validator class should be a
   * subclass of {@link Validator} and in package {@link
   * com.google.wireless.qa.mobileharness.shared.api.validator}.
   *
   * @return the validator class for the given driver, or null if can not find the test validator
   *     class
   */
  public static Optional<Class<? extends Validator>> getValidatorClass(
      String driverOrDecoratorSimpleClassName) {
    try {
      return Optional.of(
          ReflectionUtil.getClass(
              driverOrDecoratorSimpleClassName + Validator.class.getSimpleName(),
              Validator.class,
              ClassConstants.SHARED_API_PACKAGE_NAME + ".validator"));
    } catch (MobileHarnessException e) {
      logger.atInfo().log(
          "No validator for driver/decorator \"%s\" (expected class with name %s)",
          driverOrDecoratorSimpleClassName,
          driverOrDecoratorSimpleClassName + Validator.class.getSimpleName());
      return Optional.empty();
    }
  }

  private ValidatorClassUtil() {}
}
