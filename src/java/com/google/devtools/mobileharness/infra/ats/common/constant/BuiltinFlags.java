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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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
  private static final ImmutableList<String> ATS_CONSOLE_FLAGS =
      ImmutableList.of(
          // keep-sorted start
          "--adb_dont_kill_server=true",
          "--adb_max_no_device_detection_rounds=1200",
          "--android_device_daemon=false",
          "--cache_installed_apks=false",
          "--check_android_device_sim_card_type=true",
          "--check_device_interval=30d",
          "--clear_android_device_multi_users=false",
          "--detect_device_interval_sec=2",
          "--disable_calling=false",
          "--disable_device_reboot=true",
          "--disable_wifi_util_func=true",
          "--enable_android_device_ready_check=false",
          "--enable_device_state_change_recover=false",
          "--enable_device_system_settings_change=false",
          "--enable_fastboot_detector=false",
          "--enable_root_device=false",
          "--ignore_check_device_failure=true",
          "--mute_android=false",
          "--olc_server_max_started_running_session_num=30",
          "--set_test_harness_property=false",
          "--simplified_log_format=true"
          // keep-sorted end
          );

  /** Built-in flags of the components [lab server] for all ATS lab server types. */
  private static final ImmutableList<String> ATS_LAB_SERVER_COMMON_FLAGS =
      ImmutableList.of(
          // keep-sorted start
          "--adb_dont_kill_server=true",
          "--adb_max_no_device_detection_rounds=1200",
          "--android_device_daemon=false",
          "--cache_installed_apks=false",
          "--check_device_interval=1h",
          "--detect_device_interval_sec=2",
          "--disable_calling=false",
          "--disable_device_reboot_for_ro_properties=true",
          "--enable_ats_mode=true",
          "--enable_device_state_change_recover=false",
          "--enable_device_system_settings_change=false",
          "--enable_external_master_server=true",
          "--enable_root_device=false",
          "--enable_test_log_collector=true",
          "--mute_android=false",
          "--resource_dir_name=lab_server_res_files",
          "--serv_via_cloud_rpc=false",
          "--set_test_harness_property=false",
          "--use_emulator_name_in_uuid=true"
          // keep-sorted end
          );

  /**
   * Built-in flags of the components [lab server] for ATS On-Prem Mode/Omni Mode, by ATS lab server
   * type.
   *
   * <p>ATS On-Prem Mode/Omni Mode can also override these flags.
   */
  @VisibleForTesting
  static final ImmutableMap<String, ImmutableList<String>> ATS_LAB_SERVER_FLAGS =
      ImmutableMap.of(
          "on-prem",
          append(
              ATS_LAB_SERVER_COMMON_FLAGS,
              // keep-sorted start
              "--check_android_device_sim_card_type=true",
              "--clear_android_device_multi_users=false",
              "--disable_device_reboot=true",
              "--disable_wifi_util_func=true",
              "--enable_android_device_ready_check=false",
              "--enable_caching_reserved_device=true",
              "--enable_cloud_logging=false",
              "--enable_fastboot_detector=false",
              "--ignore_check_device_failure=true"
              // keep-sorted end
              ),
          "omni-dda",
          append(
              ATS_LAB_SERVER_COMMON_FLAGS,
              // keep-sorted start
              "--add_supported_dimension_for_omni_mode_usage=dda",
              "--android_factory_reset_wait_time=3m",
              "--check_android_device_sim_card_type=false",
              "--clear_android_device_multi_users=true",
              "--disable_device_reboot=false",
              "--disable_wifi_util_func=false",
              "--enable_android_device_ready_check=true",
              "--enable_caching_reserved_device=false",
              "--enable_fastboot_detector=true",
              "--force_device_reboot_after_test=true",
              "--ignore_check_device_failure=false",
              "--reset_device_in_android_real_device_setup=true"
              // keep-sorted end
              ),
          "omni-public-testing",
          append(
              ATS_LAB_SERVER_COMMON_FLAGS,
              // keep-sorted start
              "--add_required_dimension_for_partner_shared_pool=true",
              "--add_supported_dimension_for_omni_mode_usage=public_testing",
              "--android_factory_reset_wait_time=3m",
              "--check_android_device_sim_card_type=false",
              "--clear_android_device_multi_users=false",
              "--disable_device_reboot=false",
              "--disable_wifi_util_func=false",
              "--enable_android_device_ready_check=true",
              "--enable_caching_reserved_device=false",
              "--enable_fastboot_detector=true",
              "--force_device_reboot_after_test=true",
              "--ignore_check_device_failure=false",
              "--reset_device_in_android_real_device_setup=true"
              // keep-sorted end
              ),
          "omni-internal-testing",
          append(
              ATS_LAB_SERVER_COMMON_FLAGS,
              // keep-sorted start
              "--add_supported_dimension_for_omni_mode_usage=internal_testing",
              "--android_factory_reset_wait_time=3m",
              "--check_android_device_sim_card_type=true",
              "--clear_android_device_multi_users=false",
              "--disable_device_reboot=true",
              "--disable_wifi_util_func=false",
              "--enable_android_device_ready_check=true",
              "--enable_caching_reserved_device=true",
              "--enable_fastboot_detector=false",
              "--force_device_reboot_after_test=true",
              "--ignore_check_device_failure=true",
              "--reset_device_in_android_real_device_setup=true"
              // keep-sorted end
              ),
          "omni-xts-testing",
          append(
              ATS_LAB_SERVER_COMMON_FLAGS,
              // keep-sorted start
              "--add_supported_dimension_for_omni_mode_usage=cts_testing",
              "--check_android_device_sim_card_type=true",
              "--clear_android_device_multi_users=false",
              "--disable_device_reboot=true",
              "--disable_wifi_util_func=true",
              "--enable_android_device_ready_check=false",
              "--enable_caching_reserved_device=true",
              "--enable_fastboot_detector=false",
              "--ignore_check_device_failure=true",
              "--keep_test_harness_false=true"
              // keep-sorted end
              ),
          "omni-private",
          append(
              ATS_LAB_SERVER_COMMON_FLAGS,
              // keep-sorted start
              "--add_supported_dimension_for_omni_mode_usage=private_usage",
              "--check_android_device_sim_card_type=true",
              "--clear_android_device_multi_users=false",
              "--disable_device_reboot=true",
              "--disable_wifi_util_func=true",
              "--enable_android_device_ready_check=false",
              "--enable_caching_reserved_device=true",
              "--enable_fastboot_detector=false",
              "--ignore_check_device_failure=true"
              // keep-sorted end
              ));

  public static ImmutableList<String> atsConsoleFlags() {
    return ATS_CONSOLE_FLAGS;
  }

  /**
   * Returns built-in flags of an ATS lab server, or an empty list if it is not an ATS lab server
   * (the {@linkplain #ATS_LAB_SERVER_TYPE_PROPERTY_KEY ATS lab server type} is not specified in the
   * system properties).
   *
   * @throws IllegalArgumentException if the {@linkplain #ATS_LAB_SERVER_TYPE_PROPERTY_KEY ATS lab
   *     server type} specified in the system properties is invalid
   */
  public static ImmutableList<String> atsLabServerFlags(
      ImmutableMap<String, String> systemProperties) {
    if (!systemProperties.containsKey(ATS_LAB_SERVER_TYPE_PROPERTY_KEY)) {
      return ImmutableList.of();
    }
    String atsLabServerType = systemProperties.get(ATS_LAB_SERVER_TYPE_PROPERTY_KEY);
    checkArgument(
        ATS_LAB_SERVER_FLAGS.containsKey(atsLabServerType),
        "Invalid value of property [%s]: [%s], valid values: %s",
        ATS_LAB_SERVER_TYPE_PROPERTY_KEY,
        atsLabServerType,
        ATS_LAB_SERVER_FLAGS.keySet());
    return ATS_LAB_SERVER_FLAGS.get(atsLabServerType);
  }

  private static ImmutableList<String> append(ImmutableList<String> list, String... elements) {
    if (elements.length == 0) {
      return list;
    }
    return ImmutableList.<String>builder().addAll(list).add(elements).build();
  }

  private BuiltinFlags() {}
}
