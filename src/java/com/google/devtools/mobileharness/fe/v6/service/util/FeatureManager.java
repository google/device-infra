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
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * Manages the enablement of features based on environment and universe.
 *
 * <p>There are three main scenarios:
 *
 * <ul>
 *   <li><b>Scenario 1: Google Internal (1P)</b> - The environment is Google internal and the
 *       universe is {@code "google_1p"}. All features (Device Flashing, Logcat, Quarantine,
 *       Screenshot, and Configuration) are enabled. Note that some features may still be
 *       unimplemented (see {@code FeatureReadiness}).
 *   <li><b>Scenario 2: Google Internal (non-1P)</b> - The environment is Google internal but the
 *       universe is not {@code "google_1p"}. All features are disabled.
 *   <li><b>Scenario 3: OSS/ATS</b> - The environment is Open Source. Only the {@code configuration}
 *       feature is supported (subject to its flag).
 * </ul>
 */
public class FeatureManager {

  private final Environment environment;
  private final UniverseScope universe;

  @AssistedInject
  FeatureManager(Environment environment, @Assisted UniverseScope universe) {
    this.environment = environment;
    this.universe = universe;
  }

  /**
   * Checks if the device flashing feature is enabled.
   *
   * <p>This feature is only available in the {@code google_1p} universe within Google internal
   * builds.
   */
  public boolean isDeviceFlashingFeatureEnabled() {
    return environment.isGoogleInternal() && universe instanceof UniverseScope.SelfUniverse;
  }

  /**
   * Checks if the logcat feature for devices is enabled.
   *
   * <p>This feature is only available in the {@code google_1p} universe within Google internal
   * builds.
   */
  public boolean isDeviceLogcatFeatureEnabled() {
    return environment.isGoogleInternal() && universe instanceof UniverseScope.SelfUniverse;
  }

  /**
   * Checks if the quarantine feature is enabled.
   *
   * <p>This feature is only available in the {@code google_1p} universe within Google internal
   * builds.
   */
  public boolean isDeviceQuarantineFeatureEnabled() {
    return environment.isGoogleInternal() && universe instanceof UniverseScope.SelfUniverse;
  }

  /**
   * Checks if the screenshot feature is enabled.
   *
   * <p>This feature is only available in the {@code google_1p} universe within Google internal
   * builds.
   */
  public boolean isDeviceScreenshotFeatureEnabled() {
    return environment.isGoogleInternal() && universe instanceof UniverseScope.SelfUniverse;
  }

  /**
   * Checks if the configuration feature is enabled.
   *
   * <p>In Google internal builds, configuration is always enabled for {@link
   * UniverseScope.SelfUniverse} and disabled for routed universes. In OSS builds, availability is
   * gated by the {@code fe_enable_configuration} flag regardless of universe.
   */
  public boolean isConfigurationFeatureEnabled() {
    if (environment.isGoogleInternal()) {
      // Internal builds: configuration is always available for the self universe, no flag needed.
      return universe instanceof UniverseScope.SelfUniverse;
    }
    // OSS/ATS: availability depends on the flag.
    return Flags.instance().feEnableConfiguration.getNonNull();
  }
}
