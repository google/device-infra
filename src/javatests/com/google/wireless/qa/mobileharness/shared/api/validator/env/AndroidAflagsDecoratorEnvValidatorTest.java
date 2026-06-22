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

package com.google.wireless.qa.mobileharness.shared.api.validator.env;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidAflagsDecoratorEnvValidator}. */
@RunWith(JUnit4.class)
public final class AndroidAflagsDecoratorEnvValidatorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private AndroidDevice androidDevice;
  @Mock private Device nonAndroidDevice;

  private AndroidAflagsDecoratorEnvValidator validator;

  @Before
  public void setUp() {
    validator = new AndroidAflagsDecoratorEnvValidator();
  }

  @Test
  public void validate_nonAndroidDevice_skips() throws Exception {
    validator.validate(nonAndroidDevice);
    verify(androidDevice, never()).getSdkVersion();
  }

  @Test
  public void validate_sdkVersionNull_skips() throws Exception {
    when(androidDevice.getSdkVersion()).thenReturn(null);

    validator.validate(androidDevice);
  }

  @Test
  public void validate_sdkVersionTooLow_throwsException() throws Exception {
    when(androidDevice.getSdkVersion()).thenReturn(34);

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> validator.validate(androidDevice));
    assertThat(e.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_DEVICE_VERSION_ENV_VALIDATOR_VERSION_TOO_LOW);
  }

  @Test
  public void validate_sdkVersionValid_success() throws Exception {
    when(androidDevice.getSdkVersion()).thenReturn(35);
    when(androidDevice.getDimension("build_type")).thenReturn(ImmutableList.of("userdebug"));

    validator.validate(androidDevice);
  }

  @Test
  public void validate_userBuild_throwsException() throws Exception {
    when(androidDevice.getSdkVersion()).thenReturn(35);
    when(androidDevice.getDimension("build_type")).thenReturn(ImmutableList.of("user"));

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> validator.validate(androidDevice));
    assertThat(e.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_AFLAGS_DECORATOR_USER_BUILD_NOT_SUPPORTED);
  }
}
