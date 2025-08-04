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
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.XtsTradefedTestDriverSpec;
import java.util.List;

/** Job validator for the {@code XtsTradefedTest} driver. */
public class XtsTradefedTestJobValidator
    implements JobValidator, SpecConfigable<XtsTradefedTestDriverSpec> {

  @Override
  public List<String> validate(JobInfo jobInfo) throws InterruptedException {
    ImmutableList.Builder<String> errors = ImmutableList.builder();
    XtsTradefedTestDriverSpec spec;
    try {
      spec = jobInfo.combinedSpec(this);
    } catch (MobileHarnessException e) {
      errors.add(e.getMessage());
      return errors.build();
    }

    if (spec.getXtsType().isEmpty()) {
      errors.add("An xTS type must be specified, check xts_tradefed_test_spec.proto.");
    } else {
      try {
        String unused = spec.getXtsType();
      } catch (IllegalArgumentException e) {
        errors.add("Unknown xTS type: " + e.getMessage());
      }
    }

    if (spec.getXtsTestPlan().isEmpty()) {
      errors.add("An xTS test plan must be specified.");
    }

    if (spec.getXtsTestPlan().equals("retry")
        && spec.getPrevSessionXtsTestPlan().isEmpty()
        && (spec.getPrevSessionTestResultXml().isEmpty()
            || spec.getPrevSessionTestRecordFiles().isEmpty())) {
      errors.add(
          "The 'prev_session_xts_test_plan' must be specified when the test plan is 'retry', or"
              + " must provide 'prev_session_test_result_xml' AND"
              + " 'prev_session_test_record_files'.");
    }

    if (spec.getXtsRootDir().isEmpty()
        && spec.getAndroidXtsZip().isEmpty()
        && !spec.getXtsType().equals(XtsConstants.ATS_NO_OP_TEST_TYPE)) {
      errors.add(
          String.format(
              "At least one of the %s, %s must be specified.", "xts_root_dir", "android_xts_zip"));
    }

    return errors.build();
  }
}
