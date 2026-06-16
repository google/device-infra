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

import com.google.common.base.Ascii;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.List;

/**
 * Env validator for {@link
 * com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidAflagsDecorator}.
 */
public class AndroidAflagsDecoratorEnvValidator extends AndroidDeviceVersionEnvValidator {
  public AndroidAflagsDecoratorEnvValidator() {
    super(35);
  }

  @Override
  public void validate(Device device) throws MobileHarnessException, InterruptedException {
    super.validate(device);

    List<String> buildTypes = device.getDimension("build_type");
    if (buildTypes.stream().anyMatch(t -> Ascii.equalsIgnoreCase(t, "user"))) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_AFLAGS_DECORATOR_USER_BUILD_NOT_SUPPORTED,
          String.format(
              "AndroidAflagsDecorator does not support user build devices (found build_type: %s on"
                  + " device %s)",
              buildTypes, device.getDeviceId()));
    }
  }
}
