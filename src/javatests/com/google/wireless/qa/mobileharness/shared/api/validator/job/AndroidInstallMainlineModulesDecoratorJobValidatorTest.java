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

import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidInstallMainlineModulesDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link AndroidInstallMainlineModulesDecoratorJobValidator}. */
@RunWith(JUnit4.class)
public final class AndroidInstallMainlineModulesDecoratorJobValidatorTest {

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  private JobInfo jobInfo;

  private final AndroidInstallMainlineModulesDecoratorJobValidator validator =
      new AndroidInstallMainlineModulesDecoratorJobValidator();

  @Before
  public void setUp() {
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(JobType.newBuilder().setDevice("device").setDriver("driver").build())
            .build();
  }

  @Test
  public void validateJob_bundletoolNotUnique() throws Exception {
    File bundletool1 = tmpFolder.newFile("bundletool1");
    File bundletool2 = tmpFolder.newFile("bundletool2");
    File apks1 = tmpFolder.newFile("apks1");

    jobInfo
        .files()
        .add(
            AndroidInstallMainlineModulesDecoratorSpec.TAG_BUNDLETOOL_FILE,
            bundletool1.getAbsolutePath());
    jobInfo
        .files()
        .add(
            AndroidInstallMainlineModulesDecoratorSpec.TAG_BUNDLETOOL_FILE,
            bundletool2.getAbsolutePath());
    jobInfo
        .files()
        .add(AndroidInstallMainlineModulesDecoratorSpec.TAG_MODULE_FILES, apks1.getAbsolutePath());

    assertThat(validator.validate(jobInfo)).hasSize(1);
  }

  @Test
  public void validateJob_hasNoModules() throws Exception {
    File bundletool = tmpFolder.newFile("bundletool");
    jobInfo
        .files()
        .add(
            AndroidInstallMainlineModulesDecoratorSpec.TAG_BUNDLETOOL_FILE,
            bundletool.getAbsolutePath());

    assertThat(validator.validate(jobInfo)).hasSize(1);
  }

  @Test
  public void validateJob_valid() throws Exception {
    File bundletool = tmpFolder.newFile("bundletool");
    File apks1 = tmpFolder.newFile("apks1");
    File apks2 = tmpFolder.newFile("apks2");

    jobInfo
        .files()
        .add(
            AndroidInstallMainlineModulesDecoratorSpec.TAG_BUNDLETOOL_FILE,
            bundletool.getAbsolutePath());
    jobInfo
        .files()
        .add(AndroidInstallMainlineModulesDecoratorSpec.TAG_MODULE_FILES, apks1.getAbsolutePath());
    jobInfo
        .files()
        .add(AndroidInstallMainlineModulesDecoratorSpec.TAG_MODULE_FILES, apks2.getAbsolutePath());

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validateJob_validIfNoBundletool() throws Exception {
    File apks1 = tmpFolder.newFile("apks1");
    File apks2 = tmpFolder.newFile("apks2");

    jobInfo
        .files()
        .add(AndroidInstallMainlineModulesDecoratorSpec.TAG_MODULE_FILES, apks1.getAbsolutePath());
    jobInfo
        .files()
        .add(AndroidInstallMainlineModulesDecoratorSpec.TAG_MODULE_FILES, apks2.getAbsolutePath());

    assertThat(validator.validate(jobInfo)).isEmpty();
  }
}
