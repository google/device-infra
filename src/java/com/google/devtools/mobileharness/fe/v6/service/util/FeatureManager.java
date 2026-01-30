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

import com.google.devtools.mobileharness.shared.util.flags.Flags;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Manages the enablement of features. */
@Singleton
public class FeatureManager {

  private final Environment environment;

  @Inject
  FeatureManager(Environment environment) {
    this.environment = environment;
  }

  /** Checks if the device flashing feature is enabled. */
  public boolean isDeviceFlashingEnabled() {
    // Controlled by the startup flag AND only available in Google internal builds.
    return Flags.instance().feEnableDeviceFlashing.getNonNull() && environment.isGoogleInternal();
  }
}
