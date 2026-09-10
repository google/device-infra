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
import static com.google.wireless.qa.mobileharness.shared.api.spec.AndroidChromeDriverProviderDecoratorSpec.PARAM_TARGET_TYPE;
import static com.google.wireless.qa.mobileharness.shared.api.validator.job.AndroidChromeDriverProviderDecoratorJobValidator.ERROR_INVALID_TARGET_TYPE;

import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AndroidChromeDriverProviderDecoratorJobValidator}. */
@RunWith(JUnit4.class)
public class AndroidChromeDriverProviderDecoratorJobValidatorTest {

  private AndroidChromeDriverProviderDecoratorJobValidator validator;
  private JobInfo jobInfo;

  @Before
  public void setUp() {
    validator = new AndroidChromeDriverProviderDecoratorJobValidator();
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                JobType.newBuilder()
                    .setDevice("AndroidRealDevice")
                    .setDriver("NoOpDriver")
                    .addDecorator("AndroidChromeDriverProviderDecorator")
                    .build())
            .build();
  }

  @Test
  public void validate_emptyParams_success() throws Exception {
    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_browserTargetType_success() throws Exception {
    jobInfo.params().add(PARAM_TARGET_TYPE, "browser");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_webviewTargetType_success() throws Exception {
    jobInfo.params().add(PARAM_TARGET_TYPE, "webview");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_caseInsensitiveTargetType_success() throws Exception {
    jobInfo.params().add(PARAM_TARGET_TYPE, "  WebView ");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_invalidTargetType_returnsError() throws Exception {
    jobInfo.params().add(PARAM_TARGET_TYPE, "firefox");

    assertThat(validator.validate(jobInfo)).containsExactly(ERROR_INVALID_TARGET_TYPE);
  }
}
