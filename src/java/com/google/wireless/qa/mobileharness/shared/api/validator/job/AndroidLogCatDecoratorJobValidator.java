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
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidLogCatSpec;
import com.google.wireless.qa.mobileharness.shared.api.validator.util.AndroidDecoratorValidatorUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidLogCatDecoratorSpec;
import java.util.ArrayList;
import java.util.List;

/** Job validator for {@code AndroidLogCatDecorator}. */
public class AndroidLogCatDecoratorJobValidator
    implements JobValidator, SpecConfigable<AndroidLogCatDecoratorSpec> {

  @Override
  public List<String> validate(JobInfo job) throws InterruptedException {
    List<String> errors = new ArrayList<>();
    List<AndroidLogCatDecoratorSpec> specs;
    try {
      specs =
          job.combinedSpecForDevices(
              this,
              subDeviceSpec ->
                  subDeviceSpec.decorators().getAll().contains("AndroidLogCatDecorator"));
    } catch (MobileHarnessException e) {
      errors.add("Error getting combined spec: " + e.getErrorId());
      return errors;
    }

    boolean hasAsyncLog = false;
    for (AndroidLogCatDecoratorSpec spec : specs) {
      if (spec.hasLogBufferSizeKb()) {
        int logBufferSizeKb = spec.getLogBufferSizeKb();
        if (logBufferSizeKb < AndroidLogCatSpec.LOG_BUFFER_SIZE_MIN_KB
            || logBufferSizeKb > AndroidLogCatSpec.LOG_BUFFER_SIZE_MAX_KB) {
          errors.add(
              String.format(
                  "Given value %d of %s is not in range [%d, %d].",
                  logBufferSizeKb,
                  AndroidLogCatSpec.PARAM_LOG_BUFFER_SIZE_KB,
                  AndroidLogCatSpec.LOG_BUFFER_SIZE_MIN_KB,
                  AndroidLogCatSpec.LOG_BUFFER_SIZE_MAX_KB));
        }
      } else if (job.params().has(AndroidLogCatSpec.PARAM_LOG_BUFFER_SIZE_KB)) {
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
      if (spec.getAsyncLog()) {
        hasAsyncLog = true;
      }
    }

    if (hasAsyncLog) {
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
