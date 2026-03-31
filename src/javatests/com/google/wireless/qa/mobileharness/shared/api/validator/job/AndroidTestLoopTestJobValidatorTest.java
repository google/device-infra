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

import static com.google.common.truth.Truth.assertThat;

import com.google.protobuf.util.Durations;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.proto.Job;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidTestLoopTestSpec;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AndroidTestLoopTestJobValidatorTest {

  private AndroidTestLoopTestJobValidator validator;

  @Before
  public void setUp() throws Exception {
    validator = new AndroidTestLoopTestJobValidator();
  }

  @Test
  public void validate_validJob_noErrors() throws Exception {
    JobInfo jobInfo = setUpJobInfo();
    AndroidTestLoopTestSpec spec = createValidSpec().build();
    jobInfo.scopedSpecs().add("AndroidTestLoopTestSpec", spec);

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_missingAppPackage_hasErrors() throws Exception {
    JobInfo jobInfo = setUpJobInfo();
    AndroidTestLoopTestSpec spec = createValidSpec().clearAppPackageId().build();
    jobInfo.scopedSpecs().add("AndroidTestLoopTestSpec", spec);

    assertThat(validator.validate(jobInfo))
        .containsExactly("App package must be configured but is empty.");
  }

  @Test
  public void validate_invalidAppPackage_hasErrors() throws Exception {
    JobInfo jobInfo = setUpJobInfo();
    AndroidTestLoopTestSpec spec = createValidSpec().setAppPackageId("1.2.3.invalid").build();
    jobInfo.scopedSpecs().add("AndroidTestLoopTestSpec", spec);

    assertThat(validator.validate(jobInfo))
        .containsExactly("App package is not a valid Android package name: 1.2.3.invalid");
  }

  @Test
  public void validate_missingScenarios_hasErrors() throws Exception {
    JobInfo jobInfo = setUpJobInfo();
    AndroidTestLoopTestSpec spec = createValidSpec().clearScenarios().build();
    jobInfo.scopedSpecs().add("AndroidTestLoopTestSpec", spec);

    assertThat(validator.validate(jobInfo))
        .containsExactly("Scenarios list must be configured but is empty.");
  }

  @Test
  public void validate_emptyScenarios_hasErrors() throws Exception {
    JobInfo jobInfo = setUpJobInfo();
    AndroidTestLoopTestSpec spec = createValidSpec().setScenarios(" ,  , ").build();
    jobInfo.scopedSpecs().add("AndroidTestLoopTestSpec", spec);

    assertThat(validator.validate(jobInfo))
        .containsExactly("Scenarios list must contain at least one valid scenario.");
  }

  @Test
  public void validate_timeoutTooLong_hasErrors() throws Exception {
    JobInfo jobInfo = setUpJobInfo(60000L); // Overall timeout 60s

    AndroidTestLoopTestSpec spec =
        createValidSpec().setScenariosTimeout(Durations.fromSeconds(120)).build();
    jobInfo.scopedSpecs().add("AndroidTestLoopTestSpec", spec);

    assertThat(validator.validate(jobInfo))
        .containsExactly(
            "Test timeout (PT2M) cannot be longer than overall MH test timeout (PT1M).");
  }

  private AndroidTestLoopTestSpec.Builder createValidSpec() {
    return AndroidTestLoopTestSpec.newBuilder()
        .setAppPackageId("com.example.app")
        .setScenarios("1,2,3")
        .setScenariosTimeout(Durations.fromSeconds(60));
  }

  private JobInfo setUpJobInfo() {
    return setUpJobInfo(120000L);
  }

  private JobInfo setUpJobInfo(long testTimeoutMs) {
    JobSetting jobSetting =
        JobSetting.newBuilder()
            .setTimeout(
                Timeout.newBuilder()
                    .setTestTimeoutMs(testTimeoutMs)
                    .setJobTimeoutMs(testTimeoutMs)
                    .build())
            .build();
    return JobInfo.newBuilder()
        .setLocator(new JobLocator("job_id", "job_name"))
        .setType(
            Job.JobType.newBuilder()
                .setDevice("device_type")
                .setDriver("AndroidTestLoopTest")
                .build())
        .setSetting(jobSetting)
        .build();
  }
}
