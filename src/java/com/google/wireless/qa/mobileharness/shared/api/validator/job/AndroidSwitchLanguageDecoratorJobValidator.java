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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidSwitchLanguageDecoratorSpec;
import java.util.List;

/** Validator for the {@code AndroidSwitchLanguageDecorator} driver decorator. */
public class AndroidSwitchLanguageDecoratorJobValidator
    implements JobValidator, SpecConfigable<AndroidSwitchLanguageDecoratorSpec> {

  @Override
  public List<String> validate(JobInfo job) throws InterruptedException {
    ImmutableList.Builder<String> errors = ImmutableList.builder();

    List<AndroidSwitchLanguageDecoratorSpec> specs;
    try {
      specs =
          job.combinedSpecForDevices(
              this,
              subDeviceSpec ->
                  subDeviceSpec.decorators().getAll().contains("AndroidSwitchLanguageDecorator"));
    } catch (MobileHarnessException e) {
      errors.add("Could not load job specs: " + e);
      return errors.build();
    }

    for (AndroidSwitchLanguageDecoratorSpec spec : specs) {
      String language = spec.getLanguage();
      if (Strings.isNullOrEmpty(language)) {
        errors.add("Missing required parameter [language] for AndroidSwitchLanguageDecorator");
      }
      continue;
    }

    return errors.build();
  }
}
