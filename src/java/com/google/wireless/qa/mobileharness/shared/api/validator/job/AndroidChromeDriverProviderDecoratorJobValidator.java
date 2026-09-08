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

package com.google.wireless.qa.mobileharness.shared.api.validator.job;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidChromeDriverProviderDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Validator for {@code AndroidChromeDriverProviderDecorator}. */
public class AndroidChromeDriverProviderDecoratorJobValidator
    implements JobValidator, AndroidChromeDriverProviderDecoratorSpec {

  private static final ImmutableSet<String> VALID_TARGET_TYPES =
      ImmutableSet.of(TARGET_TYPE_BROWSER, TARGET_TYPE_WEBVIEW);

  @VisibleForTesting
  static final String ERROR_INVALID_TARGET_TYPE =
      "Invalid target_type: must be either 'browser' or 'webview'.";

  @CanIgnoreReturnValue
  @Override
  public List<String> validate(JobInfo job) throws InterruptedException {
    List<String> errors = new ArrayList<>();
    validateTargetType(job, errors);
    return errors;
  }

  private static void validateTargetType(JobInfo job, List<String> errors) {
    if (!job.params().has(PARAM_TARGET_TYPE)) {
      return;
    }
    String targetTypeParam = job.params().get(PARAM_TARGET_TYPE);
    if (targetTypeParam == null || targetTypeParam.trim().isEmpty()) {
      return;
    }
    String targetType = targetTypeParam.trim().toLowerCase(Locale.US);
    if (!VALID_TARGET_TYPES.contains(targetType)) {
      errors.add(ERROR_INVALID_TARGET_TYPE);
    }
  }
}
