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

package com.google.devtools.deviceaction.common.utils;

import static java.lang.String.format;

import com.google.common.base.Preconditions;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import javax.annotation.CheckForNull;

/**
 * Static convenience methods that help a method or constructor check whether it was invoked
 * correctly (that is, whether its <i>preconditions</i> were met).
 *
 * <p>Similar to the guava {@link Preconditions}, the methods are used to check if caller has made a
 * mistake. While the methods of {@link Verify} are to make verification checks of the consumed
 * APIs. See <a href="https://github.com/google/guava/wiki/ConditionalFailuresExplained">Conditional
 * failures explained</a> in the Guava User Guide for more advice.
 */
public final class Conditions {

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorType the type of the error
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *     message is formed by replacing each {@code %s} placeholder in the template with an
   *     argument. These are matched by position - the first {@code %s} gets {@code
   *     errorMessageArgs[0]}, etc. Unmatched arguments will be appended to the formatted message in
   *     square braces. Unmatched placeholders will be left as-is.
   * @param errorMessageArgs the arguments to be substituted into the message template. Arguments
   *     are converted to strings using {@link String#valueOf(Object)}.
   * @throws DeviceActionException if {@code expression} is false
   * @see Verify#verify Verify.verify()
   */
  @FormatMethod
  public static void checkState(
      boolean expression,
      ErrorType errorType,
      @FormatString String errorMessageTemplate,
      @CheckForNull Object... errorMessageArgs)
      throws DeviceActionException {
    if (!expression) {
      throw new DeviceActionException(
          "ILLEGAL_STATE", errorType, format(errorMessageTemplate, errorMessageArgs));
    }
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorType the type of the error.
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *     message is formed by replacing each {@code %s} placeholder in the template with an
   *     argument. These are matched by position - the first {@code %s} gets {@code
   *     errorMessageArgs[0]}, etc. Unmatched arguments will be appended to the formatted message in
   *     square braces. Unmatched placeholders will be left as-is.
   * @param errorMessageArgs the arguments to be substituted into the message template. Arguments
   *     are converted to strings using {@link String#valueOf(Object)}.
   * @throws DeviceActionException if {@code expression} is false
   */
  @FormatMethod
  public static void checkArgument(
      boolean expression,
      ErrorType errorType,
      @FormatString String errorMessageTemplate,
      @CheckForNull Object... errorMessageArgs)
      throws DeviceActionException {
    if (!expression) {
      throw new DeviceActionException(
          "ILLEGAL_ARGUMENT", errorType, format(errorMessageTemplate, errorMessageArgs));
    }
  }

  private Conditions() {}
}
