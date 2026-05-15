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

import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;

/** Specs for Android bugreport. */
public interface AndroidBugreportSpec {

  /** The bugreport file name (SDK < 24). */
  public static final String BUGREPORT_TXT_FILE_NAME = "bugreport.txt";

  /** The bugreport zip file name (SDK >= 24). */
  public static final String BUGREPORT_ZIP_FILE_NAME = "bugreport.zip";

  /** Bugreport file type. */
  public static final String FILE_TYPE_BUGREPORT = "bugreport";

  /** Monsoon file type. */
  public static final String FILE_TYPE_POWER_FILE = "powermonitor";

  /** BatteryStats output file name. */
  public static final String BATTERY_STATS_FILE_NAME = "batterystats_%s.log";

  @ParamAnnotation(
      required = false,
      help = "Whether or not to run this decorator when a test passes.")
  String PARAM_BUGREPORT_ON_PASS = "bugreport_on_pass";

  @ParamAnnotation(
      required = false,
      help = "Whether to delete all bugreport generated on the device. By default, it is true.")
  String PARAM_BUGREPORT_DELETE = "delete_bugreport";

  @ParamAnnotation(
      required = false,
      help =
          "If true, will disable batterystats auto reset before a test and re-enable it after the "
              + "test. Default is false. ")
  public static final String PARAM_NO_AUTO_RESET = "bs_no_auto_reset";

  @ParamAnnotation(
      required = false,
      help =
          "If true, will logically discharge the device before a test by using "
              + "\"dumpsys battery set usb 0\", and resume normal state after the test. "
              + "Default is false.")
  public static final String PARAM_LOGICAL_DISCHARGE = "bs_logical_discharge";
}
