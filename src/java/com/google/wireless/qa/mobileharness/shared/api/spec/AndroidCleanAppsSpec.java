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

package com.google.wireless.qa.mobileharness.shared.api.spec;

import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;

/** Specs for AndroidCleanAppsDecorator. */
public interface AndroidCleanAppsSpec {

  @FileAnnotation(
      required = false,
      help =
          "The build apks, which are the packages under test. If specified and they are system "
              + "packages, will clear their data before running tests.")
  String TAG_BUILD_APK = "build_apk";

  @ParamAnnotation(
      required = false,
      help = "Comma separated apk list which won't be uninstalled/cleaned.")
  String PARAM_PKGS_TO_KEEP = "pkgs_to_keep";

  @ParamAnnotation(
      required = false,
      help = "Regex expression for name of packages which won't be uninstalled/cleaned.")
  String PARAM_PKGS_TO_KEEP_REGEX = "pkgs_to_keep_regex";

  @ParamAnnotation(
      required = false,
      help = "Comma separated list of first party packages to uninstall")
  String PARAM_1P_PKGS_TO_UNINSTALL = "1p_pkgs_to_uninstall";

  @ParamAnnotation(
      required = false,
      help =
          "Boolean value to specify whether to reboot the device after uninstallation. Default is"
              + " set to false.")
  String PARAM_REBOOT_AFTER_UNINSTALLATION = "reboot_after_uninstallation";

  /**
   * Test property to protect additional packages against uninstall. Unlike the above parameter,
   * this may be set dynamically by upstream decorators or plugins
   */
  String PROPERTY_EXTRA_PKGS_TO_KEEP = "extra_pkgs_to_keep";
}
