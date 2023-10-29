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

import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import javax.annotation.CheckForNull;

/**
 * Static convenience methods to check if the codes behave as expected.
 *
 * <p>Similar to the guava {@link Verify}, the methods are used to make verification checks of the
 * consumed APIs. While the methods of {@link Verify} are to check if caller has made a mistake. See
 * <a href="https://github.com/google/guava/wiki/ConditionalFailuresExplained">Conditional *
 * failures explained</a> in the Guava User Guide for more advice. Example:
 *
 * <pre>{@code
 * Bill bill = remoteService.getLastUnpaidBill();
 *
 * // In case bug 12345 happens again we'd rather just die
 * Verify.verify(bill.status() == Status.UNPAID,
 *    "Unexpected bill status: %s", bill.status());
 *
 * }</pre>
 */
public final class Verify {

  /**
   * Ensures that {@code expression} is {@code true}, throwing a {@link DeviceActionException} with
   * a custom message otherwise.
   *
   * @param expression a boolean expression
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *     message is formed by replacing each {@code %s} placeholder in the template with an
   *     argument. These are matched by position - the first {@code %s} gets {@code
   *     errorMessageArgs[0]}, etc. Unmatched arguments will be appended to the formatted message in
   *     square braces. Unmatched placeholders will be left as-is.
   * @param errorMessageArgs the arguments to be substituted into the message template. Arguments
   *     are converted to strings using {@link String#valueOf(Object)}.
   * @throws DeviceActionException if {@code expression} is {@code false}
   * @see Conditions#checkState Conditions.checkState()
   */
  @FormatMethod
  public static void verify(
      boolean expression,
      @FormatString String errorMessageTemplate,
      @CheckForNull Object... errorMessageArgs)
      throws DeviceActionException {
    if (!expression) {
      throw new DeviceActionException(
          "VERIFICATION_FAILED",
          ErrorType.DEPENDENCY_ISSUE,
          format(errorMessageTemplate, errorMessageArgs));
    }
  }

  private Verify() {}
}
