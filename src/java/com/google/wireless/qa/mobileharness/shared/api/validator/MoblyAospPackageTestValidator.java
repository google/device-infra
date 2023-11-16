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

package com.google.wireless.qa.mobileharness.shared.api.validator;

import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfoFactory.SuiteType;
import com.google.wireless.qa.mobileharness.shared.api.driver.MoblyAospPackageTest;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.List;

/** Validator for the {@link MoblyAospPackageTest} driver. */
public class MoblyAospPackageTestValidator extends BaseValidator {

  @Override
  public List<String> validateJob(JobInfo job) {
    List<String> errors = new ArrayList<>();
    // Ensure that only one test package is specified in the job info.
    try {
      job.files().checkUnique(MoblyAospPackageTest.FILE_MOBLY_PKG);
    } catch (MobileHarnessException e) {
      errors.add(e.getMessage());
    }

    String suiteType = job.params().get(MoblyAospPackageTest.PARAM_CERTIFICATION_SUITE_TYPE);
    if (!Strings.isNullOrEmpty(suiteType)) {
      try {
        SuiteType unused = SuiteType.valueOf(Ascii.toUpperCase(suiteType));
      } catch (IllegalArgumentException e) {
        errors.add(
            String.format(
                "The param certification_suite_type needs to be \"CTS\", while it's given \"%s\".",
                suiteType));
      }
    }

    return errors;
  }
}
