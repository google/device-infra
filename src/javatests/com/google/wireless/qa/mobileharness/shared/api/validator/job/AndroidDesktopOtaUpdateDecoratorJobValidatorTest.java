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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidDesktopOtaUpdateDecoratorSpec;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidDesktopOtaUpdateDecoratorValidator}. */
@RunWith(JUnit4.class)
public final class AndroidDesktopOtaUpdateDecoratorJobValidatorTest {

  @Mock private JobInfo jobInfo;
  @Mock private Files files;
  private AndroidDesktopOtaUpdateDecoratorJobValidator validator;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Before
  public void setUp() throws Exception {
    when(jobInfo.files()).thenReturn(files);
    validator = new AndroidDesktopOtaUpdateDecoratorJobValidator();
  }

  @Test
  public void validate_pass() throws Exception {
    mockSpec(
        getDefaultTestSpecBuilder()
            .setOtaTools("otatools.zip")
            .setOtaPackage("otapackage.zip")
            .build());

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_missingOtaToolsFile() throws Exception {
    mockSpec(getDefaultTestSpecBuilder().setOtaPackage("otapackage.zip").build());

    assertThat(validator.validate(jobInfo)).hasSize(1);
  }

  @Test
  public void validate_missingOtaPackageFile() throws Exception {
    mockSpec(getDefaultTestSpecBuilder().setOtaTools("otatools.zip").build());

    assertThat(validator.validate(jobInfo)).hasSize(1);
  }

  @Test
  public void validate_missingBothFiles() throws Exception {
    mockSpec(getDefaultTestSpecBuilder().build());

    assertThat(validator.validate(jobInfo)).hasSize(2);
  }

  private void mockSpec(AndroidDesktopOtaUpdateDecoratorSpec... specs) throws Exception {
    when(jobInfo.combinedSpecForDevices(eq(validator), any()))
        .thenReturn(ImmutableList.copyOf(specs));
  }

  private static AndroidDesktopOtaUpdateDecoratorSpec.Builder getDefaultTestSpecBuilder() {
    return AndroidDesktopOtaUpdateDecoratorSpec.newBuilder();
  }
}
