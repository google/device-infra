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

  /**
   * Built-in flags of the components [lab server] for ATS On-Prem Mode/Omni Mode.
   *
   * <p>ATS On-Prem Mode/Omni Mode can also override these flags.
   */
  @VisibleForTesting
  static final ImmutableMap<String, ImmutableList<String>> ATS_LAB_SERVER_FLAGS =
      ImmutableMap.of(
          "on-prem",
          ImmutableList.of(
              // keep-sorted start
              // keep-sorted end
              ),
          "omni-dda",
          ImmutableList.of(
              // keep-sorted start
              // keep-sorted end
              ),
          "omni-public-testing",
          ImmutableList.of(
              // keep-sorted start
              // keep-sorted end
              ),
          "omni-internal-testing",
          ImmutableList.of(
              // keep-sorted start
              // keep-sorted end
              ),
          "omni-xts-testing",
          ImmutableList.of(
              // keep-sorted start
              // keep-sorted end
              ),
          "omni-private",
          ImmutableList.of(
              // keep-sorted start
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

  private BuiltinFlags() {}
}
