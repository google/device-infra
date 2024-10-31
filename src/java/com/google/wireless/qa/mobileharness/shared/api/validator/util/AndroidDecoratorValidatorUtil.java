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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Util class for Android decorators' validator. */
public final class AndroidDecoratorValidatorUtil {

  /** Make constructor private to keep this class as a static util class. */
  private AndroidDecoratorValidatorUtil() {}

  /**
   * Validate if {@code targetDecorator} executed before any decorators in a set of decorators.
   *
   * <p>Note, it returns empty list if 1) type of {@code job} is null, 2) {@code targetDecorator}
   * not found in the decorator list in job type, 3) {@code decorators} is empty.
   *
   * @param job data model of a Mobile Harness job
   * @param targetDecorator the target decorator being check
   * @param decorators set of decorators that need to run after {@code targetDecorator}
   * @return list of error messages for order check
   */
  public static ImmutableList<String> validateDecoratorsOrder(
      JobInfo job, String targetDecorator, Set<String> decorators) {
    return validateDecoratorsOrder(job, targetDecorator, decorators, /* targetRunBefore= */ true);
  }

  /**
   * Validate if {@code targetDecorator} executed before or after a set of decorators.
   *
   * <p>Note, it returns empty list if 1) type of {@code job} is null, or 2) {@code targetDecorator}
   * not found in the decorator list in job type, or 3) {@code decorators} is empty.
   *
   * @param job data model of a Mobile Harness job
   * @param targetDecorator the target decorator being check
   * @param decorators set of decorators that need to run before or after {@code targetDecorator}
   *     based on value of {@code targetRunBefore}
   * @param targetRunBefore set to {@code true} if need to validate {@code targetDecorator} executed
   *     before any decorators in {@code decorators}, or {@code false} if to validate {@code
   *     targetDecorator} executed after any decorators in {@code decorators}
   * @return list of error messages for order check
   */
  public static ImmutableList<String> validateDecoratorsOrder(
      JobInfo job, String targetDecorator, Set<String> decorators, boolean targetRunBefore) {
    if (job.type() == null || decorators.isEmpty()) {
      return ImmutableList.of();
    }

    List<String> errors = new ArrayList<>();
    boolean foundTarget = false;
    // Note the first decorator in the list is the last executed decorator
    for (String decorator : job.type().getDecoratorList()) {
      if (decorator.equals(targetDecorator)) {
        foundTarget = true;
        continue;
      }
      if (decorators.contains(decorator)) {
        if (targetRunBefore && foundTarget) {
          errors.add(
              String.format(
                  "%s should be executed before %s, please re-order decorators accordingly",
                  targetDecorator, decorator));
        } else if (!targetRunBefore && !foundTarget) {
          errors.add(
              String.format(
                  "%s should be executed after %s, please re-order decorators accordingly",
                  targetDecorator, decorator));
        }
      }
    }
    return foundTarget ? ImmutableList.copyOf(errors) : ImmutableList.of();
  }

  /**
   * Validate timeout value of job param {@code timeoutParamName} is in range [{@code minTimeout},
   * {@code maxTimeout}].
   *
   * @param job data model of a Mobile Harness job
   * @param timeoutParamName name for the param storing timeout value
   * @param unit time unit for {@code timeoutParamName}
   * @param minTimeout minimum timeout for {@code timeoutParamName}
   * @param maxTimeout maximum timeout for {@code timeoutParamName}
   * @return list of error messages for validation
   */
  public static ImmutableList<String> validateTimeoutWithinRange(
      JobInfo job,
      String timeoutParamName,
      TimeUnit unit,
      Duration minTimeout,
      Duration maxTimeout) {
    List<String> errors = new ArrayList<>();
    String timeoutStr = job.params().get(timeoutParamName);
    if (!StrUtil.isEmptyOrWhitespace(timeoutStr)) {
      Duration timeout = null;
      try {
        timeout = Duration.ofMillis(unit.toMillis(Long.parseLong(timeoutStr)));
      } catch (NumberFormatException nfe) {
        errors.add(String.format("Could not parse value for %s: %s", timeoutParamName, timeoutStr));
      }

      if (timeout != null) {
        if (timeout.compareTo(minTimeout) < 0 || timeout.compareTo(maxTimeout) > 0) {
          errors.add(
              String.format(
                  "Given value of param %s is out of range [%s, %s]: %s",
                  timeoutParamName, minTimeout, maxTimeout, timeout));
        }
      }
    }
    return ImmutableList.copyOf(errors);
  }
}
