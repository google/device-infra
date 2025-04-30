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

import com.google.common.collect.Lists;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.wireless.qa.mobileharness.shared.api.job.JobTypeUtil;
import com.google.wireless.qa.mobileharness.shared.api.spec.CompositeDeviceDecoratorAdapterSpec;
import com.google.wireless.qa.mobileharness.shared.api.validator.util.CompositeDeviceAdapterValidatorUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.util.List;
import javax.annotation.Nullable;

/** Validator for the {@code CompositeDeviceDecoratorAdapter} driver decorator. */
public class CompositeDeviceDecoratorAdapterJobValidator implements JobValidator {
  @Override
  public List<String> validate(JobInfo job) throws InterruptedException {
    List<String> errors = Lists.newLinkedList();
    // Ideally we'd like to check if device implements CompositeDevice, but there is no way to
    // reliably resolve a device string to a class on the client side (b/34181247). So just skip it
    // and instead rely on devices to call addSupportedDecorator() properly.
    validateSubdecorators(errors, job);
    return errors;
  }

  private void validateSubdecorators(List<String> errors, JobInfo job) throws InterruptedException {
    String subTypeStr = checkSubdecoratorParamPresent(errors, job);
    JobType subType = null;
    if (subTypeStr != null) {
      // The first two values are device and driver; we will make fake ones for parsing.
      subTypeStr = "_nodevice_+_nodriver_+" + subTypeStr;
      try {
        subType = JobTypeUtil.parseString(subTypeStr);
      } catch (MobileHarnessException e) {
        errors.add(
            String.format(
                "Failed to parse %s param: %s",
                CompositeDeviceDecoratorAdapterSpec.PARAM_COMPOSITE_DEVICE_SUBDECORATORS, e));
      }
    }
    if (subType != null) {
      if (subType.getDecoratorList().isEmpty()) {
        errors.add("No decorators found in subdecorator list: " + subType);
      } else {
        CompositeDeviceAdapterValidatorUtil.runSubValidators(
            errors, subType.getDecoratorList(), job);
      }
    }
  }

  private static @Nullable String checkSubdecoratorParamPresent(List<String> errors, JobInfo job) {
    String subTypeStr =
        job.params().get(CompositeDeviceDecoratorAdapterSpec.PARAM_COMPOSITE_DEVICE_SUBDECORATORS);
    if (StrUtil.isEmptyOrWhitespace(subTypeStr)) {
      errors.add(
          "Parameter ["
              + CompositeDeviceDecoratorAdapterSpec.PARAM_COMPOSITE_DEVICE_SUBDECORATORS
              + "] is null or empty");
      return null;
    }
    return subTypeStr;
  }
}
