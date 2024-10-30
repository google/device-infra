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

import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidNativeBinSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Job validator for the {@code AndroidNativeBin} driver. */
public class AndroidNativeBinJobValidator implements JobValidator {

  @Override
  public List<String> validate(JobInfo job) {
    List<String> errors = new ArrayList<>();
    try {
      /**
       * As long as the tag "bin" is not empty, we are good here. Both driver and lister can support
       * multiple binary already (b/80501459).
       */
      job.files().checkNotEmpty(AndroidNativeBinSpec.TAG_BIN);
    } catch (MobileHarnessException e) {
      errors.add(e.getMessage());
    }
    if (job.params().has(AndroidNativeBinSpec.PARAM_ANDROID_BIN_TIMEOUT_SEC)) {
      int binRunTime = job.params().getInt(AndroidNativeBinSpec.PARAM_ANDROID_BIN_TIMEOUT_SEC, -1);
      if (binRunTime <= 0) {
        errors.add("Parameter \"bin_run_time_sec\" should be positive.");
      } else if (binRunTime
          >= (int) Duration.ofMillis(job.setting().getTimeout().getTestTimeoutMs()).toSeconds()) {
        errors.add(
            "AndroidNativeBin binary execution time is no less than"
                + " timeout time and will mark test result to TIMEOUT.");
      }
    }

    if (job.params().has(AndroidNativeBinSpec.PARAM_CPU_AFFINITY)) {
      String cpuAffinity = job.params().get(AndroidNativeBinSpec.PARAM_CPU_AFFINITY);
      if (!cpuAffinity.matches("\\p{XDigit}+")) {
        errors.add(
            "Parameter \"cpu_affinity\" should be a bitmask in hexadecimal without prefix \"0x\"");
      }
    }
    return errors;
  }
}
