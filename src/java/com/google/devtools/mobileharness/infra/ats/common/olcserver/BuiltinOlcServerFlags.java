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
 * Builtin flags of OLC server for ATS console / local runner.
 *
 * <p>ATS console / local runner can also override these flags.
 */
class BuiltinOlcServerFlags {

  private static final ImmutableList<String> VALUE =
      ImmutableList.of(
          "--adb_dont_kill_server=true",
          "--adb_max_no_device_detection_rounds=1200",
          "--android_device_daemon=false",
          "--check_device_interval=1h",
          "--clear_android_device_multi_users=false",
          "--disable_calling=false",
          "--disable_device_reboot=true",
          "--disable_wifi_util_func=true",
          "--enable_android_device_ready_check=false",
          "--enable_device_state_change_recover=false",
          "--enable_device_system_settings_change=false",
          "--mute_android=false",
          "--olc_server_max_started_running_session_num=30",
          "--set_test_harness_property=false",
          "--simplified_log_format=true");

  static ImmutableList<String> get() {
    return VALUE;
  }

  private BuiltinOlcServerFlags() {}
}
