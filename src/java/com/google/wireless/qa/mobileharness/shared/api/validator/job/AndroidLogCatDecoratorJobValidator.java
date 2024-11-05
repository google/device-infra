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

import com.google.common.collect.ImmutableSet;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidLogCatSpec;
import com.google.wireless.qa.mobileharness.shared.api.validator.util.AndroidDecoratorValidatorUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.List;

/** Job validator for {@link AndroidLogCatDecorator} */
public class AndroidLogCatDecoratorJobValidator implements JobValidator {

  @Override
  public List<String> validate(JobInfo job) throws InterruptedException {
    List<String> errors = new ArrayList<>();
    if (job.params().has(AndroidLogCatSpec.PARAM_LOG_BUFFER_SIZE_KB)) {
      try {
        job.params()
            .checkInt(
                AndroidLogCatSpec.PARAM_LOG_BUFFER_SIZE_KB,
                AndroidLogCatSpec.LOG_BUFFER_SIZE_MIN_KB,
                AndroidLogCatSpec.LOG_BUFFER_SIZE_MAX_KB);
      } catch (MobileHarnessException e) {
        errors.add(e.getMessage());
      }
    }
    if (job.params().isTrue(AndroidLogCatSpec.PARAM_ASYNC_LOG)) {
      // For async log, AndroidLogCatDecorator must execute after
      // AndroidReinstallSystemAppsDecorator otherwise AndroidReinstallSystemAppsDecorator will
      // reboot device which interrupt running async logcat command (b/144229264).
      errors.addAll(
          AndroidDecoratorValidatorUtil.validateDecoratorsOrder(
              job,
              "AndroidLogCatDecorator",
              ImmutableSet.of("AndroidReinstallSystemAppsDecorator"),
              /* targetRunBefore= */ false));
    }
    return errors;
  }
}
