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
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidRealDevice;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class AndroidScreenshotDecoratorEnvValidatorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private AndroidRealDevice realDevice;
  private AndroidScreenshotDecoratorEnvValidator validator;

  @Before
  public void setUp() throws Exception {
    validator = new AndroidScreenshotDecoratorEnvValidator();
  }

  @Test
  public void validateEnv_pass() throws Exception {
    when(realDevice.getSdkVersion())
        .thenReturn(AndroidScreenshotDecoratorEnvValidator.VERSION_REQUIREMENT);
    validator.validate(realDevice);
  }

  @Test
  public void deviceTooOld() throws Exception {
    when(realDevice.getSdkVersion())
        .thenReturn(AndroidScreenshotDecoratorEnvValidator.VERSION_REQUIREMENT - 1);
    when(realDevice.isRooted()).thenReturn(true);
    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> validator.validate(realDevice));
    assertThat(e.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_DEVICE_VERSION_ENV_VALIDATOR_VERSION_TOO_LOW);
  }
}
