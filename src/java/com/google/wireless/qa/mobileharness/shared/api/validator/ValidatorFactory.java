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

import com.google.common.collect.Lists;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import java.util.Collection;
import java.util.List;

/** Simple factory for creating {@link Validator} instances. */
public class ValidatorFactory {
  /**
   * Creates a new {@link Validator} instance according to the given class type.
   *
   * @param validatorClass {@link Validator} class type
   * @throws MobileHarnessException if fails to create a new {@link Validator} instance
   */
  public Validator createValidator(Class<? extends Validator> validatorClass)
      throws MobileHarnessException {
    // Gets the constructor.
    try {
      return validatorClass.newInstance();
    } catch (IllegalAccessException
        | InstantiationException
        | ExceptionInInitializerError
        | SecurityException e) {
      throw new MobileHarnessException(
          ErrorCode.REFLECTION_INSTANTIATION_ERROR,
          "Failed to create new instance for " + validatorClass.getSimpleName(),
          e);
    }
  }

  /**
   * Create a list of validator classes.
   *
   * @param validatorClasses a list of {@code ValidatorDecorator} class types
   * @throws MobileHarnessException if fails to create {@code Validator} instance
   */
  public List<Validator> createValidators(Collection<Class<? extends Validator>> validatorClasses)
      throws MobileHarnessException {
    List<Validator> validators = Lists.newArrayList();
    for (Class<? extends Validator> validatorClass : validatorClasses) {
      validators.add(createValidator(validatorClass));
    }
    return validators;
  }
}
