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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.wireless.qa.mobileharness.shared.api.spec.XtsTradefedTestSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.XtsTradefedTestDriverSpec;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link XtsTradefedTestJobValidator}. */
@RunWith(JUnit4.class)
public final class XtsTradefedTestJobValidatorTest {

  private XtsTradefedTestJobValidator validator;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private JobInfo mockJobInfo;
  @Mock private Files mockJobFiles;

  @Before
  public void setUp() {
    validator = new XtsTradefedTestJobValidator();
    when(mockJobInfo.files()).thenReturn(mockJobFiles);
  }

  @Test
  public void validateJob_pass() throws Exception {
    when(mockJobInfo.combinedSpec(any()))
        .thenReturn(
            XtsTradefedTestDriverSpec.newBuilder()
                .setXtsType("cts")
                .setXtsTestPlan("cts")
                .setXtsRootDir("/path/to/cts_root")
                .build());

    assertThat(validator.validate(mockJobInfo)).isEmpty();

    when(mockJobInfo.combinedSpec(any()))
        .thenReturn(
            XtsTradefedTestDriverSpec.newBuilder()
                .setXtsType("cts")
                .setXtsTestPlan("cts")
                .setAndroidXtsZip("/path/to/android_xts_zip")
                .build());

    assertThat(validator.validate(mockJobInfo)).isEmpty();
  }

  @Test
  public void validateJob_missXtsType_error() throws Exception {
    when(mockJobInfo.combinedSpec(any()))
        .thenReturn(
            XtsTradefedTestDriverSpec.newBuilder()
                .setXtsTestPlan("cts")
                .setXtsRootDir("/path/to/cts_root")
                .build());

    List<String> errors = validator.validate(mockJobInfo);
    assertThat(errors).hasSize(1);
    assertThat(errors)
        .contains("An xTS type must be specified, check xts_tradefed_test_spec.proto.");
  }

  @Test
  public void validateJob_missXtsTestPlan_error() throws Exception {
    when(mockJobInfo.combinedSpec(any()))
        .thenReturn(
            XtsTradefedTestDriverSpec.newBuilder()
                .setXtsType("cts")
                .setXtsRootDir("/path/to/cts_root")
                .build());

    List<String> errors = validator.validate(mockJobInfo);
    assertThat(errors).hasSize(1);
    assertThat(errors).contains("An xTS test plan must be specified.");
  }

  @Test
  public void validateJob_missPrevSessionXtsTestPlanWhenTestPlanIsRetry_error() throws Exception {
    when(mockJobInfo.combinedSpec(any()))
        .thenReturn(
            XtsTradefedTestDriverSpec.newBuilder()
                .setXtsType("cts")
                .setXtsTestPlan("retry")
                .setXtsRootDir("/path/to/cts_root")
                .build());

    List<String> errors = validator.validate(mockJobInfo);
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("'prev_session_xts_test_plan' must be specified");
  }

  @Test
  public void validateJob_missPrevSessionTestResultXmlWhenTestPlanIsRetry_error() throws Exception {
    when(mockJobInfo.combinedSpec(any()))
        .thenReturn(
            XtsTradefedTestDriverSpec.newBuilder()
                .setXtsType("cts")
                .setXtsTestPlan("retry")
                .setXtsRootDir("/path/to/cts_root")
                .setPrevSessionTestRecordFiles("/path/to/prev_session_test_record_files")
                .build());
    when(mockJobFiles.isTagNotEmpty(XtsTradefedTestSpec.TAG_PREV_SESSION_TEST_RECORD_PB_FILES))
        .thenReturn(true);

    List<String> errors = validator.validate(mockJobInfo);
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("'prev_session_test_result_xml' must be specified");
  }

  @Test
  public void validateJob_missPrevSessionTestRecordFilesWhenTestPlanIsRetry_error()
      throws Exception {
    when(mockJobInfo.combinedSpec(any()))
        .thenReturn(
            XtsTradefedTestDriverSpec.newBuilder()
                .setXtsType("cts")
                .setXtsTestPlan("retry")
                .setXtsRootDir("/path/to/cts_root")
                .setPrevSessionTestResultXml("/path/to/prev_session_test_result_xml")
                .build());

    List<String> errors = validator.validate(mockJobInfo);
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0))
        .contains(
            "either 'prev_session_test_record_files' or 'prev_session_test_record_pb_files' must be"
                + " specified");
  }

  @Test
  public void validateJob_missBothXtsRootDirAndAndroidXtsZip_error() throws Exception {
    when(mockJobInfo.combinedSpec(any()))
        .thenReturn(
            XtsTradefedTestDriverSpec.newBuilder().setXtsType("cts").setXtsTestPlan("cts").build());

    List<String> errors = validator.validate(mockJobInfo);
    assertThat(errors).hasSize(1);
    assertThat(errors)
        .contains("At least one of the xts_root_dir, android_xts_zip must be specified.");
  }
}
