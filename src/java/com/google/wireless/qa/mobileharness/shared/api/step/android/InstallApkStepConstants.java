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

package com.google.wireless.qa.mobileharness.shared.api.step.android;

import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;

/** Constants for {@code InstallApkStep}. */
@SuppressWarnings("InterfaceWithOnlyStatics")
public interface InstallApkStepConstants {

  @FileAnnotation(
      required = false,
      help =
          "The build apks. Typically it is the AUT(app under test). "
              + "If you have packed your AUT and your test code together into a single apk, "
              + "you can ignore this parameter. "
              + "If you have dependencies apks needed for your test, you can also list them here.")
  String TAG_BUILD_APK = "build_apk";

  @FileAnnotation(
      required = false,
      help = "Extra apks to install.  Used in combination with TAG_BUILD_APK")
  String TAG_EXTRA_APK = "extra_apk";

  @ParamAnnotation(
      required = false,
      help =
          "Comma separated file tags. Apks in this tags will be installed in the order the tags are"
              + " listed.")
  String PARAM_INSTALL_APK_EXTRA_FILE_TAGS = "install_apk_extra_file_tags";

  @FileAnnotation(
      required = false,
      help =
          "Dex metadata files to install with the apks. Each Dex metadata file must match by name"
              + " with one of the apks being installed.")
  String TAG_DEX_METADATA = "dex_metadata";

  @ParamAnnotation(
      required = false,
      help = "Skip installing GMS if it is a downgrade. Default value is true.")
  String PARAM_SKIP_GMS_DOWNGRADE = "skip_gms_downgrade";

  @ParamAnnotation(
      required = false,
      help = "Skip installing apks if it is a downgrade. Default value is false.")
  String PARAM_SKIP_APK_DOWNGRADE = "skip_apk_downgrade";

  @ParamAnnotation(
      required = false,
      help =
          "Max execution time of the 'adb install ...' command for each build APK. "
              + "No effect if large than test timeout setting. ")
  String PARAM_INSTALL_APK_TIMEOUT_SEC = "install_apk_timeout_sec";

  @ParamAnnotation(
      required = false,
      help =
          "Use -g for installing build apks. Default value is true."
              + "We didn't get the feature request for supporting different runtime permissions "
              + "when installing multiple build apks, so simply add the entire switch now.")
  String PARAM_GRANT_PERMISSIONS_ON_INSTALL = "grant_permissions_on_install";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to broadcast message when starting and finishing installing the app. Default "
              + "value is false. Specify this as true when you need to register message listener.")
  String PARAM_BROADCAST_INSTALL_MESSAGE = "broadcast_install_message";

  @ParamAnnotation(
      help = "Whether to clear GMS app data before and after installation. Default value is false.")
  String PARAM_CLEAR_GMS_APP_DATA = "clear_gms_app_data";

  @ParamAnnotation(help = "Whether to force install apks. Default value is false.")
  String PARAM_FORCE_INSTALL_APKS = "force_install_apks";

  @ParamAnnotation(
      required = false,
      help =
          "The time to sleep after installing GMS core APK. "
              + "Should smaller than test timeout setting. ")
  String PARAM_SLEEP_AFTER_INSTALL_GMS_SEC = "sleep_after_install_gms_sec";

  @ParamAnnotation(
      required = false,
      help = "Force to reboot the device after installing all build APKs.")
  String PARAM_REBOOT_AFTER_ALL_BUILD_APKS_INSTALLATION =
      "reboot_after_all_build_apks_installation";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to bypass low target sdk check, only works on the device with sdk >= 34."
              + "Default is false.")
  String PARAM_BYPASS_LOW_TARGET_SDK_BLOCK = "bypass_low_target_sdk_block";

  @ParamAnnotation(
      required = false,
      help = "Whether to check installed GMS core version before the test (default true)")
  String PARAM_CHECK_INSTALLED_GMS_CORE_VERSION = "check_installed_gms_core_version";
}
