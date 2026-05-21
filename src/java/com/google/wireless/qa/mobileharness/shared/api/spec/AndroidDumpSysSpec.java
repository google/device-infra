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

/** Spec for AndroidDumpSysDecorator. */
public final class AndroidDumpSysSpec {

  public static final String DUMPSYS_TYPE_HELP =
      "https://github.com/google/device-infra/blob/main/src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb/DumpSysType.java";

  @ParamAnnotation(
      required = false,
      help =
          "If set to 'true', will save the dumpsys log in file and send back to client or test"
              + " result service. Otherwise if set to 'false' or not specified, it will merge the"
              + " dumpsys log to the test log.")
  public static final String PARAM_LOG_TO_FILE = "log_to_file";

  public static final String PARAM_LOG_FILE_NAME_DEFAULT = "dumpsys.log";
  public static final String PARAM_DUMPSYS_TYPE_DEFAULT = "activity";
  public static final String PARAM_DUMPSYS_TYPE_NONE = "none";
  public static final String PARAM_DUMPSYS_SUFFIX_DEFAULT = "all";

  @ParamAnnotation(
      required = false,
      help =
          "If set to 'true', will save the dumpsys log in a user specific file name. "
              + "Otherwise if set to 'false' or not specified, it will use default name "
              + "dumpsys.log.")
  public static final String PARAM_LOG_FILE_NAME = "dumpsys_log_file_name";

  @ParamAnnotation(
      required = false,
      help =
          "Runs dumpsys <DUMPSYS_TYPE> <DUMPSYS_SUFFIX>. "
              + "See <a href='"
              + DUMPSYS_TYPE_HELP
              + "'>help</a> for dump sys types. "
              + "Default values will run 'dumpsys "
              + PARAM_DUMPSYS_TYPE_DEFAULT
              + " "
              + PARAM_DUMPSYS_SUFFIX_DEFAULT
              + "'. For backward compatibility, set type to "
              + PARAM_DUMPSYS_TYPE_NONE
              + " to get a diagnostic output for all system services.")
  public static final String PARAM_DUMPSYS_TYPE = "dumpsys_type";

  @ParamAnnotation(
      required = false,
      help =
          "Runs dumpsys <DUMPSYS_TYPE> <DUMPSYS_SUFFIX>. "
              + "See <a href='"
              + DUMPSYS_TYPE_HELP
              + "'>help</a> for dump sys types. "
              + "Default values will run 'dumpsys "
              + PARAM_DUMPSYS_TYPE_DEFAULT
              + " "
              + PARAM_DUMPSYS_SUFFIX_DEFAULT
              + "'. For backward compatibility, set type to "
              + PARAM_DUMPSYS_TYPE_NONE
              + " to get a diagnostic output for all system services, "
              + "suffix will be ignored in this case.")
  public static final String PARAM_DUMPSYS_SUFFIX = "dumpsys_suffix";

  private AndroidDumpSysSpec() {}
}
