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

import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidNetworkActivityLoggingDecoratorSpec;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AndroidNetworkActivityLoggingJobValidatorTest {

  private final AndroidNetworkActivityLoggingJobValidator validator =
      new AndroidNetworkActivityLoggingJobValidator();
  private final JobInfo jobInfo = setUpJobInfo();

  @Test
  public void validate_noSpec_noErrors() throws Exception {
    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_defaultSpec_noErrors() throws Exception {
    AndroidNetworkActivityLoggingDecoratorSpec spec =
        AndroidNetworkActivityLoggingDecoratorSpec.getDefaultInstance();
    jobInfo
        .scopedSpecs()
        .add(AndroidNetworkActivityLoggingDecoratorSpec.class.getSimpleName(), spec);

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_validPackageId_noErrors() throws Exception {
    AndroidNetworkActivityLoggingDecoratorSpec spec =
        AndroidNetworkActivityLoggingDecoratorSpec.newBuilder()
            .addPackageIds("com.some.app")
            .build();
    jobInfo
        .scopedSpecs()
        .add(AndroidNetworkActivityLoggingDecoratorSpec.class.getSimpleName(), spec);

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_invalidPackageId_hasErrorMessageWithInvalidPackageId() throws Exception {
    AndroidNetworkActivityLoggingDecoratorSpec spec =
        AndroidNetworkActivityLoggingDecoratorSpec.newBuilder()
            .addPackageIds("com.some.app")
            .addPackageIds("invalidPackageId")
            .build();
    jobInfo
        .scopedSpecs()
        .add(AndroidNetworkActivityLoggingDecoratorSpec.class.getSimpleName(), spec);

    assertThat(validator.validate(jobInfo)).containsExactly("Invalid package id: invalidPackageId");
  }

  private static JobInfo setUpJobInfo() {
    return JobInfo.newBuilder()
        .setLocator(new JobLocator("job_id", "job_name"))
        .setType(
            Job.JobType.newBuilder()
                .setDevice("device_type")
                .setDriver("AndroidInstrumentation")
                .build())
        .build();
  }
}
