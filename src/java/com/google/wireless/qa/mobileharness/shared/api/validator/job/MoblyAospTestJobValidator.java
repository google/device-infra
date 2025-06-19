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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.errorprone.annotations.Keep;
import com.google.wireless.qa.mobileharness.shared.api.spec.MoblyAospTestSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.List;

/** Job validator for the {@code MoblyAospTest} driver. */
@Keep
public class MoblyAospTestJobValidator implements JobValidator {

  @Override
  public List<String> validate(JobInfo job) {
    // Skip the client validator if the test package is from XML.
    if (!job.params().getBool("enable_mobly_aosp_test_validator", true)) {
      return ImmutableList.of();
    }

    List<String> errors = new ArrayList<>();
    // Ensure that only one test package is specified in the job info.
    try {
      job.files().checkUnique(MoblyAospTestSpec.FILE_MOBLY_PKG);
    } catch (MobileHarnessException e) {
      errors.add(e.getMessage());
    }
    return ImmutableList.copyOf(errors);
  }
}
