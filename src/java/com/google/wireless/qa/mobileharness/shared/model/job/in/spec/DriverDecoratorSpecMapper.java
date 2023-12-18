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

package com.google.wireless.qa.mobileharness.shared.model.job.in.spec;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;

/**
 * This class maps driver or decorator names to their corresponding spec names. It provides methods
 * to query the spec name for a given driver or decorator name, and to access the entire map.
 */
public final class DriverDecoratorSpecMapper {

  /** Internal map containing driver/decorator names (keys) and spec names (values). */
  private static final ImmutableMap<String, String> DRIVER_DECORATOR_SPEC_MAP;

  /**
   * Retrieves the spec name associated with the given driver or decorator name. Returns null if no
   * mapping exists.
   *
   * @param driverOrDecoratorName The driver or decorator name to query.
   * @return The corresponding spec name.
   */
  public static Optional<String> getSpecNameByDriverOrDecorator(String driverOrDecoratorName) {
    return Optional.ofNullable(DRIVER_DECORATOR_SPEC_MAP.get(driverOrDecoratorName));
  }

  /**
   * Returns a copy of the internal map containing all driver and decorator name-to-spec name
   * mappings.
   *
   * @return An unmodifiable copy of the driver-decorator spec name map.
   */
  public static ImmutableMap<String, String> getDriverDecoratorSpecMap() {
    return DRIVER_DECORATOR_SPEC_MAP;
  }

  static {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    /*
     * =============================================================================================
     * Initialize the map with driver-spec pairs.
     * =============================================================================================
     */
    // go/keep-sorted start google3-only(internal)
    builder.put("NoOpDriver", "NoOpDriverSpec");
    builder.put("XtsTradefedTest", "XtsTradefedTestDriverSpec");
    // go/keep-sorted end google3-only(internal)

    /*
     * =============================================================================================
     * Initialize the map with decorator-spec pairs.
     * =============================================================================================
     */
    // go/keep-sorted start google3-only(internal)
    builder.put("AndroidAdbShellDecorator", "AndroidAdbShellDecoratorSpec");
    builder.put("AndroidDeviceFeaturesCheckDecorator", "AndroidDeviceFeaturesCheckDecoratorSpec");
    builder.put("AndroidDeviceSettingsDecorator", "AndroidDeviceSettingsDecoratorSpec");
    builder.put("AndroidInstallAppsDecorator", "InstallApkStepSpec");
    builder.put("AndroidMainlineModulesCheckDecorator", "AndroidMainlineModulesCheckDecoratorSpec");
    builder.put("AndroidSwitchUserDecorator", "AndroidSwitchUserDecoratorSpec");
    builder.put("NoOpDecorator", "NoOpDecoratorSpec");
    // go/keep-sorted end google3-only(internal)

    DRIVER_DECORATOR_SPEC_MAP = builder.buildOrThrow();
  }

  private DriverDecoratorSpecMapper() {}
}
