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

package com.google.wireless.qa.mobileharness.shared.api.metadata;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;

/** Metadata of Mobile Harness drivers and decorators. */
public final class DriverDecoratorMetadata {

  /** Internal map containing driver/decorator names (keys) and spec names (values). */
  private static final ImmutableMap<String, String> DRIVER_DECORATOR_SPEC_MAP;

  /** Internal map containing decorator names (keys) and non-FULL decorator types (values). */
  private static final ImmutableMap<String, DecoratorType> DECORATOR_TYPE_MAP;

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

  /**
   * Retrieves the {@link DecoratorType} associated with the given decorator simple name.
   *
   * @param decoratorName The decorator simple name to query.
   * @return The corresponding {@link DecoratorType}, or {@link DecoratorType#FULL} if not
   *     specifically registered.
   */
  public static DecoratorType getDecoratorType(String decoratorName) {
    return DECORATOR_TYPE_MAP.getOrDefault(decoratorName, DecoratorType.FULL);
  }

  /** Returns whether the decorator is a {@link DecoratorType#SETUP_ONLY} decorator. */
  public static boolean isSetupOnlyDecorator(String decoratorName) {
    return getDecoratorType(decoratorName) == DecoratorType.SETUP_ONLY;
  }

  /** Returns whether the decorator is a {@link DecoratorType#TEARDOWN_ONLY} decorator. */
  public static boolean isTeardownOnlyDecorator(String decoratorName) {
    return getDecoratorType(decoratorName) == DecoratorType.TEARDOWN_ONLY;
  }

  /** Returns whether the decorator is a {@link DecoratorType#PHASE_SKIPPABLE} decorator. */
  public static boolean isPhaseSkippableDecorator(String decoratorName) {
    return getDecoratorType(decoratorName) == DecoratorType.PHASE_SKIPPABLE;
  }

  /**
   * Returns a copy of the internal map containing all decorator name-to-type mappings for
   * non-{@link DecoratorType#FULL} decorators.
   *
   * @return An unmodifiable copy of the decorator type map.
   */
  public static ImmutableMap<String, DecoratorType> getDecoratorTypeMap() {
    return DECORATOR_TYPE_MAP;
  }

  static {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    /*
     * =============================================================================================
     * Initialize the map with driver-spec pairs.
     * =============================================================================================
     */
    // keep-sorted start
    builder.put("AndroidInstrumentation", "AndroidInstrumentationSpec");
    builder.put("AndroidRoboTest", "AndroidRoboTestSpec");
    builder.put("AndroidTestLoopTest", "AndroidTestLoopTestSpec");
    builder.put("NoOpDriver", "NoOpDriverSpec");
    builder.put("SlateDriver", "SlateDriverSpec");
    builder.put("TradefedTest", "TradefedTestDriverSpec");
    // keep-sorted end

    /*
     * =============================================================================================
     * Initialize the map with decorator-spec pairs.
     * =============================================================================================
     */
    // keep-sorted start
    builder.put("AndroidAdbShellDecorator", "AndroidAdbShellDecoratorSpec");
    builder.put("AndroidAflagsDecorator", "AndroidAflagsDecoratorSpec");
    builder.put(
        "AndroidAtsDynamicConfigPusherDecorator", "AndroidAtsDynamicConfigPusherDecoratorSpec");
    builder.put(
        "AndroidAtsDynamicConfigPusherSetupOnlyDecorator",
        "AndroidAtsDynamicConfigPusherDecoratorSpec");
    builder.put(
        "AndroidAtsDynamicConfigPusherTeardownOnlyDecorator",
        "AndroidAtsDynamicConfigPusherDecoratorSpec");
    builder.put(
        "AndroidBusinessLogicSkipModuleDecorator", "AndroidBusinessLogicSkipModuleDecoratorSpec");
    builder.put("AndroidDeviceFeaturesCheckDecorator", "AndroidDeviceFeaturesCheckDecoratorSpec");
    builder.put("AndroidDeviceSettingsDecorator", "AndroidDeviceSettingsDecoratorSpec");
    builder.put("AndroidEmulatorVideoDecorator", "AndroidEmulatorVideoDecoratorSpec");
    builder.put("AndroidFilePullerDecorator", "AndroidFilePullerDecoratorSpec");
    builder.put("AndroidInstallAppsDecorator", "InstallApkStepSpec");
    builder.put(
        "AndroidLabTestSupportSettingsDecorator", "AndroidLabTestSupportSettingsDecoratorSpec");
    builder.put("AndroidLogcatMonitoringDecorator", "AndroidLogcatMonitoringDecoratorSpec");
    builder.put("AndroidMainlineModulesCheckDecorator", "AndroidMainlineModulesCheckDecoratorSpec");
    builder.put("AndroidMinSdkVersionCheckDecorator", "AndroidMinSdkVersionCheckDecoratorSpec");
    builder.put(
        "AndroidNetworkActivityLoggingDecorator", "AndroidNetworkActivityLoggingDecoratorSpec");
    builder.put("AndroidRuntimeStatsDecorator", "AndroidRuntimeStatsDecoratorSpec");
    builder.put("AndroidSetWifiDecorator", "AndroidSetWifiDecoratorSpec");
    builder.put(
        "AndroidShippingApiLevelCheckDecorator", "AndroidShippingApiLevelCheckDecoratorSpec");
    builder.put("AndroidShowInstructionDecorator", "AndroidShowInstructionDecoratorSpec");
    builder.put("AndroidSwitchLanguageDecorator", "AndroidSwitchLanguageDecoratorSpec");
    builder.put("AndroidSwitchUserDecorator", "AndroidSwitchUserDecoratorSpec");
    builder.put("ApkPreconditionCheckDecorator", "ApkPreconditionCheckDecoratorSpec");
    builder.put("DeviceInfoCollectorDecorator", "DeviceInfoCollectorDecoratorSpec");
    builder.put("DeviceInfoCollectorSetupOnlyDecorator", "DeviceInfoCollectorDecoratorSpec");
    builder.put("DeviceInfoCollectorTeardownOnlyDecorator", "DeviceInfoCollectorDecoratorSpec");
    builder.put("MoblyDecoratorAdapter", "MoblyDecoratorAdapterSpec");
    builder.put("NoOpDecorator", "NoOpDecoratorSpec");
    builder.put("PythonVersionCheckDecorator", "PythonVersionCheckDecoratorSpec");
    builder.put("ReportLogCollectorDecorator", "ReportLogCollectorDecoratorSpec");
    // keep-sorted end

    DRIVER_DECORATOR_SPEC_MAP = builder.buildOrThrow();

    ImmutableMap.Builder<String, DecoratorType> typeBuilder = ImmutableMap.builder();
    /*
     * =============================================================================================
     * Initialize the map with decorator-type pairs.
     * =============================================================================================
     */
    // keep-sorted start
    typeBuilder.put("AndroidAtsDynamicConfigPusherDecorator", DecoratorType.PHASE_SKIPPABLE);
    typeBuilder.put("AndroidAtsDynamicConfigPusherSetupOnlyDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put(
        "AndroidAtsDynamicConfigPusherTeardownOnlyDecorator", DecoratorType.TEARDOWN_ONLY);
    typeBuilder.put("AndroidBusinessLogicSkipModuleDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("AndroidCleanAppsDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("AndroidDeviceFeaturesCheckDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("AndroidDumpSysDecorator", DecoratorType.TEARDOWN_ONLY);
    typeBuilder.put("AndroidFilePusherDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("AndroidInstallMainlineModulesDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("AndroidLabTestSupportSettingsDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("AndroidMainlineModulesCheckDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("AndroidMinSdkVersionCheckDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("AndroidOrientationDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("AndroidRuntimeStatsDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("AndroidScreenshotDecorator", DecoratorType.TEARDOWN_ONLY);
    typeBuilder.put("AndroidSetWifiDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("AndroidShippingApiLevelCheckDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("AndroidShowInstructionDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("AndroidStartAppsDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("ApkPreconditionCheckDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("DeviceInfoCollectorDecorator", DecoratorType.PHASE_SKIPPABLE);
    typeBuilder.put("DeviceInfoCollectorSetupOnlyDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("DeviceInfoCollectorTeardownOnlyDecorator", DecoratorType.TEARDOWN_ONLY);
    typeBuilder.put("NoOpDecorator", DecoratorType.TEARDOWN_ONLY);
    typeBuilder.put("PythonVersionCheckDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("ReportIntegrityCollectorDecorator", DecoratorType.SETUP_ONLY);
    typeBuilder.put("ReportLogCollectorDecorator", DecoratorType.TEARDOWN_ONLY);
    // keep-sorted end

    DECORATOR_TYPE_MAP = typeBuilder.buildOrThrow();
  }

  private DriverDecoratorMetadata() {}
}
