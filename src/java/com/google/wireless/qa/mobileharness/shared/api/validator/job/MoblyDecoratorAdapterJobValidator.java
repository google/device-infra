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

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.validator.util.CompositeDeviceAdapterValidatorUtil;
import com.google.wireless.qa.mobileharness.shared.api.validator.util.MoblyDecoratorAdapterJobValidatorUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.DeviceToJobSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.MoblyDecoratorAdapterSpec;
import java.util.ArrayList;
import java.util.List;

/** Job validator for {@code MoblyDecoratorAdapter}. */
public class MoblyDecoratorAdapterJobValidator
    implements SpecConfigable<MoblyDecoratorAdapterSpec>, JobValidator {

  @Override
  public List<String> validate(JobInfo job) throws InterruptedException {
    List<String> errors = new ArrayList<>();

    // Check spec
    MoblyDecoratorAdapterSpec spec = null;
    try {
      spec = job.combinedSpec(this);
      MoblyDecoratorAdapterJobValidatorUtil.validateSpec(spec);
    } catch (MobileHarnessException e) {
      errors.add(e.getMessage());
    }

    // Ideally we'd like to check if device implements CompositeDevice, but there is no way to
    // reliably resolve a device string to a class on the client side (b/34181247). So just skip it
    // and instead rely on devices to call addSupportedDecorator() properly.

    // Check params
    if (spec != null) {
      for (DeviceToJobSpec deviceToJobSpec : spec.getDeviceToJobSpecList()) {
        checkDeviceToJobSpec(errors, job, deviceToJobSpec);
      }
    }

    return errors;
  }

  private static void checkDeviceToJobSpec(List<String> errors, JobInfo job, DeviceToJobSpec spec)
      throws InterruptedException {
    JobInfo subJobInfo;
    try {
      subJobInfo =
          MoblyDecoratorAdapterJobValidatorUtil.makeSubDeviceJobInfo(job, spec.getJobSpec());
    } catch (MobileHarnessException e) {
      errors.add("Unable to make subdevice job info: " + e.getMessage());
      return;
    }
    CompositeDeviceAdapterValidatorUtil.runSubValidators(
        errors, spec.getJobSpec().getDecoratorList(), subJobInfo);
  }
}
