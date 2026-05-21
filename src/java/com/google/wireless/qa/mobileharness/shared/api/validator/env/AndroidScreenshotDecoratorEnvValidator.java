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
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;

/** Env validator for the {@code AndroidScreenshotDecorator}. */
public class AndroidScreenshotDecoratorEnvValidator extends AndroidDeviceVersionEnvValidator {

  /** Android supports screencap command since API level 10. */
  @VisibleForTesting static final int VERSION_REQUIREMENT = 10;

  public AndroidScreenshotDecoratorEnvValidator() {
    super(VERSION_REQUIREMENT);
  }

  @Override
  public void validate(Device device) throws MobileHarnessException, InterruptedException {
    // Validates Android SDK version >= 10.
    super.validate(device);

    // Validates device is AndroidDevice.
    if (!(device instanceof AndroidDevice)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SCREENSHOT_DECORATOR_ENV_VALIDATOR_DEVICE_NOT_SUPPORTED,
          "AndroidScreenshotDecorator only can work with Android device.");
    }
  }
}
