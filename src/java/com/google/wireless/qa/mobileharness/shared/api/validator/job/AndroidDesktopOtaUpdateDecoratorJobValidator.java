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
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidDesktopOtaUpdateDecoratorSpec;
import java.util.ArrayList;
import java.util.List;

/** Validator for the {@code AndroidDesktopOtaUpdateDecorator} driver decorator. */
public class AndroidDesktopOtaUpdateDecoratorJobValidator
    implements JobValidator, SpecConfigable<AndroidDesktopOtaUpdateDecoratorSpec> {

  @Override
  public List<String> validate(JobInfo job) throws InterruptedException {
    List<String> errors = new ArrayList<>();
    List<AndroidDesktopOtaUpdateDecoratorSpec> specs;
    try {
      specs =
          job.combinedSpecForDevices(
              this,
              subDeviceSpec ->
                  subDeviceSpec.decorators().getAll().contains("AndroidDesktopOtaUpdateDecorator"));
    } catch (MobileHarnessException e) {
      errors.add(e.getMessage());
      return errors;
    }

    for (AndroidDesktopOtaUpdateDecoratorSpec spec : specs) {
      if (!spec.hasOtaTools()) {
        errors.add(
            "Please specify the android build path to the otatools.zip file in ota-tools file"
                + " tag.");
      }
      if (!spec.hasOtaPackage()) {
        errors.add(
            "Please specify the android build path to the ota package zip file in ota-package file"
                + " tag.");
      }
    }

    return errors;
  }
}
