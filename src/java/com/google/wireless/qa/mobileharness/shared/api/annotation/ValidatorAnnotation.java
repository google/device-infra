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

package com.google.wireless.qa.mobileharness.shared.api.annotation;

import com.google.errorprone.annotations.Keep;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Flag annotation which indicates that a method is a validator for the device/driver/decorator/step
 * in which it is declared.
 *
 * <p>The annotated method should have the parameter types, the return type, the modifiers, etc.,
 * specified by the type of the annotation.
 *
 * @see Type
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Keep
public @interface ValidatorAnnotation {

  /** Validator type. */
  enum Type {

    /**
     * A job validator.
     *
     * <p>The annotated method should be like the following:
     *
     * <pre>
     * <b>&#064;ValidatorAnnotation(type = Type.JOB)</b>
     * private <b>static</b> <b>List&lt;String&gt;</b> validateJob(<b>JobInfo</b> jobInfo)
     *     throws ... {
     *   ... // If the job is valid, returns an empty list, or returns a list of error messages.
     * }
     * </pre>
     *
     * <p>{@linkplain com.google.wireless.qa.mobileharness.shared.api.validator.JobChecker
     * #validateJobByValidatorMethods JobChecker.validateJobByValidatorMethods} could be used for
     * testing a job validator method.
     *
     * @see
     *     com.google.wireless.qa.mobileharness.shared.api.validator.JobChecker#validateJobByValidatorMethods
     */
    JOB,
  }

  /** The validator type. */
  Type type();
}
