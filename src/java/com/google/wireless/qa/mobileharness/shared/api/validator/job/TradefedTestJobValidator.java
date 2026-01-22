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
import com.google.wireless.qa.mobileharness.shared.api.spec.TradefedTestSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.TradefedTestDriverSpec;
import java.util.List;

/** Job validator for the {@code TradefedTest} driver. */
public class TradefedTestJobValidator
    implements JobValidator, SpecConfigable<TradefedTestDriverSpec> {

  @Override
  public List<String> validate(JobInfo jobInfo) throws InterruptedException {
    ImmutableList.Builder<String> errors = ImmutableList.builder();
    TradefedTestDriverSpec spec;
    try {
      spec = jobInfo.combinedSpec(this);
    } catch (MobileHarnessException e) {
      errors.add(e.getMessage());
      return errors.build();
    }

    if (spec.getXtsType().isEmpty()) {

      // Need to check that it does not have xts related fields in non xts mode.
      if (!spec.getXtsTestPlan().isEmpty()) {
        errors.add(
            "When xts_type is not specified, this is running non xTS tests, so xts_test_plan must"
                + " be empty.");
      }
      if (!spec.getXtsRootDir().isEmpty()) {
        errors.add(
            "When xts_type is not specified, this is running non xTS tests, so xts_root_dir must be"
                + " empty.");
      }
      if (!spec.getAndroidXtsZip().isEmpty()) {
        errors.add(
            "When xts_type is not specified, this is running non xTS tests, so android_xts_zip must"
                + " be empty.");
      }
      if (!spec.getPrevSessionTestResultXml().isEmpty()) {
        errors.add(
            "When xts_type is not specified, this is running non xTS tests, so"
                + " prev_session_test_result_xml must be empty.");
      }
      if (!spec.getPrevSessionTestRecordFiles().isEmpty()) {
        errors.add(
            "When xts_type is not specified, this is running non xTS tests, so"
                + " prev_session_test_record_files must be empty.");
      }
      if (!spec.getPrevSessionXtsTestPlan().isEmpty()) {
        errors.add(
            "When xts_type is not specified, this is running non xTS tests, so"
                + " prev_session_xts_test_plan must be empty.");
      }
      return errors.build();
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

    if (spec.getXtsTestPlan().equals("retry") && spec.getPrevSessionXtsTestPlan().isEmpty()) {
      if (spec.getPrevSessionTestResultXml().isEmpty()
          && spec.getPrevSessionTestRecordFiles().isEmpty()
          && !jobInfo
              .files()
              .isTagNotEmpty(TradefedTestSpec.TAG_PREV_SESSION_TEST_RECORD_PB_FILES)) {
        errors.add(
            "When the test plan is 'retry' and no 'prev_session_test_result_xml',"
                + " 'prev_session_test_record_files', 'prev_session_test_record_pb_files'"
                + " specified, 'prev_session_xts_test_plan' must be specified.");
      } else if (spec.getPrevSessionTestResultXml().isEmpty()) {
        errors.add(
            "When the test plan is 'retry' and no 'prev_session_xts_test_plan' specified,"
                + " 'prev_session_test_result_xml' must be specified.");
      } else if (spec.getPrevSessionTestRecordFiles().isEmpty()
          && !jobInfo
              .files()
              .isTagNotEmpty(TradefedTestSpec.TAG_PREV_SESSION_TEST_RECORD_PB_FILES)) {
        errors.add(
            "When the test plan is 'retry' and no 'prev_session_xts_test_plan' specified, either"
                + " 'prev_session_test_record_files' or 'prev_session_test_record_pb_files' must be"
                + " specified.");
      }
    }

    if (spec.getXtsRootDir().isEmpty() && spec.getAndroidXtsZip().isEmpty()) {
      errors.add(
          String.format(
              "At least one of the %s, %s must be specified.", "xts_root_dir", "android_xts_zip"));
    }

    return errors.build();
  }
}
