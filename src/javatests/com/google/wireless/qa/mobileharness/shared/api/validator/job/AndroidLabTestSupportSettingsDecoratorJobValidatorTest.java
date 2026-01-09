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
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecHelper;
import com.google.wireless.qa.mobileharness.shared.proto.Job;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidLabTestSupportSettingsDecoratorSpec;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AndroidLabTestSupportSettingsDecoratorJobValidator} */
@RunWith(JUnit4.class)
public class AndroidLabTestSupportSettingsDecoratorJobValidatorTest {
  private AndroidLabTestSupportSettingsDecoratorJobValidator validator;

  private JobInfo jobInfo;

  @Before
  public void setUp() {
    validator = new AndroidLabTestSupportSettingsDecoratorJobValidator();

    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                Job.JobType.newBuilder()
                    .setDevice("device_type")
                    .setDriver("NoOpDriver")
                    .addDecorator("AndroidLabTestSupportSettingsDecorator")
                    .build())
            .build();
  }

  @Test
  public void validate_withSignatureMasquerading_success() throws Exception {
    var spec =
        AndroidLabTestSupportSettingsDecoratorSpec.newBuilder()
            .setSignatureMasqueradingPackageId("com.test.me")
            .setSignatureFile("signature.txt")
            .build();
    setProtoSpec(spec);

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_withoutSignatureMasquerading_success() throws Exception {
    var spec = AndroidLabTestSupportSettingsDecoratorSpec.getDefaultInstance();
    setProtoSpec(spec);

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_invalidSignatureMasqueradingConfig_hasError() throws Exception {
    var spec =
        AndroidLabTestSupportSettingsDecoratorSpec.newBuilder()
            .setSignatureMasqueradingPackageId("com.test.me")
            .build();
    setProtoSpec(spec);

    assertThat(validator.validate(jobInfo)).isNotEmpty();
  }

  private void setProtoSpec(AndroidLabTestSupportSettingsDecoratorSpec spec) throws Exception {
    JobSpec.Builder jobSpecBuilder = JobSpec.newBuilder();
    var jobSpecHelper = new JobSpecHelper();
    jobSpecHelper.setSpec(jobSpecBuilder, spec);
    jobInfo.protoSpec().setProto(jobSpecBuilder.build());
  }
}
