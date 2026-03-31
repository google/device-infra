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

package com.google.devtools.mobileharness.fe.v6.service.util;

import javax.inject.Singleton;

// TODO: Add test coverage for this file.
/** Concrete class for checking if a feature is ready for use. Unready features return false. */
@Singleton
public class FeatureReadiness {
  public boolean isDeviceFlashingReady() {
    return false;
  }

  public boolean isDeviceLogcatReady() {
    return false;
  }

  public boolean isDeviceQuarantineReady() {
    return false;
  }

  public boolean isDeviceScreenshotReady() {
    return false;
  }
}
