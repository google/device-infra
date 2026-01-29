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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;

/** Driver/decorator env validator for validating device sdk version. */
public abstract class AndroidDeviceVersionEnvValidator implements EnvValidator {
  public final int minDeviceSdkVersion;

  protected AndroidDeviceVersionEnvValidator(int minDeviceSdkVersion) {
    this.minDeviceSdkVersion = minDeviceSdkVersion;
  }

  @Override
  public void validate(Device device) throws MobileHarnessException, InterruptedException {
    checkNotNull(device);
    // Oxygen device is only a place hold, so it cannot run the adb command.
    if (device.getClass().getSimpleName().equals("OxygenDevice")) {
      return;
    }
    if (device instanceof AndroidDevice) {
      Integer sdkVersion = ((AndroidDevice) device).getSdkVersion();
      if (sdkVersion != null && sdkVersion < minDeviceSdkVersion) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_DEVICE_VERSION_ENV_VALIDATOR_VERSION_TOO_LOW,
            String.format(
                "Device %s has SDK %s, which is lower than the required minimum %s",
                device.getDeviceId(), sdkVersion, minDeviceSdkVersion));
      }
    }
  }
}
