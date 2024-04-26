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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.spec.MoblyAospPackageTestSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class MoblyAospPackageTestJobValidatorTest {

  private MoblyAospPackageTestJobValidator validator;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private JobInfo mockJobInfo;
  @Mock private Files jobFiles;
  @Mock private Params jobParams;

  @Before
  public void setUp() {
    validator = new MoblyAospPackageTestJobValidator();
    when(mockJobInfo.files()).thenReturn(jobFiles);
    when(mockJobInfo.params()).thenReturn(jobParams);
  }

  @Test
  public void validateJob_pass() throws Exception {
    doNothing().when(jobFiles).checkUnique(MoblyAospPackageTestSpec.FILE_MOBLY_PKG);

    assertThat(validator.validate(mockJobInfo)).isEmpty();
  }

  @Test
  public void validateJob_multipleMoblyPackages_error() throws Exception {
    doThrow(
            new MobileHarnessException(
                BasicErrorId.JOB_OR_TEST_FILE_MULTI_PATHS,
                "More than one files/dirs marked for each tag(s)"))
        .when(jobFiles)
        .checkUnique(MoblyAospPackageTestSpec.FILE_MOBLY_PKG);

    List<String> errors = validator.validate(mockJobInfo);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("More than one files/dirs marked for each tag(s)");
  }
}
