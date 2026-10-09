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
import static com.google.wireless.qa.mobileharness.shared.api.spec.AndroidFlashstationDecoratorSpec.TAG_TOOL_FILE;
import static com.google.wireless.qa.mobileharness.shared.api.spec.AndroidFlashstationDecoratorSpec.TAG_TOOL_OAUTH_FILE;
import static com.google.wireless.qa.mobileharness.shared.api.spec.AndroidFlashstationDecoratorSpec.TAG_TOOL_PRIVATE_KEY_FILE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.step.android.DeviceInitializationStep;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidFlashstationDecoratorSpec;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidFlashstationDecoratorJobValidator}. */
@RunWith(JUnit4.class)
public final class AndroidFlashstationDecoratorJobValidatorTest {

  private static final String TEST_BUILD_ID = "OPM1.170928.003";
  private static final String TEST_BRANCH = "master";
  private static final String TEST_BUILD_TARGET = "sailfish-userdebug";
  private static final String TEST_AUTOOTA = "droidfood.weekly";
  private static final String TEST_AUTOOTA_BUILD_PREFIX = "ASA1";

  private static final String TEST_TOOL_PATH = "path/to/tool";
  private static final String TEST_TOOL_OAUTH_PATH = "path/to/oauth";
  private static final String TEST_TOOL_PRIVATE_KEY_PATH = "path/to/private/key/file";

  private static final String TEST_DEVICE_READY_TIMEOUT_SEC = "1800";

  private AndroidFlashstationDecoratorJobValidator validator;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private JobInfo mockJobInfo;
  @Mock private Params mockParams;
  @Mock private Files mockFiles;

  @Before
  public void setUp() throws Exception {
    when(mockJobInfo.params()).thenReturn(mockParams);

    when(mockJobInfo.files()).thenReturn(mockFiles);
    when(mockFiles.getSingle(TAG_TOOL_FILE)).thenReturn(TEST_TOOL_PATH);
    when(mockFiles.get(TAG_TOOL_OAUTH_FILE)).thenReturn(ImmutableSet.of(TEST_TOOL_OAUTH_PATH));
    when(mockFiles.get(TAG_TOOL_PRIVATE_KEY_FILE))
        .thenReturn(ImmutableSet.of(TEST_TOOL_PRIVATE_KEY_PATH));

    when(mockParams.get(DeviceInitializationStep.PARAM_DEVICE_READY_TIMEOUT_SEC))
        .thenReturn(TEST_DEVICE_READY_TIMEOUT_SEC);

    validator = new AndroidFlashstationDecoratorJobValidator();
  }

  @Test
  public void testNoErrorsWhenEverythingIsSpecified() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId(TEST_BUILD_ID)
            .setBuildTarget(TEST_BUILD_TARGET)
            .build());

    assertThat(validator.validate(mockJobInfo)).isEmpty();
  }

  @Test
  public void testMissingBuildIdOnly() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBranch(TEST_BRANCH)
            .setBuildTarget(TEST_BUILD_TARGET)
            .build());

    List<String> errors = validator.validate(mockJobInfo);
    assertThat(errors).isEmpty();
  }

  @Test
  public void testMissingBranchOnly() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId(TEST_BUILD_ID)
            .setBuildTarget(TEST_BUILD_TARGET)
            .build());

    List<String> errors = validator.validate(mockJobInfo);
    assertThat(errors).isEmpty();
  }

  @Test
  public void testMissingBuildIdAndBranchAndAutoota() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId("")
            .setBuildTarget(TEST_BUILD_TARGET)
            .build());

    List<String> errors = validator.validate(mockJobInfo);
    assertThat(errors).hasSize(1);
  }

  @Test
  public void testMissingBuildIdAndBranchAndAutootaWithDisableFlashstationFlash() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId("")
            .setBuildTarget(TEST_BUILD_TARGET)
            .setDisableFlashstationFlash(true)
            .build());

    List<String> errors = validator.validate(mockJobInfo);
    assertThat(errors).isEmpty();
  }

  @Test
  public void testMultiSepcWithOneDisableFlashstationFlash() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId("")
            .setBuildTarget(TEST_BUILD_TARGET)
            .setDisableFlashstationFlash(true)
            .build(),
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId("")
            .setBuildTarget(TEST_BUILD_TARGET)
            .setDisableFlashstationFlash(false)
            .build());

    List<String> errors = validator.validate(mockJobInfo);
    assertThat(errors).hasSize(1);
  }

  @Test
  public void setAutootaWithAutootaBuildPrefix_noErrors() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setAutootaGroup(TEST_AUTOOTA)
            .setAutootaBuildPrefix(TEST_AUTOOTA_BUILD_PREFIX)
            .setBuildTarget(TEST_BUILD_TARGET)
            .build());

    List<String> errors = validator.validate(mockJobInfo);
    assertThat(errors).isEmpty();
  }

  @Test
  public void testMissingToolFile() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId(TEST_BUILD_ID)
            .setBranch(TEST_BRANCH)
            .setBuildTarget(TEST_BUILD_TARGET)
            .build());
    when(mockFiles.getSingle(TAG_TOOL_FILE))
        .thenThrow(new MobileHarnessException(BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND, ""));

    List<String> errors = validator.validate(mockJobInfo);
    assertThat(errors).hasSize(1);
  }

  @Test
  public void videoDecorators_executeBeforeAndroidFlashstationDecorator_error() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId(TEST_BUILD_ID)
            .setBranch(TEST_BRANCH)
            .setBuildTarget(TEST_BUILD_TARGET)
            .build());
    when(mockJobInfo.type())
        .thenReturn(
            JobType.newBuilder()
                .addDecorator("AndroidFlashstationDecorator")
                .addDecorator("AndroidSdVideoDecorator")
                .addDecorator("AndroidHdVideoDecorator")
                .build());

    assertThat(validator.validate(mockJobInfo)).hasSize(2);
  }

  @Test
  public void videoDecorators_executeAfterAndroidFlashstationDecorator_pass() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId(TEST_BUILD_ID)
            .setBranch(TEST_BRANCH)
            .setBuildTarget(TEST_BUILD_TARGET)
            .build());
    when(mockJobInfo.type())
        .thenReturn(
            JobType.newBuilder()
                .addDecorator("AndroidSdVideoDecorator")
                .addDecorator("AndroidHdVideoDecorator")
                .addDecorator("AndroidFlashstationDecorator")
                .build());

    assertThat(validator.validate(mockJobInfo)).isEmpty();
  }

  @Test
  public void testNoErrorsWhenEverythingIsSpecified_withSkipReboot() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId(TEST_BUILD_ID)
            .setBuildTarget(TEST_BUILD_TARGET)
            .setSkipReboot(true)
            .build());
    when(mockJobInfo.type())
        .thenReturn(JobType.newBuilder().addDecorator("AndroidFlashstationDecorator").build());

    assertThat(validator.validate(mockJobInfo)).isEmpty();
  }

  @Test
  public void skipReboot_withOtherDecorators_error() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId(TEST_BUILD_ID)
            .setBranch(TEST_BRANCH)
            .setBuildTarget(TEST_BUILD_TARGET)
            .setSkipReboot(true)
            .build());
    when(mockJobInfo.type())
        .thenReturn(
            JobType.newBuilder()
                .addDecorator("AndroidSdVideoDecorator")
                .addDecorator("AndroidHdVideoDecorator")
                .addDecorator("AndroidFlashstationDecorator")
                .build());
    assertThat(validator.validate(mockJobInfo)).hasSize(1);
    assertThat(validator.validate(mockJobInfo).get(0))
        .contains(
            "The param skip_reboot is only supported when it's the only decorator in the list.");
  }

  @Test
  public void validate_deviceReadyTimeoutInvalid() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId(TEST_BUILD_ID)
            .setBranch(TEST_BRANCH)
            .setBuildTarget(TEST_BUILD_TARGET)
            .build());
    when(mockParams.get(DeviceInitializationStep.PARAM_DEVICE_READY_TIMEOUT_SEC))
        .thenReturn("invalidNumber99");

    assertThat(validator.validate(mockJobInfo)).hasSize(1);
  }

  @Test
  public void validate_deviceReadyTimeoutOutOfRange() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId(TEST_BUILD_ID)
            .setBranch(TEST_BRANCH)
            .setBuildTarget(TEST_BUILD_TARGET)
            .build());
    when(mockParams.get(DeviceInitializationStep.PARAM_DEVICE_READY_TIMEOUT_SEC))
        .thenReturn("59")
        .thenReturn("1801");

    assertThat(validator.validate(mockJobInfo)).hasSize(1);
    assertThat(validator.validate(mockJobInfo)).hasSize(1);
  }

  @Test
  public void validate_invalidMinFlashTimeSecValue() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId(TEST_BUILD_ID)
            .setBuildTarget(TEST_BUILD_TARGET)
            .setMinFlashTimeSec(0)
            .build());

    assertThat(validator.validate(mockJobInfo)).hasSize(1);
  }

  @Test
  @SuppressWarnings("deprecation") // Tests deprecated use_mh_flashstation_key_if_exists field.
  public void validate_useMhFlashstationKeyIfExists_returnsError() throws Exception {
    mockSpec(
        AndroidFlashstationDecoratorSpec.newBuilder()
            .setBuildId(TEST_BUILD_ID)
            .setBuildTarget(TEST_BUILD_TARGET)
            .setUseMhFlashstationKeyIfExists(true)
            .build());

    List<String> errors = validator.validate(mockJobInfo);

    assertThat(errors).isNotEmpty();
    assertThat(errors.get(0)).contains("use_mh_flashstation_key_if_exists=true");
  }

  private void mockSpec(AndroidFlashstationDecoratorSpec... specs) throws Exception {
    when(mockJobInfo.combinedSpecForDevices(eq(validator), any()))
        .thenReturn(ImmutableList.copyOf(specs));
  }
}
