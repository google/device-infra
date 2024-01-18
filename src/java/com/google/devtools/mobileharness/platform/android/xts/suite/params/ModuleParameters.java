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

package com.google.devtools.mobileharness.platform.android.xts.suite.params;

/** Special values associated with the suite "parameter" keys in the metadata of each module. */
public enum ModuleParameters {
  /** Describes a parameterization based on app that should be installed in instant mode. */
  INSTANT_APP("instant_app", ModuleParameters.INSTANT_APP_FAMILY),
  NOT_INSTANT_APP("not_instant_app", ModuleParameters.INSTANT_APP_FAMILY),

  MULTI_ABI("multi_abi", ModuleParameters.MULTI_ABI_FAMILY),
  NOT_MULTI_ABI("not_multi_abi", ModuleParameters.MULTI_ABI_FAMILY),

  SECONDARY_USER("secondary_user", ModuleParameters.SECONDARY_USER_FAMILY),
  NOT_SECONDARY_USER("not_secondary_user", ModuleParameters.SECONDARY_USER_FAMILY),

  // Secondary user started on background, visible in a secondary display
  SECONDARY_USER_ON_SECONDARY_DISPLAY(
      "secondary_user_on_secondary_display",
      ModuleParameters.SECONDARY_USER_ON_SECONDARY_DISPLAY_FAMILY),
  NOT_SECONDARY_USER_ON_SECONDARY_DISPLAY(
      "not_secondary_user_on_secondary_display",
      ModuleParameters.SECONDARY_USER_ON_SECONDARY_DISPLAY_FAMILY),

  // Secondary user started on background, visible in the default display
  SECONDARY_USER_ON_DEFAULT_DISPLAY(
      "secondary_user_on_defauilt_display",
      ModuleParameters.SECONDARY_USER_ON_DEFAULT_DISPLAY_FAMILY),
  NOT_SECONDARY_USER_ON_DEFAULT_DISPLAY(
      "not_secondary_user_on_default_display",
      ModuleParameters.SECONDARY_USER_ON_DEFAULT_DISPLAY_FAMILY),

  // Multi-user
  MULTIUSER("multiuser", ModuleParameters.MULTIUSER_FAMILY),
  RUN_ON_WORK_PROFILE("run_on_work_profile", ModuleParameters.RUN_ON_WORK_PROFILE_FAMILY),
  RUN_ON_SECONDARY_USER("run_on_secondary_user", ModuleParameters.RUN_ON_SECONDARY_USER_FAMILY),
  RUN_ON_CLONE_PROFILE("run_on_clone_profile", ModuleParameters.RUN_ON_CLONE_PROFILE_FAMILY),

  // Foldable mode
  ALL_FOLDABLE_STATES("all_foldable_states", ModuleParameters.FOLDABLE_STATES_FAMILY),
  NO_FOLDABLE_STATES("no_foldable_states", ModuleParameters.FOLDABLE_STATES_FAMILY),

  // SDK sandbox mode
  RUN_ON_SDK_SANDBOX("run_on_sdk_sandbox", ModuleParameters.RUN_ON_SDK_SANDBOX_FAMILY),
  NOT_RUN_ON_SDK_SANDBOX("not_run_on_sdk_sandbox", ModuleParameters.RUN_ON_SDK_SANDBOX_FAMILY);

  public static final String INSTANT_APP_FAMILY = "instant_app_family";
  public static final String MULTI_ABI_FAMILY = "multi_abi_family";
  public static final String SECONDARY_USER_FAMILY = "secondary_user_family";
  public static final String SECONDARY_USER_ON_SECONDARY_DISPLAY_FAMILY =
      "secondary_user_on_secondary_display_family";
  public static final String SECONDARY_USER_ON_DEFAULT_DISPLAY_FAMILY =
      "secondary_user_on_default_display_family";
  public static final String MULTIUSER_FAMILY = "multiuser_family";
  public static final String FOLDABLE_STATES_FAMILY = "foldable_family";
  public static final String RUN_ON_SDK_SANDBOX_FAMILY = "run_on_sdk_sandbox_family";
  public static final String RUN_ON_WORK_PROFILE_FAMILY = "run_on_work_profile_family";
  public static final String RUN_ON_SECONDARY_USER_FAMILY = "run_on_secondary_user_family";
  public static final String RUN_ON_CLONE_PROFILE_FAMILY = "run_on_clone_profile_family";

  private final String name;

  /** Defines whether several module parameters are associated and mutually exclusive. */
  private final String family;

  private ModuleParameters(String name, String family) {
    this.name = name;
    this.family = family;
  }

  @Override
  public String toString() {
    return name;
  }

  /** Gets the family of the Module Parameter. */
  public String getFamily() {
    return family;
  }
}
