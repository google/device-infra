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
import com.google.devtools.mobileharness.platform.android.connectivity.UsbReverseTetheringUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidRealDevice;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidReverseUsbTetheringDecoratorEnvValidator}. */
@RunWith(JUnit4.class)
public class AndroidReverseUsbTetheringDecoratorEnvValidatorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private UsbReverseTetheringUtil usbReverseTetheringUtil;
  @Mock private AndroidRealDevice device;

  private AndroidReverseUsbTetheringDecoratorEnvValidator validator;

  @Before
  public void setUp() {
    validator = new AndroidReverseUsbTetheringDecoratorEnvValidator(usbReverseTetheringUtil);
  }

  @Test
  public void validate_usbDeviceAndSupportedHost_passes() throws Exception {
    when(device.getDeviceId()).thenReturn("44121FDKD00042");
    when(usbReverseTetheringUtil.isHostSupported()).thenReturn(true);

    validator.validate(device);
  }

  @Test
  public void validate_overTcpDevice_throws() {
    when(device.getDeviceId()).thenReturn("127.0.0.1:5555");

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> validator.validate(device));

    assertThat(thrown.getErrorId())
        .isEqualTo(
            AndroidErrorId
                .ANDROID_REVERSE_USB_TETHERING_DECORATOR_ENV_VALIDATOR_TCP_DEVICE_NOT_SUPPORTED);
  }

  @Test
  public void validate_unsupportedHost_throws() {
    when(device.getDeviceId()).thenReturn("44121FDKD00042");
    when(usbReverseTetheringUtil.isHostSupported()).thenReturn(false);

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> validator.validate(device));

    assertThat(thrown.getErrorId())
        .isEqualTo(
            AndroidErrorId
                .ANDROID_REVERSE_USB_TETHERING_DECORATOR_ENV_VALIDATOR_HOST_NOT_SUPPORTED);
  }
}
