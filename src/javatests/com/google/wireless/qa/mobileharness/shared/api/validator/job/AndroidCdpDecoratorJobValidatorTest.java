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
import static com.google.wireless.qa.mobileharness.shared.api.validator.job.AndroidCdpDecoratorJobValidator.ERROR_INVALID_DEBUG_PORT;
import static com.google.wireless.qa.mobileharness.shared.api.validator.job.AndroidCdpDecoratorJobValidator.ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX;
import static com.google.wireless.qa.mobileharness.shared.api.validator.job.AndroidCdpDecoratorJobValidator.ERROR_PACKAGE_NAME_MANDATORY_FOR_WEBVIEW;
import static com.google.wireless.qa.mobileharness.shared.api.validator.job.AndroidCdpDecoratorJobValidator.PARAM_DEBUG_PORT;
import static com.google.wireless.qa.mobileharness.shared.api.validator.job.AndroidCdpDecoratorJobValidator.PARAM_LOCAL_SERVER_PORT;
import static com.google.wireless.qa.mobileharness.shared.api.validator.job.AndroidCdpDecoratorJobValidator.PARAM_PACKAGE_NAME;
import static com.google.wireless.qa.mobileharness.shared.api.validator.job.AndroidCdpDecoratorJobValidator.PARAM_TARGET_TYPE;

import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AndroidCdpDecoratorJobValidator}. */
@RunWith(JUnit4.class)
public class AndroidCdpDecoratorJobValidatorTest {

  private AndroidCdpDecoratorJobValidator validator;
  private JobInfo jobInfo;

  @Before
  public void setUp() {
    validator = new AndroidCdpDecoratorJobValidator();
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                JobType.newBuilder()
                    .setDevice("AndroidRealDevice")
                    .setDriver("NoOpDriver")
                    .addDecorator("AndroidCdpDecorator")
                    .build())
            .build();
  }

  @Test
  public void validate_emptyParams_success() throws Exception {
    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_browserDefault_withExplicitTargetType_success() throws Exception {
    jobInfo.params().add(PARAM_TARGET_TYPE, "browser");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_browserDefault_withPackageName_success() throws Exception {
    jobInfo.params().add(PARAM_TARGET_TYPE, "browser");
    jobInfo.params().add(PARAM_PACKAGE_NAME, "com.android.chrome");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_webview_withPackageName_success() throws Exception {
    jobInfo.params().add(PARAM_TARGET_TYPE, "webview");
    jobInfo.params().add(PARAM_PACKAGE_NAME, "com.example.app");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_webview_caseInsensitiveTargetType_success() throws Exception {
    jobInfo.params().add(PARAM_TARGET_TYPE, "  WebView  ");
    jobInfo.params().add(PARAM_PACKAGE_NAME, "com.example.app");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_webview_missingPackageName_returnsError() throws Exception {
    jobInfo.params().add(PARAM_TARGET_TYPE, "webview");

    assertThat(validator.validate(jobInfo))
        .containsExactly(ERROR_PACKAGE_NAME_MANDATORY_FOR_WEBVIEW);
  }

  @Test
  public void validate_webview_emptyPackageName_returnsError() throws Exception {
    jobInfo.params().add(PARAM_TARGET_TYPE, "webview");
    jobInfo.params().add(PARAM_PACKAGE_NAME, "");

    assertThat(validator.validate(jobInfo))
        .containsExactly(ERROR_PACKAGE_NAME_MANDATORY_FOR_WEBVIEW);
  }

  @Test
  public void validate_webview_blankPackageName_returnsError() throws Exception {
    jobInfo.params().add(PARAM_TARGET_TYPE, "webview");
    jobInfo.params().add(PARAM_PACKAGE_NAME, "   ");

    assertThat(validator.validate(jobInfo))
        .containsExactly(ERROR_PACKAGE_NAME_MANDATORY_FOR_WEBVIEW);
  }

  @Test
  public void validate_debugPort_validPort_success() throws Exception {
    jobInfo.params().add(PARAM_DEBUG_PORT, "9222");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_debugPort_zeroPort_success() throws Exception {
    jobInfo.params().add(PARAM_DEBUG_PORT, "0");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_debugPort_maxPort_success() throws Exception {
    jobInfo.params().add(PARAM_DEBUG_PORT, "65535");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_debugPort_withSurroundingWhitespace_success() throws Exception {
    jobInfo.params().add(PARAM_DEBUG_PORT, "  9222  ");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_debugPort_nonInteger_returnsError() throws Exception {
    jobInfo.params().add(PARAM_DEBUG_PORT, "not_a_number");

    assertThat(validator.validate(jobInfo)).containsExactly(ERROR_INVALID_DEBUG_PORT);
  }

  @Test
  public void validate_debugPort_emptyString_returnsError() throws Exception {
    jobInfo.params().add(PARAM_DEBUG_PORT, "");

    assertThat(validator.validate(jobInfo)).containsExactly(ERROR_INVALID_DEBUG_PORT);
  }

  @Test
  public void validate_debugPort_negative_returnsError() throws Exception {
    jobInfo.params().add(PARAM_DEBUG_PORT, "-1");

    assertThat(validator.validate(jobInfo)).containsExactly(ERROR_INVALID_DEBUG_PORT);
  }

  @Test
  public void validate_debugPort_outOfRange_returnsError() throws Exception {
    jobInfo.params().add(PARAM_DEBUG_PORT, "65536");

    assertThat(validator.validate(jobInfo)).containsExactly(ERROR_INVALID_DEBUG_PORT);
  }

  @Test
  public void validate_localServerPort_singlePort_success() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "8080");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_localServerPort_portMapping_success() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "8080:9090");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_localServerPort_multipleMappingsWithSpaces_success() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "8080, 9090:9090, 3000:3000");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_localServerPort_zeroPorts_success() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "0:0");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_localServerPort_maxPorts_success() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "65535:65535");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_localServerPort_singlePortOutOfRange_returnsError() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "70000");

    assertThat(validator.validate(jobInfo))
        .containsExactly(ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX + "70000");
  }

  @Test
  public void validate_localServerPort_singlePortNegative_returnsError() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "-1");

    assertThat(validator.validate(jobInfo))
        .containsExactly(ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX + "-1");
  }

  @Test
  public void validate_localServerPort_guestPortOutOfRange_returnsError() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "70000:8080");

    assertThat(validator.validate(jobInfo))
        .containsExactly(ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX + "70000:8080");
  }

  @Test
  public void validate_localServerPort_hostPortOutOfRange_returnsError() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "8080:70000");

    assertThat(validator.validate(jobInfo))
        .containsExactly(ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX + "8080:70000");
  }

  @Test
  public void validate_localServerPort_negativeGuestPort_returnsError() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "-1:8080");

    assertThat(validator.validate(jobInfo))
        .containsExactly(ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX + "-1:8080");
  }

  @Test
  public void validate_localServerPort_missingHostPort_returnsError() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "8080:");

    assertThat(validator.validate(jobInfo))
        .containsExactly(ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX + "8080:");
  }

  @Test
  public void validate_localServerPort_missingGuestPort_returnsError() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, ":8080");

    assertThat(validator.validate(jobInfo))
        .containsExactly(ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX + ":8080");
  }

  @Test
  public void validate_localServerPort_nonNumericSingle_returnsError() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "abc");

    assertThat(validator.validate(jobInfo))
        .containsExactly(ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX + "abc");
  }

  @Test
  public void validate_localServerPort_nonNumericHost_returnsError() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "8080:abc");

    assertThat(validator.validate(jobInfo))
        .containsExactly(ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX + "8080:abc");
  }

  @Test
  public void validate_localServerPort_nonNumericGuest_returnsError() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "abc:8080");

    assertThat(validator.validate(jobInfo))
        .containsExactly(ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX + "abc:8080");
  }

  @Test
  public void validate_localServerPort_multipleColons_returnsError() throws Exception {
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "8080:8080:8080");

    assertThat(validator.validate(jobInfo))
        .containsExactly(ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX + "8080:8080:8080");
  }

  @Test
  public void validate_multipleInvalidParameters_aggregatesAllErrors() throws Exception {
    jobInfo.params().add(PARAM_TARGET_TYPE, "webview");
    jobInfo.params().add(PARAM_DEBUG_PORT, "invalid_port");
    jobInfo.params().add(PARAM_LOCAL_SERVER_PORT, "bad1, bad2");

    assertThat(validator.validate(jobInfo))
        .containsExactly(
            ERROR_PACKAGE_NAME_MANDATORY_FOR_WEBVIEW,
            ERROR_INVALID_DEBUG_PORT,
            ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX + "bad1",
            ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX + "bad2");
  }
}
