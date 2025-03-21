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

/** Env validator for the {@code AndroidNativeBin} driver. */
public class AndroidNativeBinEnvValidator extends AndroidDeviceVersionEnvValidator {
  /** AndroidNativeBin only works with API level 16 or higher. */
  private static final int MIN_DEVICE_SDK_VERSION = 16;

  public AndroidNativeBinEnvValidator() {
    super(MIN_DEVICE_SDK_VERSION);
  }
}
