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
import java.time.Duration;

/** Specs for AndroidInstallMainlineModulesDecorator. */
public interface AndroidInstallMainlineModulesDecoratorSpec {

  @FileAnnotation(help = "The bundletool jar.", required = false)
  public static final String TAG_BUNDLETOOL_FILE = "bundletool";

  @FileAnnotation(help = "The mainline modules, which are .apks files.", required = true)
  public static final String TAG_MODULE_FILES = "mainline_modules";

  @ParamAnnotation(
      help = "Whether to reboot the device after installation. Default to be true",
      required = false)
  public static final String REBOOT_AFTER_INSTALLATION = "reboot_after_installation";

  public static final boolean DEFAULT_REBOOT_AFTER_INSTALLATION = true;

  @ParamAnnotation(
      help = "Whether to verify module version code after installation. Default to be false",
      required = false)
  public static final String VERIFY_INSTALLATION = "verify_installation";

  public static final boolean DEFAULT_VERIFY_INSTALLATION = false;

  @ParamAnnotation(
      help = "The timeout to wait for staged session ready in ms. Default to be 2000",
      required = false)
  public static final String WAIT_FOR_STAGED_SESSION_READY_MS = "wait_for_staged_session_ready_ms";

  public static final Duration DEFAULT_WAIT_FOR_STAGED_SESSION_READY = Duration.ofSeconds(2);

  @ParamAnnotation(
      help = "The timeout to execute 'adb install-multi-package' in minutes. Default to be 6",
      required = false)
  public static final String INSTALL_MULTI_PACKAGE_TIMEOUT_MIN =
      "install_multi_package_timeout_min";

  public static final Duration DEFAULT_INSTALL_MULTI_PACKAGE_TIMEOUT = Duration.ofMinutes(6);
}
