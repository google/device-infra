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

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import javax.annotation.Nullable;

/** Env validator for {@link AndroidSwitchUserDecorator} */
public class AndroidSwitchUserDecoratorEnvValidator implements EnvValidator {
  private static final int SDK_VERSION_MIN = 28;

  @Override
  public void validate(Device device) throws MobileHarnessException, InterruptedException {
    @Nullable Integer sdkVersion = ((AndroidDevice) device).getSdkVersion();

    String error = null;
    if (sdkVersion == null) {
      error = "'sdk_version' dimension is not available";
    } else if (sdkVersion.intValue() < SDK_VERSION_MIN) {
      error =
          String.format(
              "AndroidSwitchUserDecorator only supports devices with sdkVersion >= %d, found %s",
              SDK_VERSION_MIN, sdkVersion);
    }

    if (error != null) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SWITCH_USER_DECORATOR_NOT_SUPPORTED, error);
    }
  }
}
