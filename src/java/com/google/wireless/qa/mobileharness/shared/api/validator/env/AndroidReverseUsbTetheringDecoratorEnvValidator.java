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

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.connectivity.UsbReverseTetheringUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;

/** Env validator for {@code AndroidReverseUsbTetheringDecorator}. */
public class AndroidReverseUsbTetheringDecoratorEnvValidator implements EnvValidator {

  private final UsbReverseTetheringUtil usbReverseTetheringUtil;

  public AndroidReverseUsbTetheringDecoratorEnvValidator() {
    this(new UsbReverseTetheringUtil());
  }

  @VisibleForTesting
  AndroidReverseUsbTetheringDecoratorEnvValidator(UsbReverseTetheringUtil usbReverseTetheringUtil) {
    this.usbReverseTetheringUtil = usbReverseTetheringUtil;
  }

  @Override
  public void validate(Device device) throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    if (DeviceUtil.isOverTcpDevice(deviceId)) {
      throw new MobileHarnessException(
          AndroidErrorId
              .ANDROID_REVERSE_USB_TETHERING_DECORATOR_ENV_VALIDATOR_TCP_DEVICE_NOT_SUPPORTED,
          String.format(
              "Device %s is connected over TCP; AndroidReverseUsbTetheringDecorator requires a"
                  + " physical USB connection.",
              deviceId));
    }
    if (!usbReverseTetheringUtil.isHostSupported()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_DECORATOR_ENV_VALIDATOR_HOST_NOT_SUPPORTED,
          String.format("Host does not support USB reverse tethering for device %s.", deviceId));
    }
  }
}
