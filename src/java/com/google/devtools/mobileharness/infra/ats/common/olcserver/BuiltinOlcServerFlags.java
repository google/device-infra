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

package com.google.devtools.mobileharness.infra.ats.common.olcserver;

import com.google.common.collect.ImmutableList;

/**
 * Built-in flags of OLC server/ATS console/ATS local runner for ATS console/ATS local runner.
 *
 * <p>ATS console/ATS local runner can also override these flags.
 */
public class BuiltinOlcServerFlags {

  private static final ImmutableList<String> VALUE =
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

  public static ImmutableList<String> get() {
    return VALUE;
  }

  private BuiltinOlcServerFlags() {}
}
