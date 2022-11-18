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

package com.google.wireless.qa.mobileharness.shared.api.lister;

import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.List;

/** Generates the NoOpDriver dummy test, purely for consistency with the rest of the drivers. */
public class NoOpDriverLister implements Lister {

  @ParamAnnotation(required = false, help = "Number of tests generated.")
  public static final String PARAM_NUM_TESTS_GENERATED = "num_tests_generated";

  @Override
  public List<String> listTests(JobInfo jobInfo) {
    String jobName = jobInfo.locator().getName();
    List<String> tests = new ArrayList<>();
    int numTestsGenerated = jobInfo.params().getInt(PARAM_NUM_TESTS_GENERATED, 1);
    if (numTestsGenerated <= 1) {
      // Only 1 test, no suffix.
      tests.add(jobName);
    } else {
      // Suffix starts from 1.
      for (int i = 1; i <= numTestsGenerated; i++) {
        tests.add(String.format("%s_%d", jobName, i));
      }
    }
    return tests;
  }
}
