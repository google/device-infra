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

package com.google.devtools.mobileharness.shared.util.logging.flogger;

/** Constants for {@link FloggerFormatter}. */
public class FloggerFormatterConstants {

  /** An environment variable which will make flogger not print context if its value is "true". */
  public static final String ENV_VAR_FLOGGER_WITHOUT_CONTEXT = "FLOGGER_WITHOUT_CONTEXT";

  public static final String CONTEXT_PREFIX = "[CONTEXT ";
  public static final String CONTEXT_SUFFIX = " ]";

  public static boolean withContext() {
    String envVar = System.getenv(FloggerFormatterConstants.ENV_VAR_FLOGGER_WITHOUT_CONTEXT);
    if (envVar != null) {
      return !Boolean.parseBoolean(envVar);
    }
    return false;
  }

  private FloggerFormatterConstants() {}
}
