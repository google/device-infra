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
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidEmulatorVideoDecoratorSpec;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AndroidEmulatorVideoDecoratorJobValidator} */
@RunWith(JUnit4.class)
public class AndroidEmulatorVideoDecoratorJobValidatorTest {

  private static final AndroidEmulatorVideoDecoratorSpec VALID_SPEC =
      AndroidEmulatorVideoDecoratorSpec.newBuilder()
          .setFps(5)
          .setBitRate(50000)
          .setTimeLimitSecs(900)
          .build();
  private AndroidEmulatorVideoDecoratorJobValidator validator;
  private JobInfo jobInfo;

  @Before
  public void setUp() {
    validator = new AndroidEmulatorVideoDecoratorJobValidator();
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                Job.JobType.newBuilder()
                    .setDevice("device_type")
                    .setDriver("NoOpDriver")
                    .addDecorator("AndroidEmulatorVideoDecorator")
                    .build())
            .build();
  }

  @Test
  public void validate_success() throws Exception {
    setProtoSpec(VALID_SPEC);

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_defaultProtoValue_success() throws Exception {
    setProtoSpec(AndroidEmulatorVideoDecoratorSpec.getDefaultInstance());

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_invalidFps_hasError() throws Exception {
    var spec = VALID_SPEC.toBuilder().setFps(-1).build();
    setProtoSpec(spec);

    assertThat(validator.validate(jobInfo)).isNotEmpty();
  }

  @Test
  public void validate_invalidBitRate_hasError() throws Exception {
    var spec = VALID_SPEC.toBuilder().setBitRate(-1).build();
    setProtoSpec(spec);

    assertThat(validator.validate(jobInfo)).isNotEmpty();
  }

  @Test
  public void validate_invalidTimeLimitSecs_hasError() throws Exception {
    var spec = VALID_SPEC.toBuilder().setTimeLimitSecs(-1).build();
    setProtoSpec(spec);

    assertThat(validator.validate(jobInfo)).isNotEmpty();
  }

  private void setProtoSpec(AndroidEmulatorVideoDecoratorSpec spec) throws Exception {
    JobSpec.Builder jobSpecBuilder = JobSpec.newBuilder();
    var jobSpecHelper = new JobSpecHelper();
    jobSpecHelper.setSpec(jobSpecBuilder, spec);
    jobInfo.protoSpec().setProto(jobSpecBuilder.build());
  }
}
