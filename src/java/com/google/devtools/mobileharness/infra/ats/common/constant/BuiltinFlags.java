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

package com.google.devtools.mobileharness.infra.ats.common.constant;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/** Built-in flags for ATS components. */
public final class BuiltinFlags {

  /**
   * The property key of ATS lab server types.
   *
   * <p>Valid property values: {@code on-prem, omni-dda, omni-public-testing, omni-internal-testing,
   * omni-xts-testing, omni-private}.
   */
  public static final String ATS_LAB_SERVER_TYPE_PROPERTY_KEY =
      "com.google.mobileharness.ats.lab_server_type";

  /**
   * Built-in flags of the components [OLC server(local mode)/ATS console/ATS local runner] for xTS
   * console/ATS local runner.
   *
   * <p>xTS console/ATS local runner can also override these flags.
   */
  private static final ImmutableMap<String, String> ATS_CONSOLE_FLAG_MAP =
      ImmutableMap.<String, String>builder()
          // keep-sorted start
          .put("adb_dont_kill_server", "true")
          .put("adb_max_no_device_detection_rounds", "1200")
          .put("android_device_daemon", "false")
          .put("cache_installed_apks", "false")
          .put("check_android_device_sim_card_type", "true")
          .put("check_device_interval", "30d")
          .put("clear_android_device_multi_users", "false")
          .put("detect_device_interval_sec", "2")
          .put("disable_calling", "false")
          .put("disable_device_reboot", "true")
          .put("disable_wifi_util_func", "true")
          .put("enable_android_device_ready_check", "false")
          .put("enable_device_state_change_recover", "false")
          .put("enable_device_system_settings_change", "false")
          .put("enable_fastboot_detector", "false")
          .put("enable_root_device", "false")
          .put("ignore_check_device_failure", "true")
          .put("mute_android", "false")
          .put("olc_server_max_started_running_session_num", "30")
          .put("set_test_harness_property", "false")
          .put("simplified_log_format", "true")
          // keep-sorted end
          .buildOrThrow();

  private static final ImmutableList<String> ATS_CONSOLE_FLAGS = toFlagList(ATS_CONSOLE_FLAG_MAP);

  /** Built-in flags of the components [lab server] for all ATS lab server types. */
  private static final ImmutableMap<String, String> ATS_LAB_SERVER_COMMON_FLAG_MAP =
      ImmutableMap.<String, String>builder()
          // keep-sorted start
          .put("adb_dont_kill_server", "true")
          .put("adb_max_no_device_detection_rounds", "1200")
          .put("android_device_daemon", "false")
          .put("cache_installed_apks", "false")
          .put("check_device_interval", "1h")
          .put("detect_device_interval_sec", "2")
          .put("disable_calling", "false")
          .put("disable_device_reboot_for_ro_properties", "true")
          .put("enable_ats_mode", "true")
          .put("enable_device_state_change_recover", "false")
          .put("enable_device_system_settings_change", "false")
          .put("enable_external_master_server", "true")
          .put("enable_root_device", "false")
          .put("enable_test_log_collector", "true")
          .put("mute_android", "false")
          .put("resource_dir_name", "lab_server_res_files")
          .put("serv_via_cloud_rpc", "false")
          .put("set_test_harness_property", "false")
          .put("use_emulator_name_in_uuid", "true")
          // keep-sorted end
          .buildOrThrow();

  /**
   * Built-in flags of the components [lab server] for ATS On-Prem Mode/Omni Mode, by ATS lab server
   * type.
   *
   * <p>ATS On-Prem Mode/Omni Mode can also override these flags.
   */
  private static final ImmutableMap<String, ImmutableMap<String, String>> ATS_LAB_SERVER_FLAG_MAPS =
      ImmutableMap.<String, ImmutableMap<String, String>>builder()
          .put(
              "on-prem",
              ImmutableMap.<String, String>builder()
                  .putAll(ATS_LAB_SERVER_COMMON_FLAG_MAP)
                  // keep-sorted start
                  .put("check_android_device_sim_card_type", "true")
                  .put("clear_android_device_multi_users", "false")
                  .put("disable_device_reboot", "true")
                  .put("disable_wifi_util_func", "true")
                  .put("enable_android_device_ready_check", "false")
                  .put("enable_caching_reserved_device", "true")
                  .put("enable_cloud_logging", "false")
                  .put("enable_fastboot_detector", "false")
                  .put("ignore_check_device_failure", "true")
                  // keep-sorted end
                  .buildOrThrow())
          .put(
              "omni-dda",
              ImmutableMap.<String, String>builder()
                  .putAll(ATS_LAB_SERVER_COMMON_FLAG_MAP)
                  // keep-sorted start
                  .put("add_supported_dimension_for_omni_mode_usage", "dda")
                  .put("android_factory_reset_wait_time", "3m")
                  .put("check_android_device_sim_card_type", "false")
                  .put("clear_android_device_multi_users", "true")
                  .put("disable_device_reboot", "false")
                  .put("disable_wifi_util_func", "false")
                  .put("enable_android_device_ready_check", "true")
                  .put("enable_caching_reserved_device", "false")
                  .put("enable_fastboot_detector", "true")
                  .put("force_device_reboot_after_test", "true")
                  .put("ignore_check_device_failure", "false")
                  .put("reset_device_in_android_real_device_setup", "true")
                  // keep-sorted end
                  .buildOrThrow())
          .put(
              "omni-public-testing",
              ImmutableMap.<String, String>builder()
                  .putAll(ATS_LAB_SERVER_COMMON_FLAG_MAP)
                  // keep-sorted start
                  .put("add_required_dimension_for_partner_shared_pool", "true")
                  .put("add_supported_dimension_for_omni_mode_usage", "public_testing")
                  .put("android_factory_reset_wait_time", "3m")
                  .put("check_android_device_sim_card_type", "false")
                  .put("clear_android_device_multi_users", "false")
                  .put("disable_device_reboot", "false")
                  .put("disable_wifi_util_func", "false")
                  .put("enable_android_device_ready_check", "true")
                  .put("enable_caching_reserved_device", "false")
                  .put("enable_fastboot_detector", "true")
                  .put("force_device_reboot_after_test", "true")
                  .put("ignore_check_device_failure", "false")
                  .put("reset_device_in_android_real_device_setup", "true")
                  // keep-sorted end
                  .buildOrThrow())
          .put(
              "omni-internal-testing",
              ImmutableMap.<String, String>builder()
                  .putAll(ATS_LAB_SERVER_COMMON_FLAG_MAP)
                  // keep-sorted start
                  .put("add_supported_dimension_for_omni_mode_usage", "internal_testing")
                  .put("android_factory_reset_wait_time", "3m")
                  .put("check_android_device_sim_card_type", "true")
                  .put("clear_android_device_multi_users", "false")
                  .put("disable_device_reboot", "true")
                  .put("disable_wifi_util_func", "false")
                  .put("enable_android_device_ready_check", "true")
                  .put("enable_caching_reserved_device", "true")
                  .put("enable_fastboot_detector", "false")
                  .put("force_device_reboot_after_test", "true")
                  .put("ignore_check_device_failure", "true")
                  .put("reset_device_in_android_real_device_setup", "true")
                  // keep-sorted end
                  .buildOrThrow())
          .put(
              "omni-xts-testing",
              ImmutableMap.<String, String>builder()
                  .putAll(ATS_LAB_SERVER_COMMON_FLAG_MAP)
                  // keep-sorted start
                  .put("add_supported_dimension_for_omni_mode_usage", "cts_testing")
                  .put("check_android_device_sim_card_type", "true")
                  .put("clear_android_device_multi_users", "false")
                  .put("disable_device_reboot", "true")
                  .put("disable_wifi_util_func", "true")
                  .put("enable_android_device_ready_check", "false")
                  .put("enable_caching_reserved_device", "true")
                  .put("enable_fastboot_detector", "false")
                  .put("ignore_check_device_failure", "true")
                  .put("keep_test_harness_false", "true")
                  // keep-sorted end
                  .buildOrThrow())
          .put(
              "omni-private",
              ImmutableMap.<String, String>builder()
                  .putAll(ATS_LAB_SERVER_COMMON_FLAG_MAP)
                  // keep-sorted start
                  .put("add_supported_dimension_for_omni_mode_usage", "private_usage")
                  .put("check_android_device_sim_card_type", "true")
                  .put("clear_android_device_multi_users", "false")
                  .put("disable_device_reboot", "true")
                  .put("disable_wifi_util_func", "true")
                  .put("enable_android_device_ready_check", "false")
                  .put("enable_caching_reserved_device", "true")
                  .put("enable_fastboot_detector", "false")
                  .put("ignore_check_device_failure", "true")
                  // keep-sorted end
                  .buildOrThrow())
          .buildOrThrow();

  @VisibleForTesting
  static final ImmutableMap<String, ImmutableList<String>> ATS_LAB_SERVER_FLAGS =
      ATS_LAB_SERVER_FLAG_MAPS.entrySet().stream()
          .collect(toImmutableMap(Map.Entry::getKey, e -> toFlagList(e.getValue())));

  public static ImmutableMap<String, String> atsConsoleFlagMap() {
    return ATS_CONSOLE_FLAG_MAP;
  }

  public static ImmutableList<String> atsConsoleFlags() {
    return ATS_CONSOLE_FLAGS;
  }

  /**
   * Returns built-in flags map of an ATS lab server.
   *
   * @throws IllegalArgumentException if the {@code atsLabServerType} is not valid
   */
  public static ImmutableMap<String, String> atsLabServerFlagMap(String atsLabServerType) {
    checkAtsLabServerType(atsLabServerType);
    return ATS_LAB_SERVER_FLAG_MAPS.get(atsLabServerType);
  }

  /**
   * Returns built-in flags of an ATS lab server.
   *
   * @throws IllegalArgumentException if the {@code atsLabServerType} is not valid
   */
  public static ImmutableList<String> atsLabServerFlags(String atsLabServerType) {
    checkAtsLabServerType(atsLabServerType);
    return ATS_LAB_SERVER_FLAGS.get(atsLabServerType);
  }

  private static void checkAtsLabServerType(String atsLabServerType) {
    checkArgument(
        ATS_LAB_SERVER_FLAG_MAPS.containsKey(atsLabServerType),
        "Invalid ATS lab server type: [%s], valid types: %s",
        atsLabServerType,
        ATS_LAB_SERVER_FLAG_MAPS.keySet());
  }

  private static ImmutableList<String> toFlagList(ImmutableMap<String, String> flagMap) {
    return flagMap.entrySet().stream()
        .map(e -> String.format("--%s=%s", e.getKey(), e.getValue()))
        .collect(toImmutableList());
  }

  private BuiltinFlags() {}
}
